import { useEffect, useState, useRef } from "react";
import { listarCompras } from "../services/compraService";
import "../css/compras.css";
import "../css/compras-filtros.css";
import "../css/admin-base.css";

const POR_PAG = 10;

const IcPlus = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="15" height="15">
    <line x1="12" y1="5" x2="12" y2="19" />
    <line x1="5" y1="12" x2="19" y2="12" />
  </svg>
);
const IcCart = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
    <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" />
    <line x1="3" y1="6" x2="21" y2="6" />
    <path d="M16 10a4 4 0 01-8 0" />
  </svg>
);
const IcCash = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
    <rect x="1" y="4" width="22" height="16" rx="2" />
    <line x1="1" y1="10" x2="23" y2="10" />
  </svg>
);
const IcBox = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="18" height="18">
    <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
  </svg>
);
const IcFilter = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14">
    <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
  </svg>
);
const IcX = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13">
    <line x1="18" y1="6" x2="6" y2="18" />
    <line x1="6" y1="6" x2="18" y2="18" />
  </svg>
);

const FILTROS_VACIOS = {
  fechaDesde: "",
  fechaHasta: "",
  totalMin: "",
  totalMax: "",
};

function formatFecha(f) {
  if (!f) return { date: "—", time: "" };
  const d = new Date(f);
  return {
    date: d.toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }),
    time: d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" }),
  };
}

function contarFiltrosActivos(filtros) {
  let count = 0;
  if (filtros.fechaDesde || filtros.fechaHasta) count++;
  if (filtros.totalMin !== "" || filtros.totalMax !== "") count++;
  return count;
}

// ─── Panel de Filtros ───────────────────────────────────────────────────────
function FilterPanel({ open, onClose, onApply, filtrosActivos }) {
  const [draft, setDraft] = useState({ ...filtrosActivos });

  function handleApply() {
    onApply(draft);
    onClose();
  }

  function handleClear() {
    const vacio = { ...FILTROS_VACIOS };
    setDraft(vacio);
    onApply(vacio);
    onClose();
  }

  if (!open) return null;

  return (
    <div className="cp-fp-panel">
      {/* Header */}
      <div className="cp-fp-header">
        <span className="cp-fp-title">{IcFilter} Filtros</span>
        <button className="cp-fp-close" onClick={onClose}>×</button>
      </div>

      <div className="cp-fp-body">
        {/* Rango fecha */}
        <div>
          <p className="cp-fp-label">Fecha</p>
          <div className="cp-fp-range">
            <input
              type="date"
              value={draft.fechaDesde}
              onChange={(e) => setDraft((p) => ({ ...p, fechaDesde: e.target.value }))}
              className="cp-fp-input"
            />
            <span className="cp-fp-range-sep">→</span>
            <input
              type="date"
              value={draft.fechaHasta}
              onChange={(e) => setDraft((p) => ({ ...p, fechaHasta: e.target.value }))}
              className="cp-fp-input"
            />
          </div>
        </div>

        {/* Rango total */}
        <div>
          <p className="cp-fp-label">Total (COP)</p>
          <div className="cp-fp-range">
            <input
              type="number"
              placeholder="Mín"
              value={draft.totalMin}
              min={0}
              onChange={(e) => setDraft((p) => ({ ...p, totalMin: e.target.value }))}
              className="cp-fp-input"
            />
            <span className="cp-fp-range-sep">→</span>
            <input
              type="number"
              placeholder="Máx"
              value={draft.totalMax}
              min={0}
              onChange={(e) => setDraft((p) => ({ ...p, totalMax: e.target.value }))}
              className="cp-fp-input"
            />
          </div>
        </div>
      </div>

      {/* Footer */}
      <div className="cp-fp-footer">
        <button className="cp-fp-btn-clear" onClick={handleClear}>Limpiar</button>
        <button className="cp-fp-btn-apply" onClick={handleApply}>Aplicar filtros</button>
      </div>
    </div>
  );
}

