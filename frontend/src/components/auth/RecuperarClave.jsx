import { useState } from "react";
import { confirmarRecuperacion, solicitarRecuperacion } from "../../services/authService";
import { MENSAJE_SIN_CONEXION } from "../../services/conexion";
import { erroresConfirmacion, erroresSolicitud } from "../../pages/recuperarClave";
import { INTENTOS_CODIGO, MINUTOS_CODIGO } from "../../pages/contactoNegocio";
import Campo from "../ui/Campo";
import CampoClave from "../ui/CampoClave";
import Alerta from "../ui/Alerta";

const enfocar = (errores, orden) => {
  const primero = orden.find((c) => errores[c]);
  if (primero) document.getElementById(`rec-${primero}`)?.focus();
};

/**
 * "¿Olvidaste tu contraseña?" dentro de la tarjeta del login. Paso 1: el correo (el backend responde lo mismo exista o
 * no la cuenta). Paso 2: el código de 6 dígitos y la contraseña nueva. Al terminar vuelve al login con onListo(mensaje,
 * correo).
 */
export default function RecuperarClave({ correoInicial = "", onListo, onVolver }) {
  const [paso, setPaso] = useState("correo"); // "correo" | "codigo"
  const [correo, setCorreo] = useState(correoInicial);
  const [codigo, setCodigo] = useState("");
  const [password, setPassword] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [intentado, setIntentado] = useState(false);
  const [aviso, setAviso] = useState(null); // mensaje del backend tras pedir el código
  const [error, setError] = useState(null);
  const [cargando, setCargando] = useState(false);

  const errores = !intentado ? {} : paso === "correo"
    ? erroresSolicitud(correo) : erroresConfirmacion({ codigo, password, confirmar });

  const pedirCodigo = async (e) => {
    e?.preventDefault();
    setError(null);
    setIntentado(true);
    const faltan = erroresSolicitud(correo);
    if (Object.keys(faltan).length) { enfocar(faltan, ["correo"]); return; }
    setCargando(true);
    try {
      const { mensaje } = await solicitarRecuperacion(correo);
      setAviso(mensaje);
      setPaso("codigo");
      setIntentado(false);
      setCodigo("");
    } catch (err) {
      setError(err.message);
    } finally {
      setCargando(false);
    }
  };

  const cambiarClave = async (e) => {
    e.preventDefault();
    setError(null);
    setIntentado(true);
    const faltan = erroresConfirmacion({ codigo, password, confirmar });
    if (Object.keys(faltan).length) { enfocar(faltan, ["codigo", "password", "confirmar"]); return; }
    setCargando(true);
    try {
      const { mensaje } = await confirmarRecuperacion(correo, codigo, password);
      onListo(mensaje, correo.trim());
    } catch (err) {
      setError(err.message);
      setCargando(false);
    }
  };

  const errorServidor = error && (
    <Alerta onReintentar={error === MENSAJE_SIN_CONEXION ? (paso === "correo" ? pedirCodigo : undefined) : undefined}
      reintentando={cargando}>{error}</Alerta>
  );

  return (
    <>
      <h1>Recupera tu contraseña</h1>
      {paso === "correo" ? (
        <>
          <p className="auth-subtitle">Escribe el correo de tu cuenta y te enviaremos un código de 6 dígitos.</p>
          <form onSubmit={pedirCodigo} noValidate>
            <Campo id="rec-correo" label="Correo electrónico" error={errores.correo}>
              <input className="ui-field__input" type="email" inputMode="email" autoComplete="email" autoCapitalize="none"
                spellCheck={false} placeholder="correo@ejemplo.com" autoFocus value={correo}
                onChange={(e) => setCorreo(e.target.value)} />
            </Campo>
            {errorServidor}
            <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande ui-btn--bloque auth-enviar"
              disabled={cargando} aria-busy={cargando}>
              {cargando ? "Enviando…" : "Enviar código"}
            </button>
          </form>
        </>
      ) : (
        <>
          <p className="auth-subtitle">Escribe el código y tu contraseña nueva. Al cambiarla cerramos tus sesiones abiertas.</p>
          {aviso && <Alerta tono="info">{aviso}</Alerta>}
          <form onSubmit={cambiarClave} noValidate className="auth-recuperar">
            <Campo id="rec-codigo" label="Código de verificación" error={errores.codigo}
              ayuda={`Lo enviamos a ${correo.trim()}. Vence en ${MINUTOS_CODIGO} minutos y tienes ${INTENTOS_CODIGO} intentos.`}>
              <input className="ui-field__input auth-codigo" type="text" inputMode="numeric" autoComplete="one-time-code"
                maxLength={6} placeholder="000000" autoFocus value={codigo}
                onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ""))} />
            </Campo>
            <Campo id="rec-password" label="Contraseña nueva" error={errores.password} ayuda="Mínimo 6 caracteres.">
              <CampoClave value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
            </Campo>
            <Campo id="rec-confirmar" label="Repite la contraseña nueva" error={errores.confirmar}>
              <CampoClave value={confirmar} onChange={(e) => setConfirmar(e.target.value)} autoComplete="new-password" />
            </Campo>
            {errorServidor}
            <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande ui-btn--bloque auth-enviar"
              disabled={cargando} aria-busy={cargando}>
              {cargando ? "Guardando…" : "Cambiar contraseña"}
            </button>
          </form>
          <p className="auth-footer">
            ¿No te llegó?{" "}
            <button type="button" className="link-button" onClick={() => pedirCodigo()} disabled={cargando}>
              Pedir otro código
            </button>
            {" · "}
            <button type="button" className="link-button" disabled={cargando}
              onClick={() => { setPaso("correo"); setError(null); setIntentado(false); }}>
              Usar otro correo
            </button>
          </p>
        </>
      )}
      <p className="auth-footer">
        <button type="button" className="link-button" onClick={onVolver}>Volver a iniciar sesión</button>
      </p>
    </>
  );
}
