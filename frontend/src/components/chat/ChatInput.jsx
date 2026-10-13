import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { sinMovimiento } from './movimiento';

const MAX_CARACTERES = 500;
const AVISO_CARACTERES = 400;
const ALTO_MAXIMO = 120;
const EJEMPLOS = ['Escribe tu mensaje…', 'Ej: pastillas de freno para Mazda 3', 'Ej: filtro de aceite', 'Ej: batería para Chevrolet Spark'];

/**
 * Campo del chat. Crece con el texto hasta 120 px, rota textos de ejemplo mientras está vacío y
 * muestra un contador desde 400 caracteres. El botón tiene tres estados: listo (hay texto),
 * vuela (al enviar) y ocupado (el bot responde). El campo no se bloquea mientras el bot responde:
 * el cliente puede ir escribiendo; solo el envío espera.
 */
const ChatInput = ({ value, onChange, onSend, ocupado }) => {
  const campoRef = useRef(null);
  const [ejemplo, setEjemplo] = useState(0);
  const [fasePh, setFasePh] = useState(''); // '' | 'sale' | 'baja'
  const [pop, setPop] = useState(false);
  const [vuela, setVuela] = useState(false);

  const largo = value.length;
  const listo = value.trim().length > 0 && !ocupado;

  // Alto automático: se mide el contenido y se limita a 120 px (después hay scroll).
  useLayoutEffect(() => {
    const campo = campoRef.current;
    if (!campo) return;
    campo.style.height = 'auto';
    campo.style.height = `${Math.min(campo.scrollHeight, ALTO_MAXIMO)}px`;
  }, [value]);

  // El botón "salta" cuando pasa a estar listo (se ajusta al renderizar, no en un efecto).
  const [listoAntes, setListoAntes] = useState(listo);
  if (listo !== listoAntes) {
    setListoAntes(listo);
    if (listo) setPop(true);
  }

  // Texto de ejemplo que rota: sale hacia arriba y el siguiente entra desde abajo.
  useEffect(() => {
    if (value || sinMovimiento()) return;
    let t1, r1, r2;
    const intervalo = setInterval(() => {
      setFasePh('sale');
      t1 = setTimeout(() => {
        setEjemplo(i => (i + 1) % EJEMPLOS.length);
        setFasePh('baja');
        // Dos cuadros: el navegador pinta la posición de abajo sin transición y luego anima.
        r1 = requestAnimationFrame(() => { r2 = requestAnimationFrame(() => setFasePh('')); });
      }, 260);
    }, 3400);
    return () => {
      clearInterval(intervalo);
      clearTimeout(t1);
      cancelAnimationFrame(r1);
      cancelAnimationFrame(r2);
      setFasePh('');
    };
  }, [value]);

  const enviar = () => {
    if (!listo) return;
    setVuela(false);
    requestAnimationFrame(() => setVuela(true));
    setTimeout(() => setVuela(false), 620);
    onSend(value);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      enviar();
    }
  };

  const clasesBoton = ['send-button', listo && 'listo', pop && 'pop', vuela && 'vuela', ocupado && 'ocupado']
    .filter(Boolean).join(' ');
  const clasesPh = ['ph', largo > 0 ? 'oculto' : fasePh].filter(Boolean).join(' ');

  return (
    <div className="chat-input-container">
      <div className="chat-input-wrapper">
        <div className="campo">
          <textarea
            ref={campoRef}
            className="chat-input"
            value={value}
            onChange={e => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            maxLength={MAX_CARACTERES}
            aria-label="Escribe tu mensaje"
          />
          <span className={clasesPh} aria-hidden="true">{EJEMPLOS[ejemplo]}</span>
        </div>
        {largo >= AVISO_CARACTERES && (
          <span className={`contador${largo >= 480 ? ' alerta' : ''}`}>{largo}/{MAX_CARACTERES}</span>
        )}
        <button
          type="button"
          className={clasesBoton}
          onClick={enviar}
          disabled={!listo}
          onAnimationEnd={e => { if (e.target === e.currentTarget) setPop(false); }}
          aria-label={ocupado ? 'Esperando respuesta' : 'Enviar'}
        >
          <svg className="flecha" width="19" height="19" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M2.01 21 23 12 2.01 3 2 10l15 2-15 2z" />
          </svg>
          <span className="rueda" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
};

export default ChatInput;
