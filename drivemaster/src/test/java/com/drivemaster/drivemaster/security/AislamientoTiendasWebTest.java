package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.CompraController;
import com.drivemaster.drivemaster.controller.ProveedorController;
import com.drivemaster.drivemaster.model.Compra;
import com.drivemaster.drivemaster.model.Proveedor;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.service.CompraService;
import com.drivemaster.drivemaster.service.ProductoService;
import com.drivemaster.drivemaster.service.ProveedorService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rutas reales de compras y proveedores con la seguridad real: solo la TIENDA entra, y siempre con SU tienda, que
 * sale de la sesión y no de lo que mande la petición.
 */
@WebMvcTest(controllers = {CompraController.class, ProveedorController.class})
@Import({SecurityConfig.class, AlcanceTienda.class})
class AislamientoTiendasWebTest {

    @Autowired private MockMvc mvc;

    @MockBean private CompraService compraService;
    @MockBean private ProveedorService proveedorService;
    @MockBean private TiendaService tiendaService;
    @MockBean private ProductoService productoService; // lo necesita AlcanceTienda
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void tiendas() {
        when(tiendaService.obtenerOperativaPorUsuario("a@demo.com")).thenReturn(Tienda.builder().id("A").nombre("Tienda A").usuarioId("uA").build());
        when(tiendaService.obtenerOperativaPorUsuario("b@demo.com")).thenReturn(Tienda.builder().id("B").nombre("Tienda B").usuarioId("uB").build());
        when(compraService.listarDeTienda(anyString())).thenReturn(List.of());
        when(proveedorService.listarDeTienda(anyString())).thenReturn(List.of());
    }

    @Test
    void cadaTiendaConsultaSoloConSuPropioId() throws Exception {
        mvc.perform(get("/api/compras").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        mvc.perform(get("/api/proveedores").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        mvc.perform(get("/api/compras").with(user("b@demo.com").roles("TIENDA"))).andExpect(status().isOk());

        verify(compraService, times(1)).listarDeTienda("A");
        verify(compraService, times(1)).listarDeTienda("B");
        verify(proveedorService, times(1)).listarDeTienda("A");
        verify(proveedorService, never()).listarDeTienda("B");
    }

    @Test
    void unaTiendaPideElRegistroDeOtraConSuPropioIdYElServicioLoNiega() throws Exception {
        when(compraService.obtenerDeTienda("compra-de-B", "A")).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Compra no encontrada"));

        when(compraService.generarPdfDeTienda("compra-de-B", "A")).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Compra no encontrada"));

        mvc.perform(get("/api/compras/compra-de-B").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/compras/compra-de-B/pdf").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isNotFound());

        verify(compraService, never()).obtenerDeTienda(anyString(), eq("B"));
    }

    private static String eq(String v) {
        return org.mockito.ArgumentMatchers.eq(v);
    }

    @Test
    void laTiendaDeLaCompraSalDeLaSesionAunqueElCuerpoDigaOtraCosa() throws Exception {
        when(compraService.registrarCompra(any(Compra.class), any(Tienda.class))).thenReturn(new Compra());

        mvc.perform(post("/api/compras/guardar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":\"p1\",\"tiendaId\":\"B\",\"usuarioId\":\"uB\",\"productos\":[]}")
                        .with(user("a@demo.com").roles("TIENDA")))
                .andExpect(status().isOk());

        ArgumentCaptor<Tienda> tienda = ArgumentCaptor.forClass(Tienda.class);
        verify(compraService).registrarCompra(any(Compra.class), tienda.capture());
        assertEquals("A", tienda.getValue().getId(), "la tienda sale de la sesión, no del cuerpo");
    }

    @Test
    void elProveedorSeCreaEditaYBorraSiempreEnLaTiendaDeLaSesion() throws Exception {
        when(proveedorService.crearEnTienda(any(Proveedor.class), anyString())).thenReturn(new Proveedor());
        when(proveedorService.actualizarEnTienda(anyString(), any(Proveedor.class), anyString())).thenReturn(new Proveedor());

        mvc.perform(post("/api/proveedores/guardar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\",\"tiendaId\":\"B\"}").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        mvc.perform(put("/api/proveedores/p9").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\",\"tiendaId\":\"B\"}").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        mvc.perform(delete("/api/proveedores/p9").with(user("a@demo.com").roles("TIENDA"))).andExpect(status().isNoContent());

        verify(proveedorService).crearEnTienda(any(Proveedor.class), org.mockito.ArgumentMatchers.eq("A"));
        verify(proveedorService).actualizarEnTienda(org.mockito.ArgumentMatchers.eq("p9"), any(Proveedor.class), org.mockito.ArgumentMatchers.eq("A"));
        verify(proveedorService).eliminarDeTienda("p9", "A");
        verify(proveedorService, never()).crearEnTienda(any(Proveedor.class), org.mockito.ArgumentMatchers.eq("B"));
    }

    @Test
    void ningunOtroRolEntraNiVeNada() throws Exception {
        for (String rol : new String[]{"SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE", "TALLER"}) {
            mvc.perform(get("/api/compras").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get("/api/proveedores").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(post("/api/compras/guardar").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/compras")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/proveedores")).andExpect(status().isUnauthorized());
        verifyNoInteractions(compraService, proveedorService);
    }
}
