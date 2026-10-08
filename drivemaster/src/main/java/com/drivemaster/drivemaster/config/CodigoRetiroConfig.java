package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.util.CodigoRetiro;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carga la clave secreta con la que se guardan los códigos de retiro (RETIRO_CODIGO_SECRET).
 *
 * En desarrollo (llaves de Wompi pub_test_) solo avisa si falta o es débil y sigue. Con cualquier otra llave
 * (producción) el backend NO arranca sin una clave de al menos 32 caracteres: un código de 40 bits guardado
 * sin clave del servidor se podría adivinar si se filtra la base. Nunca escribe la clave.
 */
@Component
public class CodigoRetiroConfig {

    static final int LONGITUD_MINIMA = 32;
    private static final Logger log = LoggerFactory.getLogger(CodigoRetiroConfig.class);

    private final String clave;
    private final String llaveWompi;

    public CodigoRetiroConfig(@Value("${retiro.codigo.secret:}") String clave,
                              @Value("${wompi.public_key:}") String llaveWompi) {
        this.clave = clave;
        this.llaveWompi = llaveWompi;
    }

    @PostConstruct
    void configurar() {
        boolean desarrollo = llaveWompi != null && llaveWompi.startsWith("pub_test_");
        boolean falta = clave == null || clave.isBlank();
        boolean corta = !falta && clave.trim().length() < LONGITUD_MINIMA;

        if (!desarrollo && (falta || corta)) {
            // Se deja sin configurar: el arranque se aborta antes de que se emita ningún código
            CodigoRetiro.configurar(null);
            throw new IllegalStateException("RETIRO_CODIGO_SECRET " + (falta ? "no está definida" : "es demasiado corta")
                    + ": con llaves de Wompi de producción es obligatoria y debe tener al menos " + LONGITUD_MINIMA
                    + " caracteres. Genérala con, por ejemplo, 'openssl rand -hex 32'.");
        }

        CodigoRetiro.configurar(clave);
        if (falta) {
            log.warn("Falta RETIRO_CODIGO_SECRET: los códigos de retiro nuevos se guardan con SHA-256 simple, que es más fácil de adivinar "
                    + "si se filtra la base. Solo se tolera en desarrollo (llaves pub_test_); define una clave larga y aleatoria "
                    + "(mín. {} caracteres).", LONGITUD_MINIMA);
        } else if (corta) {
            log.warn("RETIRO_CODIGO_SECRET es corta (menos de {} caracteres): usa una clave larga y aleatoria.", LONGITUD_MINIMA);
        }
    }
}
