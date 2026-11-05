package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;

import com.drivemaster.drivemaster.dto.comun.Pagina;

/**
 * Consultas de solo lectura del panel de la plataforma (SUPERADMIN). Entregan lo justo para monitorear:
 * no incluyen teléfonos, direcciones, documentos ni datos administrativos de las cuentas.
 */
public final class ConsultasPlataformaDTO {

    private ConsultasPlataformaDTO() {
    }

    // La página de cada tabla usa el formato común (dto.comun.Pagina), con el total de páginas

    public record TiendaOpcion(String id, String nombre) {}

    public record ProductoFila(String id, String codigo, String nombre, String categoria, String marca, String tipo,
                               String tiendaId, String tienda, Integer stock, Double precio, boolean activo,
                               String imagenUrl, LocalDateTime fechaIngreso, boolean tiendaActiva) {}

    /** Productos de la página y las tiendas para el filtro. */
    public record ProductosRespuesta(Pagina<ProductoFila> productos, List<TiendaOpcion> tiendas) {}

    /** Correo enmascarado: basta para distinguir a la persona sin exponerlo completo. */
    public record ClienteFila(String id, String nombre, String correo, String ciudad, boolean activo,
                              LocalDateTime registrado, long compras, long servicios) {}

    public record ClientesRespuesta(Pagina<ClienteFila> clientes, List<String> ciudades) {}

    /**
     * Una fila del monitor "Pedidos y envíos". Sin datos del cliente (ni nombre, teléfono, dirección ni montos):
     * para vigilar el avance basta saber la tienda, la ciudad de destino y cuántos días lleva.
     * semaforoDespacho y semaforoRetiro: VERDE, AMARILLO o ROJO (SemaforoPedido), o null si no aplican.
     */
    public record PedidoFila(String id, String origen, String estado, String tiendaId, String tienda, String entrega,
                             String destino, int articulos, long diasDesdePago, LocalDateTime fechaPago,
                             LocalDateTime plazoDespacho, boolean atrasado, boolean problemaAbierto,
                             String semaforoDespacho, String semaforoRetiro, Long diasSinRetirar) {}

    /**
     * porEstado: todos los pedidos por estado (para los contadores); atrasados, por vencer, sin retirar y problemas,
     * también del total.
     */
    public record PedidosRespuesta(Pagina<PedidoFila> pedidos, java.util.Map<String, Long> porEstado, long atrasados,
                                   long problemasAbiertos, List<TiendaOpcion> tiendas, long porVencer, long sinRetirar) {}

    /** Panel operativo mínimo del ADMIN: solo lo que le toca atender. */
    public record Operacion(LocalDateTime generadoEn, long tiendasPorAprobar, long talleresPorAprobar,
                            long problemasEnvioAbiertos, long despachosAtrasados) {}
}
