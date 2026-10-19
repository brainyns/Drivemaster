// Proveedores de la tienda: búsqueda y validación del formulario. Sin componentes: lo prueban los tests con node
// --test. Las reglas son las de siempre (nombre y correo obligatorios; el correo es a donde llega el PDF de cada
// compra); solo se agregan los formatos para avisar antes de guardar.

const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TELEFONO = /^\+?[\d\s()-]{7,20}$/;

export function coincideProveedor(p, termino) {
  const q = (termino || "").trim().toLowerCase();
  if (!q) return true;
  return [p.nombre, p.nit, p.correo, p.telefono].some((x) => x && String(x).toLowerCase().includes(q));
}

export function erroresProveedor({ nombre, nit, telefono, correo }) {
  const e = {};
  if (!String(nombre || "").trim()) e.nombre = "Escribe el nombre de la empresa.";
  if (!String(correo || "").trim()) e.correo = "Escribe el correo: ahí le enviamos el PDF de cada compra.";
  else if (!CORREO.test(correo.trim())) e.correo = "Escribe un correo válido, como contacto@proveedor.com.";
  if (String(telefono || "").trim() && !TELEFONO.test(telefono.trim())) e.telefono = "Escribe un teléfono válido: solo números, espacios y el signo +.";
  if (String(nit || "").trim() && !/^[\d.\s-]{5,20}$/.test(nit.trim())) e.nit = "Escribe el NIT con números y guion, como 900123456-7.";
  return e;
}
