package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.CatalogoController;
import com.drivemaster.drivemaster.controller.PedidoTiendaController;
import com.drivemaster.drivemaster.controller.TiendaController;
import com.drivemaster.drivemaster.service.CatalogoService;
import com.drivemaster.drivemaster.service.CompraCatalogoService;
import com.drivemaster.drivemaster.service.PedidoTiendaService;
import com.drivemaster.drivemaster.service.RecepcionEnvioService;
import com.drivemaster.drivemaster.service.TiendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permisos reales de SecurityConfig sobre los endpoints nuevos de la Fase 4 (no se mockea la
 * seguridad: se carga la misma cadena de filtros y se prueba con cada rol).
 *
 * Lo más delicado: resolver un problema de envío (decide si se paga a la tienda o se reembolsa
 * al cliente) solo puede hacerlo ADMIN o SUPERADMIN.
 */
@WebMvcTest(controllers = {PedidoTiendaController.class, CatalogoController.class, TiendaController.class})
@Import(SecurityConfig.class)
class PermisosEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private PedidoTiendaService pedidoTiendaService;
    @MockBean private CatalogoService catalogoService;
    @MockBean private CompraCatalogoService compraCatalogoService;
    @MockBean private RecepcionEnvioService recepcionEnvioService;
    @MockBean private TiendaService tiendaService;
    // La cadena de seguridad real los necesita; sin Authorization el filtro JWT deja pasar la petición
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @org.junit.jupiter.api.BeforeEach
    void servicios() {
        // Los controladores convierten el pedido a DTO: los mocks devuelven uno vacío
        com.drivemaster.drivemaster.model.PedidoTienda vacio = com.drivemaster.drivemaster.model.PedidoTienda.builder().build();
        when(recepcionEnvioService.confirmarRecepcion(anyString(), anyString())).thenReturn(vacio);
        when(recepcionEnvioService.reportarProblema(anyString(), anyString(), anyString())).thenReturn(vacio);
    }

    private static final String JSON_RESOLUCION = "{\"resolucion\":\"REEMBOLSAR\"}";

    private MockHttpServletRequestBuilder resolver() {
        return post("/api/pedidos-tienda/p1/problema/resolver").contentType(MediaType.APPLICATION_JSON).content(JSON_RESOLUCION);
    }

    // ─── Resolver un problema de envío: solo ADMIN / SUPERADMIN ──────────────

    @Test
    void adminYSuperadminPuedenResolverUnProblema() throws Exception {
        for (String rol : new String[]{"ADMIN", "SUPERADMIN"}) {
            mvc.perform(resolver().with(user("jefe@demo.com").roles(rol))).andExpect(status().isOk());
        }
        verify(pedidoTiendaService, times(2)).resolverProblema(anyString(), org.mockito.ArgumentMatchers.eq("p1"),
                org.mockito.ArgumentMatchers.eq("REEMBOLSAR"));
    }

    @Test
    void clienteTiendaYTallerNoPuedenResolverUnProblema() throws Exception {
        for (String rol : new String[]{"CLIENTE", "TIENDA", "TALLER", "MECANICO"}) {
            mvc.perform(resolver().with(user("intruso@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(pedidoTiendaService);
    }

    @Test
    void sinSesionNoSePuedeResolverUnProblema() throws Exception {
        mvc.perform(resolver()).andExpect(status().isUnauthorized());
        verifyNoInteractions(pedidoTiendaService);
    }

    // ─── Confirmar recepción y reportar problema: solo el CLIENTE ────────────

    @Test
    void soloElClienteConfirmaLaRecepcionOReportaUnProblema() throws Exception {
        for (String ruta : new String[]{"/api/catalogo/pedidos/p1/recibido", "/api/catalogo/pedidos/p1/problema"}) {
            MockHttpServletRequestBuilder base = post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"detalle\":\"no llegó\"}");
            mvc.perform(base.with(user("ana@demo.com").roles("CLIENTE"))).andExpect(status().isOk());
            for (String rol : new String[]{"ADMIN", "SUPERADMIN", "TIENDA", "TALLER"}) {
                mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"detalle\":\"x\"}")
                        .with(user("otro@demo.com").roles(rol))).andExpect(status().isForbidden());
            }
            mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        }
        verify(recepcionEnvioService, times(1)).confirmarRecepcion(anyString(), anyString());
        verify(recepcionEnvioService, times(1)).reportarProblema(anyString(), anyString(), anyString());
    }

    // ─── Retiro y despacho: solo la TIENDA ───────────────────────────────────

    @Test
    void soloLaTiendaValidaElCodigoYMarcaListoParaRetirar() throws Exception {
        mvc.perform(post("/api/pedidos-tienda/p1/retiro/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"ABCD-1234\"}").with(user("tienda@demo.com").roles("TIENDA"))).andExpect(status().isOk());
        mvc.perform(patch("/api/pedidos-tienda/p1/listo-retiro").with(user("tienda@demo.com").roles("TIENDA"))).andExpect(status().isOk());

        for (String rol : new String[]{"CLIENTE", "ADMIN", "SUPERADMIN", "TALLER"}) {
            mvc.perform(post("/api/pedidos-tienda/p1/retiro/validar").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"codigo\":\"ABCD-1234\"}").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/pedidos-tienda/p1/listo-retiro").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        verify(pedidoTiendaService, times(1)).validarRetiro(anyString(), anyString(), anyString());
        verify(pedidoTiendaService, times(1)).listoParaRetirar(anyString(), anyString());
    }

    // ─── Catálogo: la consulta es pública, la compra es del CLIENTE ──────────

    @Test
    void elCatalogoSeConsultaSinSesion() throws Exception {
        mvc.perform(get("/api/catalogo/productos")).andExpect(status().isOk());
        mvc.perform(get("/api/catalogo/productos/p1")).andExpect(status().isOk());
        mvc.perform(get("/api/catalogo/filtros")).andExpect(status().isOk());
    }

    @Test
    void soloElClienteCotizaPagaYVeSusCompras() throws Exception {
        String[][] rutas = {{"POST", "/api/catalogo/compras/cotizar"}, {"POST", "/api/catalogo/compras"},
                {"GET", "/api/catalogo/compras/mias"}, {"POST", "/api/catalogo/pedidos/p1/codigo-retiro"}};
        for (String[] r : rutas) {
            mvc.perform(pedido(r).with(user("ana@demo.com").roles("CLIENTE"))).andExpect(status().isOk());
            for (String rol : new String[]{"TIENDA", "TALLER", "ADMIN"}) {
                mvc.perform(pedido(r).with(user("otro@demo.com").roles(rol))).andExpect(status().isForbidden());
            }
            mvc.perform(pedido(r)).andExpect(status().isUnauthorized());
        }
    }

    private static MockHttpServletRequestBuilder pedido(String[] r) {
        MockHttpServletRequestBuilder b = "GET".equals(r[0]) ? get(r[1]) : post(r[1]);
        return b.contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @Test
    void unaRutaDelCatalogoQueNoExisteSeNiegaPorDefecto() throws Exception {
        mvc.perform(delete("/api/catalogo/compras/c1").with(user("ana@demo.com").roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/catalogo/compras/c1").with(user("admin@demo.com").roles("ADMIN"))).andExpect(status().isForbidden());
    }

    // ─── Datos de la tienda propia: solo administración ──────────────────────

    @Test
    void soloAdministracionCompletaLosDatosDeLaTiendaPropia() throws Exception {
        for (String rol : new String[]{"ADMIN", "SUPERADMIN"}) {
            mvc.perform(put("/api/tiendas/plataforma/datos").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .with(user("jefe@demo.com").roles(rol))).andExpect(status().isOk());
        }
        for (String rol : new String[]{"TIENDA", "CLIENTE", "TALLER"}) {
            mvc.perform(put("/api/tiendas/plataforma/datos").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        verify(tiendaService, times(2)).actualizarPlataforma(org.mockito.ArgumentMatchers.any());
    }
}
