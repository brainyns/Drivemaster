import { getToken } from "./authService";

let ws = null;
let token = null;
let reconnectTimer = null;
let retries = 0;
let generation = 0;
const listeners = new Set();

function wsUrl() {
  const base = import.meta.env.VITE_API_URL || "http://localhost:8080";
  const url = new URL(base);
  const proto = url.protocol === "https:" ? "wss" : "ws";
  return `${proto}://${url.host}/ws/notificaciones?token=${encodeURIComponent(token)}`;
}

function notify(data) {
  listeners.forEach((cb) => {
    try { cb(data); } catch { /* ignore */ }
  });
}

function scheduleReconnect() {
  if (reconnectTimer || !token) return;
  const delay = Math.min(30000, 3000 * (retries + 1));
  retries += 1;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect();
  }, delay);
}

function connect() {
  const stored = getToken();
  if (stored) token = stored;
  if (!token) return;

  const gen = generation;
  let socket;
  try {
    socket = new WebSocket(wsUrl());
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
}

export function connectNotifications(tok) {
  if (!tok) return;
  if (ws && token === tok) return;
  token = tok;
  disconnectInternal();
  connect();
}

export function disconnectNotifications() {
  token = null;
  disconnectInternal();
  listeners.clear();
}

export function subscribeNotifications(cb) {
  listeners.add(cb);
  return () => listeners.delete(cb);
}
