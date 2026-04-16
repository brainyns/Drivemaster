import { useEffect, useState } from "react";
import { crearCliente, actualizarCliente, obtenerCliente } from "../services/clienteService";
import "../css/clientes.css";

function ClienteForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    nombre: "", identificacion: "", telefono: "", correo: "", direccion: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState(null);

  useEffect(() => {
    if (esEdicion) obtenerCliente(id, token).then(setForm).catch(e => setError(e.message));
  }, [id, token]);

  const handle = e => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async e => {
    e.preventDefault();
    setLoading(true); setError(null);
    try {
      esEdicion ? await actualizarCliente(id, form, token) : await crearCliente(form, token);
      onVolver();
    } catch(e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const iniciales = form.nombre
    ? form.nombre.split(" ").map(n => n[0]).join("").toUpperCase().slice(0,2)
    : null;

  return (
    <div className="km-root km-form-page">

      {/* Topbar */}
      <header className="km-top">
        <div className="km-top-left">
          <span className="km-brand">DriveMaster</span>
          <span className="km-divider">|</span>
          <span className="km-breadcrumb">Gestión de Clientes</span>
        </div>
        <div className="km-top-tabs">
          <button className="km-tab" onClick={onVolver}>Ver Clientes</button>
          <button className="km-tab active">
            {esEdicion ? "Editar Cliente" : "Agregar Cliente"} <span className="km-tab-dot" />
          </button>
        </div>
        <div className="km-top-right">
          <button className="km-ico-btn">🔔</button>
          <button className="km-ico-btn">⚙</button>
          <div className="km-avatar">A</div>
        </div>
      </header>

      {/* Page header */}
      <div className="km-page-header">
        <div>
          <h1 className="km-page-h1">Cartera de Clientes</h1>
          <p className="km-page-sub">Registre nuevos clientes y gestione sus expedientes técnicos.</p>
        </div>
      </div>

      {error && <p className="km-error">{error}</p>}

      <div className="km-bento">

        {/* Formulario principal */}
        <div className="km-form-card">
          <div className="km-form-section">
            <div className="km-form-sec-ico">👤</div>
            <div>
              <h3 className="km-form-sec-title">Información General</h3>
              <p className="km-form-sec-sub">Complete los campos obligatorios para el registro</p>
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="km-form-grid">

              <div className="km-field km-fg-full">
                <label>Nombre Completo</label>
                <input name="nombre" value={form.nombre} onChange={handle} placeholder="Ej. Juan Pérez" required />
              </div>

              <div className="km-field km-fg-full">
                <label>Identificación</label>
                <input name="identificacion" value={form.identificacion} onChange={handle} placeholder="Ej. 12345678" required />
              </div>

              <div className="km-field km-fg-half">
                <label>Teléfono</label>
                <input name="telefono" value={form.telefono} onChange={handle} placeholder="+57 300 000 0000" />
              </div>

              <div className="km-field km-fg-half">
                <label>Correo Electrónico</label>
                <input name="correo" type="email" value={form.correo} onChange={handle} placeholder="juan@ejemplo.com" />
              </div>

              <div className="km-field km-fg-full">
                <label>Dirección</label>
                <input name="direccion" value={form.direccion} onChange={handle} placeholder="Calle, número de casa, punto de referencia" />
              </div>

            </div>

            <div className="km-form-footer">
              <button type="button" className="km-btn-cancel" onClick={onVolver}>Cancelar</button>
              <button type="submit" className="km-btn-save" disabled={loading}>
                💾 {loading ? "Guardando..." : esEdicion ? "Actualizar Cliente" : "Guardar Cliente"}
              </button>
            </div>
          </form>
        </div>

        {/* Columna derecha */}
        <div className="km-side">

          {/* Vista previa */}
          <div className="km-preview-card">
            <p className="km-preview-label">Vista Previa de Ficha</p>
            <div className="km-preview-body">
              <div
                className="km-preview-ava"
                style={iniciales
                  ? { background: "linear-gradient(135deg, #ff3d00, #ff7043)", color: "white", border: "2px solid rgba(255,61,0,0.3)" }
                  : {}
                }
              >
                {iniciales || "—"}
              </div>
              <h4 className="km-preview-name">{form.nombre || "— — —"}</h4>
              <span className="km-preview-tag">{esEdicion ? "Edición" : "Nuevo Perfil"}</span>
              <div className="km-preview-fields">
                <div className="km-preview-field">
                  📞 <span>{form.telefono || "Sin asignar"}</span>
                </div>
                <div className="km-preview-field">
                  ✉ <span>{form.correo || "Sin correo"}</span>
                </div>
                <div className="km-preview-field">
                  📍 <span>{form.direccion || "Sin dirección"}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Tips */}
          <div className="km-tips-card">
            <div className="km-tips-h">
              <span style={{ fontSize:"0.9rem" }}>ℹ</span>
              <h4 className="km-tips-title">Consejos de Registro</h4>
            </div>
            <ul className="km-tips-list">
              <li><span className="km-tip-check">✔</span> Asegúrese de que el número de teléfono incluya el código de área.</li>
              <li><span className="km-tip-check">✔</span> La identificación es el dato principal para historiales.</li>
              <li><span className="km-tip-check">✔</span> Puede editar la información del cliente después de crearlo.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ClienteForm;