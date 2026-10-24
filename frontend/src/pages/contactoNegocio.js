// Tarjeta "Contacto" de Mi taller y Mi tienda: teléfono colombiano, correo de la cuenta y el código de verificación.
// Sin componentes: lo prueban los tests con node --test. Las reglas son las del backend (TelefonoColombia y Correos).

export const MENSAJE_TELEFONO = "Escribe un celular de 10 dígitos que empiece por 3 (ej. 300 123 4567) "
  + "o un fijo de 10 dígitos que empiece por 60 (ej. 601 234 5678).";

export const MINUTOS_CODIGO = 15;
export const INTENTOS_CODIGO = 5;

/** Correo sin espacios y en minúsculas: así se guarda y así se busca al iniciar sesión. */
export const normalizarCorreo = (correo) => String(correo ?? "").replace(/\s+/g, "").toLowerCase();

export function errorCorreo(correo) {
  const c = normalizarCorreo(correo);
  if (!c) return "Escribe el correo nuevo.";
  if (c.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(c)) return "Revisa el correo: debe tener la forma nombre@dominio.com.";
  return null;
}

/**
 * Los 10 dígitos de un celular (3…) o un fijo (60 + indicativo 1 a 8) de Colombia, o null. Acepta espacios,
 * guiones, puntos, paréntesis y el prefijo +57.
 */
export function normalizarTelefono(telefono) {
  let d = String(telefono ?? "").trim().replace(/[\s().-]/g, "");
  if (d.startsWith("+57")) d = d.slice(3);
  else if (d.length === 12 && d.startsWith("57")) d = d.slice(2);
  return /^(3\d{9}|60[1-8]\d{7})$/.test(d) ? d : null;
}

export function errorTelefono(telefono) {
  if (!String(telefono ?? "").trim()) return "Escribe el número de teléfono.";
  return normalizarTelefono(telefono) ? null : MENSAJE_TELEFONO;
}

/** "3001234567" → "300 123 4567"; lo que no sea un número válido se muestra tal cual. */
export function formatoTelefono(telefono) {
  const d = normalizarTelefono(telefono);
  return d ? `${d.slice(0, 3)} ${d.slice(3, 6)} ${d.slice(6)}` : telefono;
}

export function errorCodigo(codigo) {
  const c = String(codigo ?? "").replace(/\s+/g, "");
  if (!c) return "Escribe el código que te enviamos.";
  return /^\d{6}$/.test(c) ? null : "El código tiene 6 números.";
}
