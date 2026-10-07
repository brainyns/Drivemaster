package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.RepuestoBuscadoDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.RepuestoSeleccionado;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.util.CompatibilidadVehiculo;
import com.drivemaster.drivemaster.util.HaversineUtil;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.drivemaster.drivemaster.util.CompatibilidadVehiculo.COMPATIBLE;
import static com.drivemaster.drivemaster.util.CompatibilidadVehiculo.NO_COMPATIBLE;
import static com.drivemaster.drivemaster.util.CompatibilidadVehiculo.POR_CONFIRMAR;

/**
 * Buscador de repuestos en el inventario de todas las tiendas aprobadas y activas
 * (las demás nunca aparecen), evaluado contra el vehículo de la orden.
 *
 * Cada oferta trae su estado calculado aquí:
 *  - compatibilidad: COMPATIBLE (universal o declarada para el vehículo),
 *    NO_COMPATIBLE (declarada para otros vehículos) o POR_CONFIRMAR (sin datos);
 *  - estado de la tarjeta: NO_COMPATIBLE → AGOTADO → POR_CONFIRMAR → COMPATIBLE;
 *  - seleccionable y motivo. Se elige lo compatible o por confirmar que esté
 *    disponible en la cantidad pedida o sea por encargo. POR_CONFIRMAR exige que
 *    el mecánico confirme la compatibilidad.
 *
 * Las ofertas de la misma pieza se agrupan (Tienda A: 8 uds, Tienda B: 3, Tienda C: 0)
 * y se ordenan: seleccionables primero, luego disponibilidad → distancia al taller
 * → precio. El plan de suscripción de la tienda no altera el orden.
 */
@Service
public class BuscadorRepuestosService {

    private static final int MAX_PRODUCTOS = 500;
    private static final int MIN_TEXTO = 2;
    private static final Set<String> ESTADOS_CON_BUSQUEDA = Set.of(EstadoOrden.EN_REVISION, EstadoOrden.DIAGNOSTICADA);
    private static final Map<String, Integer> RANGO_DISPONIBILIDAD = Map.of(
            OfertaRepuestoDTO.DISPONIBLE, 0,
            OfertaRepuestoDTO.ENCARGO, 1,
            OfertaRepuestoDTO.PARCIAL, 2,
            OfertaRepuestoDTO.AGOTADO, 3);

