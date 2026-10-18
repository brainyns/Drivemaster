// Resultado de un pago de Wompi: cómo se muestra cada estado, cuándo se vuelve a consultar y el
// límite de tiempo de cada consulta (sin dependencias: lo prueba tests/pago-resultado.test.mjs).

/** Cada cuánto se vuelve a consultar un pago pendiente o que todavía no aparece. */
export const INTERVALO_REINTENTO_MS = 5000;

/** Límite de cada consulta al backend. Render dormido tarda en despertar: se avisa en vez de esperar sin fin. */
export const LIMITE_VERIFICACION_MS = 30000;

// Intentos por cada resultado antes de dejar de consultar:
// - PENDIENTE: PSE o Nequi pueden tardar; 36 x 5 s = 3 minutos.
// - NO_VERIFICADO: Wompi aún no muestra la transacción o el backend falló.
// - SIN_RESPUESTA: el backend no respondió a tiempo; se reintenta una vez (despertar Render tarda hasta ~1 min).
export const MAX_INTENTOS = { PENDIENTE: 36, NO_VERIFICADO: 3, SIN_RESPUESTA: 2 };

/**
 * Estado de la transacción en Wompi (o NO_ENCONTRADO del backend) → cómo se muestra:
 * APROBADO, PENDIENTE, RECHAZADO (sin cobro) o NO_VERIFICADO.
 * Solo RECHAZADO puede decir "no se hizo ningún cobro".
 */
export function clasificar(estadoWompi) {
  switch (estadoWompi) {
    case "APPROVED":
      return "APROBADO";
    case "PENDING":
      return "PENDIENTE";
    case "DECLINED":
    case "VOIDED":
    case "ERROR":
      return "RECHAZADO";
    default:
      return "NO_VERIFICADO";
  }
}

/** true si hay que volver a consultar; intento cuenta desde 1 para cada resultado. */
export function debeReintentar(clase, intento) {
  const max = MAX_INTENTOS[clase];
  return max !== undefined && intento < max;
}

/** La consulta pasó el límite de tiempo (el servidor no respondió). */
export class TiempoAgotado extends Error {
  constructor(ms) {
    super(`El servidor no respondió en ${Math.round(ms / 1000)} s`);
    this.name = "TiempoAgotado";
  }
}

/**
 * Ejecuta fn(signal) y la corta a los ms: cancela la petición con el signal y rechaza con TiempoAgotado,
 * aunque fn no lo respete.
 */
export function conLimiteDeTiempo(fn, ms) {
  const control = new AbortController();
  let reloj;
  const limite = new Promise((_, rechazar) => {
    reloj = setTimeout(() => {
      control.abort();
      rechazar(new TiempoAgotado(ms));
    }, ms);
  });
  return Promise.race([Promise.resolve().then(() => fn(control.signal)), limite])
    .finally(() => clearTimeout(reloj));
}
