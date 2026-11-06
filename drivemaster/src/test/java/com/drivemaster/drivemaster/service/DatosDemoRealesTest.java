package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.drivemaster.drivemaster.service.DatosDemoCatalogo.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Ventas demo para negocios que YA existen (La Vaquita, Los Rios y Taller los hermanos): nada de ellos cambia salvo el
 * stock y el inventario, y borrar los deja exactamente como estaban.
 */
class DatosDemoRealesTest {

    static final String VAQUITA = "64f0a1b2c3d4e5f6a7b8c901";
    static final String RIOS = "64f0a1b2c3d4e5f6a7b8c902";
    static final String HERMANOS = "64f0a1b2c3d4e5f6a7b8c903";

    static Producto producto(String id, String tienda, String nombre, double precio, Integer stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setTiendaId(tienda);
        p.setNombre(nombre);
        p.setMarca("Genérica");
        p.setReferencia("REF-" + id.substring(id.length() - 3));
        p.setCategoria("Frenos");
        p.setPrecioVenta(precio);
        p.setPrecioCompra(precio * 0.7);
        p.setStockActual(stock);
        p.setStockMinimo(2);
        p.setTipo("STOCK");
        p.setActivo(true);
        return p;
    }

    static Tienda vaquita() {
        return Tienda.builder().id(VAQUITA).nombre("AUTOPARTE LA VAQUITA").ciudad("Cartagena").direccion("Calle 1 # 2-3")
                .horario("L-S 8-6").costoEnvio(5000.0).nit("900111222-3").estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
    }

    static Tienda rios() {
        return Tienda.builder().id(RIOS).nombre("Tienda Los Rios").ciudad("Cartagena").direccion("Carrera 4 # 5-6")
                .horario("L-V 8-5").nit("900333444-5").estadoAprobacion(EstadoAprobacion.APROBADO).activo(true).build();
    }

    static Taller hermanos() {
        return Taller.builder().id(HERMANOS).usuarioId("64f0a1b2c3d4e5f6a7b8c9aa").nombre("Taller los hermanos").ciudad("Cartagena")
                .direccion("Calle 9 # 9-9").telefono("3001112233").tarifaHora(55000.0).responsableIva(false)
                .estadoAprobacion(EstadoAprobacion.APROBADO).activo(true)
                .mecanicos(new ArrayList<>(List.of(Mecanico.builder().id("m1").nombre("Pedro").activo(true).build()))).build();
    }

    static List<Producto> productosVaquita() {
        List<Producto> l = new ArrayList<>();
        String[] nombres = {"Pastillas de freno", "Filtro de aceite", "Aceite 20W-50", "Bujías", "Amortiguador", "Correa de repartición"};
        for (int i = 0; i < nombres.length; i++) l.add(producto(VAQUITA.substring(0, 21) + "a0" + i, VAQUITA, nombres[i], 30000 + i * 9000, i == 5 ? 40 : 1));
        Producto encargo = producto(VAQUITA.substring(0, 21) + "a09", VAQUITA, "Motor completo", 3_000_000, 0);
        encargo.setTipo("ENCARGO");
        l.add(encargo);
        return l;
    }

    static List<Producto> productosRios() {
        return new ArrayList<>(List.of(
                producto(RIOS.substring(0, 21) + "b01", RIOS, "Bomba de dirección hidráulica Renault Logan", 420000, 2),
                producto(RIOS.substring(0, 21) + "b02", RIOS, "Bomba de dirección Chevrolet Aveo", 380000, 1),
                producto(RIOS.substring(0, 21) + "b03", RIOS, "Cuerpo de aceleración Chevrolet Spark", 310000, 0),
                producto(RIOS.substring(0, 21) + "b04", RIOS, "Tapete de caucho", 25000, 30),
                producto(RIOS.substring(0, 21) + "b05", RIOS, "Ambientador", 8000, 50)));
    }

    static DatosDemoArmado.Reales reales() {
        return new DatosDemoArmado.Reales(vaquita(), productosVaquita(), rios(), productosRios(), hermanos(), "hermanos@correo.co");
    }

