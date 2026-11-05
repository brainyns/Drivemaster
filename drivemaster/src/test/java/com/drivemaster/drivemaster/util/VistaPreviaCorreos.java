package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera con los builders reales una vista previa de cada correo y del PDF de la factura, con datos de
 * ejemplo, en target/vista-previa-correos/ (abre index.html en el navegador). No es una prueba: solo corre con
 * mvn test -Dtest=VistaPreviaCorreos -DvistaPrevia=true
 */
@EnabledIfSystemProperty(named = "vistaPrevia", matches = "true")
class VistaPreviaCorreos {

    private static final Path SALIDA = Path.of("target", "vista-previa-correos");
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 30, 15, 30);

    private final Map<String, List<String[]>> grupos = new LinkedHashMap<>();

    private void correo(String grupo, String nombre, String html) throws Exception {
        String archivo = grupos.values().stream().mapToInt(List::size).sum() + 1 + "-" + nombre.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-") + ".html";
        Files.writeString(SALIDA.resolve(archivo), html, StandardCharsets.UTF_8);
        grupos.computeIfAbsent(grupo, g -> new ArrayList<>()).add(new String[] { nombre, archivo });
    }

    private static Cotizacion cotizacion() {
        List<CotizacionItem> items = List.of(
                CotizacionItem.builder().tipo(CotizacionItem.REPUESTO).descripcion("Pastillas de freno delanteras").cantidad(2)
                        .productoNombre("Pastillas cerámicas BP-85020").tiendaNombre("Repuestos La Sabana").disponibilidad("ENCARGO").diasEntrega(3)
                        .precioUnitario(45_000.0).subtotal(90_000.0).build(),
                CotizacionItem.builder().tipo(CotizacionItem.MANO_OBRA).descripcion("Cambio de pastillas")
                        .modalidad(CotizacionItem.POR_HORAS).horas(1.5).precioUnitario(40_000.0).subtotal(60_000.0).build());
        return Cotizacion.builder().version(1).tallerNombre("Taller Los Cedros").vehiculoDescripcion("Chevrolet Spark GT 2021").items(items)
                .subtotalRepuestos(90_000.0).subtotalManoObra(60_000.0).subtotal(150_000.0)
                .ivaPct(19.0).ivaRepuestos(17_100.0).ivaManoObra(11_400.0).iva(28_500.0).total(178_500.0)
                .anticipoDiagnostico(45_000.0).totalAPagar(133_500.0).tiempoEstimadoHoras(4).diasEntregaRepuestos(3)
                .observaciones("Incluye revisión de discos.").vigenciaHasta(FECHA.plusDays(7)).build();
    }

    private static PedidoTienda.PedidoTiendaBuilder pedido() {
        List<CotizacionItem> items = List.of(
                CotizacionItem.builder().cantidad(2).productoNombre("Pastillas cerámicas").referencia("BP-85020").subtotal(90_000.0).build(),
                CotizacionItem.builder().cantidad(1).productoNombre("Disco ventilado").disponibilidad("ENCARGO").subtotal(173_800.0).build());
        return PedidoTienda.builder().id("p1").tiendaNombre("Repuestos La Sabana").items(items).iva(50_122.0).total(313_922.0).costoEnvio(12_000.0)
                .tiendaDireccion("Cra 70 # 44-21").tiendaCiudad("Medellín").tiendaHorario("L-S 8:00-18:00")
                .tallerNombre("Taller Los Cedros").tallerDireccion("Cra 70 # 40-10").tallerCiudad("Medellín").tallerTelefono("6044120087")
                .vehiculoDescripcion("Chevrolet Spark GT 2021").plazoDespachoHasta(FECHA.plusDays(1)).clienteNombre("Laura Gómez")
                .envioReceptor("Laura Gómez").envioTelefono("3004417721").envioDireccion("Calle 10 # 43-21").envioCiudad("Medellín")
                .envioIndicaciones("Dejar en portería.").notaDespacho("Guía Servientrega 2087731");
    }

    @Test
    void generar() throws Exception {
        Files.createDirectories(SALIDA);
        // Los mismos valores de producción: el PDF descarga el logo publicado y recorta su margen
        new BrandConfig("DriveMaster", "#F28C28", "#17191C", "#17191C", "", "80");
        Usuario laura = FacturaMontosTest.usuario();
        Files.write(SALIDA.resolve("factura.pdf"), PdfVentaBuilder.construir(laura, FacturaMontosTest.venta(), 0.19));

        // Los correos muestran el logo recortado del repositorio, aunque todavía no esté desplegado
        ReflectionTestUtils.setField(BrandConfig.class, "logoUrl",
                Path.of("..", "frontend", "public", "images", "Drivemaster_logo_correo.png").toAbsolutePath().normalize().toUri().toString());
        ReflectionTestUtils.setField(BrandConfig.class, "confirmarLogoNuevo", false);

        Vehiculo spark = Vehiculo.builder().marca("Chevrolet").modelo("Spark GT").anio(2021).placa("XYZ-123").build();
        OrdenServicio orden = OrdenServicio.builder().tallerNombre("Taller Los Cedros").productoNombre("Cambio de pastillas")
                .observaciones("Se cambiaron pastillas delanteras.").total(178_400.0).build();
        String cliente = "Laura Gómez";
        String veh = "Chevrolet Spark GT 2021";
        String taller = "Taller Los Cedros";
        String tienda = "Repuestos La Sabana";
        String dir = "Cra 70 # 40-10";
        PedidoTienda aRetirar = pedido().entrega(PedidoTienda.RETIRO).build();
        PedidoTienda aEnviar = pedido().id("p2").entrega(PedidoTienda.ENVIO).build();

        // Factura y recibos
        correo("Factura y recibos de pago", "Factura de venta", EmailVentaBuilder.construir(laura, FacturaMontosTest.venta(), 0.19));
        Map<String, Double> lineas = new LinkedHashMap<>();
        lineas.put("Cotización", 178_500.0);
        lineas.put("Diagnóstico ya pagado", -45_000.0);
        correo("Factura y recibos de pago", "Comprobante de pago Wompi", EmailServicioBuilder.clienteComprobantePago(cliente, "la cotización", veh,
                "DM-1C55C6-20261030", "1234567-1730300000-12345", FECHA, 133_500.0, lineas, "El taller ya pidió tus repuestos."));
        CompraCatalogo compra = CompraCatalogo.builder().subtotal(263_800.0).iva(50_122.0).costoEnvio(12_000.0).total(325_922.0).build();
        correo("Factura y recibos de pago", "Comprobante de compra del catalogo", EmailServicioBuilder.clienteCompraPagada(cliente, compra,
                List.of(aRetirar, aEnviar), Map.of("p1", "482913"), "DM-CAT-20261030", "1234567-1730300000-54321", FECHA));

        // Solicitud
        correo("Solicitud (pedidos)", "Pago recibido", EmailSolicitudBuilder.pagoRecibido(laura, 416_500));
        correo("Solicitud (pedidos)", "Pedido entregado", EmailSolicitudBuilder.entregado(laura));

        // Mantenimiento
        correo("Mantenimiento", "Recordatorio por kilometraje", EmailMantenimientoBuilder.recordatorioKm(laura, spark, 44_200, 40_000, 5_000));
        correo("Mantenimiento", "Cita confirmada", EmailMantenimientoBuilder.citaProgramada(laura, spark, FECHA.plusDays(2)));
        correo("Mantenimiento", "Mantenimiento registrado", EmailMantenimientoBuilder.mantenimientoRealizado(laura, spark, 45_000, 5_000));
        correo("Mantenimiento", "Vehiculo en el taller", EmailMantenimientoBuilder.servicioIniciado(laura, spark, orden));
        correo("Mantenimiento", "Vehiculo listo (anterior)", EmailMantenimientoBuilder.servicioFinalizado(laura, spark, orden));

        // Servicio: cliente
        String g = "Servicio: cliente";
        correo(g, "Taller asignado", EmailServicioBuilder.clienteTallerAsignado(cliente, veh, taller, dir, "Medellín"));
        correo(g, "Sin taller", EmailServicioBuilder.clienteSinTaller(cliente, veh));
        correo(g, "Cita de revision por pagar", EmailServicioBuilder.clienteCitaRevision(cliente, veh, taller, dir, FECHA.plusDays(1), "Jhon Ríos", 45_000, true, FECHA.plusHours(12)));
        correo(g, "Solicitud cancelada", EmailServicioBuilder.clienteSolicitudCancelada(cliente, veh, "Ya resolví el problema."));
        correo(g, "Cotizacion", EmailServicioBuilder.clienteCotizacion(cliente, cotizacion(), "https://drivemaster-orcin.vercel.app/cotizacion?id=1#token=ejemplo"));
        correo(g, "Pago no aplicado", EmailServicioBuilder.clientePagoNoAplicado(cliente, veh, 133_500, "La cotización ya no estaba vigente."));
        correo(g, "Pedido no despachado", EmailServicioBuilder.clientePedidoNoDespachado(cliente, veh, tienda, 90_000));
        correo(g, "Vehiculo listo", EmailServicioBuilder.clienteVehiculoListo(cliente, veh, taller, dir));
        correo(g, "Nuevo codigo de retiro", EmailServicioBuilder.clienteCodigoRetiroNuevo(cliente, aRetirar, "730254"));
        correo(g, "Pedido listo para retirar", EmailServicioBuilder.clientePedidoListoParaRetirar(cliente, aRetirar));
        correo(g, "Recordatorio de retiro", EmailServicioBuilder.clienteRecordatorioRetiro(cliente, aRetirar, 3));
        correo(g, "Retiro cancelado", EmailServicioBuilder.clienteRetiroCancelado(cliente, aRetirar, 313_922));
        correo(g, "Envio en camino", EmailServicioBuilder.clienteEnvioEnCamino(cliente, aEnviar, 5));
        correo(g, "Problema de envio resuelto", EmailServicioBuilder.clienteProblemaEnvioResuelto(cliente, tienda, true, 313_922));
        correo(g, "Compra no aplicada", EmailServicioBuilder.clienteCompraNoAplicada(cliente, 325_922, "Una tienda se quedó sin stock."));

        // Servicio: tienda, taller y administración
        g = "Servicio: tienda, taller y admin";
        correo(g, "Tienda nuevo pedido", EmailServicioBuilder.tiendaNuevoPedido(tienda, pedido().build()));
        correo(g, "Tienda nueva compra", EmailServicioBuilder.tiendaNuevaCompra(tienda, aEnviar));
        correo(g, "Tienda problema de envio", EmailServicioBuilder.tiendaProblemaEnvio(tienda, aEnviar, "La caja llegó abierta."));
        correo(g, "Taller nueva solicitud", EmailServicioBuilder.tallerNuevaSolicitud(taller, veh, "Chirrido al frenar.", "Laureles, Medellín", "Mañanas", FECHA.plusHours(8)));
        correo(g, "Taller diagnostico pagado", EmailServicioBuilder.tallerDiagnosticoPagado(taller, veh, FECHA.plusDays(1)));
        correo(g, "Taller cotizacion pagada", EmailServicioBuilder.tallerCotizacionPagada(taller, veh, 2));
        correo(g, "Taller pedido no despachado", EmailServicioBuilder.tallerPedidoNoDespachado(taller, veh, tienda, "Sin inventario."));
        correo(g, "Taller respuesta de cotizacion", EmailServicioBuilder.tallerRespuestaCotizacion(taller, veh, 1, "Pidió cambios", "¿Pueden revisar otra marca?"));
        correo(g, "Taller cotizacion vencida", EmailServicioBuilder.tallerCotizacionVencida(taller, veh, 1));
        correo(g, "Taller solicitud retirada", EmailServicioBuilder.tallerSolicitudRetirada(taller, veh, "El cliente canceló."));
        correo(g, "Alerta para admin", EmailServicioBuilder.adminAlerta("Pago con monto inválido", "La referencia DM-1C55C6 llegó con un monto distinto."));

        Files.writeString(SALIDA.resolve("index.html"), indice(), StandardCharsets.UTF_8);
    }

    /** Lista por grupos a la izquierda y el correo a la derecha, con ancho de escritorio o de celular. */
    private String indice() {
        StringBuilder lista = new StringBuilder();
        for (Map.Entry<String, List<String[]>> grupo : grupos.entrySet()) {
            lista.append("<h2>").append(PlantillaCorreo.e(grupo.getKey())).append("</h2>");
            for (String[] c : grupo.getValue()) {
                lista.append("<a href=\"").append(c[1]).append("\" target=\"vista\">").append(PlantillaCorreo.e(c[0])).append("</a>");
            }
        }
        lista.append("<h2>PDF</h2><a href=\"factura.pdf\" target=\"vista\">Factura de venta (PDF)</a>");
        return """
                <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"/><title>Vista previa de correos</title>
                <style>
                  body { margin:0; display:flex; height:100vh; font-family:'Segoe UI',Arial,sans-serif; color:#374151; }
                  nav { width:300px; flex-shrink:0; overflow-y:auto; padding:16px; box-sizing:border-box; border-right:1px solid #e5e7eb; }
                  h1 { margin:0; font-size:17px; } h2 { margin:18px 0 6px; font-size:12px; color:#6b7280; text-transform:uppercase; letter-spacing:1px; }
                  a { display:block; padding:5px 8px; border-radius:6px; font-size:14px; color:#374151; text-decoration:none; }
                  a:hover { background:#FEF3E9; }
                  main { flex:1; display:flex; flex-direction:column; align-items:center; background:#ffffff; min-width:0; }
                  .anchos { padding:10px; } button { font:inherit; padding:6px 12px; margin:0 4px; border:1px solid #d1d5db; border-radius:6px; background:#fff; cursor:pointer; }
                  iframe { flex:1; width:680px; max-width:100%%; border:1px solid #e5e7eb; }
                </style></head><body>
                <nav><h1>Correos de DriveMaster</h1>%s</nav>
                <main><div class="anchos"><button onclick="ancho('680px')">Escritorio</button><button onclick="ancho('375px')">Celular (375 px)</button></div>
                <iframe name="vista" title="Correo" src="1-factura-de-venta.html"></iframe></main>
                <script>function ancho(a) { document.querySelector('iframe').style.width = a; }</script>
                </body></html>
                """.formatted(lista);
    }
}
