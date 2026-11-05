package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.TallerController;
import com.drivemaster.drivemaster.controller.TiendaController;
import com.drivemaster.drivemaster.controller.UsuarioController;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.service.TallerService;
import com.drivemaster.drivemaster.service.TiendaService;
import com.drivemaster.drivemaster.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea A: el rol VENDEDOR ya no se crea ni se asigna (solo ADMIN y SUPERADMIN desde la gestión de usuarios), y el
 * ADMIN recibe respuestas mínimas al aprobar o rechazar negocios y al listar talleres.
 */
@WebMvcTest(controllers = {UsuarioController.class, TiendaController.class, TallerController.class})
@Import(SecurityConfig.class)
class LimpiezaUsuariosYRespuestasAdminTest {

    @Autowired private MockMvc mvc;

    @MockBean private UsuarioService usuarioService;
    @MockBean private UsuarioRepository usuarioRepository;
    @MockBean private PasswordEncoder passwordEncoder;
    @MockBean private TiendaService tiendaService;
    @MockBean private TallerService tallerService;
    @MockBean private com.drivemaster.drivemaster.service.CuentaNegocioService cuentaNegocioService;
    @MockBean private com.drivemaster.drivemaster.service.NotificacionService notificacionService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void sesion() {
        when(usuarioRepository.findByCorreo("jefe@demo.com")).thenReturn(Optional.of(Usuario.builder().id("u0").rol("SUPERADMIN").build()));
        when(usuarioRepository.findByCorreo(argThatNoEs("jefe@demo.com"))).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioService.crearUsuario(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioService.actualizarUsuario(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static String argThatNoEs(String correo) {
        return org.mockito.ArgumentMatchers.argThat(c -> c != null && !c.equals(correo));
    }

    private int crear(String rol) throws Exception {
        String cuerpo = "{\"nombre\":\"Luis\",\"correo\":\"nuevo" + System.nanoTime() + "@demo.com\",\"password\":\"Clave123!\""
                + (rol == null ? "" : ",\"rol\":\"" + rol + "\"") + "}";
        return mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .with(user("jefe@demo.com").roles("SUPERADMIN"))).andReturn().getResponse().getStatus();
    }

    // ─── Crear usuarios: solo ADMIN y SUPERADMIN ─────────────────────────────

    @Test
    void desdeLaGestionDeUsuariosSoloSeCreanSuperadmins() throws Exception {
        assertEquals(201, crear("SUPERADMIN"));
        assertEquals(201, crear("superadmin"), "sin importar mayúsculas");

        // ADMIN y VENDEDOR quedan en el enum por las cuentas anteriores, pero ya no se crean
        for (String rol : new String[]{"ADMIN", "VENDEDOR", "TIENDA", "TALLER", "CLIENTE", "MECANICO", "INVENTADO", "", null}) {
            assertEquals(400, crear(rol), "el rol " + rol + " no se crea desde aquí");
        }
        org.mockito.ArgumentCaptor<Usuario> guardados = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioService, times(2)).crearUsuario(guardados.capture());
        assertTrue(guardados.getAllValues().stream().allMatch(u -> "SUPERADMIN".equals(u.getRol())));
    }

    @Test
    void elMensajeDeRechazoExplicaPorDondeSeCreanTiendasYTalleres() throws Exception {
        String cuerpo = mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"correo\":\"x@demo.com\",\"password\":\"Clave123!\",\"rol\":\"TIENDA\"}")
                        .with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andReturn().getResolvedException().getMessage();
        assertTrue(cuerpo != null && cuerpo.contains("Registra tu negocio") && cuerpo.contains("Aprobaciones"), String.valueOf(cuerpo));
    }

    // ─── Cambiar de rol: nunca a VENDEDOR; una cuenta de negocio o cliente no cambia ─────

    private int cambiarRol(Usuario destino, String nuevoRol) throws Exception {
        when(usuarioService.obtenerPorId(destino.getId())).thenReturn(destino);
        return mvc.perform(put("/api/usuarios/" + destino.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"" + nuevoRol + "\"}").with(user("jefe@demo.com").roles("SUPERADMIN"))).andReturn().getResponse().getStatus();
    }

