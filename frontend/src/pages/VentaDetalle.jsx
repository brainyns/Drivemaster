import { useEffect, useState } from "react";
import { obtenerVenta, descargarPdfVenta } from "../services/ventaService";
import "../css/venta.css";

const METODO_ICO = {
  TARJETA:       "💳",
  EFECTIVO:      "💵",
  TRANSFERENCIA: "🏦",
  NEQUI:         "💰",
};

const ESTADO_CLASS = {
  COMPLETADA: "completada",
  PAGADA:     "pagada",
  PENDIENTE:  "pendiente",
  CANCELADA:  "cancelada",
  ANULADA:    "anulada",
};

function VentaDetalle({ id, onVolver, token }) {
  const [venta, setVenta]       = useState(null);
  const [loading, setLoading]   = useState(true);
  const [descargando, setDescargando] = useState(false);

  useEffect(() => {
    obtenerVenta(id, token)
      .then(setVenta)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [id, token]);

  const handleDescargarPdf = async () => {
    setDescargando(true);
    try {
      await descargarPdfVenta(id, token);
    } catch (e) {
      alert("Error al generar PDF: " + e.message);
    } finally {
      setDescargando(false);
    }
  };

  const handleImprimir = async () => {
    setDescargando(true);
    try {
      const res = await fetch(`http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/ventas/${id}/pdf`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error("Error al generar PDF");
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank");
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      alert("Error al imprimir: " + e.message);
    } finally {
      setDescargando(false);
    }
  };

  const handleExportarFactura = async () => {
    setDescargando(true);
    try {
      await descargarPdfVenta(id, token);
    } catch (e) {
      alert("Error al exportar: " + e.message);
    } finally {
      setDescargando(false);
    }
  };

  if (loading) return (
    <div className="vd-page" style={{ justifyContent: "center", alignItems: "center" }}>
      <p style={{ color: "var(--muted)", fontFamily: "DM Sans,sans-serif" }}>Cargando venta...</p>
    </div>
  );
  if (!venta) return (
    <div className="vd-page" style={{ justifyContent: "center", alignItems: "center" }}>
      <p style={{ color: "var(--red)", fontFamily: "DM Sans,sans-serif" }}>Venta no encontrada</p>
    </div>
  );

  const total     = parseFloat(venta.total || 0);
  const subtotal  = venta.productos?.reduce((a, p) => a + parseFloat(p.subtotal || 0), 0) || total;
  const impuesto  = total - subtotal;
  const estadoKey = venta.estado?.toUpperCase?.() || "";
  const fecha     = venta.fecha
    ? new Date(venta.fecha).toLocaleDateString("es-CO", { day: "2-digit", month: "numeric", year: "numeric" })
    : "—";

  return (
    <div className="vd-page">

  
      {/* Sub-header */}
      <div className="vd-subheader">
        <button className="vd-back" onClick={onVolver}>← Volver al historial</button>
        <div className="vd-subheader-actions">
          <button
            className="vt-btn-outline"
            onClick={handleImprimir}
            disabled={descargando}
          >
            🖨 Imprimir
          </button>
          <button
            className="vt-btn-primary"
            onClick={handleExportarFactura}
            disabled={descargando}
          >
            {descargando ? "Generando..." : "↓ Exportar factura"}
          </button>
        </div>
      </div>

      {/* Title */}
      <div className="vd-page-title">
        <h1>Detalle de Venta</h1>
      </div>

      {/* Body */}
      <div className="vd-body">

        {/* Columna izquierda */}
        <div className="vd-left">

          <div className="vd-panel relative">
            <div className="vd-resumen-ico">🧾</div>
            <p className="vd-panel-label">Resumen de Venta</p>
            <p className="vd-resumen-fecha-label">Fecha de Transacción</p>
            <p className="vd-resumen-fecha">{fecha}</p>
            <p className="vd-resumen-estado-label">Estado</p>
            <span className={`vt-badge ${ESTADO_CLASS[estadoKey] || "pendiente"}`}>
              {venta.estado || "—"}
            </span>
            <p className="vd-resumen-monto-label">Monto Total</p>
            <p className="vd-resumen-monto">${total.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</p>
          </div>

          <div className="vd-panel">
            <p className="vd-panel-label">Cliente</p>
            <div className="vd-client-row">
              <div className="vd-client-ava">👤</div>
              <div>
                <p className="vd-client-name">{venta.clienteNombre || `Cliente #${venta.clienteId}`}</p>
                <p className="vd-client-id">ID: {venta.clienteIdentificacion || venta.clienteId}</p>
              </div>
            </div>
            {venta.clienteCorreo && (
              <div className="vd-detail-row">
                <span className="vd-detail-label">Email</span>
                <span className="vd-detail-val">{venta.clienteCorreo}</span>
              </div>
            )}
            {venta.clienteTelefono && (
              <div className="vd-detail-row">
                <span className="vd-detail-label">Teléfono</span>
                <span className="vd-detail-val">{venta.clienteTelefono}</span>
              </div>
            )}
          </div>
        </div>

        {/* Columna derecha */}
        <div className="vd-right">

          <div className="vd-panel" style={{ padding: 0, overflow: "hidden" }}>
            <div style={{ padding: "1.2rem 1.25rem .75rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <p className="vd-panel-title">Productos y Servicios</p>
              <span style={{ fontSize: ".74rem", color: "var(--muted)" }}>
                {venta.productos?.length || 0} {venta.productos?.length === 1 ? "ítem" : "ítems"} en total
              </span>
            </div>
            <table className="vd-table">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th className="right">Cant.</th>
                  <th className="right">Precio Unit.</th>
                  <th className="right">Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {(venta.productos || []).map((p, i) => (
                  <tr key={i}>
                    <td>
                      <p className="vd-prod-name">{p.nombre}</p>
                      {p.categoria && <p className="vd-prod-cat">{p.categoria}</p>}
                    </td>
                    <td className="right">{p.cantidad}</td>
                    <td className="right">${parseFloat(p.precioUnitario || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}</td>
                    <td className="right vd-subtotal-orange">${parseFloat(p.subtotal || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="vd-totals">
              <div className="vd-totals-row">
                <span className="vd-totals-label">Subtotal Bruto</span>
                <span className="vd-totals-val">${subtotal.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</span>
              </div>
              <div className="vd-totals-row">
                <span className="vd-totals-label">Impuestos</span>
                <span className="vd-totals-val">${impuesto.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</span>
              </div>
              <div className="vd-totals-row final">
                <span className="vd-totals-label">Total Final</span>
                <span className="vd-totals-val">${total.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</span>
              </div>
            </div>
          </div>

          <div className="vd-panel">
            <div className="vd-pagos-header">
              <p className="vd-panel-title">Registro de Pagos</p>
              <span className="vd-pagos-count">{venta.pagos?.length || 0} registro(s)</span>
            </div>
            <table className="vd-table" style={{ marginBottom: ".5rem" }}>
              <thead>
                <tr>
                  <th>Método</th>
                  <th className="right">Monto</th>
                  <th className="right">Referencia</th>
                </tr>
              </thead>
            </table>
            {(venta.pagos || []).map((p, i) => (
              <div key={i} className="vd-pago-row">
                <div className="vd-pago-left">
                  <span className="vd-pago-ico">{METODO_ICO[p.metodo] || "💰"}</span>
                  <span className="vd-pago-metodo">{p.metodo}</span>
                </div>
                <span className="vd-pago-monto">${parseFloat(p.monto || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}</span>
                {p.referencia && <span className="vd-pago-ref">{p.referencia}</span>}
              </div>
            ))}
            <div className="vd-footer" style={{ paddingTop: "1rem", paddingLeft: 0, paddingRight: 0, marginTop: "1rem" }}>
              <span>Creado por: <strong>{venta.creadoPor || "Admin"}</strong> &nbsp; Terminal: <strong>{venta.terminal || "POS-01"}</strong></span>
              <span className="vd-footer-secure">🔒 Transacción Encriptada</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default VentaDetalle;