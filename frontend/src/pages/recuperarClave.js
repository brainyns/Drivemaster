// "¿Olvidaste tu contraseña?": validación de los dos pasos. Sin componentes: lo prueban los tests con node --test.
// Las reglas son las del backend (RecuperacionClaveService), que son las mismas del registro.
import { errorCodigo, errorCorreo } from "./contactoNegocio.js";

export const CLAVE_MIN = 6;
export const CLAVE_MAX = 128;

export function erroresSolicitud(correo) {
  const e = {};
  const err = errorCorreo(correo);
  if (err) e.correo = err === "Escribe el correo nuevo." ? "Escribe el correo de tu cuenta." : err;
  return e;
}

/** Errores del paso 2, en el orden en que se recorren con el foco. */
export function erroresConfirmacion({ codigo, password, confirmar }) {
  const e = {};
  const errCodigo = errorCodigo(codigo);
  if (errCodigo) e.codigo = errCodigo;
  if (!password || password.length < CLAVE_MIN) e.password = `Mínimo ${CLAVE_MIN} caracteres.`;
  else if (password.length > CLAVE_MAX) e.password = `Máximo ${CLAVE_MAX} caracteres.`;
  if (confirmar !== password) e.confirmar = "Las contraseñas no coinciden.";
  return e;
}
