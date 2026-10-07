package com.drivemaster.drivemaster.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Límite de intentos por IP para los endpoints públicos con token (ej. responder
 * una cotización desde el correo). Frena a quien intente adivinar tokens.
 * En memoria: suficiente para una instancia; con varias réplicas cada una lleva
 * su propia cuenta.
 */
@Component
public class LimiteIntentos {

    private static final int INTENTOS = 20;
    private static final Duration VENTANA = Duration.ofMinutes(10);

    private final Cache<String, Bucket> porClave = Caffeine.newBuilder()
            .expireAfterAccess(VENTANA.multipliedBy(2))
            .maximumSize(50_000)
            .build();

    /** Consume un intento; responde 429 si la clave agotó su cupo. */
    public void consumir(String clave) {
        Bucket bucket = porClave.get(clave, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(INTENTOS).refillGreedy(INTENTOS, VENTANA).build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Espera unos minutos e intenta de nuevo.");
        }
    }
}
