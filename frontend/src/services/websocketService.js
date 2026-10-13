import { pedirTicket } from "./notificacionesService";
import { urlWebSocket, retrasoReconexion } from "./notificacionesUtil";

// Conexión única a /ws/notificaciones. Cada conexión (y cada reconexión) usa un ticket nuevo
// de un solo uso: el token de sesión nunca viaja en la URL.

const API_BASE = import.meta.env.VITE_API_URL || "http://localhost:8080";

let ws = null;
let activo = false;
let conectando = false;
let reconnectTimer = null;
let retries = 0;
let generation = 0;
let estado = "desconectado"; // "conectado" | "conectando" | "desconectado"
const listeners = new Set();
const estadoListeners = new Set();

function notify(data) {
  listeners.forEach((cb) => {
    try { cb(data); } catch { /* ignore */ }
  });
}

function setEstado(nuevo) {
  if (estado === nuevo) return;
  estado = nuevo;
  estadoListeners.forEach((cb) => {
    try { cb(nuevo); } catch { /* ignore */ }
  });
}

function scheduleReconnect() {
  if (reconnectTimer || !activo) return;
  const delay = retrasoReconexion(retries);
  retries += 1;
  setEstado("conectando");
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect();
  }, delay);
}

async function connect() {
  if (!activo || ws || conectando) return;
  conectando = true;
  setEstado("conectando");
  const gen = generation;

  let ticket;
  try {
    ticket = await pedirTicket();
  } catch (e) {
    conectando = false;
    if (gen !== generation) return;
    // Sin sesión o sin permiso no tiene sentido insistir; un fallo de red sí se reintenta
    if (e?.status === 401 || e?.status === 403) { setEstado("desconectado"); return; }
    scheduleReconnect();
    return;
  }
  conectando = false;
  if (gen !== generation || !activo) return;

  let socket;
  try {
    socket = new WebSocket(urlWebSocket(API_BASE, ticket));
  } catch {
    scheduleReconnect();
    return;
  }
  ws = socket;

  socket.onopen = () => {
    if (gen !== generation) {
      try { socket.close(); } catch { /* ignore */ }
      return;
    }
    retries = 0;
    setEstado("conectado");
  };

  socket.onmessage = (ev) => {
    if (gen !== generation) return;
    try {
      const data = JSON.parse(ev.data);
      if (data && data.type) notify(data);
    } catch { /* ignore */ }
  };

  socket.onclose = () => {
    if (ws === socket) ws = null;
    if (gen !== generation) return;
    scheduleReconnect();
  };

  socket.onerror = () => {
    if (gen !== generation) return;
    try { socket.close(); } catch { /* ignore */ }
  };
}

function disconnectInternal() {
  generation += 1;
  conectando = false;
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  if (ws) {
    const socket = ws;
    ws = null;
    socket.onclose = null;
    if (socket.readyState === WebSocket.CONNECTING) {
      socket.onopen = () => { try { socket.close(); } catch { /* ignore */ } };
    } else {
      try { socket.close(); } catch { /* ignore */ }
    }
  }
  setEstado("desconectado");
}

/** Abre la conexión de la sesión actual (si ya está abierta, no hace nada). */
export function connectNotifications() {
  if (activo && (ws || conectando || reconnectTimer)) return;
  activo = true;
  retries = 0;
  connect();
}

export function disconnectNotifications() {
  activo = false;
  retries = 0;
  disconnectInternal();
}

export function subscribeNotifications(cb) {
  listeners.add(cb);
  return () => listeners.delete(cb);
}

/** Estado de la conexión en vivo, para mostrarlo en la campanita. */
export function subscribeEstadoConexion(cb) {
  estadoListeners.add(cb);
  cb(estado);
  return () => estadoListeners.delete(cb);
}
