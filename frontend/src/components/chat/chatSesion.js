// Sesión del chat en este navegador. El sessionId es la clave de la memoria del agente en n8n;
// los mensajes se guardan para que recargar la página no esconda lo que el bot sí recuerda.

const CLAVE_SESION = 'dm-chat-session';
const CLAVE_MENSAJES = 'dm-chat-mensajes';
const CLAVE_VISTO = 'dm-chat-visto';
const MAX_MENSAJES = 50;

export const EVENTO_REINICIO = 'chat:reiniciar';

// localStorage puede no existir o fallar (modo privado, almacenamiento bloqueado): el chat sigue funcionando.
function leer(clave) {
  try { return localStorage.getItem(clave); } catch { return null; }
}
function escribir(clave, valor) {
  try { localStorage.setItem(clave, valor); } catch { /* sin almacenamiento: solo se pierde la persistencia */ }
}
function borrar(clave) {
  try { localStorage.removeItem(clave); } catch { /* idem */ }
}

// crypto.randomUUID solo existe en HTTPS o localhost; por la LAN (http://IP-VM:8088) no está.
// getRandomValues sí, y evita que dos personas compartan memoria por un id basado en la hora.
function nuevoId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join('');
}

export function obtenerSessionId() {
  let id = leer(CLAVE_SESION);
  if (!id) {
    id = nuevoId();
    escribir(CLAVE_SESION, id);
  }
  return id;
}

export function cargarMensajes() {
  try {
    const guardados = JSON.parse(leer(CLAVE_MENSAJES));
    return Array.isArray(guardados) && guardados.length > 0 ? guardados : null;
  } catch {
    return null;
  }
}

export function guardarMensajes(mensajes) {
  escribir(CLAVE_MENSAJES, JSON.stringify(mensajes.slice(-MAX_MENSAJES)));
}

// Si el cliente ya abrió el chat alguna vez, el botón deja de llamar la atención (ondas e invitación).
export function chatVisto() {
  return leer(CLAVE_VISTO) === '1';
}
export function marcarChatVisto() {
  escribir(CLAVE_VISTO, '1');
}

/**
 * Conversación nueva: otro sessionId (el agente empieza sin memoria) y sin mensajes guardados.
 * Se llama desde "Nueva conversación" y al cerrar sesión, para que en un equipo compartido
 * la siguiente persona no herede la conversación anterior.
 */
export function reiniciarChat() {
  borrar(CLAVE_SESION);
  borrar(CLAVE_MENSAJES);
  window.dispatchEvent(new Event(EVENTO_REINICIO));
}
