package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.dto.taller.MovimientosTallerDTO;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fase 2 del stock: movimientos manuales (ajuste, merma, devoluciones, carga inicial), su historial y la alerta de stock
 * bajo. Nunca negativo, motivo obligatorio, FIFO al sacar, una alerta por cruce y cada taller con lo suyo.
 */
class MovimientosTallerTest {

    private static final String A = "tA";
    private static final String B = "tB";
    private static final String PIEZA = PiezaTaller.idDe(A, "REF:BP1");

    private final InventarioEnMemoria inventario = new InventarioEnMemoria();
    private final StockTallerService stock = inventario.stock;
    private final TallerService talleres = mock(TallerService.class);
    private final ProductoService productos = mock(ProductoService.class);
    private final TiendaRepository tiendas = mock(TiendaRepository.class);
    private final PrecioPublicoTaller precioPublico = mock(PrecioPublicoTaller.class);
    private final List<PiezaTaller> avisos = new ArrayList<>();
    private MovimientosTallerService servicio;

    private static Taller taller(String id, boolean responsable) {
        return Taller.builder().id(id).nombre("Taller " + id).usuarioId("u-" + id).activo(true).responsableIva(responsable).build();
    }

    @BeforeEach
    void preparar() {
        when(talleres.obtenerPorUsuario("a@taller.com")).thenReturn(taller(A, true));
        when(talleres.obtenerPorUsuario("b@taller.com")).thenReturn(taller(B, false));
        ReferenciasMovimiento referencias = new ReferenciasMovimiento(mock(PedidoTiendaRepository.class), mock(PagoRepository.class));
        servicio = new MovimientosTallerService(talleres, inventario.piezaRepository, inventario.movimientoRepository, stock,
                productos, tiendas, mock(UsuarioRepository.class), precioPublico, referencias, mock(ExportacionMovimientos.class));
        stock.setAviso(avisos::add);
    }

    /** Dos lotes: el viejo (2 a $100.000) sale antes que el nuevo (3 a $120.000). */
    private void dosLotes(String tallerId) {
        PiezaTaller datos = PiezaTaller.builder().clave("REF:BP1").nombre("Pastillas").referencia("BP1").build();
        stock.entrar(new StockTallerService.Entrada(tallerId, datos, PiezaTaller.Lote.builder().id("viejo|p1").pedidoId("viejo")
                .productoId("p1").tiendaNombre("La Vaquita").cantidad(2).disponible(2).costoUnitario(100_000.0)
                .fechaEntrada(LocalDateTime.now().minusDays(9)).build(), "u1"));
        stock.entrar(new StockTallerService.Entrada(tallerId, datos, PiezaTaller.Lote.builder().id("nuevo|p1").pedidoId("nuevo")
                .productoId("p1").tiendaNombre("Repuestos Sur").cantidad(3).disponible(3).costoUnitario(120_000.0)
                .fechaEntrada(LocalDateTime.now().minusDays(1)).build(), "u1"));
    }

    private MovimientoPiezaTaller manual(String clase, int cantidad, String motivo) {
        return servicio.registrar("a@taller.com", new MovimientosTallerDTO.Registro(clase, PIEZA, null, cantidad, null, motivo));
    }

