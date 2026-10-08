package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.TiendaController;
import com.drivemaster.drivemaster.service.TiendaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Matriz de permisos del panel de plataforma, con la cadena de seguridad REAL (SecurityConfig).
 *
 * SUPERADMIN: panel completo. ADMIN: solo la operación diaria (aprobar tiendas y talleres, servicios,
 * problemas de envío). Las rutas se prueban sin controlador propio: lo que se verifica es que la
 * seguridad deje pasar (cualquier respuesta distinta de 401/403) o corte (403 con sesión, 401 sin ella).
 */
@WebMvcTest(controllers = TiendaController.class)
@Import(SecurityConfig.class)
class PermisosPanelPlataformaTest {

    private static final List<String> ROLES = List.of("SUPERADMIN", "ADMIN", "TIENDA", "TALLER", "CLIENTE", "VENDEDOR");

    @Autowired
    private MockMvc mvc;

    @MockBean private TiendaService tiendaService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private record Ruta(HttpMethod metodo, String url, List<String> permitidos) {}

    private static Ruta ruta(HttpMethod m, String url, String... permitidos) {
        return new Ruta(m, url, List.of(permitidos));
    }

    private static final List<Ruta> RUTAS = List.of(
            // Solo SUPERADMIN: usuarios, reportes, finanzas, proveedores y configuración
            ruta(HttpMethod.GET, "/api/usuarios", "SUPERADMIN"),
            ruta(HttpMethod.POST, "/api/usuarios", "SUPERADMIN"),
            ruta(HttpMethod.PUT, "/api/usuarios/u1", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/reportes/dashboard", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/reportes/servicios/reportes", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/liquidaciones", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/dashboard", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/productos", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/clientes", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/inexistente", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/negocios", "SUPERADMIN"),
            // Tiendas, talleres y planes: el ADMIN solo aprueba o rechaza
            ruta(HttpMethod.GET, "/api/tiendas/t1", "SUPERADMIN"),
            ruta(HttpMethod.PATCH, "/api/tiendas/t1/activo", "SUPERADMIN"),
            ruta(HttpMethod.POST, "/api/talleres", "SUPERADMIN"),
            ruta(HttpMethod.PUT, "/api/talleres/w1", "SUPERADMIN"),
            ruta(HttpMethod.PATCH, "/api/talleres/w1/activo", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/suscripciones", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/suscripciones/planes", "SUPERADMIN"),
            ruta(HttpMethod.POST, "/api/suscripciones", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/reportes/ventas", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/reportes/productos", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/reportes/clientes", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/reportes/servicios", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/reportes/comision", "SUPERADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/usuarios", "SUPERADMIN"),
            // Compras, proveedores e inventario: solo la TIENDA, cada una con lo suyo (lo comprueba AislamientoTiendasWebTest)
            ruta(HttpMethod.GET, "/api/proveedores", "TIENDA"),
            ruta(HttpMethod.GET, "/api/proveedores/p1", "TIENDA"),
            ruta(HttpMethod.POST, "/api/proveedores/guardar", "TIENDA"),
            ruta(HttpMethod.PUT, "/api/proveedores/p1", "TIENDA"),
            ruta(HttpMethod.DELETE, "/api/proveedores/p1", "TIENDA"),
            ruta(HttpMethod.GET, "/api/proveedores/exportar", "TIENDA"),
            ruta(HttpMethod.GET, "/api/tiendas/mi-tienda/informes", "TIENDA"),
            ruta(HttpMethod.GET, "/api/compras/c1", "TIENDA"),
            ruta(HttpMethod.GET, "/api/compras/c1/pdf", "TIENDA"),
            ruta(HttpMethod.POST, "/api/compras/guardar", "TIENDA"),
            ruta(HttpMethod.POST, "/api/movimientos/registrar", "TIENDA"),
            ruta(HttpMethod.PUT, "/inventario/ajuste/p1/5", "TIENDA"),
            ruta(HttpMethod.PUT, "/api/suscripciones/planes/basico", "SUPERADMIN"),
            ruta(HttpMethod.PUT, "/api/config/parametros/COMISION_PCT", "SUPERADMIN"),
            ruta(HttpMethod.POST, "/api/config/metodos-pago", "SUPERADMIN"),
            ruta(HttpMethod.PATCH, "/api/config/metodos-pago/efectivo/toggle", "SUPERADMIN"),

            // Gestión comercial interna: SUPERADMIN y VENDEDOR (ya no ADMIN)
            ruta(HttpMethod.GET, "/api/ventas", "SUPERADMIN", "VENDEDOR"),
            ruta(HttpMethod.GET, "/api/compras", "TIENDA"),
            ruta(HttpMethod.GET, "/api/clientes/c1", "SUPERADMIN", "VENDEDOR"),
            ruta(HttpMethod.GET, "/api/solicitudes", "SUPERADMIN", "VENDEDOR"),

            // Inventario y productos: SUPERADMIN y la TIENDA (que ve solo lo suyo, lo valida el controlador)
            ruta(HttpMethod.GET, "/api/movimientos", "TIENDA"),
            ruta(HttpMethod.POST, "/api/productos", "SUPERADMIN", "VENDEDOR", "TIENDA"),

            // Operación diaria: SUPERADMIN y ADMIN
            ruta(HttpMethod.GET, "/api/plataforma/operacion", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/pedidos", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/plataforma/negocios/pendientes", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/tiendas", "SUPERADMIN"),
            ruta(HttpMethod.PATCH, "/api/tiendas/t1/aprobacion", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/talleres", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.PATCH, "/api/talleres/t1/aprobacion", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/pedidos-tienda/problemas", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.POST, "/api/pedidos-tienda/p1/problema/resolver", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/solicitudes-servicio", "SUPERADMIN", "ADMIN"),
            ruta(HttpMethod.GET, "/api/ordenes-servicio/taller/t1", "SUPERADMIN", "ADMIN")
    );

    private MockHttpServletRequestBuilder peticion(Ruta r) {
        return request(r.metodo(), r.url()).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @Test
    void cadaRolSoloEntraALasRutasQueLeCorresponden() throws Exception {
        for (Ruta r : RUTAS) {
            for (String rol : ROLES) {
                int status = mvc.perform(peticion(r).with(user(rol.toLowerCase() + "@demo.com").roles(rol)))
                        .andReturn().getResponse().getStatus();
                if (r.permitidos().contains(rol)) {
                    assertTrue(status != 401 && status != 403,
                            rol + " debería poder entrar a " + r.metodo() + " " + r.url() + " pero recibió " + status);
                } else {
                    assertTrue(status == 403,
                            rol + " NO debería entrar a " + r.metodo() + " " + r.url() + " y recibió " + status);
                }
            }
        }
    }

    // ─── El ADMIN solo aprueba y rechaza: el plan de prueba es el único que puede asignar ──

    private int aprobar(String rol, String json) throws Exception {
        return mvc.perform(request(HttpMethod.PATCH, "/api/tiendas/t1/aprobacion")
                        .contentType(MediaType.APPLICATION_JSON).content(json).with(user(rol.toLowerCase() + "@demo.com").roles(rol)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void elAdminSoloPuedeAprobarConElPlanDePrueba() throws Exception {
        assertEquals(403, aprobar("ADMIN", "{\"aprobado\":true,\"planCodigo\":\"PREMIUM\"}"), "otro plan: solo SUPERADMIN");
        assertEquals(403, aprobar("ADMIN", "{\"aprobado\":true,\"planCodigo\":\"basico\"}"));
        assertEquals(200, aprobar("ADMIN", "{\"aprobado\":true,\"planCodigo\":\"PRUEBA\"}"));
        assertEquals(200, aprobar("ADMIN", "{\"aprobado\":true,\"planCodigo\":\"prueba\"}"));
        assertEquals(200, aprobar("ADMIN", "{\"aprobado\":true}"), "sin plan arranca con PRUEBA");
        assertEquals(200, aprobar("ADMIN", "{\"aprobado\":false,\"motivo\":\"Documentos incompletos\"}"), "rechazar sí puede");
    }

    @Test
    void elSuperadminPuedeAprobarConCualquierPlan() throws Exception {
        assertEquals(200, aprobar("SUPERADMIN", "{\"aprobado\":true,\"planCodigo\":\"PREMIUM\"}"));
    }

    @Test
    void sinSesionNingunaRutaDelPanelEstaAbierta() throws Exception {
        for (Ruta r : RUTAS) {
            // /api/productos POST y demás: sin sesión siempre 401
            int status = mvc.perform(peticion(r)).andReturn().getResponse().getStatus();
            assertTrue(status == 401, "Sin sesión " + r.metodo() + " " + r.url() + " debería dar 401 y dio " + status);
        }
    }

    @Test
    void elAdminYaNoVeClientesProductosNiFinanzas() throws Exception {
        for (Ruta r : RUTAS) {
            boolean financieraOComercial = r.url().startsWith("/api/usuarios") || r.url().startsWith("/api/reportes")
                    || r.url().startsWith("/api/liquidaciones") || r.url().startsWith("/api/ventas")
                    || r.url().startsWith("/api/compras") || r.url().startsWith("/api/clientes")
                    || r.url().startsWith("/api/productos") || r.url().startsWith("/api/movimientos")
                    || r.url().startsWith("/api/proveedores") || r.url().startsWith("/api/config")
                    || r.url().equals("/api/solicitudes")
                    || (r.url().startsWith("/api/plataforma/") && !r.url().endsWith("/operacion") && !r.url().endsWith("/pedidos") && !r.url().endsWith("/pendientes"));
            if (!financieraOComercial) continue;
            int status = mvc.perform(peticion(r).with(user("admin@demo.com").roles("ADMIN")))
                    .andReturn().getResponse().getStatus();
            assertTrue(status == 403, "ADMIN no debe entrar a " + r.metodo() + " " + r.url() + " y recibió " + status);
        }
    }

    @Test
    void laConfiguracionSeLeeSinSesionPeroNoSeEscribeSinSuperadmin() throws Exception {
        int lectura = mvc.perform(request(HttpMethod.GET, "/api/config/parametros/COMISION_PCT"))
                .andReturn().getResponse().getStatus();
        assertTrue(lectura != 401 && lectura != 403, "La lectura de parámetros es pública y dio " + lectura);

        for (String rol : List.of("ADMIN", "TIENDA", "TALLER", "CLIENTE")) {
            int escritura = mvc.perform(request(HttpMethod.PUT, "/api/config/parametros/COMISION_PCT")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"99\"}")
                            .with(user("x@demo.com").roles(rol)))
                    .andReturn().getResponse().getStatus();
            assertTrue(escritura == 403, rol + " no debe cambiar la comisión y recibió " + escritura);
        }
    }
}
