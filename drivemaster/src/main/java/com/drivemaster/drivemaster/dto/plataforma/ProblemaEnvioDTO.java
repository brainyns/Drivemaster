package com.drivemaster.drivemaster.dto.plataforma;

import com.drivemaster.drivemaster.model.PedidoTienda;

import java.time.LocalDateTime;

/**
 * Lo mínimo que el ADMIN necesita para resolver un problema de envío. No lleva el hash del código de retiro,
 * ni teléfono, receptor, dirección o identificadores del cliente: la ciudad basta para ubicar el pedido.
 */
public record ProblemaEnvioDTO(
        String id, String tiendaNombre, String origen, String estado, String entrega, Double total,
        String envioCiudad, LocalDateTime fechaDespacho, String notaDespacho,
        String problemaDetalle, LocalDateTime fechaProblema,
        String resolucionProblema, LocalDateTime fechaResolucionProblema, String motivoResolucion, String resueltoPor) {

    public static ProblemaEnvioDTO de(PedidoTienda p) {
        if (p == null) return null;
        return new ProblemaEnvioDTO(p.getId(), p.getTiendaNombre(), p.esCatalogo() ? PedidoTienda.ORIGEN_CATALOGO : PedidoTienda.ORIGEN_TALLER,
                p.getEstado(), p.getEntrega(), p.getTotal(), p.getEnvioCiudad(), p.getFechaDespacho(), p.getNotaDespacho(),
                p.getProblemaDetalle(), p.getFechaProblema(), p.getResolucionProblema(), p.getFechaResolucionProblema(),
                p.getMotivoResolucion(), p.getResueltoPor());
    }
}
