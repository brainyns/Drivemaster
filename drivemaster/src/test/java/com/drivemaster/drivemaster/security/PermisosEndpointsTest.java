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

    private static final String JSON_RESOLUCION = "{\"resolucion\":\"REEMBOLSAR\",\"motivo\":\"No llegó\"}";

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
                org.mockito.ArgumentMatchers.eq("REEMBOLSAR"), org.mockito.ArgumentMatchers.eq("No llegó"));
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
        mvc.perform(put("/api/tiendas/plataforma/datos").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isOk());
        // El ADMIN solo aprueba y rechaza negocios: los datos de la tienda propia ya no son suyos
        for (String rol : new String[]{"ADMIN", "TIENDA", "CLIENTE", "TALLER"}) {
            mvc.perform(put("/api/tiendas/plataforma/datos").contentType(MediaType.APPLICATION_JSON).content("{}")
                    .with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        verify(tiendaService, times(1)).actualizarPlataforma(org.mockito.ArgumentMatchers.any());
    }

    // ─── Bloque 3: lista de problemas (ADMIN) y vista de la tienda ───────────

    @Test
    void soloAdministracionVeLosProblemasDeEnvio() throws Exception {
        for (String rol : new String[]{"ADMIN", "SUPERADMIN"}) {
            mvc.perform(get("/api/pedidos-tienda/problemas").with(user("jefe@demo.com").roles(rol))).andExpect(status().isOk());
        }
        for (String rol : new String[]{"TIENDA", "CLIENTE", "TALLER"}) {
            mvc.perform(get("/api/pedidos-tienda/problemas").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/pedidos-tienda/problemas")).andExpect(status().isUnauthorized());
        verify(pedidoTiendaService, times(2)).problemasDeEnvio();
    }

    private static com.drivemaster.drivemaster.model.PedidoTienda pedidoConDatosDelCliente(String entrega) {
        return com.drivemaster.drivemaster.model.PedidoTienda.builder().id("p1").tiendaId("s1").tiendaNombre("Tienda Norte")
                .origen("CATALOGO").entrega(entrega).estado("PENDIENTE").total(47_600.0)
                .clienteId("u-secreto").clienteNombre("Ana Cuenta").compraId("c-secreta").pagoId(99L)
                .envioReceptor("Ana Pérez").envioTelefono("3001234567").envioDireccion("Cra 10 # 20-30").envioCiudad("Medellín")
                .codigoRetiroHash("hash-secreto").codigoRetiroIntentos(2).build();
    }

    @Test
    void laTiendaRecibeSusPedidosSinIdentificadoresInternosNiCodigos() throws Exception {
        when(pedidoTiendaService.misPedidos(anyString())).thenReturn(java.util.List.of(
                pedidoConDatosDelCliente("RETIRO"), pedidoConDatosDelCliente("ENVIO")));

        String json = mvc.perform(get("/api/pedidos-tienda/mios").with(user("tienda@demo.com").roles("TIENDA")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Los valores internos no viajan (las claves pueden venir en null) y los hashes ni siquiera existen
        for (String secreto : new String[]{"u-secreto", "Ana Cuenta", "c-secreta", "hash-secreto", "codigoRetiroHash", "codigoRetiroIntentos"}) {
            org.junit.jupiter.api.Assertions.assertFalse(json.contains(secreto), "no debe viajar a la tienda: " + secreto + " en " + json);
        }
        // Un retiro no lleva dirección del cliente; un envío sí, porque la necesita para despachar
        com.fasterxml.jackson.databind.JsonNode lista = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        org.junit.jupiter.api.Assertions.assertTrue(lista.get(0).path("envioDireccion").isMissingNode()
                || lista.get(0).path("envioDireccion").isNull());
        org.junit.jupiter.api.Assertions.assertEquals("Cra 10 # 20-30", lista.get(1).path("envioDireccion").asText());
        org.junit.jupiter.api.Assertions.assertEquals("3001234567", lista.get(1).path("envioTelefono").asText());
    }

    @Test
    void lasAccionesDeLaTiendaTampocoDevuelvenDatosInternos() throws Exception {
        com.drivemaster.drivemaster.model.PedidoTienda p = pedidoConDatosDelCliente("RETIRO");
        when(pedidoTiendaService.listoParaRetirar(anyString(), anyString())).thenReturn(p);
        when(pedidoTiendaService.validarRetiro(anyString(), anyString(), anyString())).thenReturn(p);
        when(pedidoTiendaService.despachar(anyString(), anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(p);

        String[] respuestas = {
                mvc.perform(patch("/api/pedidos-tienda/p1/listo-retiro").with(user("t@demo.com").roles("TIENDA")))
                        .andReturn().getResponse().getContentAsString(),
                mvc.perform(post("/api/pedidos-tienda/p1/retiro/validar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"ABCD-1234\"}").with(user("t@demo.com").roles("TIENDA")))
                        .andReturn().getResponse().getContentAsString(),
                mvc.perform(patch("/api/pedidos-tienda/p1/despachar").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user("t@demo.com").roles("TIENDA"))).andReturn().getResponse().getContentAsString(),
        };
        for (String json : respuestas) {
            org.junit.jupiter.api.Assertions.assertFalse(json.contains("u-secreto") || json.contains("hash-secreto")
                    || json.contains("Ana Cuenta") || json.contains("c-secreta") || json.contains("99") && json.contains("\"pagoId\":99"), json);
            org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"pagoId\":null") && json.contains("\"clienteId\":null"), json);
        }
    }
}
