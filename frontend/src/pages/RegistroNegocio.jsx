import { useState } from "react";
import { registrarTaller } from "../services/tallerService";
import { registrarTienda } from "../services/tiendaService";
import Logo from "../components/Logo";
import SelectorUbicacion from "../components/mapa/SelectorUbicacion";
import "../css/Login.css";
import "../css/registro-negocio.css";

const TIPOS = [
  {
    valor: "TALLER",
    titulo: "Taller mecánico",
    detalle: "Recibe solicitudes de diagnóstico de tu zona, cotiza y agenda trabajos.",
  },
  {
    valor: "TIENDA",
    titulo: "Tienda de autopartes",
    detalle: "Publica tu inventario y recibe pedidos de los talleres.",
  },
];

const FORM_INICIAL = {
  nombreNegocio: "", nit: "", telefono: "",
  ciudad: "", barrio: "", direccion: "",
  latitud: "", longitud: "",
  especialidades: "", precioManoObra: "", tarifaHora: "", horario: "",
  nombreContacto: "", correo: "", password: "", confirmarPassword: "",
};

const vacio = (v) => v === null || v === undefined || String(v).trim() === "";

function validar(tipo, f) {
  const e = {};
  if (vacio(f.nombreNegocio)) e.nombreNegocio = "Escribe el nombre del negocio.";
  if (vacio(f.ciudad)) e.ciudad = "Indica la ciudad.";
  if (vacio(f.direccion)) e.direccion = "Indica la dirección.";
  // La tienda vende con retiro en su local: el horario es obligatorio (el servidor también lo exige)
  if (tipo === "TIENDA" && vacio(f.horario)) e.horario = "Indica el horario de atención: lo necesita el cliente para retirar.";
  if (!vacio(f.telefono) && !/^[0-9+\s()-]{7,20}$/.test(f.telefono.trim())) e.telefono = "Teléfono no válido.";
  if (tipo === "TALLER") {
    const precio = Number(f.precioManoObra);
    if (vacio(f.precioManoObra) || isNaN(precio) || precio <= 0) e.precioManoObra = "Indica el valor de tu mano de obra.";
    if (!vacio(f.tarifaHora) && (isNaN(Number(f.tarifaHora)) || Number(f.tarifaHora) <= 0)) e.tarifaHora = "Tarifa no válida.";
    // Con la ubicación el taller aparece en el mapa del cliente
    if (vacio(f.latitud) || vacio(f.longitud)) e.ubicacion = "Marca la ubicación de tu taller en el mapa.";
  }
  if (vacio(f.correo) || !/^\S+@\S+\.\S+$/.test(f.correo.trim())) e.correo = "Escribe un correo válido.";
  if (vacio(f.password) || f.password.length < 6) e.password = "Mínimo 6 caracteres.";
  if (f.confirmarPassword !== f.password) e.confirmarPassword = "Las contraseñas no coinciden.";
  return e;
}

function Campo({ id, label, error, opcional, ancho, children }) {
  return (
    <div className={`rn-campo${ancho ? " rn-campo--ancho" : ""}`}>
      <label className="rn-campo__label" htmlFor={id}>
        {label}
        {opcional && <span className="rn-campo__opcional">opcional</span>}
      </label>
      {children}
      {error && <span className="rn-campo__error">{error}</span>}
    </div>
  );
}

