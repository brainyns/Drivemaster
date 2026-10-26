package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.MovimientoInventarioRepository;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exporta los movimientos de inventario a Excel o PDF con los mismos filtros que la pantalla de Inventario
 * (inventarioTienda.js filtrarMovimientos): producto por nombre o código, tipo y fechas desde/hasta (cada una sola o
 * las dos, ambos días incluidos). Una tienda solo exporta lo suyo. Columnas: fecha, producto, tipo, cantidad, stock
 * antes, stock después, motivo y usuario.
 */
@Service
public class ExportacionMovimientos {

    public record Filtros(String busqueda, String tipo, LocalDate desde, LocalDate hasta) {}

    public record Fila(LocalDateTime fecha, String producto, String codigo, String tipo, int cantidad,
                       Integer stockAntes, Integer stockDespues, String motivo, String usuario) {}

    static final String[] COLUMNAS = {"Fecha", "Producto", "Código", "Tipo", "Cantidad", "Stock antes", "Stock después", "Motivo", "Usuario"};
    private static final Map<String, String> TIPOS = Map.of("ENTRADA", "Entrada", "SALIDA", "Salida", "AJUSTE", "Ajuste");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MovimientoInventarioRepository movimientos;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;

    public ExportacionMovimientos(MovimientoInventarioRepository movimientos, ProductoRepository productos,
                                  UsuarioRepository usuarios) {
        this.movimientos = movimientos;
        this.productos = productos;
        this.usuarios = usuarios;
    }

