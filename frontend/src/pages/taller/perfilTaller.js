// Mi taller: qué tan completo está el perfil y validación de lo que el taller edita (sin componentes: se prueba con
// node --test). Los límites son los del backend (DatosTallerService).

export const TARIFA_MAX = 1000000;
export const HORARIO_MAX = 200;

/**
 * Lo que hace completo un perfil, en el orden en que conviene resolverlo. seccion: dónde se arregla dentro de Mi
 * taller; admin: solo el administrador de DriveMaster puede cambiarlo.
 */
const REQUISITOS = [
  { clave: "ubicacion", texto: "Marca tu ubicación en el mapa", seccion: "mt-ubicacion",
    hecho: (t) => t?.latitud != null && t?.longitud != null },
  { clave: "horario", texto: "Escribe tu horario de atención", seccion: "mt-horario", hecho: (t) => !!t?.horario?.trim() },
  { clave: "tarifa", texto: "Define tu tarifa por hora", seccion: "mt-servicios", hecho: (t) => t?.tarifaHora > 0 },
  { clave: "mecanicos", texto: "Registra al menos un mecánico activo", seccion: "mt-equipo",
    hecho: (_t, mecanicosActivos) => mecanicosActivos > 0 },
  { clave: "telefono", texto: "Teléfono de contacto", admin: true, hecho: (t) => !!t?.telefono?.trim() },
  { clave: "direccion", texto: "Dirección escrita", admin: true, hecho: (t) => !!t?.direccion?.trim() },
  { clave: "especialidades", texto: "Servicios que ofreces", admin: true, hecho: (t) => (t?.especialidades || []).length > 0 },
];

/** Porcentaje completo y lo que falta (lo que el taller puede resolver va primero). */
export function completitudPerfil(taller, mecanicosActivos = 0) {
  const items = REQUISITOS.map(({ hecho, ...r }) => ({ ...r, hecho: !!hecho(taller, mecanicosActivos) }));
  const hechos = items.filter((i) => i.hecho).length;
  return {
    porcentaje: Math.round((hechos * 100) / items.length),
    faltan: items.filter((i) => !i.hecho),
  };
}

/** Error de la tarifa por hora, o null si sirve. Vacía no se puede guardar: el backend la deja como estaba. */
export function errorTarifa(texto) {
  if (String(texto ?? "").trim() === "") return "Escribe la tarifa por hora.";
  const valor = Number(texto);
  if (!Number.isFinite(valor)) return "Escribe solo números, sin puntos ni signos.";
  if (valor <= 0) return "La tarifa debe ser mayor que 0.";
  if (valor > TARIFA_MAX) return "La tarifa no puede pasar de $ 1.000.000 por hora.";
  return null;
}

export function errorHorario(texto) {
  return (texto || "").length > HORARIO_MAX ? `El horario admite hasta ${HORARIO_MAX} caracteres.` : null;
}

/**
 * El taller con la forma que recibe el cliente (TallerMapaDTO) a partir de los datos de Mi taller (TallerDTO): así la
 * vista previa usa exactamente los mismos campos que la lista y el mapa de Solicitar diagnóstico.
 */
export function comoLoVeElCliente(t) {
  return {
    id: t.id, nombre: t.nombre, direccion: t.direccion, ciudad: t.ciudad, telefono: t.telefono,
    latitud: t.latitud, longitud: t.longitud, horario: t.horario, especialidades: t.especialidades || [],
    calificacion: t.rating ?? null, calificaciones: t.calificaciones ?? 0,
  };
}

/** Iniciales para el avatar mientras no haya logo: "Mecánica Rápida" → "MR"; "Taller El Pistón" → "P". */
export function iniciales(nombre) {
  const palabras = (nombre || "").split(/\s+/).filter((p) => p && !/^(taller|de|del|la|el|los|las|y)$/i.test(p));
  const base = palabras.length ? palabras : (nombre || "").split(/\s+/).filter(Boolean);
  return base.slice(0, 2).map((p) => p[0].toUpperCase()).join("") || "T";
}
