package com.drivemaster.drivemaster.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "proveedores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Proveedor {

    @Id
    private String id;

    private String nombre;
    private String nit;
    private String telefono;
    private String correo;

    // Si al correo le llega el PDF de cada compra. null = sí (los proveedores anteriores al interruptor lo recibían).
    // Sin correo no se envía nada, valga lo que valga.
    private Boolean enviarPdfCompras;

    // Opcionales: a quién se le habla, dónde queda y cómo se le paga (CONTADO o CREDITO a diasCredito días)
    private String contacto;
    private String direccion;
    private String condicionPago;
    private Integer diasCredito;

    // Tienda dueña del proveedor. null = registro anterior a que las tiendas llevaran sus propios proveedores.
    private String tiendaId;
}
