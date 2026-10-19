// Filtros de "Mis servicios": en curso y finalizadas. Funciones puras.

/** Estados en los que el servicio ya terminó (entregado, sin taller o cancelado). */
export const ESTADOS_FINALES = ["ENTREGADA", "RECHAZADA", "CANCELADA"];

/** Con más solicitudes que esto se muestran los filtros; con pocas no hacen falta. */
export const MINIMO_PARA_FILTRAR = 4;

export const FILTROS_SERVICIOS = [
  { id: "todas", label: "Todas" },
  { id: "enCurso", label: "En curso" },
  { id: "finalizadas", label: "Finalizadas" },
];

const coincide = (filtro, s) => {
  if (filtro === "enCurso") return !ESTADOS_FINALES.includes(s.estado);
  if (filtro === "finalizadas") return ESTADOS_FINALES.includes(s.estado);
  return true;
};

export const filtrarServicios = (solicitudes, filtro) => (solicitudes || []).filter((s) => coincide(filtro, s));

/** Cuántas hay en cada filtro, para mostrarlo en el chip. */
export const contarServicios = (solicitudes) =>
  Object.fromEntries(FILTROS_SERVICIOS.map((f) => [f.id, filtrarServicios(solicitudes, f.id).length]));
