package com.drivemaster.drivemaster.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "compras")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Compra {

    @Id
    private String id;

    private String proveedorId;
    private LocalDateTime fecha;

    private List<DetalleCompra> productos;

    private Double total;
    private String usuarioId;

    // Tienda dueña de la compra. null = registro anterior a que las tiendas llevaran sus propias compras.
    private String tiendaId;

    // RECIBIDA | PENDIENTE | CANCELADA. null = RECIBIDA: las compras anteriores a los estados sumaron el stock al
    // registrarse. El stock se suma una sola vez, al quedar RECIBIDA; una cancelada no lo toca.
    public static final String RECIBIDA = "RECIBIDA";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String CANCELADA = "CANCELADA";
    private String estado;
    private LocalDateTime fechaRecepcion;
    private LocalDateTime fechaCancelacion;
    private String motivoCancelacion;

    // Opcionales: cómo se paga (EFECTIVO, TRANSFERENCIA, TARJETA o CREDITO) y el número de la factura del proveedor
    private String metodoPago;
    private String numeroFactura;

    /** El estado con las compras anteriores a los estados contadas como recibidas. */
    public String estadoEfectivo() {
        return estado == null ? RECIBIDA : estado;
    }
}
