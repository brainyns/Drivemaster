package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.service.TransicionOrdenService.Actor;
import com.drivemaster.drivemaster.util.HaversineUtil;
import com.drivemaster.drivemaster.util.PlazoHabil;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Asigna cada solicitud al taller más cercano y la reasigna cuando el taller la
 * rechaza o no responde a tiempo.
 *
 * Candidatos: talleres aprobados y activos que no hayan descartado ya la solicitud.
 *  1. Talleres de la misma ciudad, ordenados por distancia (si hay coordenadas) y rating.
 *  2. Si no hay ninguno en la ciudad: el más cercano dentro de RADIO_MAX_KM
 *     (requiere coordenadas del cliente y del taller).
 *  3. Si tampoco hay: la solicitud queda RECHAZADA.
 */
@Service
public class AsignacionTallerService {

    private final TallerRepository tallerRepository;
    private final TransicionOrdenService transiciones;
    private final ParametrosMarketplace parametros;
    private final AvisosSolicitudServicio avisos;
    private final LiquidacionService liquidacion;

    public AsignacionTallerService(TallerRepository tallerRepository,
                                   TransicionOrdenService transiciones,
                                   ParametrosMarketplace parametros,
                                   AvisosSolicitudServicio avisos,
                                   LiquidacionService liquidacion) {
        this.liquidacion = liquidacion;
        this.tallerRepository = tallerRepository;
        this.transiciones = transiciones;
        this.parametros = parametros;
        this.avisos = avisos;
    }

    record Candidato(Taller taller, Double distanciaKm) {
    }

    /** Asigna una solicitud ENVIADA o REASIGNADA al mejor candidato disponible. */
    public OrdenServicio asignarSiguiente(OrdenServicio orden) {
        List<Candidato> candidatos = candidatos(orden);

        if (candidatos.isEmpty()) {
            OrdenServicio rechazada = transiciones.transicionar(orden, EstadoOrden.RECHAZADA, Actor.SISTEMA,
                    "No hay talleres disponibles en la zona", null,
                    new Update().set("motivoCierre", "No hay talleres disponibles en la zona"));
            // Sin taller no hubo diagnóstico: si ya se había pagado, la línea se anula y se reembolsa
            liquidacion.ordenRechazadaSinTaller(rechazada.getId());
            avisos.sinTallerDisponible(rechazada);
            return rechazada;
        }

        Candidato elegido = candidatos.get(0);
        Taller taller = elegido.taller();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = PlazoHabil.sumarMinutosHabiles(ahora, parametros.timeoutTallerMin(),
                parametros.horaInicioAtencion(), parametros.horaFinAtencion());

        Update cambios = new Update()
                .set("tallerId", taller.getId())
                .set("tallerNombre", taller.getNombre())
                .set("fechaAsignacion", ahora)
                .set("fechaLimiteRespuesta", limite)
                .set("distanciaTallerKm", elegido.distanciaKm())
                .set("aceptadaPorTaller", false);

        String nota = "Asignada a " + taller.getNombre()
                + (elegido.distanciaKm() != null ? String.format(Locale.ROOT, " (%.1f km)", elegido.distanciaKm()) : "");
        OrdenServicio asignada = transiciones.transicionar(orden, EstadoOrden.ASIGNADA_A_TALLER, Actor.SISTEMA,
                nota, null, cambios);
        avisos.tallerAsignado(asignada, taller);
        return asignada;
    }

    /**
     * Quita la solicitud al taller actual (rechazo, falta de respuesta o decisión del
     * ADMIN) y la asigna al siguiente candidato. La condición sobre tallerId y
     * aceptadaPorTaller evita reasignar dos veces la misma solicitud o quitarle una
     * solicitud a un taller que acaba de aceptarla.
     */
    public OrdenServicio reasignar(OrdenServicio orden, Actor actor, String motivo) {
        String tallerAnterior = orden.getTallerId();
        Criteria condicion = Criteria.where("tallerId").is(tallerAnterior).and("aceptadaPorTaller").ne(true);
        Update cambios = new Update()
                .addToSet("talleresDescartados", tallerAnterior)
                .unset("tallerId")
                .unset("tallerNombre")
                .unset("fechaLimiteRespuesta")
                .unset("distanciaTallerKm");

        OrdenServicio liberada = transiciones.transicionar(orden, EstadoOrden.REASIGNADA, actor, motivo, condicion, cambios);
        avisos.solicitudRetirada(tallerAnterior, liberada, motivo);
        return asignarSiguiente(liberada);
    }

    List<Candidato> candidatos(OrdenServicio orden) {
        Set<String> descartados = orden.getTalleresDescartados() != null
                ? Set.copyOf(orden.getTalleresDescartados()) : Set.of();
        boolean clienteConCoordenadas = orden.getLatitud() != null && orden.getLongitud() != null;
        String ciudad = normalizarCiudad(orden.getCiudad());

        List<Candidato> disponibles = tallerRepository.findByActivoTrue().stream()
                .filter(t -> EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()))
                .filter(t -> !descartados.contains(t.getId()))
                .map(t -> new Candidato(t, distancia(orden, t, clienteConCoordenadas)))
                .toList();

        Comparator<Candidato> porCercania = Comparator
                .comparing(Candidato::distanciaKm, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.taller().getRating(), Comparator.nullsLast(Comparator.reverseOrder()));

        List<Candidato> mismaCiudad = disponibles.stream()
                .filter(c -> !ciudad.isEmpty() && ciudad.equals(normalizarCiudad(c.taller().getCiudad())))
                .sorted(porCercania)
                .toList();
        if (!mismaCiudad.isEmpty()) return mismaCiudad;

        double radio = parametros.radioMaxKm();
        return disponibles.stream()
                .filter(c -> c.distanciaKm() != null && c.distanciaKm() <= radio)
                .sorted(porCercania)
                .toList();
    }

    private Double distancia(OrdenServicio orden, Taller taller, boolean clienteConCoordenadas) {
        if (!clienteConCoordenadas || taller.getLatitud() == null || taller.getLongitud() == null) return null;
        return HaversineUtil.calcularDistanciaKm(orden.getLatitud(), orden.getLongitud(),
                taller.getLatitud(), taller.getLongitud());
    }

    /** "Bogotá D.C." y "bogota" se consideran la misma ciudad. */
    static String normalizarCiudad(String ciudad) {
        if (ciudad == null) return "";
        String sinTildes = Normalizer.normalize(ciudad, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String soloLetras = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return soloLetras.endsWith("dc") ? soloLetras.substring(0, soloLetras.length() - 2) : soloLetras;
    }
}
