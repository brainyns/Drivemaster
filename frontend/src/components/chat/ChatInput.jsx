import React from 'react';

const ChatInput = ({ value, onChange, onSend, isTyping }) => {
  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      onSend(value);
    }
  };

  return (
    <div className="chat-input-container">
      <div className="chat-input-wrapper">
        <textarea
          className="chat-input"
          value={value}
          onChange={onChange}
          placeholder="Escribe tu mensaje..."
          rows={1}
          onKeyPress={handleKeyPress}
          disabled={isTyping}
        />
        <button 
          className="send-button"
          onClick={() => onSend(value)}
          disabled={!value.trim() || isTyping}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
      </div>
    </div>
  );
};

export default ChatInput;