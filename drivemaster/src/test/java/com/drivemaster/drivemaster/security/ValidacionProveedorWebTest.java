package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.ProveedorController;
import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.service.ProductoService;
import com.drivemaster.drivemaster.service.ProveedorService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un proveedor inválido responde 400 con el mensaje general y el error de cada campo, para mostrarlo en línea. */
@WebMvcTest(controllers = ProveedorController.class)
@Import({SecurityConfig.class, AlcanceTienda.class})
class ValidacionProveedorWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private ProveedorService proveedorService;
    @MockBean private TiendaService tiendaService;
    @MockBean private ProductoService productoService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void losErroresLleganPorCampoEnEspanol() throws Exception {
        when(tiendaService.obtenerOperativaPorUsuario("a@demo.com")).thenReturn(Tienda.builder().id("A").build());
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("nombre", "Escribe el nombre de la empresa.");
        campos.put("correo", "Escribe un correo válido, como contacto@proveedor.com.");
        when(proveedorService.crearEnTienda(any(Proveedor.class), anyString())).thenThrow(new DatosInvalidosException(campos));

        mvc.perform(post("/api/proveedores/guardar").with(user("a@demo.com").roles("TIENDA"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\",\"correo\":\"ventas@\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Revisa los datos marcados"))
                .andExpect(jsonPath("$.campos.nombre").value("Escribe el nombre de la empresa."))
                .andExpect(jsonPath("$.campos.correo").value("Escribe un correo válido, como contacto@proveedor.com."));
    }
}
