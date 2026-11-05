package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que ve una persona en vez de los ids internos de un movimiento: "Pedido #6AE8CE" y a dónde lleva. destino es
 * PEDIDO (Pedidos de la tienda), COMPRA (compra a proveedor de la tienda), ORDEN (la orden del taller) o null si no hay
 * pantalla de detalle; id es lo que abre esa pantalla.
 *
 * Los movimientos guardan referencias de distintas épocas: PAGO-<ref de Wompi>, CANCELACION-<orden>-<pedido>, el id de
 * un pedido, de una compra o de una venta. Cada una se resuelve con lo que se tenga; nunca se muestra el id largo.
 */
@Service
public class ReferenciasMovimiento {

    public static final String PEDIDO = "PEDIDO";
    public static final String COMPRA = "COMPRA";
    public static final String ORDEN = "ORDEN";

    public record Referencia(String texto, String destino, String id) {
    }

    private final PedidoTiendaRepository pedidos;
    private final PagoRepository pagos;

    public ReferenciasMovimiento(PedidoTiendaRepository pedidos, PagoRepository pagos) {
        this.pedidos = pedidos;
        this.pagos = pagos;
    }

    /** Los últimos 6 caracteres en mayúscula, como en el resto de la app. */
    public static String corta(String id) {
        if (id == null || id.isBlank()) return "";
        return id.substring(Math.max(0, id.length() - 6)).toUpperCase(Locale.ROOT);
    }

    // ─── Tienda ───────────────────────────────────────────────────────────────

    /** Resuelve la página de una vez: los pagos repetidos se buscan una sola vez. */
    public void deTienda(List<MovimientoInventario> lista, String tiendaId) {
        Map<String, Referencia> cache = new HashMap<>();
        for (MovimientoInventario m : lista) {
            String ref = m.getReferencia();
            if (ref == null || ref.isBlank()) continue;
            m.setReferenciaLegible(cache.computeIfAbsent(ref + "|" + m.getMotivo(), k -> deTienda(m, tiendaId)));
        }
    }

    Referencia deTienda(MovimientoInventario m, String tiendaId) {
        String ref = m.getReferencia();
        if ("MANUAL".equals(ref) || ref.startsWith("PROD_")) return null;
        if (ref.startsWith("PAGO-")) {
            return pagos.findByReferencia(ref.substring(5)).flatMap(p -> pedidos.findByPagoId(p.getId()).stream()
                            .filter(x -> tiendaId == null || tiendaId.equals(x.getTiendaId())).findFirst())
                    .map(ReferenciasMovimiento::pedido).orElse(new Referencia("Pago #" + corta(ref), null, null));
        }
        if (ref.startsWith("CANCELACION-")) {
            String pedidoId = ref.substring(ref.lastIndexOf('-') + 1);
            return new Referencia("Pedido #" + corta(pedidoId), PEDIDO, pedidoId);
        }
        String motivo = m.getMotivo() == null ? "" : m.getMotivo();
        if (motivo.startsWith("Compra a proveedor")) return new Referencia("Compra #" + corta(ref), COMPRA, ref);
        if (motivo.startsWith("Venta") || motivo.startsWith("Orden de servicio") || motivo.startsWith("Anulación de venta")) {
            return new Referencia("Venta #" + corta(ref), null, null);
        }
        Optional<PedidoTienda> pedido = pedidos.findById(ref).filter(p -> tiendaId == null || tiendaId.equals(p.getTiendaId()));
        return pedido.map(ReferenciasMovimiento::pedido).orElse(new Referencia("#" + corta(ref), null, null));
    }

    private static Referencia pedido(PedidoTienda p) {
        return new Referencia("Pedido #" + corta(p.getId()), PEDIDO, p.getId());
    }

    // ─── Taller ───────────────────────────────────────────────────────────────

    /** Orden de una venta, reversa o cancelación; compra del catálogo de una llegada; nada en los manuales. */
    public Referencia deTaller(MovimientoPiezaTaller m, Map<String, PedidoTienda> cachePedidos) {
        if (m.getOrdenId() != null) return new Referencia("Orden #" + corta(m.getOrdenId()), ORDEN, m.getOrdenId());
        if (m.getPedidoId() != null) {
            PedidoTienda p = cachePedidos.computeIfAbsent(m.getPedidoId(), id -> pedidos.findById(id).orElse(null));
            if (p != null && p.getOrdenId() != null && MovimientoPiezaTaller.CANCELACION.equals(MovimientoPiezaTaller.claseDe(m))) {
                return new Referencia("Orden #" + corta(p.getOrdenId()), ORDEN, p.getOrdenId());
            }
            String compra = p != null && p.getCompraId() != null ? p.getCompraId() : m.getPedidoId();
            return new Referencia("Compra #" + corta(compra), null, null);
        }
        return null;
    }
}
