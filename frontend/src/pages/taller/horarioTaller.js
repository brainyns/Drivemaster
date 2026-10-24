// Horario de atención estructurado del taller (lógica pura: se prueba con node --test). Las mismas reglas que
// HorarioTallerService en el backend: cierre después de la apertura, franjas sin cruces y máximo 4 por día.

export const DIAS = [
  { clave: "LUNES", nombre: "Lunes", corto: "Lun" },
  { clave: "MARTES", nombre: "Martes", corto: "Mar" },
  { clave: "MIERCOLES", nombre: "Miércoles", corto: "Mié" },
  { clave: "JUEVES", nombre: "Jueves", corto: "Jue" },
  { clave: "VIERNES", nombre: "Viernes", corto: "Vie" },
  { clave: "SABADO", nombre: "Sábado", corto: "Sáb" },
  { clave: "DOMINGO", nombre: "Domingo", corto: "Dom" },
];

export const HABILES = ["MARTES", "MIERCOLES", "JUEVES", "VIERNES"];
export const MAX_FRANJAS = 4;

/** "HH:mm" → minutos desde la medianoche. */
export const aMinutos = (hhmm) => {
  const [h, m] = String(hhmm).split(":").map(Number);
  return h * 60 + m;
};

const aHHmm = (min) => `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}`;

