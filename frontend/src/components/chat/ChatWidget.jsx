import React, { useState, useCallback, useEffect } from 'react';
import ChatWindow from './ChatWindow';
import './chat.css';

const ChatWidget = () => {
    const [chatState, setChatState] = useState('closed'); // 'closed' | 'open' | 'minimized'
    const [isClosing, setIsClosing] = useState(false);

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
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="white">
                        <path d="M12 2a8 8 0 00-8 8v8a4 4 0 004 4h8a4 4 0 004-4v-8a8 8 0 00-8-8z"/>
                        <path d="M16 12a4 4 0 11-8 0 4 4 0 018 0z"/>
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
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="white">
                        <path d="M12 2a8 8 0 00-8 8v8a4 4 0 004 4h8a4 4 0 004-4v-8a8 8 0 00-8-8z"/>
                    </svg>
                    <span>Chat Drivemaster</span>
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
                onClose={handleToggle}
                onMinimize={handleMinimize}
                isClosing={isClosing}
            />
        </div>
    );
};

export default ChatWidget;