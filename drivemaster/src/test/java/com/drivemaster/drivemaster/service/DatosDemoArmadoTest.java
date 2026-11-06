package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.CotizacionItem;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.util.Dinero;
import com.drivemaster.drivemaster.util.NitColombia;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.drivemaster.drivemaster.service.DatosDemoCatalogo.*;
import static org.junit.jupiter.api.Assertions.*;

/** El lote de la demo, armado en memoria: marcas para borrarlo, cuotas, la historia y que todo cuadre. */
class DatosDemoArmadoTest {

    static final DatosDemoArmado.Parametros PARAMETROS = new DatosDemoArmado.Parametros(19, 10, 50000, 2, 3);
    static DatosDemoArmado.Lote lote;

    static DatosDemoArmado.Lote armar() {
        AtomicInteger n = new AtomicInteger();
        return new DatosDemoArmado(PARAMETROS, p -> "bcrypt(" + p + ")", () -> String.format("DMO%05d", n.incrementAndGet()),
                (id, codigo) -> "h:" + id + ":" + codigo).armar(false);
    }

    @BeforeAll
    static void armarUnaVez() {
        lote = armar();
    }

    private static List<PedidoTienda> pedidos(String tienda, int mes) {
        return lote.pedidos.stream().filter(p -> p.getTiendaId().equals(tienda(tienda).id()) && p.getFechaCreacion().getMonthValue() == mes).toList();
    }

    private static double ventas(String tienda, int mes) {
        return pedidos(tienda, mes).stream().mapToDouble(PedidoTienda::getTotal).sum();
    }

    // ─── Marcas: todo lo demo se reconoce, nada real coincide ────────────────

    @Test
    void todo_lo_que_va_a_mongo_tiene_id_demo() {
        List<String> ids = new ArrayList<>();
        lote.usuarios.forEach(x -> ids.add(x.getId()));
        lote.vehiculos.forEach(x -> ids.add(x.getId()));
        lote.tiendas.forEach(x -> ids.add(x.getId()));
        lote.talleres.forEach(x -> ids.add(x.getId()));
        lote.productos.forEach(x -> ids.add(x.getId()));
        lote.compras.forEach(x -> ids.add(x.getId()));
        lote.pedidos.forEach(x -> ids.add(x.getId()));
        lote.movimientos.forEach(x -> ids.add(x.getId()));
        lote.ordenes.forEach(x -> ids.add(x.getId()));
        lote.cotizaciones.forEach(x -> ids.add(x.getId()));
        assertFalse(ids.isEmpty());
        for (String id : ids) assertTrue(DatosDemoService.ES_DEMO.matcher(id).find(), id);
        assertEquals(ids.size(), new HashSet<>(ids).size(), "sin ids repetidos");
        // Lo que apunta a otro documento también es demo: borrar no deja huérfanos ni toca lo real
        lote.pedidos.forEach(p -> {
            assertTrue(p.getTiendaId().startsWith(PREFIJO));
            assertTrue(p.getClienteId().startsWith(PREFIJO));
        });
        lote.movimientos.forEach(m -> assertTrue(m.getProductoId().startsWith(PREFIJO) && m.getTiendaId().startsWith(PREFIJO)));
    }

    @Test
    void la_marca_demo_no_coincide_con_ids_reales() {
        for (String real : new String[]{"6543a1b2c3d4e5f6a7b8c9d0", "DRV-ABC12LX9Q", "xdemo-1", "Demo-tienda", "tienda-demo-1", ""}) {
            assertFalse(DatosDemoService.ES_DEMO.matcher(real).find(), real);
        }
    }

