package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.dto.CuentaBancariaDTO;
import com.drivemaster.drivemaster.model.CuentaBancaria;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValidacionCuentaBancaria {

    private static final Set<String> TIPOS_CUENTA = Set.of("AHORROS", "CORRIENTE");
    private static final Set<String> TIPOS_DOCUMENTO = Set.of("CC", "NIT", "CE", "PP");
    private static final Pattern NUMERO_CUENTA = Pattern.compile("^[0-9\\-]{4,25}$");
    private static final Pattern CODIGO_BANCO = Pattern.compile("^[A-Za-z0-9]{3,10}$");
    private static final Pattern NUMERO_DOCUMENTO = Pattern.compile("^[A-Za-z0-9\\-]{4,30}$");

    private ValidacionCuentaBancaria() {
    }

    public static CuentaBancaria validarYConvertir(CuentaBancariaDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos bancarios son obligatorios");
        }

        String banco = texto(dto.getBanco(), "El nombre del banco es obligatorio", 80);
        String bancoCodigo = texto(dto.getBancoCodigo(), "El código del banco es obligatorio", 10).toUpperCase();
        if (!CODIGO_BANCO.matcher(bancoCodigo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código del banco no es válido");
        }

        String tipoCuenta = texto(dto.getTipoCuenta(), "El tipo de cuenta es obligatorio", 20).toUpperCase();
        if (!TIPOS_CUENTA.contains(tipoCuenta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de cuenta debe ser AHORROS o CORRIENTE");
        }

        String numeroCuenta = texto(dto.getNumeroCuenta(), "El número de cuenta es obligatorio", 25);
        if (!NUMERO_CUENTA.matcher(numeroCuenta).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de cuenta debe contener solo dígitos y guiones (4 a 25 caracteres)");
        }

        String titularNombre = texto(dto.getTitularNombre(), "El nombre del titular es obligatorio", 120);
        if (titularNombre.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del titular debe tener al menos 3 caracteres");
        }

        String titularDocTipo = texto(dto.getTitularDocumentoTipo(), "El tipo de documento del titular es obligatorio", 10).toUpperCase();
        if (!TIPOS_DOCUMENTO.contains(titularDocTipo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de documento debe ser CC, NIT, CE o PP");
        }

        String titularDocNum = texto(dto.getTitularDocumentoNumero(), "El número de documento del titular es obligatorio", 30);
        if (!NUMERO_DOCUMENTO.matcher(titularDocNum).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de documento del titular no es válido");
        }

        return CuentaBancaria.builder()
                .banco(banco)
                .bancoCodigo(bancoCodigo)
                .tipoCuenta(tipoCuenta)
                .numeroCuenta(numeroCuenta)
                .titularNombre(titularNombre)
                .titularDocumentoTipo(titularDocTipo)
                .titularDocumentoNumero(titularDocNum)
                .fechaActualizacion(LocalDateTime.now())
                .build();
    }

    public static CuentaBancariaDTO toDTO(CuentaBancaria c) {
        if (c == null) return null;
        return CuentaBancariaDTO.builder()
                .banco(c.getBanco())
                .bancoCodigo(c.getBancoCodigo())
                .tipoCuenta(c.getTipoCuenta())
                .numeroCuenta(c.getNumeroCuenta())
                .titularNombre(c.getTitularNombre())
                .titularDocumentoTipo(c.getTitularDocumentoTipo())
                .titularDocumentoNumero(c.getTitularDocumentoNumero())
                .fechaActualizacion(c.getFechaActualizacion())
                .build();
    }

    private static String texto(String valor, String errorSiVacio, int max) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorSiVacio);
        }
        String t = valor.trim();
        if (t.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo supera el máximo permitido de " + max + " caracteres");
        }
        return t;
    }
}

