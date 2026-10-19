import { useEffect, useRef, useState } from "react";
import { registrarTaller } from "../services/tallerService";
import { registrarTienda } from "../services/tiendaService";
import Logo from "../components/Logo";
import SelectorUbicacion from "../components/mapa/SelectorUbicacion";
import Pasos from "../components/ui/Pasos";
import Campo from "../components/ui/Campo";
import CampoClave from "../components/ui/CampoClave";
import Alerta from "../components/ui/Alerta";
import Icon from "../components/public/Icon";
import { formatPrecio } from "../components/servicio/estadosServicio";
import {
  CAMPOS_PASO, NOMBRE_PASO, datosRegistro, erroresDePasos, pasosDe, primerPasoConErrores, vacio,
} from "./pasosRegistroNegocio";
import "../css/Login.css";
import "../css/registro-negocio.css";

const TIPOS = [
  {
    valor: "TALLER",
    titulo: "Taller mecánico",
    detalle: "Recibe solicitudes de diagnóstico de tu zona, cotiza y agenda trabajos.",
    icono: "tool",
  },
  {
    valor: "TIENDA",
    titulo: "Tienda de autopartes",
    detalle: "Publica tu inventario y recibe pedidos de los talleres.",
    icono: "box",
  },
];

const FORM_INICIAL = {
  nombreNegocio: "", nit: "", telefono: "",
  ciudad: "", barrio: "", direccion: "",
  latitud: "", longitud: "",
  especialidades: "", precioManoObra: "", tarifaHora: "", horario: "",
  nombreContacto: "", correo: "", password: "", confirmarPassword: "",
};

// Control que recibe el foco cuando ese campo tiene un error (el mapa: su buscador)
const idDeCampo = (campo) => (campo === "ubicacion" ? "rn-mapa-buscar-input" : `rn-${campo}`);

/** Una fila del resumen final. */
function Dato({ etiqueta, children, vacioTexto = "Sin indicar" }) {
  const sinValor = children == null || children === "";
  return (
    <div className="rn-dato">
      <dt>{etiqueta}</dt>
      <dd className={sinValor ? "rn-dato--vacio" : ""}>{sinValor ? vacioTexto : children}</dd>
    </div>
  );
}