    static DatosDemoArmado.Lote armar(DatosDemoArmado.Reales r) {
        AtomicInteger n = new AtomicInteger();
        return new DatosDemoArmado(DatosDemoArmadoTest.PARAMETROS, p -> "x", () -> String.format("DMO%05d", n.incrementAndGet()),
                (id, c) -> "h:" + id).armar(false, r);
    }

    private static List<PedidoTienda> catalogo(DatosDemoArmado.Lote lote, String tienda, int mes) {
        return lote.pedidos.stream().filter(p -> p.getTiendaId().equals(tienda) && p.getFechaCreacion().getMonthValue() == mes).toList();
    }

    // ─── El armado ───────────────────────────────────────────────────────────

    @Nested
    class Armado {
        DatosDemoArmado.Reales r = reales();
        DatosDemoArmado.Lote lote = armar(r);

        @Test
        void cuotas_de_la_vaquita_y_los_rios() {
            assertEquals(12, catalogo(lote, VAQUITA, 10).size());
            assertEquals(5, catalogo(lote, VAQUITA, 11).size());
            assertEquals(3, catalogo(lote, RIOS, 10).size());
            assertEquals(5, catalogo(lote, RIOS, 11).size());
        }

        @Test
        void la_vaquita_es_estable_y_tiene_un_producto_que_sube() {
            double oct = catalogo(lote, VAQUITA, 10).stream().mapToDouble(PedidoTienda::getTotal).sum() / 31;
            double nov = catalogo(lote, VAQUITA, 11).stream().mapToDouble(PedidoTienda::getTotal).sum() / 6;
            assertTrue(nov > oct * 0.6 && nov < oct * 3, "por día: octubre " + oct + ", noviembre " + nov);
            String sube = VAQUITA.substring(0, 21) + "a05"; // el de más stock
            int octubre = unidades(catalogo(lote, VAQUITA, 10), sube), noviembre = unidades(catalogo(lote, VAQUITA, 11), sube);
            assertTrue(noviembre > octubre, "sube: " + octubre + " → " + noviembre);
        }

        private int unidades(List<PedidoTienda> pedidos, String producto) {
            return pedidos.stream().flatMap(p -> p.getItems().stream()).filter(i -> i.getProductoId().equals(producto))
                    .mapToInt(i -> i.getCantidad()).sum();
        }

        @Test
        void los_rios_crece_y_vende_sobre_todo_bombas_y_cuerpos_de_aceleracion() {
            double oct = catalogo(lote, RIOS, 10).stream().mapToDouble(PedidoTienda::getTotal).sum();
            double nov = catalogo(lote, RIOS, 11).stream().mapToDouble(PedidoTienda::getTotal).sum();
            assertTrue(nov > oct, oct + " → " + nov);
            int foco = 0, total = 0;
            for (PedidoTienda p : catalogo(lote, RIOS, 10)) for (var i : p.getItems()) { total += i.getCantidad(); if (i.getProductoNombre().matches("(Bomba|Cuerpo).*")) foco += i.getCantidad(); }
            for (PedidoTienda p : catalogo(lote, RIOS, 11)) for (var i : p.getItems()) { total += i.getCantidad(); if (i.getProductoNombre().matches("(Bomba|Cuerpo).*")) foco += i.getCantidad(); }
            assertTrue(foco >= total * 0.6, foco + " de " + total);
        }

        @Test
        void no_cambia_nada_de_las_tiendas_ni_de_sus_productos_salvo_el_stock_al_escribirse() {
            List<Producto> originales = productosVaquita();
            originales.addAll(productosRios());
            List<Producto> usados = new ArrayList<>(r.productosVaquita());
            usados.addAll(r.productosRios());
            for (int i = 0; i < originales.size(); i++) assertEquals(originales.get(i), usados.get(i), "el armado no toca el objeto real");
            assertTrue(lote.productos.stream().allMatch(p -> p.getId().startsWith(PREFIJO)), "los productos reales no se insertan");
            assertTrue(lote.tiendas.stream().allMatch(t -> t.getId().startsWith(PREFIJO)), "ni las tiendas");
            assertTrue(lote.talleres.stream().allMatch(t -> t.getId().startsWith(PREFIJO)), "ni el taller");
            catalogo(lote, VAQUITA, 10).forEach(p -> p.getItems().forEach(i -> assertEquals(
                    originales.stream().filter(o -> o.getId().equals(i.getProductoId())).findFirst().orElseThrow().getPrecioVenta(),
                    i.getPrecioUnitario(), "con su precio de hoy")));
            assertTrue(lote.pedidos.stream().flatMap(p -> p.getItems().stream()).noneMatch(i -> "Motor completo".equals(i.getProductoNombre())),
                    "un producto por encargo no se usa");
        }

