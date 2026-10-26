package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.Producto;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Las reglas del producto en el backend, las mismas que avisa el formulario (reglasProducto.js). */
class ValidacionProductoTest {

    private static Producto valido() {
        Producto p = new Producto();
        p.setNombre("  Pastillas de freno ");
        p.setCodigo(" knt-001 ");
        p.setTipo("STOCK");
        p.setPrecioCompra(10_000.0);
        p.setPrecioVenta(15_000.0);
        p.setStockActual(5);
        p.setStockMinimo(1);
        p.setModelosCompatibles(List.of(new Compatibilidad("Mazda", "Mazda3", 2015, 2020, null)));
        return p;
    }

    private static Map<String, String> errores(Producto p) {
        return assertThrows(DatosInvalidosException.class, () -> ValidacionProducto.validar(p)).getCampos();
    }

    @Test
    void unProductoValidoQuedaNormalizado() {
        Producto p = ValidacionProducto.validar(valido());
        assertEquals("Pastillas de freno", p.getNombre());
        assertEquals("KNT-001", p.getCodigo());
    }

    @Test
    void venderPorDebajoDelCostoNoEsUnError() {
        Producto p = valido();
        p.setPrecioVenta(8_000.0);
        assertDoesNotThrow(() -> ValidacionProducto.validar(p));
    }

    @Test
    void preciosYStockNoPuedenSerNegativos() {
        Producto p = valido();
        p.setPrecioCompra(-1.0);
        p.setPrecioVenta(-1.0);
        p.setStockActual(-2);
        p.setStockMinimo(-1);
        Map<String, String> e = errores(p);
        assertTrue(e.keySet().containsAll(List.of("precioCompra", "precioVenta", "stockActual", "stockMinimo")), e.toString());
    }

    @Test
    void faltanNombreCodigoYPrecio() {
        Producto p = valido();
        p.setNombre(" ");
        p.setCodigo("");
        p.setPrecioVenta(null);
        assertEquals(List.of("nombre", "codigo", "precioVenta"), List.copyOf(errores(p).keySet()));
    }

    @Test
    void tipoYDiasDeEncargo() {
        Producto p = valido();
        p.setTipo("REGALO");
        assertTrue(errores(p).containsKey("tipo"));

        Producto encargo = valido();
        encargo.setTipo("ENCARGO");
        encargo.setDiasEntregaEncargo(121);
        assertTrue(errores(encargo).containsKey("diasEntregaEncargo"));
    }

    @Test
    void elAnoHastaNoPuedeSerAnteriorAlDesdeNiSalirDelRango() {
        int actual = Year.now().getValue();
        Producto p = valido();
        p.setModelosCompatibles(List.of(
                new Compatibilidad("Mazda", "Mazda3", 2020, 2015, null),
                new Compatibilidad("Kia", "Rio", 1800, actual + 3, null)));
        Map<String, String> e = errores(p);
        assertEquals("El año final no puede ser anterior al inicial.", e.get("modelosCompatibles.0.anoHasta"));
        assertTrue(e.containsKey("modelosCompatibles.1.anoDesde"));
        assertTrue(e.containsKey("modelosCompatibles.1.anoHasta"));
    }

    @Test
    void unProductoUniversalNoValidaVehiculos() {
        Producto p = valido();
        p.setCompatibilidadUniversal(true);
        p.setModelosCompatibles(List.of(new Compatibilidad("", "", null, null, null)));
        assertDoesNotThrow(() -> ValidacionProducto.validar(p));
    }
}
