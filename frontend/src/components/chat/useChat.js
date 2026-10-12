import { useCallback, useEffect, useRef, useState } from 'react';
import { obtenerSessionId, cargarMensajes, guardarMensajes, reiniciarChat, EVENTO_REINICIO } from './chatSesion';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
// Un poco más que los 90 s que el backend espera a n8n: así llega su mensaje amable y no un corte.
const TIMEOUT_MS = 95_000;
const MSG_ERROR = 'Lo siento, hubo un error. Por favor intenta de nuevo.';
const MSG_TIMEOUT = 'El asistente está tardando demasiado. Por favor intenta de nuevo en unos segundos.';

const hora = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

const mensaje = (text, isUser) => ({
  id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  text,
  isUser,
  timestamp: hora()
});

const saludo = () => mensaje('¡Hola! Soy tu asesor virtual de Drivemaster. ¿En qué puedo ayudarte hoy?', false);

/**
 * Estado del chat. Vive en ChatWidget (siempre montado), no en ChatWindow: la ventana se
 * desmonta al minimizar o cerrar, y antes eso borraba la conversación y perdía la respuesta en curso.
 */
export function useChat() {
  const [messages, setMessages] = useState(() => cargarMensajes() || [saludo()]);
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

  const enviar = useCallback(async (texto) => {
    if (!texto.trim() || peticionRef.current) return;

    const conversacion = conversacionRef.current;
    const controller = new AbortController();
    peticionRef.current = controller;
    let porTimeout = false;
    const timer = setTimeout(() => { porTimeout = true; controller.abort(); }, TIMEOUT_MS);

    setMessages(prev => [...prev, mensaje(texto, true)]);
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
      // 429 (demasiados mensajes) trae el motivo en "error"; 400 (mensaje vacío) en "respuesta".
      respuesta = res.ok && data?.respuesta ? data.respuesta : (data?.error || data?.respuesta || MSG_ERROR);
    } catch (error) {
      // AbortError sin timeout = el usuario empezó una conversación nueva: no es un fallo.
      if (error?.name !== 'AbortError') console.error('Error en el chat:', error);
      respuesta = porTimeout ? MSG_TIMEOUT : MSG_ERROR;
    } finally {
      clearTimeout(timer);
      if (peticionRef.current === controller) peticionRef.current = null;
    }

    if (conversacion !== conversacionRef.current) return;
    setMessages(prev => [...prev, mensaje(respuesta, false)]);
    setIsTyping(false);
  }, []);

  return { messages, isTyping, enviar, nuevaConversacion: reiniciarChat };
}
