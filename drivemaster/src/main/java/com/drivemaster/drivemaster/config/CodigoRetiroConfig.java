package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.util.CodigoRetiro;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Carga la clave secreta con la que se guardan los códigos de retiro (RETIRO_CODIGO_SECRET) y avisa al arrancar
 * si falta o es débil. Nunca escribe la clave.
 */
@Component
public class CodigoRetiroConfig {

    static final int LONGITUD_MINIMA = 32;
    private static final Logger log = LoggerFactory.getLogger(CodigoRetiroConfig.class);

    private final String clave;

    public CodigoRetiroConfig(@Value("${retiro.codigo.secret:}") String clave) {
        this.clave = clave;
    }

    @PostConstruct
    void configurar() {
        CodigoRetiro.configurar(clave);
        if (!CodigoRetiro.configurado()) {
            log.warn("Falta RETIRO_CODIGO_SECRET: los códigos de retiro nuevos se guardan con SHA-256 simple, que es más fácil de adivinar "
                    + "si se filtra la base. Define una clave larga y aleatoria (mín. {} caracteres).", LONGITUD_MINIMA);
        } else if (clave.trim().length() < LONGITUD_MINIMA) {
            log.warn("RETIRO_CODIGO_SECRET es corta (menos de {} caracteres): usa una clave larga y aleatoria.", LONGITUD_MINIMA);
        }
    }
}
