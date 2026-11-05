package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** La exportación usa los mismos filtros que la pantalla de Inventario y solo los movimientos de la tienda. */
class ExportacionMovimientosTest {

    private ExportacionMovimientos exportacion;

    private static MovimientoInventario mov(String id, String productoId, String tipo, int cantidad, LocalDateTime fecha, String usuario) {
        MovimientoInventario m = new MovimientoInventario();
        m.setId(id);
        m.setProductoId(productoId);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(10);
        m.setStockNuevo("SALIDA".equals(tipo) ? 10 - cantidad : 10 + cantidad);
        m.setMotivo("Motivo " + id);
        m.setReferencia("MANUAL");
        m.setFecha(fecha);
        m.setUsuarioId(usuario);
        m.setTiendaId("A");
        return m;
    }

    @BeforeEach
    void setUp() {
        MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Producto pastillas = new Producto();
        pastillas.setId("p1");
        pastillas.setNombre("Pastillas de freno");
        pastillas.setCodigo("KNT-BRK-001");
        Producto filtro = new Producto();
        filtro.setId("p2");
        filtro.setNombre("Filtro de aceite");
        filtro.setCodigo("FLT-01");
        when(productos.findByTiendaId("A")).thenReturn(List.of(pastillas, filtro));
        when(movimientos.findByTiendaIdOrderByFechaDesc("A")).thenReturn(List.of(
                mov("m3", "p1", "SALIDA", 2, LocalDateTime.of(2026, 10, 20, 9, 0), "tienda@demo.com"),
                mov("m2", "p2", "ENTRADA", 5, LocalDateTime.of(2026, 10, 10, 9, 0), "u1"),
                mov("m1", "p1", "ENTRADA", 3, LocalDateTime.of(2026, 9, 30, 23, 0), "u1")));
        Usuario u = new Usuario();
        u.setCorreo("compras@demo.com");
        when(usuarios.findById("u1")).thenReturn(Optional.of(u));
        exportacion = new ExportacionMovimientos(movimientos, productos, usuarios);
    }

    @Test
    void sinFiltrosVanTodosDelMasRecienteAlMasAntiguoConElCorreoDelUsuario() {
        List<ExportacionMovimientos.Fila> filas = exportacion.filas("A", new ExportacionMovimientos.Filtros(null, null, null, null));
        assertEquals(List.of("Salida", "Entrada", "Entrada"), filas.stream().map(ExportacionMovimientos.Fila::tipo).toList());
        assertEquals("tienda@demo.com", filas.get(0).usuario());
        assertEquals("compras@demo.com", filas.get(1).usuario(), "las compras guardan el id: se muestra el correo");
    }

    @Test
    void filtraPorProductoTipoYFechasComoLaPantalla() {
        assertEquals(2, exportacion.filas("A", new ExportacionMovimientos.Filtros("knt-brk", null, null, null)).size(), "por código");
        assertEquals(1, exportacion.filas("A", new ExportacionMovimientos.Filtros("pastillas", "ENTRADA", null, null)).size());
        // Solo "desde": del 1 de octubre en adelante (el del 30 de septiembre queda fuera)
        assertEquals(2, exportacion.filas("A", new ExportacionMovimientos.Filtros(null, null, LocalDate.of(2026, 10, 1), null)).size());
        // "Hasta" incluye todo ese día
        assertEquals(2, exportacion.filas("A", new ExportacionMovimientos.Filtros(null, null, null, LocalDate.of(2026, 10, 10))).size());
    }

    @Test
    void elExcelTraeLasColumnasYUnaFilaPorMovimientoYElPdfSeGenera() throws Exception {
        List<ExportacionMovimientos.Fila> filas = exportacion.filas("A", new ExportacionMovimientos.Filtros(null, null, null, null));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(exportacion.excel(filas)))) {
            var hoja = wb.getSheetAt(0);
            assertEquals("Stock después", hoja.getRow(0).getCell(6).getStringCellValue());
            assertEquals(3, hoja.getLastRowNum());
            assertEquals("Pastillas de freno", hoja.getRow(1).getCell(1).getStringCellValue());
        }
        byte[] pdf = exportacion.pdf(filas, "Repuestos A", "Todos los movimientos");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void elResumenDeLosFiltrosVaEnElPdf() {
        assertEquals("Todos los movimientos", ExportacionMovimientos.resumenFiltros(new ExportacionMovimientos.Filtros(null, "", null, null)));
        assertEquals("Entradas · desde el 01/10/2026 hasta el 31/10/2026 · producto “filtro”",
                ExportacionMovimientos.resumenFiltros(new ExportacionMovimientos.Filtros(" filtro ", "ENTRADA",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))));
    }
}
