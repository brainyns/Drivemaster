// Errores que responde el backend (GlobalExceptionHandler): { error } o { message }, y { campos } cuando un formulario
// trae el error de cada campo. Sin componentes: lo prueban los tests.

/**
 * El mensaje para la persona, o porDefecto si la respuesta no trae uno. "Stock insuficiente" llega con el producto y
 * lo disponible (StockInsuficienteException): se arma la frase completa.
 */
export function mensajeDeError(datos, porDefecto) {
  if (!datos || typeof datos !== "object") return porDefecto;
  if (datos.error === "Stock insuficiente" && datos.producto) {
    const n = Number(datos.stockDisponible) || 0;
    return `Stock insuficiente: de “${datos.producto}” ${n === 1 ? "queda 1 unidad" : `quedan ${n} unidades`}.`;
  }
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

/** Lo que se muestra cuando el error no trae un mensaje que la persona pueda entender. */
export const MENSAJE_GENERICO = "Algo salió mal. Inténtalo de nuevo en un momento.";

/** Pagos: además de lo que pasó, la tranquilidad de que no se cobró nada. */
export const AVISO_COBRO = "Si el cobro no se aplica, no se hará ningún cargo.";

// Textos del navegador, de JavaScript o del servidor que no son para la persona: "Failed to fetch", "Unexpected
// token < in JSON", "HTTP 500", "Cannot read properties of undefined", nombres de clases de Java…
const TECNICO = new RegExp([
  /failed to fetch|networkerror|load failed|\bjson\b|unexpected (token|end)|is not (a function|defined|valid)/,
  /cannot read|\bundefined\b|\bnull\b|typeerror|syntaxerror|referenceerror|exception|stack ?trace/,
  /status code|^http\b|\bhttp \d{3}\b|^error:? ?\d{3}\b|^\d{3}\b|internal server error|bad request/,
  /\bnot found\b|forbidden|unauthorized|bad gateway|\btimeout\b|\baborted?\b|\bjava\.|\borg\./,
  /springframework|\bsql|mongo|could not|econn|err_/,
].map((r) => r.source).join("|"), "i");

/**
 * El texto de un error listo para una Alerta: el mensaje del backend o de la app cuando es una frase para la persona,
 * o porDefecto cuando es un texto técnico, vacío o demasiado largo. Nunca devuelve el message crudo de un fallo técnico.
 */
export function textoError(e, porDefecto = MENSAJE_GENERICO) {
  const m = typeof e === "string" ? e : e?.message;
  if (typeof m !== "string") return porDefecto;
  const t = m.trim();
  if (!t || t.length > 240 || TECNICO.test(t)) return porDefecto;
  return t;
}

/** Igual que textoError, para los pagos: siempre termina con el aviso de que no se hizo ningún cargo. */
export function textoErrorPago(e, porDefecto = "No pudimos iniciar el pago.") {
  const t = textoError(e, porDefecto);
  return `${/[.!?]$/.test(t) ? t : `${t}.`} ${AVISO_COBRO}`;
}
