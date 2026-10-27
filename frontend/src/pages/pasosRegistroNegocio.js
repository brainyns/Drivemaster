// Pasos y validación del registro de talleres y tiendas. Las reglas son las de siempre (el servidor exige las
// mismas); aquí solo se reparten por paso para validar cada uno antes de seguir. Funciones puras.

export const vacio = (v) => v === null || v === undefined || String(v).trim() === "";

/** Pasos según el tipo: la tienda no tiene el paso "Servicio" (mano de obra y especialidades). */
export function pasosDe(tipo) {
  return tipo === "TALLER"
    ? ["negocio", "ubicacion", "servicio", "cuenta", "revisar"]
    : ["negocio", "ubicacion", "cuenta", "revisar"];
}

export const NOMBRE_PASO = {
  negocio: "Negocio",
  ubicacion: "Ubicación",
  servicio: "Servicio",
  cuenta: "Cuenta",
  revisar: "Revisar",
};

/** Campos de cada paso, en el orden en que se recorren con el foco. */
export const CAMPOS_PASO = {
  negocio: ["nombreNegocio", "nit", "telefono"],
  ubicacion: ["ciudad", "barrio", "direccion", "horario", "ubicacion"],
  servicio: ["precioManoObra", "tarifaHora", "especialidades"],
  cuenta: ["nombreContacto", "correo", "password", "confirmarPassword"],
  revisar: [],
};

/** Todos los errores del formulario. */
export function validarNegocio(tipo, f) {
  const e = {};
  if (vacio(f.nombreNegocio)) e.nombreNegocio = "Escribe el nombre del negocio.";
  if (vacio(f.ciudad)) e.ciudad = "Indica la ciudad.";
  if (vacio(f.direccion)) e.direccion = "Indica la dirección.";
  // La tienda vende con retiro en su local: el horario es obligatorio (el servidor también lo exige)
  if (tipo === "TIENDA" && vacio(f.horario)) e.horario = "Indica el horario de atención: lo necesita el cliente para retirar.";
  if (!vacio(f.telefono) && !/^[0-9+\s()-]{7,20}$/.test(f.telefono.trim())) e.telefono = "Teléfono no válido: usa solo números, espacios o +.";
  if (tipo === "TALLER") {
    const precio = Number(f.precioManoObra);
    if (vacio(f.precioManoObra) || isNaN(precio) || precio <= 0) e.precioManoObra = "Indica el valor de tu mano de obra.";
    if (!vacio(f.tarifaHora) && (isNaN(Number(f.tarifaHora)) || Number(f.tarifaHora) <= 0)) e.tarifaHora = "Tarifa no válida.";
    // Con la ubicación el taller aparece en el mapa del cliente
    if (vacio(f.latitud) || vacio(f.longitud)) e.ubicacion = "Marca la ubicación de tu taller en el mapa.";
  }
  if (vacio(f.correo) || !/^\S+@\S+\.\S+$/.test(f.correo.trim())) e.correo = "Escribe un correo válido.";
  if (vacio(f.password) || f.password.length < 6) e.password = "Mínimo 6 caracteres.";
  if (f.confirmarPassword !== f.password) e.confirmarPassword = "Las contraseñas no coinciden.";
  return e;
}

/** Errores de un paso (o de varios). */
export function erroresDePasos(tipo, form, pasos) {
  const todos = validarNegocio(tipo, form);
  const campos = new Set(pasos.flatMap((p) => CAMPOS_PASO[p] || []));
  return Object.fromEntries(Object.entries(todos).filter(([campo]) => campos.has(campo)));
}

/** El primer paso que tiene errores, o null si todo está bien (para volver ahí antes de enviar). */
export function primerPasoConErrores(tipo, form) {
  return pasosDe(tipo).find((p) => Object.keys(erroresDePasos(tipo, form, [p])).length > 0) || null;
}

/**
 * El backend rechaza un negocio repetido (409) con un mensaje que dice qué dato ya existe ("Ya hay una tienda
 * registrada con ese NIT."): el campo donde mostrarlo, o null si el error es de otra cosa.
 */
export function campoDelDuplicado(mensaje) {
  const m = String(mensaje || "").toLowerCase();
  if (!m.startsWith("ya hay") && !m.startsWith("ya existe")) return null;
  if (m.includes("nit")) return "nit";
  if (m.includes("nombre")) return "nombreNegocio";
  if (m.includes("correo")) return "correo";
  return null;
}

/** El paso del formulario donde está un campo. */
export const pasoDelCampo = (tipo, campo) => pasosDe(tipo).find((p) => CAMPOS_PASO[p].includes(campo)) || null;

/** Los datos que se envían al backend: los mismos de antes. */
export function datosRegistro(tipo, form) {
  const numero = (v) => (vacio(v) ? null : Number(v));
  const datos = {
    nombreNegocio: form.nombreNegocio.trim(),
    nit: form.nit.trim() || null,
    telefono: form.telefono.trim() || null,
    ciudad: form.ciudad.trim(),
    barrio: form.barrio.trim() || null,
    direccion: form.direccion.trim(),
    latitud: numero(form.latitud),
    longitud: numero(form.longitud),
    nombreContacto: form.nombreContacto.trim() || null,
    correo: form.correo.replace(/\s+/g, "").toLowerCase(),
    password: form.password,
  };
  if (tipo === "TALLER") {
    datos.especialidades = form.especialidades.split(",").map((s) => s.trim()).filter(Boolean);
    datos.precioManoObra = numero(form.precioManoObra);
    datos.tarifaHora = numero(form.tarifaHora);
  }
  datos.horario = form.horario.trim() || null;
  return datos;
}
