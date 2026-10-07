import { useState } from "react";
import { verificarPago } from "../../services/solicitudService";
import { simularPago } from "../../services/pagoMarketplaceService";

const ESTADO_WOMPI = {
  APPROVED: "Pago aprobado.",
  DECLINED: "Wompi rechazó el pago. No se hizo ningún cobro.",
  VOIDED: "El pago fue anulado.",
  ERROR: "El pago terminó con error.",
  PENDING: "El pago sigue pendiente en Wompi. Intenta verificar en unos segundos.",
  NO_ENCONTRADO: "No encontramos esa transacción en Wompi. Revisa el ID.",
};

/**
 * Desarrollo local: Wompi no puede devolver al cliente a localhost, así que el
 * pago se abre en otra pestaña y luego se verifica aquí con el ID de la
 * transacción. El backend consulta la API de Wompi; el frontend no marca nada
 * como pagado.
 *
 * Este componente solo se monta si el backend respondió verificacionManual = true, y
 * eso solo ocurre con PAGO_VERIFICACION_MANUAL_ENABLED, llaves pub_test_ y sin
 * redirect-url. En producción el backend nunca lo ofrece; aquí además se descarta
 * cualquier respuesta que no lo traiga explícito.
 */
export default function PagoLocal({ token, pago, onVerificado, onCerrar }) {
  const [transaccion, setTransaccion] = useState("");
  const [mensaje, setMensaje] = useState(null);
  const [error, setError] = useState(null);
  const [procesando, setProcesando] = useState(false);

  const ejecutar = async (accion) => {
    setProcesando(true);
    setError(null);
    setMensaje(null);
    try {
      const res = await accion();
      // Wompi puede aprobar y DriveMaster no aplicar el pago (la solicitud cambió, faltó stock…)
      if (res?.estado === "APPROVED" && res.aplicado === false) {
        setMensaje(res.reembolsoPendiente
          ? "Wompi aprobó el pago, pero no se pudo aplicar a esta solicitud. Te enviamos un correo y DriveMaster te devolverá el dinero."
          : "Wompi aprobó el pago y lo estamos confirmando. Verifica de nuevo en unos segundos.");
        if (res.reembolsoPendiente) await onVerificado();
        return;
      }
      setMensaje(ESTADO_WOMPI[res?.estado] || `Estado: ${res?.estado || "desconocido"}`);
      if (res?.estado === "APPROVED") await onVerificado();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const verificar = () => {
    const id = transaccion.trim();
    if (!id) return setError("Pega el ID de la transacción que muestra Wompi al terminar.");
    ejecutar(() => verificarPago(token, id));
  };

  // Sin verificacion manual explicita del backend no se muestra nada (defensa en profundidad)
  if (!pago?.verificacionManual) return null;

  return (
    <div className="ss-aviso ct-pago-local" role="region" aria-label="Pago en modo desarrollo">
      <strong>Modo desarrollo: Wompi no te devolverá a la app automáticamente.</strong>
      <ol className="ct-pago-local__pasos">
        <li>
          <a href={pago.checkoutUrl} target="_blank" rel="noopener noreferrer">Abre el pago en Wompi</a> (nueva pestaña) y págalo.
        </li>
        <li>Copia el ID de la transacción que muestra Wompi al terminar y pégalo aquí.</li>
      </ol>
      <div className="ct-pago-local__fila">
        <input className="ss-input" placeholder="ID de la transacción (ej. 12345-1700000000-12345)"
          aria-label="ID de la transacción de Wompi" value={transaccion} onChange={(e) => setTransaccion(e.target.value)} />
        <button className="sv-btn" onClick={verificar} disabled={procesando}>
          {procesando ? "Verificando..." : "Ya pagué, verificar mi pago"}
        </button>
      </div>
      {pago.pagoSimulado && (
        <div className="ct-pago-local__fila">
          <button className="sv-btn-outline" onClick={() => ejecutar(() => simularPago(token, pago.referencia))} disabled={procesando}>
            Simular pago aprobado (solo desarrollo)
          </button>
        </div>
      )}
      {mensaje && <p className="ss-meta" role="status">{mensaje}</p>}
      {error && <p className="ss-error" role="alert">{error}</p>}
      {onCerrar && <button className="sv-btn-outline ct-pago-local__cerrar" onClick={onCerrar} disabled={procesando}>Cerrar</button>}
    </div>
  );
}
