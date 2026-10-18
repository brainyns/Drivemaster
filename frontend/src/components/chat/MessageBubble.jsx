import React, { useEffect, useState } from 'react';
import { sinMovimiento } from './movimiento';

const formatoPrecio = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const precio = (n) => `$${formatoPrecio.format(n)}`;

const IconoReloj = () => (
  <svg className="reloj" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
    <circle cx="12" cy="12" r="8" /><path d="M12 8v4l2.5 2" />
  </svg>
);
const IconoCheck = () => (
  <svg className="chk" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M5 12.5l4.5 4.5L19 7.5" />
  </svg>
);
const IconoFlecha = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M5 12h14M13 6l6 6-6 6" />
  </svg>
);
const IconoImagen = () => (
  <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <rect x="3" y="5" width="18" height="14" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="m21 16-5-5-9 8" />
  </svg>
);
const IconoSinFoto = ({ size = 26 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M3 3l18 18" /><path d="M9.5 5h5L16 7h3a2 2 0 0 1 2 2v8.5M17.5 19H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.5" /><path d="M9.9 10.1a3 3 0 0 0 4 4" />
  </svg>
);
const IconoAlerta = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5h.01" />
  </svg>
);
const IconoReintentar = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4z" />
  </svg>
);

// Misma lógica de disponibilidad que el agente: ENCARGO con días, STOCK con o sin existencias.
function Disponibilidad({ producto }) {
  if (producto.tipo === 'ENCARGO') {
    const dias = producto.diasEntregaEncargo;
    return <span className="chip chip-info">{dias ? `Por encargo · ${dias} días` : 'Por encargo'}</span>;
  }
  if (producto.stockActual == null) return null;
  if (producto.stockActual <= 0) return <span className="chip chip-agotado">Agotado</span>;
  return <span className="chip chip-ok">{producto.stockActual} {producto.stockActual === 1 ? 'disponible' : 'disponibles'}</span>;
}

/** Foto con esqueleto mientras descarga; pasa de borrosa a nítida con el onLoad del <img>. */
function FotoProducto({ producto }) {
  const [cargada, setCargada] = useState(false);
  const [fallo, setFallo] = useState(false);

  if (!producto.imagenUrl || fallo) {
    return (
      <div className="producto-foto sin-foto">
        <IconoSinFoto />
        <span>Sin foto</span>
      </div>
    );
  }
  return (
    <div className={`producto-foto${cargada ? ' cargada' : ''}`}>
      <div className="shimmer"><IconoImagen /></div>
      <img className="img" src={producto.imagenUrl} alt={producto.nombre} onLoad={() => setCargada(true)} onError={() => setFallo(true)} />
    </div>
  );
}

function Miniatura({ producto, activa, indice, onClick }) {
  const [fallo, setFallo] = useState(false);
  const conFoto = producto.imagenUrl && !fallo;
  return (
    <button
      type="button"
      className={`mini${activa ? ' on' : ''}${conFoto ? '' : ' sin'}`}
      style={{ '--i': indice }}
      aria-label={`Ver ${producto.nombre}`}
      aria-pressed={activa}
      onClick={onClick}
    >
      {conFoto ? <img src={producto.imagenUrl} alt="" loading="lazy" onError={() => setFallo(true)} /> : <IconoSinFoto size={18} />}
    </button>
  );
}

/** Tarjeta destacada: foto grande del producto elegido y, si hay varios, miniaturas para cambiar. */
function TarjetaProducto({ productos, seleccionado, onSeleccionar }) {
  const [cambiando, setCambiando] = useState(false);
  const [mostrado, setMostrado] = useState(seleccionado);
  const producto = productos[mostrado] || productos[0];

  // La foto cambia enseguida; los datos salen con un fundido corto y entran los del producto nuevo.
  const elegir = (i) => {
    if (i === seleccionado) return;
    onSeleccionar(i);
    if (sinMovimiento()) { setMostrado(i); return; }
    setCambiando(true);
    setTimeout(() => { setMostrado(i); setCambiando(false); }, 160);
  };

  const meta = [producto.marca, producto.tienda].filter(Boolean).join(' · ');

  return (
    <div className="producto">
      <FotoProducto key={`foto-${productos[seleccionado]?.id}`} producto={productos[seleccionado] || producto} />
      {productos.length > 1 && (
        <div className="minis">
          {productos.map((p, i) => (
            <Miniatura key={p.id} producto={p} indice={i} activa={i === seleccionado} onClick={() => elegir(i)} />
          ))}
        </div>
      )}
      <div className={`producto-info${cambiando ? ' cambiando' : ''}`} key={`info-${producto.id}`}>
        <div className="p-nombre" style={{ '--d': '60ms' }}>{producto.nombre}</div>
        {meta && <div className="p-meta" style={{ '--d': '120ms' }}>{meta}</div>}
        <div className="p-fila" style={{ '--d': '180ms' }}>
          {producto.precioVenta != null && <span className="precio">{precio(producto.precioVenta)}</span>}
          <Disponibilidad producto={producto} />
        </div>
      </div>
    </div>
  );
}

