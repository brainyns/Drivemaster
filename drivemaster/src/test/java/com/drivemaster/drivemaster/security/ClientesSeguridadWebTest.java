package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.controller.ClienteController;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hallazgos de la auditoría sobre /api/clientes, con la seguridad real: lista pública con datos personales, edición
 * de cualquier cuenta por cualquier usuario con sesión, rol y permisos tomados del cuerpo y borrado sin límites.
 */
@WebMvcTest(controllers = ClienteController.class)
@Import(SecurityConfig.class)
class ClientesSeguridadWebTest {

    private static final String URL = "/api/clientes";

    @Autowired private MockMvc mvc;

    @MockBean private UsuarioRepository usuarios;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private Usuario clienteA;
    private Usuario clienteB;
    private Usuario superadmin;
    private Usuario otroSuperadmin;

    @BeforeEach
    void setUp() {
        clienteA = usuario("A", "a@demo.com", "CLIENTE");
        clienteB = usuario("B", "b@demo.com", "CLIENTE");
        superadmin = usuario("S1", "root@demo.com", "SUPERADMIN");
        otroSuperadmin = usuario("S2", "root2@demo.com", "SUPERADMIN");
        for (Usuario u : List.of(clienteA, clienteB, superadmin, otroSuperadmin)) {
            when(usuarios.findById(u.getId())).thenReturn(Optional.of(u));
            when(usuarios.findByCorreo(u.getCorreo())).thenReturn(Optional.of(u));
        }
        when(usuarios.findAll()).thenReturn(List.of(clienteA, clienteB, superadmin));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Usuario usuario(String id, String correo, String rol) {
        return Usuario.builder().id(id).nombre("Nombre " + id).correo(correo).rol(rol)
                .identificacion("1010" + id).googleId("g-" + id).telefono("300" + id).direccion("Calle " + id)
                .ciudad("Bogotá").permisos(com.drivemaster.drivemaster.security.RolPermisos.de(rol)).activo(true).build();
    }

    // ── 1. La lista ya no es pública y no lleva cédula ni googleId ──

    @Test
    void laListaDeClientesSoloLaVeElSuperadmin() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String rol : new String[]{"ADMIN", "VENDEDOR", "CLIENTE", "TIENDA", "TALLER"}) {
            mvc.perform(get(URL).with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get(URL).with(user("root@demo.com").roles("SUPERADMIN"))).andExpect(status().isOk());
    }

