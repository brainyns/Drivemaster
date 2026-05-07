import { useEffect, useState } from "react";
import { listarCompras } from "../services/compraService";
import "../css/compras.css";

const POR_PAG = 10;

const ESTADO_CSS = {
  RECIBIDA: "cp-est--recibida",
  PENDIENTE: "cp-est--pendiente",
  CANCELADA: "cp-est--cancelada",
  PAGADA: "cp-est--recibida",
};

const IcPlus = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2.5"
    width="15"
    height="15"
  >
    <line x1="12" y1="5" x2="12" y2="19" />
    <line x1="5" y1="12" x2="19" y2="12" />
  </svg>
);
const IcSearch = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    width="14"
    height="14"
  >
    <circle cx="11" cy="11" r="8" />
    <line x1="21" y1="21" x2="16.65" y2="16.65" />
  </svg>
);
const IcCart = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    width="18"
    height="18"
  >
    <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" />
    <line x1="3" y1="6" x2="21" y2="6" />
    <path d="M16 10a4 4 0 01-8 0" />
  </svg>
);
const IcCash = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    width="18"
    height="18"
  >
    <rect x="1" y="4" width="22" height="16" rx="2" />
    <line x1="1" y1="10" x2="23" y2="10" />
  </svg>
);
const IcBox = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    width="18"
    height="18"
  >
    <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
  </svg>
);
const IcEye = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    width="14"
    height="14"
  >
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);
const IcFilter = (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    width="14"
    height="14"
  >
    <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
  </svg>
);

function formatFecha(f) {
  if (!f) return "—";
  const d = new Date(f);
  return {
    date: d.toLocaleDateString("es-CO", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }),
    time: d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" }),
  };
}

