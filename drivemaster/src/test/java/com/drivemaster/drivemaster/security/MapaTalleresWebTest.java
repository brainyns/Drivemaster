package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.TallerController;
import com.drivemaster.drivemaster.dto.TallerMapaDTO;
import com.drivemaster.drivemaster.service.TallerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/talleres/mapa: el CLIENTE lo usa al pedir un diagnóstico; el resto de /api/talleres sigue cerrado. */
@WebMvcTest(controllers = TallerController.class)
@Import(SecurityConfig.class)
class MapaTalleresWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private TallerService tallerService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void elClienteVeLosTalleresDelMapa() throws Exception {
        when(tallerService.listarParaMapa()).thenReturn(List.of(
                new TallerMapaDTO("t1", "Taller Uno", "Cra 1 # 2-3", "Cartagena", "3001234567", 10.39, -75.48)));

        mvc.perform(get("/api/talleres/mapa").with(user("ana@demo.com").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[0].latitud").value(10.39));
    }

    @Test
    void sinSesionNoSeVeElMapa() throws Exception {
        mvc.perform(get("/api/talleres/mapa")).andExpect(status().isUnauthorized());
    }

    @Test
    void tallerYTiendaNoUsanElMapa() throws Exception {
        for (String rol : new String[]{"TALLER", "TIENDA"}) {
            mvc.perform(get("/api/talleres/mapa").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
    }

    @Test
    void elClienteSigueSinVerElListadoDeAdministracion() throws Exception {
        mvc.perform(get("/api/talleres").with(user("ana@demo.com").roles("CLIENTE"))).andExpect(status().isForbidden());
    }
}
