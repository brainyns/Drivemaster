package com.drivemaster.drivemaster.dto.plataforma;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Reportes financieros de la plataforma (solo SUPERADMIN). Se calculan sobre los pedidos de las tiendas, los pagos
 * del marketplace y las órdenes de servicio; nunca sobre las ventas del flujo anterior. Sin datos personales de
 * clientes más allá de un nombre abreviado.
 */
public final class ReportesPlataformaDTO {

    private ReportesPlataformaDTO() {
    }

    public record MesVenta(String mes, long pedidos, double ventas) {}

    /** porMes va alineado con la lista de meses del reporte. */
    public record TiendaVentas(String tiendaId, String tienda, List<MesVenta> porMes, long pedidos, double ventas) {}

    /**
     * Ventas de las tiendas por mes: lo que vendieron las tiendas (con IVA y envío), NO ingreso de DriveMaster.
     * Cuenta los pedidos que no quedaron sin despachar ni sin entregar.
     */
    public record VentasPorTienda(List<String> meses, List<TiendaVentas> tiendas, List<MesVenta> totalesPorMes) {}

    public record ProductoMasVendido(String productoId, String nombre, String tienda, long unidades, double ventas) {}

    public record ProductosMasVendidos(int meses, List<ProductoMasVendido> productos) {}

    /** El nombre se abrevia ("Ana P."): para ver quién compra más no hace falta más. */
    public record ClienteCompras(String clienteId, String cliente, String ciudad, long compras, double total) {}

    public record ClientesConMasCompras(int meses, List<ClienteCompras> clientes) {}

    public record TallerServicios(String tallerId, String taller, long total, long entregadas, long enCurso,
                                  long canceladas, double movidoAlTaller) {}

    /** movidoAlTaller: lo repartido al taller por sus pagos (sin contar lo anulado); no es ingreso de DriveMaster. */
    public record ServiciosPorTaller(List<TallerServicios> talleres) {}

    public record MesComision(String mes, double registrada, double anulada, double neta) {}

    /**
     * Comisión registrada neta = comisión de cada pago aprobado menos la anulada por reembolsos. Es informativa:
     * no es dinero cobrado.
     */
    public record ComisionNeta(LocalDateTime generadoEn, List<MesComision> meses, double registrada, double anulada,
                               double neta, Map<String, Double> netaPorConcepto) {}

    // ─── Usuarios ────────────────────────────────────────────────────────────

    /** Correo enmascarado en la lista; los datos completos solo se tocan al editar. */
    public record UsuarioFila(String id, String nombre, String correo, String rol, boolean activo, boolean bloqueado,
                              LocalDateTime creado, LocalDateTime ultimoAcceso) {}

    public record UsuariosRespuesta(com.drivemaster.drivemaster.dto.comun.Pagina<UsuarioFila> usuarios, Map<String, Long> porRol) {}
}