/**
 * Texto del bot palabra por palabra, con cursor. Los lectores de pantalla reciben el texto
 * completo de una vez (span .sr) y no la animación.
 */
function TextoEscrito({ texto, animar, saltar, retraso, onAvance, onFin }) {
  const [partes] = useState(() => texto.split(/(\s+)/));
  const [avance, setMostradas] = useState(animar ? 0 : partes.length);
  // Un toque en la burbuja muestra el texto completo de una vez.
  const mostradas = saltar ? partes.length : avance;
  const terminado = mostradas >= partes.length;

  useEffect(() => {
    if (terminado) { onFin(); return; }
    const t = setTimeout(() => {
      setMostradas(n => {
        let siguiente = n + 1;
        // Los espacios van pegados a la palabra anterior: así avanza de palabra en palabra.
        if (partes[siguiente] && /^\s+$/.test(partes[siguiente])) siguiente += 1;
        return siguiente;
      });
      onAvance();
    }, mostradas === 0 ? retraso : 30 + Math.random() * 35);
    return () => clearTimeout(t);
    // onAvance y onFin cambian en cada render del padre; solo importa el avance del texto.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mostradas, terminado]);

  if (!animar) return texto;
  return (
    <>
      <span className="sr">{texto}</span>
      <span aria-hidden="true">{partes.slice(0, mostradas).join('')}</span>
      {!terminado && <span className="cursor" aria-hidden="true" />}
    </>
  );
}

/**
 * Un mensaje de la conversación. "animar" es true solo para los mensajes que llegan con la ventana
 * abierta: los que ya estaban (guardados o recibidos con el chat minimizado) aparecen completos.
 */
const MessageBubble = ({ message, animar, saliendo, retrasoSalida, onReintentar, onVerProducto, onCrecer }) => {
  const productos = !message.isUser && Array.isArray(message.productos) ? message.productos.filter(p => p?.id) : [];
  const conProducto = productos.length > 0;
  const [seleccionado, setSeleccionado] = useState(0);
  const [saltar, setSaltar] = useState(false);
  const animarTexto = animar && !message.isUser && !message.error && !message.saludo && !sinMovimiento();

  const salida = saliendo ? ' saliendo' : '';
  const estiloSalida = saliendo ? { '--retraso': `${retrasoSalida}ms` } : undefined;

  if (message.isUser) {
    return (
      <div className={`message-bubble user-message${animar ? ' entra-u' : ''}${salida}`} style={estiloSalida}>
        <div className="message-content">{message.text}</div>
        <span className="message-time">
          {message.timestamp}
          <span className={`estado-envio${message.estado === 'enviando' ? '' : ' enviado'}`} title={message.estado === 'enviando' ? 'Enviando' : 'Enviado'}>
            <IconoReloj /><IconoCheck />
          </span>
        </span>
      </div>
    );
  }

  if (message.error) {
    return (
      <div className={`message-bubble bot-message${animar ? ' entra-error' : ''}${salida}`} style={estiloSalida}>
        <div className="burbuja-bot burbuja-error">
          <p className="bot-texto"><IconoAlerta /><span>{message.text}</span></p>
          {message.reintento && (
            <button type="button" className="reintentar" onClick={() => onReintentar(message.id)}>
              <IconoReintentar /> Reintentar
            </button>
          )}
        </div>
        <span className="message-time">{message.timestamp}</span>
      </div>
    );
  }

  return (
    <div className={`message-bubble bot-message${animar ? ' entra-b' : ''}${conProducto ? ' con-producto' : ''}${salida}`} style={estiloSalida}>
      <div className="burbuja-bot">
        {conProducto && <TarjetaProducto productos={productos} seleccionado={seleccionado} onSeleccionar={setSeleccionado} />}
        <p className="bot-texto" onClick={() => setSaltar(true)}>
          <TextoEscrito
            texto={message.text}
            animar={animarTexto}
            saltar={saltar}
            retraso={conProducto ? 450 : 120}
            onAvance={onCrecer}
            onFin={onCrecer}
          />
        </p>
        {conProducto && (
          <button type="button" className="btn-ver" onClick={() => onVerProducto(productos[seleccionado].id)}>
            Ver producto <IconoFlecha />
          </button>
        )}
      </div>
      <span className="message-time">{message.timestamp}</span>
    </div>
  );
};

export default MessageBubble;
