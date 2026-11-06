import React, { useEffect, useRef, useState } from 'react';
import ChatWindow from './ChatWindow';
import ChatInicio, { PestanasChat } from './ChatInicio';
import ChatLogo from './ChatLogo';
import { useChat } from './useChat';
import { chatVisto, marcarChatVisto } from './chatSesion';
import { sinMovimiento } from './movimiento';
import './chat.css';

const DURACION_CIERRE = 200;
const RETRASO_INVITACION = 1200;
// En pantallas pequeñas la ventana ocupa todo: al ir a un producto se minimiza para verlo.
const ANCHO_MOVIL = 520;

const ChatWidget = ({ onVerProducto }) => {
    const [chatState, setChatState] = useState('closed'); // 'closed' | 'open' | 'minimized'
    const [isClosing, setIsClosing] = useState(false);
    // El estado de la conversación vive aquí: ChatWindow se desmonta al minimizar o cerrar.
    // El borrador del campo también, para no perderlo al minimizar.
    const chat = useChat();
    const [borrador, setBorrador] = useState('');
    // Hasta que el cliente abre el chat por primera vez, el botón llama la atención con ondas.
    const [visto, setVisto] = useState(chatVisto);
    const [invitacion, setInvitacion] = useState(false);
    const [noLeidos, setNoLeidos] = useState(0);
    // Pestaña visible con la ventana abierta. 'cambia-vista' evita repetir la animación de apertura al cambiar de pestaña.
    const [vista, setVista] = useState('inicio'); // 'inicio' | 'chat'
    const [entrada, setEntrada] = useState('');
    // Tras cerrar o minimizar, el foco del teclado vuelve al botón que reabre el chat.
    const devolverFoco = useRef(false);
    const recibirFoco = (el) => {
        if (el && devolverFoco.current) { devolverFoco.current = false; el.focus(); }
    };

    // Invitación y la insignia "1" a los 1,2 s, solo si nunca se abrió el chat.
    useEffect(() => {
        if (visto) return;
        const t = setTimeout(() => {
            setInvitacion(true);
            setNoLeidos(n => Math.max(n, 1));
        }, RETRASO_INVITACION);
        return () => clearTimeout(t);
    }, [visto]);

    // Respuestas que llegan con la ventana cerrada, minimizada o en Inicio: se cuentan como no leídas.
    // Se ajusta al renderizar: el último mensaje cambió desde la vez anterior.
    const ultimo = chat.messages[chat.messages.length - 1];
    const [ultimoId, setUltimoId] = useState(ultimo?.id);
    if (ultimo && ultimo.id !== ultimoId) {
        setUltimoId(ultimo.id);
        if (!ultimo.isUser && (chatState !== 'open' || vista === 'inicio') && chat.messages.length > 1) setNoLeidos(n => n + 1);
    }

    // Una respuesta sin leer abre directo el chat; si no, cerrado abre Inicio y minimizado vuelve a donde estaba.
    const abrir = () => {
        setVista(v => (visto && noLeidos > 0 ? 'chat' : chatState === 'closed' ? 'inicio' : v));
        setEntrada('');
        setChatState('open');
        setInvitacion(false);
        setNoLeidos(0);
        if (!visto) { setVisto(true); marcarChatVisto(); }
    };

    // Cerrar y minimizar comparten la animación de salida de la ventana.
    const ocultar = (destino) => {
        if (isClosing) return;
        setIsClosing(true);
        devolverFoco.current = true;
        setTimeout(() => {
            setChatState(destino);
            setIsClosing(false);
        }, sinMovimiento() ? 0 : DURACION_CIERRE);
    };

    const verProducto = (id) => {
        onVerProducto?.(id);
        if (window.innerWidth <= ANCHO_MOVIL) ocultar('minimized');
    };

    if (chatState === 'closed') {
        return (
            <div className="chat-widget-container">
                {invitacion && (
                    <div className="burbuja-invita">
                        <b>¿Buscas un repuesto?</b> Dime cuál y para qué carro, y te muestro opciones.
                        <button type="button" className="x-invita" aria-label="Cerrar invitación"
                            onClick={(e) => { e.stopPropagation(); setInvitacion(false); }}>
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                                <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
                            </svg>
                        </button>
                    </div>
                )}
                <button type="button" ref={recibirFoco} className={`chat-toggle-button${visto ? ' visto' : ''}`} onClick={abrir}
                    aria-label={noLeidos ? `Abrir chat, ${noLeidos} sin leer` : 'Abrir chat'} title="Abrir chat">
                    <ChatLogo size={26} pensando={chat.isTyping} />
                    {noLeidos > 0 && <span className="insignia" aria-hidden="true">{noLeidos}</span>}
                </button>
            </div>
        );
    }

    if (chatState === 'minimized') {
        return (
            <div className="chat-widget-container">
                <button type="button" ref={recibirFoco} className="chat-minimized-bar" onClick={abrir}>
                    <ChatLogo size={20} pensando={chat.isTyping} />
                    <span>
                        {chat.isTyping ? (
                            <>Drivemaster está escribiendo<span className="mini-puntos" aria-hidden="true"><i /><i /><i /></span></>
                        ) : noLeidos ? (
                            <>Nueva respuesta <span className="num">{noLeidos}</span></>
                        ) : 'Chat Drivemaster'}
                    </span>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <path d="M7.41 15.41 12 10.83l4.59 4.58L18 14l-6-6-6 6z" />
                    </svg>
                </button>
            </div>
        );
    }

    const cambiarVista = (v) => {
        setEntrada(' cambia-vista');
        setVista(v);
        if (v === 'chat') setNoLeidos(0);
    };

    // Se envía después de montar ChatWindow para que el mensaje entre con su animación y baje al final.
    const preguntar = (texto) => {
        cambiarVista('chat');
        setTimeout(() => chat.enviar(texto), 0);
    };

    const pie = <PestanasChat vista={vista} onVista={cambiarVista} nuevo={vista === 'inicio' && noLeidos > 0} />;

    // Estado 'open' (puede estar en animación de cierre)
    if (vista === 'inicio') {
        return (
            <div className="chat-widget-container abierto">
                <ChatInicio
                    messages={chat.messages}
                    isTyping={chat.isTyping}
                    onAbrirChat={() => cambiarVista('chat')}
                    onPregunta={preguntar}
                    onClose={() => ocultar('closed')}
                    onMinimize={() => ocultar('minimized')}
                    isClosing={isClosing}
                    entrada={entrada}
                    pie={pie}
                />
            </div>
        );
    }

    return (
        <div className="chat-widget-container abierto">
            <ChatWindow
                entrada={entrada}
                pie={pie}
                messages={chat.messages}
                isTyping={chat.isTyping}
                onSend={chat.enviar}
                onReintentar={chat.reintentar}
                onNuevaConversacion={chat.nuevaConversacion}
                onClose={() => ocultar('closed')}
                onMinimize={() => ocultar('minimized')}
                isClosing={isClosing}
                inputValue={borrador}
                onInputChange={setBorrador}
                onVerProducto={verProducto}
            />
        </div>
    );
};

export default ChatWidget;
