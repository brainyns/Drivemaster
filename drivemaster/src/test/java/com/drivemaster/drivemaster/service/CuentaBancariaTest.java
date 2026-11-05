package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CuentaBancariaDTO;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.model.CuentaBancaria;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.ValidacionCuentaBancaria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CuentaBancariaTest {

    private CuentaBancariaDTO cuentaValida() {
        return CuentaBancariaDTO.builder()
                .banco("Bancolombia")
                .bancoCodigo("BANCOL")
                .tipoCuenta("AHORROS")
                .numeroCuenta("123-456789-01")
                .titularNombre("Juan Pérez")
                .titularDocumentoTipo("CC")
                .titularDocumentoNumero("1020304050")
                .build();
    }

    @Test
    @DisplayName("Validación y conversión exitosa de cuenta bancaria")
    void validacionExitosa() {
        CuentaBancariaDTO dto = cuentaValida();
        CuentaBancaria cb = ValidacionCuentaBancaria.validarYConvertir(dto);

        assertNotNull(cb);
        assertEquals("Bancolombia", cb.getBanco());
        assertEquals("BANCOL", cb.getBancoCodigo());
        assertEquals("AHORROS", cb.getTipoCuenta());
        assertEquals("123-456789-01", cb.getNumeroCuenta());
        assertEquals("Juan Pérez", cb.getTitularNombre());
        assertEquals("CC", cb.getTitularDocumentoTipo());
        assertEquals("1020304050", cb.getTitularDocumentoNumero());
        assertNotNull(cb.getFechaActualizacion());

        CuentaBancariaDTO mappedDTO = ValidacionCuentaBancaria.toDTO(cb);
        assertNotNull(mappedDTO);
        assertEquals(cb.getBanco(), mappedDTO.getBanco());
        assertEquals(cb.getNumeroCuenta(), mappedDTO.getNumeroCuenta());
    }

    @Test
    @DisplayName("Rechaza DTO nulo")
    void rechazaDtoNulo() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ValidacionCuentaBancaria.validarYConvertir(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Rechaza banco vacío")
    void rechazaBancoVacio() {
        CuentaBancariaDTO dto = cuentaValida();
        dto.setBanco("   ");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ValidacionCuentaBancaria.validarYConvertir(dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Rechaza tipo de cuenta inválido")
    void rechazaTipoCuentaInvalido() {
        CuentaBancariaDTO dto = cuentaValida();
        dto.setTipoCuenta("NOMINA");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ValidacionCuentaBancaria.validarYConvertir(dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Rechaza tipo de documento no permitido")
    void rechazaTipoDocumentoInvalido() {
        CuentaBancariaDTO dto = cuentaValida();
        dto.setTitularDocumentoTipo("TI");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ValidacionCuentaBancaria.validarYConvertir(dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Rechaza número de cuenta con caracteres inválidos")
    void rechazaNumeroCuentaInvalido() {
        CuentaBancariaDTO dto = cuentaValida();
        dto.setNumeroCuenta("12345 ABC 67");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ValidacionCuentaBancaria.validarYConvertir(dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("TallerServiceImpl.guardarCuentaBancaria asocia y persiste la cuenta bancaria")
    void tallerGuardaCuentaBancaria() {
        TallerRepository tallerRepo = mock(TallerRepository.class);
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        CuentaNegocioService cuentas = mock(CuentaNegocioService.class);
        SuscripcionService suscripciones = mock(SuscripcionService.class);

        Usuario user = Usuario.builder().id("usr-1").correo("taller@test.com").build();
        Taller taller = Taller.builder().id("tal-1").usuarioId("usr-1").nombre("Taller Pro").build();

        when(usuarioRepo.findByCorreo("taller@test.com")).thenReturn(Optional.of(user));
        when(tallerRepo.findByUsuarioId("usr-1")).thenReturn(Optional.of(taller));
        when(tallerRepo.save(any(Taller.class))).thenAnswer(i -> i.getArgument(0));
        when(suscripciones.vigente(anyString(), anyString())).thenReturn(Optional.empty());

        TallerServiceImpl service = new TallerServiceImpl(tallerRepo, usuarioRepo, cuentas, suscripciones);

        TallerDTO res = service.guardarCuentaBancaria("taller@test.com", cuentaValida());
        assertNotNull(res);
        assertNotNull(res.getCuentaBancaria());
        assertEquals("Bancolombia", res.getCuentaBancaria().getBanco());
        assertEquals("123-456789-01", res.getCuentaBancaria().getNumeroCuenta());
        verify(tallerRepo, times(1)).save(any(Taller.class));
    }

    @Test
    @DisplayName("TiendaServiceImpl.guardarCuentaBancaria asocia y persiste la cuenta bancaria")
    void tiendaGuardaCuentaBancaria() {
        TiendaRepository tiendaRepo = mock(TiendaRepository.class);
        UsuarioRepository usuarioRepo = mock(UsuarioRepository.class);
        CuentaNegocioService cuentas = mock(CuentaNegocioService.class);
        SuscripcionService suscripciones = mock(SuscripcionService.class);

        Usuario user = Usuario.builder().id("usr-2").correo("tienda@test.com").build();
        Tienda tienda = Tienda.builder().id("tie-1").usuarioId("usr-2").nombre("Repuestos Ya").build();

        when(usuarioRepo.findByCorreo("tienda@test.com")).thenReturn(Optional.of(user));
        when(tiendaRepo.findByUsuarioId("usr-2")).thenReturn(Optional.of(tienda));
        when(tiendaRepo.save(any(Tienda.class))).thenAnswer(i -> i.getArgument(0));
        when(suscripciones.vigente(anyString(), anyString())).thenReturn(Optional.empty());

        TiendaServiceImpl service = new TiendaServiceImpl(tiendaRepo, usuarioRepo, cuentas, suscripciones);

        TiendaDTO res = service.guardarCuentaBancaria("tienda@test.com", cuentaValida());
        assertNotNull(res);
        assertNotNull(res.getCuentaBancaria());
        assertEquals("Bancolombia", res.getCuentaBancaria().getBanco());
        assertEquals("123-456789-01", res.getCuentaBancaria().getNumeroCuenta());
        verify(tiendaRepo, times(1)).save(any(Tienda.class));
    }
}