    @Test
    void los_pagos_son_DEMO_y_las_liquidaciones_SIM_DEMO() {
        lote.ventas.forEach(v -> {
            assertTrue(v.pago().getReferencia().startsWith(REF_PAGO), v.pago().getReferencia());
            assertEquals("PAGADO", v.pago().getEstado());
            assertNull(v.pago().getId(), "el id lo pone Postgres");
            v.lineas().stream().filter(l -> RepartoPago.LIQUIDADO.equals(l.getEstado())).forEach(l -> {
                assertTrue(l.getReferenciaLiquidacion().startsWith(REF_LIQUIDACION), l.getReferenciaLiquidacion());
                assertEquals("SIMULADO", l.getModoLiquidacion());
                assertTrue(l.getFechaLiquidacion().isAfter(l.getFechaCreacion()));
            });
        });
        assertEquals(lote.ventas.size(), lote.ventas.stream().map(v -> v.pago().getReferencia()).distinct().count());
    }

    @Test
    void es_determinista_para_saber_si_la_base_ya_lo_tiene() {
        DatosDemoArmado.Lote otro = armar();
        assertEquals(lote.pedidos.size(), otro.pedidos.size());
        assertEquals(lote.pedidos.stream().mapToDouble(PedidoTienda::getTotal).sum(), otro.pedidos.stream().mapToDouble(PedidoTienda::getTotal).sum());
        assertEquals(lote.pedidos.get(17).getFechaCreacion(), otro.pedidos.get(17).getFechaCreacion());
    }

    @Test
    void los_nit_demo_son_validos_y_distintos() {
        List<String> nits = Stream.concat(TIENDAS.stream().map(TiendaDemo::nit), TALLERES.stream().map(TallerDemo::nit)).toList();
        nits.forEach(n -> assertEquals(n, NitColombia.normalizar(n)));
        assertEquals(nits.size(), new HashSet<>(nits).size());
        assertEquals(8, NitColombia.digitoVerificacion("890903938"), "el NIT de Bancolombia: 890.903.938-8");
    }

    @Test
    void los_correos_usan_el_alias_de_gmail() {
        lote.usuarios.forEach(u -> assertTrue(u.getCorreo().matches("brainy(796|blancoh)\\+[a-z.]+@gmail\\.com"), u.getCorreo()));
        CLIENTES.forEach(c -> assertTrue(c.correo().startsWith("brainy796+")));
        TIENDAS.forEach(t -> assertTrue(t.correo().startsWith("brainyblancoh+")));
        assertEquals(lote.usuarios.size(), lote.usuarios.stream().map(u -> u.getCorreo()).distinct().count());
        assertEquals("bcrypt(" + PASSWORD + ")", lote.usuarios.get(0).getPassword(), "la contraseña va encriptada");
    }

    // ─── Cantidades y fechas ─────────────────────────────────────────────────

    @Test
    void cuotas_por_tienda_y_mes() {
        assertEquals(22, pedidos(BOCAGRANDE, 10).size());
        assertEquals(20, pedidos(MANGA, 10).size());
        assertEquals(12, pedidos(CRESPO, 10).size());
        assertEquals(10, pedidos(PRADO, 10).size());
        assertEquals(6, pedidos(EL_BOSQUE, 10).size());
        assertEquals(10, pedidos(EL_BOSQUE, 11).size());
        assertEquals(1, pedidos(MANGA, 11).size());
        long octubre = lote.pedidos.stream().filter(p -> p.getFechaCreacion().getMonthValue() == 10).count();
        long noviembre = lote.pedidos.size() - octubre;
        assertTrue(octubre >= 60 && octubre <= 80, "octubre: " + octubre);
        assertTrue(noviembre >= 20 && noviembre <= 30, "noviembre: " + noviembre);
        assertEquals(22, CLIENTES.size());
        assertEquals(4, lote.ordenes.size());
        for (String t : SURTIDO.keySet()) assertTrue(SURTIDO.get(t).size() >= 15 && SURTIDO.get(t).size() <= 25);
    }