        @Test
        void el_stock_real_nunca_queda_negativo_y_la_entrada_demo_solo_aparece_si_falta() {
            Map<String, Integer> saldo = new HashMap<>();
            for (DatosDemoArmado.AjusteStock a : lote.ajustesStock) saldo.put(a.productoId(), a.original() == null ? 0 : a.original());
            for (MovimientoInventario m : lote.movimientos) {
                if (!saldo.containsKey(m.getProductoId())) continue;
                assertTrue(m.getId().startsWith(PREFIJO), m.getId());
                assertEquals(saldo.get(m.getProductoId()), m.getStockAnterior(), m.getId());
                int s = "ENTRADA".equals(m.getTipo()) ? saldo.get(m.getProductoId()) + m.getCantidad() : saldo.get(m.getProductoId()) - m.getCantidad();
                assertTrue(s >= 0, m.getId());
                saldo.put(m.getProductoId(), s);
            }
            for (DatosDemoArmado.AjusteStock a : lote.ajustesStock) assertEquals(a.fin(), saldo.get(a.productoId()), a.productoId());
            assertTrue(lote.movimientos.stream().anyMatch(m -> m.getId().startsWith(PREFIJO + "mi-real-") && "ENTRADA".equals(m.getTipo())),
                    "con stock 0 o 1 hace falta comprar");
            String conStock = VAQUITA.substring(0, 21) + "a05";
            assertTrue(lote.movimientos.stream().noneMatch(m -> m.getProductoId().equals(conStock) && "ENTRADA".equals(m.getTipo())),
                    "con 40 en stock no hace falta");
        }

        @Test
        void todo_lo_nuevo_lleva_la_marca_demo_y_cuadra() {
            lote.pedidos.forEach(p -> assertTrue(p.getId().startsWith(PREFIJO)));
            lote.compras.forEach(c -> assertTrue(c.getId().startsWith(PREFIJO)));
            lote.ordenes.forEach(o -> assertTrue(o.getId().startsWith(PREFIJO)));
            for (DatosDemoArmado.Venta v : lote.ventas) {
                assertTrue(v.pago().getReferencia().startsWith(REF_PAGO));
                double netos = v.lineas().stream().mapToDouble(RepartoPago::getMonto).sum();
                double comision = v.lineas().stream().mapToDouble(RepartoPago::getComisionRetenida).sum();
                assertEquals(v.pago().getMonto(), netos + comision, 0.001, v.pago().getReferencia());
                v.lineas().forEach(l -> {
                    assertNotNull(l.getIvaIncluido());
                    if (RepartoPago.LIQUIDADO.equals(l.getEstado())) assertTrue(l.getReferenciaLiquidacion().startsWith(REF_LIQUIDACION));
                });
            }
        }

