import { useEffect, useState } from "react";
import { listarProveedores, eliminarProveedor } from "../services/proveedorService";
import "../css/proveedores.css";

// ── Iconos SVG inline ──────────────────────────────────────────────────────────
const IconTruck = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <rect x="1" y="3" width="15" height="13" rx="1"/><path d="M16 8h4l3 5v4h-7V8z"/>
    <circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>
  </svg>
);
const IconShield = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
  </svg>
);
const IconDollar = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
  </svg>
);
const IconPlus = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
    <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
  </svg>
);
const IconSearch = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
  </svg>
);
const IconEdit = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
  </svg>
);
const IconTrash = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
    <path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
  </svg>
);
const IconFilter = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>
  </svg>
);
const IconDownload = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
    <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
  </svg>
);
const IconChevronLeft = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="15 18 9 12 15 6"/>
  </svg>
);
const IconChevronRight = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="9 18 15 12 9 6"/>
  </svg>
);

// ── Helpers ────────────────────────────────────────────────────────────────────
function getInitials(nombre = "") {
  return nombre.split(" ").slice(0, 2).map((w) => w[0]).join("").toUpperCase();
}
function getCategoryColor(nombre = "") {
  const colors = ["#e85d2f", "#7c6af7", "#2fb8e8", "#2fe875", "#e8c12f", "#e82fa8"];
  let h = 0;
  for (let i = 0; i < nombre.length; i++) h = (h * 31 + nombre.charCodeAt(i)) % colors.length;
  return colors[h];
}

const ITEMS_PER_PAGE = 8;

