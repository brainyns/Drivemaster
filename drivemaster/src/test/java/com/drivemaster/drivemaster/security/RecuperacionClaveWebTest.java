package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.RecuperacionClaveController;
import com.drivemaster.drivemaster.service.RecuperacionClaveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "¿Olvidaste tu contraseña?" funciona sin sesión, y solo por POST. */
@WebMvcTest(controllers = RecuperacionClaveController.class)
@Import(SecurityConfig.class)
class RecuperacionClaveWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private RecuperacionClaveService recuperacion;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void seSolicitaYSeConfirmaSinIniciarSesion() throws Exception {
        when(recuperacion.solicitar(anyString(), anyString())).thenReturn(RecuperacionClaveService.MENSAJE_ENVIADO);
        when(recuperacion.confirmar(any(), any(), any(), anyString())).thenReturn(RecuperacionClaveService.MENSAJE_LISTO);

        mvc.perform(post("/api/auth/recuperar/solicitar").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"ana@demo.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(RecuperacionClaveService.MENSAJE_ENVIADO));
        mvc.perform(post("/api/auth/recuperar/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@demo.com\",\"codigo\":\"123456\",\"nuevaPassword\":\"nueva12\"}"))
                .andExpect(status().isOk());
        verify(recuperacion).confirmar(eq("ana@demo.com"), eq("123456"), eq("nueva12"), anyString());
    }

    @Test
    void otrosMetodosNoQuedanAbiertos() throws Exception {
        mvc.perform(get("/api/auth/recuperar/solicitar")).andExpect(status().isUnauthorized());
    }
}
