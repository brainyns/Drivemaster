import { useEffect, useState, useRef } from "react";
import { crearCliente, actualizarCliente, obtenerCliente } from "../services/clienteService";
import "../css/clientes.css";

/* ── PAÍSES ── */
const COUNTRIES = [
  { code: "CO", name: "Colombia",        dial: "+57",   flag: "🇨🇴" },
  { code: "US", name: "United States",   dial: "+1",    flag: "🇺🇸" },
  { code: "MX", name: "México",          dial: "+52",   flag: "🇲🇽" },
  { code: "ES", name: "España",          dial: "+34",   flag: "🇪🇸" },
  { code: "AR", name: "Argentina",       dial: "+54",   flag: "🇦🇷" },
  { code: "CL", name: "Chile",           dial: "+56",   flag: "🇨🇱" },
  { code: "PE", name: "Perú",            dial: "+51",   flag: "🇵🇪" },
  { code: "VE", name: "Venezuela",       dial: "+58",   flag: "🇻🇪" },
  { code: "EC", name: "Ecuador",         dial: "+593",  flag: "🇪🇨" },
  { code: "BO", name: "Bolivia",         dial: "+591",  flag: "🇧🇴" },
  { code: "PY", name: "Paraguay",        dial: "+595",  flag: "🇵🇾" },
  { code: "UY", name: "Uruguay",         dial: "+598",  flag: "🇺🇾" },
  { code: "PA", name: "Panamá",          dial: "+507",  flag: "🇵🇦" },
  { code: "CR", name: "Costa Rica",      dial: "+506",  flag: "🇨🇷" },
  { code: "GT", name: "Guatemala",       dial: "+502",  flag: "🇬🇹" },
  { code: "HN", name: "Honduras",        dial: "+504",  flag: "🇭🇳" },
  { code: "BR", name: "Brasil",          dial: "+55",   flag: "🇧🇷" },
  { code: "DO", name: "Rep. Dominicana", dial: "+1809", flag: "🇩🇴" },
  { code: "GB", name: "United Kingdom",  dial: "+44",   flag: "🇬🇧" },
  { code: "DE", name: "Alemania",        dial: "+49",   flag: "🇩🇪" },
  { code: "FR", name: "Francia",         dial: "+33",   flag: "🇫🇷" },
  { code: "CA", name: "Canadá",          dial: "+1",    flag: "🇨🇦" },
  { code: "IN", name: "India",           dial: "+91",   flag: "🇮🇳" },
];

