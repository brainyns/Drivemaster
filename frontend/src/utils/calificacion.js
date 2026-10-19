// Calificación de un taller como la da el backend: rating (promedio de 1 a 5, null sin calificaciones) y
// calificaciones (cuántas). Sin componentes: lo prueban los tests con node --test.

/** Datos para mostrar: nuevo (sin calificaciones), el promedio con una cifra decimal y el texto de la cantidad. */
export function datosCalificacion(rating, calificaciones) {
  const total = Number(calificaciones) || 0;
  if (rating == null || total === 0) {
    return { nuevo: true, promedio: null, texto: "Sin calificaciones aún", etiqueta: "Taller nuevo, sin calificaciones aún" };
  }
  const promedio = Number(rating).toLocaleString("es-CO", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const texto = `${total} ${total === 1 ? "calificación" : "calificaciones"}`;
  return { nuevo: false, promedio, texto, etiqueta: `Calificación ${promedio} de 5, ${texto}` };
}

/**
 * Promedio de los talleres que ya tienen calificaciones, con la misma regla que datosCalificacion: un rating sin
 * calificaciones (un taller "Nuevo") no cuenta. null si ninguno tiene.
 */
export function promedioDeTalleres(talleres) {
  const calificados = (talleres || []).filter((t) => !datosCalificacion(t.rating, t.calificaciones).nuevo);
  if (calificados.length === 0) return null;
  return calificados.reduce((s, t) => s + Number(t.rating), 0) / calificados.length;
}
