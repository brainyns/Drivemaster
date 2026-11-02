// Motivo escrito de una cancelación: el mismo mínimo en todas (cliente, tienda, taller y administrador), igual que el
// backend (util/Motivos.java). Sin componentes: lo prueban los tests.

export const MINIMO_MOTIVO = 10;

/** Cuántos caracteres faltan para el mínimo (0 si ya alcanza). Los espacios a los lados no cuentan. */
export const faltanMotivo = (motivo, minimo = MINIMO_MOTIVO) => Math.max(0, minimo - String(motivo ?? "").trim().length);

/** El error para el campo, o null si el motivo alcanza el mínimo. */
export function errorMotivo(motivo, minimo = MINIMO_MOTIVO) {
  if (!String(motivo ?? "").trim()) return "Escribe el motivo: queda en el historial.";
  const faltan = faltanMotivo(motivo, minimo);
  return faltan > 0 ? `Explica un poco más: mínimo ${minimo} caracteres (te ${faltan === 1 ? "falta 1" : `faltan ${faltan}`}).` : null;
}

/** La ayuda bajo el campo: el mínimo y lo que falta mientras se escribe. */
export function ayudaMotivo(motivo, minimo = MINIMO_MOTIVO) {
  const faltan = faltanMotivo(motivo, minimo);
  return faltan > 0 ? `Mínimo ${minimo} caracteres · ${faltan === 1 ? "falta 1" : `faltan ${faltan}`}.` : `Mínimo ${minimo} caracteres · listo.`;
}