/* ── PHONE SELECTOR ── */
function PhoneSelector({ value, onChange }) {
  const [open, setOpen]       = useState(false);
  const [query, setQuery]     = useState("");
  const [country, setCountry] = useState(COUNTRIES[0]);
  const [number, setNumber]   = useState("");
  const ref = useRef(null);

  useEffect(() => {
    const handler = e => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useEffect(() => {
    onChange(number ? `${country.dial} ${number}` : "");
  }, [country, number]);

  useEffect(() => {
    if (value && !number) {
      const found = COUNTRIES.find(c => value.startsWith(c.dial));
      if (found) {
        setCountry(found);
        setNumber(value.replace(found.dial, "").trim());
      }
    }
  }, []);

  const filtered = COUNTRIES.filter(c =>
    c.name.toLowerCase().includes(query.toLowerCase()) || c.dial.includes(query)
  );

  const selectCountry = c => { setCountry(c); setOpen(false); setQuery(""); };

  return (
    <div className="kf-phone-wrap" ref={ref}>
      <button
        type="button"
        className={`kf-country-btn ${open ? "open" : ""}`}
        onClick={() => setOpen(!open)}
      >
        <span className="kf-flag">{country.flag}</span>
        <span className="kf-dial">{country.dial}</span>
        <span className={`kf-chevron ${open ? "rotated" : ""}`}>▾</span>
      </button>

      <input
        className="kf-number-input"
        type="tel"
        placeholder="300 000 0000"
        value={number}
        onChange={e => setNumber(e.target.value.replace(/[^0-9\s\-]/g, ""))}
      />

      {open && (
        <div className="kf-dropdown">
          <div className="kf-search-wrap">
            <span className="kf-search-ico">⌕</span>
            <input
              className="kf-search"
              placeholder="Buscar país o código..."
              value={query}
              onChange={e => setQuery(e.target.value)}
              autoFocus
            />
          </div>
          <div className="kf-list">
            {filtered.length === 0 && <div className="kf-empty">Sin resultados</div>}
            {filtered.map(c => (
              <button
                key={c.code}
                type="button"
                className={`kf-option ${c.code === country.code ? "selected" : ""}`}
                onClick={() => selectCountry(c)}
              >
                <span className="kf-flag">{c.flag}</span>
                <span className="kf-option-name">{c.name}</span>
                <span className="kf-option-dial">{c.dial}</span>
                {c.code === country.code && <span className="kf-check">✓</span>}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ── MAIN COMPONENT ── */
function ClienteForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    nombre: "", identificacion: "", telefono: "", correo: "", direccion: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState(null);
  const [success, setSuccess] = useState(false);
  const [focused, setFocused] = useState(null);

  useEffect(() => {
    if (esEdicion) obtenerCliente(id, token).then(setForm).catch(e => setError(e.message));
  }, [id, token]);

  const handle = e => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async e => {
    e.preventDefault();
    setLoading(true); setError(null);
    try {
      esEdicion ? await actualizarCliente(id, form, token) : await crearCliente(form, token);
      setSuccess(true);
      setTimeout(() => onVolver(), 900);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const iniciales = form.nombre
    ? form.nombre.split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2)
    : null;

  const fp = name => ({
    onFocus: () => setFocused(name),
    onBlur:  () => setFocused(null),
  });

  return (
    <div className="km-root kf-page">

      {/* ── Header ── */}
      <div className="kf-header">
        <div className="kf-header-left">
          <button className="kf-back-btn" onClick={onVolver}>←</button>
          <div>
            <div className="kf-breadcrumb">
              <span>Clientes</span>
              <span className="kf-breadcrumb-sep">/</span>
              <span className="kf-breadcrumb-active">
                {esEdicion ? "Editar cliente" : "Nuevo cliente"}
              </span>
            </div>
            <h1 className="kf-page-title">Cartera de Clientes</h1>
            <p className="kf-page-sub">Registre y gestione expedientes de clientes</p>
          </div>
        </div>
        <div className="kf-header-badge">
          ✦ {esEdicion ? "Modo edición" : "Registro nuevo"}
        </div>
      </div>

      {/* ── Error ── */}
      {error && (
        <div className="kf-alert error">
          <span>⚠</span> {error}
        </div>
      )}

      {/* ── Bento ── */}
      <div className="kf-bento">

        {/* ── Formulario ── */}
        <div className="kf-main-card">

          <div className="kf-card-header">
            <div className="kf-card-ico">👤</div>
            <div>
              <h3 className="kf-card-title">Información General</h3>
              <p className="kf-card-sub">Complete los campos obligatorios para el registro</p>
            </div>
            <div className="kf-required-badge">
              <span className="kf-dot-req" /> Campos obligatorios
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="kf-grid">

              <div className="kf-field kf-full">
                <label className="kf-label">👤 Nombre Completo</label>
                <div className={`kf-input-wrap ${focused === "nombre" ? "focused" : ""}`}>
                  <input
                    name="nombre" value={form.nombre} onChange={handle}
                    placeholder="Ej. Juan Pérez García" required
                    {...fp("nombre")}
                  />
                  <span className="kf-req-star">*</span>
                </div>
              </div>

              <div className="kf-field kf-full">
                <label className="kf-label"># Número de Identificación</label>
                <div className={`kf-input-wrap ${focused === "identificacion" ? "focused" : ""}`}>
                  <input
                    name="identificacion" value={form.identificacion} onChange={handle}
                    placeholder="Ej. 1020304050" required
                    {...fp("identificacion")}
                  />
                  <span className="kf-req-star">*</span>
                </div>
              </div>

              <div className="kf-field kf-half">
                <label className="kf-label">📞 Teléfono</label>
                <PhoneSelector
                  value={form.telefono}
                  onChange={val => setForm(f => ({ ...f, telefono: val }))}
                />
              </div>

              <div className="kf-field kf-half">
                <label className="kf-label">✉ Correo Electrónico</label>
                <div className={`kf-input-wrap ${focused === "correo" ? "focused" : ""}`}>
                  <input
                    name="correo" type="email" value={form.correo} onChange={handle}
                    placeholder="juan@ejemplo.com"
                    {...fp("correo")}
                  />
                </div>
              </div>

              <div className="kf-field kf-full">
                <label className="kf-label">📍 Dirección</label>
                <div className={`kf-input-wrap ${focused === "direccion" ? "focused" : ""}`}>
                  <input
                    name="direccion" value={form.direccion} onChange={handle}
                    placeholder="Calle 123 #45-67, Barrio, Ciudad"
                    {...fp("direccion")}
                  />
                </div>
              </div>

            </div>

            <div className="kf-footer">
              <button type="button" className="kf-btn-cancel" onClick={onVolver}>
                ← Cancelar
              </button>
              <button
                type="submit"
                className={`kf-btn-save ${success ? "success" : ""}`}
                disabled={loading || success}
              >
                {success
                  ? "✓ Guardado"
                  : loading
                  ? "Guardando..."
                  : `💾 ${esEdicion ? "Actualizar Cliente" : "Guardar Cliente"}`}
              </button>
            </div>
          </form>
        </div>

        {/* ── Columna derecha ── */}
        <div className="kf-side">

          <div className="kf-preview-card">
            <p className="kf-preview-label">Vista Previa de Ficha</p>
            <div className="kf-preview-body">
              <div
                className="kf-avatar"
                style={iniciales ? {
                  background: "linear-gradient(135deg, #ff3d00, #ff7043)",
                  color: "white",
                  boxShadow: "0 0 28px rgba(255,61,0,0.35)",
                  border: "2px solid rgba(255,61,0,0.3)",
                } : {}}
              >
                {iniciales || "—"}
              </div>
              <h4 className="kf-preview-name">{form.nombre || "Nuevo Cliente"}</h4>
              <span className="kf-preview-badge">
                {esEdicion ? "✦ Edición" : "✦ Nuevo Perfil"}
              </span>
              <div className="kf-preview-fields">
                <div className="kf-preview-row">
                  <span className="kf-preview-ico">📞</span>
                  <span className={form.telefono ? "kf-prow-val" : "kf-prow-muted"}>
                    {form.telefono || "Sin teléfono"}
                  </span>
                </div>
                <div className="kf-preview-row">
                  <span className="kf-preview-ico">✉</span>
                  <span className={form.correo ? "kf-prow-val" : "kf-prow-muted"}>
                    {form.correo || "Sin correo"}
                  </span>
                </div>
                <div className="kf-preview-row">
                  <span className="kf-preview-ico">📍</span>
                  <span className={form.direccion ? "kf-prow-val" : "kf-prow-muted"}>
                    {form.direccion || "Sin dirección"}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="kf-tips-card">
            <div className="kf-tips-h">
              <span className="kf-tips-ico">ℹ</span>
              <span className="kf-tips-title">Consejos de Registro</span>
            </div>
            <ul className="kf-tips-list">
              <li><span className="kf-tip-dot" />Incluya el código de área en el número telefónico.</li>
              <li><span className="kf-tip-dot" />La identificación es el dato clave para historiales técnicos.</li>
              <li><span className="kf-tip-dot" />Puede editar la información del cliente después de crearlo.</li>
            </ul>
          </div>

        </div>
      </div>
    </div>
  );
}

export default ClienteForm;