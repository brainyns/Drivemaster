package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.util.Dinero;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Dispersión real mientras no exista Wompi Pagos a Terceros: el ADMIN transfiere por
 * el banco y anota aquí el número de transferencia (obligatorio). Nunca es automática:
 * el evento deja la línea POR_LIQUIDAR y la paga una persona.
 */
public class ManualDispersionService implements DispersionService {

    public static final String MODO = "MANUAL";
    private static final Pattern NUMERO_VALIDO = Pattern.compile("[A-Za-z0-9._/\\-]{3,80}");

    @Override
    public String modo() {
        return MODO;
    }

    @Override
    public boolean automatica() {
        return false;
    }

    @Override
    public void validar(SolicitudDispersion solicitud) {
        String numero = solicitud.numeroTransferencia() != null ? solicitud.numeroTransferencia().trim() : "";
        if (numero.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Escribe el número de transferencia con el que pagaste");
        }
        if (!NUMERO_VALIDO.matcher(numero).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El número de transferencia solo puede llevar letras, números y . _ / - (3 a 80 caracteres)");
        }
    }

    @Override
    public ResultadoDispersion liquidar(RepartoPago reparto, SolicitudDispersion solicitud) {
        validar(solicitud);
        BigDecimal monto = Dinero.pesos(reparto.getMonto());
        if (monto.signum() <= 0) {
            throw new DispersionException("El monto a liquidar debe ser mayor que cero");
        }
        return new ResultadoDispersion(MODO, solicitud.numeroTransferencia().trim(), monto);
    }
}
