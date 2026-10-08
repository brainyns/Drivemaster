package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.catalogo.PaginaCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.ProductoCatalogoDTO;
import com.drivemaster.drivemaster.dto.catalogo.TiendaCatalogoDTO;
import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CatalogoServiceTest {

    private MongoTemplate mongoTemplate;
    private TiendaRepository tiendaRepository;
    private VehiculoService vehiculoService;
    private CatalogoService service;

    private static Tienda tienda(String id, String ciudad, String direccion, String horario, Double envio) {
        return Tienda.builder().id(id).nombre("Tienda " + id).ciudad(ciudad).direccion(direccion).horario(horario)
                .costoEnvio(envio).nit("900-NIT-" + id).telefono("300" + id).correoContacto(id + "@secreto.com")
                .estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
    }

    private static Producto producto(String id, String tiendaId, String nombre, String referencia, double precio, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setTiendaId(tiendaId);
        p.setNombre(nombre);
        p.setReferencia(referencia);
        p.setPrecioVenta(precio);
        p.setPrecioCompra(1.0);
        p.setProveedorId("prov-secreto");
        p.setUbicacion("bodega-7");
        p.setStockActual(stock);
        p.setTipo("STOCK");
        return p;
    }

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        tiendaRepository = mock(TiendaRepository.class);
        vehiculoService = mock(VehiculoService.class);
        service = new CatalogoService(mongoTemplate, tiendaRepository, vehiculoService);
    }

    // ─── Qué tiendas se ven ──────────────────────────────────────────────────

    @Test
    void soloLasTiendasAprobadasActivasYConDatosCompletosSonVisibles() {
        Tienda ok = tienda("A", "Bogotá", "Cra 7", "L-S 8-6", null);
        assertTrue(CatalogoService.tiendaVisible(ok));

        Tienda sinHorario = tienda("B", "Bogotá", "Cra 7", null, null);
        Tienda sinDireccion = tienda("C", "Bogotá", " ", "L-S", null);
        Tienda sinCiudad = tienda("D", "", "Cra 7", "L-S", null);
        Tienda pendiente = tienda("E", "Bogotá", "Cra 7", "L-S", null);
        pendiente.setEstadoAprobacion(EstadoAprobacion.PENDIENTE);
        Tienda inactiva = tienda("F", "Bogotá", "Cra 7", "L-S", null);
        inactiva.setActivo(false);
        Tienda sinHorarioNulo = tienda("G", "Bogotá", "Cra 7", null, null);

        for (Tienda t : List.of(sinHorario, sinDireccion, sinCiudad, pendiente, inactiva, sinHorarioNulo)) {
            assertFalse(CatalogoService.tiendaVisible(t), t.getId());
        }
        assertFalse(CatalogoService.tiendaVisible(null));
    }

    @Test
    void lasTiendasPorCompletarNoMuestranSusProductosYLaConsultaSoloPideLasVisibles() {
        when(tiendaRepository.findAll()).thenReturn(List.of(
                tienda("tA", "Bogotá", "Cra 7", "L-S", null),
                tienda("tB", "Medellín", "Cll 10", "L-V", 8000.0),
                tienda("tC", "Bogotá", "Cra 9", null, null)));   // por completar
        when(mongoTemplate.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(
                producto("p1", "tA", "Filtro de aceite", "FA-1", 20_000, 5),
                producto("p2", "tB", "Filtro de aceite", "FA-1", 18_000, 3)));

        PaginaCatalogoDTO pagina = service.buscar(null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(q.capture(), eq(Producto.class));
        String consulta = q.getValue().getQueryObject().toString();
        assertTrue(consulta.contains("tA") && consulta.contains("tB"), consulta);
        assertFalse(consulta.contains("tC"), "la tienda por completar no se consulta: " + consulta);
        assertEquals(2, pagina.total());
    }

    @Test
    void sinTiendasVisiblesElCatalogoVieneVacioSinConsultarProductos() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tC", "Bogotá", "Cra 9", null, null)));
        PaginaCatalogoDTO pagina = service.buscar(null, null, null, null, null, null, null, null, null);
        assertEquals(0, pagina.total());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void elFiltroDeCiudadNoDistingueTildesNiMayusculas() {
        when(tiendaRepository.findAll()).thenReturn(List.of(
                tienda("tA", "Bogotá", "Cra 7", "L-S", null),
                tienda("tB", "Medellín", "Cll 10", "L-V", null)));
        when(mongoTemplate.find(any(Query.class), eq(Producto.class)))
                .thenReturn(List.of(producto("p1", "tA", "Filtro", "F1", 10_000, 5)));

        service.buscar(null, "  BOGOTA ", null, null, null, null, null, null, null);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(q.capture(), eq(Producto.class));
        String consulta = q.getValue().getQueryObject().toString();
        assertTrue(consulta.contains("tA") && !consulta.contains("tB"), consulta);
    }

    // ─── Qué se muestra de cada producto ─────────────────────────────────────

    @Test
    void elProductoPublicoNoExponeLoAdministrativo() {
        Set<String> prohibidos = Set.of("precioCompra", "proveedorId", "ubicacion", "stockMinimo", "nit", "telefono",
                "correoContacto", "usuarioId", "estadoAprobacion");
        for (Class<?> dto : List.of(ProductoCatalogoDTO.class, TiendaCatalogoDTO.class)) {
            for (RecordComponent c : dto.getRecordComponents()) {
                assertFalse(prohibidos.contains(c.getName()), dto.getSimpleName() + " no debe exponer " + c.getName());
            }
        }
        ProductoCatalogoDTO d = CatalogoService.aDTO(producto("p1", "tA", "Filtro", "F1", 10_000, 5),
                tienda("tA", "Bogotá", "Cra 7", "L-S 8-6", 8000.0), null);
        String texto = d.toString();
        assertFalse(texto.contains("secreto") || texto.contains("bodega-7") || texto.contains("NIT"), texto);
        // Sí: tienda, ciudad, dirección, horario y precio
        assertEquals("Tienda tA", d.tienda().nombre());
        assertEquals("Bogotá", d.tienda().ciudad());
        assertEquals("Cra 7", d.tienda().direccion());
        assertEquals("L-S 8-6", d.tienda().horario());
        assertEquals(10_000.0, d.precio());
        assertTrue(d.tienda().ofreceEnvio());
        assertEquals(8000.0, d.tienda().costoEnvio());
    }

    @Test
    void elStockVisibleTieneTopeYLosEncargosNoTienenStock() {
        Tienda t = tienda("tA", "Bogotá", "Cra 7", "L-S", null);
        assertEquals(20, CatalogoService.aDTO(producto("p1", "tA", "F", "F1", 1000, 500), t, null).stock());
        Producto encargo = producto("p2", "tA", "Disco", "D1", 5000, 0);
        encargo.setTipo("ENCARGO");
        encargo.setDiasEntregaEncargo(4);
        ProductoCatalogoDTO d = CatalogoService.aDTO(encargo, t, null);
        assertTrue(d.disponible());
        assertNull(d.stock());
        assertEquals(4, d.diasEntrega());
        assertFalse(CatalogoService.aDTO(producto("p3", "tA", "F", "F1", 1000, 0), t, null).disponible());
    }

    // ─── Compatibilidad: avisa, nunca oculta ─────────────────────────────────

    private static Vehiculo vehiculo() {
        Vehiculo v = new Vehiculo();
        v.setId("v1");
        v.setMarca("Renault");
        v.setModelo("Logan");
        v.setAnio(2018);
        return v;
    }

    private static Producto conCompatibilidad(String id, String tiendaId, String marca, String modelo, double precio) {
        Producto p = producto(id, tiendaId, "Pastillas " + id, "PF-" + id, precio, 5);
        Compatibilidad c = new Compatibilidad();
        c.setMarca(marca);
        c.setModelo(modelo);
        p.setModelosCompatibles(new ArrayList<>(List.of(c)));
        return p;
    }

    @Test
    void unProductoNoCompatibleSeMuestraConSuAviso() {
        ProductoCatalogoDTO d = CatalogoService.aDTO(conCompatibilidad("p1", "tA", "Chevrolet", "Spark", 30_000),
                tienda("tA", "Bogotá", "Cra 7", "L-S", null), vehiculo());
        assertEquals("NO_COMPATIBLE", d.compatibilidad());
        assertNotNull(d.avisoCompatibilidad());
        assertTrue(d.avisoCompatibilidad().contains("No es compatible"));
        assertEquals("Tienda tA", d.tienda().nombre(), "el producto sigue ahí");
    }

    @Test
    void sinDatosDeCompatibilidadSeAvisaQueHayQueConfirmar() {
        ProductoCatalogoDTO d = CatalogoService.aDTO(producto("p1", "tA", "Filtro", "F1", 10_000, 5),
                tienda("tA", "Bogotá", "Cra 7", "L-S", null), vehiculo());
        assertEquals("POR_CONFIRMAR", d.compatibilidad());
        assertTrue(d.avisoCompatibilidad().contains("confirma"));
    }

    @Test
    void unProductoCompatibleNoLlevaAviso() {
        ProductoCatalogoDTO d = CatalogoService.aDTO(conCompatibilidad("p1", "tA", "Renault", "Logan", 30_000),
                tienda("tA", "Bogotá", "Cra 7", "L-S", null), vehiculo());
        assertEquals("COMPATIBLE", d.compatibilidad());
        assertNull(d.avisoCompatibilidad());
    }

    @Test
    void sinVehiculoNoHayCompatibilidadNiAviso() {
        ProductoCatalogoDTO d = CatalogoService.aDTO(conCompatibilidad("p1", "tA", "Chevrolet", "Spark", 30_000),
                tienda("tA", "Bogotá", "Cra 7", "L-S", null), null);
        assertNull(d.compatibilidad());
        assertNull(d.avisoCompatibilidad());
    }

    @Test
    void conVehiculoNingunProductoSeOcultaPeroLosCompatiblesVanPrimero() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tA", "Bogotá", "Cra 7", "L-S", null)));
        when(vehiculoService.obtenerEntidad("v1")).thenReturn(vehiculo());
        when(mongoTemplate.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(
                conCompatibilidad("noComp", "tA", "Chevrolet", "Spark", 5_000),    // el más barato, pero no compatible
                producto("sinDatos", "tA", "Filtro", "F1", 6_000, 5),
                conCompatibilidad("comp", "tA", "Renault", "Logan", 90_000)));

        PaginaCatalogoDTO pagina = service.buscar(null, null, null, null, "v1", "cliente@demo.com", null, null, null);

        assertEquals(3, pagina.total(), "nada se oculta");
        assertEquals(List.of("comp", "sinDatos", "noComp"), pagina.items().stream().map(ProductoCatalogoDTO::id).toList());
        verify(vehiculoService).obtenerDeUsuario("cliente@demo.com", "v1");
    }

    @Test
    void sinSesionElVehiculoSeIgnoraSinError() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tA", "Bogotá", "Cra 7", "L-S", null)));
        when(mongoTemplate.find(any(Query.class), eq(Producto.class)))
                .thenReturn(List.of(producto("p1", "tA", "Filtro", "F1", 6_000, 5)));
        PaginaCatalogoDTO pagina = service.buscar(null, null, null, null, "v1", null, null, null, null);
        assertNull(pagina.items().get(0).compatibilidad());
        verifyNoInteractions(vehiculoService);
    }

    // ─── Comparar el mismo repuesto entre tiendas ────────────────────────────

    @Test
    void elDetalleMuestraElMismoRepuestoDeOtrasTiendasDeMasBaratoAMasCaro() {
        Producto base = producto("p1", "tA", "Filtro de aceite", "fa-100", 20_000, 5);
        when(tiendaRepository.findAll()).thenReturn(List.of(
                tienda("tA", "Bogotá", "Cra 7", "L-S", null),
                tienda("tB", "Medellín", "Cll 10", "L-V", 8000.0),
                tienda("tC", "Cali", "Av 3", "L-S", null)));
        when(mongoTemplate.findById("p1", Producto.class)).thenReturn(base);
        when(mongoTemplate.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(
                base,
                producto("p2", "tB", "Filtro Aceite", "FA-100", 25_000, 2),   // misma referencia (otra capitalización)
                producto("p3", "tC", "Filtro de aceite", "FA-100", 18_000, 9),
                producto("p4", "tC", "Filtro de aire", "FAIRE-9", 12_000, 9))); // otro repuesto: no se compara

        ProductoCatalogoDTO d = service.detalle("p1", null, null);

        assertEquals("Tienda tA", d.tienda().nombre());
        assertEquals(List.of("p3", "p2"), d.otrasTiendas().stream().map(ProductoCatalogoDTO::id).toList());
        assertEquals(List.of("Tienda tC", "Tienda tB"), d.otrasTiendas().stream().map(o -> o.tienda().nombre()).toList());
        assertEquals("Cali", d.otrasTiendas().get(0).tienda().ciudad());
    }

    @Test
    void sinReferenciaSeComparaPorNombreSinTildesNiMayusculas() {
        Producto a = producto("p1", "tA", "Bujía NGK", null, 20_000, 5);
        Producto b = producto("p2", "tB", "BUJIA ngk", null, 21_000, 5);
        assertEquals(CatalogoService.claveComparable(a), CatalogoService.claveComparable(b));
        assertNotEquals(CatalogoService.claveComparable(a),
                CatalogoService.claveComparable(producto("p3", "tB", "Bujía Bosch", null, 1, 1)));
    }

    @Test
    void elDetalleDeUnProductoDeUnaTiendaPorCompletarNoExiste() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tC", "Bogotá", "Cra 9", null, null)));
        when(mongoTemplate.findById("p1", Producto.class)).thenReturn(producto("p1", "tC", "Filtro", "F1", 1000, 5));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.detalle("p1", null, null));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void unProductoDesactivadoNoTieneDetalle() {
        Producto p = producto("p1", "tA", "Filtro", "F1", 1000, 5);
        p.setActivo(false);
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tA", "Bogotá", "Cra 7", "L-S", null)));
        when(mongoTemplate.findById("p1", Producto.class)).thenReturn(p);
        assertThrows(ResponseStatusException.class, () -> service.detalle("p1", null, null));
    }

    // ─── Orden y paginación ──────────────────────────────────────────────────

    @Test
    void ordenaPorPrecioYPagina() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tA", "Bogotá", "Cra 7", "L-S", null)));
        List<Producto> todos = new ArrayList<>();
        for (int i = 1; i <= 5; i++) todos.add(producto("p" + i, "tA", "Producto " + i, "R" + i, i * 1000.0, 5));
        when(mongoTemplate.find(any(Query.class), eq(Producto.class))).thenReturn(todos);

        PaginaCatalogoDTO desc = service.buscar(null, null, null, null, null, null, "precio_desc", 0, 2);
        assertEquals(List.of("p5", "p4"), desc.items().stream().map(ProductoCatalogoDTO::id).toList());
        assertEquals(5, desc.total());

        PaginaCatalogoDTO pag2 = service.buscar(null, null, null, null, null, null, "precio_asc", 2, 2);
        assertEquals(List.of("p5"), pag2.items().stream().map(ProductoCatalogoDTO::id).toList());

        // Tamaño fuera de rango se acota
        assertEquals(60, service.buscar(null, null, null, null, null, null, null, 0, 9999).tamanio());
    }

    @Test
    void elTextoDeBusquedaSeEscapaYNoEsUnaExpresionRegular() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tA", "Bogotá", "Cra 7", "L-S", null)));
        when(mongoTemplate.find(any(Query.class), eq(Producto.class))).thenReturn(List.of());
        assertDoesNotThrow(() -> service.buscar(".*(", null, null, null, null, null, null, null, null));
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(q.capture(), eq(Producto.class));
        assertTrue(q.getValue().getQueryObject().toString().contains("\\Q.*(\\E"), q.getValue().getQueryObject().toString());
    }

    // ─── DriveMaster es la aplicación, no una tienda ─────────────────────────

    @Test
    void ningunaRespuestaDelCatalogoLlevaMarcaDeTiendaPropiaDeDriveMaster() {
        for (Class<?> dto : new Class<?>[]{ProductoCatalogoDTO.class, com.drivemaster.drivemaster.dto.catalogo.TiendaCatalogoDTO.class}) {
            for (var campo : dto.getRecordComponents()) {
                assertFalse(campo.getName().toLowerCase().contains("plataforma"), dto.getSimpleName() + "." + campo.getName());
            }
        }
    }

    @Test
    void losProductosDeVariasTiendasConviven_yElMismoRepuestoSePuedeComparar() {
        when(tiendaRepository.findAll()).thenReturn(List.of(tienda("tA", "Bogotá", "Cra 7", "L-S", null),
                tienda("tB", "Medellín", "Cll 10", "L-V", 8000.0)));
        Producto base = producto("p1", "tA", "Filtro de aceite", "FA-100", 20_000, 5);
        when(mongoTemplate.findById("p1", Producto.class)).thenReturn(base);
        when(mongoTemplate.find(any(Query.class), eq(Producto.class))).thenReturn(List.of(
                base, producto("p2", "tB", "Filtro de aceite", "FA-100", 18_000, 5)));

        ProductoCatalogoDTO d = service.detalle("p1", null, null);

        assertEquals("Tienda tA", d.tienda().nombre());
        assertEquals(1, d.otrasTiendas().size());
        assertEquals("Tienda tB", d.otrasTiendas().get(0).tienda().nombre());
    }

    @Test
    void unaTiendaSinDatosCompletosNoSeConsultaHastaCompletarlos() {
        Tienda incompleta = Tienda.builder().id("tI").nombre("Tienda nueva")
                .estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
        when(tiendaRepository.findAll()).thenReturn(List.of(incompleta, tienda("tA", "Bogotá", "Cra 7", "L-S", null)));
        when(mongoTemplate.find(any(Query.class), eq(Producto.class)))
                .thenReturn(List.of(producto("p1", "tA", "Filtro", "F1", 10_000, 5)));

        service.buscar(null, null, null, null, null, null, null, null, null);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(q.capture(), eq(Producto.class));
        String consulta = q.getValue().getQueryObject().toString();
        assertTrue(consulta.contains("tA"), consulta);
        assertFalse(consulta.contains("tI"), "sin ciudad, dirección y horario la tienda no se consulta: " + consulta);
        assertEquals(1, service.filtros().tiendas().size(), "ni aparece en el filtro de tiendas");

        // Al completar sus datos entra al catálogo y a los filtros
        incompleta.setCiudad("Cali");
        incompleta.setDireccion("Cra 7 # 45-10");
        incompleta.setHorario("L-S 8-6");
        reset(mongoTemplate);
        when(mongoTemplate.find(any(Query.class), eq(Producto.class)))
                .thenReturn(List.of(producto("p2", "tI", "Aceite", "AC1", 40_000, 9)));
        service.buscar(null, null, null, null, null, null, null, null, null);
        verify(mongoTemplate).find(q.capture(), eq(Producto.class));
        assertTrue(q.getValue().getQueryObject().toString().contains("tI"));
        assertEquals(2, service.filtros().tiendas().size());
        assertEquals(List.of("Bogotá", "Cali"), service.filtros().ciudades());
    }
}
