// Proveedores de la tienda: búsqueda, validación del formulario y avisos. Sin componentes: lo prueban los tests con
// node --test. Las reglas son las del backend (ValidacionProveedor): nombre obligatorio y correo, NIT y teléfono
// opcionales pero con formato válido. El NIT no se repite dentro de la tienda (lo bloquea el backend); el nombre
// repetido y un dígito de verificación que no cuadra solo se advierten.

const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TELEFONO = /^\+?[\d\s()-]{7,20}$/;

export function coincideProveedor(p, termino) {
  const q = (termino || "").trim().toLowerCase();
  if (!q) return true;
  return [p.nombre, p.nit, p.correo, p.telefono].some((x) => x && String(x).toLowerCase().includes(q));
}

export const correoValido = (correo) => CORREO.test(String(correo || "").trim());

/** Recibe el PDF de cada compra: tiene correo y la tienda no apagó el envío (sin dato = sí, como antes). */
export const recibePdf = (p) => !!p?.correo && p.enviarPdfCompras !== false;

export function erroresProveedor({ nombre, nit, telefono, correo }) {
  const e = {};
  if (!String(nombre || "").trim()) e.nombre = "Escribe el nombre de la empresa.";
  if (String(correo || "").trim() && !correoValido(correo)) e.correo = "Escribe un correo válido, como contacto@proveedor.com.";
  if (String(telefono || "").trim() && !TELEFONO.test(telefono.trim())) e.telefono = "Escribe un teléfono válido: solo números, espacios y el signo +.";
  if (String(nit || "").trim() && !/^[\d.\s-]{5,20}$/.test(nit.trim())) e.nit = "Escribe el NIT con números y guion, como 900123456-7.";
  return e;
}

// Pesos de la DIAN para el dígito de verificación, del dígito de la derecha hacia la izquierda
const PESOS_DIAN = [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71];

/** Dígito de verificación de un NIT (solo el número base, sin el dígito), con el algoritmo de la DIAN. */
export function digitoVerificacion(base) {
  const digitos = String(base).replace(/\D/g, "");
  if (!digitos || digitos.length > PESOS_DIAN.length) return null;
  const suma = [...digitos].reverse().reduce((s, d, i) => s + Number(d) * PESOS_DIAN[i], 0);
  const resto = suma % 11;
  return resto > 1 ? 11 - resto : resto;
}

/** Aviso (no error) si el NIT trae dígito de verificación ("900123456-7") y no es el que corresponde. */
export function avisoDigitoNit(nit) {
  const m = /^([\d.\s]+)-\s*(\d)$/.exec(String(nit || "").trim());
  if (!m) return null;
  const esperado = digitoVerificacion(m[1]);
  return esperado != null && esperado !== Number(m[2])
    ? `El dígito de verificación de este NIT debería ser ${esperado}. Revísalo antes de guardar.`
    : null;
}

const normalizar = (s) => String(s || "").trim().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "")
  .replace(/[.,]/g, "").replace(/\b(s ?a ?s|ltda|sa)\b/g, "").replace(/\s+/g, " ").trim();

/** Otro proveedor de la tienda con el mismo nombre (sin mayúsculas, tildes ni S.A.S.), o null. Solo se advierte. */
export function nombreRepetido(nombre, proveedores, idPropio) {
  const n = normalizar(nombre);
  if (!n) return null;
  return proveedores.find((p) => String(p.id) !== String(idPropio) && normalizar(p.nombre) === n) || null;
}
