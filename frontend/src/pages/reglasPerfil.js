// Validación de Mi perfil (datos personales y cambio de contraseña). Funciones puras.

export const MIN_CLAVE = 6;

/** Errores de los datos personales; el nombre es obligatorio y el teléfono, si se escribe, debe ser válido. */
export function validarDatos(d) {
  const e = {};
  if (!d.nombre.trim()) e.nombre = "Escribe tu nombre.";
  if (d.telefono.trim() && !/^[0-9+\s()-]{7,20}$/.test(d.telefono.trim())) {
    e.telefono = "Teléfono no válido: usa solo números, espacios o +.";
  }
  return e;
}

/** Errores del cambio de contraseña (el servidor exige lo mismo y además comprueba la actual). */
export function validarClave(c) {
  const e = {};
  if (!c.actual) e.actual = "Escribe tu contraseña actual.";
  if (c.nueva.length < MIN_CLAVE) e.nueva = `La nueva contraseña debe tener al menos ${MIN_CLAVE} caracteres.`;
  else if (c.nueva === c.actual) e.nueva = "La nueva contraseña debe ser distinta de la actual.";
  if (c.confirmar !== c.nueva) e.confirmar = "Las contraseñas no coinciden.";
  return e;
}

/**
 * Los datos del formulario a partir del perfil que devuelve el backend (PerfilClienteResponse: el documento llega
 * como "documento" y el correo como "email"; al guardar, el PUT sigue esperando "identificacion").
 */
export const datosDesdePerfil = (perfil, nombre) => ({
  nombre: perfil?.nombre || nombre || "",
  identificacion: perfil?.documento || "",
  telefono: perfil?.telefono || "",
  ciudad: perfil?.ciudad || "",
  direccion: perfil?.direccion || "",
  referencia: perfil?.referencia || "",
});

/** Lo que se envía a PUT /mi-perfil (el nombre va por su propio endpoint). */
export const cuerpoPerfil = (d) => ({
  identificacion: d.identificacion.trim(),
  telefono: d.telefono.trim(),
  ciudad: d.ciudad.trim(),
  direccion: d.direccion.trim(),
  referencia: d.referencia.trim(),
});