/** Registro público de talleres y tiendas. La cuenta queda pendiente de aprobación. */
export default function RegistroNegocio({ onIrLogin, onVolver }) {
  const [tipo, setTipo] = useState("TALLER");
  const [form, setForm] = useState(FORM_INICIAL);
  const [errores, setErrores] = useState({});
  const [errorServidor, setErrorServidor] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [registrado, setRegistrado] = useState(null);

  const cambiar = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
    if (errores[name]) setErrores((prev) => ({ ...prev, [name]: undefined }));
  };

  const cambiarUbicacion = ({ latitud, longitud }) => {
    setForm((prev) => ({ ...prev, latitud, longitud }));
    if (errores.ubicacion) setErrores((prev) => ({ ...prev, ubicacion: undefined }));
  };

  // La dirección del pin completa los campos que el negocio aún no escribió (no pisa lo escrito)
  const completarDireccion = ({ direccion, barrio, ciudad }) => {
    setForm((prev) => ({
      ...prev,
      direccion: prev.direccion.trim() || direccion || prev.direccion,
      barrio: prev.barrio.trim() || barrio || prev.barrio,
      ciudad: prev.ciudad.trim() || ciudad || prev.ciudad,
    }));
  };

  const enviar = async (e) => {
    e.preventDefault();
    setErrorServidor(null);
    const errs = validar(tipo, form);
    setErrores(errs);
    if (Object.keys(errs).length > 0) return;

    const numero = (v) => (vacio(v) ? null : Number(v));
    const datos = {
      nombreNegocio: form.nombreNegocio.trim(),
      nit: form.nit.trim() || null,
      telefono: form.telefono.trim() || null,
      ciudad: form.ciudad.trim(),
      barrio: form.barrio.trim() || null,
      direccion: form.direccion.trim(),
      latitud: numero(form.latitud),
      longitud: numero(form.longitud),
      nombreContacto: form.nombreContacto.trim() || null,
      correo: form.correo.trim(),
      password: form.password,
    };
    if (tipo === "TALLER") {
      datos.especialidades = form.especialidades.split(",").map((s) => s.trim()).filter(Boolean);
      datos.precioManoObra = numero(form.precioManoObra);
      datos.tarifaHora = numero(form.tarifaHora);
    }
    datos.horario = form.horario.trim() || null;

    setEnviando(true);
    try {
      const negocio = tipo === "TALLER" ? await registrarTaller(datos) : await registrarTienda(datos);
      setRegistrado({ nombre: negocio.nombre, correo: datos.correo });
    } catch (err) {
      setErrorServidor(err.message);
    } finally {
      setEnviando(false);
    }
  };

  if (registrado) {
    return (
      <div className="auth-page">
        <div className="rn-card rn-card--exito">
          <Logo onClick={onVolver} className="rn-logo" />
          <span className="rn-eyebrow">Registro enviado</span>
          <h1 className="rn-titulo">¡Listo, {registrado.nombre}!</h1>
          <p className="rn-texto">
            Recibimos tu solicitud. El equipo de DriveMaster validará los datos de tu negocio y te
            avisará a <strong>{registrado.correo}</strong> cuando la cuenta quede aprobada.
          </p>
          <p className="rn-texto">
            Ya puedes iniciar sesión para ver el estado de tu revisión.
          </p>
          <button type="button" className="rn-boton" onClick={onIrLogin}>Ir a iniciar sesión</button>
        </div>
      </div>
    );
  }

  const esTaller = tipo === "TALLER";

  return (
    <div className="auth-page">
      <div className="rn-card">
        <Logo onClick={onVolver} className="rn-logo" />
        <span className="rn-eyebrow">DriveMaster para negocios</span>
        <h1 className="rn-titulo">Registra tu negocio</h1>
        <p className="rn-texto">
          Únete al marketplace que conecta clientes, talleres y tiendas. Revisamos cada registro
          antes de activarlo.
        </p>

        <form className="rn-form" onSubmit={enviar} noValidate>
          <fieldset className="rn-tipos">
            <legend className="rn-seccion">Tipo de negocio</legend>
            {TIPOS.map((t) => (
              <label key={t.valor} className={`rn-tipo${tipo === t.valor ? " rn-tipo--activo" : ""}`}>
                <input
                  type="radio"
                  name="tipo"
                  value={t.valor}
                  checked={tipo === t.valor}
                  onChange={() => setTipo(t.valor)}
                />
                <span className="rn-tipo__titulo">{t.titulo}</span>
                <span className="rn-tipo__detalle">{t.detalle}</span>
              </label>
            ))}
          </fieldset>

          <h2 className="rn-seccion">Datos del negocio</h2>
          <div className="rn-grid">
            <Campo id="rn-nombre" label="Nombre del negocio" error={errores.nombreNegocio} ancho>
              <input id="rn-nombre" name="nombreNegocio" className="rn-input" value={form.nombreNegocio} onChange={cambiar}
                placeholder={esTaller ? "Ej. Taller El Pistón" : "Ej. Autopartes La 80"} />
            </Campo>
            <Campo id="rn-nit" label="NIT" opcional>
              <input id="rn-nit" name="nit" className="rn-input" value={form.nit} onChange={cambiar} placeholder="900.123.456-7" />
            </Campo>
            <Campo id="rn-telefono" label="Teléfono" error={errores.telefono} opcional>
              <input id="rn-telefono" name="telefono" type="tel" className="rn-input" value={form.telefono} onChange={cambiar} placeholder="300 123 4567" />
            </Campo>
          </div>

          <h2 className="rn-seccion">Ubicación</h2>
          <div className="rn-grid">
            <Campo id="rn-ciudad" label="Ciudad" error={errores.ciudad}>
              <input id="rn-ciudad" name="ciudad" className="rn-input" value={form.ciudad} onChange={cambiar} placeholder="Bogotá D.C." />
            </Campo>
            <Campo id="rn-barrio" label="Barrio" opcional>
              <input id="rn-barrio" name="barrio" className="rn-input" value={form.barrio} onChange={cambiar} placeholder="Chapinero" />
            </Campo>
            <Campo id="rn-direccion" label="Dirección" error={errores.direccion} ancho>
              <input id="rn-direccion" name="direccion" className="rn-input" value={form.direccion} onChange={cambiar} placeholder="Cra 13 # 45-20" />
            </Campo>
            <Campo id="rn-horario" label="Horario de atención" error={errores.horario} opcional={esTaller} ancho>
              <input id="rn-horario" name="horario" className="rn-input" value={form.horario} onChange={cambiar} placeholder="L-V 8:00-18:00, S 8:00-13:00" />
            </Campo>
            <div className="rn-campo rn-campo--ancho">
              <span className="rn-campo__label">
                {esTaller ? "Ubicación del taller en el mapa" : "Ubicación de la tienda en el mapa"}
                {!esTaller && <span className="rn-campo__opcional">opcional</span>}
              </span>
              <SelectorUbicacion
                id="rn-mapa"
                ciudad={form.ciudad}
                value={vacio(form.latitud) ? null : { latitud: Number(form.latitud), longitud: Number(form.longitud) }}
                onChange={cambiarUbicacion}
                onDireccion={completarDireccion}
                error={errores.ubicacion}
                ayuda={esTaller
                  ? "Busca la dirección del taller, usa tu ubicación o toca el mapa. Así te llegan las solicitudes cercanas."
                  : "Busca la dirección de la tienda, usa tu ubicación o toca el mapa para marcar el punto exacto."}
              />
            </div>
          </div>

          {esTaller && (
            <>
              <h2 className="rn-seccion">Servicio</h2>
              <div className="rn-grid">
                <Campo id="rn-precio" label="Mano de obra por servicio (COP)" error={errores.precioManoObra}>
                  <input id="rn-precio" name="precioManoObra" type="number" min="0" className="rn-input" value={form.precioManoObra} onChange={cambiar} placeholder="80000" />
                </Campo>
                <Campo id="rn-tarifa" label="Tarifa por hora (COP)" error={errores.tarifaHora} opcional>
                  <input id="rn-tarifa" name="tarifaHora" type="number" min="0" className="rn-input" value={form.tarifaHora} onChange={cambiar} placeholder="45000" />
                </Campo>
                <Campo id="rn-especialidades" label="Especialidades" opcional ancho>
                  <input id="rn-especialidades" name="especialidades" className="rn-input" value={form.especialidades} onChange={cambiar} placeholder="Frenos, Suspensión, Motor (separadas por coma)" />
                </Campo>
              </div>
            </>
          )}

          <h2 className="rn-seccion">Cuenta de acceso</h2>
          <div className="rn-grid">
            <Campo id="rn-contacto" label="Nombre de quien administra" opcional ancho>
              <input id="rn-contacto" name="nombreContacto" className="rn-input" value={form.nombreContacto} onChange={cambiar} placeholder="Tu nombre" />
            </Campo>
            <Campo id="rn-correo" label="Correo" error={errores.correo} ancho>
              <input id="rn-correo" name="correo" type="email" className="rn-input" value={form.correo} onChange={cambiar} placeholder="negocio@ejemplo.com" autoComplete="email" />
            </Campo>
            <Campo id="rn-password" label="Contraseña" error={errores.password}>
              <input id="rn-password" name="password" type="password" className="rn-input" value={form.password} onChange={cambiar} placeholder="Mínimo 6 caracteres" autoComplete="new-password" />
            </Campo>
            <Campo id="rn-confirmar" label="Confirmar contraseña" error={errores.confirmarPassword}>
              <input id="rn-confirmar" name="confirmarPassword" type="password" className="rn-input" value={form.confirmarPassword} onChange={cambiar} autoComplete="new-password" />
            </Campo>
          </div>

          {errorServidor && <p className="auth-error" role="alert">{errorServidor}</p>}

          <button type="submit" className="rn-boton" disabled={enviando}>
            {enviando ? "Enviando registro..." : `Registrar ${esTaller ? "taller" : "tienda"}`}
          </button>
        </form>

        <p className="auth-footer">
          ¿Ya tienes cuenta?{" "}
          <button type="button" className="link-button" onClick={onIrLogin}>Inicia sesión</button>
        </p>
        {onVolver && (
          <button type="button" className="auth-back" onClick={onVolver}>
            <svg viewBox="0 0 24 24">
              <line x1="20" y1="12" x2="5" y2="12" />
              <polyline points="11 18 5 12 11 6" />
            </svg>
            Volver al catálogo
          </button>
        )}
      </div>
    </div>
  );
}