    private static HttpStatus estado(Runnable r) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, r::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    @Test
    void el_motivo_es_obligatorio_en_todo_movimiento_manual_y_en_la_carga_inicial() {
        dosLotes(A);
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> manual(MovimientoPiezaTaller.MERMA, 1, "   ")));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> manual(MovimientoPiezaTaller.AJUSTE, 4, null)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.registrar("a@taller.com",
                new MovimientosTallerDTO.Registro(MovimientoPiezaTaller.CARGA_INICIAL, null, "p1", 2, 50_000.0, ""))));
        assertEquals(5, inventario.piezas.get(PIEZA).getStock(), "nada se movió");
    }

    @Test
    void el_stock_nunca_queda_negativo() {
        dosLotes(A);
        assertEquals(HttpStatus.CONFLICT, estado(() -> manual(MovimientoPiezaTaller.MERMA, 6, "Se cayó la caja")));
        assertEquals(HttpStatus.CONFLICT, estado(() -> manual(MovimientoPiezaTaller.DEVOLUCION_TIENDA, 9, "Vino defectuosa")));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> manual(MovimientoPiezaTaller.AJUSTE, -1, "Conteo")));
        assertEquals(5, inventario.piezas.get(PIEZA).getStock());
        assertEquals(Map.of("viejo|p1", 2, "nuevo|p1", 3), inventario.disponiblePorLote(PIEZA));

        // Contar 0 sí se puede: deja la pieza en cero, no debajo
        manual(MovimientoPiezaTaller.AJUSTE, 0, "No quedó ninguna");
        assertEquals(0, inventario.piezas.get(PIEZA).getStock());
    }

    @Test
    void la_merma_y_el_ajuste_negativo_salen_por_fifo_con_rastro_de_lote_y_costo() {
        dosLotes(A);
        MovimientoPiezaTaller merma = manual(MovimientoPiezaTaller.MERMA, 3, "Llegaron rotas");
        assertEquals(MovimientoPiezaTaller.SALIDA, merma.getTipo());
        assertEquals(List.of("viejo|p1", "nuevo|p1"), merma.getLotes().stream().map(MovimientoPiezaTaller.ConsumoLote::getLoteId).toList());
        assertEquals(List.of(2, 1), merma.getLotes().stream().map(MovimientoPiezaTaller.ConsumoLote::getCantidad).toList());
        assertEquals(100_000.0, merma.getLotes().get(0).getCostoUnitario());

        // Contó 1 y había 2: la diferencia sale del lote que queda
        MovimientoPiezaTaller ajuste = manual(MovimientoPiezaTaller.AJUSTE, 1, "Conteo de fin de mes");
        assertEquals(MovimientoPiezaTaller.AJUSTE, ajuste.getTipo());
        assertEquals(1, ajuste.getCantidad());
        assertEquals(2, ajuste.getStockAnterior());
        assertEquals(1, ajuste.getStockNuevo());
        assertEquals(Map.of("viejo|p1", 0, "nuevo|p1", 1), inventario.disponiblePorLote(PIEZA));
        assertEquals("a@taller.com", ajuste.getUsuarioId(), "queda quién");
        assertNotNull(ajuste.getFecha(), "y cuándo");
    }

    @Test
    void lo_que_entra_vuelve_al_lote_mas_reciente_con_espacio_y_lo_que_sobra_a_un_lote_nuevo_con_el_ultimo_costo() {
        dosLotes(A);
        manual(MovimientoPiezaTaller.MERMA, 3, "Llegaron rotas");
        MovimientoPiezaTaller devolucion = manual(MovimientoPiezaTaller.DEVOLUCION_CLIENTE, 1, "El cliente no la usó");
        assertEquals(MovimientoPiezaTaller.ENTRADA, devolucion.getTipo());
        assertEquals(Map.of("viejo|p1", 0, "nuevo|p1", 3), inventario.disponiblePorLote(PIEZA));

        // Contó 6 con 3 en stock: 2 caben en el lote viejo y la que sobra va a uno nuevo a $120.000
        manual(MovimientoPiezaTaller.AJUSTE, 6, "Aparecieron en bodega");
        PiezaTaller p = inventario.piezas.get(PIEZA);
        assertEquals(6, p.getStock());
        PiezaTaller.Lote extra = p.getLotes().stream().filter(l -> l.getId().startsWith("MANUAL-")).findFirst().orElseThrow();
        assertEquals(1, extra.getDisponible());
        assertEquals(120_000.0, extra.getCostoUnitario());
        assertEquals(p.getStock(), p.getLotes().stream().mapToInt(PiezaTaller.Lote::getDisponible).sum(), "lotes y stock cuadran");
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> manual(MovimientoPiezaTaller.AJUSTE, 6, "Igual")), "contar lo mismo no es ajuste");
    }

    @Test
    void la_carga_inicial_crea_un_lote_sin_compra_con_el_costo_del_taller_y_el_tope_como_precio() {
        Producto producto = new Producto();
        producto.setId("p9");
        producto.setNombre("Filtro de aceite");
        producto.setReferencia("FA9");
        producto.setPrecioVenta(30_000.0);
        producto.setTiendaId("s1");
        when(productos.obtenerPorId("p9")).thenReturn(producto);
        when(tiendas.findById("s1")).thenReturn(Optional.of(Tienda.builder().id("s1").nombre("La Vaquita").build()));
        when(precioPublico.tope(anyDouble(), any())).thenReturn(30_000.0);

        MovimientoPiezaTaller m = servicio.registrar("a@taller.com",
                new MovimientosTallerDTO.Registro(MovimientoPiezaTaller.CARGA_INICIAL, null, "p9", 4, 21_000.0, "Lo tenía antes de DriveMaster"));

        PiezaTaller p = inventario.piezas.get(PiezaTaller.idDe(A, "REF:FA9"));
        assertEquals(4, p.getStock());
        assertEquals(30_000.0, p.getPrecioVenta(), "precio inicial: el tope");
        PiezaTaller.Lote lote = p.getLotes().get(0);
        assertNull(lote.getCompraId());
        assertNull(lote.getPedidoId());
        assertEquals(21_000.0, lote.getCostoUnitario());
        assertEquals(30_000.0, lote.getPrecioPublico());
        assertEquals("La Vaquita", lote.getTiendaNombre());
        assertFalse(lote.getIvaEnCosto(), "taller responsable: costo sin IVA");
        assertEquals(MovimientoPiezaTaller.CARGA_INICIAL, m.getClase());
        assertEquals("Lo tenía antes de DriveMaster", m.getMotivo());
        assertEquals("a@taller.com", m.getUsuarioId());

        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.registrar("a@taller.com",
                new MovimientosTallerDTO.Registro(MovimientoPiezaTaller.CARGA_INICIAL, null, "p9", 2, 0.0, "Sin costo"))));
    }

    @Test
    void la_alerta_de_stock_bajo_avisa_una_vez_por_cruce_y_se_rearma_al_subir() {
        dosLotes(A);
        stock.cambiarMinimo(A, PIEZA, 3);
        assertTrue(avisos.isEmpty(), "con 5 y mínimo 3 no avisa");

        manual(MovimientoPiezaTaller.MERMA, 2, "Rotas");
        assertEquals(1, avisos.size(), "llegó al mínimo");
        manual(MovimientoPiezaTaller.MERMA, 1, "Otra rota");
        stock.descontar(new StockTallerService.Venta(A, PIEZA, 1, "PAGO-1", 150_000, "o1", "c1", 1L, "cl"));
        assertEquals(1, avisos.size(), "sigue debajo: no repite");

        manual(MovimientoPiezaTaller.DEVOLUCION_CLIENTE, 3, "Volvieron");
        assertFalse(inventario.piezas.get(PIEZA).getAvisoStockBajo(), "subió sobre el mínimo: se rearma");
        manual(MovimientoPiezaTaller.MERMA, 2, "Rotas otra vez");
        assertEquals(2, avisos.size(), "cruzó de nuevo: un aviso más");

        stock.cambiarMinimo(A, PIEZA, null);
        assertNull(inventario.piezas.get(PIEZA).getStockMinimo());
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> stock.cambiarMinimo(A, PIEZA, -1)));
    }

    @Test
    void poner_un_minimo_que_ya_se_cruzo_avisa_de_una_vez() {
        dosLotes(A);
        stock.cambiarMinimo(A, PIEZA, 5);
        assertEquals(1, avisos.size());
        stock.cambiarMinimo(A, PIEZA, 6);
        assertEquals(1, avisos.size());
    }

    @Test
    void cada_taller_ve_y_mueve_solo_lo_suyo() {
        dosLotes(A);
        dosLotes(B);
        manual(MovimientoPiezaTaller.MERMA, 1, "Rota");

        PeticionTabla t = PeticionTabla.de(0, 20, null, null, null, MovimientosTallerService.ORDEN);
        MovimientosTallerDTO.Tabla deA = servicio.tabla("a@taller.com", new MovimientosTallerService.Filtros(null, null, RangoFechas.SIN_RANGO), t);
        MovimientosTallerDTO.Tabla deB = servicio.tabla("b@taller.com", new MovimientosTallerService.Filtros(null, null, RangoFechas.SIN_RANGO), t);
        assertEquals(3, deA.movimientos().total());
        assertEquals(2, deB.movimientos().total());
        assertTrue(deA.movimientos().items().stream().allMatch(f -> f.piezaId().startsWith(A + "|")));
        assertTrue(deB.movimientos().items().stream().allMatch(f -> f.piezaId().startsWith(B + "|")));

        // A no puede mover la pieza de B aunque mande su id
        String deOtro = PiezaTaller.idDe(B, "REF:BP1");
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.registrar("a@taller.com",
                new MovimientosTallerDTO.Registro(MovimientoPiezaTaller.MERMA, deOtro, null, 1, null, "No es mía"))));
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> servicio.cambiarMinimo("a@taller.com", deOtro, 2)));
        assertEquals(5, inventario.piezas.get(deOtro).getStock());
    }

    @Test
    void la_tabla_filtra_por_tipo_y_pieza_resume_y_muestra_referencias_y_lotes_legibles() {
        dosLotes(A);
        stock.descontar(new StockTallerService.Venta(A, PIEZA, 2, "PAGO-1", 150_000, "orden0006ae8ce", "c1", 1L, "cl"));
        stock.revertir("PAGO-1", "Orden cancelada", "admin", MovimientoPiezaTaller.CANCELACION);
        manual(MovimientoPiezaTaller.AJUSTE, 4, "Conteo");

        PeticionTabla t = PeticionTabla.de(0, 20, null, null, null, MovimientosTallerService.ORDEN);
        MovimientosTallerDTO.Tabla todas = servicio.tabla("a@taller.com", new MovimientosTallerService.Filtros(null, null, RangoFechas.SIN_RANGO), t);
        assertEquals(new MovimientosTallerDTO.Resumen(5, 7, 2, 1), todas.resumen(), "5 movimientos; entran 2+3+2; sale 2; 1 ajuste");

        MovimientosTallerDTO.Tabla entradas = servicio.tabla("a@taller.com", new MovimientosTallerService.Filtros("ENTRADA", null, RangoFechas.SIN_RANGO), t);
        assertEquals(3, entradas.movimientos().total(), "la devolución por cancelación cuenta como entrada");
        assertEquals(1, servicio.tabla("a@taller.com", new MovimientosTallerService.Filtros("AJUSTE", null, RangoFechas.SIN_RANGO), t).movimientos().total());
        assertEquals(0, servicio.tabla("a@taller.com", new MovimientosTallerService.Filtros(null, "filtro", RangoFechas.SIN_RANGO), t).movimientos().total());
        assertEquals(5, servicio.tabla("a@taller.com", new MovimientosTallerService.Filtros(null, "bp1", RangoFechas.SIN_RANGO), t).movimientos().total());

        MovimientosTallerDTO.Fila venta = todas.movimientos().items().stream()
                .filter(f -> MovimientoPiezaTaller.VENTA.equals(f.clase())).findFirst().orElseThrow();
        assertEquals("Orden #6AE8CE", venta.referencia().texto(), "referencia corta, no el id largo");
        assertEquals(ReferenciasMovimiento.ORDEN, venta.referencia().destino());
        assertEquals("orden0006ae8ce", venta.referencia().id());
        assertTrue(venta.lotes().get(0).startsWith("2 del lote del ") && venta.lotes().get(0).endsWith("La Vaquita"), venta.lotes().get(0));
        assertTrue(todas.movimientos().items().stream().anyMatch(f -> MovimientoPiezaTaller.CANCELACION.equals(f.clase())));

        assertThrows(ResponseStatusException.class, () -> servicio.tabla("a@taller.com",
                new MovimientosTallerService.Filtros("OTRO", null, RangoFechas.SIN_RANGO), t));
    }
}
