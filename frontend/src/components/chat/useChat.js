import { useCallback, useEffect, useRef, useState } from 'react';
import { obtenerSessionId, cargarMensajes, guardarMensajes, reiniciarChat, EVENTO_REINICIO } from './chatSesion';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
// Un poco más que los 90 s que el backend espera a n8n: así llega su mensaje amable y no un corte.
const TIMEOUT_MS = 95_000;
// Los errores dicen qué pasó y qué hacer; todos ofrecen el botón Reintentar.
const MSG_ERROR = 'El asistente no está disponible en este momento. Toca Reintentar en unos segundos.';
const MSG_SIN_CONEXION = 'No pude conectarme con el asistente. Revisa tu conexión a internet y toca Reintentar.';
const MSG_TIMEOUT = 'El asistente tardó demasiado en responder. Toca Reintentar.';
// El reloj del mensaje del cliente pasa a check tras este tiempo: es una señal visual de "enviado".
const MS_ENVIADO = 450;

const hora = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

const nuevoId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const mensaje = (text, isUser, extra = {}) => ({
  id: nuevoId(),
  text,
  isUser,
  timestamp: hora(),
  ...extra
});

// El saludo aparece completo, sin el efecto de escritura.
const saludo = () => mensaje('¡Hola! Soy el asistente de Drivemaster. ¿Qué repuesto buscas y para qué carro?', false, { saludo: true });

// Un mensaje guardado "enviando" (se recargó la página a mitad del envío) se muestra como enviado.
function mensajesIniciales() {
  const guardados = cargarMensajes();
  if (!guardados) return [saludo()];
  return guardados.map(m => (m.estado === 'enviando' ? { ...m, estado: 'enviado' } : m));
}

/**
 * Estado del chat. Vive en ChatWidget (siempre montado), no en ChatWindow: la ventana se
 * desmonta al minimizar o cerrar, y antes eso borraba la conversación y perdía la respuesta en curso.
 *
 * Mensajes del cliente: estado 'enviando' | 'enviado'. Mensajes del bot: productos (tarjeta) o
 * error: true con el texto a reenviar en "reintento".
 */
export function useChat() {
  const [messages, setMessages] = useState(mensajesIniciales);
  const [isTyping, setIsTyping] = useState(false);
  const peticionRef = useRef(null);
  // Cambia con cada conversación nueva: una respuesta que llega tarde no se mezcla con la siguiente.
  const conversacionRef = useRef(0);

  useEffect(() => {
    guardarMensajes(messages);
  }, [messages]);

  const alReiniciar = useCallback(() => {
    conversacionRef.current += 1;
    peticionRef.current?.abort();
    peticionRef.current = null;
    setIsTyping(false);
    setMessages([saludo()]);
  }, []);

  useEffect(() => {
    window.addEventListener(EVENTO_REINICIO, alReiniciar);
    return () => window.removeEventListener(EVENTO_REINICIO, alReiniciar);
  }, [alReiniciar]);

  // Pide la respuesta de un texto ya mostrado en la conversación (envío nuevo o reintento).
  const pedir = useCallback(async (texto) => {
    const conversacion = conversacionRef.current;
    const controller = new AbortController();
    peticionRef.current = controller;
    let porTimeout = false;
    const timer = setTimeout(() => { porTimeout = true; controller.abort(); }, TIMEOUT_MS);
    setIsTyping(true);

    let respuesta;
    try {
      const res = await fetch(`${API_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mensaje: texto, sessionId: obtenerSessionId() }),
        signal: controller.signal
      });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.respuesta) {
        respuesta = mensaje(data.respuesta, false, {
          productos: Array.isArray(data.productos) ? data.productos : []
        });
      } else {
        // 429 (demasiados mensajes) trae el motivo en "error"; 400 (mensaje vacío) en "respuesta".
        respuesta = mensaje(data?.error || data?.respuesta || MSG_ERROR, false, { error: true, reintento: texto });
      }
    } catch (error) {
      // AbortError sin timeout = el usuario empezó una conversación nueva: no es un fallo.
      if (error?.name !== 'AbortError') console.error('Error en el chat:', error);
      const sinConexion = typeof navigator !== 'undefined' && navigator.onLine === false;
      const motivo = porTimeout ? MSG_TIMEOUT : (sinConexion || error?.name === 'TypeError') ? MSG_SIN_CONEXION : MSG_ERROR;
      respuesta = mensaje(motivo, false, { error: true, reintento: texto });
    } finally {
      clearTimeout(timer);
      if (peticionRef.current === controller) peticionRef.current = null;
    }

    if (conversacion !== conversacionRef.current) return;
    setMessages(prev => [...prev, respuesta]);
    setIsTyping(false);
  }, []);

  const enviar = useCallback((texto) => {
    if (!texto.trim() || peticionRef.current) return;
    const nuevo = mensaje(texto, true, { estado: 'enviando' });
    setMessages(prev => [...prev, nuevo]);
    setTimeout(() => {
      setMessages(prev => prev.map(m => (m.id === nuevo.id ? { ...m, estado: 'enviado' } : m)));
    }, MS_ENVIADO);
    pedir(texto);
  }, [pedir]);

  // Quita la burbuja de error y vuelve a pedir el mismo mensaje, sin que el cliente lo escriba otra vez.
  const reintentar = useCallback((idError) => {
    if (peticionRef.current) return;
    const error = messages.find(m => m.id === idError);
    if (!error?.reintento) return;
    setMessages(prev => prev.filter(m => m.id !== idError));
    pedir(error.reintento);
  }, [messages, pedir]);

  return { messages, isTyping, enviar, reintentar, nuevaConversacion: reiniciarChat };
}
