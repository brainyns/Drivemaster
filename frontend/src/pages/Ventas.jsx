import { useEffect, useState } from "react";
import { listarVentas, anularVenta } from "../services/ventaService";
import "../css/venta.css";

const POR_PAG = 10;

const METODO_ICO = {
  TARJETA:       { ico: "💳", label: "Tarjeta" },
  EFECTIVO:      { ico: "💵", label: "Efectivo" },
  TRANSFERENCIA: { ico: "🏦", label: "Transferencia" },
};

const ESTADO_CLASS = {
  COMPLETADA: "completada",
  PAGADA:     "pagada",
  PENDIENTE:  "pendiente",
  CANCELADA:  "cancelada",
  ANULADA:    "anulada",
};

function formatFecha(f) {
  if (!f) return "—";
  const d = new Date(f);
  return d.toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" })
       + "\n" + d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" });
}

function Ventas({ onNueva, onDetalle }) {
  const [ventas, setVentas]     = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState(null);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina]     = useState(1);

  useEffect(() => { cargar(); }, []);

  const cargar = async () => {
    setLoading(true);
    try {
      const data = await listarVentas();
      setVentas(data);
    } catch(e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const handleAnular = async (id) => {
    if (!confirm("¿Anular esta venta?")) return;
    try {
      await anularVenta(id);
      cargar();
    } catch(e) { alert(e.message); }
  };

  const filtradas = ventas.filter(v =>
    String(v.id).includes(busqueda) ||
    v.clienteNombre?.toLowerCase().includes(busqueda.toLowerCase()) ||
    v.clienteId?.toLowerCase?.().includes(busqueda.toLowerCase())
  );

  const totalPags = Math.ceil(filtradas.length / POR_PAG) || 1;
  const paginadas = filtradas.slice((pagina-1)*POR_PAG, pagina*POR_PAG);

  const totalVentas      = ventas.reduce((a,v) => a + (parseFloat(v.total)||0), 0);
  const transacciones    = ventas.length;
  const ticketPromedio   = transacciones ? (totalVentas / transacciones) : 0;
  const cancelaciones    = ventas.filter(v => ["CANCELADA","ANULADA"].includes(v.estado)).length;

  return (
    <div className="vt-root">

      {/* Topbar */}
      <header className="vt-top">
        <div className="vt-top-left">
          <span className="vt-brand">DriveMaster</span>
          <span className="vt-div">|</span>
          <span className="vt-breadcrumb">Ventas</span>
        </div>
        <div className="vt-tabs">
          <button className="vt-tab" onClick={onNueva}>Registrar Venta</button>
          <button className="vt-tab active">Ver Ventas</button>
        </div>
        <div className="vt-top-right">
          <div className="vt-search">
            <span className="vt-search-ico">🔍</span>
            <input
              placeholder="Buscar transacciones, clientes..."
              value={busqueda}
              onChange={e => { setBusqueda(e.target.value); setPagina(1); }}
            />
          </div>
          <button className="vt-ico-btn">🔔</button>
          <div className="vt-avatar">A</div>
        </div>
      </header>

      {/* Page header */}
      <div className="vt-page-header">
        <div>
          <h1 className="vt-h1">Ventas</h1>
        </div>
        <div className="vt-header-actions">
          <button className="vt-toggle-group" style={{ border:"none", background:"none", padding:0, display:"flex" }}>
            <button className="vt-toggle" onClick={onNueva}>Registrar Venta</button>
            <button className="vt-toggle active">Ver Ventas</button>
          </button>
          <button className="vt-btn-outline">📅 Últimos 30 días</button>
          <button className="vt-btn-outline">⚙ Filtros</button>
          <button className="vt-btn-primary">↓ Exportar</button>
        </div>
      </div>

      {error && <p className="vt-error">{error}</p>}

      {/* Table */}
      <div className="vt-card">
        {loading ? (
          <p className="vt-empty">Cargando ventas...</p>
        ) : (
          <div style={{ overflowX:"auto" }}>
            <table className="vt-table">
              <thead>
                <tr>
                  <th>ID Venta</th>
                  <th>Cliente</th>
                  <th>Fecha</th>
                  <th>Método</th>
                  <th>Estado</th>
                  <th style={{ textAlign:"right" }}>Total</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {paginadas.length === 0 ? (
                  <tr><td colSpan={7} className="vt-empty">No hay ventas registradas</td></tr>
                ) : paginadas.map(v => {
                  const m = METODO_ICO[v.metodoPago] || { ico:"💰", label: v.metodoPago || "—" };
                  const estadoKey = v.estado?.toUpperCase?.() || "";
                  return (
                    <tr key={v.id}>
                      <td>
                        <span className="vt-id">
                          #VK-{String(v.id).padStart(5,"0")}
                        </span>
                      </td>
                      <td>
                        <div className="vt-client-cell">
                          <span className="vt-client-name">{v.clienteNombre || `Cliente #${v.clienteId}`}</span>
                          <span className="vt-client-id">{v.clienteIdentificacion || v.clienteId || "—"}</span>
                        </div>
                      </td>
                      <td style={{ whiteSpace:"pre-line", fontSize:".8rem", color:"var(--text)", lineHeight:1.5 }}>
                        {formatFecha(v.fecha)}
                      </td>
                      <td>
                        <div className="vt-metodo">
                          <span className="vt-metodo-ico">{m.ico}</span>
                          {m.label}
                        </div>
                      </td>
                      <td>
                        <span className={`vt-badge ${ESTADO_CLASS[estadoKey] || "pendiente"}`}>
                          {v.estado || "—"}
                        </span>
                      </td>
                      <td style={{ textAlign:"right" }}>
                        <span className="vt-total">${parseFloat(v.total || 0).toLocaleString("es-CO", { minimumFractionDigits:2 })}</span>
                      </td>
                      <td>
                        <div className="vt-row-actions">
                          <button className="vt-action-btn ver" onClick={() => onDetalle(v.id)}>Ver detalle</button>
                          {!["CANCELADA","ANULADA"].includes(estadoKey) && (
                            <button className="vt-action-btn anular" onClick={() => handleAnular(v.id)}>Anular</button>
                          )}
                          <button className="vt-menu-btn">⋮</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        <div className="vt-pag">
          <span className="vt-pag-info">
            Mostrando <strong>{filtradas.length === 0 ? 0 : (pagina-1)*POR_PAG+1}-{Math.min(pagina*POR_PAG, filtradas.length)}</strong> de <strong>{filtradas.length}</strong> ventas
          </span>
          <div className="vt-pag-right">
            <button className="vt-pag-btn" onClick={() => setPagina(p=>Math.max(1,p-1))} disabled={pagina===1}>‹</button>
            {Array.from({ length: Math.min(totalPags,5) }, (_,i) => i+1).map(n => (
              <button key={n} className={`vt-pag-num${pagina===n?" on":""}`} onClick={() => setPagina(n)}>{n}</button>
            ))}
            {totalPags > 5 && <span style={{ color:"var(--muted)", padding:"0 .2rem" }}>...</span>}
            {totalPags > 5 && <button className="vt-pag-num" onClick={() => setPagina(totalPags)}>{totalPags}</button>}
            <button className="vt-pag-btn" onClick={() => setPagina(p=>Math.min(totalPags,p+1))} disabled={pagina===totalPags}>›</button>
          </div>
        </div>
      </div>

      {/* Stats cards */}
      <div className="vt-stats">
        <div className="vt-stat-card">
          <div className="vt-stat-top">
            <div className="vt-stat-ico">📈</div>
            <span className="vt-stat-delta pos">+12.5%</span>
          </div>
          <p className="vt-stat-label">Ventas Totales</p>
          <p className="vt-stat-val orange">${totalVentas.toLocaleString("es-CO", { minimumFractionDigits:2 })}</p>
        </div>
        <div className="vt-stat-card">
          <div className="vt-stat-top">
            <div className="vt-stat-ico">🧾</div>
            <span className="vt-stat-delta pos">+4%</span>
          </div>
          <p className="vt-stat-label">Transacciones</p>
          <p className="vt-stat-val">{transacciones.toLocaleString()}</p>
        </div>
        <div className="vt-stat-card">
          <div className="vt-stat-top">
            <div className="vt-stat-ico">💰</div>
            <span className="vt-stat-delta neu">Avg.</span>
          </div>
          <p className="vt-stat-label">Ticket Promedio</p>
          <p className="vt-stat-val">${ticketPromedio.toLocaleString("es-CO", { minimumFractionDigits:2 })}</p>
        </div>
        <div className="vt-stat-card">
          <div className="vt-stat-top">
            <div className="vt-stat-ico">❌</div>
            <span className="vt-stat-delta neg">-2%</span>
          </div>
          <p className="vt-stat-label">Cancelaciones</p>
          <p className="vt-stat-val red">{cancelaciones}</p>
        </div>
      </div>
    </div>
  );
}

export default Ventas;