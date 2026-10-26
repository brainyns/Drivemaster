package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** El login y el registro normalizan el correo; las cuentas antiguas guardadas con mayúsculas se siguen encontrando. */
class UsuarioCorreoSinMayusculasTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final UsuarioServiceImpl service = new UsuarioServiceImpl(repo);

    @Test
    void encuentraLaCuentaAntiguaConMayusculasAunqueSeEscribaEnMinusculas() {
        Usuario viejo = Usuario.builder().id("u1").correo("Taller.Norte@Gmail.com").build();
        when(repo.findByCorreo(anyString())).thenReturn(Optional.empty());
        when(repo.findByCorreoIgnoreCase("taller.norte@gmail.com")).thenReturn(List.of(viejo));

        assertSame(viejo, service.buscarPorCorreo(" taller.norte@GMAIL.com ").orElseThrow());
        assertSame(viejo, service.obtenerPorCorreo("taller.norte@gmail.com"));
    }

    @Test
    void elCorreoGuardadoTalCualNoNecesitaLaBusquedaSinMayusculas() {
        Usuario viejo = Usuario.builder().id("u1").correo("Taller.Norte@Gmail.com").build();
        when(repo.findByCorreo("Taller.Norte@Gmail.com")).thenReturn(Optional.of(viejo));

        assertSame(viejo, service.obtenerPorCorreo("Taller.Norte@Gmail.com")); // así llega desde el token
        verify(repo, never()).findByCorreoIgnoreCase(anyString());
    }

    @Test
    void siDosCuentasSoloSeDiferencianEnMayusculasNoEligeNinguna() {
        when(repo.findByCorreo(anyString())).thenReturn(Optional.empty());
        when(repo.findByCorreoIgnoreCase("ana@demo.com")).thenReturn(List.of(
                Usuario.builder().id("a").correo("Ana@demo.com").build(),
                Usuario.builder().id("b").correo("ANA@demo.com").build()));

        assertTrue(service.buscarPorCorreo("ana@demo.com").isEmpty());
    }

    @Test
    void elCorreoEstaOcupadoSinImportarMayusculas() {
        when(repo.existsByCorreo("ana@demo.com")).thenReturn(false);
        when(repo.findByCorreoIgnoreCase("ana@demo.com")).thenReturn(List.of(Usuario.builder().correo("Ana@Demo.com").build()));

        assertTrue(service.correoOcupado("  ANA@demo.com"));
        assertFalse(service.correoOcupado(null));
    }
}
