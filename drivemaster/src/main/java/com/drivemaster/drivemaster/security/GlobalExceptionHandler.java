package com.drivemaster.drivemaster.security;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.drivemaster.drivemaster.exception.DatosInvalidosException;
import com.drivemaster.drivemaster.exception.RepartoYaEnPagoException;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(RepartoYaEnPagoException.class)
    public ResponseEntity<?> handleRepartoYaEnPago(RepartoYaEnPagoException ex) {
        Map<String, Object> response = buildResponse(ex.getReason(), HttpStatus.CONFLICT);
        response.put("estado", ex.getEstado());
        response.put("referencia", ex.getReferencia());
        response.put("sinConfirmar", ex.isSinConfirmar());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
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

    /** JSON mal formado, un texto donde va un número, una fecha inválida: es un dato malo, no un fallo del servidor. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> handleDatoIlegible(Exception ex) {
        log.warn("Petición con datos ilegibles: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(buildResponse("Los datos enviados no son válidos. Revísalos e intenta de nuevo.", HttpStatus.BAD_REQUEST));
    }

    /** La base no respondió: se puede reintentar, y el detalle del error no le sirve a quien lo lee. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> handleBaseDeDatos(DataAccessException ex) {
        log.error("Fallo de acceso a datos", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildResponse("No pudimos completar la acción. Intenta de nuevo en un momento.", HttpStatus.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
    }

}