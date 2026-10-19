import { useEffect, useRef, useState } from "react";
import { preguntarAsistente } from "../services/informesTiendaService";
import { miTienda } from "../services/tiendaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Icon from "../components/public/Icon";
import "../css/asistente-tienda.css";

const SUGERENCIAS = [
  "¿Cómo van mis ventas frente al mes pasado?",
  "¿Qué debo reponer y cuánto?",
  "¿A qué proveedor le compro más barato?",
  "¿Qué pedidos despacho primero?",
  "¿Cuánto me van a pagar y cuándo?",
  "¿Qué productos no se venden hace 3 meses?",
  "¿Qué producto me deja más ganancia?",
];

const PESTANAS = [
  { id: "seguridad", etiqueta: "Seguridad" },
  { id: "modelo", etiqueta: "Modelo" },
  { id: "herramienta", etiqueta: "Herramienta" },
];

const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);

/** Qué pasó en el sistema para responder el último mensaje: seguridad, modelo y datos consultados. */
function DetrasDeEscena({ detras }) {
  const [pestana, setPestana] = useState("seguridad");

  // Pestañas con el patrón de teclado de ARIA: flechas, Inicio y Fin mueven el foco y la selección
  const mover = (e) => {
    const i = PESTANAS.findIndex((p) => p.id === pestana);
    const destino = { ArrowRight: i + 1, ArrowLeft: i - 1, Home: 0, End: PESTANAS.length - 1 }[e.key];
    if (destino == null) return;
    e.preventDefault();
    const siguiente = PESTANAS[(destino + PESTANAS.length) % PESTANAS.length].id;
    setPestana(siguiente);
    document.getElementById(`as-tab-${siguiente}`)?.focus();
  };

  return (
    <section className="as-panel as-detras" aria-labelledby="as-detras-titulo">
      <header className="as-panel__cabecera">
        <h2 id="as-detras-titulo">Detrás de escena</h2>
        <p>Lo que pasó en el sistema para responder el último mensaje.</p>
      </header>
      <div className="as-pestanas" role="tablist" aria-label="Detrás de escena">
        {PESTANAS.map((p) => (
          <button key={p.id} type="button" role="tab" id={`as-tab-${p.id}`} aria-selected={pestana === p.id}
            aria-controls={`as-tabpanel-${p.id}`} className={`as-pestana${pestana === p.id ? " is-activa" : ""}`}
            tabIndex={pestana === p.id ? 0 : -1} onKeyDown={mover} onClick={() => setPestana(p.id)}>
            {p.etiqueta}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`as-tabpanel-${pestana}`} aria-labelledby={`as-tab-${pestana}`} className="as-detras__cuerpo" tabIndex={0}>
        {!detras ? (
          <p className="as-tenue">Envía un mensaje para ver aquí qué datos se usaron y cómo se protegió tu tienda.</p>
        ) : pestana === "seguridad" ? (
          <ul className="as-lista as-lista--ok">{detras.seguridad.map((s) => <li key={s}>{s}</li>)}</ul>
        ) : pestana === "modelo" ? (
          <dl className="as-datos">
            <div><dt>Modelo</dt><dd>{detras.modelo || "Sin respuesta del modelo"}</dd></div>
            <div><dt>Tokens enviados</dt><dd>{detras.tokensEntrada != null ? num(detras.tokensEntrada) : "—"}</dd></div>
            <div><dt>Tokens de la respuesta</dt><dd>{detras.tokensSalida != null ? num(detras.tokensSalida) : "—"}</dd></div>
            <div><dt>Tiempo</dt><dd>{(detras.ms / 1000).toFixed(1)} s</dd></div>
          </dl>
        ) : (
          <>
            <p className="as-herramienta">{detras.herramienta}</p>
            <ul className="as-lista">{detras.datos.map((d) => <li key={d}>{d}</li>)}</ul>
          </>
        )}
      </div>
    </section>
  );
}

/**
 * Asistente de la TIENDA: preguntas en lenguaje natural sobre sus propios datos, con un panel que muestra qué pasó
 * detrás de cada respuesta. Solo lee y sugiere; el backend toma la tienda de la sesión.
 */
export default function AsistenteTienda({ token }) {
  const [tienda, setTienda] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [texto, setTexto] = useState("");
  const [pensando, setPensando] = useState(false);
  const [detras, setDetras] = useState(null);
  const fin = useRef(null);
  const campo = useRef(null);

  useEffect(() => {
    let vivo = true;
    miTienda(token).then((t) => { if (vivo) setTienda(t); }).catch(() => {});
    return () => { vivo = false; };
  }, [token]);

  useEffect(() => { fin.current?.scrollIntoView({ block: "nearest" }); }, [mensajes, pensando]);

  async function enviar(pregunta) {
    const limpia = pregunta.trim();
    if (!limpia || pensando) return;
    const historial = mensajes.filter((m) => !m.error).slice(-6).map(({ rol, texto: t }) => ({ rol, texto: t }));
    setMensajes((m) => [...m, { rol: "usuario", texto: limpia }]);
    setTexto("");
    setPensando(true);
    try {
      const r = await preguntarAsistente(token, limpia, historial);
      setMensajes((m) => [...m, { rol: "asistente", texto: r.respuesta, error: r.error }]);
      setDetras(r.detras);
    } catch (e) {
      setMensajes((m) => [...m, { rol: "asistente", texto: e.message, error: true }]);
    } finally {
      setPensando(false);
      campo.current?.focus();
    }
  }

  const nombre = tienda?.nombre || "tu tienda";

  return (
    <div className="ss-panel--tienda as-root">
      <EncabezadoPagina titulo="Asistente de tu tienda"
        descripcion="Pregunta por tus ventas, inventario, pedidos y dinero. La IA solo lee y sugiere: nada cambia sin ti." />

      <div className="as-rejilla">
        <section className="as-panel as-chat" aria-labelledby="as-chat-titulo">
          <header className="as-chat__cabecera">
            <h2 id="as-chat-titulo">Asistente DriveMaster</h2>
            <ChipEstado tono="acento"><Icon name="lock" size={12} /> Solo ve los datos de {nombre}</ChipEstado>
          </header>

          {mensajes.length === 0 ? (
            <div className="as-inicio">
              <p className="as-tenue">Prueba con una de estas preguntas:</p>
              <ul className="as-sugerencias ui-filtros">
                {SUGERENCIAS.map((s) => <li key={s}><button type="button" className="ui-chip" onClick={() => enviar(s)}>{s}</button></li>)}
              </ul>
            </div>
          ) : (
            <ol className="as-mensajes" aria-live="polite">
              {mensajes.map((m, i) => (
                <li key={i} className={`as-msg is-${m.rol}${m.error ? " is-error" : ""}`}>
                  <span className="as-solo-lectores">{m.rol === "usuario" ? "Tú:" : "Asistente:"}</span>{m.texto}
                </li>
              ))}
              {pensando && <li className="as-msg is-asistente is-pensando" role="status">Revisando los datos de tu tienda…</li>}
              <li ref={fin} aria-hidden="true" />
            </ol>
          )}

          <form className="as-form" onSubmit={(e) => { e.preventDefault(); enviar(texto); }}>
            <label htmlFor="as-campo" className="ui-field__label">Mensaje</label>
            <div className="as-form__fila">
              <input id="as-campo" ref={campo} className="ui-field__input" value={texto} maxLength={500} autoComplete="off"
                placeholder="Ej.: ¿qué producto se vende más y cuánto stock me queda?" onChange={(e) => setTexto(e.target.value)} />
              <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande" disabled={pensando || !texto.trim()} aria-busy={pensando}>
                {pensando ? "Pensando…" : "Enviar"}
              </button>
            </div>
          </form>
          <p className="as-tenue as-aviso">Lo escribe una IA con los datos de tu tienda: revisa las cifras antes de comprar o cambiar precios.</p>
        </section>

        <DetrasDeEscena detras={detras} />
      </div>
    </div>
  );
}