    @Test
    void unaCuentaAnteriorDelEquipoSoloPasaASuperadmin() throws Exception {
        Usuario vendedor = Usuario.builder().id("v1").nombre("Vera").rol("VENDEDOR").activo(true).build();
        assertEquals(400, cambiarRol(vendedor, "VENDEDOR"));
        assertEquals(400, cambiarRol(vendedor, "ADMIN"));
        assertEquals(400, cambiarRol(vendedor, "TIENDA"));
        assertEquals(200, cambiarRol(vendedor, "SUPERADMIN"));
        assertEquals("SUPERADMIN", vendedor.getRol());

        Usuario admin = Usuario.builder().id("a1").nombre("Sebastián").rol("ADMIN").activo(true).build();
        assertEquals(200, cambiarRol(admin, "SUPERADMIN"), "un ADMIN anterior pasa a SUPERADMIN");
    }

    @Test
    void nadieSeDesactivaNiSeCambiaElRolASiMismo() throws Exception {
        Usuario yo = Usuario.builder().id("u0").correo("jefe@demo.com").nombre("Jefe").rol("SUPERADMIN").activo(true).build();
        when(usuarioService.obtenerPorId("u0")).thenReturn(yo);
        when(usuarioRepository.findByRol("SUPERADMIN")).thenReturn(List.of(yo, Usuario.builder().id("u9").rol("SUPERADMIN").activo(true).build()));

        mvc.perform(put("/api/usuarios/u0").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")
                .with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/usuarios/u0").contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"ADMIN\"}")
                .with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/usuarios/u0").with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isBadRequest());
        assertTrue(yo.getActivo());
    }

    @Test
    void noSeDesactivaAlUltimoSuperadmin() throws Exception {
        Usuario otro = Usuario.builder().id("s2").correo("otro@demo.com").nombre("Otro").rol("SUPERADMIN").activo(true).build();
        when(usuarioService.obtenerPorId("s2")).thenReturn(otro);
        // Solo queda él activo (el que pide el cambio figura inactivo en esta prueba)
        when(usuarioRepository.findByRol("SUPERADMIN")).thenReturn(List.of(otro,
                Usuario.builder().id("u0").rol("SUPERADMIN").activo(false).build()));

        mvc.perform(put("/api/usuarios/s2").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")
                .with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/usuarios/s2").with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isConflict());
        assertTrue(otro.getActivo());
    }

    @Test
    void unVendedorExistenteSeDesactivaSinBorrarlo() throws Exception {
        Usuario vendedor = Usuario.builder().id("v1").nombre("Vera").rol("VENDEDOR").activo(true).build();
        when(usuarioService.obtenerPorId("v1")).thenReturn(vendedor);

        mvc.perform(put("/api/usuarios/v1").contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}")
                .with(user("jefe@demo.com").roles("SUPERADMIN"))).andExpect(status().isOk());

        assertFalse(vendedor.getActivo());
        assertEquals("VENDEDOR", vendedor.getRol(), "el usuario y su rol se conservan");
    }

    @Test
    void unaCuentaDeClienteTiendaOTallerNoCambiaDeRol() throws Exception {
        for (String rol : new String[]{"CLIENTE", "TIENDA", "TALLER"}) {
            assertEquals(400, cambiarRol(Usuario.builder().id("x-" + rol).rol(rol).build(), "ADMIN"), rol);
        }
    }

