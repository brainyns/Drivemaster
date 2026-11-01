// Reglas de los formularios del mecánico, con los mismos límites del backend. Sin componentes: las prueban los tests.

export const MAX_FALLAS = 20;
export const MAX_TEXTO_FALLA = 200;
export const MIN_TEXTO_TRABAJO = 3;
export const MAX_TEXTO_TRABAJO = 200;
export const MAX_VALOR = 100000000;
export const MAX_HORAS = 200;
export const MAX_TIEMPO_HORAS = 2000;
export const MAX_CANTIDAD = 99;

/** Lo escrito en cantidad: siempre un entero entre 1 y 99 (1,5 pasa a 1, no se manda decimal). */
export const cantidadEntera = (valor) => Math.max(1, Math.min(MAX_CANTIDAD, Math.trunc(Number(valor)) || 1));

/** Las fallas, una por línea. */
export const lineasFallas = (texto) => texto.split("\n").map((f) => f.trim()).filter(Boolean);

export function errorFallas(texto) {
  const fallas = lineasFallas(texto);
  if (fallas.length > MAX_FALLAS) return `Máximo ${MAX_FALLAS} fallas.`;
  const larga = fallas.find((f) => f.length > MAX_TEXTO_FALLA);
  return larga ? `Cada falla admite hasta ${MAX_TEXTO_FALLA} caracteres: acorta "${larga.slice(0, 30)}…".` : null;
}

/** Un trabajo sin nada escrito se ignora; uno a medias se avisa, en vez de perderse sin decir nada. */
const lineaVacia = (l) => !l.descripcion.trim() && !String(l.valor).trim() && !String(l.horas).trim();

function errorLinea(l, n, tarifaHora) {
  const trabajo = `El trabajo ${n}`;
  const descripcion = l.descripcion.trim();
  if (descripcion.length < MIN_TEXTO_TRABAJO) return `${trabajo} necesita una descripción de al menos ${MIN_TEXTO_TRABAJO} caracteres.`;
  if (descripcion.length > MAX_TEXTO_TRABAJO) return `${trabajo} admite hasta ${MAX_TEXTO_TRABAJO} caracteres.`;
  if (l.modalidad === "HORAS") {
    const horas = Number(l.horas);
    if (!(horas > 0) || horas > MAX_HORAS) return `${trabajo} necesita las horas: más de 0 y hasta ${MAX_HORAS}.`;
    if (!(tarifaHora > 0)) return `Para cotizar por horas configura tu tarifa por hora en "Mi taller", o usa valor fijo.`;
    return null;
  }
  const valor = Number(l.valor);
  if (!(valor > 0) || valor > MAX_VALOR) return `${trabajo} necesita un valor mayor que 0.`;
  return null;
}

/** Lo primero que falta para calcular la cotización, o null. tiempo: horas estimadas, opcional. */
export function errorCotizacion(lineas, tiempo, { hayRepuestos = false, tarifaHora = null } = {}) {
  const conDatos = lineas.filter((l) => !lineaVacia(l));
  if (conDatos.length === 0 && !hayRepuestos) return "Agrega al menos un trabajo de mano de obra.";
  for (let i = 0; i < lineas.length; i++) {
    if (lineaVacia(lineas[i])) continue;
    const problema = errorLinea(lineas[i], i + 1, tarifaHora);
    if (problema) return problema;
  }
  if (String(tiempo).trim()) {
    const horas = Number(tiempo);
    if (!Number.isInteger(horas) || horas < 1 || horas > MAX_TIEMPO_HORAS) {
      return `El tiempo estimado va en horas enteras, de 1 a ${MAX_TIEMPO_HORAS}.`;
    }
  }
  return null;
}
