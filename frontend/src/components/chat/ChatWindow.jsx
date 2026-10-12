import React, { useState, useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';

// La conversación (mensajes, envío y sessionId) la maneja useChat en ChatWidget.
const ChatWindow = ({ messages, isTyping, onSend, onNuevaConversacion, onClose, onMinimize, isClosing }) => {
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = (text) => {
    if (!text.trim() || isTyping) return;
    setInputValue('');
    onSend(text);
  };

  return (
      <div className={`chat-window-container ${isClosing ? 'closing' : ''}`}>
        <div className="chat-header">
          <div className="chat-header-left">
            <div className="chat-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M7 8a4 4 0 0 1 4-4h2a4 4 0 0 1 4 4v2a3 3 0 0 1-1.5 2.6l-3 1.8a1 1 0 0 0-.5.9v1.2a1 1 0 0 1-2 0v-1.2a1 1 0 0 0-.5-.9l-3-1.8A3 3 0 0 1 7 10Z" />
                <path d="M12 6v2" />
                <circle cx="12" cy="15" r="1.25" fill="white" stroke="none" />
              </svg>
            </div>
            <div>
              <div className="chat-header-title">Drivemaster</div>
              <div className="chat-header-subtitle">
                {isTyping ? (
                    <div className="typing-header-indicator">
                      <span className="dot"></span>
                      <span className="dot"></span>
                      <span className="dot"></span>
                    </div>
                ) : (
                    'Asesor virtual'
                )}
              </div>
            </div>
          </div>
          <div className="chat-header-actions">
            <button className="header-icon-btn" onClick={onNuevaConversacion} title="Nueva conversación" aria-label="Nueva conversación">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
              </svg>
            </button>
            <button className="header-icon-btn" onClick={onMinimize} title="Minimizar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M19 13H5v-2h14v2z"/>
              </svg>
            </button>
            <button className="header-icon-btn" onClick={onClose} title="Cerrar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
              </svg>
            </button>
          </div>
        </div>

        <div className="chat-messages">
          {messages.map(message => (
              <MessageBubble key={message.id} message={message} />
          ))}
          <div ref={messagesEndRef} />
        </div>

        <ChatInput
            value={inputValue}
            onChange={e => setInputValue(e.target.value)}
            onSend={handleSendMessage}
            isTyping={isTyping}
        />
      </div>
  );
};

export default ChatWindow;
