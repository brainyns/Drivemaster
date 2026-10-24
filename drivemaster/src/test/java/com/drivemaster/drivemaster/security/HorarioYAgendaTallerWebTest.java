package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.AgendaTallerController;
import com.drivemaster.drivemaster.controller.HorarioTallerController;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.service.HorarioTallerService;
import com.drivemaster.drivemaster.service.SolicitudServicioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Horario y agenda de un taller con la seguridad real: el taller (el servicio valida que sea el suyo) y el personal. */
@WebMvcTest(controllers = {HorarioTallerController.class, AgendaTallerController.class})
@Import(SecurityConfig.class)
class HorarioYAgendaTallerWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private HorarioTallerService horario;
    @MockBean private SolicitudServicioService solicitudes;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void clientesYTiendasNoVenNiCambianElHorarioNiLasCitas() throws Exception {
        for (String rol : new String[]{"CLIENTE", "TIENDA", "VENDEDOR"}) {
            mvc.perform(get("/api/talleres/A/horario").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get("/api/talleres/A/citas?desde=2026-10-01&hasta=2026-10-31").with(user("x@demo.com").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "CLIENTE"}) {
            mvc.perform(put("/api/talleres/A/horario").with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/talleres/A/horario")).andExpect(status().isUnauthorized());
        verifyNoInteractions(horario, solicitudes);
    }

    @Test
    void elTallerGuardaSuHorarioConHorasEnFormatoHHmm() throws Exception {
        mvc.perform(put("/api/talleres/A/horario").with(user("a@demo.com").roles("TALLER")).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"diaSemana\":\"LUNES\",\"abierto\":true,\"horaApertura\":\"08:00\",\"horaCierre\":\"12:30\",\"orden\":0}]"))
                .andExpect(status().isOk());
        verify(horario).reemplazar(eq("A"), eq("a@demo.com"), any());
    }

    @Test
    void lasCitasSePidenPorRangoValido() throws Exception {
        when(horario.tallerAccesible(anyString(), anyString(), anyBoolean())).thenReturn(Taller.builder().id("A").build());
        when(solicitudes.citasTaller(any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/talleres/A/citas?desde=2026-10-01&hasta=2026-10-31").with(user("a@demo.com").roles("TALLER")))
                .andExpect(status().isOk());
        verify(solicitudes).citasTaller("A", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        mvc.perform(get("/api/talleres/A/citas?desde=2026-10-31&hasta=2026-10-01").with(user("a@demo.com").roles("TALLER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/talleres/A/citas?desde=2026-01-01&hasta=2026-12-31").with(user("a@demo.com").roles("TALLER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/talleres/A/citas?desde=ayer&hasta=2026-10-01").with(user("a@demo.com").roles("TALLER")))
                .andExpect(status().isBadRequest());
    }
}
