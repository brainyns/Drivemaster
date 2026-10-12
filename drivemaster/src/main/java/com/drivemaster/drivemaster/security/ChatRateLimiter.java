package com.drivemaster.drivemaster.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Límite de mensajes de chat por IP. Sustituye al Semaphore interno que tenía
 * GeminiServiceImpl: ahora protege la puerta de entrada del proxy hacia n8n,
 * de modo que una IP no pueda disparar llamadas costosas al agente sin control.
 * En memoria (como LimiteIntentos): suficiente para una instancia.
 */
@Component
public class ChatRateLimiter {

    private final int maxPorMinuto;

    private final Cache<String, Bucket> porIp = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public ChatRateLimiter(@Value("${n8n.chat.rate.max-por-minuto:15}") int maxPorMinuto) {
        this.maxPorMinuto = Math.max(1, maxPorMinuto);
    }

    /** Consume un mensaje; responde 429 si la IP agotó su cupo del minuto. */
    public void consumir(String ip) {
        Bucket bucket = porIp.get(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(maxPorMinuto)
                        .refillGreedy(maxPorMinuto, Duration.ofMinutes(1))
                        .build())
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Has enviado demasiados mensajes. Espera un momento e inténtalo de nuevo.");
        }
    }
}
