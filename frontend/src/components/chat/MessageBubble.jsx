import React from 'react';
import './chat.css';

const MessageBubble = ({ message }) => {
  return (
    <div className={`message-bubble ${message.isUser ? 'user-message' : 'bot-message'}`}>
      <div className="message-content">
        <p>{message.text}</p>
        <span className="message-time">{message.timestamp}</span>
      </div>
    </div>
  );
};

export default MessageBubble;