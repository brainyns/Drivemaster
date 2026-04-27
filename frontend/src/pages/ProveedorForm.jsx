import { useEffect, useState } from "react";
import { crearProveedor, actualizarProveedor, obtenerProveedor } from "../services/proveedorService";
import "../css/proveedores.css";

const CATEGORIAS = [
  "Motores & Transmisión",
  "Neumáticos",
  "Mantenimiento",
  "Electrónica & Sensores",
  "Carrocería & Pintura",
  "Frenos & Suspensión",
  "Climatización",
  "Eléctrico & Batería",
  "Filtros & Aceites",
  "Otro",
];

// ── Iconos ────────────────────────────────────────────────────────────────────
const IconBuilding = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
  </svg>
);
const IconTag = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
    <line x1="7" y1="7" x2="7.01" y2="7"/>
  </svg>
);
const IconUser = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
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
const IconMapPin = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/>
  </svg>
);
const IconChevronDown = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <polyline points="6 9 12 15 18 9"/>
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
    categoria: "",
    contacto: "",
    telefono: "",
    correo: "",
    direccion: "",
  });

  const [loading, setLoading] = useState(false);
  const [fetchLoading, setFetchLoading] = useState(false);
  const [error, setError] = useState(null);
  const [guardado, setGuardado] = useState(false);
  const [catOpen, setCatOpen] = useState(false);

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

                  {/* Categoría */}
                  <div className="kpf-group">
                    <label className="kpf-label">Categoría de Repuestos</label>
                    <div className="kpf-select-wrap">
                      <span className="kpf-select-icon"><IconTag /></span>
                      <button
                        type="button"
                        className={`kpf-select-btn ${catOpen ? "open" : ""} ${!form.categoria ? "empty" : ""}`}
                        onClick={() => setCatOpen(!catOpen)}
                      >
                        {form.categoria || "Seleccionar categoría..."}
                        <span className="arrow"><IconChevronDown /></span>
                      </button>
                      {catOpen && (
                        <div className="kpf-dropdown">
                          {CATEGORIAS.map((cat) => (
                            <div
                              key={cat}
                              className={`kpf-option ${form.categoria === cat ? "selected" : ""}`}
                              onClick={() => { setForm({ ...form, categoria: cat }); setCatOpen(false); }}
                            >
                              {cat}
                              {form.categoria === cat && <IconCheck />}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Contacto */}
                  <div className="kpf-group">
                    <label className="kpf-label">Persona de Contacto</label>
                    <div className="kpf-input-wrap">
                      <span className="kpf-input-icon"><IconUser /></span>
                      <input
                        className="kpf-input"
                        name="contacto"
                        value={form.contacto}
                        onChange={handleChange}
                        placeholder="Nombre completo del representante"
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
                        placeholder="+52 55 1234 5678"
                      />
                    </div>
                  </div>

                  {/* Correo */}
                  <div className="kpf-group full">
                    <label className="kpf-label">Correo Electrónico Corporativo <span className="req">*</span></label>
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

                  {/* Dirección */}
                  <div className="kpf-group full">
                    <label className="kpf-label">Dirección Fiscal / Almacén</label>
                    <div className="kpf-input-wrap" style={{ alignItems: "flex-start" }}>
                      <span className="kpf-input-icon" style={{ top: "13px", position: "absolute" }}><IconMapPin /></span>
                      <textarea
                        className="kpf-textarea"
                        name="direccion"
                        value={form.direccion}
                        onChange={handleChange}
                        placeholder="Calle, número, código postal y ciudad..."
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
                Asigna la categoría correcta ahora para habilitar las alertas de stock crítico automáticas.
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}