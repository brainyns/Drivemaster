// Calendario de la agenda del taller (lógica pura, sin librerías de fechas: se prueba con node --test). Todo en la
// hora local del navegador y con la semana empezando el lunes, como el horario de atención.

export const DIAS_CORTOS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

const MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
  "noviembre", "diciembre"];

const dos = (n) => String(n).padStart(2, "0");

/** Date local → "YYYY-MM-DD" (la clave de cada día y el formato del endpoint de citas). */
export const claveDia = (d) => `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`;

/** "YYYY-MM-DD" → Date local a medianoche. */
export function desdeClave(clave) {
  const [a, m, d] = clave.split("-").map(Number);
  return new Date(a, m - 1, d);
}

export const sumarDias = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
export const primeroDelMes = (d) => new Date(d.getFullYear(), d.getMonth(), 1);
export const sumarMeses = (d, n) => new Date(d.getFullYear(), d.getMonth() + n, 1);

/** El lunes de la semana de esa fecha. */
export const lunesDe = (d) => sumarDias(d, -((d.getDay() + 6) % 7));

/** Las 7 fechas de la semana (lunes a domingo) que contiene esa fecha. */
export const semanaDe = (d) => Array.from({ length: 7 }, (_, i) => sumarDias(lunesDe(d), i));

/** La rejilla del mes: 6 semanas de lunes a domingo, con los días de los meses vecinos en los bordes. */
export function rejillaMes(mes) {
  const inicio = lunesDe(primeroDelMes(mes));
  return Array.from({ length: 6 }, (_, s) => Array.from({ length: 7 }, (_, i) => sumarDias(inicio, s * 7 + i)));
}

/** "octubre de 2026" */
export const tituloMes = (d) => `${MESES[d.getMonth()]} de ${d.getFullYear()}`;

/** "19 al 25 de octubre" o "28 de septiembre al 4 de octubre" (y el año si cambia). */
export function tituloSemana(dias) {
  const [a, b] = [dias[0], dias.at(-1)];
  const mesA = MESES[a.getMonth()];
  const mesB = MESES[b.getMonth()];
  if (a.getFullYear() !== b.getFullYear()) return `${a.getDate()} de ${mesA} de ${a.getFullYear()} al ${b.getDate()} de ${mesB} de ${b.getFullYear()}`;
  return mesA === mesB ? `${a.getDate()} al ${b.getDate()} de ${mesB}` : `${a.getDate()} de ${mesA} al ${b.getDate()} de ${mesB}`;
}

/** "lunes 19 de octubre" */
export const tituloDia = (d) =>
  `${d.toLocaleDateString("es-CO", { weekday: "long" })} ${d.getDate()} de ${MESES[d.getMonth()]}`;

/** Citas agrupadas por día ("YYYY-MM-DD"), cada día de la más temprana a la más tarde. */
export function citasPorDia(citas) {
  const mapa = {};
  [...citas]
    .filter((c) => c.fechaRevision)
    .sort((x, y) => new Date(x.fechaRevision) - new Date(y.fechaRevision))
    .forEach((c) => { (mapa[claveDia(new Date(c.fechaRevision))] ||= []).push(c); });
  return mapa;
}
