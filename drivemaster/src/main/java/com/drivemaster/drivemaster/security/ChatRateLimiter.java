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
 * Límites del chat: cada mensaje dispara llamadas de pago al modelo (OpenRouter), así que
 * se acota por IP (por minuto y por día) y en total por día. El tope global es el que fija
 * el gasto máximo diario aunque lleguen muchas IP distintas.
 * En memoria (como LimiteIntentos): suficiente para una instancia.
 */
@Component
public class ChatRateLimiter {

    private final int maxPorMinuto;
    private final int maxPorDiaIp;
    private final Bucket global;

    private final Cache<String, Bucket> porIp = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofDays(1))
            .maximumSize(100_000)
            .build();

    public ChatRateLimiter(@Value("${n8n.chat.rate.max-por-minuto:6}") int maxPorMinuto,
                           @Value("${n8n.chat.rate.max-por-dia-ip:60}") int maxPorDiaIp,
                           @Value("${n8n.chat.rate.max-por-dia-global:400}") int maxPorDiaGlobal) {
        this.maxPorMinuto = Math.max(1, maxPorMinuto);
        this.maxPorDiaIp = Math.max(1, maxPorDiaIp);
        this.global = Bucket.builder().addLimit(porDia(Math.max(1, maxPorDiaGlobal))).build();
    }

    /** Consume un mensaje; responde 429 si la IP o el chat en total agotaron su cupo. */
    public void consumir(String ip) {
        Bucket bucket = porIp.get(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(maxPorMinuto)
                        .refillGreedy(maxPorMinuto, Duration.ofMinutes(1))
                        .build())
                .addLimit(porDia(maxPorDiaIp))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Has enviado demasiados mensajes. Espera un momento e inténtalo de nuevo.");
        }
        if (!global.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "El asistente alcanzó su límite de consultas por hoy. Puedes buscar tus repuestos en el catálogo o volver mañana.");
        }
    }

    private static Bandwidth porDia(int max) {
        return Bandwidth.builder()
                .capacity(max)
                .refillGreedy(max, Duration.ofDays(1))
                .build();
    }
}
