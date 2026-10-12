package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.controller.ProductoController;
import com.drivemaster.drivemaster.dto.ProductoDTO;
import com.drivemaster.drivemaster.model.Compatibilidad;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Herramienta "Buscar productos" del agente del chat (n8n): alcance, relevancia y token de servicio. */
class BuscarParaAgenteTest {

    private ProductoRepository productoRepository;
    private TiendaService tiendaService;
    private ProductoServiceImpl servicio;

    private static Tienda tienda(String id, String nombre) {
        return Tienda.builder().id(id).nombre(nombre).ciudad("Bogotá").direccion("Cra 1").horario("L-V")
                .estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
    }

    private static Producto producto(String id, String tiendaId, boolean activo) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setCodigo("C-" + id);
        p.setTiendaId(tiendaId);
        p.setActivo(activo);
        p.setStockActual(0);
        p.setPrecioVenta(50_000.0);
        p.setPrecioCompra(20_000.0);
        p.setTipo("ENCARGO");
        p.setDiasEntregaEncargo(3);
        p.setModelosCompatibles(new ArrayList<>(List.of(new Compatibilidad("Mazda", "3", 2014, 2018))));
        return p;
    }

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        tiendaService = mock(TiendaService.class);
        servicio = new ProductoServiceImpl(productoRepository, mock(CloudinaryService.class), tiendaService, mock(MongoTemplate.class));
        when(tiendaService.listarVisibles()).thenReturn(List.of(tienda("tA", "Repuestos Norte")));
    }

    @Test
    void soloDevuelveProductosActivosDeTiendasVisiblesConSuTiendaYSuTipo() {
        when(productoRepository.searchByTerm("freno")).thenReturn(List.of(
                producto("visible", "tA", true),
                producto("inactivo", "tA", false),
                producto("tiendaOculta", "tOculta", true),
                producto("sinTienda", null, true)));

        List<ProductoDTO> r = servicio.buscarParaAgente(List.of("freno"));

        assertEquals(1, r.size(), "ni inactivos, ni tiendas ocultas, ni productos sin tienda");
        ProductoDTO dto = r.get(0);
        assertEquals("visible", dto.getId());
        assertEquals("Repuestos Norte", dto.getTienda());
        assertEquals("ENCARGO", dto.getTipo(), "el agente debe saber que no está agotado");
        assertEquals(3, dto.getDiasEntregaEncargo());
        assertEquals("Mazda", dto.getModelosCompatibles().get(0).getMarca());
    }

    @Test
    void ordenaPorCuantosTerminosCoinciden() {
        Producto soloFreno = producto("soloFreno", "tA", true);
        Producto frenoMazda = producto("frenoMazda", "tA", true);
        when(productoRepository.searchByTerm("freno")).thenReturn(List.of(soloFreno, frenoMazda));
        when(productoRepository.searchByTerm("mazda")).thenReturn(List.of(frenoMazda));

        List<ProductoDTO> r = servicio.buscarParaAgente(List.of("freno", "Mazda"));

        assertEquals(List.of("frenoMazda", "soloFreno"), r.stream().map(ProductoDTO::getId).toList());
    }

    @Test
    void devuelveComoMaximoDiezProductos() {
        List<Producto> muchos = new ArrayList<>();
        for (int i = 0; i < 25; i++) muchos.add(producto("p" + i, "tA", true));
        when(productoRepository.searchByTerm("filtro")).thenReturn(muchos);

        assertEquals(10, servicio.buscarParaAgente(List.of("filtro")).size());
    }

    @Test
    void escapaLosCaracteresDeExpresionRegular() {
        servicio.buscarParaAgente(List.of("c++", "(aceite)"));

        verify(productoRepository).searchByTerm("c\\+\\+");
        verify(productoRepository).searchByTerm("\\(aceite\\)");
        assertEquals("1\\.6", ProductoServiceImpl.escaparRegex("1.6"));
    }

    @Test
    void descartaTerminosCortosYPalabrasVaciasSinConsultarMongo() {
        assertTrue(servicio.buscarParaAgente(List.of("de", "x", " para ", "")).isEmpty());
        assertTrue(servicio.buscarParaAgente(null).isEmpty());
        verify(productoRepository, never()).searchByTerm(anyString());
    }

    @Test
    void sinTiendasVisiblesNoBuscaNada() {
        when(tiendaService.listarVisibles()).thenReturn(List.of());

        assertTrue(servicio.buscarParaAgente(List.of("freno")).isEmpty());
        verify(productoRepository, never()).searchByTerm(anyString());
    }

    // ─── Token de servicio del endpoint /api/productos/buscar ───────────────

    private static ProductoController controlador(ProductoService productoService, String secreto) {
        return new ProductoController(productoService, mock(MovimientoInventarioService.class),
                mock(AlcanceTienda.class), secreto);
    }

    @Test
    void sinSecretoConfiguradoLaHerramientaQuedaCerrada() {
        ProductoService productoService = mock(ProductoService.class);

        for (String sinSecreto : new String[]{null, "", "   "}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> controlador(productoService, sinSecreto).buscarParaChat(List.of("freno"), "cualquiera"));
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
        verifyNoInteractions(productoService);
    }

    @Test
    void conTokenIncorrectoOAusenteResponde403YConElCorrectoBusca() {
        ProductoService productoService = mock(ProductoService.class);
        ProductoController c = controlador(productoService, "secreto-tools");

        assertThrows(ResponseStatusException.class, () -> c.buscarParaChat(List.of("freno"), null));
        assertThrows(ResponseStatusException.class, () -> c.buscarParaChat(List.of("freno"), "otro"));
        verifyNoInteractions(productoService);

        c.buscarParaChat(List.of("freno"), "secreto-tools");
        verify(productoService).buscarParaAgente(List.of("freno"));
    }
}
