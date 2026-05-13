import React, { useState, useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble';
import ChatInput from './ChatInput';

const ChatWindow = ({ onClose, onMinimize, isClosing }) => {
  const [messages, setMessages] = useState([
    {
      id: 1,
      text: '¡Hola! Soy tu asesor virtual de Drivemaster. ¿En qué puedo ayudarte hoy?',
      isUser: false,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  ]);
  const [isTyping, setIsTyping] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (text) => {
    if (!text.trim()) return;

    const userMessage = {
      id: Date.now(),
      text: text,
      isUser: true,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsTyping(true);

    try {
      const response = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mensaje: text })
      });

      if (!response.ok) throw new Error('Network response was not ok');

      const data = await response.json();

      setTimeout(() => {
        const botMessage = {
          id: Date.now() + 1,
          text: data.respuesta,
          isUser: false,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        setMessages(prev => [...prev, botMessage]);
        setIsTyping(false);
      }, 800);
    } catch (error) {
      console.error('Error:', error);
      setIsTyping(false);
      setMessages(prev => [...prev, {
        id: Date.now() + 1,
        text: 'Lo siento, hubo un error. Por favor intenta de nuevo.',
        isUser: false,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }]);
    }
  };

  return (
      <div className={`chat-window-container ${isClosing ? 'closing' : ''}`}>
        <div className="chat-header">
          <div className="chat-header-left">
            <div className="chat-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="white">
                <path d="M12 2a8 8 0 00-8 8v8a4 4 0 004 4h8a4 4 0 004-4v-8a8 8 0 00-8-8z"/>
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