    @Test
    void laListaNoExponeCedulaGoogleIdNiPermisos() throws Exception {
        mvc.perform(get(URL).with(user("root@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correo").value("a@demo.com"))
                .andExpect(jsonPath("$[0].identificacion").doesNotExist())
                .andExpect(jsonPath("$[0].googleId").doesNotExist())
                .andExpect(jsonPath("$[0].permisos").doesNotExist())
                .andExpect(jsonPath("$[0].rol").doesNotExist())
                .andExpect(jsonPath("$[0].intentosFallidos").doesNotExist());
    }

    @Test
    void elDetalleDeUnClienteTampocoExponeGoogleIdNiPermisos() throws Exception {
        mvc.perform(get(URL + "/A").with(user("root@demo.com").roles("SUPERADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificacion").value("1010A")) // el formulario de edición la necesita
                .andExpect(jsonPath("$.googleId").doesNotExist())
                .andExpect(jsonPath("$.permisos").doesNotExist());
    }

    // ── 2. PUT: cada usuario solo edita su cuenta; el SUPERADMIN, cualquiera ──

    @Test
    void unClienteNoEditaLaCuentaDeOtro() throws Exception {
        mvc.perform(put(URL + "/B").with(user("a@demo.com").roles("CLIENTE")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Hackeado\",\"telefono\":\"1\"}")).andExpect(status().isForbidden());
        assertEquals("Nombre B", clienteB.getNombre());
        verify(usuarios, never()).save(clienteB);
    }

    @Test
    void unClienteEditaSuPropiaCuentaYElSuperadminCualquiera() throws Exception {
        mvc.perform(put(URL + "/A").with(user("a@demo.com").roles("CLIENTE")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nuevo nombre\"}")).andExpect(status().isOk());
        assertEquals("Nuevo nombre", clienteA.getNombre());

        mvc.perform(put(URL + "/B").with(user("root@demo.com").roles("SUPERADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\":\"3159999999\"}")).andExpect(status().isOk());
        assertEquals("3159999999", clienteB.getTelefono());
    }

    @Test
    void laEdicionNuncaAceptaRolNiPermisosDelCuerpo() throws Exception {
        List<String> permisosAntes = List.copyOf(clienteA.getPermisos());
        mvc.perform(put(URL + "/A").with(user("a@demo.com").roles("CLIENTE")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"SUPERADMIN\",\"permisos\":[\"ROLE_SUPERADMIN\"],\"activo\":false,\"correo\":\"otro@x.com\"}"));
        assertEquals("CLIENTE", clienteA.getRol());
        assertEquals(permisosAntes, clienteA.getPermisos());
        assertEquals("a@demo.com", clienteA.getCorreo());
        assertEquals(Boolean.TRUE, clienteA.getActivo());
    }

    @Test
    void ElSuperadminTampocoCambiaElRolPorEstaRuta() throws Exception {
        mvc.perform(put(URL + "/B").with(user("root@demo.com").roles("SUPERADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"SUPERADMIN\",\"permisos\":[\"ROLE_SUPERADMIN\"]}"));
        assertEquals("CLIENTE", clienteB.getRol());
        assertFalse(clienteB.getPermisos().contains("ROLE_SUPERADMIN"));
    }

    // ── 3. Crear: el rol y los permisos los pone el servidor ──

    @Test
    void crearUnClienteIgnoraRolYPermisosDelCuerpo() throws Exception {
        when(usuarios.existsByCorreo(anyString())).thenReturn(false);
        mvc.perform(post(URL + "/guardar").with(user("root@demo.com").roles("SUPERADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nuevo\",\"correo\":\"nuevo@demo.com\",\"password\":\"Segura123\","
                        + "\"rol\":\"SUPERADMIN\",\"permisos\":[\"ROLE_SUPERADMIN\",\"USUARIOS_DELETE\"]}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(guardado.capture());
        assertEquals("CLIENTE", guardado.getValue().getRol());
        assertEquals(RolPermisos.de("CLIENTE"), guardado.getValue().getPermisos());
        assertNotEquals("Segura123", guardado.getValue().getPassword()); // nunca en claro
    }

    @Test
    void soloElSuperadminCreaClientesDesdeAqui() throws Exception {
        for (String rol : new String[]{"ADMIN", "VENDEDOR", "CLIENTE", "TIENDA", "TALLER"}) {
            mvc.perform(post(URL + "/guardar").with(user("x@demo.com").roles(rol)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"correo\":\"z@demo.com\"}")).andExpect(status().isForbidden());
        }
        verify(usuarios, never()).save(any(Usuario.class));
    }

    // ── 4. Borrar: solo SUPERADMIN, nunca a un SUPERADMIN ni a sí mismo, y se desactiva ──

    @Test
    void soloElSuperadminBorraClientes() throws Exception {
        for (String rol : new String[]{"ADMIN", "VENDEDOR", "CLIENTE", "TIENDA", "TALLER"}) {
            mvc.perform(delete(URL + "/B").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(delete(URL + "/B")).andExpect(status().isUnauthorized());
        verify(usuarios, never()).deleteById(anyString());
    }

    @Test
    void nuncaSeBorraAOtroSuperadminNiAUnoMismo() throws Exception {
        mvc.perform(delete(URL + "/S2").with(user("root@demo.com").roles("SUPERADMIN"))).andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/S1").with(user("root@demo.com").roles("SUPERADMIN"))).andExpect(status().isForbidden());
        verify(usuarios, never()).deleteById(anyString());
        assertEquals(Boolean.TRUE, otroSuperadmin.getActivo());
        assertEquals(Boolean.TRUE, superadmin.getActivo());
    }

    @Test
    void borrarUnClienteLoDesactivaYNoLoElimina() throws Exception {
        mvc.perform(delete(URL + "/B").with(user("root@demo.com").roles("SUPERADMIN"))).andExpect(status().isNoContent());
        verify(usuarios, never()).deleteById(anyString());
        assertEquals(Boolean.FALSE, clienteB.getActivo());
        // y deja de aparecer en la lista
        mvc.perform(get(URL).with(user("root@demo.com").roles("SUPERADMIN")))
                .andExpect(jsonPath("$[?(@.id=='B')]").isEmpty());
    }

    @Test
    void exportarYBuscarPorIdSonSoloDelSuperadmin() throws Exception {
        for (String rol : new String[]{"ADMIN", "VENDEDOR", "CLIENTE"}) {
            mvc.perform(get(URL + "/exportar").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
            mvc.perform(get(URL + "/B").with(user("x@demo.com").roles(rol))).andExpect(status().isForbidden());
        }
    }
}