    @Test
    void las_fechas_van_del_1_de_octubre_al_6_de_noviembre_y_nada_es_ahora() {
        LocalDateTime desde = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime hasta = LocalDateTime.of(2026, 11, 7, 0, 0);
        lote.pedidos.forEach(p -> assertTrue(!p.getFechaCreacion().isBefore(desde) && p.getFechaCreacion().isBefore(hasta)));
        lote.pedidos.stream().filter(p -> p.getFechaCreacion().toLocalDate().equals(DatosDemoArmado.FIN_NOVIEMBRE))
                .forEach(p -> assertTrue(p.getFechaCreacion().getHour() < 12, "el 6 de noviembre, solo en la mañana"));
        lote.ventas.forEach(v -> {
            assertTrue(v.pago().getFechaPago().isBefore(hasta));
            v.lineas().forEach(l -> assertEquals(v.pago().getFechaPago(), l.getFechaCreacion(), "la línea, con la fecha del pago"));
        });
        lote.movimientos.forEach(m -> assertTrue(m.getFecha().isBefore(hasta)));
    }

    @Test
    void octubre_cerrado_y_noviembre_con_pedidos_en_curso() {
        lote.pedidos.stream().filter(p -> p.getFechaCreacion().getMonthValue() == 10)
                .forEach(p -> assertTrue(DatosDemoArmado.cerrado(p.getEstado()), p.getId() + " " + p.getEstado()));
        Set<String> enCurso = new HashSet<>();
        lote.pedidos.stream().filter(p -> !DatosDemoArmado.cerrado(p.getEstado())).forEach(p -> enCurso.add(p.getEstado()));
        assertEquals(Set.of(PedidoTienda.PENDIENTE, PedidoTienda.EN_PREPARACION, PedidoTienda.LISTO_PARA_RETIRAR, PedidoTienda.DESPACHADO), enCurso);
        lote.pedidos.forEach(p -> {
            if (PedidoTienda.ENVIO.equals(p.getEntrega())) {
                assertNotNull(p.getEnvioDireccion());
                assertNull(p.getCodigoRetiroHash());
            } else {
                assertNotNull(p.getCodigoRetiroHash(), "un retiro siempre tiene su código");
                assertNotNull(p.getTiendaDireccion());
            }
            if (PedidoTienda.RETIRADO.equals(p.getEstado())) assertTrue(p.getCodigoRetiroUsado());
            if (PedidoTienda.ENTREGADO.equals(p.getEstado())) assertNotNull(p.getFechaRecibidoCliente());
            if (PedidoTienda.DESPACHADO.equals(p.getEstado())) assertNotNull(p.getFechaDespacho());
            assertTrue(p.getAlertaAtrasoEnviada(), "sin alertas de despacho por pedidos demo");
        });
        assertFalse(lote.codigosRetiro.isEmpty(), "hay códigos para validar un retiro en la feria");
        lote.codigosRetiro.forEach((pedido, codigo) -> assertEquals(PedidoTienda.LISTO_PARA_RETIRAR,
                lote.pedidos.stream().filter(p -> p.getId().equals(pedido)).findFirst().orElseThrow().getEstado()));
    }

    // ─── La historia ─────────────────────────────────────────────────────────

    @Test
    void repuestos_el_bosque_vende_mas_en_6_dias_de_noviembre_que_en_todo_octubre() {
        assertTrue(ventas(EL_BOSQUE, 11) > 2 * ventas(EL_BOSQUE, 10), ventas(EL_BOSQUE, 11) + " vs " + ventas(EL_BOSQUE, 10));
        assertTrue(pedidos(EL_BOSQUE, 11).size() > pedidos(EL_BOSQUE, 10).size());
    }

    @Test
    void autopartes_manga_casi_no_vende_en_noviembre() {
        assertTrue(ventas(MANGA, 11) < 0.1 * ventas(MANGA, 10), ventas(MANGA, 11) + " vs " + ventas(MANGA, 10));
    }

