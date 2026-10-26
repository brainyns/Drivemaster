package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Anteriores;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Calificacion;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Comentario;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Informes;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Ingresos;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Monto;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Servicios;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.TiempoRespuesta;
import com.drivemaster.drivemaster.model.EstadoOrden;
import com.drivemaster.drivemaster.model.EventoOrden;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Informes del TALLER. Todo se filtra por el taller dueño (el que sale de la sesión): un taller nunca ve datos de otro.
 * Los meses se cuentan en hora de Colombia (America/Bogota). Todas las cifras usan el mismo periodo (desde el día 1 del
 * primer mes): servicios y tiempo de respuesta por la creación de la solicitud, ingresos por la fecha de cada pago y la
 * calificación por la fecha en que el cliente calificó.
 */
@Service
public class InformesTallerService {

    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int MESES_POR_DEFECTO = 6;
    static final int MESES_MAXIMO = 24;
    static final int COMENTARIOS_RECIENTES = 5;
    /** Orden en que se muestran los estados: del primer aviso a la entrega, y al final los cierres sin trabajo. */
    static final List<String> ESTADOS = List.of(
            EstadoOrden.ASIGNADA_A_TALLER, EstadoOrden.EN_REVISION, EstadoOrden.DIAGNOSTICADA, EstadoOrden.COTIZADA,
            EstadoOrden.APROBADA, EstadoOrden.PAGADA, EstadoOrden.REPUESTOS_PEDIDOS, EstadoOrden.REPUESTOS_RECIBIDOS,
            EstadoOrden.EN_INSTALACION, EstadoOrden.LISTA, EstadoOrden.ENTREGADA, EstadoOrden.CANCELADA,
            EstadoOrden.RECHAZADA, EstadoOrden.REASIGNADA);
    static final String DIAGNOSTICO = "DIAGNOSTICO";
    static final String COTIZACION = "COTIZACION";

    private final OrdenServicioRepository ordenRepository;
    private final RepartoPagoRepository repartoRepository;

    public InformesTallerService(OrdenServicioRepository ordenRepository, RepartoPagoRepository repartoRepository) {
        this.ordenRepository = ordenRepository;
        this.repartoRepository = repartoRepository;
    }

    public Informes informes(String tallerId, int meses) {
        return informes(tallerId, meses, YearMonth.now(ZONA), LocalDateTime.now());
    }

    Informes informes(String tallerId, int meses, YearMonth hasta, LocalDateTime ahora) {
        if (tallerId == null || tallerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Falta el taller dueño");
        }
        int m = meses(meses);
        // Inicio del primer mes en hora de Colombia, expresado en la hora del servidor (así se guardan las fechas)
        LocalDate inicio = hasta.minusMonths(m - 1L).atDay(1);
        LocalDateTime desde = inicio.atStartOfDay(ZONA).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        List<OrdenServicio> todas = ordenRepository.findByTallerIdOrderByFechaCreacionDesc(tallerId);
        List<OrdenServicio> marketplace = todas.stream()
                .filter(o -> EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .toList();
        // Cada solicitud cuenta una vez, en su estado actual, si se creó en el periodo
        List<OrdenServicio> delPeriodo = marketplace.stream()
                .filter(o -> enPeriodo(o.getFechaCreacion(), desde))
                .toList();

        return new Informes(ahora, m, inicio, servicios(delPeriodo), ingresos(tallerId, todas, desde),
                tiempoRespuesta(tallerId, delPeriodo), calificacion(marketplace, desde));
    }

    // ─── Servicios por estado ────────────────────────────────────────────────

    private Servicios servicios(List<OrdenServicio> ordenes) {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (String estado : ESTADOS) porEstado.put(estado, 0L);
        for (OrdenServicio o : ordenes) {
            if (o.getEstado() != null) porEstado.merge(o.getEstado(), 1L, Long::sum);
        }
        return new Servicios(porEstado, ordenes.size());
    }

    // ─── Ingresos del periodo: salen de las líneas de reparto del taller ─────
    // Lo liquidado cuenta por la fecha en que se pagó; lo pendiente y lo anulado, por la fecha del pago del cliente.

    private Ingresos ingresos(String tallerId, List<OrdenServicio> todas, LocalDateTime desde) {
        double[] mano = new double[4];  // liquidado, líneas liquidadas, pendiente, líneas pendientes
        double[] diag = new double[4];
        double anulado = 0;
        for (Object[] fila : repartoRepository.resumenPorConceptoYEstadoDesde(RepartoPago.TALLER, tallerId, desde)) {
            String concepto = String.valueOf(fila[0]);
            String estado = String.valueOf(fila[1]);
            long lineas = fila[2] instanceof Number c ? c.longValue() : 0;
            double monto = fila[3] instanceof Number s ? s.doubleValue() : 0;
            double[] destino = COTIZACION.equals(concepto) ? mano : DIAGNOSTICO.equals(concepto) ? diag : null;
            if (RepartoPago.ANULADO.equals(estado)) {
                anulado += monto;
            } else if (destino != null) {
                int base = RepartoPago.LIQUIDADO.equals(estado) ? 0 : 2;
                destino[base] += monto;
                destino[base + 1] += lineas;
            }
        }
        // Órdenes del flujo anterior (sin flujo marketplace) finalizadas en el periodo: su mano de obra queda aparte,
        // con su propia nota en pantalla
        List<OrdenServicio> anteriores = todas.stream()
                .filter(o -> !EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()) && "FINALIZADA".equals(o.getEstado()))
                .filter(o -> enPeriodo(o.getFechaFinalizacion() != null ? o.getFechaFinalizacion() : o.getFechaCreacion(), desde))
                .toList();
        double anterioresMano = anteriores.stream().mapToDouble(o -> o.getPrecioManoObra() == null ? 0 : o.getPrecioManoObra()).sum();
        return new Ingresos(monto(mano), monto(diag), anulado, new Anteriores(anteriores.size(), anterioresMano));
    }

