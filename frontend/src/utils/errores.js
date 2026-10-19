// Errores que responde el backend (GlobalExceptionHandler): { error } o { message }, y { campos } cuando un formulario
// trae el error de cada campo. Sin componentes: lo prueban los tests.

/** El mensaje para la persona, o porDefecto si la respuesta no trae uno. */
export function mensajeDeError(datos, porDefecto) {
  if (!datos || typeof datos !== "object") return porDefecto;
  return datos.error || datos.message || porDefecto;
}

/**
 * Error a partir de una respuesta fallida: con el mensaje real del backend y, si trae errores por campo, en
 * error.campos para mostrarlos en línea. Sin cuerpo (o sin JSON), el mensaje genérico.
 */
export async function errorDeRespuesta(res, porDefecto) {
  let datos = null;
  try { datos = await res.json(); } catch { /* sin cuerpo JSON */ }
  const error = new Error(mensajeDeError(datos, porDefecto));
  if (datos?.campos && typeof datos.campos === "object") error.campos = datos.campos;
  return error;
}
