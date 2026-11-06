package com.drivemaster.drivemaster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.NitColombia;

/**
 * NIT obligatorio al registrar talleres y tiendas, con formato colombiano (con o sin dígito de verificación), sin
 * repetirse, y que los negocios anteriores sin NIT pueden agregar una sola vez desde Mi taller / Mi tienda.
 */
class NitRegistroNegocioTest {

    private TallerRepository talleres;
    private TiendaRepository tiendas;
    private UsuarioRepository usuarios;
    private CuentaNegocioService cuentas;

    @BeforeEach
    void setUp() {
        talleres = mock(TallerRepository.class);
        tiendas = mock(TiendaRepository.class);
        usuarios = mock(UsuarioRepository.class);
        cuentas = new CuentaNegocioService(usuarios, mock(PasswordEncoder.class), mock(EmailsService.class), talleres, tiendas);
    }

    private static RegistroNegocioRequest registro(String nit) {
        RegistroNegocioRequest r = new RegistroNegocioRequest();
        r.setNombreNegocio("Taller los Lirios");
        r.setNit(nit);
        r.setCiudad("Cartagena");
        r.setDireccion("Calle 30 # 17-25");
        r.setCorreo("lirios@demo.co");
        r.setPassword("secreta1");
        return r;
    }

    private static HttpStatus estado(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // ─── Formato ─────────────────────────────────────────────────────────────

    @Test
    void elFormatoAceptaElNitConOSinDigitoDeVerificacionYLoGuardaSinPuntos() {
        assertEquals("900123456-7", NitColombia.normalizar("900123456-7"));
        assertEquals("900123456", NitColombia.normalizar("900123456"));
        assertEquals("900123456-7", NitColombia.normalizar(" 900.123.456-7 "));
        assertEquals("1047123456-3", NitColombia.normalizar("1047123456-3"), "persona natural: cédula y dígito");
        for (String malo : new String[]{"ABC", "900-123-456", "12345", "900123456-78", "12345678901", "900123456-", "-7"}) {
            assertNull(NitColombia.normalizar(malo), malo);
        }
    }

    @Test
    void elRegistroConNitLoGuardaNormalizado() {
        RegistroNegocioRequest r = registro("900.123.456-7");

        cuentas.validarRegistro(r);

        assertEquals("900123456-7", r.getNit());
    }

    @Test
    void elRegistroSinNitOConNitInvalidoSeRechazaCon400() {
        for (String malo : new String[]{null, "", "   ", "NIT pendiente", "900-123"}) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> cuentas.validarRegistro(registro(malo)));
            assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(e.getStatusCode().value()), String.valueOf(malo));
            assertTrue(e.getReason().contains("NIT"), e.getReason());
        }
    }

    // ─── Duplicados ──────────────────────────────────────────────────────────

    @Test
    void elRegistroConUnNitRepetidoSeRechazaConOSinDigitoDeVerificacion() {
        when(talleres.findAll()).thenReturn(List.of(
                Taller.builder().id("w1").nombre("Otro taller").nit("900123456-7").estadoAprobacion(EstadoAprobacion.APROBADO).build()));

        for (String repetido : new String[]{"900123456-7", "900.123.456-7", "900123456", "9001234567"}) {
            RegistroNegocioRequest r = registro(repetido);
            cuentas.validarRegistro(r);
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> cuentas.validarSinDuplicados(CuentaNegocioService.TALLER, r));
            assertEquals("Ya hay un taller registrado con ese NIT.", e.getReason(), repetido);
        }
    }

    @Test
    void unNitDistintoNoChoca() {
        assertFalse(NitColombia.mismo("900123456-7", "900123457-7"));
        assertFalse(NitColombia.mismo("900123456-7", null));
    }

    // ─── Agregarlo después (negocios anteriores sin NIT) ─────────────────────

    private TiendaServiceImpl tiendaConUsuario(Tienda tienda) {
        when(usuarios.findByCorreo("tienda@demo.co")).thenReturn(Optional.of(Usuario.builder().id("u1").build()));
        when(tiendas.findByUsuarioId("u1")).thenReturn(Optional.of(tienda));
        when(tiendas.save(any(Tienda.class))).thenAnswer(i -> i.getArgument(0));
        SuscripcionService suscripciones = mock(SuscripcionService.class);
        when(suscripciones.vigente(any(), any())).thenReturn(Optional.empty());
        return new TiendaServiceImpl(tiendas, usuarios, cuentas, suscripciones);
    }

    @Test
    void unaTiendaSinNitLoAgregaYQuedaGuardado() {
        Tienda sinNit = Tienda.builder().id("t1").nombre("Frenos Sur").estadoAprobacion(EstadoAprobacion.APROBADO).build();
        when(tiendas.findAll()).thenReturn(List.of(sinNit));

        assertEquals("800555111-2", tiendaConUsuario(sinNit).agregarNit("tienda@demo.co", "800.555.111-2").getNit());
        assertEquals("800555111-2", sinNit.getNit());
    }

    @Test
    void agregarUnNitRepetidoOInvalidoNoSeGuarda() {
        Tienda sinNit = Tienda.builder().id("t1").nombre("Frenos Sur").estadoAprobacion(EstadoAprobacion.APROBADO).build();
        Tienda otra = Tienda.builder().id("t2").nombre("Baterías Norte").nit("800555111").estadoAprobacion(EstadoAprobacion.PENDIENTE).build();
        when(tiendas.findAll()).thenReturn(List.of(sinNit, otra));
        TiendaServiceImpl servicio = tiendaConUsuario(sinNit);

        assertEquals(HttpStatus.CONFLICT, estado(() -> servicio.agregarNit("tienda@demo.co", "800555111-2")));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> servicio.agregarNit("tienda@demo.co", "ochocientos")));
        assertNull(sinNit.getNit());
        verify(tiendas, never()).save(any());
    }

    @Test
    void siYaTieneNitNoSeCambiaDesdeMiTienda() {
        Tienda conNit = Tienda.builder().id("t1").nombre("Frenos Sur").nit("900123456-7").estadoAprobacion(EstadoAprobacion.APROBADO).build();
        TiendaServiceImpl servicio = tiendaConUsuario(conNit);

        assertEquals(HttpStatus.CONFLICT, estado(() -> servicio.agregarNit("tienda@demo.co", "800555111-2")));
        assertEquals("900123456-7", conNit.getNit());
    }

    @Test
    void elMismoNegocioNoChocaConSuPropioRegistro() {
        Taller propio = Taller.builder().id("w1").nombre("Taller los Lirios").estadoAprobacion(EstadoAprobacion.APROBADO).build();
        Taller rechazado = Taller.builder().id("w2").nombre("Viejo").nit("900123456-7").estadoAprobacion(EstadoAprobacion.RECHAZADO).build();
        when(talleres.findAll()).thenReturn(List.of(propio, rechazado));

        assertEquals("900123456-7", cuentas.nitLibre(CuentaNegocioService.TALLER, "w1", "900123456-7"),
                "un rechazado no bloquea, igual que en el registro");
    }
}
