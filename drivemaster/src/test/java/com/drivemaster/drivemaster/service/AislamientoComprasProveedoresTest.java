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
        compraService = new CompraServiceImpl(compraRepo, productoService, movimientos, proveedorService, mock(EmailsService.class));

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
}