        @Test
        void taller_los_hermanos_tiene_sus_ordenes_cerradas_con_repuestos_de_tiendas_demo_y_sus_compras() {
            List<OrdenServicio> ordenes = lote.ordenes.stream().filter(o -> HERMANOS.equals(o.getTallerId())).toList();
            assertEquals(3, ordenes.stream().filter(o -> o.getFechaCreacion().getMonthValue() == 10).count());
            assertEquals(1, ordenes.stream().filter(o -> o.getFechaCreacion().getMonthValue() == 11).count());
            for (OrdenServicio o : ordenes) {
                assertEquals("ENTREGADA", o.getEstado());
                assertTrue(o.getUsuarioId().startsWith(PREFIJO), "con clientes demo");
                assertEquals(50000.0, o.getTarifaDiagnostico(), "no responsable de IVA: el diagnóstico va sin IVA");
                PedidoTienda repuestos = lote.pedidos.stream().filter(p -> o.getId().equals(p.getOrdenId())).findFirst().orElseThrow();
                assertEquals(PedidoTienda.ORIGEN_TALLER, repuestos.getOrigen());
                assertEquals(PedidoTienda.RECIBIDO, repuestos.getEstado());
                assertTrue(repuestos.getTiendaId().startsWith(PREFIJO), "de una tienda demo");
                DatosDemoArmado.Venta cot = lote.ventas.stream().filter(v -> o.getId().equals(v.ordenId()) && v.esCotizacion()).findFirst().orElseThrow();
                assertEquals(2, cot.lineas().size(), "la tienda y el taller");
                RepartoPago tienda = cot.lineas().get(0);
                assertEquals(repuestos.getTotal(), tienda.getMontoBruto(), 0.001);
                assertEquals(repuestos.getIva(), tienda.getIvaIncluido());
                assertEquals(0.0, cot.lineas().get(1).getIvaIncluido(), "la mano de obra de un no responsable no lleva IVA");
            }
            List<PedidoTienda> compras = lote.pedidos.stream().filter(p -> HERMANOS.equals(p.getCompradorTallerId())).toList();
            assertEquals(2, compras.size());
            compras.forEach(p -> p.getItems().forEach(i -> assertEquals(Math.round(i.getPrecioPublico() * 0.8), i.getPrecioUnitario(), 0.001)));
            assertEquals(2, lote.entradasTaller.stream().filter(e -> HERMANOS.equals(e.tallerId())).count());
            assertEquals(HERMANOS, lote.tallerReal);
        }

        @Test
        void la_historia_de_las_tiendas_demo_no_cambia_por_los_negocios_reales() {
            DatosDemoArmado.Lote soloDemo = DatosDemoArmadoTest.armar();
            for (DatosDemoCatalogo.TiendaDemo t : TIENDAS) {
                for (int mes : new int[]{10, 11}) {
                    double a = soloDemo.pedidos.stream().filter(p -> p.getTiendaId().equals(t.id()) && p.getFechaCreacion().getMonthValue() == mes)
                            .mapToDouble(PedidoTienda::getTotal).sum();
                    double b = lote.pedidos.stream().filter(p -> p.getTiendaId().equals(t.id()) && p.getFechaCreacion().getMonthValue() == mes
                            && !HERMANOS.equals(p.getCompradorTallerId()) && !HERMANOS.equals(p.getTallerId())).mapToDouble(PedidoTienda::getTotal).sum();
                    assertEquals(a, b, 0.001, t.nombre() + " " + mes);
                }
            }
        }

        @Test
        void los_rios_sin_bombas_ni_cuerpos_avisa_y_usa_lo_que_tiene() {
            List<Producto> sinFoco = List.of(producto(RIOS.substring(0, 21) + "b09", RIOS, "Tapete", 25000, 30));
            DatosDemoArmado.Lote l = armar(new DatosDemoArmado.Reales(null, List.of(), rios(), sinFoco, null, null));
            assertTrue(l.avisos.stream().anyMatch(a -> a.contains("bombas de dirección")), l.avisos.toString());
            assertEquals(8, l.pedidos.stream().filter(p -> RIOS.equals(p.getTiendaId())).count());
        }
    }

    // ─── Sembrar y borrar contra una base en memoria ─────────────────────────

    @Nested
    class SembrarYBorrar {
        MongoTemplate mongo = mock(MongoTemplate.class);
        PagoRepository pagos = mock(PagoRepository.class);
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        InventarioTallerService inventario = mock(InventarioTallerService.class);
        DatosDemoService servicio;
        final Map<String, Integer> stockBase = new LinkedHashMap<>();
        final Map<String, Producto> productos = new LinkedHashMap<>();
        final Map<String, Document> marcas = new HashMap<>();
        final Map<String, Document> piezas = new LinkedHashMap<>();
        Map<String, Document> piezasAntes;
        Map<String, Integer> stockAntes;
        final List<RepartoPago> lineas = new ArrayList<>();
        long id = 1;
        Tienda tiendaVaquita = vaquita();

