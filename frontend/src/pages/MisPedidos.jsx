import { useState, useEffect } from "react";
import { getToken } from "../services/authService";
import "../css/mis-pedidos.css";

const API = "http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api";

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

function headers(token) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

const COLORES = {
  PAGADA: "#3b82f6",
  ENTREGADO: "#22c55e",
  ANULADA: "#6b7280",
  APROBADO: "#22c55e",
};

export default function MisPedidos({ onVolver }) {
  const token = getToken();
  const [ventas, setVentas] = useState([]);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    (async () => {
      setCargando(true);
      try {
        const res = await fetch(`${API}/ventas/mis-ventas`, { headers: headers(token) });
        if (res.ok) setVentas(await res.json());
      } catch (e) {
        console.error(e);
      } finally {
        setCargando(false);
      }
    })();
  }, []);

  return (
    <div className="mis-pedidos-page">
      <div className="mis-pedidos-header">
        <h1>Historial de compras</h1>
        <button className="mis-pedidos-volver" onClick={onVolver}>← Volver al catálogo</button>
      </div>

      {cargando ? (
        <div className="mis-pedidos-loading">Cargando...</div>
      ) : ventas.length === 0 ? (
        <div className="mis-pedidos-empty">No tienes compras realizadas</div>
      ) : (
        <div className="mis-pedidos-lista">
          {ventas.map((v) => (
            <div key={v.id} className="mis-pedidos-card">
              <div className="mis-pedidos-card-top">
                <span className="mis-pedidos-card-id">#{v.id?.slice(-8).toUpperCase() || "N/A"}</span>
                <span className="mis-pedidos-card-estado" style={{
                  background: COLORES[v.estado] || "#f59e0b"
                }}>
                  {v.estado || "Pendiente"}
                </span>
              </div>
              <div className="mis-pedidos-card-body">
                <div className="mis-pedidos-card-productos">
                  {v.productos?.map((p, i) => (
                    <div key={i} className="mis-pedidos-card-producto">
                      <span>{p.nombre} x{p.cantidad}</span>
                      <span>{formatPrecio(p.subtotal)}</span>
                    </div>
                  ))}
                </div>
                <div className="mis-pedidos-card-total">
                  <span>Total</span>
                  <span>{formatPrecio(v.total)}</span>
                </div>
                <div className="mis-pedidos-card-info">
                  <span>Método: {v.pagos?.[0]?.metodo || "—"}</span>
                  <span>{v.fecha ? new Date(v.fecha).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : ""}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