/** "14:30" → "2:30 p. m." */
export function hora12(hhmm) {
  const min = aMinutos(hhmm);
  const h = Math.floor(min / 60);
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${String(min % 60).padStart(2, "0")} ${h < 12 ? "a. m." : "p. m."}`;
}

/** Las horas del selector: cada 30 minutos, de 12:00 a. m. a 11:30 p. m. */
export const OPCIONES_HORA = Array.from({ length: 48 }, (_, i) => ({ valor: aHHmm(i * 30), texto: hora12(aHHmm(i * 30)) }));

const franja = (apertura, cierre) => ({ apertura, cierre });

/** Propuesta para quien aún no configura nada: lunes a viernes 8–12 y 2–6, sábado 8–1, domingo cerrado. */
export function semanaPorDefecto() {
  return DIAS.map(({ clave }) => {
    if (clave === "DOMINGO") return { dia: clave, abierto: false, franjas: [] };
    if (clave === "SABADO") return { dia: clave, abierto: true, franjas: [franja("08:00", "13:00")] };
    return { dia: clave, abierto: true, franjas: [franja("08:00", "12:00"), franja("14:00", "18:00")] };
  });
}

/** Lo que llega de GET /horario (filas por franja) como una fila por día; null si el taller aún no lo configuró. */
export function semanaDesdeFranjas(filas) {
  if (!filas?.length) return null;
  return DIAS.map(({ clave }) => {
    const delDia = filas.filter((f) => f.diaSemana === clave && f.abierto)
      .sort((a, b) => a.orden - b.orden)
      .map((f) => franja(f.horaApertura.slice(0, 5), f.horaCierre.slice(0, 5)));
    return { dia: clave, abierto: delDia.length > 0, franjas: delDia };
  });
}

/** Una fila por día → el cuerpo de PUT /horario. */
export function franjasDesdeSemana(semana) {
  return semana.flatMap((d) => (d.abierto && d.franjas.length
    ? d.franjas.map((f, i) => ({ diaSemana: d.dia, abierto: true, horaApertura: f.apertura, horaCierre: f.cierre, orden: i }))
    : [{ diaSemana: d.dia, abierto: false, horaApertura: null, horaCierre: null, orden: 0 }]));
}

/** Mensaje del primer problema de un día, o null si está bien. */
export function errorDia(dia) {
  if (!dia.abierto) return null;
  if (!dia.franjas.length) return "Agrega al menos una franja o marca el día como cerrado.";
  if (dia.franjas.some((f) => aMinutos(f.cierre) <= aMinutos(f.apertura))) {
    return "La hora de cierre debe ser después de la de apertura.";
  }
  const orden = [...dia.franjas].sort((a, b) => aMinutos(a.apertura) - aMinutos(b.apertura));
  for (let i = 1; i < orden.length; i++) {
    if (aMinutos(orden[i].apertura) < aMinutos(orden[i - 1].cierre)) return "Las franjas de este día se cruzan.";
  }
  return null;
}

/** Copia el lunes (abierto o cerrado, con sus franjas) a martes, miércoles, jueves y viernes. */
export function copiarLunesAHabiles(semana) {
  const lunes = semana.find((d) => d.dia === "LUNES");
  return semana.map((d) => (HABILES.includes(d.dia)
    ? { ...d, abierto: lunes.abierto, franjas: lunes.franjas.map((f) => ({ ...f })) }
    : d));
}

/** Una franja nueva para la pausa del almuerzo: empieza una hora después del último cierre. */
export function franjaSiguiente(dia) {
  const ultima = dia.franjas.at(-1);
  if (!ultima) return franja("08:00", "12:00");
  const desde = Math.min(aMinutos(ultima.cierre) + 60, 23 * 60);
  return franja(aHHmm(desde), aHHmm(Math.min(desde + 4 * 60, 23 * 60 + 30)));
}

/** "8:00 a. m.–12:00 p. m. y 2:00 p. m.–6:00 p. m." o "Cerrado". */
export const textoDia = (dia) => (dia.abierto && dia.franjas.length
  ? dia.franjas.map((f) => `${hora12(f.apertura)}–${hora12(f.cierre)}`).join(" y ")
  : "Cerrado");

// ─── Lo usan la agenda y la cita de revisión ───

/** La fila del día de la semana de una fecha (Date local). */
export const diaDeFecha = (semana, fecha) => semana?.[(fecha.getDay() + 6) % 7] || null;

/** ¿El taller abre ese día? Sin horario configurado, todos los días cuentan como abiertos. */
export function abreEseDia(semana, fecha) {
  if (!semana) return true;
  const d = diaDeFecha(semana, fecha);
  return !!d?.abierto && d.franjas.length > 0;
}

/**
 * Las horas en que se puede citar ese día ("HH:mm", cada 30 min, dentro de las franjas y sin la hora del cierre).
 * Sin horario configurado, todas las del día.
 */
export function horasParaCita(semana, fecha) {
  if (!semana) return OPCIONES_HORA.map((o) => o.valor);
  const d = diaDeFecha(semana, fecha);
  if (!d?.abierto) return [];
  return OPCIONES_HORA.map((o) => o.valor)
    .filter((h) => d.franjas.some((f) => aMinutos(h) >= aMinutos(f.apertura) && aMinutos(h) < aMinutos(f.cierre)));
}

/** Las horas para citar en un día ("YYYY-MM-DD"); si es hoy, solo las que aún no pasan. */
export function horasDeCita(semana, clave, ahora = new Date()) {
  const [a, m, d] = clave.split("-").map(Number);
  const fecha = new Date(a, m - 1, d);
  const horas = horasParaCita(semana, fecha);
  if (fecha.toDateString() !== ahora.toDateString()) return horas;
  const minutos = ahora.getHours() * 60 + ahora.getMinutes();
  return horas.filter((h) => aMinutos(h) > minutos);
}

/** Por qué no se puede citar ese día, o null si se puede. */
export function errorDiaCita(semana, clave, ahora = new Date()) {
  if (!clave) return null;
  const [a, m, d] = clave.split("-").map(Number);
  const fecha = new Date(a, m - 1, d);
  if (!abreEseDia(semana, fecha)) {
    const nombre = diaDeFecha(semana, fecha) && DIAS[(fecha.getDay() + 6) % 7].nombre.toLowerCase();
    return `Tu taller está cerrado los ${nombre.endsWith("s") ? nombre : `${nombre}s`}. Elige otro día.`;
  }
  if (!horasDeCita(semana, clave, ahora).length) return "Ya no quedan horas de atención ese día. Elige otro.";
  return null;
}
