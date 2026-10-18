import React, { useState } from 'react';
import ChatWindow from './ChatWindow';
import { useChat } from './useChat';
import './chat.css';

const ChatWidget = () => {
    const [chatState, setChatState] = useState('closed'); // 'closed' | 'open' | 'minimized'
    const [isClosing, setIsClosing] = useState(false);
    // El estado de la conversación vive aquí: ChatWindow se desmonta al minimizar o cerrar.
    const chat = useChat();

    const handleToggle = () => {
        if (chatState === 'open') {
            // Iniciar animación de cierre
            setIsClosing(true);
            setTimeout(() => {
                setChatState('closed');
                setIsClosing(false);
            }, 200); // Duración de la animación
        } else {
            setChatState('open');
        }
    };

    const handleMinimize = () => {
        setChatState('minimized');
    };

    const handleExpandFromMinimized = () => {
        setChatState('open');
    };

    // Si el chat está cerrado, mostrar solo el botón flotante
    if (chatState === 'closed') {
        return (
            <div className="chat-widget-container">
                <div className="chat-toggle-button" onClick={handleToggle} title="Abrir chat">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="white" stroke="white" strokeWidth="1.25" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M7 8a4 4 0 0 1 4-4h2a4 4 0 0 1 4 4v2a3 3 0 0 1-1.5 2.6l-3 1.8a1 1 0 0 0-.5.9v1.2a1 1 0 0 1-2 0v-1.2a1 1 0 0 0-.5-.9l-3-1.8A3 3 0 0 1 7 10Z" />
                        <path d="M12 6v2" />
                        <circle cx="12" cy="15" r="1.25" fill="white" stroke="none" />
                    </svg>
                </div>
            </div>
        );
    }

    // Si está minimizado, mostrar barra flotante
    if (chatState === 'minimized') {
        return (
            <div className="chat-widget-container">
                <div className="chat-minimized-bar" onClick={handleExpandFromMinimized}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="white" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M7 8a4 4 0 0 1 4-4h2a4 4 0 0 1 4 4v2a3 3 0 0 1-1.5 2.6l-3 1.8a1 1 0 0 0-.5.9v1.2a1 1 0 0 1-2 0v-1.2a1 1 0 0 0-.5-.9l-3-1.8A3 3 0 0 1 7 10Z" />
                        <path d="M12 6v2" />
                        <circle cx="12" cy="15" r="1.25" fill="white" stroke="none" />
                    </svg>
                    <span>{chat.isTyping ? 'Drivemaster está escribiendo…' : 'Chat Drivemaster'}</span>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="white" style={{marginLeft: 'auto'}}>
                        <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"/>
                    </svg>
                </div>
            </div>
        );
    }

    // Estado 'open' (puede estar en animación de cierre)
    return (
        <div className="chat-widget-container">
            <ChatWindow
                messages={chat.messages}
                isTyping={chat.isTyping}
                onSend={chat.enviar}
                onNuevaConversacion={chat.nuevaConversacion}
                onClose={handleToggle}
                onMinimize={handleMinimize}
                isClosing={isClosing}
            />
        </div>
    );
};

export default ChatWidget;
