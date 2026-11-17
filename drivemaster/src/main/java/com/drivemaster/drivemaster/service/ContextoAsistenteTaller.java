package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Calificacion;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Comentario;
import com.drivemaster.drivemaster.dto.taller.InformesTallerDTO.Informes;
import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO.Inventario;
import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO.LineaEnCamino;
import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO.Pieza;
import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO.Saldo;
import com.drivemaster.drivemaster.model.Taller;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que el asistente de IA del TALLER puede ver: sus informes, su agenda de la semana, cuántas solicitudes esperan
 * respuesta y su inventario. Todo sale de los servicios que ya filtran por el taller de la sesión. Sin nombre,
 * teléfono, correo, placa ni dirección de clientes; los textos que escribió otra persona (síntomas, comentarios,
 * nombres de piezas) se ocultan si parecen instrucciones al modelo.
 */
@Service
public class ContextoAsistenteTaller {

    static final int DIAS_AGENDA = 7;
    static final int MAX_PIEZAS = 40;
    static final int MAX_TEXTO = 120;

    private final InformesTallerService informes;
    private final InventarioTallerService inventario;
    private final SolicitudServicioService solicitudes;

    public ContextoAsistenteTaller(InformesTallerService informes, InventarioTallerService inventario,
                                   SolicitudServicioService solicitudes) {
        this.informes = informes;
        this.inventario = inventario;
        this.solicitudes = solicitudes;
    }

    public record Cita(LocalDateTime fecha, String estado, String vehiculo, String sintomas, String mecanico) {}
    public record PiezaResumen(String nombre, String referencia, int stock, Integer minimo, boolean stockBajo,
                               Double costoUnitario, Double precioVenta, Double margenPct,
                               Double gananciaDespuesComision, boolean enPerdida) {}

    public Map<String, Object> contexto(Taller taller, String correo) {
        LocalDate hoy = LocalDate.now(ContextoAsistenteTienda.ZONA);
        Informes inf = informes.informes(taller.getId(), 6);
        Inventario inv = inventario.inventario(correo);

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("taller", taller.getNombre());
        c.put("hoy", hoy.toString());
        c.put("mesesConsultados", inf.mesesConsultados());
        c.put("servicios", inf.servicios());
        c.put("ingresos", inf.ingresos());
        c.put("tiempoRespuesta", inf.tiempoRespuesta());
        c.put("calificacion", calificacion(inf.calificacion()));
        c.put("serieMensual", inf.serieMensual());
        c.put("solicitudesPorResponder", solicitudes.bandejaTaller(correo).stream()
                .filter(s -> "ASIGNADA_A_TALLER".equals(s.getEstado()) && !Boolean.TRUE.equals(s.getAceptadaPorTaller())).count());
        c.put("citasProximos7Dias", citas(solicitudes.citasTaller(taller.getId(), hoy, hoy.plusDays(DIAS_AGENDA))));
        c.put("piezas", piezas(inv.piezas()));
        c.put("piezasEnInventario", inv.piezas().size());
        c.put("inventarioACosto", inv.valorACosto());
        c.put("inventarioAVenta", inv.valorAVenta());
        c.put("pedidosEnCamino", inv.enCamino().stream().map(p -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("tienda", limpio(p.tiendaNombre()));
            f.put("estado", p.estado());
            f.put("entrega", p.entrega());
            f.put("comprado", p.fechaCreacion());
            f.put("piezas", p.lineas().stream().map(ContextoAsistenteTaller::linea).toList());
            return f;
        }).toList());
        c.put("saldoPendienteConDriveMaster", inv.saldos().stream().mapToDouble(Saldo::monto).sum());
        return c;
    }

    /** Solo lo que sirve para planear el día: cuándo, qué carro, qué le pasa y quién lo atiende. */
    static List<Cita> citas(List<SolicitudServicioDTO> lista) {
        return lista.stream()
                .filter(s -> s.getFechaRevision() != null)
                .sorted(Comparator.comparing(SolicitudServicioDTO::getFechaRevision))
                .map(s -> new Cita(s.getFechaRevision(), s.getEstado(), limpio(s.getVehiculoDescripcion()),
                        limpio(s.getSintomas()), limpio(s.getMecanicoNombre())))
                .toList();
    }

    /** Primero lo que pide atención (stock bajo, en pérdida), luego lo de menos stock. Sin lotes ni imágenes. */
    static List<PiezaResumen> piezas(List<Pieza> piezas) {
        return piezas.stream()
                .sorted(Comparator.comparing((Pieza p) -> !p.stockBajo())
                        .thenComparing(p -> !p.enPerdida())
                        .thenComparingInt(Pieza::stock))
                .limit(MAX_PIEZAS)
                .map(p -> new PiezaResumen(Objects.requireNonNullElse(limpio(p.nombre()), "Pieza (nombre oculto)"),
                        limpio(p.referencia()), p.stock(), p.stockMinimo(), p.stockBajo(), p.costoUnitario(),
                        p.precioVenta(), p.margenPct(), p.gananciaDespuesComision(), p.enPerdida()))
                .toList();
    }

    /** Los comentarios los escribió el cliente: el que parece una instrucción al modelo no viaja. */
    static Calificacion calificacion(Calificacion cal) {
        if (cal == null || cal.recientes() == null) return cal;
        return new Calificacion(cal.total(), cal.promedio(), cal.distribucion(), cal.recientes().stream()
                .map(k -> new Comentario(k.calificacion(), limpio(k.comentario()), k.fecha())).toList());
    }

    private static String linea(LineaEnCamino l) {
        return Objects.requireNonNullElse(limpio(l.nombre()), "Pieza") + " x" + l.cantidad();
    }

    /** Texto de otra persona, recortado; null si está vacío o parece una instrucción al modelo. */
    static String limpio(String s) {
        if (s == null || s.isBlank() || PedidosChatService.SOSPECHOSO.matcher(s).find()) return null;
        String t = s.strip();
        return t.length() > MAX_TEXTO ? t.substring(0, MAX_TEXTO) : t;
    }
}
