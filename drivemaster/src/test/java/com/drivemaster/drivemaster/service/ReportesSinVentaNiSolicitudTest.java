package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Solicitud;
import com.drivemaster.drivemaster.model.Venta;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.PlanSuscripcionRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.repository.mysql.SuscripcionRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Los reportes y el panel nuevos salen de pedidos de tienda, compras pagadas, repartos y órdenes de servicio.
 * Ninguno depende de Venta ni de Solicitud (el flujo anterior): por eso esas pantallas se pueden ocultar sin que
 * se rompa ninguna cifra nueva.
 */
class ReportesSinVentaNiSolicitudTest {

    private static final List<String> PROHIBIDAS = List.of("ventas", "solicitudes");

    private MongoTemplate mongoQueDevuelveVacio() {
        return mock(MongoTemplate.class, invocation -> {
            Class<?> tipo = invocation.getMethod().getReturnType();
            if (tipo == AggregationResults.class) return new AggregationResults<Document>(List.of(), new Document());
            if (tipo == long.class) return 0L;
            if (tipo == List.class) return new ArrayList<>();
            return RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private static boolean toca(Object argumento) {
        if (argumento == Venta.class || argumento == Solicitud.class) return true;
        if (argumento instanceof String s) return PROHIBIDAS.contains(s);
        if (argumento instanceof Aggregation a) {
            String pipeline = a.toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
            return pipeline.contains("\"ventas\"") || pipeline.contains("Venta") || pipeline.contains("solicitud");
        }
        return false;
    }

    @Test
    void ningunReporteNiElPanelConsultaVentasNiSolicitudes() {
        MongoTemplate mongo = mongoQueDevuelveVacio();
        RepartoPagoRepository repartos = mock(RepartoPagoRepository.class);
        PlataformaReportesService reportes = new PlataformaReportesService(mongo, repartos);
        PlataformaDashboardService dashboard = new PlataformaDashboardService(mongo, mock(PagoRepository.class), repartos,
                mock(ReembolsoRepository.class), mock(SuscripcionRepository.class), mock(ParametrosMarketplace.class));
        PlataformaConsultasService consultas = new PlataformaConsultasService(mongo);
        PlataformaNegociosService negocios = new PlataformaNegociosService(mongo, mock(SuscripcionRepository.class), mock(PlanSuscripcionRepository.class));

        // Los cinco reportes nuevos
        reportes.ventasPorTienda(12);
        reportes.productosMasVendidos(12, 10);
        reportes.clientesConMasCompras(12, 10);
        reportes.serviciosPorTaller();
        reportes.comisionNeta(12);
        // y el resto del panel de la plataforma
        dashboard.dashboard();
        dashboard.operacion();
        consultas.pedidos(null, null, null, false, 0, 20);
        consultas.clientes(null, null, null, 0, 20);
        consultas.productos(null, null, null, null, 0, 20);
        consultas.usuarios(null, null, null, 0, 20);
        negocios.negocios(null, null, null, 0, 20);
        negocios.pendientes();

        List<String> culpables = new ArrayList<>();
        for (Invocation llamada : mockingDetails(mongo).getInvocations()) {
            for (Object arg : llamada.getArguments()) {
                if (toca(arg)) culpables.add(llamada.getMethod().getName() + "(" + arg + ")");
            }
        }
        assertTrue(culpables.isEmpty(), "Consultas a Venta o Solicitud: " + culpables);
        assertTrue(mockingDetails(mongo).getInvocations().size() > 20, "la prueba realmente ejecutó consultas");
    }

    @Test
    void losServiciosDeReportesNoReciben_ni_conocen_repositoriosDeVentasNiSolicitudes() {
        for (Class<?> servicio : new Class<?>[]{PlataformaReportesService.class, PlataformaDashboardService.class,
                PlataformaConsultasService.class, PlataformaNegociosService.class}) {
            for (Constructor<?> c : servicio.getDeclaredConstructors()) {
                for (Class<?> parametro : c.getParameterTypes()) {
                    String nombre = parametro.getSimpleName().toLowerCase();
                    assertFalse(nombre.contains("venta") || nombre.contains("solicitud"),
                            servicio.getSimpleName() + " depende de " + parametro.getSimpleName());
                }
            }
        }
    }

    @Test
    void elReporteViejoDeServiciosNoUsaVentaPeroLosDeClientesYProductosSi() throws Exception {
        // ReporteServiceImpl (reportes anteriores): solo "servicios" sale de las órdenes; los demás parten de Venta.
        String fuente = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/drivemaster/drivemaster/service/ReporteServiceImpl.java"), java.nio.charset.StandardCharsets.UTF_8);
        int desde = fuente.indexOf("public ReporteServiciosDTO getReportesServicios");
        assertTrue(desde > 0);
        String cuerpo = fuente.substring(desde);
        assertFalse(cuerpo.contains("ventasActivas()") || cuerpo.contains("ventaRepository"),
                "Reporte de servicios debe seguir saliendo de las órdenes de servicio, no de Venta");
        assertTrue(fuente.substring(0, desde).contains("ventasActivas()"), "los demás reportes anteriores sí parten de Venta");
        assertNotNull(LocalDateTime.now());
    }
}
