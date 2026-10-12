package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Alta de un taller por el administrador con un usuarioId existente: debe ser una cuenta TALLER sin taller. */
class AltaTallerAdminTest {

    private TallerRepository tallerRepository;
    private UsuarioRepository usuarioRepository;
    private CuentaNegocioService cuentas;
    private TallerServiceImpl service;

    @BeforeEach
    void setUp() {
        tallerRepository = mock(TallerRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cuentas = mock(CuentaNegocioService.class);
        SuscripcionService suscripciones = mock(SuscripcionService.class);
        when(suscripciones.vigente(anyString(), anyString())).thenReturn(Optional.empty());
        when(tallerRepository.save(any(Taller.class))).thenAnswer(i -> i.getArgument(0));
        when(tallerRepository.findByUsuarioId(anyString())).thenReturn(Optional.empty());
        service = new TallerServiceImpl(tallerRepository, usuarioRepository, cuentas, suscripciones);
    }

    private static CrearTallerRequest alta(String usuarioId) {
        CrearTallerRequest r = new CrearTallerRequest();
        r.setNombre("Taller Norte");
        r.setCiudad("Cartagena");
        r.setDireccion("Calle 30 # 17-25");
        r.setLatitud(10.391);
        r.setLongitud(-75.479);
        r.setPrecioManoObra(80000.0);
        r.setUsuarioId(usuarioId);
        return r;
    }

    private void cuenta(String id, String rol) {
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(Usuario.builder().id(id).rol(rol).build()));
    }

    @Test
    void unUsuarioQueNoExisteSeRechaza() {
        when(usuarioRepository.findById("fantasma")).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.crear(alta("fantasma")));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(tallerRepository, never()).save(any());
    }

    @Test
    void unaCuentaQueNoEsDeTallerSeRechaza() {
        for (String rol : new String[]{"CLIENTE", "TIENDA", "ADMIN", "SUPERADMIN"}) {
            cuenta("u-" + rol, rol);
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.crear(alta("u-" + rol)));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), rol);
        }
        verify(tallerRepository, never()).save(any());
    }

    @Test
    void unaCuentaQueYaTieneTallerSeRechazaConConflicto() {
        cuenta("u1", "TALLER");
        when(tallerRepository.findByUsuarioId("u1")).thenReturn(Optional.of(Taller.builder().id("t1").usuarioId("u1").build()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.crear(alta("u1")));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(tallerRepository, never()).save(any());
    }

    @Test
    void unaCuentaDeTallerLibreSeEnlazaSinCrearOtroUsuario() {
        cuenta("u2", "TALLER");

        service.crear(alta(" u2 "));

        verify(tallerRepository).save(argThat(t -> "u2".equals(t.getUsuarioId())));
        verify(cuentas, never()).crearUsuario(any(), any(), any(), any(), any(), any());
        verify(cuentas, never()).conCuentaNueva(any(), any());
    }

    @Test
    void sinUsuarioIdSeCreaLaCuentaYElGuardadoPasaPorLaCompensacion() {
        CrearTallerRequest r = alta(null);
        r.setCorreoUsuario("nuevo@demo.com");
        r.setPasswordUsuario("secreto123");
        Usuario creado = Usuario.builder().id("u-nuevo").build();
        when(cuentas.crearUsuario(any(), any(), any(), any(), any(), any())).thenReturn(creado);
        when(cuentas.conCuentaNueva(any(), any())).thenAnswer(i -> ((java.util.function.Supplier<?>) i.getArgument(1)).get());

        service.crear(r);

        verify(cuentas).conCuentaNueva(eq(creado), any());
        verify(tallerRepository).save(argThat(t -> "u-nuevo".equals(t.getUsuarioId())));
    }
}
