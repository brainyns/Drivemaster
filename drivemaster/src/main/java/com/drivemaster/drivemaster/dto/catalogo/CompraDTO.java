package com.drivemaster.drivemaster.dto.catalogo;

import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.PedidoTienda;

import java.time.LocalDateTime;
import java.util.List;

/** Una compra del catálogo vista por su cliente, con el estado de cada pedido por tienda. */
public record CompraDTO(String id, String estado, String motivoNoAplicada, double subtotal, double iva,
                        double costoEnvio, double total, LocalDateTime fechaCreacion, LocalDateTime fechaPago,
                        List<CompraCatalogo.Linea> lineas, List<PedidoCompraDTO> pedidos) {

    /** Pedido a una tienda dentro de la compra. El código de retiro NUNCA viene aquí. */
    public record PedidoCompraDTO(String id, String tiendaId, String tiendaNombre, String entrega, String estado,
                                  double total, double costoEnvio, LocalDateTime plazoDespachoHasta,
                                  String tiendaDireccion, String tiendaCiudad, String tiendaHorario,
                                  String envioDireccion, String envioCiudad,
                                  LocalDateTime fechaListoRetiro, LocalDateTime fechaRetirado, boolean codigoUsado,
                                  // Envío: despacho, guía, confirmación y problema reportado
                                  LocalDateTime fechaDespacho, String notaDespacho, LocalDateTime autoconfirmaDespuesDe,
                                  LocalDateTime fechaRecibidoCliente, boolean recepcionAutomatica,
                                  boolean problemaReportado, String resolucionProblema) {

        public static PedidoCompraDTO de(PedidoTienda p) {
            return new PedidoCompraDTO(p.getId(), p.getTiendaId(), p.getTiendaNombre(), p.getEntrega(), p.getEstado(),
                    p.getTotal() != null ? p.getTotal() : 0, p.getCostoEnvio() != null ? p.getCostoEnvio() : 0,
                    p.getPlazoDespachoHasta(), p.getTiendaDireccion(), p.getTiendaCiudad(), p.getTiendaHorario(),
                    p.getEnvioDireccion(), p.getEnvioCiudad(), p.getFechaListoRetiro(), p.getFechaRetirado(),
                    Boolean.TRUE.equals(p.getCodigoRetiroUsado()),
                    p.getFechaDespacho(), p.getNotaDespacho(), p.getAutoconfirmaDespuesDe(), p.getFechaRecibidoCliente(),
                    Boolean.TRUE.equals(p.getRecepcionAutomatica()), Boolean.TRUE.equals(p.getProblemaReportado()),
                    p.getResolucionProblema());
        }
    }
}