// ─── Componente principal ───────────────────────────────────────────────────
function Compras({ onNueva, onDetalle, token }) {
  const [compras, setCompras] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina] = useState(1);
  const [panelOpen, setPanelOpen] = useState(false);
  const [filtros, setFiltros] = useState({ ...FILTROS_VACIOS });
  const filterWrapRef = useRef(null);

  useEffect(() => {
    listarCompras(token)
      .then(setCompras)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [token]);

  // Cierra el panel al hacer click fuera
  useEffect(() => {
    function handleClickOutside(e) {
      if (filterWrapRef.current && !filterWrapRef.current.contains(e.target)) {
        setPanelOpen(false);
      }
    }
    if (panelOpen) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [panelOpen]);

  // ── Filtrado combinado ──────────────────────────────────────────────────
  const filtradas = compras.filter((c) => {
    // Búsqueda por texto
    const q = busqueda.toLowerCase();
    const matchSearch =
      !q ||
      String(c.id).toLowerCase().includes(q) ||
      c.proveedorNombre?.toLowerCase().includes(q) ||
      c.proveedorId?.toLowerCase?.().includes(q);

    // Rango de fecha
    const fecha = c.fecha ? new Date(c.fecha) : null;
    const matchDesde = !filtros.fechaDesde || (fecha && fecha >= new Date(filtros.fechaDesde));
    const matchHasta = !filtros.fechaHasta || (fecha && fecha <= new Date(filtros.fechaHasta + "T23:59:59"));

    // Rango de total
    const matchMin = filtros.totalMin === "" || (c.total || 0) >= Number(filtros.totalMin);
    const matchMax = filtros.totalMax === "" || (c.total || 0) <= Number(filtros.totalMax);

    return matchSearch && matchDesde && matchHasta && matchMin && matchMax;
  });

  const totalPags = Math.ceil(filtradas.length / POR_PAG) || 1;
  const paginadas = filtradas.slice((pagina - 1) * POR_PAG, pagina * POR_PAG);

  // KPIs (sobre la lista completa, no filtrada)
  const totalGastado = compras.reduce((s, c) => s + (c.total || 0), 0);
  const totalItems = compras.reduce((s, c) => s + (c.productos?.length || 0), 0);
  const pendientes = compras.filter((c) => c.estado === "PENDIENTE").length;

  const filtrosActivos = contarFiltrosActivos(filtros);

  function handleApplyFiltros(nuevosFiltros) {
    setFiltros(nuevosFiltros);
    setPagina(1);
  }

  function quitarFiltro(tipo) {
    setFiltros((prev) => {
      const next = { ...prev };
      if (tipo === "fecha") { next.fechaDesde = ""; next.fechaHasta = ""; }
      if (tipo === "total") { next.totalMin = ""; next.totalMax = ""; }
      return next;
    });
    setPagina(1);
  }

  // Tags de filtros activos
  const filterTags = [];
  if (filtros.fechaDesde || filtros.fechaHasta)
    filterTags.push({ key: "fecha", label: [filtros.fechaDesde, filtros.fechaHasta].filter(Boolean).join(" → ") });
  if (filtros.totalMin !== "" || filtros.totalMax !== "")
    filterTags.push({
      key: "total",
      label: `$${filtros.totalMin || "0"} – $${filtros.totalMax || "∞"}`,
    });

  // Variante y texto del badge de cada estado (admin-base.css)
  const ESTADO = {
    RECIBIDA:  { label: "Recibida",  badge: "exito" },
    PAGADA:    { label: "Pagada",    badge: "exito" },
    PENDIENTE: { label: "Pendiente", badge: "pendiente" },
    CANCELADA: { label: "Cancelada", badge: "peligro" },
  };
  const dinero = (n) => `$${(n || 0).toLocaleString("es-CO", { maximumFractionDigits: 0 })}`;
  const abrirConTeclado = (e, id) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onDetalle(id); }
  };

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Compras</h1>
          <p className="adm-encabezado__sub">Órdenes de compra a tus proveedores. Al recibirlas, el stock se actualiza en tu inventario.</p>
        </div>
        <div className="adm-encabezado__lado">
          <button type="button" className="adm-btn adm-btn--primario" onClick={onNueva}>{IcPlus} Nueva compra</button>
        </div>
      </header>

      <section className="adm-kpis" aria-label="Resumen de compras">
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true">{IcCart}</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Órdenes registradas</span>
            <p className="adm-kpi__valor">{compras.length.toLocaleString("es-CO")}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true">{IcCash}</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Total gastado</span>
            <p className="adm-kpi__valor">{dinero(totalGastado)}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono adm-kpi__icono--info" aria-hidden="true">{IcBox}</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Ítems comprados</span>
            <p className="adm-kpi__valor">{totalItems}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className={`adm-kpi__icono${pendientes > 0 ? " adm-kpi__icono--pendiente" : ""}`} aria-hidden="true">{IcFilter}</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Pendientes por recibir</span>
            <p className="adm-kpi__valor">{pendientes}</p>
          </div>
        </div>
      </section>

      <div className="adm-filtros" role="search" aria-label="Filtros de compras">
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" placeholder="Número de compra o proveedor" value={busqueda}
            onChange={(e) => { setBusqueda(e.target.value); setPagina(1); }} />
        </label>
        {/* Botón Filtrar + panel flotante de fecha y total */}
        <div className="adm-filtros__acciones cp-filter-wrap" ref={filterWrapRef}>
          <button type="button" className={`adm-btn${panelOpen ? " adm-btn--activo" : ""}`}
            aria-expanded={panelOpen} onClick={() => setPanelOpen((v) => !v)}>
            {IcFilter} Más filtros
            {filtrosActivos > 0 && <span className="adm-pestana__n">{filtrosActivos}</span>}
          </button>
          <FilterPanel
            key={panelOpen ? "abierto" : "cerrado"}
            open={panelOpen}
            onClose={() => setPanelOpen(false)}
            onApply={handleApplyFiltros}
            filtrosActivos={filtros}
          />
        </div>
      </div>

      {/* Filtros activos */}
      {filterTags.length > 0 && (
        <div className="adm-badges cp-filtros-activos">
          <span className="adm-campo__ayuda">Filtros activos:</span>
          {filterTags.map((tag) => (
            <span key={tag.key} className="adm-badge adm-badge--info cp-filtro-activo">
              {tag.label}
              <button type="button" onClick={() => quitarFiltro(tag.key)} aria-label={`Quitar filtro ${tag.label}`}>{IcX}</button>
            </span>
          ))}
          <button type="button" className="adm-btn adm-btn--chico adm-btn--sutil" onClick={() => { setFiltros({ ...FILTROS_VACIOS }); setPagina(1); }}>
            Limpiar todo
          </button>
        </div>
      )}

      {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}

      <div className="adm-tarjeta" aria-busy={loading}>
        {loading ? (
          <div className="adm-cargando">Cargando compras…</div>
        ) : paginadas.length === 0 ? (
          <p className="adm-vacio">No hay compras que coincidan con los filtros.</p>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Compra</th>
                  <th>Proveedor</th>
                  <th>Fecha</th>
                  <th className="adm-tabla__der">Ítems</th>
                  <th className="adm-tabla__der">Total</th>
                  <th>Estado</th>
                  <th className="adm-tabla__der">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {paginadas.map((c) => {
                  const f = formatFecha(c.fecha);
                  const estKey = c.estado?.toUpperCase() || "RECIBIDA";
                  const estado = ESTADO[estKey] || { label: c.estado, badge: "neutro" };
                  return (
                    <tr key={c.id} className="adm-tabla__fila--clic" tabIndex={0}
                      onClick={() => onDetalle(c.id)} onKeyDown={(e) => abrirConTeclado(e, c.id)}>
                      <td data-label="Compra"><strong className="adm-mono">#{String(c.id).toUpperCase()}</strong></td>
                      <td data-label="Proveedor">
                        <div className="adm-celda-producto">
                          <span className="adm-miniatura" aria-hidden="true">{(c.proveedorNombre || c.proveedorId || "P")[0].toUpperCase()}</span>
                          <strong>{c.proveedorNombre || c.proveedorId}</strong>
                        </div>
                      </td>
                      <td data-label="Fecha" className="adm-tabla__nowrap">
                        {f.date}
                        <span className="adm-tabla__sub">{f.time}</span>
                      </td>
                      <td data-label="Ítems" className="adm-tabla__der">{c.productos?.length || 0}</td>
                      <td data-label="Total" className="adm-tabla__der adm-tabla__nowrap"><strong>{dinero(c.total)}</strong></td>
                      <td data-label="Estado"><span className={`adm-badge adm-badge--${estado.badge}`}>{estado.label}</span></td>
                      <td className="adm-tabla__der adm-tabla__celda--ancha">
                        <div className="adm-tabla__acciones">
                          <button type="button" className="adm-btn adm-btn--chico"
                            onClick={(e) => { e.stopPropagation(); onDetalle(c.id); }}>Ver detalle</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <nav className="adm-paginacion" aria-label="Paginación">
        {totalPags > 1 && (
          <button type="button" className="adm-btn" onClick={() => setPagina((p) => Math.max(1, p - 1))} disabled={pagina === 1}>Anterior</button>
        )}
        <span>
          Mostrando {filtradas.length === 0 ? 0 : (pagina - 1) * POR_PAG + 1}–{Math.min(pagina * POR_PAG, filtradas.length)} de {filtradas.length} compras
        </span>
        {totalPags > 1 && (
          <button type="button" className="adm-btn" onClick={() => setPagina((p) => Math.min(totalPags, p + 1))} disabled={pagina === totalPags}>Siguiente</button>
        )}
      </nav>
    </div>
  );
}

export default Compras;
