package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.util.IvaTaller;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Tope del precio de una pieza del taller: nunca más de lo que el cliente pagaría por ella en la tienda.
 *
 * La referencia es el precio público vigente de la tienda donde la compró: el producto de la compra más reciente que siga
 * activo. Si ninguno lo está, el precio público que quedó guardado al comprar. Así el tope no cambia porque otra tienda
 * haga una promoción, y se explica fácil: "no cobras más de lo que esa tienda cobra hoy".
 *
 * Se compara como lo paga el cliente: la tienda cobra el IVA general y el taller el suyo (0 % si no es responsable).
 * Por eso el tope antes de IVA es público × (1 + IVA tienda) ÷ (1 + IVA taller), en pesos enteros hacia abajo.
 */
@Service
public class PrecioPublicoTaller {

    private final ProductoService productos;
    private final ParametrosMarketplace parametros;

    public PrecioPublicoTaller(ProductoService productos, ParametrosMarketplace parametros) {
        this.productos = productos;
        this.parametros = parametros;
    }

    /** precioPublico: antes de IVA, como lo publica la tienda. vigente: false si es el guardado al comprar. */
    public record Referencia(Double precioPublico, String tiendaNombre, boolean vigente) {
    }

    /** Precio público de referencia, o null si la pieza no tiene de dónde sacarlo. */
    public Referencia referencia(PiezaTaller pieza) {
        List<PiezaTaller.Lote> recientes = pieza.getLotes().stream()
                .sorted(Comparator.comparing(PiezaTaller.Lote::getFechaEntrada, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
        for (PiezaTaller.Lote l : recientes) {
            Producto p = producto(l.getProductoId());
            if (p != null && !Boolean.FALSE.equals(p.getActivo()) && p.getPrecioVenta() != null && p.getPrecioVenta() > 0) {
                return new Referencia(p.getPrecioVenta(), l.getTiendaNombre(), true);
            }
        }
        return recientes.stream().filter(l -> l.getPrecioPublico() != null && l.getPrecioPublico() > 0).findFirst()
                .map(l -> new Referencia(l.getPrecioPublico(), l.getTiendaNombre(), false)).orElse(null);
    }

    /** El precio máximo antes de IVA que este taller puede cobrar por la pieza, o null si no hay referencia. */
    public Double tope(PiezaTaller pieza, Taller taller) {
        Referencia r = referencia(pieza);
        return r == null ? null : tope(r.precioPublico(), taller);
    }

    public double tope(double precioPublico, Taller taller) {
        double ivaTienda = parametros.ivaPct();
        return topeDe(precioPublico, ivaTienda, IvaTaller.pct(taller, ivaTienda));
    }

    /** Lo que paga el cliente en la tienda, llevado a la base antes del IVA del taller. */
    static double topeDe(double precioPublico, double ivaTienda, double ivaTaller) {
        double finalTienda = Math.round(precioPublico * (100 + ivaTienda) / 100.0);
        // El precio más alto con el que el cliente paga lo mismo o menos: el IVA se redondea al peso como en la cotización
        double tope = Math.floor(finalTienda * 100.0 / (100 + ivaTaller));
        while (tope + 1 + Math.round((tope + 1) * ivaTaller / 100.0) <= finalTienda) tope++;
        while (tope > 0 && tope + Math.round(tope * ivaTaller / 100.0) > finalTienda) tope--;
        return tope;
    }

    /** El mismo precio, con el IVA que pagaría el cliente en la tienda. */
    public double finalEnTienda(double precioPublico) {
        return Math.round(precioPublico * (100 + parametros.ivaPct()) / 100.0);
    }

    private Producto producto(String id) {
        if (id == null) return null;
        try {
            return productos.obtenerPorId(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
