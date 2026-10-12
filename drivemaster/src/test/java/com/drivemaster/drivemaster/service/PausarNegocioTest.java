package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Al desactivar una cuenta de taller o tienda, su negocio queda pausado (activo = false). */
class PausarNegocioTest {

    private TallerRepository tallerRepository;
    private TiendaRepository tiendaRepository;
    private CuentaNegocioService cuentas;

    @BeforeEach
    void setUp() {
        tallerRepository = mock(TallerRepository.class);
        tiendaRepository = mock(TiendaRepository.class);
        cuentas = new CuentaNegocioService(mock(UsuarioRepository.class), null, null, tallerRepository, tiendaRepository);
    }

    @Test
    void desactivarUnaCuentaDeTallerPausaSuTaller() {
        Taller taller = Taller.builder().id("t1").nombre("Taller Norte").usuarioId("u1").activo(true).build();
        when(tallerRepository.findByUsuarioId("u1")).thenReturn(Optional.of(taller));

        cuentas.pausarNegocioDe(Usuario.builder().id("u1").rol("TALLER").activo(false).build());

        assertFalse(taller.getActivo());
        verify(tallerRepository).save(taller);
        verifyNoInteractions(tiendaRepository);
    }

    @Test
    void desactivarUnaCuentaDeTiendaPausaSuTienda() {
        Tienda tienda = Tienda.builder().id("s1").nombre("Autopartes Sur").usuarioId("u2").activo(true).build();
        when(tiendaRepository.findByUsuarioId("u2")).thenReturn(Optional.of(tienda));

        cuentas.pausarNegocioDe(Usuario.builder().id("u2").rol("TIENDA").activo(false).build());

        assertFalse(tienda.getActivo());
        verify(tiendaRepository).save(tienda);
        verifyNoInteractions(tallerRepository);
    }

    @Test
    void unNegocioYaPausadoNoSeVuelveAGuardar() {
        Taller pausado = Taller.builder().id("t1").usuarioId("u1").activo(false).build();
        when(tallerRepository.findByUsuarioId("u1")).thenReturn(Optional.of(pausado));

        cuentas.pausarNegocioDe(Usuario.builder().id("u1").rol("TALLER").build());

        verify(tallerRepository, never()).save(any());
    }

    @Test
    void lasCuentasSinNegocioNoTocanNada() {
        for (String rol : new String[]{"CLIENTE", "ADMIN", "SUPERADMIN", "VENDEDOR"}) {
            cuentas.pausarNegocioDe(Usuario.builder().id("u-" + rol).rol(rol).build());
        }
        cuentas.pausarNegocioDe(null);
        cuentas.pausarNegocioDe(Usuario.builder().rol("TALLER").build());

        verifyNoInteractions(tallerRepository, tiendaRepository);
    }

    @Test
    void unaCuentaDeTallerSinTallerNoFalla() {
        when(tallerRepository.findByUsuarioId("u9")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> cuentas.pausarNegocioDe(Usuario.builder().id("u9").rol("TALLER").build()));

        verify(tallerRepository, never()).save(any());
    }
}
