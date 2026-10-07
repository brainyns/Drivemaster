package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.OfertaRepuestoDTO;
import com.drivemaster.drivemaster.dto.RepuestoBuscadoDTO;
import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.service.BuscadorRepuestosService.Oferta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuscadorRepuestosServiceTest {

    private static final Vehiculo SPARK = Vehiculo.builder().marca("Chevrolet").modelo("Spark GT").anio(2018).motor("1.2").build();
    // Taller en Chapinero (Bogotá)
    private static final Taller TALLER = Taller.builder().id("t1").latitud(4.6486).longitud(-74.0631).build();
    private static final Tienda TIENDA = tienda("a", null, null);

    private static Tienda tienda(String id, Double lat, Double lng) {
        return Tienda.builder().id(id).nombre("Tienda " + id).latitud(lat).longitud(lng).build();
    }

    private static Producto producto(String id, String tiendaId, String referencia, int stock, double precio,
                                     String tipo, Compatibilidad... compat) {
        Producto p = new Producto();
        p.setId(id);
        p.setTiendaId(tiendaId);
        p.setNombre("Pastillas de freno");
        p.setMarca("Bosch");
        p.setReferencia(referencia);
        p.setStockActual(stock);
        p.setPrecioVenta(precio);
        p.setTipo(tipo);
        p.setModelosCompatibles(new ArrayList<>(List.of(compat)));
        return p;
    }

    private static Compatibilidad spark(Integer desde, Integer hasta, String motor) {
        return new Compatibilidad("Chevrolet", "Spark GT", desde, hasta, motor);
    }

    private static Compatibilidad logan() {
        return new Compatibilidad("Renault", "Logan", 2015, 2020);
    }

    private static OfertaRepuestoDTO evaluar(Producto p, int cantidad) {
        return BuscadorRepuestosService.evaluar(p, TIENDA, cantidad, TALLER, SPARK);
    }

    private static Oferta oferta(Producto p, Tienda t, int cantidad) {
        return new Oferta(p, BuscadorRepuestosService.evaluar(p, t, cantidad, TALLER, SPARK));
    }

    // ─── Estado de la tarjeta ────────────────────────────────────────────

    @Test
    void compatibleConStockSeElige() {
        OfertaRepuestoDTO o = evaluar(producto("1", "a", "R", 4, 1, "STOCK", spark(2015, 2020, "1.2")), 2);
        assertEquals("COMPATIBLE", o.getEstado());
        assertEquals("COMPATIBLE", o.getCompatibilidad());
        assertTrue(o.isSeleccionable());
        assertFalse(o.isRequiereConfirmacion());
        assertNull(o.getMotivo());
    }

    @Test
    void otroVehiculoAnioOMotorEsNoCompatible() {
        OfertaRepuestoDTO otroModelo = evaluar(producto("1", "a", "R", 5, 1, "STOCK", logan()), 1);
        OfertaRepuestoDTO otroAnio = evaluar(producto("2", "a", "R", 5, 1, "STOCK", spark(2010, 2014, null)), 1);
        OfertaRepuestoDTO otroMotor = evaluar(producto("3", "a", "R", 5, 1, "STOCK", spark(2015, 2020, "1.0")), 1);

        for (OfertaRepuestoDTO o : List.of(otroModelo, otroAnio, otroMotor)) {
            assertEquals("NO_COMPATIBLE", o.getEstado());
            assertFalse(o.isSeleccionable());
            assertEquals("No es compatible con Chevrolet Spark GT 2018", o.getMotivo());
        }
    }

    @Test
    void noCompatibleGanaAAgotado() {
        OfertaRepuestoDTO o = evaluar(producto("1", "a", "R", 0, 1, "STOCK", logan()), 1);
        assertEquals("NO_COMPATIBLE", o.getEstado());
        assertEquals(OfertaRepuestoDTO.AGOTADO, o.getDisponibilidad());
    }

    @Test
    void stockCeroSeMuestraAgotadoYNoSeElige() {
        OfertaRepuestoDTO compatible = evaluar(producto("1", "a", "R", 0, 1, "STOCK", spark(null, null, null)), 1);
        OfertaRepuestoDTO sinDatos = evaluar(producto("2", "a", "R", 0, 1, "STOCK"), 1);

        assertEquals("AGOTADO", compatible.getEstado());
        assertEquals("AGOTADO", sinDatos.getEstado(), "Agotado gana a por confirmar");
        assertFalse(compatible.isSeleccionable());
        assertEquals("Agotado en esta tienda", compatible.getMotivo());
    }

    @Test
    void stockInsuficienteNoSeEligeYDiceCuantoHay() {
        OfertaRepuestoDTO o = evaluar(producto("1", "a", "R", 2, 1, "STOCK", spark(null, null, null)), 4);
        assertEquals("COMPATIBLE", o.getEstado());
        assertEquals(OfertaRepuestoDTO.PARCIAL, o.getDisponibilidad());
        assertFalse(o.isSeleccionable());
        assertEquals("Solo hay 2 unidad(es) en esta tienda", o.getMotivo());
    }

    @Test
    void encargoCompatibleSeEligeConTiempoEstimado() {
        Producto encargo = producto("1", "a", "R", 0, 1, "ENCARGO", spark(2015, 2020, null));
        encargo.setDiasEntregaEncargo(5);
        OfertaRepuestoDTO o = evaluar(encargo, 3);
        assertEquals("COMPATIBLE", o.getEstado());
        assertEquals(OfertaRepuestoDTO.ENCARGO, o.getDisponibilidad());
        assertEquals(5, o.getDiasEntrega());
        assertTrue(o.isSeleccionable());
    }

    @Test
    void sinDatosEsPorConfirmarYSeEligeConConfirmacion() {
        OfertaRepuestoDTO o = evaluar(producto("1", "a", "R", 3, 1, "STOCK"), 1);
        assertEquals("POR_CONFIRMAR", o.getEstado());
        assertTrue(o.isSeleccionable());
        assertTrue(o.isRequiereConfirmacion());
        assertNotNull(o.getMotivo());
    }

    @Test
    void universalEsCompatibleSinConfirmacion() {
        Producto aceite = producto("1", "a", "R", 3, 1, "STOCK");
        aceite.setCompatibilidadUniversal(true);
        OfertaRepuestoDTO o = evaluar(aceite, 1);
        assertEquals("COMPATIBLE", o.getEstado());
        assertFalse(o.isRequiereConfirmacion());

        // La marca universal también gana a compatibilidades de otros vehículos.
        Producto universalConDatos = producto("2", "a", "R", 3, 1, "STOCK", logan());
        universalConDatos.setCompatibilidadUniversal(true);
        assertEquals("COMPATIBLE", evaluar(universalConDatos, 1).getEstado());
    }

    // ─── Agrupación y orden ──────────────────────────────────────────────

    @Test
    void agrupaPorReferenciaYOrdenaDisponibilidadDistanciaPrecio() {
        Tienda cercaCara = tienda("cercaCara", 4.6500, -74.0600);     // ~0.4 km
        Tienda lejosBarata = tienda("lejosBarata", 4.7500, -74.0900); // ~11 km
        Tienda sinStock = tienda("sinStock", 4.6490, -74.0630);       // a metros, pero agotada
        Tienda cercaBarata = tienda("cercaBarata", 4.6500, -74.0600);

        List<Oferta> ofertas = List.of(
                oferta(producto("p1", "lejosBarata", "BP-85.020", 8, 90_000, "STOCK", spark(null, null, null)), lejosBarata, 1),
                oferta(producto("p2", "sinStock", "bp 85020", 0, 70_000, "STOCK", spark(null, null, null)), sinStock, 1),
                oferta(producto("p3", "cercaCara", "BP85020", 3, 120_000, "STOCK", spark(null, null, null)), cercaCara, 1),
                oferta(producto("p4", "cercaBarata", "BP85020", 2, 100_000, "STOCK", spark(null, null, null)), cercaBarata, 1));

        List<RepuestoBuscadoDTO> grupos = BuscadorRepuestosService.agrupar(ofertas);

        assertEquals(1, grupos.size(), "Las variantes de la referencia son la misma pieza");
        RepuestoBuscadoDTO g = grupos.get(0);
        assertEquals(List.of("cercaBarata", "cercaCara", "lejosBarata", "sinStock"),
                g.getOfertas().stream().map(OfertaRepuestoDTO::getTiendaId).toList());
        assertEquals(13, g.getStockTotal());
        assertEquals(3, g.getTiendasConStock());
    }

    @Test
    void lasNoSeleccionablesVanAlFinal() {
        Tienda cerca = tienda("cerca", 4.6490, -74.0630);
        Tienda lejos = tienda("lejos", 4.7500, -74.0900);
        List<Oferta> ofertas = List.of(
                oferta(producto("x", "cerca", "R1", 9, 10, "STOCK", logan()), cerca, 1),               // no compatible, muy cerca
                oferta(producto("y", "lejos", "R1", 9, 99, "STOCK", spark(null, null, null)), lejos, 1)); // compatible, lejos

        RepuestoBuscadoDTO g = BuscadorRepuestosService.agrupar(ofertas).get(0);
        assertEquals(List.of("lejos", "cerca"), g.getOfertas().stream().map(OfertaRepuestoDTO::getTiendaId).toList());
    }

    @Test
    void sinReferenciaAgrupaPorMarcaYNombre() {
        Producto a = producto("a", "x", null, 1, 1, "STOCK");
        Producto b = producto("b", "y", "", 1, 1, "STOCK");
        b.setNombre("PASTILLAS DE FRENO");
        assertEquals(BuscadorRepuestosService.clavePieza(a), BuscadorRepuestosService.clavePieza(b));
    }
}
