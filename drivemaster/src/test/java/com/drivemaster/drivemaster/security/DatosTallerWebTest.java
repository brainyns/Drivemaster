package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.DatosTallerController;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.taller.ActualizarDatosTallerRequest;
import com.drivemaster.drivemaster.service.DatosTallerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Edición de tarifa y horario con la seguridad real: solo el TALLER, siempre sobre el taller de la sesión. */
@WebMvcTest(controllers = DatosTallerController.class)
@Import(SecurityConfig.class)
class DatosTallerWebTest {

    private static final String URL = "/api/talleres/mi-taller/datos";

    @Autowired private MockMvc mvc;

    @MockBean private DatosTallerService datos;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void ningunOtroRolEditaLosDatosDeUnTaller() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE", "TIENDA"}) {
            mvc.perform(patch(URL).with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tarifaHora\":50000}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"tarifaHora\":50000}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(datos);
    }

    @Test
    void elTallerEditaSiempreElSuyoYUnIdEnElCuerpoSeIgnora() throws Exception {
        when(datos.actualizar(any(), any())).thenReturn(TallerDTO.builder().id("A").build());

        mvc.perform(patch(URL).with(user("a@demo.com").roles("TALLER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tarifaHora\":55000,\"horario\":\"L-V 8-18\"}")).andExpect(status().isOk());
        mvc.perform(patch(URL + "?tallerId=B").with(user("a@demo.com").roles("TALLER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"B\",\"tallerId\":\"B\",\"tarifaHora\":60000}")).andExpect(status().isOk());
        mvc.perform(patch(URL).with(user("b@demo.com").roles("TALLER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horario\":\"24 h\"}")).andExpect(status().isOk());

        verify(datos, times(2)).actualizar(eq("a@demo.com"), any());
        verify(datos, times(1)).actualizar(eq("b@demo.com"), any());
    }

    @Test
    void losCamposQueElTallerNoPuedeCambiarSeDescartan() throws Exception {
        when(datos.actualizar(any(), any())).thenReturn(TallerDTO.builder().id("A").build());

        mvc.perform(patch(URL).with(user("a@demo.com").roles("TALLER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tarifaHora\":55000,\"nombre\":\"Hackeado\",\"ciudad\":\"Otra\",\"estadoAprobacion\":\"APROBADO\","
                        + "\"activo\":true,\"precioManoObra\":1,\"rating\":5}")).andExpect(status().isOk());

        ArgumentCaptor<ActualizarDatosTallerRequest> pedido = ArgumentCaptor.forClass(ActualizarDatosTallerRequest.class);
        verify(datos).actualizar(eq("a@demo.com"), pedido.capture());
        assertEquals(55_000.0, pedido.getValue().getTarifaHora());
    }
}
