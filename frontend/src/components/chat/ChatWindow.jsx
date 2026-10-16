import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';
import ChatLogo from './ChatLogo';
import { sinMovimiento } from './movimiento';

// Aviso de la burbuja de espera. El backend no informa en qué paso va el agente, así que no se
// anuncian pasos ("Buscando en el catálogo…") que quizá no ocurren: solo se avisa si tarda.
const PISTAS = [
  [9500, 'Está tardando más de lo normal. Sigo aquí.']
];
const RETRASO_ESPERA = 380;
const DISTANCIA_IR_FINAL = 140;

const SUGERENCIAS = [
  {
    texto: 'Pastillas de freno', mensaje: 'Necesito pastillas de freno',
    icono: <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="12" cy="12" r="8" /><circle cx="12" cy="12" r="2.5" /></svg>
  },
  {
    texto: 'Filtro de aceite', mensaje: 'Busco un filtro de aceite',
    icono: <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true"><path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z" /></svg>
  },
  {
    texto: 'Batería', mensaje: '¿Tienen batería para mi carro?',
    icono: <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="7" width="18" height="13" rx="2" /><path d="M7 4v3M17 4v3M7 13h3M15.5 11.5v3M14 13h3" /></svg>
  }
];

/** Burbuja "escribiendo" con tres puntos y un aviso que cambia si la respuesta tarda. */
function BurbujaEspera() {
  const [pista, setPista] = useState('Escribiendo…');
  const [cambia, setCambia] = useState(false);

  useEffect(() => {
    const timers = PISTAS.map(([ms, texto]) => setTimeout(() => {
      if (sinMovimiento()) { setPista(texto); return; }
      setCambia(true);
      timers.push(setTimeout(() => { setPista(texto); setCambia(false); }, 220));
    }, ms));
    return () => timers.forEach(clearTimeout);
  }, []);

  return (
    <div className="message-bubble bot-message escribiendo entra-b">
      <div className="burbuja-bot burbuja-espera">
        <span className="puntos" aria-hidden="true"><i /><i /><i /></span>
        <span className={`pista${cambia ? ' cambia' : ''}`}>{pista}</span>
      </div>
    </div>
  );
}