/** Registro público de talleres y tiendas, por pasos y con un resumen antes de enviar. Queda pendiente de aprobación. */
export default function RegistroNegocio({ onIrLogin, onVolver }) {
  const [tipo, setTipo] = useState("TALLER");
  const [form, setForm] = useState(FORM_INICIAL);
  const [indice, setIndice] = useState(0);
  // Pasos en los que ya se intentó continuar: sus errores se muestran y se actualizan al corregir
  const [intentados, setIntentados] = useState([]);
  const [errorServidor, setErrorServidor] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [registrado, setRegistrado] = useState(null);
  const tituloPaso = useRef(null);
  const primerRender = useRef(true);

  const pasos = pasosDe(tipo);
  const paso = pasos[Math.min(indice, pasos.length - 1)];
  const esTaller = tipo === "TALLER";
  const errores = erroresDePasos(tipo, form, intentados);

  // Al cambiar de paso, el foco va a su título (los lectores de pantalla anuncian dónde se está)
  useEffect(() => {
    if (primerRender.current) { primerRender.current = false; return; }
    tituloPaso.current?.focus({ preventScroll: true });
    tituloPaso.current?.closest(".rn-card")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [indice]);

  const cambiar = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const cambiarUbicacion = ({ latitud, longitud }) => setForm((prev) => ({ ...prev, latitud, longitud }));

  // La dirección del pin completa los campos que el negocio aún no escribió (no pisa lo escrito)
  const completarDireccion = ({ direccion, barrio, ciudad }) => {
    setForm((prev) => ({
      ...prev,
      direccion: prev.direccion.trim() || direccion || prev.direccion,
      barrio: prev.barrio.trim() || barrio || prev.barrio,
      ciudad: prev.ciudad.trim() || ciudad || prev.ciudad,
    }));
  };

  const marcarIntentado = (lista) => setIntentados((prev) => [...new Set([...prev, ...lista])]);

  const enfocarError = (erroresPaso, pasoConError) => {
    const campo = CAMPOS_PASO[pasoConError].find((c) => erroresPaso[c]);
    if (campo) document.getElementById(idDeCampo(campo))?.focus();
  };

  const irA = (n) => setIndice(n - 1);

  const continuar = () => {
    const erroresPaso = erroresDePasos(tipo, form, [paso]);
    marcarIntentado([paso]);
    if (Object.keys(erroresPaso).length) {
      enfocarError(erroresPaso, paso);
      return;
    }
    setIndice((i) => i + 1);
  };

  const enviar = async () => {
    setErrorServidor(null);
    const conErrores = primerPasoConErrores(tipo, form);
    if (conErrores) {
      marcarIntentado(pasos);
      setIndice(pasos.indexOf(conErrores));
      return;
    }
    const datos = datosRegistro(tipo, form);
    setEnviando(true);
    try {
      const negocio = esTaller ? await registrarTaller(datos) : await registrarTienda(datos);
      setRegistrado({ nombre: negocio.nombre, correo: datos.correo });
    } catch (err) {
      setErrorServidor(err.message);
    } finally {
      setEnviando(false);
    }
  };

  const alEnviar = (e) => {
    e.preventDefault();
    if (paso === "revisar") enviar();
    else continuar();
  };

  if (registrado) {
    return (
      <div className="auth-page">
        <div className="rn-card rn-card--exito">
          <Logo onClick={onVolver} className="rn-logo" />
          <span className="rn-exito__icono" aria-hidden="true"><Icon name="check" size={28} /></span>
          <h1 className="rn-titulo">¡Listo, {registrado.nombre}!</h1>
          <p className="rn-texto">
            Recibimos tu solicitud. El equipo de DriveMaster validará los datos de tu negocio y te
            avisará a <strong>{registrado.correo}</strong> cuando la cuenta quede aprobada.
          </p>
          <p className="rn-texto">Ya puedes iniciar sesión para ver el estado de tu revisión.</p>
          <button type="button" className="ui-btn ui-btn--primario ui-btn--grande ui-btn--bloque" onClick={onIrLogin}>
            Ir a iniciar sesión
          </button>
        </div>
      </div>
    );
  }

  const campo = (nombre, label, props = {}, extra = {}) => (
    <Campo id={`rn-${nombre}`} label={label} error={errores[nombre]} opcional={extra.opcional}
      ayuda={extra.ayuda} className={extra.ancho ? "rn-ancho" : ""}>
      <input name={nombre} className="ui-field__input" value={form[nombre]} onChange={cambiar} {...props} />
    </Campo>
  );

  const editar = (p) => (
    <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => setIndice(pasos.indexOf(p))}
      aria-label={`Editar ${NOMBRE_PASO[p].toLowerCase()}`}>
      Editar
    </button>
  );

  return (
    <div className="auth-page">
      <div className="rn-card">
        <Logo onClick={onVolver} className="rn-logo" />
        <h1 className="rn-titulo">Registra tu negocio</h1>
        <p className="rn-texto">
          Únete al marketplace que conecta clientes, talleres y tiendas. Revisamos cada registro antes de activarlo.
        </p>

        <Pasos pasos={pasos.map((p) => NOMBRE_PASO[p])} actual={pasos.indexOf(paso) + 1} onIr={irA} id="rn-pasos" />

        <form className="rn-form" onSubmit={alEnviar} noValidate>
          <h2 className="rn-paso__titulo" ref={tituloPaso} tabIndex={-1}>
            {{
              negocio: "Tu negocio",
              ubicacion: "¿Dónde está?",
              servicio: "Tu servicio",
              cuenta: "Cuenta de acceso",
              revisar: "Revisa antes de enviar",
            }[paso]}
          </h2>

          {paso === "negocio" && (
            <>
              <fieldset className="rn-tipos">
                <legend className="rn-legend">Tipo de negocio</legend>
                {TIPOS.map((t) => (
                  <label key={t.valor} className={`rn-tipo${tipo === t.valor ? " rn-tipo--activo" : ""}`}>
                    <input type="radio" name="tipo" className="ui-sr" value={t.valor} checked={tipo === t.valor}
                      onChange={() => setTipo(t.valor)} />
                    <span className="rn-tipo__icono" aria-hidden="true"><Icon name={t.icono} size={20} /></span>
                    <span className="rn-tipo__titulo">{t.titulo}</span>
                    <span className="rn-tipo__detalle">{t.detalle}</span>
                  </label>
                ))}
              </fieldset>
              <div className="rn-grid">
                {campo("nombreNegocio", "Nombre del negocio",
                  { placeholder: esTaller ? "Ej. Taller El Pistón" : "Ej. Autopartes La 80", autoComplete: "organization" },
                  { ancho: true })}
                {campo("nit", "NIT", { placeholder: "900.123.456-7" }, { opcional: true })}
                {campo("telefono", "Teléfono", { type: "tel", inputMode: "tel", placeholder: "300 123 4567", autoComplete: "tel" },
                  { opcional: true })}
              </div>
            </>
          )}

          {paso === "ubicacion" && (
            <div className="rn-grid">
              {campo("ciudad", "Ciudad", { placeholder: "Bogotá D.C.", autoComplete: "address-level2" })}
              {campo("barrio", "Barrio", { placeholder: "Chapinero" }, { opcional: true })}
              {campo("direccion", "Dirección", { placeholder: "Cra 13 # 45-20", autoComplete: "street-address" }, { ancho: true })}
              {campo("horario", "Horario de atención", { placeholder: "L-V 8:00-18:00, S 8:00-13:00" },
                { ancho: true, opcional: esTaller, ayuda: esTaller ? null : "Lo ve el cliente para saber cuándo retirar su pedido." })}
              <div className="rn-ancho ui-field">
                <span className="ui-field__label">
                  {esTaller ? "Ubicación del taller en el mapa" : "Ubicación de la tienda en el mapa"}
                  {!esTaller && <span className="ui-field__opcional">opcional</span>}
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
          )}

          {paso === "servicio" && (
            <div className="rn-grid">
              {campo("precioManoObra", "Mano de obra por servicio (COP)", { type: "number", min: "0", inputMode: "numeric", placeholder: "80000" },
                { ayuda: "El valor base que cobras por un servicio." })}
              {campo("tarifaHora", "Tarifa por hora (COP)", { type: "number", min: "0", inputMode: "numeric", placeholder: "45000" },
                { opcional: true, ayuda: "Se usa al cotizar trabajos por horas." })}
              {campo("especialidades", "Especialidades", { placeholder: "Frenos, Suspensión, Motor" },
                { opcional: true, ancho: true, ayuda: "Sepáralas con comas." })}
            </div>
          )}

          {paso === "cuenta" && (
            <div className="rn-grid">
              {campo("nombreContacto", "Nombre de quien administra", { placeholder: "Tu nombre", autoComplete: "name" },
                { opcional: true, ancho: true })}
              {campo("correo", "Correo", { type: "email", inputMode: "email", placeholder: "negocio@ejemplo.com", autoComplete: "email",
                autoCapitalize: "none", spellCheck: false }, { ancho: true, ayuda: "Te avisamos aquí cuando la cuenta quede aprobada." })}
              <Campo id="rn-password" label="Contraseña" error={errores.password} ayuda="Mínimo 6 caracteres.">
                <CampoClave name="password" value={form.password} onChange={cambiar} autoComplete="new-password" />
              </Campo>
              <Campo id="rn-confirmarPassword" label="Confirmar contraseña" error={errores.confirmarPassword}>
                <CampoClave name="confirmarPassword" value={form.confirmarPassword} onChange={cambiar} autoComplete="new-password" />
              </Campo>
            </div>
          )}

          {paso === "revisar" && (
            <div className="rn-revision">
              <section className="rn-revision__grupo" aria-labelledby="rn-r-negocio">
                <header><h3 id="rn-r-negocio">Negocio</h3>{editar("negocio")}</header>
                <dl>
                  <Dato etiqueta="Tipo">{TIPOS.find((t) => t.valor === tipo).titulo}</Dato>
                  <Dato etiqueta="Nombre">{form.nombreNegocio.trim()}</Dato>
                  <Dato etiqueta="NIT">{form.nit.trim()}</Dato>
                  <Dato etiqueta="Teléfono">{form.telefono.trim()}</Dato>
                </dl>
              </section>
              <section className="rn-revision__grupo" aria-labelledby="rn-r-ubicacion">
                <header><h3 id="rn-r-ubicacion">Ubicación</h3>{editar("ubicacion")}</header>
                <dl>
                  <Dato etiqueta="Dirección">{[form.direccion.trim(), form.barrio.trim(), form.ciudad.trim()].filter(Boolean).join(", ")}</Dato>
                  <Dato etiqueta="Horario">{form.horario.trim()}</Dato>
                  <Dato etiqueta="En el mapa" vacioTexto="Sin marcar">{vacio(form.latitud) ? "" : "Punto marcado"}</Dato>
                </dl>
              </section>
              {esTaller && (
                <section className="rn-revision__grupo" aria-labelledby="rn-r-servicio">
                  <header><h3 id="rn-r-servicio">Servicio</h3>{editar("servicio")}</header>
                  <dl>
                    <Dato etiqueta="Mano de obra">{vacio(form.precioManoObra) ? "" : formatPrecio(Number(form.precioManoObra))}</Dato>
                    <Dato etiqueta="Tarifa por hora">{vacio(form.tarifaHora) ? "" : formatPrecio(Number(form.tarifaHora))}</Dato>
                    <Dato etiqueta="Especialidades">{form.especialidades.trim()}</Dato>
                  </dl>
                </section>
              )}
              <section className="rn-revision__grupo" aria-labelledby="rn-r-cuenta">
                <header><h3 id="rn-r-cuenta">Cuenta de acceso</h3>{editar("cuenta")}</header>
                <dl>
                  <Dato etiqueta="Administra">{form.nombreContacto.trim()}</Dato>
                  <Dato etiqueta="Correo">{form.correo.trim()}</Dato>
                  <Dato etiqueta="Contraseña">{form.password ? "••••••" : ""}</Dato>
                </dl>
              </section>
              <p className="rn-texto rn-revision__nota">
                Al enviar, tu cuenta queda pendiente de aprobación. DriveMaster revisa los datos y te avisa por correo.
              </p>
            </div>
          )}

          {errorServidor && <Alerta>{errorServidor}</Alerta>}

          <div className="rn-navegacion">
            {indice > 0 && (
              <button type="button" className="ui-btn ui-btn--secundario ui-btn--grande" onClick={() => setIndice((i) => i - 1)}
                disabled={enviando}>
                Atrás
              </button>
            )}
            <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande rn-navegacion__principal"
              disabled={enviando} aria-busy={enviando}>
              {paso === "revisar"
                ? (enviando ? "Enviando registro…" : `Registrar ${esTaller ? "taller" : "tienda"}`)
                : <>Continuar <Icon name="arrow" size={16} /></>}
            </button>
          </div>
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
