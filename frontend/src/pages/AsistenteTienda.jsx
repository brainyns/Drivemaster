import { useEffect, useRef, useState } from "react";
import { preguntarAsistente } from "../services/informesTiendaService";
import { preguntarAsistenteTaller } from "../services/informesTallerService";
import { miTienda } from "../services/tiendaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Icon from "../components/public/Icon";
import "../css/asistente-tienda.css";
import { textoError } from "../utils/errores";
import { decimal } from "../utils/formato";

// La misma página sirve a la tienda y al taller: solo cambian los textos y a qué servicio se pregunta.
const TIPOS = {
  tienda: {
    negocio: "tu tienda",
    clase: "ss-panel--tienda as-root",
    preguntar: preguntarAsistente,
    descripcion: "Pregunta por tus ventas, inventario, pedidos y dinero. La IA solo lee y sugiere: nada cambia sin ti.",
    ejemplo: "Ej.: ¿qué producto se vende más y cuánto stock me queda?",
    aviso: "Lo escribe una IA con los datos de tu tienda: revisa las cifras antes de comprar o cambiar precios.",
    sugerencias: [
      "¿Cómo van mis ventas frente al mes pasado?",
      "¿Qué debo reponer y cuánto?",
      "¿A qué proveedor le compro más barato?",
      "¿Qué pedidos despacho primero?",
      "¿Cuánto me van a pagar y cuándo?",
      "¿Qué productos no se venden hace 3 meses?",
      "¿Qué producto me deja más ganancia?",
    ],
  },
  taller: {
    negocio: "tu taller",
    clase: "as-root",
    preguntar: preguntarAsistenteTaller,
    descripcion: "Pregunta por tu agenda, solicitudes, ingresos e inventario. La IA solo lee y sugiere: nada cambia sin ti.",
    ejemplo: "Ej.: ¿qué citas tengo mañana y qué piezas me faltan?",
    aviso: "Lo escribe una IA con los datos de tu taller: revisa las cifras antes de comprar, cotizar o cambiar precios.",
    sugerencias: [
      "¿Qué tengo agendado esta semana?",
      "¿Cuántas solicitudes esperan mi respuesta?",
      "¿Cómo van mis ingresos frente al mes pasado?",
      "¿Qué piezas tengo con stock bajo?",
      "¿Qué piezas estoy vendiendo en pérdida?",
      "¿Qué dicen mis clientes en las calificaciones?",
      "¿Cuánto me falta por cobrar?",
    ],
  },
};

const PESTANAS = [
  { id: "seguridad", etiqueta: "Seguridad" },
  { id: "modelo", etiqueta: "Modelo" },
  { id: "herramienta", etiqueta: "Herramienta" },
];

const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);

/** Qué pasó en el sistema para responder el último mensaje: seguridad, modelo y datos consultados. */
function DetrasDeEscena({ detras, negocio }) {
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
          <p className="as-tenue">Envía un mensaje para ver aquí qué datos se usaron y cómo se protegió {negocio}.</p>
        ) : pestana === "seguridad" ? (
          <ul className="as-lista as-lista--ok">{detras.seguridad.map((s) => <li key={s}>{s}</li>)}</ul>
        ) : pestana === "modelo" ? (
          <dl className="as-datos">
            <div><dt>Modelo</dt><dd>{detras.modelo || "Sin respuesta del modelo"}</dd></div>
            <div><dt>Tokens enviados</dt><dd>{detras.tokensEntrada != null ? num(detras.tokensEntrada) : "—"}</dd></div>
            <div><dt>Tokens de la respuesta</dt><dd>{detras.tokensSalida != null ? num(detras.tokensSalida) : "—"}</dd></div>
            <div><dt>Tiempo</dt><dd>{decimal(detras.ms / 1000)} s</dd></div>
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
 * Con tipo="taller" es el asistente del taller (el panel del taller ya tiene su nombre y lo pasa en nombreNegocio).
 */
export default function AsistenteTienda({ token, tipo = "tienda", nombreNegocio }) {
  const t = TIPOS[tipo];
  const [tienda, setTienda] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [texto, setTexto] = useState("");
  const [pensando, setPensando] = useState(false);
  const [detras, setDetras] = useState(null);
  const fin = useRef(null);
  const campo = useRef(null);

  useEffect(() => {
    if (tipo !== "tienda") return undefined;
    let vivo = true;
    miTienda(token).then((datos) => { if (vivo) setTienda(datos); }).catch(() => {});
    return () => { vivo = false; };
  }, [token, tipo]);

  useEffect(() => { fin.current?.scrollIntoView({ block: "nearest" }); }, [mensajes, pensando]);

  async function enviar(pregunta) {
    const limpia = pregunta.trim();
    if (!limpia || pensando) return;
    const historial = mensajes.filter((m) => !m.error).slice(-6).map((m) => ({ rol: m.rol, texto: m.texto }));
    setMensajes((m) => [...m, { rol: "usuario", texto: limpia }]);
    setTexto("");
    setPensando(true);
    try {
      const r = await t.preguntar(token, limpia, historial);
      setMensajes((m) => [...m, { rol: "asistente", texto: r.respuesta, error: r.error }]);
      setDetras(r.detras);
    } catch (e) {
      setMensajes((m) => [...m, { rol: "asistente", texto: textoError(e), error: true }]);
    } finally {
      setPensando(false);
      campo.current?.focus();
    }
  }

  const nombre = nombreNegocio || tienda?.nombre || t.negocio;

  return (
    <div className={t.clase}>
      <EncabezadoPagina titulo={`Asistente de ${t.negocio}`} descripcion={t.descripcion} />

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
                {t.sugerencias.map((s) => <li key={s}><button type="button" className="ui-chip" onClick={() => enviar(s)}>{s}</button></li>)}
              </ul>
            </div>
          ) : (
            <ol className="as-mensajes" aria-live="polite">
              {mensajes.map((m, i) => (
                <li key={i} className={`as-msg is-${m.rol}${m.error ? " is-error" : ""}`}>
                  <span className="as-solo-lectores">{m.rol === "usuario" ? "Tú:" : "Asistente:"}</span>{m.texto}
                </li>
              ))}
              {pensando && <li className="as-msg is-asistente is-pensando" role="status">Revisando los datos de {t.negocio}…</li>}
              <li ref={fin} aria-hidden="true" />
            </ol>
          )}

          <form className="as-form" onSubmit={(e) => { e.preventDefault(); enviar(texto); }}>
            <label htmlFor="as-campo" className="ui-field__label">Mensaje</label>
            <div className="as-form__fila">
              <input id="as-campo" ref={campo} className="ui-field__input" value={texto} maxLength={500} autoComplete="off"
                placeholder={t.ejemplo} onChange={(e) => setTexto(e.target.value)} />
              <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande" disabled={pensando || !texto.trim()} aria-busy={pensando}>
                {pensando ? "Pensando…" : "Enviar"}
              </button>
            </div>
          </form>
          <p className="as-tenue as-aviso">{t.aviso}</p>
        </section>

        <DetrasDeEscena detras={detras} negocio={t.negocio} />
      </div>
    </div>
  );
}
