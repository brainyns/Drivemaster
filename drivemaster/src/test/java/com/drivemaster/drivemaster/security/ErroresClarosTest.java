package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.SolicitudServicioController;
import com.drivemaster.drivemaster.service.SolicitudServicioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los fallos que no son del negocio llegan a la pantalla con un código correcto y un mensaje en español, sin detalles internos. */
class ErroresClarosTest {

    private final SolicitudServicioService service = mock(SolicitudServicioService.class);
    private final TestingAuthenticationToken taller = new TestingAuthenticationToken("taller@demo.com", "x");
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SolicitudServicioController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void unJsonMalFormadoEs400YNo500() throws Exception {
        mvc.perform(patch("/api/solicitudes-servicio/o1/aceptar").principal(taller)
                        .contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Los datos enviados no son válidos. Revísalos e intenta de nuevo."));
    }

    @Test
    void unaFechaInvalidaEs400() throws Exception {
        mvc.perform(patch("/api/solicitudes-servicio/o1/aceptar").principal(taller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fechaRevision\":\"mañana\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinCuerpoEs400() throws Exception {
        mvc.perform(put("/api/solicitudes-servicio/o1/diagnostico").principal(taller).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaFallaDeLaBaseEs503YNoExponeElDetalle() throws Exception {
        when(service.marcarEntregada(any(), any())).thenThrow(new DataAccessResourceFailureException("mongodb+srv://usuario@cluster caído"));

        mvc.perform(patch("/api/solicitudes-servicio/o1/entregada").principal(taller))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("No pudimos completar la acción. Intenta de nuevo en un momento."));
    }

    @Test
    void unaRuntimeDeOtroModuloConservaSuMensaje() throws Exception {
        when(service.marcarEntregada(any(), any())).thenThrow(new RuntimeException("Mensaje del negocio"));

        mvc.perform(patch("/api/solicitudes-servicio/o1/entregada").principal(taller))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Mensaje del negocio"));
    }
}