// La conversación (mensajes, envío y sessionId) la maneja useChat en ChatWidget.
const ChatWindow = ({
  messages, isTyping, onSend, onReintentar, onNuevaConversacion, onClose, onMinimize, isClosing,
  inputValue, onInputChange, onVerProducto
}) => {
  // Una sugerencia se envía tal cual y no toca lo que el cliente tenga escrito en el campo.
  const onSugerencia = (texto) => { if (!isTyping) onSend(texto); };
  const enviarCampo = (texto) => {
    if (!texto.trim() || isTyping) return;
    onInputChange('');
    onSend(texto);
  };
  const listaRef = useRef(null);
  // Los mensajes que ya estaban al abrir la ventana aparecen sin animación.
  const [idsIniciales] = useState(() => new Set(messages.map(m => m.id)));
  const [verEspera, setVerEspera] = useState(isTyping);
  const [irFinal, setIrFinal] = useState(false);
  const [vaciando, setVaciando] = useState(false);
  const [girando, setGirando] = useState(false);
  const autoHasta = useRef(0);
  const timerAuto = useRef(null);

  // ── Desplazamiento ──
  // "Ir al final" se calcula con la posición de destino, no con la actual: el desplazamiento suave tarda.
  // Mientras dura un desplazamiento automático se ignoran los eventos de scroll intermedios.
  const actualizarIrFinal = useCallback((destino) => {
    const lista = listaRef.current;
    if (!lista) return;
    const pos = typeof destino === 'number' ? destino : lista.scrollTop;
    setIrFinal(lista.scrollHeight - pos - lista.clientHeight > DISTANCIA_IR_FINAL);
  }, []);

  const marcarAuto = useCallback(() => {
    autoHasta.current = Date.now() + 500;
    clearTimeout(timerAuto.current);
    timerAuto.current = setTimeout(() => actualizarIrFinal(), 520);
  }, [actualizarIrFinal]);

  const bajarAlFinal = useCallback(() => {
    const lista = listaRef.current;
    if (!lista) return;
    marcarAuto();
    lista.scrollTop = lista.scrollHeight;
    actualizarIrFinal(lista.scrollHeight);
  }, [marcarAuto, actualizarIrFinal]);

  // Mantiene visible el final de la respuesta, pero nunca sube su inicio (la foto) por encima del borde.
  const seguir = useCallback(() => {
    const lista = listaRef.current;
    const m = lista?.lastElementChild;
    if (!m) return;
    const objetivo = Math.min(m.offsetTop + m.offsetHeight - lista.clientHeight + 14, m.offsetTop - 10);
    const destino = Math.max(objetivo, lista.scrollTop);
    if (objetivo > lista.scrollTop) { marcarAuto(); lista.scrollTop = objetivo; }
    actualizarIrFinal(destino);
  }, [marcarAuto, actualizarIrFinal]);

  // Al abrir: directo al final, sin desplazamiento suave.
  useLayoutEffect(() => {
    const lista = listaRef.current;
    if (!lista) return;
    lista.style.scrollBehavior = 'auto';
    lista.scrollTop = lista.scrollHeight;
    lista.style.scrollBehavior = '';
  }, []);

  useEffect(() => () => clearTimeout(timerAuto.current), []);

  // Escape minimiza el chat. Se escucha en el documento: tocar el texto de una burbuja saca el foco
  // de la ventana y, con un listener en la ventana, Escape dejaría de funcionar.
  const minimizarRef = useRef(onMinimize);
  useEffect(() => { minimizarRef.current = onMinimize; });
  useEffect(() => {
    const alPulsar = (e) => { if (e.key === 'Escape') minimizarRef.current(); };
    document.addEventListener('keydown', alPulsar);
    return () => document.removeEventListener('keydown', alPulsar);
  }, []);

  // Mensaje nuevo: el del cliente baja al final; la respuesta del bot se sigue desde su inicio.
  const ultimoId = useRef(messages[messages.length - 1]?.id);
  useEffect(() => {
    const ultimo = messages[messages.length - 1];
    if (!ultimo || ultimo.id === ultimoId.current) return;
    ultimoId.current = ultimo.id;
    if (ultimo.isUser) bajarAlFinal();
    else seguir();
  }, [messages, bajarAlFinal, seguir]);

  // La burbuja de espera aparece un poco después del envío, para que se vea primero el mensaje.
  if (!isTyping && verEspera) setVerEspera(false);
  useEffect(() => {
    if (!isTyping) return;
    const t = setTimeout(() => setVerEspera(true), sinMovimiento() ? 0 : RETRASO_ESPERA);
    return () => clearTimeout(t);
  }, [isTyping]);

  useEffect(() => {
    if (verEspera) bajarAlFinal();
  }, [verEspera, bajarAlFinal]);

  // ── Nueva conversación: los mensajes salen escalonados y luego se reinicia ──
  const nuevaConversacion = () => {
    if (vaciando) return;
    setGirando(false);
    requestAnimationFrame(() => setGirando(true));
    if (sinMovimiento()) { onNuevaConversacion(); return; }
    setVaciando(true);
    const escalonados = Math.min(messages.length, 10);
    setTimeout(() => { setVaciando(false); onNuevaConversacion(); }, 260 + escalonados * 35);
  };

  const soloSaludo = messages.length === 1 && !messages[0].isUser && !messages[0].error;
  const verSugerencias = soloSaludo && !vaciando;

  return (
    <section className={`chat-window-container${isClosing ? ' cerrando' : ''}`} aria-label="Chat con el asistente de DriveMaster">
      <div className={`chat-header${isTyping ? ' escribiendo' : ''}`}>
        <div className="chat-header-left">
          <div className="chat-avatar">
            <ChatLogo size={19} pensando={isTyping} />
            <span className="estado-punto" />
          </div>
          <div>
            <div className="chat-header-title">Drivemaster</div>
            <div className="chat-header-subtitle">
              <span className="sub-normal">Asistente con IA</span>
              <span className="sub-escribe">Escribiendo<span className="mini-puntos" aria-hidden="true"><i /><i /><i /></span></span>
            </div>
          </div>
        </div>
        <div className="chat-header-actions">
          <button type="button" className={`header-icon-btn${girando ? ' gira' : ''}`} onClick={nuevaConversacion}
            onAnimationEnd={() => setGirando(false)} title="Nueva conversación" aria-label="Nueva conversación">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z" />
            </svg>
          </button>
          <button type="button" className="header-icon-btn" onClick={onMinimize} title="Minimizar" aria-label="Minimizar">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M19 13H5v-2h14z" /></svg>
          </button>
          <button type="button" className="header-icon-btn" onClick={onClose} title="Cerrar" aria-label="Cerrar">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </button>
        </div>
      </div>

      <div className="zona-mensajes">
        <div className="chat-messages" ref={listaRef} aria-live="polite"
          onScroll={() => { if (Date.now() >= autoHasta.current) actualizarIrFinal(); }}>
          {/* El texto lo escribe un modelo: puede equivocarse aunque los datos de la tarjeta salgan de la base. */}
          <p className="aviso-ia">Respondo con IA usando el catálogo real. Confirma precio y stock en la ficha antes de comprar.</p>
          {messages.map((message, i) => (
            <MessageBubble
              key={message.id}
              message={message}
              animar={!idsIniciales.has(message.id)}
              saliendo={vaciando}
              retrasoSalida={Math.min(i, 10) * 35}
              onReintentar={onReintentar}
              onVerProducto={onVerProducto}
              onCrecer={seguir}
            />
          ))}
          {verEspera && isTyping && !vaciando && <BurbujaEspera />}
        </div>
        <button type="button" className={`ir-final${irFinal ? ' ver' : ''}`} tabIndex={irFinal ? 0 : -1}
          onClick={bajarAlFinal} aria-label="Ir al final" title="Ir al final">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z" /></svg>
        </button>
      </div>

      <div className={`sugerencias${verSugerencias ? '' : ' oculta'}`} inert={!verSugerencias}>
        {/* La clave cambia con cada conversación: los botones vuelven a entrar uno tras otro. */}
        {SUGERENCIAS.map((s, i) => (
          <button key={`${messages[0]?.id}-${s.texto}`} type="button" className="sugerencia" style={{ '--i': i }} onClick={() => onSugerencia(s.mensaje)}>
            {s.icono}{s.texto}
          </button>
        ))}
      </div>

      <ChatInput value={inputValue} onChange={onInputChange} onSend={enviarCampo} ocupado={isTyping} />
    </section>
  );
};

export default ChatWindow;
