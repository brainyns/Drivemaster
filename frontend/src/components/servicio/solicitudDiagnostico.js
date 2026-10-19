// Reglas de presentación de Solicitar diagnóstico: síntomas sugeridos, datos del vehículo y qué falta para
// poder enviar. Funciones puras (sin React) para probarlas con node --test.

export const MIN_SINTOMAS = 10;
export const MAX_SINTOMAS = 1500;

/** Los cinco pasos del servicio (los mismos de "Cómo funciona" en la portada). */
export const PASOS_SERVICIO = ["Diagnóstico", "Taller", "Cotización", "Repuestos", "Instalación"];

/** Síntomas comunes: cada chip agrega (o quita) una frase al texto del cliente. */
export const SUGERENCIAS_SINTOMAS = [
  { id: "frenos", etiqueta: "Frenos", texto: "Los frenos hacen ruido o el pedal se siente distinto." },
  { id: "ruido", etiqueta: "Ruido extraño", texto: "Se escucha un ruido extraño al andar." },
  { id: "motor", etiqueta: "Motor", texto: "El motor falla, vibra o pierde fuerza." },
  { id: "tablero", etiqueta: "Luces del tablero", texto: "Se encendió una luz de advertencia en el tablero." },
  { id: "bateria", etiqueta: "Batería", texto: "Le cuesta encender o la batería se descarga." },
  { id: "aire", etiqueta: "Aire acondicionado", texto: "El aire acondicionado no enfría bien." },
  { id: "direccion", etiqueta: "Dirección y suspensión", texto: "La dirección se siente dura o el vehículo se va hacia un lado." },
  { id: "fugas", etiqueta: "Fugas", texto: "Veo una fuga de líquido debajo del vehículo." },
];

export const tieneSugerencia = (sintomas, sugerencia) => (sintomas || "").includes(sugerencia.texto);

/** Agrega la frase de la sugerencia al final del texto o, si ya está, la quita (sin dejar espacios de más). */
export function alternarSugerencia(sintomas, sugerencia) {
  const actual = sintomas || "";
  if (tieneSugerencia(actual, sugerencia)) {
    return actual.replace(sugerencia.texto, "").replace(/[ \t]{2,}/g, " ").replace(/\n{3,}/g, "\n\n").trim();
  }
  const base = actual.trimEnd();
  return base ? `${base} ${sugerencia.texto}` : sugerencia.texto;
}

/**
 * El motor se guarda como texto libre: "1.2" o "1600" se leen como cilindrada ("Motor 1.2 L", "Motor 1600 cc");
 * lo demás se muestra tal cual con su etiqueta ("Motor 2.0 turbo").
 */
export function formatoMotor(motor) {
  const m = (motor || "").trim();
  if (!m) return null;
  if (/^\d+([.,]\d+)?$/.test(m)) return Number(m.replace(",", ".")) >= 100 ? `Motor ${m} cc` : `Motor ${m} L`;
  return /^motor\b/i.test(m) ? m : `Motor ${m}`;
}

/** "45000" → "45.000 km". */
export function formatoKm(km) {
  if (km == null || !Number.isFinite(Number(km))) return null;
  return `${Number(km).toLocaleString("es-CO")} km`;
}

/**
 * Lo que falta para poder enviar, en el orden del formulario: [{ campo, resumen, error }]. `resumen` va en la lista
 * del resumen ("Elige el vehículo"); `error` es el mensaje en línea bajo el campo.
 */
export function faltantes({ vehiculoId, sintomas, ciudad }) {
  const lista = [];
  if (!vehiculoId) {
    lista.push({ campo: "vehiculo", resumen: "Elige el vehículo", error: "Elige el vehículo que va a revisar el taller." });
  }
  const largo = (sintomas || "").trim().length;
  if (largo < MIN_SINTOMAS) {
    const faltan = MIN_SINTOMAS - largo;
    lista.push({
      campo: "sintomas",
      resumen: "Cuenta qué está pasando",
      error: largo === 0
        ? `Cuéntanos qué está pasando (mínimo ${MIN_SINTOMAS} caracteres).`
        : `Cuéntanos un poco más: ${faltan === 1 ? "falta 1 carácter" : `faltan ${faltan} caracteres`}.`,
    });
  }
  if (!(ciudad || "").trim()) {
    lista.push({ campo: "ciudad", resumen: "Escribe la ciudad", error: "Escribe la ciudad donde está el vehículo." });
  }
  return lista;
}
