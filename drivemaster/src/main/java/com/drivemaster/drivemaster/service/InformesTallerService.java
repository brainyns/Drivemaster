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
 * Los meses se cuentan en hora de Colombia (America/Bogota).
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
        LocalDateTime desde = hasta.minusMonths(m - 1L).atDay(1).atStartOfDay(ZONA)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        List<OrdenServicio> todas = ordenRepository.findByTallerIdOrderByFechaCreacionDesc(tallerId);
        List<OrdenServicio> marketplace = todas.stream()
                .filter(o -> EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()))
                .toList();
        List<OrdenServicio> delPeriodo = marketplace.stream()
                .filter(o -> o.getFechaCreacion() != null && !o.getFechaCreacion().isBefore(desde))
                .toList();

        return new Informes(ahora, m, servicios(delPeriodo), ingresos(tallerId, todas),
                tiempoRespuesta(delPeriodo), calificacion(marketplace));
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

    // ─── Ingresos: salen de las líneas de reparto del taller ─────────────────

    private Ingresos ingresos(String tallerId, List<OrdenServicio> todas) {
        double[] mano = new double[4];  // liquidado, líneas liquidadas, pendiente, líneas pendientes
        double[] diag = new double[4];
        double anulado = 0;
        for (Object[] fila : repartoRepository.resumenPorConceptoYEstado(RepartoPago.TALLER, tallerId)) {
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
        // Órdenes del flujo anterior (sin flujo marketplace): su mano de obra queda aparte, con su propia nota en pantalla
        List<OrdenServicio> anteriores = todas.stream()
                .filter(o -> !EstadoOrden.FLUJO_MARKETPLACE.equals(o.getFlujo()) && "FINALIZADA".equals(o.getEstado()))
                .toList();
        double anterioresMano = anteriores.stream().mapToDouble(o -> o.getPrecioManoObra() == null ? 0 : o.getPrecioManoObra()).sum();
        return new Ingresos(monto(mano), monto(diag), anulado, new Anteriores(anteriores.size(), anterioresMano));
    }

    private static Monto monto(double[] v) {
        return new Monto(v[0], (long) v[1], v[2], (long) v[3]);
    }

    // ─── Tiempo de respuesta: ASIGNADA_A_TALLER → EN_REVISION, de la línea de tiempo ──────────

    private TiempoRespuesta tiempoRespuesta(List<OrdenServicio> ordenes) {
        List<Double> horas = new ArrayList<>();
        for (OrdenServicio o : ordenes) {
            Double h = horasDeRespuesta(o.getEventos());
            if (h != null) horas.add(h);
        }
        if (horas.isEmpty()) return new TiempoRespuesta(0, null, null);
        horas.sort(Comparator.naturalOrder());
        double promedio = horas.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        int n = horas.size();
        double mediana = n % 2 == 1 ? horas.get(n / 2) : (horas.get(n / 2 - 1) + horas.get(n / 2)) / 2;
        return new TiempoRespuesta(n, redondear(promedio), redondear(mediana));
    }

    /** Horas desde la última asignación al taller anterior a la primera revisión; null si falta alguno de los dos eventos. */
    static Double horasDeRespuesta(List<EventoOrden> eventos) {
        if (eventos == null) return null;
        LocalDateTime revision = eventos.stream()
                .filter(e -> EstadoOrden.EN_REVISION.equals(e.getEstado()) && e.getFecha() != null)
                .map(EventoOrden::getFecha).min(Comparator.naturalOrder()).orElse(null);
        if (revision == null) return null;
        LocalDateTime asignada = eventos.stream()
                .filter(e -> EstadoOrden.ASIGNADA_A_TALLER.equals(e.getEstado()) && e.getFecha() != null
                        && !e.getFecha().isAfter(revision))
                .map(EventoOrden::getFecha).max(Comparator.naturalOrder()).orElse(null);
        if (asignada == null) return null;
        return Duration.between(asignada, revision).toMinutes() / 60.0;
    }

    // ─── Calificación: solo con lo que el cliente calificó, sin promedios inventados ───────────

    private Calificacion calificacion(List<OrdenServicio> ordenes) {
        List<OrdenServicio> calificadas = ordenes.stream()
                .filter(o -> o.getCalificacionTaller() != null && o.getCalificacionTaller() >= 1 && o.getCalificacionTaller() <= 5)
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

    static int meses(int pedido) {
        return pedido <= 0 ? MESES_POR_DEFECTO : Math.min(pedido, MESES_MAXIMO);
    }

    private static double redondear(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
