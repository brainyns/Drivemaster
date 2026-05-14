import { useEffect, useState, useRef } from "react";
import { listarVentas, anularVenta, exportarVentasExcel } from "../services/ventaService";
import "../css/venta.css";
import "../css/filtros-panel.css";

const POR_PAG = 10;

const METODO_ICO = {
  TARJETA:       { ico: "💳", label: "Tarjeta" },
  EFECTIVO:      { ico: "💵", label: "Efectivo" },
  TRANSFERENCIA: { ico: "🏦", label: "Transferencia" },
  NEQUI:         { ico: "💰", label: "Nequi" },
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

// ─── Panel de filtros ────────────────────────────────────────────────────────
function FiltrosPanel({ filtros, onChange, onCerrar, anchorRef }) {
  const panelRef = useRef(null);

  useEffect(() => {
    const handler = (e) => {
      if (
        panelRef.current && !panelRef.current.contains(e.target) &&
        anchorRef.current && !anchorRef.current.contains(e.target)
      ) {
        onCerrar();
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onCerrar, anchorRef]);

  const ESTADOS = ["COMPLETADA", "PAGADA", "PENDIENTE", "CANCELADA", "ANULADA"];
  const METODOS = ["TARJETA", "EFECTIVO", "TRANSFERENCIA", "NEQUI"];

  const toggleEstado = (est) => {
    const set = new Set(filtros.estados);
    set.has(est) ? set.delete(est) : set.add(est);
    onChange({ ...filtros, estados: [...set] });
  };

  const toggleMetodo = (met) => {
    const set = new Set(filtros.metodos);
    set.has(met) ? set.delete(met) : set.add(met);
    onChange({ ...filtros, metodos: [...set] });
  };

  const limpiar = () =>
    onChange({ estados: [], metodos: [], montoMin: "", montoMax: "" });

  const hayFiltros =
    filtros.estados.length > 0 ||
    filtros.metodos.length > 0 ||
    filtros.montoMin !== "" ||
    filtros.montoMax !== "";

  return (
    <div className="vt-filtros-panel" ref={panelRef}>
      <div className="vt-filtros-header">
        <span className="vt-filtros-title">⚙ Filtros</span>
        {hayFiltros && (
          <button className="vt-filtros-clear" onClick={limpiar}>Limpiar todo</button>
        )}
      </div>

      <div className="vt-filtros-group">
        <p className="vt-filtros-label">Estado</p>
        <div className="vt-filtros-chips">
          {ESTADOS.map(est => (
            <button
              key={est}
              className={`vt-chip ${filtros.estados.includes(est) ? "on" : ""} ${ESTADO_CLASS[est] || ""}`}
              onClick={() => toggleEstado(est)}
            >
              {est}
            </button>
          ))}
        </div>
      </div>

      <div className="vt-filtros-group">
        <p className="vt-filtros-label">Método de pago</p>
        <div className="vt-filtros-chips">
          {METODOS.map(met => (
            <button
              key={met}
              className={`vt-chip ${filtros.metodos.includes(met) ? "on" : ""}`}
              onClick={() => toggleMetodo(met)}
            >
              {METODO_ICO[met]?.ico} {METODO_ICO[met]?.label}
            </button>
          ))}
        </div>
      </div>

      <div className="vt-filtros-group">
        <p className="vt-filtros-label">Monto ($)</p>
        <div className="vt-filtros-range">
          <input
            type="number"
            placeholder="Mín"
            value={filtros.montoMin}
            onChange={e => onChange({ ...filtros, montoMin: e.target.value })}
            className="vt-filtros-input"
          />
          <span className="vt-filtros-sep">—</span>
          <input
            type="number"
            placeholder="Máx"
            value={filtros.montoMax}
            onChange={e => onChange({ ...filtros, montoMax: e.target.value })}
            className="vt-filtros-input"
          />
        </div>
      </div>

      <button className="vt-filtros-apply" onClick={onCerrar}>Aplicar filtros</button>
    </div>
  );
}

// ─── Componente principal ────────────────────────────────────────────────────
function Ventas({ onNueva, onDetalle, token }) {
  const [ventas, setVentas]           = useState([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState(null);
  const [busqueda, setBusqueda]       = useState("");
  const [pagina, setPagina]           = useState(1);
  const [ultimos30, setUltimos30]     = useState(false);
  const [filtrosOpen, setFiltrosOpen] = useState(false);
  const [exportando, setExportando]   = useState(false);
  const [filtros, setFiltros]         = useState({
    estados: [], metodos: [], montoMin: "", montoMax: "",
  });
  const filtrosRef = useRef(null);

  useEffect(() => { cargar(); }, [token]);

  const cargar = async () => {
    setLoading(true);
    try {
      const data = await listarVentas(token);
      setVentas(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAnular = async (id) => {
    if (!confirm("¿Anular esta venta?")) return;
    try {
      await anularVenta(id, token);
      cargar();
    } catch (e) {
      alert(e.message);
    }
  };

  const handleExportar = async () => {
    setExportando(true);
    try {
      await exportarVentasExcel(token);
    } catch (e) {
      alert("Error al exportar: " + e.message);
    } finally {
      setExportando(false);
    }
  };

  const hace30Dias = new Date();
  hace30Dias.setDate(hace30Dias.getDate() - 30);

  const filtradas = ventas.filter(v => {
    const matchBusqueda =
      String(v.id).includes(busqueda) ||
      v.clienteNombre?.toLowerCase().includes(busqueda.toLowerCase()) ||
      v.clienteId?.toLowerCase?.().includes(busqueda.toLowerCase());
    if (!matchBusqueda) return false;

    if (ultimos30 && new Date(v.fecha) < hace30Dias) return false;

    if (filtros.estados.length > 0 && !filtros.estados.includes(v.estado?.toUpperCase())) return false;

    if (filtros.metodos.length > 0) {
      const metodo = v.pagos?.[0]?.metodo;
      if (!filtros.metodos.includes(metodo)) return false;
    }

    const total = parseFloat(v.total) || 0;
    if (filtros.montoMin !== "" && total < parseFloat(filtros.montoMin)) return false;
    if (filtros.montoMax !== "" && total > parseFloat(filtros.montoMax)) return false;

    return true;
  });

  const totalPags = Math.ceil(filtradas.length / POR_PAG) || 1;
  const paginadas = filtradas.slice((pagina - 1) * POR_PAG, pagina * POR_PAG);

  const totalVentas   = ventas.reduce((a, v) => a + (parseFloat(v.total) || 0), 0);
  const transacciones = ventas.length;
  const ticketPromedio = transacciones ? totalVentas / transacciones : 0;
  const cancelaciones  = ventas.filter(v => ["CANCELADA", "ANULADA"].includes(v.estado)).length;

  const hayFiltros =
    filtros.estados.length > 0 ||
    filtros.metodos.length > 0 ||
    filtros.montoMin !== "" ||
    filtros.montoMax !== "";

  return (
    <div className="vt-root">

     

      {/* Page header */}
      <div className="vt-page-header">
        <div>
          <h1 className="vt-h1">Ventas</h1>
        </div>
        <div className="vt-header-actions">
          <div className="vt-toggle-group" style={{ border: "none", background: "none", padding: 0, display: "flex" }}>
            <button className="vt-toggle" onClick={onNueva}>Registrar Venta</button>
            <button className="vt-toggle active">Ver Ventas</button>
          </div>

          <button
            className={`vt-btn-outline${ultimos30 ? " active" : ""}`}
            onClick={() => { setUltimos30(p => !p); setPagina(1); }}
          >
            📅 {ultimos30 ? "Últimos 30 días ✓" : "Últimos 30 días"}
          </button>

          <div style={{ position: "relative" }}>
            <button
              ref={filtrosRef}
              className={`vt-btn-outline${hayFiltros ? " active" : ""}`}
              onClick={() => setFiltrosOpen(p => !p)}
            >
              ⚙ Filtros{hayFiltros ? ` (${
                filtros.estados.length + filtros.metodos.length +
                (filtros.montoMin !== "" || filtros.montoMax !== "" ? 1 : 0)
              })` : ""}
            </button>
            {filtrosOpen && (
              <FiltrosPanel
                filtros={filtros}
                onChange={(f) => { setFiltros(f); setPagina(1); }}
                onCerrar={() => setFiltrosOpen(false)}
                anchorRef={filtrosRef}
              />
            )}
          </div>

          <button
            className="vt-btn-primary"
            onClick={handleExportar}
            disabled={exportando}
          >
            {exportando ? "Exportando..." : "↓ Exportar"}
          </button>
        </div>
      </div>

      {error && <p className="vt-error">{error}</p>}

      {/* Tabla */}
      <div className="vt-card">
        {loading ? (
          <p className="vt-empty">Cargando ventas...</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="vt-table">
              <thead>
                <tr>
                  <th>ID Venta</th>
                  <th>Cliente</th>
                  <th>Fecha</th>
                  <th>Método</th>
                  <th>Estado</th>
                  <th style={{ textAlign: "right" }}>Total</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {paginadas.length === 0 ? (
                  <tr><td colSpan={7} className="vt-empty">No hay ventas que coincidan con los filtros</td></tr>
                ) : paginadas.map(v => {
                  const metodo   = v.pagos?.[0]?.metodo;
                  const m        = METODO_ICO[metodo] || { ico: "💰", label: metodo || "—" };
                  const estadoKey = v.estado?.toUpperCase?.() || "";
                  return (
                    <tr key={v.id}>
                      <td>
                        <span className="vt-id">
                          #VK-{String(v.id).toUpperCase()}
                        </span>
                      </td>
                      <td>
                        <div className="vt-client-cell">
                          <span className="vt-client-name">{v.clienteNombre || `Cliente #${v.clienteId}`}</span>
                          <span className="vt-client-id">{v.clienteIdentificacion || v.clienteId || "—"}</span>
                        </div>
                      </td>
                      <td style={{ whiteSpace: "pre-line", fontSize: ".8rem", color: "var(--text)", lineHeight: 1.5 }}>
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
                      <td style={{ textAlign: "right" }}>
                        <span className="vt-total">
                          ${parseFloat(v.total || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 })}
                        </span>
                      </td>
                      <td>
                        <div className="vt-row-actions">
                          <button className="vt-action-btn ver" onClick={() => onDetalle(v.id)}>Ver detalle</button>
                          {!["CANCELADA", "ANULADA"].includes(estadoKey) && (
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

        {/* Paginación */}
        <div className="vt-pag">
          <span className="vt-pag-info">
            Mostrando <strong>{filtradas.length === 0 ? 0 : (pagina - 1) * POR_PAG + 1}-{Math.min(pagina * POR_PAG, filtradas.length)}</strong> de <strong>{filtradas.length}</strong> ventas
          </span>
          <div className="vt-pag-right">
            <button className="vt-pag-btn" onClick={() => setPagina(p => Math.max(1, p - 1))} disabled={pagina === 1}>‹</button>
            {Array.from({ length: Math.min(totalPags, 5) }, (_, i) => i + 1).map(n => (
              <button key={n} className={`vt-pag-num${pagina === n ? " on" : ""}`} onClick={() => setPagina(n)}>{n}</button>
            ))}
            {totalPags > 5 && <span style={{ color: "var(--muted)", padding: "0 .2rem" }}>...</span>}
            {totalPags > 5 && <button className="vt-pag-num" onClick={() => setPagina(totalPags)}>{totalPags}</button>}
            <button className="vt-pag-btn" onClick={() => setPagina(p => Math.min(totalPags, p + 1))} disabled={pagina === totalPags}>›</button>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="vt-stats">
        <div className="vt-stat-card">
          <div className="vt-stat-top">
            <div className="vt-stat-ico">📈</div>
            <span className="vt-stat-delta pos">+12.5%</span>
          </div>
          <p className="vt-stat-label">Ventas Totales</p>
          <p className="vt-stat-val orange">${totalVentas.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</p>
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
          <p className="vt-stat-val">${ticketPromedio.toLocaleString("es-CO", { minimumFractionDigits: 2 })}</p>
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