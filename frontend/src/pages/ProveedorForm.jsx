import { useEffect, useState } from "react";
import { crearProveedor, actualizarProveedor, obtenerProveedor } from "../services/proveedorService";
import "../css/proveedores.css";

// ── Iconos ────────────────────────────────────────────────────────────────────
const IconBuilding = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
  </svg>
);
const IconHash = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <line x1="4" y1="9" x2="20" y2="9"/><line x1="4" y1="15" x2="20" y2="15"/>
    <line x1="10" y1="3" x2="8" y2="21"/><line x1="16" y1="3" x2="14" y2="21"/>
  </svg>
);
const IconPhone = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.85 13a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.77 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l.91-.91a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
  </svg>
);
const IconMail = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
    <polyline points="22,6 12,13 2,6"/>
  </svg>
);
const IconCheck = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);
const IconArrowLeft = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
  </svg>
);
const IconSave = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
    <polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/>
  </svg>
);

export default function ProveedorForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    nombre: "",
    contacto: "",   // NIT
    telefono: "",
    correo: "",
  });

  const [loading, setLoading] = useState(false);
  const [fetchLoading, setFetchLoading] = useState(false);
  const [error, setError] = useState(null);
  const [guardado, setGuardado] = useState(false);

  useEffect(() => {
    if (esEdicion) {
      setFetchLoading(true);
      obtenerProveedor(id, token)
        .then(setForm)
        .catch((err) => setError(err.message))
        .finally(() => setFetchLoading(false));
    }
  }, [id, token]);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (esEdicion) {
        await actualizarProveedor(id, form, token);
      } else {
        await crearProveedor(form, token);
      }
      setGuardado(true);
      setTimeout(onVolver, 900);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const camposCompletos = form.nombre && form.correo;

  return (
    <div className="kpf">
      {/* Breadcrumb */}
      <div className="kpf-breadcrumb">
        <a onClick={onVolver}>Proveedores</a>
        <span className="sep">›</span>
        <span className="current">{esEdicion ? "Editar Registro" : "Nuevo Registro"}</span>
      </div>

      {/* Header */}
      <div className="kpf-header">
        <div className="kpf-title">{esEdicion ? "Editar Proveedor" : "Alta de Proveedor"}</div>
        <div className="kpf-subtitle">Completa los datos técnicos y comerciales para el alta en el sistema.</div>
      </div>

      {fetchLoading ? (
        <div className="kpf-loading">Cargando datos del proveedor...</div>
      ) : (
        <div className="kpf-layout">
          {/* Form card */}
          <div>
            {error && <div className="kpf-error">⚠ {error}</div>}

            <div className="kpf-card">
              <div className="kpf-card-title">
                <span className="kpf-card-icon">💼</span>
                Información de la Entidad
              </div>

              <form onSubmit={handleSubmit}>
                <div className="kpf-grid">
                  {/* Nombre */}
                  <div className="kpf-group">
                    <label className="kpf-label">Nombre de la Empresa <span className="req">*</span></label>
                    <div className="kpf-input-wrap">
                      <span className="kpf-input-icon"><IconBuilding /></span>
                      <input
                        className="kpf-input"
                        name="nombre"
                        value={form.nombre}
                        onChange={handleChange}
                        placeholder="Ej: Repuestos Eléctricos S.A."
                        required
                      />
                    </div>
                  </div>

                  {/* NIT (campo contacto en el modelo) */}
                  <div className="kpf-group">
                    <label className="kpf-label">NIT</label>
                    <div className="kpf-input-wrap">
                      <span className="kpf-input-icon"><IconHash /></span>
                      <input
                        className="kpf-input"
                        name="contacto"
                        value={form.contacto}
                        onChange={handleChange}
                        placeholder="Ej: 900123456-7"
                      />
                    </div>
                  </div>

                  {/* Teléfono */}
                  <div className="kpf-group">
                    <label className="kpf-label">Teléfono de Contacto</label>
                    <div className="kpf-input-wrap">
                      <span className="kpf-input-icon"><IconPhone /></span>
                      <input
                        className="kpf-input"
                        name="telefono"
                        value={form.telefono}
                        onChange={handleChange}
                        placeholder="+57 300 123 4567"
                      />
                    </div>
                  </div>

                  {/* Correo */}
                  <div className="kpf-group">
                    <label className="kpf-label">Correo Electrónico <span className="req">*</span></label>
                    <div className="kpf-input-wrap">
                      <span className="kpf-input-icon"><IconMail /></span>
                      <input
                        className="kpf-input"
                        name="correo"
                        type="email"
                        value={form.correo}
                        onChange={handleChange}
                        placeholder="contacto@proveedor.com"
                        required
                      />
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div className="kpf-actions">
                  <button type="button" className="kpf-btn-cancel" onClick={onVolver}>
                    <IconArrowLeft /> Cancelar
                  </button>
                  <button
                    type="submit"
                    className={`kpf-btn-save ${guardado ? "success" : ""}`}
                    disabled={loading || !camposCompletos}
                  >
                    {guardado ? (
                      <><IconCheck /> ¡Guardado!</>
                    ) : loading ? (
                      "Guardando..."
                    ) : (
                      <><IconSave /> Guardar Registro</>
                    )}
                  </button>
                </div>
              </form>
            </div>
          </div>

          {/* Sidebar */}
          <div className="kpf-side">
            {/* Banner */}
            <div className="kpf-side-banner">
              <div className="kpf-side-badge">● Logística en Tiempo Real</div>
              <div className="kpf-side-banner-title">Validación Automática</div>
              <div className="kpf-side-banner-text">
                Cada registro es verificado contra nuestra base de datos de calidad OEM para asegurar la precisión del inventario.
              </div>
            </div>

            {/* Requerimientos */}
            <div className="kpf-reqs">
              <div className="kpf-reqs-title">Requerimientos</div>
              {[
                { label: "Identificación Fiscal", desc: "Válida para procesos de facturación automática.", done: true },
                { label: "Categorización API", desc: "Clasificación según estándares industriales.", done: true },
                { label: "Documentación Adjunta", desc: "Se solicitará tras el primer registro.", done: false },
              ].map((r) => (
                <div className="kpf-req" key={r.label}>
                  <div className={`kpf-req-check ${r.done ? "done" : "pending"}`}>
                    {r.done && <IconCheck />}
                  </div>
                  <div>
                    <div className="kpf-req-name">{r.label}</div>
                    <div className="kpf-req-desc">{r.desc}</div>
                  </div>
                </div>
              ))}
            </div>

            {/* Tip */}
            <div className="kpf-tip">
              <div className="kpf-tip-icon">💡</div>
              <div className="kpf-tip-title">Consejo de Eficiencia</div>
              <div className="kpf-tip-text">
                Registra el NIT correctamente para habilitar la facturación electrónica automática.
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}