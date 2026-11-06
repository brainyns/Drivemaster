import React, { useEffect, useRef } from 'react';
import ChatLogo from './ChatLogo';
import { SUGERENCIAS } from './sugerencias';

// Solo preguntas que el asistente sabe responder con el catálogo (ver el prompt del agente en n8n).
const PREGUNTAS = [
  { texto: 'Ayúdame a encontrar un repuesto', mensaje: 'Ayúdame a encontrar un repuesto para mi carro' },
  { texto: '¿Cómo sé si es compatible con mi carro?', mensaje: '¿Cómo sé si un repuesto es compatible con mi carro?' },
  { texto: '¿Está disponible o es por encargo?', mensaje: '¿Cómo sé si un repuesto está disponible o llega por encargo?' },
  { texto: '¿Cómo compro un repuesto?', mensaje: '¿Cómo compro un repuesto en DriveMaster?' }
];

const Flecha = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z" /></svg>
);

/** Pestaña de inicio: saludo, acceso a la conversación, búsquedas rápidas y preguntas frecuentes. */
const ChatInicio = ({ messages, isTyping, onAbrirChat, onPregunta, onClose, onMinimize, isClosing, entrada, pie }) => {
  // Escape minimiza, igual que en la pestaña de chat.
  const minimizarRef = useRef(onMinimize);
  useEffect(() => { minimizarRef.current = onMinimize; });
  useEffect(() => {
    const alPulsar = (e) => { if (e.key === 'Escape') minimizarRef.current(); };
    document.addEventListener('keydown', alPulsar);
    return () => document.removeEventListener('keydown', alPulsar);
  }, []);

  const hayConversacion = messages.some(m => m.isUser);
  const ultimoBot = [...messages].reverse().find(m => !m.isUser);

  return (
    <section className={`chat-window-container vista-inicio${entrada}${isClosing ? ' cerrando' : ''}`} aria-label="Inicio del chat de DriveMaster">
      <div className="inicio-scroll">
        <div className="inicio-hero">
          <span className="hero-marca" aria-hidden="true"><ChatLogo size={210} /></span>
          <div className="hero-top">
            <div className="chat-header-left">
              <div className="chat-avatar">
                <ChatLogo size={19} pensando={isTyping} />
                <span className="estado-punto" />
              </div>
              <div>
                <div className="chat-header-title">Drivemaster</div>
                <div className="hero-sub">En línea, responde al instante</div>
              </div>
            </div>
            <button type="button" className="header-icon-btn" onClick={onClose} title="Cerrar" aria-label="Cerrar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
              </svg>
            </button>
          </div>
          <h2 className="hero-saludo">Hola. ¿Qué repuesto buscas hoy?</h2>
        </div>

        <div className="inicio-cuerpo">
          <button type="button" className="tarjeta-chat" onClick={onAbrirChat}>
            <span className="tc-icono">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true"><path d="M22 2 11 13M22 2l-7 20-4-9-9-4z" /></svg>
            </span>
            <span className="tc-texto">
              <b>{hayConversacion ? 'Sigue tu conversación' : 'Chatea con el asistente'}</b>
              <span>
                {isTyping ? 'Escribiendo una respuesta…'
                  : hayConversacion && ultimoBot ? ultimoBot.text
                  : 'Busca por repuesto, marca o vehículo.'}
              </span>
            </span>
            <span className="tc-ir" aria-hidden="true">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M9 5v2h6.59L4 18.59 5.41 20 17 8.41V15h2V5z" /></svg>
            </span>
          </button>

          <div className="inicio-bloque">
            <h3>Búsquedas rápidas</h3>
            <div className="inicio-chips">
              {SUGERENCIAS.map(s => (
                <button key={s.texto} type="button" className="sugerencia" disabled={isTyping} onClick={() => onPregunta(s.mensaje)}>
                  {s.icono}{s.texto}
                </button>
              ))}
            </div>
          </div>

          <div className="inicio-bloque faq">
            <h3>Preguntas frecuentes</h3>
            <ul>
              {PREGUNTAS.map(p => (
                <li key={p.texto}>
                  <button type="button" disabled={isTyping} onClick={() => onPregunta(p.mensaje)}>
                    <span className="faq-icono" aria-hidden="true">?</span>
                    <span className="faq-texto">{p.texto}</span>
                    <Flecha />
                  </button>
                </li>
              ))}
            </ul>
          </div>

          <p className="aviso-ia">Respondo con IA usando el catálogo real. Confirma precio y stock en la ficha antes de comprar.</p>
        </div>
      </div>
      {pie}
    </section>
  );
};

/** Barra inferior para cambiar entre Inicio y Chat. "nuevo" marca una respuesta que llegó estando en Inicio. */
export const PestanasChat = ({ vista, onVista, nuevo }) => (
  <nav className="chat-pestanas" aria-label="Secciones del chat">
    <button type="button" className={vista === 'inicio' ? 'on' : ''} aria-current={vista === 'inicio' ? 'page' : undefined} onClick={() => onVista('inicio')}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" /></svg>
      Inicio
    </button>
    <button type="button" className={vista === 'chat' ? 'on' : ''} aria-current={vista === 'chat' ? 'page' : undefined} onClick={() => onVista('chat')}
      aria-label={nuevo ? 'Chat, respuesta nueva' : undefined}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.9A8 8 0 1 1 21 12z" /></svg>
      Chat
      {nuevo && <span className="pestana-punto" aria-hidden="true" />}
    </button>
  </nav>
);

export default ChatInicio;
