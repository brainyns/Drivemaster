package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.CancelacionOrdenController;
import com.drivemaster.drivemaster.controller.InventarioTallerController;
import com.drivemaster.drivemaster.dto.taller.InventarioTallerDTO;
import com.drivemaster.drivemaster.service.CancelacionOrdenService;
import com.drivemaster.drivemaster.service.InventarioTallerService;
import com.drivemaster.drivemaster.service.MovimientosTallerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Riesgo 2 con la seguridad real: costo y margen solo salen hacia el taller dueño; cancelar es solo del SUPERADMIN. */
@WebMvcTest(controllers = {InventarioTallerController.class, CancelacionOrdenController.class})
@Import(SecurityConfig.class)
class InventarioTallerWebTest {

    private static final String URL = "/api/inventario-taller";

    @Autowired private MockMvc mvc;

    @MockBean private InventarioTallerService inventario;
    @MockBean private CancelacionOrdenService cancelacion;
    @MockBean private MovimientosTallerService movimientos;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void solo_el_taller_entra_a_su_inventario() throws Exception {
        for (String rol : new String[]{"CLIENTE", "TIENDA", "ADMIN", "SUPERADMIN", "VENDEDOR"}) {
            mvc.perform(get(URL).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(post(URL + "/pedidos/ped1/llego").with(csrf()).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/movimientos").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(post(URL + "/movimientos").with(csrf()).with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(inventario);
    }

    @Test
    void el_inventario_sale_del_taller_de_la_sesion_aunque_pidan_otro() throws Exception {
        when(inventario.inventario("a@demo.com")).thenReturn(new InventarioTallerDTO.Inventario(List.of(new InventarioTallerDTO.Pieza(
                "t1|REF:X", "Filtro", null, null, null, null, 2, 135_000.0, 100_000.0, 35.0, 25.9, 13_500.0, 21_500.0, false, true, List.of(), List.of(),
                150_000.0, 178_500.0, "Repuestos Sur", 150_000.0, 135_000.0, false, false, null, false)),
                List.of(), 10, true, 35, 200_000, 270_000, List.of()));

        mvc.perform(get(URL + "?tallerId=otro").with(user("a@demo.com").roles("TALLER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"costoUnitario\":100000")));

        verify(inventario).inventario("a@demo.com");
    }

    @Test
    void cancelar_una_orden_pagada_es_solo_del_superadmin() throws Exception {
        String ruta = "/api/plataforma/ordenes/o1/cancelar";
        for (String rol : new String[]{"CLIENTE", "TALLER", "TIENDA", "ADMIN", "VENDEDOR"}) {
            mvc.perform(post(ruta).with(csrf()).with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"El cliente desistió\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(cancelacion);

        when(cancelacion.cancelar(eq("root@demo.com"), anyString(), any()))
                .thenReturn(new CancelacionOrdenService.Resultado(100_000, 1, List.of(), 1, 0));
        mvc.perform(post(ruta).with(csrf()).with(user("root@demo.com").roles("SUPERADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"El cliente desistió\"}")).andExpect(status().isOk());
        verify(cancelacion).cancelar("root@demo.com", "o1", "El cliente desistió");
    }
}
