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

/**
 * En qué punto está la cuenta de un taller o una tienda: APROBADO (puede operar), PENDIENTE (en revisión), RECHAZADO o
 * INACTIVO (desactivada). Los negocios anteriores al marketplace no tienen estado y cuentan como aprobados. Sin datos
 * (aún cargando o sin perfil) es null.
 */
export function estadoDelNegocio(negocio) {
  if (!negocio) return null;
  const aprobado = !negocio.estadoAprobacion || negocio.estadoAprobacion === "APROBADO";
  if (aprobado && negocio.activo !== false) return "APROBADO";
  if (negocio.estadoAprobacion === "RECHAZADO") return "RECHAZADO";
  if (negocio.estadoAprobacion === "PENDIENTE") return "PENDIENTE";
  return "INACTIVO";
}

/** Lo único que un negocio sin aprobar puede abrir: sus datos, para corregirlos. */
export const PAGINAS_SIN_APROBAR = { TIENDA: ["mi-tienda"], TALLER: ["taller-mitaller"] };

/** El texto del menú sobre un apartado bloqueado. */
export const AVISO_BLOQUEADO = "Disponible cuando aprobemos tu cuenta";

/**
 * Si una página del panel queda cerrada para este negocio: solo para TALLER y TIENDA sin aprobar, y nunca su página de
 * datos. Mientras no se sabe el estado (null) no se bloquea nada: lo decide quien llama (App espera a saberlo).
 */
export function paginaBloqueada(rol, pagina, estado) {
  const permitidas = PAGINAS_SIN_APROBAR[rol];
  if (!permitidas || !estado || estado === "APROBADO") return false;
  return !permitidas.includes(pagina);
}
