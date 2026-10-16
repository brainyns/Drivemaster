import React from 'react';

/**
 * Logo del asesor: un globo de chat con forma de tacómetro. Al montarse, la aguja hace el
 * "arranque" una vez; con pensando (mientras el bot responde) acelera en bucle. Va en un solo
 * color (currentColor) para servir sobre el naranja del botón, la barra y la cabecera.
 */
const ChatLogo = ({ size = 24, pensando = false }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" className={`lg-tacometro${pensando ? ' pensando' : ''}`} fill="none" aria-hidden="true">
    <path d="M3.5 11.5A8.5 8.5 0 0 1 20.5 11.5V15.5A2 2 0 0 1 18.5 17.5H8L3.5 21.3Z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M6.43 8.13 4.89 6.84M12 5V3M17.57 8.13 19.11 6.84" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    <g transform="rotate(30 12 12.8)">
      <path className="lg-tacometro-aguja" d="M12 7.43 14.01 11.9A2.2 2.2 0 1 1 9.99 11.9Z" fill="currentColor" />
    </g>
  </svg>
);

export default ChatLogo;