function Compras({ onNueva, onDetalle, token }) {
  const [compras, setCompras] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina] = useState(1);

  useEffect(() => {
    listarCompras(token)
      .then(setCompras)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [token]);

  const filtradas = compras.filter(
    (c) =>
      String(c.id).includes(busqueda) ||
      c.proveedorNombre?.toLowerCase().includes(busqueda.toLowerCase()) ||
      c.proveedorId?.toLowerCase?.().includes(busqueda.toLowerCase()),
  );

  const totalPags = Math.ceil(filtradas.length / POR_PAG) || 1;
  const paginadas = filtradas.slice((pagina - 1) * POR_PAG, pagina * POR_PAG);

  // Stats
  const totalGastado = compras.reduce((s, c) => s + (c.total || 0), 0);
  const totalItems = compras.reduce(
    (s, c) => s + (c.productos?.length || 0),
    0,
  );
  const pendientes = compras.filter((c) => c.estado === "PENDIENTE").length;

  return (
    <div className="cp-root">
      {/* Header */}
      <div className="cp-header">
        <div>
          <p className="cp-breadcrumb">Gestión de compras</p>
          <h1 className="cp-h1">Purchase Management</h1>
        </div>
        <button className="cp-btn-new" onClick={onNueva}>
          {IcPlus} Nueva Compra
        </button>
      </div>

      {/* KPIs */}
      <div className="cp-kpis">
        <div className="cp-kpi">
          <div className="cp-kpi-ico-wrap">{IcCart}</div>
          <div>
            <p className="cp-kpi-label">Total Órdenes</p>
            <p className="cp-kpi-val">{compras.length.toLocaleString()}</p>
            <p className="cp-kpi-sub">registradas</p>
          </div>
        </div>
        <div className="cp-kpi">
          <div className="cp-kpi-ico-wrap orange">{IcCash}</div>
          <div>
            <p className="cp-kpi-label">Total Gastado</p>
            <p className="cp-kpi-val orange">
              $
              {totalGastado.toLocaleString("es-CO", {
                minimumFractionDigits: 0,
              })}
            </p>
            <p className="cp-kpi-sub">en compras</p>
          </div>
        </div>
        <div className="cp-kpi">
          <div className="cp-kpi-ico-wrap blue">{IcBox}</div>
          <div>
            <p className="cp-kpi-label">Ítems Comprados</p>
            <p className="cp-kpi-val blue">{totalItems}</p>
            <p className="cp-kpi-sub">productos totales</p>
          </div>
        </div>
        <div className="cp-kpi cp-kpi--highlight">
          <div className="cp-kpi-ico-wrap amber">{IcFilter}</div>
          <div>
            <p className="cp-kpi-label">Pendientes</p>
            <p className="cp-kpi-val amber">{pendientes}</p>
            <p className="cp-kpi-sub">por recibir</p>
          </div>
        </div>
      </div>

      {/* Tabla */}
      <div className="cp-card">
        <div className="cp-card-toolbar">
          <div className="cp-card-title-wrap">
            <span className="cp-accent-bar" />
            <span className="cp-card-title">Live Purchase Feed</span>
            <span className="cp-card-sub">Sincronización en tiempo real</span>
          </div>
          <div className="cp-toolbar-right">
            <div className="cp-search-wrap">
              <span>{IcSearch}</span>
              <input
                className="cp-search"
                placeholder="Buscar compra, proveedor..."
                value={busqueda}
                onChange={(e) => {
                  setBusqueda(e.target.value);
                  setPagina(1);
                }}
              />
            </div>
            <button className="cp-btn-outline">{IcFilter} Filtrar</button>
          </div>
        </div>

        {loading ? (
          <div className="cp-loading">
            <div className="cp-spinner" />
            Cargando compras...
          </div>
        ) : error ? (
          <p className="cp-error">{error}</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="cp-table">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Purchase ID</th>
                  <th>Proveedor</th>
                  <th style={{ textAlign: "center" }}>Ítems</th>
                  <th style={{ textAlign: "right" }}>Total</th>
                  <th>Estado</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {paginadas.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="cp-empty">
                      No hay compras registradas
                    </td>
                  </tr>
                ) : (
                  paginadas.map((c) => {
                    const f = formatFecha(c.fecha);
                    const estKey = c.estado?.toUpperCase() || "RECIBIDA";
                    return (
                      <tr
                        key={c.id}
                        className="cp-row"
                        onClick={() => onDetalle(c.id)}
                        style={{ cursor: "pointer" }}
                      >
                        <td>
                          <p className="cp-fecha-date">{f.date}</p>
                          <p className="cp-fecha-time">{f.time}</p>
                        </td>
                        <td>
                          <span className="cp-po-id">
                            #{String(c.id).toUpperCase()}
                          </span>
                        </td>
                        <td>
                          <div className="cp-prov-cell">
                            <div className="cp-prov-ico">
                              {(c.proveedorNombre || "P")[0].toUpperCase()}
                            </div>
                            <div>
                              <p className="cp-prov-name">{c.proveedorId}</p>
                              <p className="cp-prov-sub">
                                {c.proveedorId?.slice?.(-8) || "—"}
                              </p>
                            </div>
                          </div>
                        </td>
                        <td style={{ textAlign: "center" }}>
                          <span className="cp-items-badge">
                            {c.productos?.length || 0} uds
                          </span>
                        </td>
                        <td style={{ textAlign: "right" }}>
                          <span className="cp-total">
                            $
                            {(c.total || 0).toLocaleString("es-CO", {
                              minimumFractionDigits: 2,
                            })}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`cp-estado ${ESTADO_CSS[estKey] || "cp-est--recibida"}`}
                          >
                            {c.estado || "RECIBIDA"}
                          </span>
                        </td>
                        <td
                          onClick={(e) => {
                            e.stopPropagation();
                            onDetalle(c.id);
                          }}
                        >
                          <button className="cp-icon-btn" title="Ver detalle">
                            {IcEye}
                          </button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* Paginación */}
        <div className="cp-pag">
          <span className="cp-pag-info">
            Mostrando{" "}
            <strong>
              {filtradas.length === 0 ? 0 : (pagina - 1) * POR_PAG + 1}–
              {Math.min(pagina * POR_PAG, filtradas.length)}
            </strong>{" "}
            de <strong>{filtradas.length}</strong> compras
          </span>
          <div className="cp-pag-btns">
            <button
              className="cp-pag-btn"
              onClick={() => setPagina((p) => Math.max(1, p - 1))}
              disabled={pagina === 1}
            >
              ‹
            </button>
            {Array.from(
              { length: Math.min(totalPags, 5) },
              (_, i) => i + 1,
            ).map((n) => (
              <button
                key={n}
                className={`cp-pag-num${pagina === n ? " on" : ""}`}
                onClick={() => setPagina(n)}
              >
                {n}
              </button>
            ))}
            {totalPags > 5 && (
              <>
                <span style={{ color: "var(--muted)" }}>...</span>
                <button
                  className="cp-pag-num"
                  onClick={() => setPagina(totalPags)}
                >
                  {totalPags}
                </button>
              </>
            )}
            <button
              className="cp-pag-btn"
              onClick={() => setPagina((p) => Math.min(totalPags, p + 1))}
              disabled={pagina === totalPags}
            >
              ›
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Compras;
