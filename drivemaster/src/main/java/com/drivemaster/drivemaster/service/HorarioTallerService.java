package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO;
import com.drivemaster.drivemaster.model.CambioTaller;
import com.drivemaster.drivemaster.model.DiaSemana;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.HorarioTaller;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.CambioTallerRepository;
import com.drivemaster.drivemaster.repository.HorarioTallerRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.util.HorarioSemanal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Horario de atención estructurado del taller: por cada día, cerrado o una o más franjas sin cruces. Reemplaza al
 * texto libre de Taller.horario, que se sigue llenando con un resumen generado para que el cliente lo vea igual que
 * antes. El taller solo ve y edita el suyo; el administrador puede consultarlo.
 */
@Service
public class HorarioTallerService {

    public static final int MAX_FRANJAS_POR_DIA = HorarioSemanal.MAX_FRANJAS_POR_DIA;

    private final HorarioTallerRepository horarios;
    private final TallerRepository tallerRepository;
    private final TallerService tallerService;
    private final CambioTallerRepository cambios;

    public HorarioTallerService(HorarioTallerRepository horarios, TallerRepository tallerRepository,
                                TallerService tallerService, CambioTallerRepository cambios) {
        this.horarios = horarios;
        this.tallerRepository = tallerRepository;
        this.tallerService = tallerService;
        this.cambios = cambios;
    }

    /**
     * El taller {id} si quien llama puede verlo: el administrador ve cualquiera; el taller, solo el suyo (si pide otro,
     * 403 sin decir si existe).
     */
    public Taller tallerAccesible(String id, String correo, boolean esAdmin) {
        if (esAdmin) {
            return tallerRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taller no encontrado"));
        }
        Taller propio = tallerService.obtenerPorUsuario(correo);
        if (!propio.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver el horario de tu taller");
        }
        return propio;
    }

    /** Las franjas del taller ordenadas por día y hora; vacío si aún no configuró el horario. */
    public List<FranjaHorarioDTO> obtener(String id, String correo, boolean esAdmin) {
        return franjasDe(tallerAccesible(id, correo, esAdmin).getId()).stream().map(HorarioTallerService::toDTO).toList();
    }

    /** Las franjas guardadas de un taller, por día y luego por orden. */
    public List<HorarioTaller> franjasDe(String tallerId) {
        return horarios.findByTallerId(tallerId).stream()
                .sorted(Comparator.comparing(HorarioTaller::getDiaSemana).thenComparingInt(HorarioTaller::getOrden))
                .toList();
    }

    /** Reemplaza el horario completo. Los días que no vienen quedan cerrados. */
    public List<FranjaHorarioDTO> reemplazar(String id, String correo, List<FranjaHorarioDTO> franjas) {
        Taller taller = tallerAccesible(id, correo, false);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller aún no está aprobado");
        }
        Map<DiaSemana, List<FranjaHorarioDTO>> porDia = HorarioSemanal.validar(franjas);

        List<HorarioTaller> nuevas = HorarioSemanal.semanaCompleta(porDia).stream()
                .map(f -> HorarioTaller.builder().tallerId(taller.getId()).diaSemana(f.getDiaSemana()).abierto(f.isAbierto())
                        .horaApertura(f.getHoraApertura()).horaCierre(f.getHoraCierre()).orden(f.getOrden()).build())
                .toList();
        horarios.deleteByTallerId(taller.getId());
        horarios.saveAll(nuevas);

        // TODO: retirar Taller.horario cuando el mapa y la lista del cliente lean el horario estructurado.
        String resumen = DatosTallerService.limpiarHorario(HorarioSemanal.resumen(porDia));
        if (!Objects.equals(resumen, taller.getHorario())) {
            cambios.save(CambioTaller.builder()
                    .tallerId(taller.getId()).campo(CambioTaller.HORARIO)
                    .valorAnterior(taller.getHorario()).valorNuevo(resumen)
                    .cambiadoPor(correo).fecha(LocalDateTime.now()).build());
            taller.setHorario(resumen);
            tallerRepository.save(taller);
        }
        return franjasDe(taller.getId()).stream().map(HorarioTallerService::toDTO).toList();
    }

    /**
     * Rechaza con 400 una cita de revisión fuera del horario del taller: un día que cierra o una hora fuera de sus
     * franjas (la hora de cierre ya no cuenta). Si el taller aún no configura su horario por días, no restringe.
     */
    public void validarCita(String tallerId, LocalDateTime fecha) {
        List<HorarioTaller> franjas = franjasDe(tallerId);
        if (franjas.isEmpty()) return;
        DiaSemana dia = DiaSemana.de(fecha.getDayOfWeek());
        List<HorarioTaller> delDia = franjas.stream().filter(f -> f.getDiaSemana() == dia && f.isAbierto()).toList();
        String nombreDia = nombre(dia).toLowerCase();
        if (delDia.isEmpty()) {
            throw invalido("Tu taller está cerrado los " + (nombreDia.endsWith("s") ? nombreDia : nombreDia + "s")
                    + ". Elige otro día para la revisión");
        }
        LocalTime hora = fecha.toLocalTime();
        boolean dentro = delDia.stream()
                .anyMatch(f -> !hora.isBefore(f.getHoraApertura()) && hora.isBefore(f.getHoraCierre()));
        if (!dentro) {
            throw invalido("La revisión a las " + hora12(hora) + " queda fuera de tu horario del " + nombreDia
                    + " (" + textoFranjas(delDia) + ")");
        }
    }

    // Las reglas y el resumen en texto son los mismos de la tienda: viven en HorarioSemanal

    private static String textoFranjas(List<HorarioTaller> lista) {
        return HorarioSemanal.textoFranjas(lista == null ? null : lista.stream().map(HorarioTallerService::toDTO).toList());
    }

    static String hora12(LocalTime t) {
        return HorarioSemanal.hora12(t);
    }

    static String nombre(DiaSemana dia) {
        return HorarioSemanal.nombre(dia);
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private static FranjaHorarioDTO toDTO(HorarioTaller h) {
        return FranjaHorarioDTO.builder().diaSemana(h.getDiaSemana()).abierto(h.isAbierto())
                .horaApertura(h.getHoraApertura()).horaCierre(h.getHoraCierre()).orden(h.getOrden()).build();
    }
}
