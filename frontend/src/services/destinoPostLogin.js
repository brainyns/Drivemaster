// Destino al que se vuelve después de iniciar sesión (ej. el enlace de la cotización del
// correo lleva a "Mis servicios"). Se guarda SOLO el nombre de la página: nunca el token
// de la cotización ni ningún dato de la sesión. Vive en sessionStorage (se borra al cerrar
// la pestaña) y se consume una sola vez.

const CLAVE = "dm.destinoPostLogin";
// Lista cerrada: nada que venga de fuera puede mandar al usuario a otra página
const DESTINOS_VALIDOS = ["mis-servicios"];

export function guardarDestinoPostLogin(destino) {
  if (!DESTINOS_VALIDOS.includes(destino)) return;
  try { sessionStorage.setItem(CLAVE, destino); } catch { /* sin almacenamiento: se usa el comportamiento normal */ }
}

/** Devuelve el destino guardado (o null) y lo borra: solo sirve para el siguiente inicio de sesión. */
export function consumirDestinoPostLogin() {
  try {
    const destino = sessionStorage.getItem(CLAVE);
    sessionStorage.removeItem(CLAVE);
    return DESTINOS_VALIDOS.includes(destino) ? destino : null;
  } catch {
    return null;
  }
}

export function limpiarDestinoPostLogin() {
  try { sessionStorage.removeItem(CLAVE); } catch { /* nada que limpiar */ }
}
