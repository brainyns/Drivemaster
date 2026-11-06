package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.model.CompraCatalogo;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.MovimientoInventario;
import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.model.PiezaTaller;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sembrar y borrar contra repositorios simulados: borrar quita solo lo demo y cubre todo lo que sembrar escribe; no se
 * siembra dos veces; un choque con datos reales no escribe nada; una falla a mitad borra lo escrito; y lo sembrado
 * cuadra con "Comisión ganada".
 */
class DatosDemoServiceTest {

    private MongoTemplate mongo;
    private PagoRepository pagos;
    private RepartoPagoRepository repartos;
    private InventarioTallerService inventario;
    private ParametrosMarketplace parametros;
    private DatosDemoService servicio;
    private final List<RepartoPago> lineas = new ArrayList<>();
    private final Set<String> coleccionesEscritas = new HashSet<>();
    private long siguienteLinea = 1;
    private long siguientePago = 1;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        when(mongo.remove(any(Query.class), anyString())).thenReturn(DeleteResult.acknowledged(0));
        when(mongo.insert(anyCollection(), any(Class.class))).thenAnswer(i -> {
            coleccionesEscritas.add(coleccion(i.getArgument(1)));
            return i.getArgument(0);
        });
        when(mongo.find(any(Query.class), eq(PiezaTaller.class))).thenReturn(List.of());
        pagos = mock(PagoRepository.class);
        when(pagos.saveAll(anyIterable())).thenAnswer(i -> {
            List<PagoEntity> l = new ArrayList<>();
            for (Object o : (Iterable<?>) i.getArgument(0)) {
                PagoEntity p = (PagoEntity) o;
                p.setId(siguientePago++);
                l.add(p);
            }
            return l;
        });
        repartos = mock(RepartoPagoRepository.class);
        when(repartos.save(any(RepartoPago.class))).thenAnswer(i -> guardar(i.getArgument(0)));
        when(repartos.saveAll(anyIterable())).thenAnswer(i -> {
            List<RepartoPago> l = new ArrayList<>();
            for (Object o : (Iterable<?>) i.getArgument(0)) l.add(guardar((RepartoPago) o));
            return l;
        });
        when(repartos.findByPagoId(anyLong())).thenAnswer(i -> lineas.stream().filter(r -> r.getPagoId().equals(i.getArgument(0))).toList());
        when(repartos.existsByPagoIdAndBeneficiarioTipo(anyLong(), anyString())).thenAnswer(i -> lineas.stream()
                .anyMatch(r -> r.getPagoId().equals(i.getArgument(0)) && r.getBeneficiarioTipo().equals(i.getArgument(1))));
        inventario = mock(InventarioTallerService.class);
        when(inventario.entrarPedido(any(), any(), any())).thenReturn(LocalDateTime.now());
        parametros = mock(ParametrosMarketplace.class);
        when(parametros.ivaPct()).thenReturn(19.0);
        when(parametros.comisionPct()).thenReturn(10.0);
        when(parametros.comisionRetenida()).thenReturn(true);
        when(parametros.tarifaDiagnostico()).thenReturn(50000.0);
        when(parametros.plazoDespachoDiasHabiles()).thenReturn(2);
        when(parametros.plazoEncargoDiasExtra()).thenReturn(3);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("$2a$10$hash");
        servicio = new DatosDemoService(mongo, pagos, repartos, inventario, parametros, encoder);
    }

    private RepartoPago guardar(RepartoPago r) {
        if (r.getId() == null) {
            r.setId(siguienteLinea++);
            lineas.add(r);
        }
        return r;
    }

    private static String coleccion(Class<?> c) {
        return c.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class).collection();
    }

    private void marcaCompleta() {
        when(mongo.findById(eq(DatosDemoService.ID_MARCA), eq(Document.class), eq(DatosDemoService.COLECCION_MARCA)))
                .thenReturn(new Document("_id", DatosDemoService.ID_MARCA).append("completo", true));
    }

    private static String regexDe(Query q, String campo) {
        Object v = q.getQueryObject().get(campo);
        return v instanceof Pattern p ? p.pattern() : String.valueOf(v);
    }

    // ─── Borrar ──────────────────────────────────────────────────────────────

    @Test
    void borrar_quita_postgres_primero_las_lineas_y_despues_los_pagos() {
        servicio.borrar();
        InOrder orden = inOrder(repartos, pagos);
        orden.verify(repartos).borrarRepartosDemo();
        orden.verify(pagos).borrarPagosDemo();
    }

    @Test
    void borrar_en_mongo_solo_toca_ids_o_duenos_que_empiezan_por_demo() {
        servicio.borrar();
        ArgumentCaptor<Query> consultas = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<String> colecciones = ArgumentCaptor.forClass(String.class);
        verify(mongo, atLeastOnce()).remove(consultas.capture(), colecciones.capture());
        for (int i = 0; i < consultas.getAllValues().size(); i++) {
            Query q = consultas.getAllValues().get(i);
            Document filtro = q.getQueryObject();
            assertEquals(1, filtro.size(), "un solo criterio: " + filtro);
            String campo = filtro.keySet().iterator().next();
            assertEquals("^demo-", regexDe(q, campo), colecciones.getAllValues().get(i) + "." + campo);
        }
        // Todas las colecciones de la lista, cada una con su criterio
        Set<String> borradas = new HashSet<>(colecciones.getAllValues());
        assertTrue(borradas.containsAll(DatosDemoService.COLECCIONES_POR_ID));
        assertTrue(borradas.containsAll(DatosDemoService.COLECCIONES_POR_DUENO.keySet()));
    }

    @Test
    void borrar_cubre_cada_coleccion_que_la_siembra_escribe() {
        Set<String> limpiadas = new HashSet<>(DatosDemoService.COLECCIONES_POR_ID);
        limpiadas.addAll(DatosDemoService.COLECCIONES_POR_DUENO.keySet());
        for (Class<?> c : List.of(Usuario.class, Vehiculo.class, Tienda.class, Taller.class, Producto.class, CompraCatalogo.class,
                PedidoTienda.class, MovimientoInventario.class, OrdenServicio.class, Cotizacion.class, PiezaTaller.class,
                MovimientoPiezaTaller.class)) {
            assertTrue(limpiadas.contains(coleccion(c)), coleccion(c));
        }
        servicio.sembrar();
        assertFalse(coleccionesEscritas.isEmpty());
        assertTrue(limpiadas.containsAll(coleccionesEscritas), coleccionesEscritas.toString());
    }

    @Test
    void las_consultas_de_postgres_solo_borran_referencias_que_empiezan_por_DEMO() throws Exception {
        for (String metodo : List.of("borrarPagosDemo", "contarPagosDemo")) {
            String jpql = PagoRepository.class.getMethod(metodo).getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
            assertTrue(jpql.contains("like 'DEMO-%'"), jpql);
        }
        String repartosJpql = RepartoPagoRepository.class.getMethod("borrarRepartosDemo")
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertTrue(repartosJpql.contains("p.referencia like 'DEMO-%'") && repartosJpql.contains("like 'SIM-DEMO-%'"), repartosJpql);
        assertFalse(repartosJpql.contains("'%DEMO"), "anclado al inicio");
    }

    @Test
    void borrar_avisa_de_lo_real_que_toca_algo_demo_y_no_lo_borra() {
        when(mongo.count(any(Query.class), eq(PedidoTienda.class))).thenReturn(2L);
        when(repartos.contarRepartosRealesDeNegociosDemo()).thenReturn(3L);
        DatosDemoService.Borrado b = servicio.borrar();
        assertEquals(2, b.avisos().size());
        assertTrue(b.toString().contains("AVISO: 2 pedido(s) reales"));
    }

    @Test
    void borrar_dos_veces_no_falla() {
        assertDoesNotThrow(() -> {
            servicio.borrar();
            servicio.borrar();
        });
    }

    // ─── Sembrar ─────────────────────────────────────────────────────────────

    @Test
    void si_ya_esta_completo_no_escribe_nada() {
        marcaCompleta();
        assertTrue(servicio.sembrar().contains("ya estaban completos"));
        verify(mongo, never()).insert(anyCollection(), any(Class.class));
        verify(pagos, never()).saveAll(anyIterable());
        verify(mongo, never()).remove(any(Query.class), anyString());
    }

    @Test
    void si_quedo_a_medias_borra_y_vuelve_a_sembrar() {
        when(pagos.contarPagosDemo()).thenReturn(12L);
        servicio.sembrar();
        InOrder orden = inOrder(pagos, mongo);
        orden.verify(pagos).borrarPagosDemo();
        orden.verify(mongo, atLeastOnce()).insert(anyCollection(), eq(Usuario.class));
    }

    @Test
    void un_correo_que_ya_usa_alguien_real_detiene_todo_sin_escribir() {
        when(mongo.exists(any(Query.class), eq(Usuario.class))).thenAnswer(i -> {
            Query q = i.getArgument(0);
            return q.getQueryObject().containsKey("correo") && regexDe(q, "correo").contains("carlos\\.mendoza");
        });
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> servicio.sembrar());
        assertTrue(e.getMessage().contains("brainy796+carlos.mendoza@gmail.com"), e.getMessage());
        verify(mongo, never()).insert(anyCollection(), any(Class.class));
        verify(pagos, never()).saveAll(anyIterable());
    }

    @Test
    void el_correo_se_compara_exacto_sin_que_el_mas_o_el_punto_sean_comodines() {
        Pattern p = Pattern.compile(DatosDemoService.regexExacto("brainy796+carlos.mendoza@gmail.com"), Pattern.CASE_INSENSITIVE);
        assertTrue(p.matcher("brainy796+carlos.mendoza@gmail.com").matches());
        assertTrue(p.matcher("Brainy796+Carlos.Mendoza@Gmail.com").matches(), "sin mayúsculas, como el registro");
        assertFalse(p.matcher("brainy796carlos.mendoza@gmail.com").matches());
        assertFalse(p.matcher("brainy796+carlosXmendoza@gmail.com").matches());
        assertFalse(p.matcher("otro.brainy796+carlos.mendoza@gmail.com").matches());
    }

    @Test
    void un_nit_o_nombre_de_negocio_real_igual_tambien_detiene_todo() {
        Tienda real = Tienda.builder().id("6543a1b2c3d4e5f6a7b8c9d0").nombre("Repuestos  el bosque").nit("800123456-1").build();
        when(mongo.find(any(Query.class), eq(Tienda.class))).thenReturn(List.of(real));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> servicio.sembrar());
        assertTrue(e.getMessage().contains("ya hay una tienda real llamada"), e.getMessage());
        verify(mongo, never()).insert(anyCollection(), any(Class.class));
    }

    @Test
    void si_falla_a_mitad_borra_lo_escrito_y_no_queda_como_completo() {
        when(repartos.saveAll(anyIterable())).thenThrow(new IllegalStateException("se cayó Postgres"));
        assertThrows(IllegalStateException.class, () -> servicio.sembrar());
        verify(repartos).borrarRepartosDemo();
        verify(pagos).borrarPagosDemo();
        verify(mongo, atLeastOnce()).remove(any(Query.class), eq("usuarios"));
        ArgumentCaptor<Object> guardados = ArgumentCaptor.forClass(Object.class);
        verify(mongo, atLeastOnce()).save(guardados.capture(), eq(DatosDemoService.COLECCION_MARCA));
        guardados.getAllValues().forEach(d -> assertNotEquals(Boolean.TRUE, ((Document) d).get("completo")));
    }

    @Test
    void al_terminar_cada_pago_tiene_su_comision_con_la_fecha_del_pago_y_la_marca_queda_completa() {
        servicio.sembrar();
        List<RepartoPago> plataforma = lineas.stream().filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())).toList();
        assertEquals(siguientePago - 1, plataforma.size(), "una línea PLATAFORMA por pago");
        plataforma.forEach(r -> {
            assertEquals(RepartoPago.RETENIDA, r.getEstado());
            assertTrue(r.getFechaCreacion().isBefore(LocalDateTime.of(2026, 11, 7, 0, 0)), "con la fecha del pago, no la de hoy");
            double deNegocios = lineas.stream().filter(l -> l.getPagoId().equals(r.getPagoId()) && !RepartoPago.PLATAFORMA.equals(l.getBeneficiarioTipo()))
                    .mapToDouble(RepartoPago::getComisionRetenida).sum();
            assertEquals(deNegocios, r.getMonto(), 0.001);
        });
        lineas.forEach(l -> assertNotNull(l.getPagoId()));
        ArgumentCaptor<Object> marca = ArgumentCaptor.forClass(Object.class);
        verify(mongo, atLeastOnce()).save(marca.capture(), eq(DatosDemoService.COLECCION_MARCA));
        assertEquals(Boolean.TRUE, ((Document) marca.getAllValues().get(marca.getAllValues().size() - 1)).get("completo"));
        verify(inventario, times(5)).entrarPedido(any(), any(), any());
    }

    @Test
    void lo_sembrado_cuadra_con_comision_ganada_de_cada_mes() {
        servicio.sembrar();
        when(repartos.comisionEntre(any(), any())).thenAnswer(i -> lineas.stream()
                .filter(r -> RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo()) && entre(r.getFechaCreacion(), i.getArgument(0), i.getArgument(1)))
                .toList());
        when(repartos.comisionCobradaEntre(any(), any())).thenReturn(List.of());
        PlataformaReportesService reportes = new PlataformaReportesService(mock(MongoTemplate.class), repartos,
                mock(ReembolsoRepository.class), parametros);
        DatosDemoArmado.Lote lote = DatosDemoArmadoTest.armar();
        var esperado = DatosDemoArmado.comisionPorMes(lote);
        var octubre = reportes.comisionGanada(RangoFechas.de("2026-10-01", "2026-10-31"), LocalDate.of(2026, 11, 6));
        var noviembre = reportes.comisionGanada(RangoFechas.de("2026-11-01", "2026-11-30"), LocalDate.of(2026, 11, 6));
        assertEquals(esperado.get("2026-10"), octubre.ganada(), 0.5);
        assertEquals(esperado.get("2026-11"), noviembre.ganada(), 0.5);
        assertEquals(0, octubre.liquidada(), 0.001, "las liquidaciones simuladas no son dinero cobrado");
        // Y es la suma de la columna "Comisión" de Liquidaciones en el periodo
        double columna = lineas.stream().filter(r -> !RepartoPago.PLATAFORMA.equals(r.getBeneficiarioTipo())
                && r.getFechaCreacion().getMonthValue() == 10).mapToDouble(RepartoPago::getComisionRetenida).sum();
        assertEquals(columna, octubre.ganada(), 0.5);
    }

    private static boolean entre(LocalDateTime f, Object desde, Object antesDe) {
        return f != null && !f.isBefore((LocalDateTime) desde) && f.isBefore((LocalDateTime) antesDe);
    }

    @Test
    void sin_superadmin_en_la_base_crea_uno_demo_y_con_uno_real_no() {
        servicio.sembrar();
        ArgumentCaptor<Collection<?>> usuarios = ArgumentCaptor.forClass(Collection.class);
        verify(mongo).insert(usuarios.capture(), eq(Usuario.class));
        assertTrue(usuarios.getValue().stream().anyMatch(u -> Objects.equals(((Usuario) u).getId(), DatosDemoCatalogo.SUPERADMIN_ID)));

        setUp();
        when(mongo.exists(any(Query.class), eq(Usuario.class))).thenAnswer(i -> ((Query) i.getArgument(0)).getQueryObject().containsKey("rol"));
        servicio.sembrar();
        verify(mongo).insert(usuarios.capture(), eq(Usuario.class));
        assertTrue(usuarios.getValue().stream().noneMatch(u -> Objects.equals(((Usuario) u).getId(), DatosDemoCatalogo.SUPERADMIN_ID)));
    }
}