        @BeforeEach
        void setUp() {
            for (Producto p : productosVaquita()) productos.put(p.getId(), p);
            for (Producto p : productosRios()) productos.put(p.getId(), p);
            productos.values().forEach(p -> stockBase.put(p.getId(), p.getStockActual()));
            // Inventario real de Taller los hermanos: una pieza que la compra demo va a tocar y otra que no
            piezas.put(HERMANOS + "|BOSCH|BP1234", new Document("_id", HERMANOS + "|BOSCH|BP1234").append("tallerId", HERMANOS)
                    .append("stock", 2).append("version", 4L).append("lotes", new ArrayList<>(List.of(
                            new Document("_id", "real-lote").append("pedidoId", "64f0aaaaaaaaaaaaaaaaaaaa").append("disponible", 2)))));
            piezas.put(HERMANOS + "|OTRA", new Document("_id", HERMANOS + "|OTRA").append("tallerId", HERMANOS).append("stock", 7));
            piezas.replaceAll((k, v) -> Document.parse(v.toJson())); // los tipos como los devuelve la base
            piezasAntes = copia(piezas);
            stockAntes = new LinkedHashMap<>(stockBase);

            when(mongo.find(any(Query.class), eq(Tienda.class))).thenAnswer(i -> List.of(tiendaVaquita, rios()));
            when(mongo.find(any(Query.class), eq(Taller.class))).thenReturn(List.of(hermanos(),
                    Taller.builder().id("64f0b").nombre("Taller los Lirios").estadoAprobacion(EstadoAprobacion.PENDIENTE).build()));
            when(mongo.find(any(Query.class), eq(Producto.class))).thenAnswer(i -> {
                Object tienda = ((Query) i.getArgument(0)).getQueryObject().get("tiendaId");
                return productos.values().stream().filter(p -> p.getTiendaId().equals(tienda)).map(this::conStockActual).toList();
            });
            when(mongo.findById(anyString(), eq(Usuario.class))).thenReturn(Usuario.builder().correo("hermanos@correo.co").build());
            when(mongo.findById(anyString(), eq(Producto.class))).thenAnswer(i -> productos.containsKey(i.<String>getArgument(0))
                    ? conStockActual(productos.get(i.<String>getArgument(0))) : null);
            when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Producto.class))).thenAnswer(i -> actualizarStock(i.getArgument(0), i.getArgument(1)));
            when(mongo.save(any(Document.class), eq(DatosDemoService.COLECCION_MARCA))).thenAnswer(i -> {
                Document d = i.getArgument(0);
                marcas.put(String.valueOf(d.get("_id")), new Document(d));
                return d;
            });
            when(mongo.findById(anyString(), eq(Document.class), eq(DatosDemoService.COLECCION_MARCA)))
                    .thenAnswer(i -> marcas.get(i.<String>getArgument(0)));
            when(mongo.find(any(Query.class), eq(Document.class), eq(DatosDemoService.COLECCION_PIEZAS))).thenAnswer(i -> {
                Object taller = ((Query) i.getArgument(0)).getQueryObject().get("tallerId");
                return piezas.values().stream().filter(d -> taller.equals(d.get("tallerId"))).map(d -> Document.parse(d.toJson())).toList();
            });
            when(mongo.save(any(Document.class), eq(DatosDemoService.COLECCION_PIEZAS))).thenAnswer(i -> {
                Document d = i.getArgument(0);
                piezas.put(String.valueOf(d.get("_id")), Document.parse(d.toJson()));
                return d;
            });
            when(mongo.insert(any(Document.class), eq(DatosDemoService.COLECCION_PIEZAS))).thenAnswer(i -> {
                Document d = i.getArgument(0);
                piezas.put(String.valueOf(d.get("_id")), Document.parse(d.toJson()));
                return d;
            });
            when(mongo.remove(any(Query.class), anyString())).thenAnswer(i -> {
                Query q = i.getArgument(0);
                if (DatosDemoService.COLECCION_PIEZAS.equals(i.getArgument(1)) && q.getQueryObject().get("_id") instanceof String pid) {
                    return DeleteResult.acknowledged(piezas.remove(pid) != null ? 1 : 0);
                }
                if (DatosDemoService.COLECCION_MARCA.equals(i.getArgument(1))) {
                    int n = marcas.size();
                    marcas.clear();
                    return DeleteResult.acknowledged(n);
                }
                return DeleteResult.acknowledged(0);
            });
            when(mongo.insert(anyCollection(), any(Class.class))).thenAnswer(i -> i.getArgument(0));
            // La compra demo entra al inventario real: suma un lote a una pieza que ya existía y crea otra
            when(inventario.entrarPedido(any(), any(), any())).thenAnswer(i -> {
                Taller t = i.getArgument(0);
                PedidoTienda p = i.getArgument(1);
                if (!HERMANOS.equals(t.getId())) return LocalDateTime.now();
                Document existente = piezas.get(HERMANOS + "|BOSCH|BP1234");
                existente.getList("lotes", Document.class).add(new Document("_id", p.getId() + "|x").append("pedidoId", p.getId()).append("disponible", 3));
                existente.put("stock", existente.getInteger("stock") + 3);
                existente.put("version", ((Number) existente.get("version")).intValue() + 1);
                String nueva = HERMANOS + "|NUEVA|" + p.getId();
                piezas.put(nueva, new Document("_id", nueva).append("tallerId", HERMANOS).append("stock", 4)
                        .append("lotes", new ArrayList<>(List.of(new Document("pedidoId", p.getId()).append("disponible", 4)))));
                return LocalDateTime.now();
            });
            when(pagos.saveAll(anyIterable())).thenAnswer(i -> {
                List<PagoEntity> l = new ArrayList<>();
                for (Object o : (Iterable<?>) i.getArgument(0)) { ((PagoEntity) o).setId(id++); l.add((PagoEntity) o); }
                return l;
            });
            when(repartos.saveAll(anyIterable())).thenAnswer(i -> {
                List<RepartoPago> l = new ArrayList<>();
                for (Object o : (Iterable<?>) i.getArgument(0)) l.add(guardar((RepartoPago) o));
                return l;
            });
            when(repartos.save(any(RepartoPago.class))).thenAnswer(i -> guardar(i.getArgument(0)));
            when(repartos.findByPagoId(anyLong())).thenAnswer(i -> lineas.stream().filter(r -> r.getPagoId().equals(i.getArgument(0))).toList());
            when(repartos.existsByPagoIdAndBeneficiarioTipo(anyLong(), anyString())).thenAnswer(i -> lineas.stream()
                    .anyMatch(r -> r.getPagoId().equals(i.getArgument(0)) && r.getBeneficiarioTipo().equals(i.getArgument(1))));
            ParametrosMarketplace parametros = mock(ParametrosMarketplace.class);
            when(parametros.ivaPct()).thenReturn(19.0);
            when(parametros.comisionPct()).thenReturn(10.0);
            when(parametros.tarifaDiagnostico()).thenReturn(50000.0);
            when(parametros.plazoDespachoDiasHabiles()).thenReturn(2);
            when(parametros.plazoEncargoDiasExtra()).thenReturn(3);
            PasswordEncoder encoder = mock(PasswordEncoder.class);
            when(encoder.encode(anyString())).thenReturn("$2a$10$x");
            servicio = new DatosDemoService(mongo, pagos, repartos, inventario, parametros, encoder);
        }

        private RepartoPago guardar(RepartoPago r) {
            if (r.getId() == null) { r.setId(id++); lineas.add(r); }
            return r;
        }

        private Producto conStockActual(Producto p) {
            p.setStockActual(stockBase.get(p.getId()));
            return p;
        }

        private UpdateResult actualizarStock(Query q, Update u) {
            Document filtro = q.getQueryObject();
            String pid = String.valueOf(filtro.get("_id"));
            if (!stockBase.containsKey(pid)) return UpdateResult.acknowledged(0, 0L, null);
            if (filtro.containsKey("stockActual") && !java.util.Objects.equals(filtro.get("stockActual"), stockBase.get(pid))) {
                return UpdateResult.acknowledged(0, 0L, null);
            }
            Document cambio = u.getUpdateObject();
            if (cambio.containsKey("$set")) stockBase.put(pid, (Integer) cambio.get("$set", Document.class).get("stockActual"));
            if (cambio.containsKey("$inc")) stockBase.put(pid, stockBase.get(pid) + ((Number) cambio.get("$inc", Document.class).get("stockActual")).intValue());
            return UpdateResult.acknowledged(1, 1L, null);
        }

        private static Map<String, Document> copia(Map<String, Document> m) {
            Map<String, Document> r = new LinkedHashMap<>();
            m.forEach((k, v) -> r.put(k, Document.parse(v.toJson())));
            return r;
        }

        @Test
        void sembrar_cambia_solo_el_stock_de_los_productos_vendidos_y_el_inventario_del_taller() {
            String r = servicio.sembrar();
            assertNotEquals(stockAntes, stockBase, "la siembra aplica el stock final");
            assertNotEquals(piezasAntes, piezas, "y las compras entran al inventario del taller");
            assertFalse(r.contains("Taller los Lirios"), "Taller los Lirios no se usa");
            // Los objetos de producto conservan nombre y precio: solo se tocó stockActual en la base
            productosVaquita().forEach(p -> assertEquals(p.getPrecioVenta(), productos.get(p.getId()).getPrecioVenta()));
        }

        @Test
        void borrar_deja_el_stock_y_el_inventario_exactamente_como_estaban() {
            servicio.sembrar();
            DatosDemoService.Borrado b = servicio.borrar();
            assertEquals(stockAntes, stockBase, "stock real restaurado");
            assertEquals(piezasAntes, piezas, "inventario de Taller los hermanos restaurado, versión incluida");
            assertTrue(b.avisos().isEmpty(), b.avisos().toString());
            assertTrue(marcas.isEmpty(), "las marcas se borran al final");
        }

        @Test
        void borrar_dos_veces_no_cambia_mas_nada() {
            servicio.sembrar();
            servicio.borrar();
            servicio.borrar();
            assertEquals(stockAntes, stockBase);
            assertEquals(piezasAntes, piezas);
        }

        @Test
        void si_hubo_una_venta_real_durante_la_feria_borrar_quita_solo_lo_demo_y_avisa() {
            servicio.sembrar();
            String vendido = stockBase.keySet().stream().filter(k -> !stockBase.get(k).equals(stockAntes.get(k))).findFirst().orElseThrow();
            stockBase.put(vendido, stockBase.get(vendido) - 1); // una venta real
            DatosDemoService.Borrado b = servicio.borrar();
            assertEquals(stockAntes.get(vendido) - 1, stockBase.get(vendido), "conserva la venta real");
            assertTrue(b.avisos().stream().anyMatch(a -> a.contains("cambió durante la feria")), b.avisos().toString());
        }

        @Test
        void si_la_siembra_falla_a_mitad_lo_real_queda_como_estaba() {
            when(repartos.saveAll(anyIterable())).thenThrow(new IllegalStateException("se cayó Postgres"));
            assertThrows(IllegalStateException.class, () -> servicio.sembrar());
            assertEquals(stockAntes, stockBase, "el stock ya aplicado se revirtió");
            assertEquals(piezasAntes, piezas);
        }

        @Test
        void una_tienda_no_aprobada_no_se_usa_y_se_avisa() {
            tiendaVaquita = vaquita();
            tiendaVaquita.setEstadoAprobacion(EstadoAprobacion.PENDIENTE);
            String r = servicio.sembrar();
            assertTrue(r.contains("\"AUTOPARTE LA VAQUITA\" no está APROBADO"), r);
            productosVaquita().forEach(p -> assertEquals(stockAntes.get(p.getId()), stockBase.get(p.getId()), "su stock no se toca"));
        }
    }
}
