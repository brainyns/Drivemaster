package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cotización para mostrar. Dos vistas:
 *  - completa(): cliente dueño, taller asignado y ADMIN;
 *  - publica(): la página del enlace del correo. Solo el detalle de la cotización,
 *    sin identificadores ni datos personales.
 * Nunca incluye el hash del token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CotizacionDTO {
    private String id;
    private String ordenId;
    private Integer version;
    private String estado;
    private boolean puedeResponder;

    private String tallerNombre;
    private String vehiculoDescripcion;
    private List<CotizacionItem> items;

    private Double subtotalRepuestos;
    private Double subtotalManoObra;
    private Double subtotal;
    private Double ivaPct;
    private Double ivaRepuestos;
    private Double ivaManoObra;
    private Double iva;
    private Double total;
    private Double anticipoDiagnostico;
    private Double totalAPagar;
    // Solo en la vista completa
    private Double comisionInformativa;

    private LocalDateTime vigenciaHasta;
    private Integer tiempoEstimadoHoras;
    private Integer diasEntregaRepuestos;
    private String observaciones;
    private LocalDateTime fechaEmision;
    private LocalDateTime fechaRespuesta;
    private String motivoRespuesta;
    private PrediccionAprobacionDTO prediccionAprobacion;

    public static CotizacionDTO completa(Cotizacion c) {
        return base(c, c.getItems())
                .id(c.getId())
                .ordenId(c.getOrdenId())
                .comisionInformativa(c.getComisionInformativa())
                .motivoRespuesta(c.getMotivoRespuesta())
                .build();
    }

    public static CotizacionDTO publica(Cotizacion c) {
        List<CotizacionItem> items = c.getItems().stream().map(i -> CotizacionItem.builder()
                .tipo(i.getTipo())
                .descripcion(i.getDescripcion())
                .productoNombre(i.getProductoNombre())
                .marca(i.getMarca())
                .referencia(i.getReferencia())
                .tiendaNombre(i.getTiendaNombre())
                .disponibilidad(i.getDisponibilidad())
                .diasEntrega(i.getDiasEntrega())
                .modalidad(i.getModalidad())
                .horas(i.getHoras())
                .cantidad(i.getCantidad())
                .precioUnitario(i.getPrecioUnitario())
                .subtotal(i.getSubtotal())
                .build()).toList();
        return base(c, items).build();
    }

    /** Se puede responder: emitida, vigente y con el token sin usar. */
    public static boolean respondible(Cotizacion c) {
        return Cotizacion.EMITIDA.equals(c.getEstado())
                && !Boolean.TRUE.equals(c.getTokenUsado())
                && c.getVigenciaHasta() != null && c.getVigenciaHasta().isAfter(LocalDateTime.now());
    }

    private static CotizacionDTOBuilder base(Cotizacion c, List<CotizacionItem> items) {
        return CotizacionDTO.builder()
                .version(c.getVersion())
                .estado(c.getEstado())
                .puedeResponder(respondible(c))
                .tallerNombre(c.getTallerNombre())
                .vehiculoDescripcion(c.getVehiculoDescripcion())
                .items(items)
                .subtotalRepuestos(c.getSubtotalRepuestos())
                .subtotalManoObra(c.getSubtotalManoObra())
                .subtotal(c.getSubtotal())
                .ivaPct(c.getIvaPct())
                .ivaRepuestos(c.getIvaRepuestos())
                .ivaManoObra(c.getIvaManoObra())
                .iva(c.getIva())
                .total(c.getTotal())
                .anticipoDiagnostico(c.getAnticipoDiagnostico())
                .totalAPagar(c.getTotalAPagar())
                .vigenciaHasta(c.getVigenciaHasta())
                .tiempoEstimadoHoras(c.getTiempoEstimadoHoras())
                .diasEntregaRepuestos(c.getDiasEntregaRepuestos())
                .observaciones(c.getObservaciones())
                .fechaEmision(c.getFechaEmision())
                .fechaRespuesta(c.getFechaRespuesta());
    }
}