    static final Comparator<OfertaRepuestoDTO> RECOMENDACION = Comparator
            .comparing((OfertaRepuestoDTO o) -> o.isSeleccionable() ? 0 : 1)
            .thenComparing(o -> RANGO_DISPONIBILIDAD.getOrDefault(o.getDisponibilidad(), 9))
            .thenComparing(OfertaRepuestoDTO::getDistanciaKm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(OfertaRepuestoDTO::getPrecio, Comparator.nullsLast(Comparator.naturalOrder()));

    private final MongoTemplate mongoTemplate;
    private final TiendaRepository tiendaRepository;
    private final OrdenServicioRepository ordenRepository;
    private final TallerService tallerService;
    private final VehiculoService vehiculoService;

    public BuscadorRepuestosService(MongoTemplate mongoTemplate,
                                    TiendaRepository tiendaRepository,
                                    OrdenServicioRepository ordenRepository,
                                    TallerService tallerService,
                                    VehiculoService vehiculoService) {
        this.mongoTemplate = mongoTemplate;
        this.tiendaRepository = tiendaRepository;
        this.ordenRepository = ordenRepository;
        this.tallerService = tallerService;
        this.vehiculoService = vehiculoService;
    }

    /** Ofertas pareadas con su producto, para agrupar. */
    record Oferta(Producto producto, OfertaRepuestoDTO dto) {
    }

    /**
     * @param soloCompatibles true (por defecto): solo ofertas COMPATIBLE, incluidas las
     *                        agotadas. false: también NO_COMPATIBLE y POR_CONFIRMAR.
     */
    public List<RepuestoBuscadoDTO> buscar(String correoTaller, String ordenId, String texto,
                                           String referencia, Integer cantidad, boolean soloCompatibles) {
        Taller taller = tallerService.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = ordenRepository.findById(ordenId)
                .filter(o -> EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!taller.getId().equals(orden.getTallerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta solicitud no está asignada a tu taller");
        }
        if (!ESTADOS_CON_BUSQUEDA.contains(orden.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El buscador está disponible durante la revisión y el diagnóstico");
        }
        int unidades = cantidad != null && cantidad > 0 ? cantidad : 1;
        Vehiculo vehiculo = vehiculoService.obtenerEntidad(orden.getVehiculoId());
        Map<String, Tienda> tiendas = tiendasOperativas();
        if (tiendas.isEmpty()) return List.of();

        Set<String> tiendasSeleccion = (orden.getRepuestos() != null ? orden.getRepuestos() : List.<RepuestoSeleccionado>of())
                .stream().map(RepuestoSeleccionado::getTiendaId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<Oferta> ofertas = consultar(tiendas.keySet(), texto, referencia, vehiculo, soloCompatibles).stream()
                .map(p -> {
                    OfertaRepuestoDTO dto = evaluar(p, tiendas.get(p.getTiendaId()), unidades, taller, vehiculo);
                    dto.setMismaTiendaQueSeleccion(tiendasSeleccion.contains(p.getTiendaId()));
                    return new Oferta(p, dto);
                })
                .filter(o -> !soloCompatibles || COMPATIBLE.equals(o.dto().getCompatibilidad()))
                .toList();
        return agrupar(ofertas);
    }

    /**
     * Valida un producto elegido para la orden con las mismas reglas del buscador y
     * devuelve su oferta evaluada. Rechaza lo NO_COMPATIBLE y lo agotado (o con
     * stock insuficiente) que no sea por encargo; la confirmación de lo
     * POR_CONFIRMAR la exige quien llama (requiereConfirmacion).
     */
    public OfertaRepuestoDTO ofertaParaSeleccion(Producto producto, int cantidad, Taller taller, Vehiculo vehiculo) {
        if (Boolean.FALSE.equals(producto.getActivo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + producto.getNombre() + "\" ya no está disponible");
        }
        Tienda tienda = producto.getTiendaId() != null ? tiendasOperativas().get(producto.getTiendaId()) : null;
        if (tienda == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La tienda de \"" + producto.getNombre() + "\" no está disponible en este momento");
        }
        OfertaRepuestoDTO oferta = evaluar(producto, tienda, cantidad, taller, vehiculo);
        if (!oferta.isSeleccionable()) {
            HttpStatus status = NO_COMPATIBLE.equals(oferta.getCompatibilidad()) ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
            throw new ResponseStatusException(status, "\"" + producto.getNombre() + "\" en " + tienda.getNombre()
                    + ": " + oferta.getMotivo());
        }
        return oferta;
    }

    // ─── Consulta ────────────────────────────────────────────────────────────

    private Map<String, Tienda> tiendasOperativas() {
        return tiendaRepository.findAll().stream()
                .filter(t -> EstadoAprobacion.estaAprobado(t.getEstadoAprobacion()) && Boolean.TRUE.equals(t.getActivo()))
                .collect(Collectors.toMap(Tienda::getId, Function.identity()));
    }

    private List<Producto> consultar(Set<String> tiendaIds, String texto, String referencia, Vehiculo vehiculo,
                                     boolean soloCompatibles) {
        List<Criteria> filtros = new ArrayList<>();
        filtros.add(Criteria.where("tiendaId").in(tiendaIds));
        // Los productos creados desde el panel no guardan "activo": null cuenta como activo.
        filtros.add(Criteria.where("activo").ne(false));

        String t = texto != null ? texto.trim() : "";
        if (t.length() >= MIN_TEXTO) {
            Pattern p = Pattern.compile(escapar(t), Pattern.CASE_INSENSITIVE);
            filtros.add(new Criteria().orOperator(
                    Criteria.where("nombre").regex(p),
                    Criteria.where("categoria").regex(p),
                    Criteria.where("marca").regex(p),
                    Criteria.where("referencia").regex(p),
                    Criteria.where("codigo").regex(p)));
        }
        String ref = referencia != null ? referencia.trim() : "";
        if (ref.length() >= MIN_TEXTO) {
            filtros.add(Criteria.where("referencia").regex(Pattern.compile(escapar(ref), Pattern.CASE_INSENSITIVE)));
        }
        if (soloCompatibles) {
            // Prefiltro: mismo modelo o universales. Año y motor se evalúan en memoria.
            filtros.add(new Criteria().orOperator(
                    Criteria.where("modelosCompatibles.modelo")
                            .regex(Pattern.compile("^" + escapar(vehiculo.getModelo()) + "$", Pattern.CASE_INSENSITIVE)),
                    Criteria.where("compatibilidadUniversal").is(true)));
        }

        Query query = Query.query(new Criteria().andOperator(filtros.toArray(new Criteria[0]))).limit(MAX_PRODUCTOS);
        return mongoTemplate.find(query, Producto.class);
    }

    // ─── Evaluación y agrupación (sin acceso a datos: probadas en unitarias) ──

    /** Oferta de un producto con su estado frente al vehículo y la cantidad pedida. */
    static OfertaRepuestoDTO evaluar(Producto p, Tienda tienda, int cantidad, Taller taller, Vehiculo vehiculo) {
        String compatibilidad = CompatibilidadVehiculo.estado(p, vehiculo);

        int stock = p.getStockActual() != null ? p.getStockActual() : 0;
        String disponibilidad;
        Integer diasEntrega = null;
        if ("ENCARGO".equals(p.getTipo())) {
            disponibilidad = OfertaRepuestoDTO.ENCARGO;
            diasEntrega = p.getDiasEntregaEncargo();
        } else if (stock >= cantidad) {
            disponibilidad = OfertaRepuestoDTO.DISPONIBLE;
        } else if (stock > 0) {
            disponibilidad = OfertaRepuestoDTO.PARCIAL;
        } else {
            disponibilidad = OfertaRepuestoDTO.AGOTADO;
        }

        boolean hayDisponibilidad = OfertaRepuestoDTO.DISPONIBLE.equals(disponibilidad)
                || OfertaRepuestoDTO.ENCARGO.equals(disponibilidad);
        boolean seleccionable = !NO_COMPATIBLE.equals(compatibilidad) && hayDisponibilidad;

        String estado;
        if (NO_COMPATIBLE.equals(compatibilidad)) estado = NO_COMPATIBLE;
        else if (OfertaRepuestoDTO.AGOTADO.equals(disponibilidad)) estado = OfertaRepuestoDTO.AGOTADO;
        else estado = compatibilidad;

        String motivo = null;
        if (NO_COMPATIBLE.equals(compatibilidad)) {
            motivo = "No es compatible con " + CompatibilidadVehiculo.describir(vehiculo);
        } else if (OfertaRepuestoDTO.AGOTADO.equals(disponibilidad)) {
            motivo = "Agotado en esta tienda";
        } else if (OfertaRepuestoDTO.PARCIAL.equals(disponibilidad)) {
            motivo = "Solo hay " + stock + " unidad(es) en esta tienda";
        } else if (POR_CONFIRMAR.equals(compatibilidad)) {
            motivo = "Sin datos de compatibilidad: confirma que le sirve al vehículo";
        }

        Double distancia = null;
        if (tienda != null && taller != null && tienda.getLatitud() != null && tienda.getLongitud() != null
                && taller.getLatitud() != null && taller.getLongitud() != null) {
            distancia = Math.round(HaversineUtil.calcularDistanciaKm(taller.getLatitud(), taller.getLongitud(),
                    tienda.getLatitud(), tienda.getLongitud()) * 10) / 10.0;
        }

        return OfertaRepuestoDTO.builder()
                .productoId(p.getId())
                .codigo(p.getCodigo())
                .nombre(p.getNombre())
                .marca(p.getMarca())
                .referencia(p.getReferencia())
                .imagenUrl(p.getImagenUrl())
                .tiendaId(tienda != null ? tienda.getId() : p.getTiendaId())
                .tiendaNombre(tienda != null ? tienda.getNombre() : null)
                .tiendaCiudad(tienda != null ? tienda.getCiudad() : null)
                .tiendaBarrio(tienda != null ? tienda.getBarrio() : null)
                .distanciaKm(distancia)
                .precio(p.getPrecioVenta())
                .stock(stock)
                .disponibilidad(disponibilidad)
                .diasEntrega(diasEntrega)
                .compatibilidad(compatibilidad)
                .estado(estado)
                .seleccionable(seleccionable)
                .requiereConfirmacion(seleccionable && POR_CONFIRMAR.equals(compatibilidad))
                .motivo(motivo)
                .build();
    }

    /** Agrupa las ofertas de la misma pieza y ordena grupos y ofertas por recomendación. */
    static List<RepuestoBuscadoDTO> agrupar(List<Oferta> ofertas) {
        Map<String, List<Oferta>> porPieza = ofertas.stream()
                .collect(Collectors.groupingBy(o -> clavePieza(o.producto()), LinkedHashMap::new, Collectors.toList()));

        List<RepuestoBuscadoDTO> grupos = new ArrayList<>();
        porPieza.forEach((clave, lista) -> {
            List<OfertaRepuestoDTO> ordenadas = lista.stream().map(Oferta::dto).sorted(RECOMENDACION).toList();
            Producto base = lista.get(0).producto();
            grupos.add(RepuestoBuscadoDTO.builder()
                    .clave(clave)
                    .nombre(base.getNombre())
                    .marca(base.getMarca())
                    .referencia(base.getReferencia())
                    .categoria(base.getCategoria())
                    .imagenUrl(lista.stream().map(o -> o.producto().getImagenUrl())
                            .filter(Objects::nonNull).findFirst().orElse(null))
                    .stockTotal(ordenadas.stream().mapToInt(o -> o.getStock() != null ? o.getStock() : 0).sum())
                    .tiendasConStock((int) ordenadas.stream().filter(o -> o.getStock() != null && o.getStock() > 0).count())
                    .ofertas(ordenadas)
                    .build());
        });

        grupos.sort(Comparator
                .comparing((RepuestoBuscadoDTO g) -> g.getOfertas().get(0), RECOMENDACION)
                .thenComparing(RepuestoBuscadoDTO::getNombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return grupos;
    }

    /** Misma referencia = misma pieza; sin referencia se agrupa por marca + nombre. */
    static String clavePieza(Producto p) {
        String ref = normalizar(p.getReferencia());
        if (!ref.isEmpty()) return "REF:" + ref;
        return "NOM:" + normalizar(p.getMarca()) + "|" + normalizar(p.getNombre());
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String escapar(String texto) {
        return Pattern.quote(texto == null ? "" : texto);
    }
}