    private static Monto monto(double[] v) {
        return new Monto(v[0], (long) v[1], v[2], (long) v[3]);
    }

    // ─── Tiempo de respuesta: asignación → primera respuesta del taller, de la línea de tiempo ──────────

    private TiempoRespuesta tiempoRespuesta(String tallerId, List<OrdenServicio> ordenes) {
        List<Double> minutos = new ArrayList<>();
        for (OrdenServicio o : ordenes) {
            Double m = minutosDeRespuesta(tallerId, o.getEventos());
            if (m != null) minutos.add(m);
        }
        if (minutos.isEmpty()) return new TiempoRespuesta(0, null, null);
        minutos.sort(Comparator.naturalOrder());
        double promedio = minutos.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        int n = minutos.size();
        double mediana = n % 2 == 1 ? minutos.get(n / 2) : (minutos.get(n / 2 - 1) + minutos.get(n / 2)) / 2;
        return new TiempoRespuesta(n, redondear(promedio), redondear(mediana));
    }

    /**
     * Minutos desde que la solicitud se asignó a este taller hasta que el taller respondió: su primer evento después
     * de la asignación (aceptar y agendar la cita, o rechazar). Con reasignaciones cuenta la última asignación anterior
     * a esa respuesta. No usa EN_REVISION: con pago de diagnóstico ese paso llega cuando paga el cliente, no cuando
     * responde el taller. null si el taller aún no responde o falta la asignación.
     */
    static Double minutosDeRespuesta(String tallerId, List<EventoOrden> eventos) {
        if (eventos == null) return null;
        LocalDateTime respuesta = eventos.stream()
                .filter(e -> e.getFecha() != null && esDelTaller(tallerId, e))
                .map(EventoOrden::getFecha).min(Comparator.naturalOrder()).orElse(null);
        if (respuesta == null) return null;
        // La aceptación con pago de diagnóstico también queda en ASIGNADA_A_TALLER, pero la hace el taller: no es asignación
        LocalDateTime asignada = eventos.stream()
                .filter(e -> EstadoOrden.ASIGNADA_A_TALLER.equals(e.getEstado()) && e.getFecha() != null
                        && !EstadoOrden.TALLER.equals(e.getActorRol()) && !e.getFecha().isAfter(respuesta))
                .map(EventoOrden::getFecha).max(Comparator.naturalOrder()).orElse(null);
        if (asignada == null) return null;
        return Duration.between(asignada, respuesta).toSeconds() / 60.0;
    }

    /** Evento hecho por este taller (el actor TALLER guarda el id del taller; otro taller que rechazó antes no cuenta). */
    private static boolean esDelTaller(String tallerId, EventoOrden e) {
        return EstadoOrden.TALLER.equals(e.getActorRol()) && (e.getActorId() == null || e.getActorId().equals(tallerId));
    }

    // ─── Calificación: solo con lo que el cliente calificó en el periodo, sin promedios inventados ───────────

    private Calificacion calificacion(List<OrdenServicio> ordenes, LocalDateTime desde) {
        List<OrdenServicio> calificadas = ordenes.stream()
                .filter(o -> o.getCalificacionTaller() != null && o.getCalificacionTaller() >= 1 && o.getCalificacionTaller() <= 5)
                .filter(o -> enPeriodo(fechaCalificacion(o), desde))
                .toList();
        Map<Integer, Long> distribucion = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) distribucion.put(i, 0L);
        calificadas.forEach(o -> distribucion.merge(o.getCalificacionTaller(), 1L, Long::sum));
        Double promedio = calificadas.isEmpty() ? null
                : redondear(calificadas.stream().mapToInt(OrdenServicio::getCalificacionTaller).average().orElse(0));
        List<Comentario> recientes = calificadas.stream()
                .filter(o -> o.getComentarioCalificacion() != null && !o.getComentarioCalificacion().isBlank())
                .sorted(Comparator.comparing(InformesTallerService::fechaCalificacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(COMENTARIOS_RECIENTES)
                .map(o -> new Comentario(o.getCalificacionTaller(), o.getComentarioCalificacion().trim(), fechaCalificacion(o)))
                .toList();
        return new Calificacion(calificadas.size(), promedio, distribucion, recientes);
    }

    private static LocalDateTime fechaCalificacion(OrdenServicio o) {
        return o.getFechaRecepcionCliente() != null ? o.getFechaRecepcionCliente() : o.getFechaCreacion();
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private static boolean enPeriodo(LocalDateTime fecha, LocalDateTime desde) {
        return fecha != null && !fecha.isBefore(desde);
    }

    static int meses(int pedido) {
        return pedido <= 0 ? MESES_POR_DEFECTO : Math.min(pedido, MESES_MAXIMO);
    }

    private static double redondear(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