    @Test
    void el_producto_estrella_y_el_que_se_deja_de_vender() {
        assertTrue(DatosDemoArmado.unidadesPorProducto(lote).get(0).getKey().startsWith("Pastillas de freno delanteras Bosch"));
        String aceite = plantilla(SE_DEJA_DE_VENDER).nombre();
        int octubre = 0, noviembre = 0;
        for (PedidoTienda p : lote.pedidos) {
            for (CotizacionItem i : p.getItems()) {
                if (!aceite.equals(i.getProductoNombre())) continue;
                if (p.getFechaCreacion().getMonthValue() == 10) octubre += i.getCantidad();
                else noviembre += i.getCantidad();
            }
        }
        assertTrue(octubre >= 10, "en octubre se vende: " + octubre);
        assertEquals(0, noviembre, "en noviembre ya no");
    }

    @Test
    void carlos_mendoza_es_el_cliente_que_mas_compra() {
        Map<String, Long> porCliente = new HashMap<>();
        lote.pedidos.forEach(p -> porCliente.merge(p.getClienteId(), 1L, Long::sum));
        long carlos = porCliente.get(cliente(FRECUENTE).id());
        porCliente.forEach((c, n) -> { if (!c.equals(cliente(FRECUENTE).id())) assertTrue(n < carlos, c + " " + n); });
    }

    @Test
    void el_bosque_tiene_que_reponer_y_manga_tiene_productos_quietos() {
        List<String> reponer = lote.productos.stream().filter(p -> p.getTiendaId().equals(tienda(EL_BOSQUE).id())
                && p.getStockActual() <= p.getStockMinimo()).map(Producto::getId).toList();
        assertTrue(reponer.containsAll(List.of(productoId(EL_BOSQUE, ESTRELLA), productoId(EL_BOSQUE, "aceite-5w30"),
                productoId(EL_BOSQUE, "bateria-60"))), reponer.toString());
        for (String quieto : MANGA_QUIETOS) {
            String id = productoId(MANGA, quieto);
            assertTrue(lote.pedidos.stream().flatMap(p -> p.getItems().stream()).noneMatch(i -> i.getProductoId().equals(id)), quieto);
            assertTrue(lote.productos.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow().getStockActual() > 0);
        }
    }

    // ─── Que todo cuadre ─────────────────────────────────────────────────────

    @Test
    void cada_pago_cuadra_con_sus_lineas_y_la_comision_va_sobre_el_precio_con_iva_sin_envio() {
        Map<String, PedidoTienda> pedidos = new HashMap<>();
        lote.pedidos.forEach(p -> pedidos.put(p.getId(), p));
        Map<String, CompraCatalogo> compras = new HashMap<>();
        lote.compras.forEach(c -> compras.put(c.getId(), c));
        for (DatosDemoArmado.Venta v : lote.ventas) {
            double netos = v.lineas().stream().mapToDouble(RepartoPago::getMonto).sum();
            double comision = v.lineas().stream().mapToDouble(RepartoPago::getComisionRetenida).sum();
            assertEquals(v.pago().getMonto(), netos + comision, 0.001, v.pago().getReferencia() + ": netos + comisión = lo pagado");
            for (RepartoPago l : v.lineas()) {
                assertEquals(10.0, l.getComisionPct());
                assertEquals(l.getComisionCalculada(), l.getComisionRetenida(), "retenida: la bandera está encendida");
                assertEquals(Dinero.aDouble(ComisionPlataforma.monto(BigDecimal.valueOf(l.getComisionBase()), 10)), l.getComisionRetenida());
                assertNotNull(l.getIvaIncluido(), "ivaIncluido lleno");
                assertEquals(l.getMontoBruto(), l.getMonto() + l.getComisionRetenida(), 0.001);
                if (l.getPedidoTiendaId() == null) continue;
                PedidoTienda p = pedidos.get(l.getPedidoTiendaId());
                assertEquals(p.getSubtotal() + p.getIva(), l.getComisionBase(), 0.001, "productos con IVA, sin envío");
                assertEquals(p.getIva(), l.getIvaIncluido());
                assertEquals(p.getSubtotal() + p.getIva() + p.getCostoEnvio(), p.getTotal(), 0.001);
                assertEquals(Math.round(p.getSubtotal() * 0.19), p.getIva(), 0.001, "IVA del 19 %");
                assertEquals(DatosDemoArmado.cerrado(p.getEstado()), RepartoPago.LIQUIDADO.equals(l.getEstado()),
                        "se liquida lo cerrado y lo en curso espera");
                CompraCatalogo c = compras.get(p.getCompraId());
                assertEquals(c.getTotal(), v.pago().getMonto(), 0.001);
                assertEquals(c.getTotal(), p.getTotal(), 0.001);
            }
        }
    }

