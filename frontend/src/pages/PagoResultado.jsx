import { useState, useEffect } from "react";
import { getToken } from "../services/authService";
import { verificarPago } from "../services/solicitudService";
import "../css/solicitudes.css";

// Pagos del marketplace (el backend envía "concepto"); las compras no lo traen.
const TEXTO_MARKETPLACE = {
  DIAGNOSTICO: "Recibimos el pago del diagnóstico. Tu cita de revisión quedó confirmada.",
  COTIZACION: "Recibimos el pago de la cotización. Pedimos los repuestos y el taller seguirá con el trabajo.",
  CATALOGO: "Recibimos el pago de tu compra. Avisamos a cada tienda; el estado de cada pedido está en “Mis compras”. Si elegiste retiro, el código te llegó por correo.",
};

export default function PagoResultado({ onIrSolicitudes, onPagoExitoso, onIrServicios, onCompraCatalogoPagada, onIrMisCompras }) {
  const token = getToken();
  const [transaccionId] = useState(() => new URLSearchParams(window.location.search).get("id") || "");
  const [estado, setEstado] = useState(transaccionId ? "VERIFICANDO" : "ERROR"); // VERIFICANDO | APPROVED | DECLINED | ERROR
  const [concepto, setConcepto] = useState(null); // null en compras normales
  const [aplicacion, setAplicacion] = useState(null); // { aplicado, reembolsoPendiente, estadoPago } del marketplace

  useEffect(() => {
    if (!transaccionId) return;
    let cancelado = false;
    const verificar = async () => {
      try {
        const res = await verificarPago(token, transaccionId);
        if (cancelado) return;
        // Solo informativo: el backend ya verificó y aplicó el pago con Wompi
        if (res.concepto) {
          setConcepto(res.concepto);
          setAplicacion({ aplicado: res.aplicado, reembolsoPendiente: res.reembolsoPendiente, estadoPago: res.estadoPago });
        }
        setEstado(res.estado || "ERROR");
        if (res.estado === "APPROVED" && !res.concepto) {
          onPagoExitoso && onPagoExitoso();
        }
        // Compra del catálogo aplicada: se vacían del carrito solo los productos que se pagaron
        if (res.estado === "APPROVED" && res.concepto === "CATALOGO" && res.aplicado !== false) {
          onCompraCatalogoPagada && onCompraCatalogoPagada();
        }
      } catch (e) {
        console.error(e);
        if (!cancelado) setEstado("ERROR");
      }
    };
    verificar();
    return () => { cancelado = true; };
  }, [transaccionId, token]);

  const irAMisSolicitudes = () => {
    if (window.history?.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    onIrSolicitudes && onIrSolicitudes();
  };

  const irAMisServicios = () => {
    if (window.history?.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    onIrServicios && onIrServicios();
  };

  const irAMisCompras = () => {
    if (window.history?.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    onIrMisCompras && onIrMisCompras();
  };

  // ─── Pago del marketplace (diagnóstico, cotización o compra del catálogo) ───
  if (concepto && estado !== "VERIFICANDO") {
    return (
      <div className="sq-root">
        <div className="sq-header">
          <h1 className="sq-title">Resultado del pago</h1>
          <p className="sq-subtitle">Confirmación de tu pago en línea.</p>
        </div>
        <div className="sq-empty" style={{ padding: "48px 24px", textAlign: "center" }}>
          {estado === "APPROVED" && aplicacion?.aplicado === false ? (
            <>
              <div style={{ fontSize: "56px", marginBottom: "8px" }}>⚠️</div>
              <h2 style={{ margin: "0 0 8px", color: "#b45309" }}>
                {aplicacion.reembolsoPendiente ? "Recibimos tu pago, pero no se pudo aplicar" : "Estamos confirmando tu pago"}
              </h2>
              <p style={{ margin: "0 0 20px", color: "#374151" }}>
                {aplicacion.reembolsoPendiente
                  ? "Te enviamos un correo con el motivo. DriveMaster te devolverá el dinero."
                  : concepto === "CATALOGO"
                    ? "Revisa el estado de tu compra en unos segundos en “Mis compras”."
                    : "Revisa el estado de tu solicitud en unos segundos en “Mis servicios”."}
              </p>
            </>
          ) : estado === "APPROVED" ? (
            <>
              <div style={{ fontSize: "56px", marginBottom: "8px" }}>✅</div>
              <h2 style={{ margin: "0 0 8px", color: "#065f46" }}>¡Pago exitoso!</h2>
              <p style={{ margin: "0 0 4px", color: "#374151" }}>{TEXTO_MARKETPLACE[concepto]}</p>
              <p style={{ margin: "0 0 4px", color: "#374151" }}>Te enviamos el comprobante de pago por correo.</p>
              <p style={{ margin: "0 0 20px", fontSize: "13px", color: "#6b7280" }}>Transacción: {transaccionId}</p>
            </>
          ) : (
            <>
              <div style={{ fontSize: "56px", marginBottom: "8px" }}>❌</div>
              <h2 style={{ margin: "0 0 8px", color: "#b91c1c" }}>Pago no aprobado</h2>
              <p style={{ margin: "0 0 20px", color: "#374151" }}>
                No se hizo ningún cobro. {concepto === "CATALOGO"
                  ? "Tu carrito sigue guardado: puedes intentarlo de nuevo."
                  : "Puedes intentarlo de nuevo desde “Mis servicios”."}
              </p>
            </>
          )}
          {concepto === "CATALOGO" ? (
            <button className="sq-btn sq-btn-approve sq-btn--md" onClick={irAMisCompras}>
              Ver mis compras
            </button>
          ) : (
            <button className="sq-btn sq-btn-approve sq-btn--md" onClick={irAMisServicios}>
              Ver mis servicios
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="sq-root">
      <div className="sq-header">
        <h1 className="sq-title">Resultado del pago</h1>
        <p className="sq-subtitle">Confirmación de tu pago en línea.</p>
      </div>

      <div className="sq-empty" style={{ padding: "48px 24px", textAlign: "center" }}>
        {estado === "VERIFICANDO" && (
          <>
            <div className="checkout-spinner" style={{ margin: "0 auto 16px" }} />
            <p>Verificando tu pago...</p>
          </>
        )}

        {estado === "APPROVED" && (
          <>
            <div style={{ fontSize: "56px", marginBottom: "8px" }}>✅</div>
            <h2 style={{ margin: "0 0 8px", color: "#065f46" }}>¡Pago exitoso!</h2>
            <p style={{ margin: "0 0 4px", color: "#374151" }}>Tu pedido fue confirmado y ya está en proceso.</p>
            <p style={{ margin: "0 0 20px", fontSize: "13px", color: "#6b7280" }}>
              Transacción: {transaccionId}
            </p>
          </>
        )}

        {estado === "DECLINED" && (
          <>
            <div style={{ fontSize: "56px", marginBottom: "8px" }}>❌</div>
            <h2 style={{ margin: "0 0 8px", color: "#b91c1c" }}>Pago rechazado</h2>
            <p style={{ margin: "0 0 20px", color: "#374151" }}>Tu pago no fue aprobado. Intenta nuevamente.</p>
          </>
        )}

        {estado === "ERROR" && (
          <>
            <div style={{ fontSize: "56px", marginBottom: "8px" }}>⚠️</div>
            <h2 style={{ margin: "0 0 8px", color: "#b45309" }}>No se pudo verificar el pago</h2>
            <p style={{ margin: "0 0 20px", color: "#374151" }}>
              No encontramos una transacción para verificar. Si realizaste el pago, pronto quedará confirmado.
            </p>
          </>
        )}

        {estado !== "VERIFICANDO" && (
          <button className="sq-btn sq-btn-approve sq-btn--md" onClick={irAMisSolicitudes}>
            Ver mis solicitudes
          </button>
        )}
      </div>
    </div>
  );
}
