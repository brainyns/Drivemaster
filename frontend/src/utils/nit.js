// NIT colombiano de talleres y tiendas, con la misma regla del backend (NitColombia): de 6 a 10 dígitos (el de una
// persona natural es su cédula) y, si lo tiene, el dígito de verificación después de un guion. Se aceptan puntos y
// espacios (900.123.456-7) y se envía sin ellos. Sin React, para probarlo con node --test.

export const MENSAJE_NIT = "Escribe el NIT con números y, si lo tienes, el dígito de verificación después de un guion (ej. 900123456-7).";

/** El NIT sin puntos ni espacios ("900123456-7"), o null si no tiene el formato. */
export function normalizarNit(nit) {
  const limpio = String(nit ?? "").trim().replace(/[\s.]/g, "");
  return /^\d{6,10}(-\d)?$/.test(limpio) ? limpio : null;
}

/** El error del campo, o null si está bien. El NIT es obligatorio. */
export function errorNit(nit) {
  if (!String(nit ?? "").trim()) return "Escribe el NIT del negocio.";
  return normalizarNit(nit) ? null : MENSAJE_NIT;
}
