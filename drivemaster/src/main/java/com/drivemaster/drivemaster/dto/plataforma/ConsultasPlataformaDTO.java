package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas de solo lectura del panel de la plataforma (SUPERADMIN). Entregan lo justo para monitorear:
 * no incluyen teléfonos, direcciones, documentos ni datos administrativos de las cuentas.
 */
public final class ConsultasPlataformaDTO {

    private ConsultasPlataformaDTO() {
    }

    public record Pagina<T>(List<T> items, long total, int pagina, int tamano) {}

    public record TiendaOpcion(String id, String nombre) {}

    public record ProductoFila(String id, String codigo, String nombre, String categoria, String marca, String tipo,
                               String tiendaId, String tienda, Integer stock, Double precio, boolean activo,
                               String imagenUrl, LocalDateTime fechaIngreso) {}

    /** Productos de la página y las tiendas para el filtro. */
    public record ProductosRespuesta(Pagina<ProductoFila> productos, List<TiendaOpcion> tiendas) {}

    /** Correo enmascarado: basta para distinguir a la persona sin exponerlo completo. */
    public record ClienteFila(String id, String nombre, String correo, String ciudad, boolean activo,
                              LocalDateTime registrado, long compras, long servicios) {}

    public record ClientesRespuesta(Pagina<ClienteFila> clientes, List<String> ciudades) {}

    /** Panel operativo mínimo del ADMIN: solo lo que le toca atender. */
    public record Operacion(LocalDateTime generadoEn, long tiendasPorAprobar, long talleresPorAprobar,
                            long problemasEnvioAbiertos, long despachosAtrasados) {}
}
