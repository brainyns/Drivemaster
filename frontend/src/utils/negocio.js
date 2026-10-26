/**
 * Nombre de un negocio con su tipo delante ("Taller AutoMax"), sin repetirlo si el nombre ya lo trae: "Taller los
 * hermanos" queda igual. Igual con el plural ("Tiendas LOS RIOS"). Es la misma regla que NombreNegocio del backend.
 */
export function conPrefijo(prefijo, nombre) {
  const n = (nombre || "").trim();
  if (!n) return prefijo;
  const escapado = prefijo.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^${escapado}(s|es)?(\\s|$)`, "iu").test(n) ? n : `${prefijo} ${n}`;
}
