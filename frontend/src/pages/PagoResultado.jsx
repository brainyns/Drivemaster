import { useState, useEffect } from "react";
import { getToken } from "../services/authService";
import { verificarPago } from "../services/solicitudService";
import "../css/solicitudes.css";

export default function PagoResultado({ onIrSolicitudes, onPagoExitoso }) {
  const token = getToken();
  const [estado, setEstado] = useState("VERIFICANDO"); // VERIFICANDO | APPROVED | DECLINED | ERROR
  const [transaccionId, setTransaccionId] = useState("");

  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get("id");
    if (!id) {
      setEstado("ERROR");
      return;
    }
    setTransaccionId(id);
    const verificar = async () => {
      try {
        const res = await verificarPago(token, id);
        setEstado(res.estado || "ERROR");
        if (res.estado === "APPROVED") {
          onPagoExitoso && onPagoExitoso();
        }
      } catch (e) {
        console.error(e);
        setEstado("ERROR");
      }
    };
    verificar();
  }, []);

  const irAMisSolicitudes = () => {
    if (window.history?.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    onIrSolicitudes && onIrSolicitudes();
  };

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
