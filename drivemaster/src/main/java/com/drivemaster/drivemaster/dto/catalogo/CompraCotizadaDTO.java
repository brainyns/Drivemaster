package com.drivemaster.drivemaster.dto.catalogo;

import com.drivemaster.drivemaster.model.CompraCatalogo;

import java.util.List;

/** Desglose del carrito por tienda y total, tal como se cobraría. No cobra ni reserva nada. */
public record CompraCotizadaDTO(List<CompraCatalogo.Linea> lineas, List<CompraCatalogo.EntregaTienda> entregas,
                                double subtotal, double iva, double costoEnvio, double total,
                                // Avisos de compatibilidad (no bloquean la compra)
                                List<String> avisos) {
}
