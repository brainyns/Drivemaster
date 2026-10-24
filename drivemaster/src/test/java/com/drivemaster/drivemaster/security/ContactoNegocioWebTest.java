package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.CambioCorreoController;
import com.drivemaster.drivemaster.controller.DatosTallerController;
import com.drivemaster.drivemaster.controller.TiendaController;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.service.CambioCorreoService;
import com.drivemaster.drivemaster.service.DatosTallerService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Teléfono y correo de la tarjeta Contacto con la seguridad real: cada negocio solo cambia lo suyo. */
@WebMvcTest(controllers = {DatosTallerController.class, TiendaController.class, CambioCorreoController.class})
@Import(SecurityConfig.class)
class ContactoNegocioWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private DatosTallerService datos;
    @MockBean private TiendaService tiendaService;
    @MockBean private CambioCorreoService cambioCorreo;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private static final String TELEFONO = "{\"telefono\":\"3001234567\"}";

    @Test
    void elTelefonoDelTallerSoloLoCambiaElTallerYSiempreElDeLaSesion() throws Exception {
        when(datos.actualizarTelefono(any(), any())).thenReturn(TallerDTO.builder().id("A").build());
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "CLIENTE", "TIENDA"}) {
            mvc.perform(put("/api/talleres/mi-taller/telefono").with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(TELEFONO)).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/talleres/mi-taller/telefono?tallerId=B").with(user("a@demo.com").roles("TALLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"3001234567\",\"tallerId\":\"B\"}"))
                .andExpect(status().isOk());
        verify(datos).actualizarTelefono(eq("a@demo.com"), eq("3001234567"));
    }

    @Test
    void elTelefonoDeLaTiendaSoloLoCambiaLaTienda() throws Exception {
        when(tiendaService.actualizarTelefono(any(), any())).thenReturn(TiendaDTO.builder().id("T").build());
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "CLIENTE", "TALLER"}) {
            mvc.perform(put("/api/tiendas/mi-tienda/telefono").with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(TELEFONO)).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/tiendas/mi-tienda/telefono").with(user("t@demo.com").roles("TIENDA"))
                .contentType(MediaType.APPLICATION_JSON).content(TELEFONO)).andExpect(status().isOk());
        verify(tiendaService).actualizarTelefono(eq("t@demo.com"), eq("3001234567"));
    }

    @Test
    void elCambioDeCorreoEsSoloDeTalleresYTiendasYSinSesionNoSePuede() throws Exception {
        String cuerpo = "{\"passwordActual\":\"x\",\"correoNuevo\":\"n@demo.com\"}";
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "CLIENTE"}) {
            mvc.perform(post("/api/cuenta/correo/solicitar").with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/cuenta/correo/confirmar").contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cambioCorreo);

        for (String rol : new String[]{"TALLER", "TIENDA"}) {
            mvc.perform(post("/api/cuenta/correo/solicitar").with(user(rol + "@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isOk());
            verify(cambioCorreo).solicitar(rol + "@demo.com", "x", "n@demo.com");
        }
    }
}
