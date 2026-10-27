package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vistas de detalle del SUPERADMIN (pedido, cliente y negocio), de solo lectura. El correo va enmascarado como en las
 * listas; el teléfono y la dirección de envío sí, porque los necesita para resolver un problema con el pedido.
 */
public final class DetallePlataformaDTO {

    private DetallePlataformaDTO() {
    }

    /** Un negocio o una persona, con lo justo para reconocerlo y abrir su propio detalle. */
    public record Parte(String id, String nombre, String correo, String telefono, String ciudad) {}

    /** Un paso de la línea de tiempo del pedido. */
    public record Evento(String etiqueta, LocalDateTime fecha, String detalle) {}

    public record Linea(String producto, String marca, String referencia, String disponibilidad, int cantidad,
                        double precioUnitario, double subtotal) {}

    /** El pago en Wompi: transaccion es el id de la transacción en Wompi. */
    public record Pago(Long id, String referencia, String transaccion, String metodo, String estado, double monto,
                       LocalDateTime fechaPago, String concepto, int pedidosDelPago) {}

    /** Una línea del reparto. delPago: la comisión se registra sobre todo el pago (puede cubrir varios pedidos). */
    public record LineaReparto(String beneficiarioTipo, String beneficiario, double monto, String estado,
                               String modoLiquidacion, LocalDateTime fechaLiquidacion, String nota, boolean delPago) {}

    public record Reembolso(double monto, String estado, String motivo, LocalDateTime fecha, LocalDateTime fechaRealizado) {}

    /** ENVIO (a dónde y a quién) o RETIRO (en qué tienda y cuándo lo recogió); los de taller van al taller. */
    public record Entrega(String tipo, String receptor, String telefono, String direccion, String ciudad,
                          String indicaciones, double costoEnvio, String horarioTienda, LocalDateTime listoDesde,
                          LocalDateTime retiradoEl, Long diasSinRetirar, Integer recordatorios) {}

    public record Problema(String detalle, LocalDateTime fecha, String resolucion, String motivo, LocalDateTime fechaResolucion) {}

    public record Pedido(String id, String origen, String estado, LocalDateTime fechaPago, LocalDateTime plazoDespacho,
                         String semaforoDespacho, String semaforoRetiro, Parte tienda, Parte cliente, Parte taller,
                         String vehiculo, List<Linea> lineas, double subtotal, double iva, double total,
                         List<Evento> lineaDeTiempo, Pago pago, List<LineaReparto> reparto, List<Reembolso> reembolsos,
                         Entrega entrega, Problema problema) {}

    // ─── Cliente ────────────────────────────────────────────────────────────

    public record Vehiculo(String id, String descripcion, String placa, Integer kilometraje) {}

    public record PedidoResumen(String id, String negocio, String estado, double total, LocalDateTime fecha) {}

    /** Una solicitud de diagnóstico o servicio (orden de servicio) con su taller. */
    public record SolicitudResumen(String id, String taller, String vehiculo, String estado, double total, LocalDateTime fecha) {}

    public record Cliente(String id, String nombre, String correo, String telefono, String identificacion, String ciudad,
                          String direccion, String proveedor, boolean activo, LocalDateTime fechaCreacion,
                          LocalDateTime ultimoLogin, List<Vehiculo> vehiculos, List<PedidoResumen> pedidos,
                          long totalPedidos, double totalComprado, List<SolicitudResumen> solicitudes, long totalSolicitudes) {}

    // ─── Negocio ────────────────────────────────────────────────────────────

    public record ProductoResumen(String id, String nombre, String marca, double precio, Integer stock, String tipo, boolean activo) {}

    public record Mecanico(String nombre, String especialidad, boolean activo) {}

    /**
     * Totales del negocio: vendido es lo que pagaron los clientes por sus pedidos u órdenes; recibido, en espera y por
     * liquidar son su parte del reparto (sin la comisión).
     */
    public record Totales(long pedidos, double vendido, double liquidado, double porLiquidar, double enEspera) {}

    public record Negocio(String tipo, String id, String nombre, String nit, String estadoAprobacion, boolean activo,
                          String motivoRechazo, LocalDateTime fechaRegistro, LocalDateTime fechaAprobacion, Parte cuenta,
                          String telefono, String ciudad, String barrio, String direccion, Double latitud, Double longitud,
                          String horario, String logoUrl, Double calificacion, Integer calificaciones, Double costoEnvio,
                          List<String> especialidades, Double tarifaHora, Double precioManoObra, List<Mecanico> mecanicos,
                          long totalProductos, long productosActivos, List<ProductoResumen> productos,
                          List<PedidoResumen> pedidos, List<SolicitudResumen> ordenes, Totales totales) {}
}
