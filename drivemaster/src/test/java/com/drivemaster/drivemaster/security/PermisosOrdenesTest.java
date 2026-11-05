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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Quién puede cada acción de una orden, con la cadena de seguridad REAL. El rol MECANICO no opera: el mecánico es un
 * nombre dentro del taller. Que cada taller solo toque lo suyo lo comprueba el servicio (EtapasFinalesTest).
 */
@WebMvcTest(controllers = TiendaController.class)
@Import(SecurityConfig.class)
class PermisosOrdenesTest {

    private static final List<String> ROLES = List.of("SUPERADMIN", "ADMIN", "TIENDA", "TALLER", "MECANICO", "CLIENTE", "VENDEDOR");

    @Autowired
    private MockMvc mvc;

    @MockBean private TiendaService tiendaService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private record Ruta(HttpMethod metodo, String url, List<String> permitidos) {}

    private static final String BASE = "/api/solicitudes-servicio/o1/";

    private static final List<Ruta> RUTAS = List.of(
            new Ruta(HttpMethod.PATCH, BASE + "aceptar", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "rechazar", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "mecanico", List.of("TALLER")),
            new Ruta(HttpMethod.PUT, BASE + "diagnostico", List.of("TALLER")),
            new Ruta(HttpMethod.PUT, BASE + "repuestos", List.of("TALLER")),
            new Ruta(HttpMethod.POST, "/api/cotizaciones/orden/o1/previsualizar", List.of("TALLER")),
            new Ruta(HttpMethod.POST, "/api/cotizaciones/orden/o1", List.of("TALLER")),
            new Ruta(HttpMethod.POST, "/api/pedidos-tienda/orden/o1/recibidos", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "iniciar-instalacion", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "en-pruebas", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "lista", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "entregada", List.of("TALLER")),
            new Ruta(HttpMethod.PATCH, BASE + "cancelar", List.of("CLIENTE")),
            new Ruta(HttpMethod.POST, "/api/cotizaciones/c1/responder", List.of("CLIENTE")),
            new Ruta(HttpMethod.PATCH, BASE + "confirmar-recepcion", List.of("CLIENTE")),
            new Ruta(HttpMethod.PATCH, BASE + "reasignar", List.of("SUPERADMIN", "ADMIN")),
            new Ruta(HttpMethod.GET, BASE + "pagos", List.of("CLIENTE")),
            new Ruta(HttpMethod.PATCH, "/api/plataforma/pedidos/ped1/despachado", List.of("SUPERADMIN")),
            new Ruta(HttpMethod.GET, "/api/talleres/mi-taller/ordenes/o1/resumen", List.of("TALLER")),
            new Ruta(HttpMethod.GET, "/api/plataforma/ordenes/o1/resumen", List.of("SUPERADMIN")));

    private int estado(Ruta r, String rol) throws Exception {
        var peticion = request(r.metodo(), r.url()).contentType(MediaType.APPLICATION_JSON).content("{}");
        return mvc.perform(rol == null ? peticion : peticion.with(user(rol.toLowerCase() + "@demo.com").roles(rol)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void cadaRolSoloHaceLasAccionesQueLeTocan() throws Exception {
        for (Ruta r : RUTAS) {
            for (String rol : ROLES) {
                int status = estado(r, rol);
                String accion = rol + " en " + r.metodo() + " " + r.url() + ": " + status;
                if (r.permitidos().contains(rol)) assertTrue(status != 401 && status != 403, accion);
                else assertEquals(403, status, accion);
            }
        }
    }

    @Test
    void sinSesionNoSeTocaNingunaOrden() throws Exception {
        for (Ruta r : RUTAS) {
            assertEquals(401, estado(r, null), r.metodo() + " " + r.url());
        }
    }
}
