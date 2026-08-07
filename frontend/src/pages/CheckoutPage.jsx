import { useState, useEffect, useRef } from "react";
import { getToken, getUser } from "../services/authService";
import { crearSolicitud, crearPago, getWompiRedirectUrl } from "../services/solicitudService";
import "../css/checkout.css";
import "../css/clientes.css";

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

const API = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

function headers(token) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

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

export default function CheckoutPage({
  onCompraExitosa, onVolver,
  solicitudItems, solicitudSubtotal, solicitudIva, solicitudTotal, onClearSolicitud, onRemoveProductList
}) {
  const user = getUser();
  const token = getToken();
  const [cliente, setCliente] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [metodoPago, setMetodoPago] = useState("WOMPI");
  const [procesando, setProcesando] = useState(false);
  const [mensaje, setMensaje] = useState(null);

  const [formData, setFormData] = useState({
    identificacion: "", telefono: "", direccion: "", ciudad: "", region: "", referencia: ""
  });

  useEffect(() => {
    const cargar = async () => {
      try {
        const perfilRes = await fetch(`${API}/clientes/mi-perfil`, { headers: headers(token) });
        if (perfilRes.ok) {
          const perfil = await perfilRes.json();
          if (perfil.id) {
            setCliente(perfil);
            setFormData({
              identificacion: perfil.identificacion || "",
              telefono: perfil.telefono || "",
              direccion: perfil.direccion || "",
              ciudad: perfil.ciudad || "",
              region: perfil.region || "",
              referencia: perfil.referencia || ""
            });
          }
        }
      } catch (e) {
        console.error(e);
      } finally {
        setCargando(false);
      }
    };
    cargar();
  }, []);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const guardarPerfil = async () => {
    const res = await fetch(`${API}/clientes/mi-perfil`, {
      method: "PUT",
      headers: headers(token),
      body: JSON.stringify(formData)
    });
    if (!res.ok) throw new Error("Error al guardar perfil");
    return res.json();
  };

  const stockItems = solicitudItems.filter(i => i.tipo === "STOCK" || !i.tipo);
  const encargoItems = solicitudItems.filter(i => i.tipo === "ENCARGO");

  const stockSubtotal = stockItems.reduce((s, i) => s + (i.precioUnitario * i.cantidad), 0);
  const encargoSubtotal = encargoItems.reduce((s, i) => s + (i.precioUnitario * i.cantidad), 0);

  const ejecutarCompra = async () => {
    setProcesando(true);
    setMensaje(null);
    try {
      await guardarPerfil();
      const productos = stockItems.map(i => ({ productoId: i.productoId, cantidad: i.cantidad }));
      const solicitud = await crearSolicitud(token, productos, metodoPago);

      if (metodoPago === "WOMPI") {
        const redirectUrl = getWompiRedirectUrl();
        const pagoData = await crearPago(token, solicitud.id, redirectUrl);
        console.log("checkoutUrl:", pagoData.checkoutUrl);
        window.location.href = pagoData.checkoutUrl;
        return;
      }

      const ids = stockItems.map(i => i.productoId);
      onRemoveProductList && onRemoveProductList(ids);
      setMensaje({ tipo: "exito", texto: "Solicitud enviada. Te contactaremos para coordinar el pago." });
      if (encargoItems.length === 0) {
        setTimeout(() => onCompraExitosa && onCompraExitosa(), 3000);
      }
      setProcesando(false);
    } catch (e) {
      setMensaje({ tipo: "error", texto: e.message });
      setProcesando(false);
    }
  };

  const ejecutarSolicitud = async () => {
    setProcesando(true);
    setMensaje(null);
    try {
      await guardarPerfil();
      const productos = encargoItems.map(i => ({ productoId: i.productoId, cantidad: i.cantidad }));
      await crearSolicitud(token, productos, metodoPago);
      const ids = encargoItems.map(i => i.productoId);
      onRemoveProductList && onRemoveProductList(ids);
      setMensaje({ tipo: "exito", texto: "Solicitud enviada. El administrador la revisará pronto." });
      if (stockItems.length === 0) {
        setTimeout(() => onCompraExitosa && onCompraExitosa(), 3000);
      }
    } catch (e) {
      setMensaje({ tipo: "error", texto: e.message });
    } finally {
      setProcesando(false);
    }
  };

  if (cargando) return <div className="checkout-loading">Cargando...</div>;

  const necesitaDatos = !cliente?.telefono || !cliente?.direccion || !cliente?.ciudad || !cliente?.region;

  return (
    <div className="checkout-page">
      <div className="checkout-header">
        <h1>Enviar solicitud</h1>
        {onVolver && <button className="checkout-volver" onClick={onVolver}>← Volver</button>}
      </div>

      {mensaje && (
        <div className={`checkout-mensaje ${mensaje.tipo}`}>
          {mensaje.texto}
        </div>
      )}

      <div className={`checkout-grid ${procesando ? "checkout-procesando-overlay" : ""}`}>
        <div className="checkout-form-section">
          <h2>Datos de envío</h2>
          {necesitaDatos && <p className="checkout-hint">Complete sus datos para continuar</p>}

          <div className="checkout-field">
            <label>Número de identificación</label>
            <input name="identificacion" value={formData.identificacion} onChange={handleChange} placeholder="CC / NIT" disabled={procesando} />
          </div>
          <div className="checkout-field">
            <label>Teléfono</label>
            <PhoneSelector
              value={formData.telefono}
              onChange={val => setFormData(f => ({ ...f, telefono: val }))}
            />
          </div>
          <div className="checkout-field">
            <label>Dirección</label>
            <input name="direccion" value={formData.direccion} onChange={handleChange} placeholder="Calle 123 #45-67" disabled={procesando} />
          </div>
          <div className="checkout-field">
            <label>Ciudad</label>
            <input name="ciudad" value={formData.ciudad} onChange={handleChange} placeholder="Bogotá" disabled={procesando} />
          </div>
          <div className="checkout-field">
            <label>Región / Departamento</label>
            <input name="region" value={formData.region} onChange={handleChange} placeholder="Cundinamarca" disabled={procesando} />
          </div>
          <div className="checkout-field">
            <label>Referencia de entrega</label>
            <input name="referencia" value={formData.referencia} onChange={handleChange} placeholder="Casa blanca, portón verde" disabled={procesando} />
          </div>
        </div>

        <div className="checkout-resumen-section">
          <h2>Resumen</h2>

          {stockItems.length > 0 && (
            <>
              <h3 className="checkout-section-subtitle">
                <span className="checkout-section-badge stock">Compra inmediata</span>
              </h3>
              {stockItems.map(item => (
                <div key={item.productoId} className="checkout-resumen-item">
                  <span>{item.nombre} x{item.cantidad}</span>
                  <span>{formatPrecio(item.precioUnitario * item.cantidad)}</span>
                </div>
              ))}
            </>
          )}

          {encargoItems.length > 0 && (
            <>
              <h3 className="checkout-section-subtitle">
                <span className="checkout-section-badge encargo">Solicitud de encargo</span>
              </h3>
              {encargoItems.map(item => (
                <div key={item.productoId} className="checkout-resumen-item">
                  <span>{item.nombre} x{item.cantidad}</span>
                  <span>{formatPrecio(item.precioUnitario * item.cantidad)}</span>
                </div>
              ))}
            </>
          )}

          <div className="checkout-resumen-line">
            <span>Subtotal</span>
            <span>{formatPrecio(solicitudSubtotal)}</span>
          </div>
          <div className="checkout-resumen-line">
            <span>IVA</span>
            <span>{formatPrecio(solicitudIva)}</span>
          </div>
          <div className="checkout-resumen-line total">
            <span>Total</span>
            <span>{formatPrecio(solicitudTotal)}</span>
          </div>

          <h2 style={{ marginTop: "24px" }}>Método de pago</h2>
          <select value={metodoPago} onChange={(e) => setMetodoPago(e.target.value)} className="checkout-select" disabled={procesando}>
            <option value="WOMPI">Pago en línea (Wompi)</option>
            <option value="TRANSFERENCIA">Transferencia bancaria</option>
            <option value="EFECTIVO">Efectivo</option>
          </select>

          {stockItems.length > 0 && (
            <button
              className={`checkout-btn ${procesando ? "checkout-btn-loading" : ""}`}
              onClick={ejecutarCompra}
              disabled={procesando || stockItems.length === 0}
            >
              {procesando ? <><span className="checkout-spinner" /> Procesando...</> : "Pagar ahora"}
            </button>
          )}

          {encargoItems.length > 0 && (
            <button
              className={`checkout-btn ${procesando ? "checkout-btn-loading" : ""}`}
              onClick={ejecutarSolicitud}
              disabled={procesando || encargoItems.length === 0}
            >
              {procesando ? <><span className="checkout-spinner" /> Enviando...</> : "Enviar solicitud"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
