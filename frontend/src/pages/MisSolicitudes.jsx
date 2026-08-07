import { useState, useEffect } from "react";
import { getToken } from "../services/authService";
import { crearPago, verificarPago, getWompiRedirectUrl } from "../services/solicitudService";
import "../css/solicitudes.css";

const ESTADOS = {
  PENDIENTE: "Pendiente",
  APROBADO: "Aprobado",
  RECHAZADO: "Rechazado",
  COMPLETADA: "Completada",
  PAGADO: "Pagado",
};

const ESTADOS_CLASS = {
  PENDIENTE: "sq-pending",
  APROBADO: "sq-approved",
  RECHAZADO: "sq-rejected",
  COMPLETADA: "sq-verified",
  PAGADO: "sq-approved",
};

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

const BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/solicitudes`;

export default function MisSolicitudes() {
  const token = getToken();
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);

  const cargar = async () => {
    setCargando(true);
    try {
      const res = await fetch(`${BASE}/mis-solicitudes`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) setSolicitudes(await res.json());
    } catch (e) {
      console.error(e);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => {
    cargar();

    const id = new URLSearchParams(window.location.search).get("id");
    if (id) {
      verificarPago(token, id)
        .then(() => cargar())
        .catch(() => cargar())
        .finally(() => {
          if (window.history?.replaceState) {
            window.history.replaceState(null, "", window.location.pathname);
          }
        });
    }
  }, []);

  const [pagando, setPagando] = useState(null);

  const handlePagar = async (solicitudId) => {
    setPagando(solicitudId);
    try {
      const redirectUrl = getWompiRedirectUrl();
      const data = await crearPago(token, solicitudId, redirectUrl);
      console.log("checkoutUrl:", data.checkoutUrl);
      window.location.href = data.checkoutUrl;
    } catch (e) {
      console.error(e);
      alert("Error al iniciar pago: " + e.message);
      setPagando(null);
    }
  };

  const filtradas = solicitudes.filter(s => s.estado !== "RECHAZADO");

  return (
    <div className="sq-root">
      <div className="sq-header">
        <h1 className="sq-title">Mis Solicitudes</h1>
        <p className="sq-subtitle">Consulta el estado de tus solicitudes de compra.</p>
      </div>

      <div className="sq-table-wrap">
        <div className="sq-table">
          <div className="sq-thead">
            <span>Productos</span>
            <span>Total</span>
            <span>Estado</span>
            <span>Fecha</span>
            <span>Acción</span>
          </div>

          {cargando ? (
            <div className="sq-empty"><p>Cargando...</p></div>
          ) : filtradas.length === 0 ? (
            <div className="sq-empty"><p>No tienes solicitudes</p></div>
          ) : (
            filtradas.map(s => (
              <div key={s.id} className="sq-row">
                <div className="sq-cell-prod">
                  <span className="sq-prod-count">{s.productos?.length || 0} producto{(s.productos?.length || 0) !== 1 ? "s" : ""}</span>
                </div>
                <div className="sq-cell-total">{formatPrecio(s.total)}</div>
                <div className="sq-cell-estado">
                  <span className={`sq-badge ${ESTADOS_CLASS[s.estado] || ""}`}>
                    <span className="sq-badge-dot" />
                    {ESTADOS[s.estado] || s.estado}
                  </span>
                </div>
                <div className="sq-cell-fecha">
                  {s.fechaCreacion ? new Date(s.fechaCreacion).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—"}
                </div>
                <div className="sq-cell-acciones">
                  {s.metodoPago === "WOMPI" && (s.estado === "PENDIENTE" || s.estado === "APROBADO") && (
                    <button
                      className="sq-btn sq-btn-cobrar"
                      onClick={() => handlePagar(s.id)}
                      disabled={pagando === s.id}
                    >
                      {pagando === s.id ? "Generando..." : s.estado === "APROBADO" ? "Pagar encargo" : "Pagar ahora"}
                    </button>
                  )}
                  {(s.estado === "PENDIENTE" || s.estado === "APROBADO") && s.metodoPago !== "WOMPI" && (
                    <span className="sq-badge sq-pending">En espera</span>
                  )}
                  {s.estado === "PAGADO" && (
                    <span className="sq-badge sq-badge-venta">Pagado</span>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
