package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.PrediccionAprobacionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PredictorCotizacionServiceTest {

    private PredictorCotizacionService predictor;

    @BeforeEach
    void setUp() {
        predictor = new PredictorCotizacionService();
        predictor.initFallback();
    }

    @Test
    @DisplayName("Debe predecir aprobación alta para cotización competitiva con respuesta rápida")
    void prediccionFavorable() {
        Map<String, Object> params = new HashMap<>();
        params.put("monto_repuestos", 120_000.0);
        params.put("monto_mano_obra", 80_000.0);
        params.put("total_con_iva", 238_000.0);
        params.put("cantidad_repuestos", 2.0);
        params.put("num_tiendas", 1.0);
        params.put("tipo_servicio", "FRENOS");
        params.put("marca_vehiculo", "Mazda");
        params.put("anio_vehiculo", 2021.0);
        params.put("distancia_km", 3.2);
        params.put("tiempo_respuesta_horas", 1.5);
        params.put("calificacion_taller", 4.9);

        PrediccionAprobacionDTO res = predictor.predecir(params);

        assertNotNull(res);
        assertNotNull(res.getProbabilidadAprobacion());
        assertTrue(res.getProbabilidadAprobacion() >= 0.70, "Debería ser probabilidad alta");
        assertEquals("BAJO", res.getNivelRiesgo());
        assertEquals("APROBADA", res.getPrediccion());
        assertNotNull(res.getFactoresFavor());
        assertFalse(res.getFactoresFavor().isEmpty(), "Debe contener factores a favor");
    }

    @Test
    @DisplayName("Debe predecir rechazo o riesgo alto si la cotización es cara, lenta y lejana")
    void prediccionDesfavorable() {
        Map<String, Object> params = new HashMap<>();
        params.put("monto_repuestos", 1_800_000.0);
        params.put("monto_mano_obra", 900_000.0);
        params.put("total_con_iva", 3_213_000.0);
        params.put("cantidad_repuestos", 6.0);
        params.put("num_tiendas", 3.0);
        params.put("tipo_servicio", "MOTOR_AFINAMIENTO");
        params.put("marca_vehiculo", "Chevrolet");
        params.put("anio_vehiculo", 2010.0); // 16 años de antigüedad
        params.put("distancia_km", 45.0);
        params.put("tiempo_respuesta_horas", 52.0);
        params.put("calificacion_taller", 3.8);

        PrediccionAprobacionDTO res = predictor.predecir(params);

        assertNotNull(res);
        assertTrue(res.getProbabilidadAprobacion() < 0.50, "Debería ser probabilidad baja");
        assertEquals("ALTO", res.getNivelRiesgo());
        assertEquals("RECHAZADA", res.getPrediccion());
        assertNotNull(res.getFactoresRiesgo());
        assertFalse(res.getFactoresRiesgo().isEmpty(), "Debe contener factores de riesgo");
    }
}

