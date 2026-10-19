package com.drivemaster.drivemaster.security;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> buildResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("error", message);
        return response;
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<?> handleUserNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildResponse("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildResponse("Credenciales incorrectas", HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(buildResponse(ex.getReason(), status));
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<?> handleStockInsuficiente(StockInsuficienteException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Stock insuficiente");
        response.put("producto", ex.getProducto());
        response.put("stockDisponible", ex.getStockDisponible());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** Datos de un formulario con errores: el mensaje general y el de cada campo, para mostrarlos en línea. */
    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<?> handleDatosInvalidos(DatosInvalidosException ex) {
        Map<String, Object> response = buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
        response.put("campos", ex.getCampos());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
    }

}