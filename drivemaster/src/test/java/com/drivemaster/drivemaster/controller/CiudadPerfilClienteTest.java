package com.drivemaster.drivemaster.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.UpdateClienteRequest;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;

/** Mi perfil: la ciudad es obligatoria y se escoge de la lista; se guarda con su nombre canónico. */
class CiudadPerfilClienteTest {

    private UsuarioRepository usuarios;
    private ClienteController controller;
    private Authentication auth;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        controller = new ClienteController(usuarios, mock(PasswordEncoder.class));
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ana@d.co");
        cliente = Usuario.builder().id("u1").correo("ana@d.co").rol("CLIENTE").build();
        when(usuarios.findByCorreo("ana@d.co")).thenReturn(Optional.of(cliente));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static UpdateClienteRequest conCiudad(String ciudad) {
        UpdateClienteRequest r = new UpdateClienteRequest();
        r.setCiudad(ciudad);
        r.setTelefono("3001234567");
        return r;
    }

    @Test
    void unaCiudadDeLaListaSeGuardaConSuNombre() {
        controller.actualizarMiPerfil(conCiudad("cartagena"), auth);

        assertEquals("Cartagena", cliente.getCiudad());
    }

    @Test
    void unaCiudadFueraDeLaListaSeRechaza() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.actualizarMiPerfil(conCiudad("bucarambag"), auth));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Escoge tu ciudad de la lista.", e.getReason());
        verify(usuarios, never()).save(any());
    }

    @Test
    void unClienteSinCiudadNoGuardaElPerfilHastaEscogerla() {
        assertThrows(ResponseStatusException.class, () -> controller.actualizarMiPerfil(conCiudad(null), auth));
        verify(usuarios, never()).save(any());
    }

    @Test
    void siYaTieneCiudadPuedeCambiarOtrosDatosSinMandarla() {
        cliente.setCiudad("Turbaco");

        controller.actualizarMiPerfil(conCiudad(null), auth);

        assertEquals("Turbaco", cliente.getCiudad());
        assertEquals("3001234567", cliente.getTelefono());
    }
}
