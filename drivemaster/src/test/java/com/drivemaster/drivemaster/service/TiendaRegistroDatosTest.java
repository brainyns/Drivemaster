package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Datos obligatorios de una tienda al registrarse (dirección, ciudad, horario) y su costo de envío. */
class TiendaRegistroDatosTest {

    private TiendaRepository tiendaRepository;
    private UsuarioRepository usuarioRepository;
    private CuentaNegocioService cuentaNegocioService;
    private TiendaServiceImpl service;

    @BeforeEach
    void setUp() {
        tiendaRepository = mock(TiendaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cuentaNegocioService = mock(CuentaNegocioService.class);
        SuscripcionService suscripciones = mock(SuscripcionService.class);
        when(suscripciones.vigente(anyString(), anyString())).thenReturn(Optional.empty());
        when(tiendaRepository.save(any(Tienda.class))).thenAnswer(i -> i.getArgument(0));
        when(cuentaNegocioService.crearUsuario(any(), any(), any(), any(), any(), any()))
                .thenReturn(Usuario.builder().id("u1").correo("tienda@demo.com").build());
        service = new TiendaServiceImpl(tiendaRepository, usuarioRepository, cuentaNegocioService, suscripciones);
    }

    private static RegistroNegocioRequest registro() {
        RegistroNegocioRequest r = new RegistroNegocioRequest();
        r.setNombreContacto("Luis");
        r.setNombreNegocio("Autopartes Norte");
        r.setCiudad("Bogotá");
        r.setDireccion("Cra 7 # 45-10");
        r.setHorario("L-S 8am-6pm");
        r.setCorreo("tienda@demo.com");
        r.setPassword("secreto123");
        return r;
    }

    @Test
    void elHorarioEsObligatorioAlRegistrarUnaTienda() {
        for (String sinHorario : new String[]{null, "", "   "}) {
            RegistroNegocioRequest r = registro();
            r.setHorario(sinHorario);
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.registrar(r));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertTrue(e.getReason().contains("horario"));
        }
        verify(tiendaRepository, never()).save(any());
        verify(cuentaNegocioService, never()).crearUsuario(any(), any(), any(), any(), any(), any());
    }

    @Test
    void laTiendaRegistradaGuardaDireccionCiudadYHorarioYQuedaCompleta() {
        TiendaDTO dto = service.registrar(registro());

        ArgumentCaptor<Tienda> t = ArgumentCaptor.forClass(Tienda.class);
        verify(tiendaRepository).save(t.capture());
        assertEquals("L-S 8am-6pm", t.getValue().getHorario());
        assertEquals("Bogotá", t.getValue().getCiudad());
        assertEquals("Cra 7 # 45-10", t.getValue().getDireccion());
        assertNull(t.getValue().getCostoEnvio(), "sin costo de envío = solo retiro");
        assertTrue(dto.getDatosCompletos());
        assertEquals("L-S 8am-6pm", dto.getHorario());
    }

    @Test
    void ciudadYDireccionSiguenSiendoObligatorias() {
        // Las valida CuentaNegocioService (real aquí para probar la regla de punta a punta)
        CuentaNegocioService real = new CuentaNegocioService(usuarioRepository, null, null);
        RegistroNegocioRequest sinDireccion = registro();
        sinDireccion.setDireccion(" ");
        assertThrows(ResponseStatusException.class, () -> real.validarRegistro(sinDireccion));
        RegistroNegocioRequest sinCiudad = registro();
        sinCiudad.setCiudad(null);
        assertThrows(ResponseStatusException.class, () -> real.validarRegistro(sinCiudad));
    }

    @Test
    void elCostoDeEnvioEsUnValorFijoDeLaTienda() {
        RegistroNegocioRequest r = registro();
        r.setOfreceEnvio(true);
        r.setCostoEnvio(8_000.4);
        service.registrar(r);
        ArgumentCaptor<Tienda> t = ArgumentCaptor.forClass(Tienda.class);
        verify(tiendaRepository).save(t.capture());
        assertEquals(8_000.0, t.getValue().getCostoEnvio(), "en pesos enteros");
    }

    @Test
    void elCostoDeEnvioDebeSerRazonable() {
        RegistroNegocioRequest negativo = registro();
        negativo.setCostoEnvio(-1.0);
        assertThrows(ResponseStatusException.class, () -> service.registrar(negativo));
        RegistroNegocioRequest enorme = registro();
        enorme.setCostoEnvio(900_000.0);
        assertThrows(ResponseStatusException.class, () -> service.registrar(enorme));
        RegistroNegocioRequest sinCosto = registro();
        sinCosto.setOfreceEnvio(true);
        assertThrows(ResponseStatusException.class, () -> service.registrar(sinCosto));
    }

    @Test
    void unaTiendaExistenteCompletaSusDatosYSusProductosEmpiezanAMostrarse() {
        Tienda vieja = Tienda.builder().id("s1").usuarioId("u1").nombre("Vieja").ciudad("Cali").direccion("Av 3").build();
        when(usuarioRepository.findByCorreo("tienda@demo.com")).thenReturn(Optional.of(Usuario.builder().id("u1").build()));
        when(tiendaRepository.findByUsuarioId("u1")).thenReturn(Optional.of(vieja));
        assertFalse(vieja.tieneDatosCompletos(), "anterior a la 4-A: por completar");

        RegistroNegocioRequest r = new RegistroNegocioRequest();
        r.setHorario("L-V 9-5");
        r.setOfreceEnvio(true);
        r.setCostoEnvio(6_000.0);
        TiendaDTO dto = service.actualizarMiTienda("tienda@demo.com", r);

        assertTrue(dto.getDatosCompletos());
        assertEquals(6_000.0, dto.getCostoEnvio());
        // Y se puede dejar de hacer envíos
        RegistroNegocioRequest sinEnvio = new RegistroNegocioRequest();
        sinEnvio.setOfreceEnvio(false);
        assertNull(service.actualizarMiTienda("tienda@demo.com", sinEnvio).getCostoEnvio());
    }

    @Test
    void noSeInventanDatosAlMigrarUnaTiendaSinHorario() {
        // La tienda anterior no se toca sola: sin horario sigue "por completar"
        Tienda vieja = Tienda.builder().id("s1").nombre("Vieja").ciudad("Cali").direccion("Av 3").build();
        when(tiendaRepository.findById("s1")).thenReturn(Optional.of(vieja));
        TiendaDTO dto = service.obtener("s1");
        assertFalse(dto.getDatosCompletos());
        assertNull(dto.getHorario());
        assertNull(vieja.getHorario());
    }
}
