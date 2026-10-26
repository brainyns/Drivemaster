package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.DetalleCompra;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.CompraRepository;
import com.drivemaster.drivemaster.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Aislamiento por tienda: la tienda A nunca ve, modifica ni usa compras, proveedores o productos de la tienda B,
 * y los registros anteriores sin dueño no los ve ninguna. Los repositorios son en memoria; el filtro se prueba en
 * los servicios, que es donde se aplica (no en la pantalla).
 */
class AislamientoComprasProveedoresTest {

    private final Map<String, Proveedor> proveedores = new LinkedHashMap<>();
    private final Map<String, Compra> compras = new LinkedHashMap<>();
    private final AtomicInteger secuencia = new AtomicInteger();

    private ProveedorServiceImpl proveedorService;
    private CompraServiceImpl compraService;
    private ProductoService productoService;
    private MovimientoInventarioService movimientos;

    private final Tienda tiendaA = Tienda.builder().id("A").nombre("Repuestos A").usuarioId("uA").build();
    private final Tienda tiendaB = Tienda.builder().id("B").nombre("Repuestos B").usuarioId("uB").build();

    @BeforeEach
    void setUp() {
        ProveedorRepository proveedorRepo = mock(ProveedorRepository.class);
        when(proveedorRepo.save(any(Proveedor.class))).thenAnswer(i -> {
            Proveedor p = i.getArgument(0);
            if (p.getId() == null) p.setId("prov" + secuencia.incrementAndGet());
            proveedores.put(p.getId(), p);
            return p;
        });
        when(proveedorRepo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(proveedores.get((String) i.getArgument(0))));
        when(proveedorRepo.findByTiendaId(anyString())).thenAnswer(i -> proveedores.values().stream()
                .filter(p -> i.getArgument(0).equals(p.getTiendaId())).toList());
        doAnswer(i -> { proveedores.remove(((Proveedor) i.getArgument(0)).getId()); return null; })
                .when(proveedorRepo).delete(any(Proveedor.class));
        proveedorService = new ProveedorServiceImpl(proveedorRepo);

        CompraRepository compraRepo = mock(CompraRepository.class);
        when(compraRepo.save(any(Compra.class))).thenAnswer(i -> {
            Compra c = i.getArgument(0);
            if (c.getId() == null) c.setId("compra-" + secuencia.incrementAndGet());
            compras.put(c.getId(), c);
            return c;
        });
        when(compraRepo.findById(anyString())).thenAnswer(i -> Optional.ofNullable(compras.get((String) i.getArgument(0))));
        when(compraRepo.findByTiendaIdOrderByFechaDesc(anyString())).thenAnswer(i -> compras.values().stream()
                .filter(c -> i.getArgument(0).equals(c.getTiendaId())).toList());
        when(compraRepo.findAll()).thenAnswer(i -> new ArrayList<>(compras.values()));

        productoService = mock(ProductoService.class);
        movimientos = mock(MovimientoInventarioService.class);
        // findAndModify en memoria: aplica el $set solo si la compra cumple el filtro (_id, tiendaId y estado)
        org.springframework.data.mongodb.core.MongoTemplate mongo = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class),
                any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Compra.class))).thenAnswer(i -> {
            org.bson.Document filtro = ((org.springframework.data.mongodb.core.query.Query) i.getArgument(0)).getQueryObject();
            Compra c = compras.get((String) filtro.get("_id"));
            if (c == null || !filtro.get("tiendaId").equals(c.getTiendaId()) || !filtro.get("estado").equals(c.getEstado())) return null;
            org.bson.Document set = (org.bson.Document) ((org.springframework.data.mongodb.core.query.Update) i.getArgument(1))
                    .getUpdateObject().get("$set");
            c.setEstado((String) set.get("estado"));
            if (set.containsKey("fechaRecepcion")) c.setFechaRecepcion((java.time.LocalDateTime) set.get("fechaRecepcion"));
            if (set.containsKey("fechaCancelacion")) c.setFechaCancelacion((java.time.LocalDateTime) set.get("fechaCancelacion"));
            if (set.containsKey("motivoCancelacion")) c.setMotivoCancelacion((String) set.get("motivoCancelacion"));
            return c;
        });
        compraService = new CompraServiceImpl(compraRepo, productoService, movimientos, proveedorService, mock(EmailsService.class), mongo);

        producto("pA", "A");
        producto("pB", "B");
    }

    private void producto(String id, String tiendaId) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setTiendaId(tiendaId);
        when(productoService.obtenerPorId(id)).thenReturn(p);
    }

    private static Proveedor proveedor(String nombre) {
        Proveedor p = new Proveedor();
        p.setNombre(nombre);
        p.setCorreo(nombre.toLowerCase() + "@demo.com");
        return p;
    }

    private static Compra compraDe(String proveedorId, String productoId, int cantidad) {
        Compra c = new Compra();
        c.setProveedorId(proveedorId);
        DetalleCompra d = new DetalleCompra();
        d.setProductoId(productoId);
        d.setCantidad(cantidad);
        d.setCosto(10_000.0);
        c.setProductos(new ArrayList<>(List.of(d)));
        return c;
    }

    private static void assertEstado(HttpStatus esperado, Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        assertEquals(esperado, e.getStatusCode(), e.getReason());
    }

    // ─── Proveedores ─────────────────────────────────────────────────────────

    @Test
    void cadaTiendaListaSoloSusProveedores() {
        proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        proveedorService.crearEnTienda(proveedor("Beta"), "B");
        Proveedor legado = proveedor("Antiguo"); // anterior a las tiendas: sin dueño
        proveedorService.crear(legado);

        assertEquals(List.of("Alfa"), proveedorService.listarDeTienda("A").stream().map(Proveedor::getNombre).toList());
        assertEquals(List.of("Beta"), proveedorService.listarDeTienda("B").stream().map(Proveedor::getNombre).toList());
    }

    @Test
    void unaTiendaNoLeeNiEditaNiBorraElProveedorDeOtra() {
        Proveedor deB = proveedorService.crearEnTienda(proveedor("Beta"), "B");

        assertEstado(HttpStatus.NOT_FOUND, () -> proveedorService.obtenerDeTienda(deB.getId(), "A"));
        assertEstado(HttpStatus.NOT_FOUND, () -> proveedorService.actualizarEnTienda(deB.getId(), proveedor("Hackeado"), "A"));
        assertEstado(HttpStatus.NOT_FOUND, () -> proveedorService.eliminarDeTienda(deB.getId(), "A"));

        assertEquals("Beta", proveedores.get(deB.getId()).getNombre(), "el proveedor de B quedó intacto");
        assertEquals("B", proveedores.get(deB.getId()).getTiendaId());
    }

    @Test
    void elCuerpoNoPermiteAdueniarseDeUnRegistroAjeno() {
        Proveedor deB = proveedorService.crearEnTienda(proveedor("Beta"), "B");

        // A manda un POST con el id de B para pisarlo: se crea uno nuevo de A y el de B no cambia
        Proveedor intento = proveedor("Pisado");
        intento.setId(deB.getId());
        intento.setTiendaId("B");
        Proveedor nuevo = proveedorService.crearEnTienda(intento, "A");
        assertNotEquals(deB.getId(), nuevo.getId());
        assertEquals("A", nuevo.getTiendaId());
        assertEquals("Beta", proveedores.get(deB.getId()).getNombre());

        // A edita uno suyo mandando tiendaId de B en el cuerpo: sigue siendo de A
        Proveedor cambio = proveedor("Renombrado");
        cambio.setTiendaId("B");
        Proveedor editado = proveedorService.actualizarEnTienda(nuevo.getId(), cambio, "A");
        assertEquals("A", editado.getTiendaId());
        assertEquals("Renombrado", editado.getNombre());
    }

    @Test
    void sinTiendaNoSeListaNada() {
        assertEstado(HttpStatus.FORBIDDEN, () -> proveedorService.listarDeTienda(null));
        assertEstado(HttpStatus.FORBIDDEN, () -> proveedorService.crearEnTienda(proveedor("X"), " "));
    }

    // ─── Compras ─────────────────────────────────────────────────────────────

    @Test
    void laCompraQuedaDeLaTiendaConElUsuarioDeLaSesionYMueveSuInventario() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Compra entrada = compraDe(provA.getId(), "pA", 3);
        entrada.setTiendaId("B");        // el cuerpo intenta ponerla en otra tienda
        entrada.setUsuarioId("intruso"); // y firmarla como otro usuario
        entrada.setId("compra-de-B");    // y pisar un id existente

        Compra guardada = compraService.registrarCompra(entrada, tiendaA);

        assertEquals("A", guardada.getTiendaId());
        assertEquals("uA", guardada.getUsuarioId());
        assertNotEquals("compra-de-B", guardada.getId());
        assertEquals(30_000.0, guardada.getTotal());
        verify(movimientos).registrarMovimiento(eq("pA"), eq("ENTRADA"), eq(3), anyString(), eq(guardada.getId()), eq("uA"));
    }

    @Test
    void noSePuedeComprarConElProveedorDeOtraTienda() {
        Proveedor provB = proveedorService.crearEnTienda(proveedor("Beta"), "B");

        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.registrarCompra(compraDe(provB.getId(), "pA", 1), tiendaA));

        assertTrue(compras.isEmpty(), "no se guardó nada");
        verifyNoInteractions(movimientos);
    }

    @Test
    void noSePuedeComprarUnProductoDeOtraTiendaNiSeSubeSuStock() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");

        assertEstado(HttpStatus.FORBIDDEN, () -> compraService.registrarCompra(compraDe(provA.getId(), "pB", 5), tiendaA));

        assertTrue(compras.isEmpty());
        verifyNoInteractions(movimientos);
    }

    @Test
    void laCompraNecesitaProductosYCantidadesValidas() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Compra vacia = compraDe(provA.getId(), "pA", 1);
        vacia.setProductos(List.of());
        assertEstado(HttpStatus.BAD_REQUEST, () -> compraService.registrarCompra(vacia, tiendaA));
        assertEstado(HttpStatus.BAD_REQUEST, () -> compraService.registrarCompra(compraDe(provA.getId(), "pA", 0), tiendaA));
        assertEstado(HttpStatus.BAD_REQUEST, () -> compraService.registrarCompra(compraDe(provA.getId(), "pA", -4), tiendaA));
        assertTrue(compras.isEmpty());
    }

    @Test
    void cadaTiendaVeSoloSusComprasYNoLasDeOtraNiLasAnterioresSinDueno() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Proveedor provB = proveedorService.crearEnTienda(proveedor("Beta"), "B");
        Compra deA = compraService.registrarCompra(compraDe(provA.getId(), "pA", 1), tiendaA);
        Compra deB = compraService.registrarCompra(compraDe(provB.getId(), "pB", 2), tiendaB);
        Compra legada = new Compra();
        legada.setId("legada");
        legada.setProductos(List.of());
        compras.put("legada", legada); // sin tiendaId: de antes

        assertEquals(List.of(deA.getId()), compraService.listarDeTienda("A").stream().map(Compra::getId).toList());
        assertEquals(List.of(deB.getId()), compraService.listarDeTienda("B").stream().map(Compra::getId).toList());

        assertEquals(deA.getId(), compraService.obtenerDeTienda(deA.getId(), "A").getId());
        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.obtenerDeTienda(deB.getId(), "A"));
        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.obtenerDeTienda("legada", "A"));
        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.obtenerDeTienda("legada", "B"));
        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.obtenerDeTienda(deA.getId(), null));
    }

    @Test
    void elPdfDeUnaCompraAjenaTampocoSeEntrega() {
        Proveedor provB = proveedorService.crearEnTienda(proveedor("Beta"), "B");
        Compra deB = compraService.registrarCompra(compraDe(provB.getId(), "pB", 2), tiendaB);

        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.generarPdfDeTienda(deB.getId(), "A"));
        assertNotNull(compraService.generarPdfDeTienda(deB.getId(), "B"));
    }

    private static Proveedor conNit(String nombre, String nit) {
        Proveedor p = proveedor(nombre);
        p.setNit(nit);
        return p;
    }

    @Test
    void elNitNoSeRepiteDentroDeLaTiendaAunqueCambienLosPuntosOElDigitoDeVerificacion() {
        proveedorService.crearEnTienda(conNit("Alfa", "900.123.456-7"), "A");

        com.drivemaster.drivemaster.exception.DatosInvalidosException e = assertThrows(
                com.drivemaster.drivemaster.exception.DatosInvalidosException.class,
                () -> proveedorService.crearEnTienda(conNit("Otra", "900123456"), "A"));
        assertEquals("Ya tienes un proveedor con este NIT.", e.getCampos().get("nit"));

        // Otra tienda sí puede tener el mismo proveedor, y el nombre repetido no se bloquea
        assertNotNull(proveedorService.crearEnTienda(conNit("Alfa", "900123456-7"), "B"));
        assertNotNull(proveedorService.crearEnTienda(conNit("Alfa", "800111222-3"), "A"));
    }

    @Test
    void editarUnProveedorConservaSuPropioNitPeroNoTomaElDeOtro() {
        Proveedor alfa = proveedorService.crearEnTienda(conNit("Alfa", "900123456-7"), "A");
        Proveedor beta = proveedorService.crearEnTienda(conNit("Beta", "800111222-3"), "A");

        assertNotNull(proveedorService.actualizarEnTienda(alfa.getId(), conNit("AlfaRenombrado", "900123456-7"), "A"));
        assertThrows(com.drivemaster.drivemaster.exception.DatosInvalidosException.class,
                () -> proveedorService.actualizarEnTienda(beta.getId(), conNit("Beta", "900123456"), "A"));
    }

    @Test
    void elInterruptorDelPdfSeGuardaAlEditar() {
        Proveedor alfa = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Proveedor cambio = proveedor("Alfa");
        cambio.setEnviarPdfCompras(false);
        assertEquals(Boolean.FALSE, proveedorService.actualizarEnTienda(alfa.getId(), cambio, "A").getEnviarPdfCompras());
    }

    @Test
    void unaCompraPendienteSumaElStockSoloAlRecibirlaYUnaSolaVez() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Compra entrada = compraDe(provA.getId(), "pA", 4);
        entrada.setEstado(Compra.PENDIENTE);
        entrada.setMetodoPago("CREDITO");
        entrada.setNumeroFactura("  FV-1020 ");

        Compra pendiente = compraService.registrarCompra(entrada, tiendaA);
        assertEquals(Compra.PENDIENTE, pendiente.getEstado());
        assertEquals("FV-1020", pendiente.getNumeroFactura());
        assertNull(pendiente.getFechaRecepcion());
        verify(movimientos, never()).registrarMovimiento(anyString(), anyString(), any(), anyString(), anyString(), anyString());

        Compra recibida = compraService.recibirCompra(pendiente.getId(), "A");
        assertEquals(Compra.RECIBIDA, recibida.getEstado());
        assertNotNull(recibida.getFechaRecepcion());
        verify(movimientos, times(1)).registrarMovimiento(eq("pA"), eq("ENTRADA"), eq(4), anyString(), eq(pendiente.getId()), eq("uA"));

        // Un segundo clic no vuelve a sumar, y una recibida no se cancela
        assertEstado(HttpStatus.CONFLICT, () -> compraService.recibirCompra(pendiente.getId(), "A"));
        assertEstado(HttpStatus.CONFLICT, () -> compraService.cancelarCompra(pendiente.getId(), "A", null));
        verify(movimientos, times(1)).registrarMovimiento(anyString(), anyString(), any(), anyString(), anyString(), anyString());
        // Otra tienda ni la ve
        assertEstado(HttpStatus.NOT_FOUND, () -> compraService.recibirCompra(pendiente.getId(), "B"));
    }

    @Test
    void unaCompraCanceladaNoTocaElStockNiSePuedeRecibir() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Compra entrada = compraDe(provA.getId(), "pA", 2);
        entrada.setEstado(Compra.PENDIENTE);
        Compra pendiente = compraService.registrarCompra(entrada, tiendaA);

        Compra cancelada = compraService.cancelarCompra(pendiente.getId(), "A", "  El proveedor no tenía existencias ");
        assertEquals(Compra.CANCELADA, cancelada.getEstado());
        assertEquals("El proveedor no tenía existencias", cancelada.getMotivoCancelacion());
        assertEstado(HttpStatus.CONFLICT, () -> compraService.recibirCompra(pendiente.getId(), "A"));
        verify(movimientos, never()).registrarMovimiento(anyString(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void lasComprasAnterioresSinEstadoCuentanComoRecibidas() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Compra legado = compraService.registrarCompra(compraDe(provA.getId(), "pA", 1), tiendaA);
        legado.setEstado(null); // como las guardadas antes de los estados
        assertEquals(Compra.RECIBIDA, legado.estadoEfectivo());
        assertEstado(HttpStatus.CONFLICT, () -> compraService.cancelarCompra(legado.getId(), "A", null));
    }

    @Test
    void elEstadoInicialYElMetodoDePagoSeValidan() {
        Proveedor provA = proveedorService.crearEnTienda(proveedor("Alfa"), "A");
        Compra cancelada = compraDe(provA.getId(), "pA", 1);
        cancelada.setEstado(Compra.CANCELADA);
        Compra conCheque = compraDe(provA.getId(), "pA", 1);
        conCheque.setMetodoPago("CHEQUE");
        assertTrue(assertThrows(com.drivemaster.drivemaster.exception.DatosInvalidosException.class,
                () -> compraService.registrarCompra(cancelada, tiendaA)).getCampos().containsKey("estado"));
        assertTrue(assertThrows(com.drivemaster.drivemaster.exception.DatosInvalidosException.class,
                () -> compraService.registrarCompra(conCheque, tiendaA)).getCampos().containsKey("metodoPago"));
        // Sin estado: recibida, como siempre
        assertEquals(Compra.RECIBIDA, compraService.registrarCompra(compraDe(provA.getId(), "pA", 1), tiendaA).getEstado());
    }
}