    @Test
    void el_ejemplo_de_un_producto_de_19500_con_envio() {
        // Lo mismo que el test de la comisión: 19.500 + IVA 3.705 + envío = productos con IVA 23.205 → comisión 2.321
        assertEquals(2321.0, Dinero.aDouble(ComisionPlataforma.monto(ComisionPlataforma.baseConIva(19500.0, 3705.0), 10)));
    }

    @Test
    void el_stock_nunca_queda_negativo_y_cuadra_con_sus_movimientos() {
        Map<String, Integer> saldo = new HashMap<>();
        for (MovimientoInventario m : lote.movimientos) {
            int antes = saldo.getOrDefault(m.getProductoId(), 0);
            assertEquals(antes, m.getStockAnterior(), m.getId());
            int despues = "ENTRADA".equals(m.getTipo()) ? antes + m.getCantidad() : antes - m.getCantidad();
            assertEquals(despues, m.getStockNuevo(), m.getId());
            assertTrue(despues >= 0, m.getId());
            saldo.put(m.getProductoId(), despues);
        }
        lote.productos.forEach(p -> assertEquals(saldo.get(p.getId()), p.getStockActual(), p.getId()));
        long salidas = lote.movimientos.stream().filter(m -> "SALIDA".equals(m.getTipo())).count();
        long lineas = lote.pedidos.stream().mapToLong(p -> p.getItems().size()).sum();
        assertEquals(lineas, salidas, "una salida por cada línea vendida");
    }

    @Test
    void los_talleres_compran_con_el_20_por_ciento_y_lo_que_llego_entra_a_su_inventario() {
        List<PedidoTienda> deTalleres = lote.pedidos.stream().filter(p -> p.getCompradorTallerId() != null).toList();
        assertEquals(6, deTalleres.size());
        deTalleres.forEach(p -> p.getItems().forEach(i -> assertEquals(Math.round(i.getPrecioPublico() * 0.8), i.getPrecioUnitario(), 0.001)));
        assertEquals(deTalleres.stream().filter(p -> DatosDemoArmado.cerrado(p.getEstado())).count(), lote.entradasTaller.size());
    }

    @Test
    void las_ordenes_de_taller_cobran_comision_una_sola_vez_sobre_lo_que_paga_el_cliente() {
        for (OrdenServicio o : lote.ordenes) {
            assertEquals("ENTREGADA", o.getEstado());
            List<DatosDemoArmado.Venta> pagos = lote.ventas.stream().filter(v -> o.getId().equals(v.ordenId())).toList();
            assertEquals(2, pagos.size(), "diagnóstico y cotización");
            double pagado = pagos.stream().mapToDouble(v -> v.pago().getMonto()).sum();
            double comision = pagos.stream().flatMap(v -> v.lineas().stream()).mapToDouble(RepartoPago::getComisionRetenida).sum();
            // diagnóstico con IVA + (cotización con IVA − adelanto) = lo que pagó el cliente en total
            assertEquals(Math.round(pagado * 0.10), comision, 1.0, o.getId());
            pagos.forEach(v -> v.lineas().forEach(l -> assertEquals(RepartoPago.TALLER, l.getBeneficiarioTipo())));
        }
    }

    @Test
    void el_resumen_trae_las_ventas_la_comision_y_el_top() {
        String r = DatosDemoArmado.resumen(lote);
        assertTrue(r.contains("Repuestos El Bosque 2026-11"));
        assertTrue(r.contains("Comisión 2026-10"));
        assertTrue(r.contains("Top: Pastillas de freno delanteras Bosch"));
    }
}
