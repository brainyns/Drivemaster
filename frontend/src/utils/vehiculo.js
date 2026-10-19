// Datos de un vehículo con su etiqueta, para las tarjetas (Mis vehículos, Solicitar diagnóstico). Funciones puras.

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
  if (km == null || km === "" || !Number.isFinite(Number(km))) return null;
  return `${Number(km).toLocaleString("es-CO")} km`;
}

/** Datos secundarios de la tarjeta, en orden: motor, combustible y kilometraje. */
export const datosVehiculo = (v) => [formatoMotor(v.motor), v.combustible, formatoKm(v.kilometraje)].filter(Boolean);
