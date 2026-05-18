import { useState, useEffect, useRef, useCallback } from "react";
import { getToken } from "../services/authService";
import "../css/notifications.css";

const API_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

function timeAgo(iso) {
  if (!iso) return "";
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "Ahora";
  if (mins < 60) return `Hace ${mins} min`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `Hace ${hrs} h`;
  const days = Math.floor(hrs / 24);
  return `Hace ${days} día${days > 1 ? "s" : ""}`;
}

function authHeaders(token) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

export default function NotificationDropdown({ onClose, onNavigate }) {
  const ref = useRef(null);
  const token = getToken();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchNotifications = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const items = [];

      // Pending solicitudes
      try {
        const sols = await fetch(`${API_BASE}/solicitudes`, { headers: authHeaders(token) }).then(r => r.json());
        const pending = (sols || []).filter(s => s.estado === "PENDIENTE_PAGO" || s.estado === "PAGO_VERIFICADO");
        pending.forEach(s => {
          items.push({
            id: `sol-${s.id}`,
            type: "solicitud",
            label: "Nueva solicitud pendiente",
            detail: s.clienteNombre
              ? `${s.clienteNombre} realizó un pedido`
              : "Solicitud de encargo pendiente",
            time: s.fechaCreacion,
            link: "solicitudes",
            linkId: s.id,
            color: "orange",
          });
        });
      } catch { /* ignore */ }

      // Low stock
      try {
        const prods = await fetch(`${API_BASE}/productos`, { headers: authHeaders(token) }).then(r => r.json());
        const lowStock = (prods || []).filter(p => p.stockActual != null && p.stockMinimo != null && p.activo !== false && p.stockActual <= p.stockMinimo);
        lowStock.forEach(p => {
          const isOut = p.stockActual === 0;
          items.push({
            id: `stock-${p.id}`,
            type: "stock",
            label: isOut ? "Producto agotado" : "Stock bajo",
            detail: isOut
              ? `${p.nombre} — Sin stock`
              : `${p.nombre} (${p.stockActual} restantes)`,
            time: null,
            link: "productos",
            linkId: p.id,
            color: isOut ? "red" : "orange",
          });
        });
      } catch { /* ignore */ }

      // Recent sales from dashboard
      try {
        const d = await fetch(`${API_BASE}/reportes/dashboard`, { headers: authHeaders(token) }).then(r => r.json());
        const totalHoy = d.totalVentasDia || 0;
        if (totalHoy > 0) {
          items.push({
            id: "sales-today",
            type: "venta",
            label: "Ventas del día",
            detail: `${totalHoy > 1 ? `${totalHoy} ventas registradas` : "Nueva venta registrada"}`,
            time: new Date().toISOString(),
            link: "ventas",
            linkId: null,
            color: "green",
          });
        }
      } catch { /* ignore */ }

      items.sort((a, b) => {
        if (!a.time && !b.time) return 0;
        if (!a.time) return 1;
        if (!b.time) return -1;
        return new Date(b.time) - new Date(a.time);
      });

      setNotifications(items.slice(0, 20));
    } catch { /* ignore */ }
    setLoading(false);
  }, [token]);

  useEffect(() => {
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 30000);
    return () => clearInterval(interval);
  }, [fetchNotifications]);

  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  const groups = [
    { key: "solicitud", label: "Solicitudes" },
    { key: "stock", label: "Inventario" },
    { key: "venta", label: "Ventas" },
    { key: "info", label: "Información" },
  ];

  const byGroup = {};
  notifications.forEach((n) => {
    if (!byGroup[n.type]) byGroup[n.type] = [];
    byGroup[n.type].push(n);
  });

  const groupedNotifs = groups.filter((g) => (byGroup[g.key] || []).length > 0);

  return (
    <div className="nt-dropdown" ref={ref}>
      <div className="nt-header">
        <span className="nt-title">Notificaciones</span>
        <span className="nt-count">{notifications.length}</span>
      </div>

      {loading && notifications.length === 0 ? (
        <div className="nt-empty">Cargando...</div>
      ) : notifications.length === 0 ? (
        <div className="nt-empty">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/>
          </svg>
          <p>No hay notificaciones</p>
        </div>
      ) : (
        <div className="nt-scroll">
          {groupedNotifs.map((g) => (
            <div key={g.key} className="nt-group">
              <div className="nt-group-label">{g.label}</div>
              {byGroup[g.key].map((n) => (
                <button
                  key={n.id}
                  className={`nt-item nt-item--${n.color}`}
                  onClick={() => { onClose(); onNavigate?.(n.link, n.linkId); }}
                >
                  <span className="nt-dot" />
                  <div className="nt-body">
                    <span className="nt-item-label">{n.label}</span>
                    <span className="nt-item-detail">{n.detail}</span>
                  </div>
                  {n.time && <span className="nt-time">{timeAgo(n.time)}</span>}
                </button>
              ))}
            </div>
          ))}
        </div>
      )}

      {notifications.length > 0 && (
        <div className="nt-footer">
          <span className="nt-update" onClick={fetchNotifications}>
            ↻ Actualizar
          </span>
        </div>
      )}
    </div>
  );
}
