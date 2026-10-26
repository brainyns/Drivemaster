import { useState } from "react";
import { cancelarCambioCorreo, confirmarCambioCorreo, solicitarCambioCorreo } from "../../services/authService";
import {
  INTENTOS_CODIGO, MINUTOS_CODIGO, errorCodigo, errorCorreo, errorTelefono, formatoTelefono, normalizarCorreo,
  normalizarTelefono,
} from "../../pages/contactoNegocio";
import SeccionEditable from "./SeccionEditable";
import Campo from "./Campo";
import Dato from "./Dato";
import PieFormulario from "./PieFormulario";
import Alerta from "./Alerta";
import Icon from "../public/Icon";

const hora = (iso) => new Date(iso).toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" });

function FormTelefono({ id, inicial, ayuda, guardar, onGuardado, onCancelar }) {
  const [telefono, setTelefono] = useState(inicial ? formatoTelefono(inicial) : "");
  const [errorCampo, setErrorCampo] = useState(null);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (e) => {
    e.preventDefault();
    const err = errorTelefono(telefono);
    setErrorCampo(err);
    if (err) return;
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await guardar(normalizarTelefono(telefono)));
    } catch (ex) {
      setError(ex.message);
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <Campo id={`${id}-telefono`} label="Teléfono" error={errorCampo} ayuda={ayuda}>
        <input className="ui-field__input ui-form__corto" type="tel" inputMode="tel" autoComplete="tel" maxLength={20}
          placeholder="300 123 4567" autoFocus value={telefono}
          onChange={(e) => { setTelefono(e.target.value); if (errorCampo) setErrorCampo(null); }} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar teléfono" />
    </form>
  );
}

/** Paso 1: contraseña actual y correo nuevo. El backend revisa que nadie más lo use y envía el código. */
function FormCorreoNuevo({ id, correoActual, inicial, onEnviado, onCancelar }) {
  const [correo, setCorreo] = useState(inicial || "");
  const [password, setPassword] = useState("");
  const [errores, setErrores] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (e) => {
    e.preventDefault();
    const faltan = {};
    const errCorreo = errorCorreo(correo);
    if (errCorreo) faltan.correo = errCorreo;
    else if (normalizarCorreo(correo) === normalizarCorreo(correoActual)) faltan.correo = "Ese ya es el correo de tu cuenta.";
    if (!password) faltan.password = "Escribe tu contraseña actual.";
    setErrores(faltan);
    if (Object.keys(faltan).length) {
      document.getElementById(faltan.correo ? `${id}-correo-nuevo` : `${id}-password`)?.focus();
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      onEnviado(await solicitarCambioCorreo(password, correo));
    } catch (ex) {
      setError(ex.message);
      setGuardando(false);
    }
  };

  const cambiar = (campo, setter) => (e) => {
    setter(e.target.value);
    if (errores[campo]) setErrores((x) => ({ ...x, [campo]: null }));
  };

  return (
    <form className="ui-form ui-contacto__form" onSubmit={enviar} noValidate>
      <p className="ui-contacto__paso">
        Te enviaremos un código de 6 dígitos al correo nuevo. Vence en {MINUTOS_CODIGO} minutos.
      </p>
      <Campo id={`${id}-correo-nuevo`} label="Correo nuevo" error={errores.correo}>
        <input className="ui-field__input" type="email" inputMode="email" autoComplete="email" autoFocus
          placeholder="negocio@ejemplo.com" maxLength={254} value={correo} onChange={cambiar("correo", setCorreo)} />
      </Campo>
      <Campo id={`${id}-password`} label="Contraseña actual" error={errores.password}
        ayuda="La pedimos para confirmar que eres tú.">
        <input className="ui-field__input ui-form__corto" type="password" autoComplete="current-password"
          value={password} onChange={cambiar("password", setPassword)} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Enviar código" />
    </form>
  );
}

