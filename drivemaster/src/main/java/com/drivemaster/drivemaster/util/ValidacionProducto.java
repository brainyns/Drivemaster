package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.Producto;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas de un producto antes de guardarlo, las mismas que avisa el formulario (reglasProducto.js): nombre y código
 * SKU obligatorios, precios y stock sin negativos, tipo STOCK o ENCARGO, días de encargo entre 1 y 120 y, en cada
 * vehículo compatible, años entre {@value #ANO_MIN} y el año siguiente (desde) o el subsiguiente (hasta), con "hasta"
 * mayor o igual que "desde". Deja el código sin espacios y en mayúsculas. Si algo falla lanza DatosInvalidosException
 * con el error de cada campo; los de un vehículo van como "modelosCompatibles.{i}.{campo}".
 *
 * Que el precio de venta sea menor que el de compra no es un error: el formulario lo confirma con la tienda.
 */
public final class ValidacionProducto {

    public static final int NOMBRE_MIN = 3;
    public static final int ANO_MIN = 1900;
    public static final int DIAS_ENCARGO_MAX = 120;

    private ValidacionProducto() {
    }

    /** Valida y normaliza el producto (lo modifica: nombre sin espacios de más y código en mayúsculas). */
    public static Producto validar(Producto p) {
        Map<String, String> errores = new LinkedHashMap<>();

        p.setNombre(p.getNombre() == null ? null : p.getNombre().trim());
        if (p.getNombre() == null || p.getNombre().length() < NOMBRE_MIN) {
            errores.put("nombre", "Escribe el nombre del producto (al menos " + NOMBRE_MIN + " caracteres).");
        }
        p.setCodigo(p.getCodigo() == null || p.getCodigo().isBlank() ? null : p.getCodigo().trim().toUpperCase());
        if (p.getCodigo() == null) errores.put("codigo", "Escribe el código SKU.");

        if (p.getTipo() != null && !"STOCK".equals(p.getTipo()) && !"ENCARGO".equals(p.getTipo())) {
            errores.put("tipo", "El producto es en stock o por encargo.");
        }
        if (p.getPrecioVenta() == null || p.getPrecioVenta() < 0) {
            errores.put("precioVenta", "Escribe un precio de venta válido, desde 0.");
        }
        if (p.getPrecioCompra() != null && p.getPrecioCompra() < 0) {
            errores.put("precioCompra", "El precio de compra no puede ser negativo.");
        }
        if (p.getStockActual() != null && p.getStockActual() < 0) {
            errores.put("stockActual", "El stock no puede ser negativo.");
        }
        if (p.getStockMinimo() != null && p.getStockMinimo() < 0) {
            errores.put("stockMinimo", "El stock mínimo no puede ser negativo.");
        }
        Integer dias = p.getDiasEntregaEncargo();
        if ("ENCARGO".equals(p.getTipo()) && dias != null && (dias < 1 || dias > DIAS_ENCARGO_MAX)) {
            errores.put("diasEntregaEncargo", "Escribe entre 1 y " + DIAS_ENCARGO_MAX + " días.");
        }

        if (!Boolean.TRUE.equals(p.getCompatibilidadUniversal()) && p.getModelosCompatibles() != null) {
            validarVehiculos(p.getModelosCompatibles(), errores);
        }

        if (!errores.isEmpty()) throw new DatosInvalidosException(errores);
        return p;
    }

    private static void validarVehiculos(List<Compatibilidad> vehiculos, Map<String, String> errores) {
        int actual = Year.now().getValue();
        for (int i = 0; i < vehiculos.size(); i++) {
            Compatibilidad c = vehiculos.get(i);
            String campo = "modelosCompatibles." + i + ".";
            if (c == null) continue;
            if (c.getMarca() == null || c.getMarca().isBlank()) errores.put(campo + "marca", "Elige la marca del vehículo.");
            if (c.getModelo() == null || c.getModelo().isBlank()) errores.put(campo + "modelo", "Elige el modelo.");
            Integer desde = c.getAnoDesde();
            Integer hasta = c.getAnoHasta();
            boolean desdeValido = desde != null && desde >= ANO_MIN && desde <= actual + 1;
            if (!desdeValido) errores.put(campo + "anoDesde", "Escribe un año entre " + ANO_MIN + " y " + (actual + 1) + ".");
            if (hasta == null || hasta < ANO_MIN || hasta > actual + 2) {
                errores.put(campo + "anoHasta", "Escribe un año entre " + ANO_MIN + " y " + (actual + 2) + ".");
            } else if (desdeValido && hasta < desde) {
                errores.put(campo + "anoHasta", "El año final no puede ser anterior al inicial.");
            }
        }
    }
}
