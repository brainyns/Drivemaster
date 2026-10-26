// Mi tienda: qué tan completo está el perfil, validación de lo que la tienda edita y cómo la ve el cliente. Sin
// componentes: lo prueban los tests con node --test. Los límites son los del backend (TiendaServiceImpl).

export const COSTO_ENVIO_MAX = 500000;

const lleno = (s) => !!s && String(s).trim() !== "";

/**
 * Lo que hace completo el perfil, en el orden en que conviene resolverlo. seccion: dónde se arregla; catalogo: sin
 * esto los productos no aparecen en el catálogo (ciudad, dirección y horario, como Tienda.tieneDatosCompletos).
 */
const REQUISITOS = [
  { clave: "direccion", texto: "Escribe la ciudad y la dirección", seccion: "mtd-ubicacion", catalogo: true,
    hecho: (t) => lleno(t?.ciudad) && lleno(t?.direccion) },
  { clave: "horario", texto: "Escribe tu horario de atención", seccion: "mtd-horario", catalogo: true, hecho: (t) => lleno(t?.horario) },
  { clave: "ubicacion", texto: "Marca tu tienda en el mapa", seccion: "mtd-ubicacion",
    hecho: (t) => t?.latitud != null && t?.longitud != null },
  { clave: "telefono", texto: "Agrega un teléfono de contacto", seccion: "mtd-contacto", hecho: (t) => lleno(t?.telefono) },
  { clave: "logo", texto: "Sube el logo o una foto de tu tienda", seccion: "mtd-logo", hecho: (t) => lleno(t?.logoUrl) },
];

export function completitudTienda(tienda) {
  const items = REQUISITOS.map(({ hecho, ...r }) => ({ ...r, hecho: !!hecho(tienda) }));
  const hechos = items.filter((i) => i.hecho).length;
  return { porcentaje: Math.round((hechos * 100) / items.length), faltan: items.filter((i) => !i.hecho) };
}

/** Errores de la sección de ubicación (ciudad y dirección son obligatorias para vender). */
export function erroresUbicacion({ ciudad, direccion }) {
  const e = {};
  if (!lleno(ciudad)) e.ciudad = "Escribe la ciudad de tu tienda.";
  if (!lleno(direccion)) e.direccion = "Escribe la dirección: ahí retiran los clientes.";
  return e;
}

export function errorHorarioTienda(horario) {
  return lleno(horario) ? null : "Escribe tu horario: el cliente lo necesita para pasar a retirar.";
}

/** Error del costo de envío, o null. Sin envío no se valida (la tienda solo ofrece retiro). */
export function errorCostoEnvio(ofreceEnvio, costo) {
  if (!ofreceEnvio) return null;
  if (String(costo ?? "").trim() === "") return "Escribe el costo fijo de envío (puede ser 0).";
  const n = Number(costo);
  if (!Number.isFinite(n)) return "Escribe solo números, sin puntos ni signos.";
  if (n < 0 || n > COSTO_ENVIO_MAX) return "El costo de envío debe estar entre $ 0 y $ 500.000.";
  return null;
}

/**
 * La tienda como la recibe el cliente en el catálogo (producto.tienda): mismos campos que usa el detalle del
 * producto en "Vendido por", para que la vista previa sea exactamente lo que se publica.
 */
export function comoLaVeElCliente(t) {
  return {
    id: t.id, nombre: t.nombre, ciudad: t.ciudad, barrio: t.barrio, direccion: t.direccion, horario: t.horario,
    ofreceEnvio: t.costoEnvio != null, costoEnvio: t.costoEnvio ?? null, logoUrl: t.logoUrl ?? null,
  };
}
