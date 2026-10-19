package com.drivemaster.drivemaster.dto;

import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las respuestas con datos de una cuenta no exponen la contraseña ni los datos de seguridad. */
class RespuestasSinDatosSensiblesTest {

    private static final List<String> SENSIBLES = List.of(
            "password", "googleId", "permisos", "intentosFallidos", "bloqueado", "fechaBloqueo", "fechaExpiracion");

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Usuario usuario(String proveedor, String googleId) {
        return Usuario.builder()
                .id("u1").nombre("Ana").correo("ana@demo.com").password("$2a$10$hash")
                .googleId(googleId).proveedor(proveedor)
                .identificacion("123").telefono("3001234567").direccion("Calle 1").ciudad("Cali").region("Valle").referencia("Casa azul")
                .rol("CLIENTE").permisos(List.of("VER_CATALOGO")).activo(true)
                .intentosFallidos(2).bloqueado(false).fechaBloqueo(Instant.now()).fechaExpiracion(Instant.now())
                .fechaCreacion(Instant.now()).ultimoLogin(Instant.now())
                .build();
    }

    private void sinSensibles(JsonNode nodo) {
        for (String campo : SENSIBLES) assertFalse(nodo.has(campo), campo + " no debe salir en el JSON");
        assertFalse(nodo.toString().contains("$2a$10$hash"), "el hash de la contraseña no debe salir");
    }

    @Test
    void miPerfilSoloLlevaLosDatosDeContacto() throws Exception {
        JsonNode nodo = json.valueToTree(PerfilClienteResponse.de(usuario("LOCAL", null)));

        sinSensibles(nodo);
        assertEquals(List.of("id", "nombre", "email", "telefono", "documento", "ciudad", "region", "direccion", "referencia",
                "rol", "cuentaGoogle"), iterar(nodo));
        assertEquals("Valle", nodo.get("region").asText());
        assertEquals("ana@demo.com", nodo.get("email").asText());
        assertEquals("123", nodo.get("documento").asText());
        assertFalse(nodo.get("cuentaGoogle").asBoolean());
    }

    @Test
    void laCuentaDeGoogleSeIndicaSinExponerElGoogleId() {
        assertTrue(PerfilClienteResponse.de(usuario("GOOGLE", "g-123")).cuentaGoogle());
        assertTrue(PerfilClienteResponse.de(usuario(null, "g-123")).cuentaGoogle());
        sinSensibles(json.valueToTree(PerfilClienteResponse.de(usuario("GOOGLE", "g-123"))));
    }

    @Test
    void laGestionDeUsuariosNoExponeDatosDeSeguridad() {
        JsonNode nodo = json.valueToTree(UsuarioAdminDTO.de(usuario("LOCAL", "g-1")));
        sinSensibles(nodo);
        assertEquals("CLIENTE", nodo.get("rol").asText());
    }

    @Test
    void laEntidadTampocoSacaLaContrasenaNiLosDatosDeSeguridadSiAlgunaVezSeSerializa() {
        JsonNode nodo = json.valueToTree(usuario("GOOGLE", "g-123"));
        for (String campo : List.of("password", "googleId", "intentosFallidos", "bloqueado", "fechaBloqueo")) {
            assertFalse(nodo.has(campo), campo);
        }
        assertFalse(nodo.toString().contains("$2a$10$hash"));
    }

    @Test
    void elTallerQueVeElClienteSoloLlevaDatosPublicos() {
        Taller taller = Taller.builder()
                .id("t1").usuarioId("u-dueno").nombre("AutoMax").ciudad("Cali").direccion("Cra 1 # 2-3").barrio("Centro")
                .telefono("3001234567").latitud(3.45).longitud(-76.53).especialidades(List.of("Frenos", "Motor"))
                .rating(4.5).calificaciones(8).precioManoObra(80000.0).tarifaHora(60000.0).horario("L-V 8-18")
                .estadoAprobacion("APROBADO").motivoRechazo("x").activo(true).logoUrl("https://img/logo.png")
                .mecanicos(List.of(Mecanico.builder().id("m1").nombre("Carlos").telefono("3110000000").build()))
                .build();

        JsonNode nodo = json.valueToTree(TallerMapaDTO.de(taller));

        assertEquals(List.of("id", "nombre", "direccion", "ciudad", "telefono", "latitud", "longitud", "horario",
                "especialidades", "calificacion", "calificaciones", "logoUrl"), iterar(nodo));
        for (String interno : List.of("usuarioId", "correoUsuario", "mecanicos", "tarifaHora", "precioManoObra",
                "estadoAprobacion", "motivoRechazo", "activo", "planActual")) {
            assertFalse(nodo.has(interno), interno + " no debe salir en el mapa del cliente");
        }
        assertFalse(nodo.toString().contains("u-dueno") || nodo.toString().contains("3110000000"));
        assertEquals(4.5, nodo.get("calificacion").asDouble());
        assertEquals(8, nodo.get("calificaciones").asInt());
    }

    @Test
    void unTallerSinCalificacionesSaleSinPromedio() {
        JsonNode nodo = json.valueToTree(TallerMapaDTO.de(Taller.builder().id("t2").nombre("Nuevo").build()));
        assertTrue(nodo.get("calificacion").isNull());
        assertEquals(0, nodo.get("calificaciones").asInt());
    }

    private static List<String> iterar(JsonNode nodo) {
        List<String> campos = new java.util.ArrayList<>();
        nodo.fieldNames().forEachRemaining(campos::add);
        return campos;
    }
}
