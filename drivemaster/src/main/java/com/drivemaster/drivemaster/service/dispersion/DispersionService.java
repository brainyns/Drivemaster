package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;

/**
 * Paga a un taller o tienda lo que le corresponde de un pago. Implementaciones:
 * {@link SimulatedDispersionService} (desarrollo), {@link ManualDispersionService}
 * (el ADMIN transfiere por el banco) y {@link WompiPayoutService} (Pagos a Terceros de
 * Wompi; el ADMIN aprueba cada pago). Se elige en {@link DispersionConfig} con DISPERSION_MODO.
 *
 * La implementación solo ejecuta el pago: el estado del reparto (tomarlo, marcarlo
 * LIQUIDADO o FALLIDO) lo maneja LiquidacionService con escrituras condicionales.
 */
public interface DispersionService {

    /** SIMULADO, MANUAL o WOMPI; se guarda en cada línea liquidada. */
    String modo();

    /** true: el evento que habilita el pago liquida solo; false: la paga el ADMIN. */
    boolean automatica();

    /** false si los pagos no mueven dinero (simulación o sandbox): la pantalla del ADMIN lo avisa. */
    default boolean dineroReal() {
        return true;
    }

    /** Valida los datos antes de tomar la línea; lanza ResponseStatusException 400 si faltan. */
    default void validar(SolicitudDispersion solicitud) {
    }

    /** Ejecuta el pago de la línea ya tomada (PROCESANDO). Lanza DispersionException si falla. */
    ResultadoDispersion liquidar(RepartoPago reparto, SolicitudDispersion solicitud);

    /** Qué pasó con un pago enviado a Wompi; referencia: la guardada, con su id si lo tiene. Quien no puede consultarlo no sabe. */
    default PagoAnterior consultarPago(String referencia) {
        return PagoAnterior.SIN_SABER;
    }
}