export default function Proveedores({ onNuevo, onEditar, token }) {
  const [proveedores, setProveedores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina] = useState(1);
  const [confirmId, setConfirmId] = useState(null);

  useEffect(() => {
    listarProveedores(token)
      .then(setProveedores)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [token]);

  const filtrados = proveedores.filter((p) =>
    [p.nombre, p.contacto, p.correo].some((v) =>
      (v || "").toLowerCase().includes(busqueda.toLowerCase())
    )
  );
  const totalPaginas = Math.ceil(filtrados.length / ITEMS_PER_PAGE);
  const paginados = filtrados.slice((pagina - 1) * ITEMS_PER_PAGE, pagina * ITEMS_PER_PAGE);

  const handleEliminar = async (id) => {
    try {
      await eliminarProveedor(id, token);
      setProveedores(proveedores.filter((p) => p.id !== id));
      setConfirmId(null);
    } catch (err) {
      alert("Error al eliminar: " + err.message);
    }
  };

  const gastoMensual = new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN" }).format(12450);

  return (
    <>
      <div className="kp-prov">
        {/* Header */}
        <div className="kp-prov-header">
          <div>
            <div className="kp-prov-title">Directorio de Proveedores</div>
            <div className="kp-prov-subtitle">Gestiona y monitorea el rendimiento de tus socios comerciales</div>
          </div>
          <div className="kp-prov-actions">
            <button className="kp-btn-report"><IconDownload /> Descargar Reporte</button>
            <button className="kp-btn-nuevo" onClick={onNuevo}><IconPlus /> Nuevo Proveedor</button>
          </div>
        </div>

        {/* Stats */}
        <div className="kp-stats">
          <div className="kp-stat orange">
            <div className="kp-stat-top">
              <div className="kp-stat-icon"><IconTruck /></div>
              <span className="kp-stat-badge">98% OTD</span>
            </div>
            <div className="kp-stat-label">Entregas a Tiempo</div>
            <div className="kp-stat-value">{proveedores.length} Activos</div>
          </div>
          <div className="kp-stat purple">
            <div className="kp-stat-top">
              <div className="kp-stat-icon"><IconShield /></div>
              <span className="kp-stat-badge">Top Tier</span>
            </div>
            <div className="kp-stat-label">Calidad de Repuestos</div>
            <div className="kp-stat-value">Premium</div>
          </div>
          <div className="kp-stat teal">
            <div className="kp-stat-top">
              <div className="kp-stat-icon"><IconDollar /></div>
              <span className="kp-stat-badge">−12% Var</span>
            </div>
            <div className="kp-stat-label">Gasto Mensual</div>
            <div className="kp-stat-value">{gastoMensual}</div>
          </div>
        </div>

        {/* Table */}
        <div className="kp-table-wrap">
          <div className="kp-table-toolbar">
            <div className="kp-table-info">
              Catálogo de Socios Comerciales
              <span>Mostrando {filtrados.length} de {proveedores.length} registrados</span>
            </div>
            <div className="kp-table-controls">
              <div className="kp-search">
                <IconSearch />
                <input
                  placeholder="Buscar por nombre, contacto o correo..."
                  value={busqueda}
                  onChange={(e) => { setBusqueda(e.target.value); setPagina(1); }}
                />
              </div>
             
            </div>
          </div>

          {loading ? (
            <div className="kp-empty"><div className="kp-empty-text">Cargando proveedores...</div></div>
          ) : error ? (
            <div className="kp-error">Error: {error}</div>
          ) : filtrados.length === 0 ? (
            <div className="kp-empty">
              <div className="kp-empty-icon">🏭</div>
              <div className="kp-empty-text">
                {busqueda ? "Sin resultados para tu búsqueda." : "No hay proveedores registrados."}
              </div>
            </div>
          ) : (
            <table className="kp-table">
              <thead>
                <tr>
                  <th>Nombre del Proveedor</th>
                  <th>Nit</th>
                  <th>Teléfono</th>
                  <th>Correo Electrónico</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {paginados.map((p) => {
                  const color = getCategoryColor(p.nombre);
                  return (
                    <tr key={p.id}>
                      <td>
                        <div className="kp-cell-prov">
                          <div className="kp-avatar" style={{ background: color }}>
                            {getInitials(p.nombre)}
                          </div>
                          <div>
                            <div className="kp-prov-name">{p.nombre}</div>
                            {p.categoria && <div className="kp-prov-cat">{p.categoria}</div>}
                          </div>
                        </div>
                      </td>
                      <td><span className="kp-cell-text">{p.nit || "—"}</span></td>
                      <td><span className="kp-cell-mono">{p.telefono || "—"}</span></td>
                      <td><span className="kp-cell-email">{p.correo || "—"}</span></td>
                      <td>
                        <div className="kp-cell-actions">
                          <button className="kp-btn-edit" title="Editar" onClick={() => onEditar(p.id)}>
                            <IconEdit />
                          </button>
                          <button className="kp-btn-del" title="Eliminar" onClick={() => setConfirmId(p.id)}>
                            <IconTrash />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}

          {/* Pagination */}
          {totalPaginas > 1 && (
            <div className="kp-pagination">
              <div className="kp-pag-info">
                Página {pagina} de {totalPaginas}
              </div>
              <div className="kp-pag-controls">
                <button className="kp-pag-btn" disabled={pagina === 1} onClick={() => setPagina(p => p - 1)}>
                  <IconChevronLeft />
                </button>
                {Array.from({ length: totalPaginas }, (_, i) => i + 1).map((n) => (
                  <button key={n} className={`kp-pag-btn ${n === pagina ? "active" : ""}`} onClick={() => setPagina(n)}>
                    {n}
                  </button>
                ))}
                <button className="kp-pag-btn" disabled={pagina === totalPaginas} onClick={() => setPagina(p => p + 1)}>
                  <IconChevronRight />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Modal de confirmación */}
      {confirmId && (
        <div className="kp-overlay" onClick={() => setConfirmId(null)}>
          <div className="kp-modal" onClick={(e) => e.stopPropagation()}>
            <div className="kp-modal-icon">⚠️</div>
            <h3>¿Eliminar proveedor?</h3>
            <p>Esta acción es permanente y no se puede deshacer. Se perderán todos los datos del proveedor.</p>
            <div className="kp-modal-btns">
              <button className="kp-modal-cancel" onClick={() => setConfirmId(null)}>Cancelar</button>
              <button className="kp-modal-confirm" onClick={() => handleEliminar(confirmId)}>Sí, eliminar</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}