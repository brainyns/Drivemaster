import { useEffect, useState } from "react";
import { listarProveedores, eliminarProveedor, exportarProveedores } from "../services/proveedorService";
import "../css/proveedores.css";
import "../css/admin-base.css";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";

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
const IconDownload = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
    <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
  </svg>
);

// ── Helpers ────────────────────────────────────────────────────────────────────
function getInitials(nombre) {
  return (nombre || "").split(" ").slice(0, 2).map((w) => w[0]).join("").toUpperCase();
}

const ITEMS_PER_PAGE = 8;

export default function Proveedores({ onNuevo, onEditar, token }) {
  const [proveedores, setProveedores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina] = useState(1);
  const [confirmId, setConfirmId] = useState(null);
  const [exportando, setExportando] = useState(false);

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

  const handleExportar = async () => {
    setExportando(true);
    try {
      await exportarProveedores(token);
    } catch (e) {
      alert("Error al exportar: " + e.message);
    } finally {
      setExportando(false);
    }
  };

  // Resumen con datos reales de la lista (antes eran cifras fijas: "98% OTD", "Premium", un gasto en MXN)
  const conNit = proveedores.filter((p) => p.nit).length;
  const conCorreo = proveedores.filter((p) => p.correo).length;
  const desdeN = filtrados.length === 0 ? 0 : (pagina - 1) * ITEMS_PER_PAGE + 1;
  const hastaN = Math.min(pagina * ITEMS_PER_PAGE, filtrados.length);
  const aEliminar = proveedores.find((p) => p.id === confirmId);

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Proveedores</h1>
          <p className="adm-encabezado__sub">Directorio de los proveedores a los que le compras para surtir tu inventario.</p>
        </div>
        <div className="adm-encabezado__lado">
          <button type="button" className={`adm-btn${exportando ? " adm-btn--cargando" : ""}`} onClick={handleExportar}
            disabled={exportando} aria-busy={exportando}>
            {!exportando && <IconDownload />} {exportando ? "Generando…" : "Descargar reporte"}
          </button>
          <button type="button" className="adm-btn adm-btn--primario" onClick={onNuevo}><IconPlus /> Nuevo proveedor</button>
        </div>
      </header>

      <section className="adm-kpis" aria-label="Resumen de proveedores">
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true"><IconTruck /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Proveedores registrados</span>
            <p className="adm-kpi__valor">{proveedores.length}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true"><IconShield /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Con NIT registrado</span>
            <p className="adm-kpi__valor">{conNit}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true"><IconSearch /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Con correo de contacto</span>
            <p className="adm-kpi__valor">{conCorreo}</p>
          </div>
        </div>
      </section>

      <div className="adm-filtros" role="search" aria-label="Buscar proveedores">
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" placeholder="Nombre, contacto o correo" value={busqueda}
            onChange={(e) => { setBusqueda(e.target.value); setPagina(1); }} />
        </label>
      </div>

      {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}

      <div className="adm-tarjeta" aria-busy={loading}>
        {loading ? (
          <div className="adm-cargando">Cargando proveedores…</div>
        ) : filtrados.length === 0 ? (
          <p className="adm-vacio">{busqueda ? "Sin resultados para tu búsqueda." : "No hay proveedores registrados."}</p>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Proveedor</th>
                  <th>NIT</th>
                  <th>Teléfono</th>
                  <th>Correo</th>
                  <th className="adm-tabla__der">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {paginados.map((p) => (
                  <tr key={p.id}>
                    <td data-label="Proveedor">
                      <div className="adm-celda-producto">
                        <span className="adm-miniatura" aria-hidden="true">{getInitials(p.nombre)}</span>
                        <div>
                          <strong>{p.nombre}</strong>
                          {p.categoria && <span className="adm-tabla__sub">{p.categoria}</span>}
                        </div>
                      </div>
                    </td>
                    <td data-label="NIT">{p.nit || "—"}</td>
                    <td data-label="Teléfono" className="adm-tabla__nowrap">{p.telefono || "—"}</td>
                    <td data-label="Correo">{p.correo || "—"}</td>
                    <td className="adm-tabla__der adm-tabla__celda--ancha">
                      <div className="adm-tabla__acciones">
                        <button type="button" className="adm-btn adm-btn--chico" onClick={() => onEditar(p.id)}>Editar</button>
                        <button type="button" className="adm-btn adm-btn--chico adm-btn--peligro" onClick={() => setConfirmId(p.id)}>Eliminar</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <nav className="adm-paginacion" aria-label="Paginación">
        {totalPaginas > 1 && (
          <button type="button" className="adm-btn" disabled={pagina === 1} onClick={() => setPagina((n) => n - 1)}>Anterior</button>
        )}
        <span>Mostrando {desdeN}–{hastaN} de {filtrados.length} proveedores</span>
        {totalPaginas > 1 && (
          <button type="button" className="adm-btn" disabled={pagina === totalPaginas} onClick={() => setPagina((n) => n + 1)}>Siguiente</button>
        )}
      </nav>

      {confirmId && (
        <ConfirmacionAdmin
          titulo={`Eliminar ${aEliminar?.nombre || "proveedor"}`}
          texto="Esta acción es permanente y no se puede deshacer. Se perderán todos los datos del proveedor."
          confirmar="Sí, eliminar"
          variante="peligro"
          onConfirmar={() => handleEliminar(confirmId)}
          onCancelar={() => setConfirmId(null)}
        />
      )}
    </div>
  );
}
