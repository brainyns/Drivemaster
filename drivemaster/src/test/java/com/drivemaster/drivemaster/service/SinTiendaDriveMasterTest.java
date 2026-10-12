package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.config.MarketplaceMigration;
import com.drivemaster.drivemaster.dto.RepuestoCompatibleDTO;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.VehiculoRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** DriveMaster es la aplicación, no una tienda: sin tienda propia, sin valores por defecto y con errores claros. */
class SinTiendaDriveMasterTest {

    private ProductoRepository productoRepository;
    private TiendaService tiendaService;

    private static Tienda tienda(String id, String nombre, String ciudad) {
        return Tienda.builder().id(id).nombre(nombre).ciudad(ciudad).direccion("Cra 1").horario("L-V")
                .estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
    }

    private static Producto producto(String id, String tiendaId, boolean activo, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Filtro " + id);
        p.setCodigo("C-" + id);
        p.setTiendaId(tiendaId);
        p.setActivo(activo);
        p.setStockActual(stock);
        p.setPrecioVenta(30_000.0);
        p.setPrecioCompra(12_000.0);
        p.setProveedorId("prov-secreto");
        p.setUbicacion("Bodega 9");
        p.setStockMinimo(3);
        com.drivemaster.drivemaster.model.Compatibilidad c = new com.drivemaster.drivemaster.model.Compatibilidad();
        c.setMarca("Chevrolet");
        c.setModelo("Spark");
        p.setModelosCompatibles(new java.util.ArrayList<>(List.of(c)));
        return p;
    }

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        tiendaService = mock(TiendaService.class);
    }

    // ─── Productos ───────────────────────────────────────────────────────────

    @Test
    void crearUnProductoSinTiendaDaUnErrorClaroYNoSeAsignaANinguna() {
        ProductoServiceImpl servicio = new ProductoServiceImpl(productoRepository, mock(CloudinaryService.class), tiendaService, mock(MongoTemplate.class));

        for (String sinTienda : new String[]{null, "", "   "}) {
            Producto p = producto("p1", sinTienda, true, 5);
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.crearProducto(p));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), "un 400 claro, no un 500 interno");
            assertTrue(e.getReason().contains("necesita una tienda"), e.getReason());
        }
        verify(productoRepository, never()).save(any());
        verifyNoInteractions(tiendaService);
    }

    @Test
    void crearUnProductoConTiendaSigueFuncionando() {
        ProductoServiceImpl servicio = new ProductoServiceImpl(productoRepository, mock(CloudinaryService.class), tiendaService, mock(MongoTemplate.class));
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        Producto creado = servicio.crearProducto(producto("p1", "tA", true, 5));

        assertEquals("tA", creado.getTiendaId());
        assertNotNull(creado.getFechaCreacion());
    }

    @Test
    void elListadoPublicoSoloTraeProductosActivosDeTiendasVisiblesYSinDatosInternos() {
        when(tiendaService.listarVisibles()).thenReturn(List.of(tienda("tA", "Norte", "Bogotá")));
        when(productoRepository.findByTiendaIdIn(any(Collection.class))).thenReturn(List.of(
                producto("p1", "tA", true, 5), producto("p2", "tA", false, 5)));
        ProductoServiceImpl servicio = new ProductoServiceImpl(productoRepository, mock(CloudinaryService.class), tiendaService, mock(MongoTemplate.class));

        List<Producto> r = servicio.listarCatalogoPublico();

        assertEquals(1, r.size(), "el inactivo no sale");
        Producto p = r.get(0);
        assertEquals(30_000.0, p.getPrecioVenta());
        assertNull(p.getPrecioCompra(), "el costo no es público");
        assertNull(p.getProveedorId());
        assertNull(p.getUbicacion());
        assertNull(p.getStockMinimo());
    }

    @Test
    void sinTiendasVisiblesElListadoPublicoEstaVacioNoDaError() {
        when(tiendaService.listarVisibles()).thenReturn(List.of());
        ProductoServiceImpl servicio = new ProductoServiceImpl(productoRepository, mock(CloudinaryService.class), tiendaService, mock(MongoTemplate.class));

        assertTrue(servicio.listarCatalogoPublico().isEmpty());
        verify(productoRepository, never()).findByTiendaIdIn(any());
    }

    // ─── Flujo V1 de repuestos: los de todas las tiendas ─────────────────────

    @Test
    void losRepuestosCompatiblesSalenDeTodasLasTiendasVisiblesConSuTienda() {
        VehiculoRepository vehiculos = mock(VehiculoRepository.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        Usuario ana = Usuario.builder().id("u1").correo("ana@demo.com").rol("CLIENTE").build();
        when(usuarios.obtenerPorCorreo("ana@demo.com")).thenReturn(ana);
        Vehiculo auto = new Vehiculo();
        auto.setId("v1");
        auto.setUsuarioId("u1");
        auto.setMarca("Chevrolet");
        auto.setModelo("Spark");
        when(vehiculos.findById("v1")).thenReturn(Optional.of(auto));

        Tienda a = tienda("tA", "Norte", "Bogotá");
        Tienda b = tienda("tB", "Sur", "Cali");
        Tienda incompleta = Tienda.builder().id("tC").nombre("Nueva").estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
        when(tiendaService.listarVisibles()).thenReturn(List.of(a, b)); // la incompleta no es visible
        when(productoRepository.findByModeloCompatible("Spark")).thenReturn(List.of(
                producto("pA", "tA", true, 4),      // sí
                producto("pB", "tB", true, 2),      // sí, de otra tienda
                producto("pC", "tC", true, 9),      // tienda no visible
                producto("pD", null, true, 9),      // sin tienda: no sale en ningún catálogo
                producto("pE", "tA", false, 9),     // inactivo
                producto("pF", "tB", true, 0)));    // sin stock

        VehiculoServiceImpl servicio = new VehiculoServiceImpl(vehiculos, productoRepository, usuarios,
                mock(CloudinaryService.class), mock(MantenimientoService.class), tiendaService);

        List<RepuestoCompatibleDTO> r = servicio.buscarRepuestosCompatibles("ana@demo.com", "v1");

        assertEquals(List.of("pA", "pB"), r.stream().map(RepuestoCompatibleDTO::getProductoId).toList());
        assertEquals("Norte", r.get(0).getTiendaNombre());
        assertEquals("Cali", r.get(1).getTiendaCiudad());
        assertEquals("tB", r.get(1).getTiendaId());
        assertFalse(r.stream().anyMatch(x -> "tC".equals(x.getTiendaId())), incompleta.getId());
    }

    @Test
    void sinTiendasVisiblesLosRepuestosCompatiblesSonUnaListaVaciaNoUnErrorInterno() {
        VehiculoRepository vehiculos = mock(VehiculoRepository.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        when(usuarios.obtenerPorCorreo("ana@demo.com")).thenReturn(Usuario.builder().id("u1").rol("CLIENTE").build());
        Vehiculo auto = new Vehiculo();
        auto.setId("v1");
        auto.setUsuarioId("u1");
        auto.setMarca("Chevrolet");
        auto.setModelo("Spark");
        when(vehiculos.findById("v1")).thenReturn(Optional.of(auto));
        when(tiendaService.listarVisibles()).thenReturn(List.of());
        when(productoRepository.findByModeloCompatible("Spark")).thenReturn(List.of(producto("pA", "tA", true, 4)));

        VehiculoServiceImpl servicio = new VehiculoServiceImpl(vehiculos, productoRepository, usuarios,
                mock(CloudinaryService.class), mock(MantenimientoService.class), tiendaService);

        assertTrue(servicio.buscarRepuestosCompatibles("ana@demo.com", "v1").isEmpty());
    }

    // ─── La migración de arranque ya no crea ninguna tienda ──────────────────

    @Test
    void laMigracionDeArranqueNoCreaTiendasNiAsignaProductosANinguna() throws Exception {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateMulti(any(Query.class), any(Update.class), eq(Producto.class))).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        TallerRepository talleres = mock(TallerRepository.class);
        when(talleres.findAll()).thenReturn(List.of());

        new MarketplaceMigration(mongo, talleres, mock(SuscripcionService.class),
                mock(com.drivemaster.drivemaster.repository.UsuarioRepository.class)).run();

        verify(mongo, never()).save(any());
        verify(mongo, never()).insert(any(Object.class));
        verify(mongo, never()).updateMulti(any(Query.class), any(Update.class), eq(com.drivemaster.drivemaster.model.MovimientoInventario.class));
        // Lo único que toca de los productos es el stock nulo
        var cambio = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(any(Query.class), cambio.capture(), eq(Producto.class));
        assertFalse(cambio.getValue().getUpdateObject().toJson().contains("tiendaId"));
        assertEquals(0, MarketplaceMigration.class.getDeclaredFields().length - java.util.Arrays.stream(MarketplaceMigration.class.getDeclaredFields())
                .filter(f -> !f.getName().toLowerCase().contains("tienda")).count(), "ya no depende de ningún repositorio de tiendas");
    }
}
