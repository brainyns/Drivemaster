package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.ProductoController;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.service.MovimientoInventarioService;
import com.drivemaster.drivemaster.service.ProductoService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Publicar u ocultar un producto con la seguridad real: solo la TIENDA dueña del producto. */
@WebMvcTest(controllers = ProductoController.class)
@Import({SecurityConfig.class, AlcanceTienda.class})
class PublicarProductoWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private ProductoService productoService;
    @MockBean private MovimientoInventarioService movimientoInventarioService;
    @MockBean private TiendaService tiendaService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private static final String CUERPO_OCULTAR = "{\"publicado\":false}";

    private static Producto producto(String id, String tiendaId, Boolean activo) {
        Producto p = new Producto();
        p.setId(id);
        p.setTiendaId(tiendaId);
        p.setActivo(activo);
        return p;
    }

    @BeforeEach
    void datos() {
        when(tiendaService.obtenerOperativaPorUsuario("a@demo.com")).thenReturn(Tienda.builder().id("A").build());
        when(productoService.obtenerPorId("de-a")).thenReturn(producto("de-a", "A", null));
        when(productoService.obtenerPorId("de-b")).thenReturn(producto("de-b", "B", null));
        when(productoService.cambiarPublicado(anyString(), anyBoolean()))
                .thenAnswer(i -> producto(i.getArgument(0), "A", i.getArgument(1)));
    }

    @Test
    void laTiendaDuenaOcultaYVuelveAPublicarSuProducto() throws Exception {
        mvc.perform(patch("/api/productos/de-a/publicado").with(user("a@demo.com").roles("TIENDA"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_OCULTAR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(patch("/api/productos/de-a/publicado").with(user("a@demo.com").roles("TIENDA"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
        verify(productoService).cambiarPublicado("de-a", false);
        verify(productoService).cambiarPublicado("de-a", true);
    }

    @Test
    void unaTiendaNoOcultaElProductoDeOtra() throws Exception {
        mvc.perform(patch("/api/productos/de-b/publicado").with(user("a@demo.com").roles("TIENDA"))
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_OCULTAR))
                .andExpect(status().isForbidden());
        verify(productoService, never()).cambiarPublicado(anyString(), anyBoolean());
    }

    @Test
    void ningunOtroRolNiUnVisitantePublicaUOculta() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE", "TALLER"}) {
            mvc.perform(patch("/api/productos/de-a/publicado").with(user("x@demo.com").roles(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(CUERPO_OCULTAR))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/productos/de-a/publicado").contentType(MediaType.APPLICATION_JSON).content(CUERPO_OCULTAR))
                .andExpect(status().isUnauthorized());
        verify(productoService, never()).cambiarPublicado(anyString(), anyBoolean());
    }

    @Test
    void sinIndicarSiQuedaPublicadoNoCambiaNada() throws Exception {
        mvc.perform(patch("/api/productos/de-a/publicado").with(user("a@demo.com").roles("TIENDA"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(productoService, never()).cambiarPublicado(anyString(), anyBoolean());
    }
}