    /** Filas que cumplen los filtros, de la más reciente a la más antigua. tiendaId null = todo (personal interno). */
    public List<Fila> filas(String tiendaId, Filtros f) {
        List<MovimientoInventario> lista = tiendaId != null
                ? movimientos.findByTiendaIdOrderByFechaDesc(tiendaId)
                : movimientos.findAllByOrderByFechaDesc();
        Map<String, Producto> porId = (tiendaId != null ? productos.findByTiendaId(tiendaId) : productos.findAll())
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity(), (a, b) -> a));
        Map<String, String> nombresUsuario = new HashMap<>();

        String q = normalizar(f.busqueda());
        LocalDateTime inicio = f.desde() != null ? f.desde().atStartOfDay() : null;
        LocalDateTime fin = f.hasta() != null ? f.hasta().atTime(23, 59, 59) : null;

        return lista.stream()
                .filter(m -> f.tipo() == null || f.tipo().isBlank() || f.tipo().equals(m.getTipo()))
                .filter(m -> m.getFecha() == null || ((inicio == null || !m.getFecha().isBefore(inicio))
                        && (fin == null || !m.getFecha().isAfter(fin))))
                .filter(m -> {
                    if (q.isEmpty()) return true;
                    Producto p = porId.get(m.getProductoId());
                    return p != null && (normalizar(p.getNombre()).contains(q) || normalizar(p.getCodigo()).contains(q));
                })
                .sorted(Comparator.comparing(MovimientoInventario::getFecha, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(m -> {
                    Producto p = porId.get(m.getProductoId());
                    String motivo = m.getMotivo() == null ? "" : m.getMotivo();
                    if (m.getReferencia() != null && !"MANUAL".equals(m.getReferencia())) motivo += " · #" + m.getReferencia();
                    return new Fila(m.getFecha(), p != null ? p.getNombre() : "Producto eliminado", p != null ? p.getCodigo() : null,
                            TIPOS.getOrDefault(m.getTipo(), m.getTipo()), m.getCantidad() == null ? 0 : m.getCantidad(),
                            m.getStockAnterior(), m.getStockNuevo(), motivo,
                            nombresUsuario.computeIfAbsent(Objects.toString(m.getUsuarioId(), ""), this::usuario));
                })
                .toList();
    }

    /** El movimiento guarda el correo (manuales) o el id del usuario (compras): se muestra el correo. */
    private String usuario(String usuarioId) {
        if (usuarioId.isBlank()) return "—";
        if (usuarioId.contains("@")) return usuarioId;
        return usuarios.findById(usuarioId).map(Usuario::getCorreo).orElse("—");
    }

    public byte[] excel(List<Fila> filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Movimientos");
            CellStyle titulo = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font negrita = wb.createFont();
            negrita.setBold(true);
            titulo.setFont(negrita);
            CellStyle fecha = wb.createCellStyle();
            fecha.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell c = cabecera.createCell(i);
                c.setCellValue(COLUMNAS[i]);
                c.setCellStyle(titulo);
            }
            int n = 1;
            for (Fila f : filas) {
                Row r = hoja.createRow(n++);
                Cell c0 = r.createCell(0);
                if (f.fecha() != null) { c0.setCellValue(f.fecha()); c0.setCellStyle(fecha); }
                r.createCell(1).setCellValue(f.producto());
                r.createCell(2).setCellValue(f.codigo() == null ? "" : f.codigo());
                r.createCell(3).setCellValue(f.tipo());
                r.createCell(4).setCellValue(f.cantidad());
                if (f.stockAntes() != null) r.createCell(5).setCellValue(f.stockAntes());
                if (f.stockDespues() != null) r.createCell(6).setCellValue(f.stockDespues());
                r.createCell(7).setCellValue(f.motivo());
                r.createCell(8).setCellValue(f.usuario());
            }
            hoja.createFreezePane(0, 1);
            for (int i = 0; i < COLUMNAS.length; i++) hoja.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el Excel de movimientos", e);
        }
    }

    /** PDF en A4 horizontal; la tabla se parte en páginas sola y repite la cabecera. */
    public byte[] pdf(List<Fila> filas, String tituloTienda, String resumenFiltros) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
            PdfWriter.getInstance(doc, out);
            doc.open();
            Color marca = BrandConfig.colorPrimarioAwt();
            Color tenue = new Color(107, 114, 128);
            doc.add(new Paragraph(BrandConfig.nombre(), new Font(Font.HELVETICA, 16, Font.BOLD, marca)));
            doc.add(new Paragraph("Movimientos de inventario" + (tituloTienda != null ? " · " + tituloTienda : ""),
                    new Font(Font.HELVETICA, 11, Font.BOLD, new Color(55, 65, 81))));
            Paragraph sub = new Paragraph(resumenFiltros + " · " + filas.size() + (filas.size() == 1 ? " movimiento" : " movimientos")
                    + " · Generado el " + LocalDateTime.now().format(FECHA), new Font(Font.HELVETICA, 8, Font.NORMAL, tenue));
            sub.setSpacingAfter(10);
            doc.add(sub);

            PdfPTable tabla = new PdfPTable(new float[]{11, 22, 10, 7, 7, 7, 8, 18, 14});
            tabla.setWidthPercentage(100);
            tabla.setHeaderRows(1);
            Font cab = new Font(Font.HELVETICA, 7.5f, Font.BOLD, tenue);
            for (String col : COLUMNAS) {
                PdfPCell c = new PdfPCell(new Phrase(col.toUpperCase(), cab));
                c.setBackgroundColor(new Color(249, 250, 251));
                c.setBorderColor(new Color(229, 231, 235));
                c.setPadding(5);
                tabla.addCell(c);
            }
            Font cuerpo = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(55, 65, 81));
            for (Fila f : filas) {
                celda(tabla, f.fecha() != null ? f.fecha().format(FECHA) : "—", cuerpo, Element.ALIGN_LEFT);
                celda(tabla, f.producto(), cuerpo, Element.ALIGN_LEFT);
                celda(tabla, f.codigo() == null ? "—" : f.codigo(), cuerpo, Element.ALIGN_LEFT);
                celda(tabla, f.tipo(), cuerpo, Element.ALIGN_LEFT);
                celda(tabla, String.valueOf(f.cantidad()), cuerpo, Element.ALIGN_RIGHT);
                celda(tabla, f.stockAntes() == null ? "—" : String.valueOf(f.stockAntes()), cuerpo, Element.ALIGN_RIGHT);
                celda(tabla, f.stockDespues() == null ? "—" : String.valueOf(f.stockDespues()), cuerpo, Element.ALIGN_RIGHT);
                celda(tabla, f.motivo().isBlank() ? "—" : f.motivo(), cuerpo, Element.ALIGN_LEFT);
                celda(tabla, f.usuario(), cuerpo, Element.ALIGN_LEFT);
            }
            if (filas.isEmpty()) {
                PdfPCell vacio = new PdfPCell(new Phrase("Ningún movimiento con estos filtros.", cuerpo));
                vacio.setColspan(COLUMNAS.length);
                vacio.setPadding(10);
                tabla.addCell(vacio);
            }
            doc.add(tabla);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de movimientos", e);
        }
    }

    private static void celda(PdfPTable tabla, String texto, Font fuente, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fuente));
        c.setHorizontalAlignment(alineacion);
        c.setBorderColor(new Color(243, 244, 246));
        c.setPadding(4);
        tabla.addCell(c);
    }

    /** Texto de los filtros para el encabezado del PDF. */
    public static String resumenFiltros(Filtros f) {
        StringBuilder s = new StringBuilder();
        if (f.tipo() != null && !f.tipo().isBlank()) s.append(TIPOS.getOrDefault(f.tipo(), f.tipo())).append("s");
        DateTimeFormatter dia = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (f.desde() != null || f.hasta() != null) {
            if (!s.isEmpty()) s.append(" · ");
            s.append(f.desde() != null ? "desde el " + f.desde().format(dia) : "").append(f.desde() != null && f.hasta() != null ? " " : "")
                    .append(f.hasta() != null ? "hasta el " + f.hasta().format(dia) : "");
        }
        if (f.busqueda() != null && !f.busqueda().isBlank()) {
            if (!s.isEmpty()) s.append(" · ");
            s.append("producto “").append(f.busqueda().trim()).append("”");
        }
        return s.isEmpty() ? "Todos los movimientos" : s.toString();
    }

    /** Como la pantalla (filtrarMovimientos): solo minúsculas, para exportar exactamente lo que se ve. */
    private static String normalizar(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).trim();
    }
}
