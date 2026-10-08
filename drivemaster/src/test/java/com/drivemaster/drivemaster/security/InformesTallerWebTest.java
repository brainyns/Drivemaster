package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.InformesTallerController;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.service.InformesTallerService;
import com.drivemaster.drivemaster.service.TallerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Informes del taller con la seguridad real: solo el TALLER entra, siempre con SU taller (el de la sesión), y solo si
 * ya fue aprobado.
 */
@WebMvcTest(controllers = InformesTallerController.class)
@Import(SecurityConfig.class)
class InformesTallerWebTest {

    private static final String URL = "/api/talleres/mi-taller/informes";

    @Autowired private MockMvc mvc;

    @MockBean private InformesTallerService informes;
    @MockBean private TallerService tallerService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        when(tallerService.obtenerPorUsuario("a@demo.com"))
                .thenReturn(Taller.builder().id("A").estadoAprobacion(EstadoAprobacion.APROBADO).build());
        when(tallerService.obtenerPorUsuario("b@demo.com"))
                .thenReturn(Taller.builder().id("B").estadoAprobacion(EstadoAprobacion.APROBADO).build());
        when(tallerService.obtenerPorUsuario("p@demo.com"))
                .thenReturn(Taller.builder().id("P").estadoAprobacion(EstadoAprobacion.PENDIENTE).build());
    }

    @Test
    void losInformesSeCalculanSiempreParaElTallerDeLaSesion() throws Exception {
        mvc.perform(get(URL + "?meses=6").with(user("a@demo.com").roles("TALLER"))).andExpect(status().isOk());
        // Un tallerId en la petición se ignora: el taller sale de la sesión
        mvc.perform(get(URL + "?meses=12&tallerId=B").with(user("a@demo.com").roles("TALLER"))).andExpect(status().isOk());
        mvc.perform(get(URL).with(user("b@demo.com").roles("TALLER"))).andExpect(status().isOk());

        verify(informes, times(2)).informes(eq("A"), anyInt());
        verify(informes, times(1)).informes(eq("B"), anyInt());
    }

    @Test
    void ningunOtroRolVeLosInformesDeUnTaller() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE", "TIENDA"}) {
            mvc.perform(get(URL).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(informes);
    }

    @Test
    void unTallerSinAprobarNoVeInformes() throws Exception {
        mvc.perform(get(URL).with(user("p@demo.com").roles("TALLER"))).andExpect(status().isForbidden());
        verify(informes, never()).informes(eq("P"), anyInt());
    }
}
