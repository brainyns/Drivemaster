package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;

/** Antes de crear una tienda o un taller: sin otro igual por nombre, NIT o correo (sin mayúsculas, tildes ni espacios). */
class DuplicadosRegistroNegocioTest {

    private TallerRepository talleres;
    private TiendaRepository tiendas;
    private CuentaNegocioService servicio;

    @BeforeEach
    void setUp() {
        talleres = mock(TallerRepository.class);
        tiendas = mock(TiendaRepository.class);
        servicio = new CuentaNegocioService(mock(UsuarioRepository.class), mock(PasswordEncoder.class),
                mock(EmailsService.class), talleres, tiendas);
    }

    private static RegistroNegocioRequest registro(String nombre, String nit, String correo) {
        RegistroNegocioRequest r = new RegistroNegocioRequest();
        r.setNombreNegocio(nombre);
        r.setNit(nit);
        r.setCorreo(correo);
        return r;
    }

    private static Tienda tienda(String nombre, String nit, String correo, String estado) {
        return Tienda.builder().id("t1").nombre(nombre).nit(nit).correoContacto(correo).estadoAprobacion(estado).build();
    }

    private String mensaje(String tipo, RegistroNegocioRequest r) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> servicio.validarSinDuplicados(tipo, r));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        return e.getReason();
    }

    @Test
    void elNombreSeComparaSinMayusculasTildesNiEspacios() {
        when(tiendas.findAll()).thenReturn(List.of(tienda("Repuestos Los Ríos", null, null, EstadoAprobacion.APROBADO)));

        assertEquals("Ya hay una tienda registrada con ese nombre.",
                mensaje(CuentaNegocioService.TIENDA, registro("  repuestos   LOS rios ", null, "nuevo@x.co")));
        assertEquals("Ya hay una tienda registrada con ese nombre.",
                mensaje(CuentaNegocioService.TIENDA, registro("RepuestosLosRíos", null, "nuevo@x.co")));
    }

    @Test
    void elNitSeComparaSoloPorSusDigitos() {
        when(tiendas.findAll()).thenReturn(List.of(tienda("Frenos Sur", "900.123.456-7", null, EstadoAprobacion.APROBADO)));

        assertEquals("Ya hay una tienda registrada con ese NIT.",
                mensaje(CuentaNegocioService.TIENDA, registro("Otra tienda", "9001234567", "nuevo@x.co")));
    }

    @Test
    void elCorreoDeContactoDeUnaTiendaTambienCuenta() {
        when(tiendas.findAll()).thenReturn(List.of(tienda("Frenos Sur", null, "Ventas@FrenosSur.co", EstadoAprobacion.APROBADO)));

        assertEquals("Ya hay una tienda registrada con ese correo.",
                mensaje(CuentaNegocioService.TIENDA, registro("Otra tienda", null, " ventas@frenossur.co ")));
    }

    @Test
    void enLosTalleresSeComparanNombreYNit() {
        when(talleres.findAll()).thenReturn(List.of(
                Taller.builder().id("w1").nombre("Taller Central").nit("800 555 111").estadoAprobacion(EstadoAprobacion.PENDIENTE).build()));

        assertEquals("Ya hay un taller registrado con ese nombre.",
                mensaje(CuentaNegocioService.TALLER, registro("taller  central", null, "nuevo@x.co")));
        assertEquals("Ya hay un taller registrado con ese NIT.",
                mensaje(CuentaNegocioService.TALLER, registro("Otro taller", "800555111", "nuevo@x.co")));
    }

    @Test
    void bloqueanLosAprobadosPendientesArchivadosYSinEstado_losRechazadosNo() {
        for (String estado : new String[]{EstadoAprobacion.APROBADO, EstadoAprobacion.PENDIENTE, EstadoAprobacion.ARCHIVADO, null}) {
            when(tiendas.findAll()).thenReturn(List.of(tienda("DriveMaster", null, null, estado)));
            mensaje(CuentaNegocioService.TIENDA, registro("Drive Master", null, "nuevo@x.co"));
        }
        when(tiendas.findAll()).thenReturn(List.of(tienda("DriveMaster", "900", "a@b.co", EstadoAprobacion.RECHAZADO)));
        assertDoesNotThrow(() -> servicio.validarSinDuplicados(CuentaNegocioService.TIENDA, registro("Drive Master", "900", "a@b.co")));
    }

    @Test
    void unaTiendaYUnTallerConElMismoNombreNoChocan() {
        when(tiendas.findAll()).thenReturn(List.of(tienda("AutoMax", null, null, EstadoAprobacion.APROBADO)));

        assertDoesNotThrow(() -> servicio.validarSinDuplicados(CuentaNegocioService.TALLER, registro("AutoMax", null, "x@y.co")));
        verify(tiendas, never()).findAll();
    }

    @Test
    void sinNitNiCorreoSoloSeComparaElNombre() {
        when(tiendas.findAll()).thenReturn(List.of(tienda("Frenos Sur", null, null, EstadoAprobacion.APROBADO)));

        assertDoesNotThrow(() -> servicio.validarSinDuplicados(CuentaNegocioService.TIENDA, registro("Baterías Norte", "  ", null)));
    }

    @Test
    void elRegistroDeTiendaYDeTallerValidanDuplicadosAntesDeCrearLaCuenta() {
        CuentaNegocioService cuentas = mock(CuentaNegocioService.class);
        ResponseStatusException duplicado = new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una tienda registrada con ese NIT.");
        org.mockito.Mockito.doThrow(duplicado).when(cuentas).validarSinDuplicados(any(), any());

        TiendaServiceImpl tiendaService = new TiendaServiceImpl(tiendas, mock(UsuarioRepository.class), cuentas,
                mock(SuscripcionService.class));
        RegistroNegocioRequest r = registro("Frenos Sur", "900", "a@b.co");
        r.setHorario("L-S 8-6");
        assertThrows(ResponseStatusException.class, () -> tiendaService.registrar(r));
        verify(cuentas, never()).crearUsuario(any(), any(), any(), any(), any(), any());
    }
}
