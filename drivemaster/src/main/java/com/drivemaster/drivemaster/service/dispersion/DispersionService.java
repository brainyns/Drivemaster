package com.drivemaster.drivemaster.service.dispersion;

import com.drivemaster.drivemaster.model.mysql.RepartoPago;

/**
 * Paga a un taller o tienda lo que le corresponde de un pago. Implementaciones:
 * {@link SimulatedDispersionService} (desarrollo) y {@link ManualDispersionService}
 * (el ADMIN transfiere por el banco). Una futura WompiPayoutService (Pagos a
 * Terceros de Wompi) se agregaría aquí y se elegiría en {@link DispersionConfig}
 * con DISPERSION_MODO; hoy NO existe.
 *
 * La implementación solo ejecuta el pago: el estado del reparto (tomarlo, marcarlo
 * LIQUIDADO o FALLIDO) lo maneja LiquidacionService con escrituras condicionales.
 */
public interface DispersionService {

    /** SIMULADO o MANUAL; se guarda en cada línea liquidada. */
    String modo();

    /** true: el evento que habilita el pago liquida solo; false: la paga el ADMIN. */
    boolean automatica();

    /** Valida los datos antes de tomar la línea; lanza ResponseStatusException 400 si faltan. */
    default void validar(SolicitudDispersion solicitud) {
    }

    /** Ejecuta el pago de la línea ya tomada (PROCESANDO). Lanza DispersionException si falla. */
    ResultadoDispersion liquidar(RepartoPago reparto, SolicitudDispersion solicitud);
}
