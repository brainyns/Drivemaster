package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.LogoTiendaController;
import com.drivemaster.drivemaster.service.LogoTiendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Subir el logo con la seguridad real: la dueña solo el de su tienda; el de cualquier tienda, solo un administrador. */
@WebMvcTest(controllers = LogoTiendaController.class)
@Import(SecurityConfig.class)
class LogoTiendaWebTest {

    private static final String MIO = "/api/tiendas/mi-tienda/logo";
    private static final String DE_OTRO = "/api/tiendas/B/logo";

    @Autowired private MockMvc mvc;

    @MockBean private LogoTiendaService logos;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private static MockMultipartFile logo() {
        return new MockMultipartFile("archivo", "logo.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void laTiendaSubeSiempreElSuyoAunqueMandeOtroId() throws Exception {
        when(logos.subirMio(any(), any())).thenReturn("https://img/logo.png");

        mvc.perform(multipart(MIO + "?tiendaId=B").file(logo()).param("id", "B").with(user("a@demo.com").roles("TIENDA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value("https://img/logo.png"));

        verify(logos).subirMio(eq("a@demo.com"), any());
        verify(logos, never()).subirPorAdmin(any(), any());
    }

    @Test
    void unaTiendaNoPuedeCambiarElLogoDeOtraTienda() throws Exception {
        mvc.perform(multipart(DE_OTRO).file(logo()).with(user("a@demo.com").roles("TIENDA")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(logos);
    }

    @Test
    void losAdministradoresCambianElDeCualquierTiendaPeroNoUsanElDeLaDuena() throws Exception {
        when(logos.subirPorAdmin(any(), any())).thenReturn("https://img/b.png");
        for (String rol : new String[]{"ADMIN", "SUPERADMIN"}) {
            mvc.perform(multipart(DE_OTRO).file(logo()).with(user("admin@demo.com").roles(rol))).andExpect(status().isOk());
            mvc.perform(multipart(MIO).file(logo()).with(user("admin@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        verify(logos, org.mockito.Mockito.times(2)).subirPorAdmin(eq("B"), any());
        verify(logos, never()).subirMio(any(), any());
    }

    @Test
    void ningunOtroRolNiUnVisitanteSubenLogos() throws Exception {
        for (String rol : new String[]{"CLIENTE", "TALLER", "VENDEDOR"}) {
            mvc.perform(multipart(MIO).file(logo()).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(multipart(DE_OTRO).file(logo()).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(MIO).file(logo())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(DE_OTRO).file(logo())).andExpect(status().isUnauthorized());
        verifyNoInteractions(logos);
    }
}