/** Paso 2: el código que llegó al correo nuevo. */
function FormCodigo({ id, pendiente, onConfirmado, onOtroCodigo, onCancelar }) {
  const [codigo, setCodigo] = useState("");
  const [errorCampo, setErrorCampo] = useState(null);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const enviar = async (e) => {
    e.preventDefault();
    const err = errorCodigo(codigo);
    setErrorCampo(err);
    if (err) return;
    setGuardando(true);
    setError(null);
    try {
      await confirmarCambioCorreo(codigo);
      onConfirmado(pendiente.correoNuevo);
    } catch (ex) {
      setError(ex.message);
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form ui-contacto__form" onSubmit={enviar} noValidate>
      <p className="ui-contacto__paso" role="status">
        Enviamos un código a <strong className="ui-correo">{pendiente.correoNuevo}</strong>. Vence a
        las {hora(pendiente.vence)} y tienes {INTENTOS_CODIGO} intentos. Si no lo ves, revisa la carpeta de spam.
      </p>
      <Campo id={`${id}-codigo`} label="Código de verificación" error={errorCampo}>
        <input className="ui-field__input ui-contacto__codigo" type="text" inputMode="numeric" autoComplete="one-time-code"
          maxLength={6} placeholder="000000" autoFocus value={codigo}
          onChange={(e) => { setCodigo(e.target.value.replace(/\D/g, "")); if (errorCampo) setErrorCampo(null); }} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Confirmar cambio" />
      <button type="button" className="ui-btn ui-btn--texto ui-btn--chico ui-contacto__otro" onClick={onOtroCodigo}
        disabled={guardando}>
        Pedir otro código o usar otro correo
      </button>
    </form>
  );
}

/**
 * Tarjeta "Contacto" de Mi taller y Mi tienda: el teléfono se edita con "Editar teléfono" (como el resto de
 * tarjetas; editandoTelefono lo controla la página para poder abrirlo desde "lo que falta") y el correo de la cuenta
 * se cambia en dos pasos con un código enviado al correo nuevo. Al confirmarlo, authService guarda la sesión nueva.
 */
export default function SeccionContacto({
  id, descripcion, telefono, correo, ayudaTelefono, guardarTelefono, onTelefonoGuardado,
  editandoTelefono, onEditarTelefono, onCerrarTelefono, onCorreoCambiado, aviso, children,
}) {
  const [paso, setPaso] = useState(null); // null | "correo" | "codigo"
  const [pendiente, setPendiente] = useState(null); // { correoNuevo, vence }
  const [avisoCorreo, setAvisoCorreo] = useState(null);

  const abrirCorreo = () => { setAvisoCorreo(null); setPaso("correo"); };
  const cerrarCorreo = () => {
    if (paso === "codigo") cancelarCambioCorreo().catch(() => {}); // el código pendiente deja de servir
    setPaso(null);
    setPendiente(null);
  };
  const confirmado = (nuevo) => {
    setPaso(null);
    setPendiente(null);
    setAvisoCorreo(`Listo: desde ahora inicias sesión con ${nuevo}. Enviamos un aviso al correo anterior.`);
    onCorreoCambiado?.(nuevo);
  };

  return (
    <SeccionEditable id={id} icono="phone" titulo="Contacto" descripcion={descripcion}
      onEditar={paso ? undefined : onEditarTelefono} textoEditar={telefono ? "Editar teléfono" : "Agregar teléfono"}
      editando={editandoTelefono}
      aviso={aviso || (avisoCorreo && <Alerta tono="ok" onCerrar={() => setAvisoCorreo(null)}>{avisoCorreo}</Alerta>)}>
      {editandoTelefono ? (
        <FormTelefono id={id} inicial={telefono} ayuda={ayudaTelefono} guardar={guardarTelefono}
          onGuardado={onTelefonoGuardado} onCancelar={onCerrarTelefono} />
      ) : (
        <>
          <dl className="ui-datos ui-datos--contacto">
            <Dato etiqueta="Teléfono">{telefono && formatoTelefono(telefono)}</Dato>
            <Dato etiqueta="Correo de la cuenta">{correo && <span className="ui-correo">{correo}</span>}</Dato>
            {children}
          </dl>
          {paso === null && (
            <div className="ui-contacto__acciones">
              <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={abrirCorreo}>
                <Icon name="mail" size={14} /> Cambiar correo
              </button>
              <span className="ui-contacto__nota">Es el correo con el que inicias sesión y recibes los avisos.</span>
            </div>
          )}
          {paso === "correo" && (
            <FormCorreoNuevo id={id} correoActual={correo} inicial={pendiente?.correoNuevo}
              onEnviado={(p) => { setPendiente(p); setPaso("codigo"); }} onCancelar={cerrarCorreo} />
          )}
          {paso === "codigo" && pendiente && (
            <FormCodigo id={id} pendiente={pendiente} onConfirmado={confirmado}
              onOtroCodigo={() => setPaso("correo")} onCancelar={cerrarCorreo} />
          )}
        </>
      )}
    </SeccionEditable>
  );
}
