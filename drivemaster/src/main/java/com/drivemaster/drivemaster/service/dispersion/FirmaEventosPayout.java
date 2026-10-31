package com.drivemaster.drivemaster.service.dispersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Firma de los eventos de Wompi Pagos a Terceros: SHA-256 de los valores de signature.properties (rutas dentro de
 * data, en ese orden y sin separadores) + timestamp + secreto de eventos. Las propiedades pueden cambiar entre
 * eventos, por eso se leen de cada uno. Sin secreto configurado se rechaza todo.
 * https://docs.wompi.co/docs/colombia/eventos-pagos-a-terceros/
 */
@Component
public class FirmaEventosPayout {

    private static final Logger log = LoggerFactory.getLogger(FirmaEventosPayout.class);

    private final String secreto;

    public FirmaEventosPayout(@Value("${wompi.payouts.events_secret:}") String secreto,
                              @Value("${dispersion.modo:SIMULADO}") String modo) {
        this.secreto = secreto != null ? secreto.trim() : "";
        if (this.secreto.isEmpty() && WompiPayoutService.MODO.equalsIgnoreCase(modo != null ? modo.trim() : "")) {
            log.error("Falta WOMPI_PAYOUTS_EVENTS_SECRET: se rechazarán todos los eventos de Wompi Pagos a Terceros");
        }
    }

    /** {@code cabecera} es X-Event-Checksum; si no llega, se usa signature.checksum del cuerpo. */
    public boolean esValida(Map<String, Object> evento, String cabecera) {
        if (secreto.isEmpty()) {
            log.warn("Evento de Wompi Payouts rechazado: falta WOMPI_PAYOUTS_EVENTS_SECRET");
            return false;
        }
        if (evento == null || !(evento.get("signature") instanceof Map<?, ?> firma)
                || !(firma.get("properties") instanceof List<?> propiedades) || propiedades.isEmpty()
                || !(evento.get("data") instanceof Map<?, ?> data) || evento.get("timestamp") == null) {
            return false;
        }
        String recibido = cabecera != null && !cabecera.isBlank() ? cabecera.trim()
                : firma.get("checksum") != null ? String.valueOf(firma.get("checksum")).trim() : "";
        if (recibido.isEmpty()) return false;

        StringBuilder cadena = new StringBuilder();
        for (Object propiedad : propiedades) {
            Object valor = valor(data, propiedad);
            if (valor == null) return false;
            cadena.append(valor);
        }
        cadena.append(evento.get("timestamp")).append(secreto);
        return MessageDigest.isEqual(sha256(cadena.toString()).getBytes(StandardCharsets.UTF_8),
                recibido.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** Valor de una ruta como "transaction.id" dentro de data; null si no existe. */
    private static Object valor(Map<?, ?> data, Object propiedad) {
        if (!(propiedad instanceof String ruta)) return null;
        Object actual = data;
        for (String parte : ruta.split("\\.")) {
            if (!(actual instanceof Map<?, ?> mapa)) return null;
            actual = mapa.get(parte);
        }
        return actual instanceof Map<?, ?> || actual instanceof List<?> ? null : actual;
    }

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
