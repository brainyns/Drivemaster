import { useEffect, useState } from "react";
import { obtenerCompra, descargarPdfCompra } from "../services/compraService";
import "../css/compras.css";

const IcArrow = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>;
const IcPrint = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14"><polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 01-2-2v-5a2 2 0 012-2h16a2 2 0 012 2v5a2 2 0 01-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>;
const IcDown  = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>;
const IcCheck = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14"><polyline points="20 6 9 17 4 12"/></svg>;
const IcTruck = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14"><rect x="1" y="3" width="15" height="13"/><path d="M16 8h4l3 4v4h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>;

function formatFecha(f) {
  if (!f) return "—";
  return new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }).toUpperCase();
}

function CompraDetalle({ id, onVolver, token }) {
  const [compra, setCompra]         = useState(null);
  const [loading, setLoading]       = useState(true);
  const [error, setError]           = useState(null);
  const [descargando, setDescargando] = useState(false);

  useEffect(() => {
    obtenerCompra(id, token)
      .then(setCompra)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, [id, token]);

  const handlePdf = async () => {
    setDescargando(true);
    try {
      await descargarPdfCompra(id, token);
    } catch (e) {
      alert("Error al generar PDF: " + e.message);
    } finally {
      setDescargando(false);
    }
  };

  const handleImprimir = async () => {
    setDescargando(true);
    try {
      await descargarPdfCompra(id, token);
      setTimeout(() => window.print(), 800);
    } catch (e) {
      alert("Error al imprimir: " + e.message);
    } finally {
      setDescargando(false);
    }
  };

  if (loading) return <div className="cp-root"><div className="cp-loading"><div className="cp-spinner"/>Cargando...</div></div>;
  if (error)   return <div className="cp-root"><p className="cp-error">{error}</p></div>;
  if (!compra) return null;

  const subtotal = compra.productos?.reduce((s, p) => s + (p.subtotal || 0), 0) || compra.total || 0;
  const poId     = `PO-${String(compra.id).slice(-5).toUpperCase()}`;
  const fecha    = formatFecha(compra.fecha);

  return (
    <div className="cp-root">

      {/* Header */}
      <div className="cp-header">
        <div>
          <button className="cp-back-link" onClick={onVolver}>{IcArrow} Volver a Compras</button>
          <h1 className="cp-h1" style={{ marginTop: ".4rem" }}>Purchase Registry</h1>
          <p className="cp-breadcrumb">Registry ID: {poId}</p>
        </div>
        <button
          className="cp-btn-new"
          onClick={onVolver}
          style={{ background: "var(--surface2)", border: "1px solid var(--border2)", color: "var(--text)" }}
        >
          + Nueva Compra
        </button>
      </div>

      <div className="cp-detalle-layout">

        {/* Factura principal */}
        <div className="cp-detalle-main">

          <div className="cp-invoice-head">
            <div>
              <span className="cp-invoice-tag">OFFICIAL INVOICE</span>
              <h2 className="cp-invoice-title">FACTURA #{poId}</h2>
            </div>
            <div style={{ textAlign: "right" }}>
              <p style={{ fontSize: ".72rem", color: "var(--muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: ".07em" }}>Issue Date</p>
              <p style={{ fontSize: "1.1rem", fontWeight: 800, letterSpacing: "-.02em" }}>{fecha}</p>
            </div>
          </div>

          <div className="cp-invoice-parties">
            <div>
              <p className="cp-invoice-party-label">Supplier Information</p>
              <p className="cp-invoice-party-name" style={{ color: "var(--primary)" }}>
                {compra.proveedorNombre || `Proveedor #${String(compra.proveedorId).slice(-6)}`}
              </p>
              <p className="cp-invoice-party-sub">{compra.proveedorContacto || "—"}</p>
              {compra.proveedorEmail && <p className="cp-invoice-party-sub">{compra.proveedorEmail}</p>}
            </div>
            <div>
              <p className="cp-invoice-party-label">Ship To</p>
              <p className="cp-invoice-party-name">DriveMaster</p>
              <p className="cp-invoice-party-sub">Almacén Central</p>
              <p className="cp-invoice-party-sub">Colombia</p>
            </div>
          </div>

          <div className="cp-invoice-section">
            <div className="cp-invoice-section-header">
              <h3 className="cp-invoice-section-title">Purchase Order Items</h3>
              <span className="cp-items-badge">{`ITEMS_COUNT: ${String(compra.productos?.length || 0).padStart(2, "0")}`}</span>
            </div>
            <table className="cp-invoice-table">
              <thead>
                <tr>
                  <th></th>
                  <th>Product Name</th>
                  <th style={{ textAlign: "center" }}>Quantity</th>
                  <th style={{ textAlign: "right" }}>Unit Cost</th>
                  <th style={{ textAlign: "right" }}>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {compra.productos?.map((p, i) => (
                  <tr key={i} className="cp-invoice-row">
                    <td>
                      <div className="cp-invoice-prod-ico">
                        {(p.nombre || "P")[0].toUpperCase()}
                      </div>
                    </td>
                    <td>
                      <p className="cp-invoice-prod-name">{p.nombre}</p>
                      <p className="cp-invoice-prod-sku">SKU: {p.productoId?.slice(-8).toUpperCase() || "—"}</p>
                    </td>
                    <td style={{ textAlign: "center", fontWeight: 800, fontSize: "1rem" }}>
                      {String(p.cantidad).padStart(2, "0")}
                    </td>
                    <td style={{ textAlign: "right", color: "var(--muted)", fontSize: ".85rem" }}>
                      ${(p.costo || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}
                    </td>
                    <td style={{ textAlign: "right", fontWeight: 800, color: "var(--primary)", fontSize: ".9rem" }}>
                      ${(p.subtotal || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="cp-invoice-totals">
            <div className="cp-invoice-total-row">
              <span>Subtotal</span>
              <span>${subtotal.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</span>
            </div>
            <div className="cp-invoice-total-final">
              <span>TOTAL DUE</span>
              <span>${(compra.total || subtotal).toLocaleString("es-CO", { minimumFractionDigits: 2 })}</span>
            </div>
            <button
              className="cp-btn-outline"
              style={{ marginTop: "1rem", width: "100%", justifyContent: "center" }}
              onClick={handlePdf}
              disabled={descargando}
            >
              {IcDown} {descargando ? "Generando..." : "Download Transaction Log"}
            </button>
          </div>
        </div>

        {/* Panel lateral */}
        <div className="cp-detalle-side">

          <div className="cp-side-panel">
            <p className="cp-side-panel-title">Registry Actions</p>
            {[
              { ico: IcPrint, label: "Print Invoice",                          onClick: handleImprimir },
              { ico: IcDown,  label: descargando ? "Generando..." : "Download PDF", onClick: handlePdf },
              { ico: IcTruck, label: "Email Supplier",                          onClick: () => alert("El PDF se envía automáticamente al registrar la compra.") },
            ].map((a, i) => (
              <button key={i} className="cp-action-row" onClick={a.onClick} disabled={descargando}>
                <span className="cp-action-ico">{a.ico}</span>
                <span>{a.label}</span>
                <span className="cp-action-arrow">›</span>
              </button>
            ))}
          </div>

          <div className="cp-side-panel cp-side-panel--verified">
            <div className="cp-verified-head">
              <span className="cp-verified-dot"/>{IcCheck}
              <span className="cp-verified-label">VERIFIED PURCHASE</span>
            </div>
            <p className="cp-verified-desc">
              Esta compra ha sido reconciliada con el inventario y los controles de calidad han sido aplicados.
            </p>
          </div>

          <div className="cp-side-panel">
            <p className="cp-side-panel-title">Información General</p>
            <div className="cp-info-rows">
              {[
                { label: "Purchase ID", val: poId },
                { label: "Fecha",       val: fecha },
                { label: "Estado",      val: compra.estado || "RECIBIDA" },
                { label: "Total ítems", val: compra.productos?.length || 0 },
                { label: "Total",       val: `$${(compra.total || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}` },
              ].map((r, i) => (
                <div key={i} className="cp-info-row">
                  <span className="cp-info-label">{r.label}</span>
                  <span className="cp-info-val">{r.val}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default CompraDetalle;