    @Test
    void soloElSuperadminGestionaUsuarios() throws Exception {
        for (String rol : new String[]{"ADMIN", "TIENDA", "TALLER", "CLIENTE", "VENDEDOR"}) {
            mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"ADMIN\"}")
                    .with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(put("/api/usuarios/u1").contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"ADMIN\"}")
                    .with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    // ─── Respuestas mínimas para el ADMIN ────────────────────────────────────

    private static TiendaDTO tienda() {
        return TiendaDTO.builder().id("t1").nombre("Repuestos Norte").ciudad("Bogotá").direccion("Cra 1")
                .latitud(4.6).longitud(-74.0).telefono("3001234567").correoContacto("contacto@norte.com")
                .correoUsuario("dueno@norte.com").nit("900123").estadoAprobacion("APROBADO").activo(true)
                .planActual("BASICO").fechaAprobacion(LocalDateTime.of(2026, 10, 1, 9, 0)).build();
    }

    private static TallerDTO taller() {
        return TallerDTO.builder().id("w1").nombre("AutoMax").ciudad("Cali").direccion("Cl 2").barrio("Centro")
                .latitud(3.4).longitud(-76.5).telefono("3109998888").correoUsuario("taller@automax.com")
                .especialidades(List.of("Frenos")).rating(4.5).precioManoObra(60_000.0).tarifaHora(40_000.0)
                .horario("L-S 8-6").activo(true).estadoAprobacion("APROBADO").planActual("BASICO")
                .distanciaKm(2.5).fechaRegistro(LocalDateTime.of(2026, 9, 1, 9, 0)).build();
    }

    private static final String[] PRIVADOS = {"telefono", "3001234567", "3109998888", "correo", "@", "latitud", "longitud",
            "nit", "900123", "planActual", "tarifaHora", "distanciaKm"};

    private static void sinDatosPrivados(String json) {
        for (String privado : PRIVADOS) {
            assertFalse(json.contains(privado), "no debe aparecer \"" + privado + "\" en: " + json);
        }
    }

    @Test
    void alAprobarOrechazarElAdminRecibeSoloLoMinimo() throws Exception {
        when(tiendaService.resolverAprobacion(anyString(), any(), anyString())).thenReturn(tienda());
        when(tallerService.resolverAprobacion(anyString(), any(), anyString())).thenReturn(taller());

        String tiendaJson = mvc.perform(patch("/api/tiendas/t1/aprobacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprobado\":true}").with(user("admin@demo.com").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String tallerJson = mvc.perform(patch("/api/talleres/w1/aprobacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprobado\":false,\"motivo\":\"Documentos\"}").with(user("admin@demo.com").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        sinDatosPrivados(tiendaJson);
        sinDatosPrivados(tallerJson);
        assertTrue(tiendaJson.contains("Repuestos Norte") && tiendaJson.contains("APROBADO"), tiendaJson);
        assertTrue(tallerJson.contains("AutoMax"), tallerJson);
    }

    @Test
    void alAprobarElSuperadminSigueRecibiendoElDtoCompleto() throws Exception {
        when(tiendaService.resolverAprobacion(anyString(), any(), anyString())).thenReturn(tienda());

        String json = mvc.perform(patch("/api/tiendas/t1/aprobacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprobado\":true}").with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(json.contains("3001234567") && json.contains("planActual"), json);
    }

    @Test
    void laListaDeTalleresDelAdminNoLlevaTelefonoCorreosNiCoordenadas() throws Exception {
        when(tallerService.listarTodos(any())).thenReturn(List.of(taller()));

        String json = mvc.perform(get("/api/talleres").with(user("admin@demo.com").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        sinDatosPrivados(json);
        for (String util : new String[]{"AutoMax", "Cali", "Frenos", "4.5", "60000"}) {
            assertTrue(json.contains(util), "falta " + util + " en: " + json);
        }
    }

    @Test
    void laListaDeTalleresDelAdminLlevaCuantasCalificacionesTieneCadaTaller() throws Exception {
        // Sin la cantidad, "Servicios" muestra "Nuevo" a un taller ya calificado (rating 4.5 con 0 calificaciones)
        TallerDTO calificado = taller();
        calificado.setCalificaciones(12);
        TallerDTO nuevo = taller();
        nuevo.setId("w2");
        nuevo.setRating(null);
        nuevo.setCalificaciones(null);
        when(tallerService.listarTodos(any())).thenReturn(List.of(calificado, nuevo));

        mvc.perform(get("/api/talleres").with(user("admin@demo.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(4.5))
                .andExpect(jsonPath("$[0].calificaciones").value(12))
                .andExpect(jsonPath("$[1].rating").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].calificaciones").value(0));
    }

    @Test
    void laListaDeTalleresDelSuperadminSigueCompleta() throws Exception {
        when(tallerService.listarTodos(any())).thenReturn(List.of(taller()));

        String json = mvc.perform(get("/api/talleres").with(user("jefe@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(json.contains("3109998888") && json.contains("latitud") && json.contains("taller@automax.com"), json);
    }
}
