import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { buscarRepuestos } from "../../services/repuestoService";
import { ESTADO_OFERTA, formatPrecio } from "./estadosServicio";
import Icon from "../public/Icon";

/**
 * Ventana del buscador de repuestos en todas las tiendas (aprobadas y activas).
 * El estado de cada oferta (compatible, no compatible, agotado, por confirmar),
 * si se puede elegir y el motivo vienen calculados del backend.
 *
 * Solo se monta mientras está abierta, así cada apertura empieza limpia.
 */
export default function BuscadorRepuestos({ abierto, ...props }) {
  if (!abierto) return null;
  return <VentanaBuscador {...props} />;
}

function VentanaBuscador({ token, solicitud, elegidos, onElegir, onCerrar }) {
  const [texto, setTexto] = useState("");
  const [cantidad, setCantidad] = useState(1);
  const [soloCompatibles, setSoloCompatibles] = useState(true);
  const [resultados, setResultados] = useState([]);
  const [buscando, setBuscando] = useState(false);
  const [buscado, setBuscado] = useState(false);
  const [error, setError] = useState(null);
  const inputRef = useRef(null);

  const buscar = useCallback(async ({ q, cant, solo }) => {
    setBuscando(true);
    setError(null);
    try {
      setResultados(await buscarRepuestos(token, {
        ordenId: solicitud.id, q, cantidad: cant, soloCompatibles: solo,
      }));
      setBuscado(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setBuscando(false);
    }
  }, [token, solicitud.id]);

  // onCerrar cambia en cada render del padre; la ref evita repetir la búsqueda inicial.
  const cerrarRef = useRef(onCerrar);
  useEffect(() => { cerrarRef.current = onCerrar; }, [onCerrar]);

  // Al abrir: foco en el buscador, Esc para cerrar, sin scroll del fondo y todo lo compatible.
  useEffect(() => {
    inputRef.current?.focus();
    const alTeclear = (e) => { if (e.key === "Escape") cerrarRef.current(); };
    document.addEventListener("keydown", alTeclear);
    const overflowPrevio = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    buscar({ q: "", cant: 1, solo: true });
    return () => {
      document.removeEventListener("keydown", alTeclear);
      document.body.style.overflow = overflowPrevio;
    };
  }, [buscar]);

  const enviar = (e) => {
    e.preventDefault();
    buscar({ q: texto.trim(), cant: cantidad, solo: soloCompatibles });
  };

  const cambiarFiltro = (solo) => {
    setSoloCompatibles(solo);
    buscar({ q: texto.trim(), cant: cantidad, solo });
  };

  const otraReferencia = () => {
    setTexto("");
    inputRef.current?.focus();
  };

  // Se dibuja en <body>: dentro del panel del taller, la animación de entrada deja un transform en
  // un contenedor y la ventana "fija" quedaba anclada a él (cortada arriba) y no a la pantalla.
  return createPortal(
    <div className="ss-modal" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onCerrar(); }}>
      <div className="ss-modal__ventana" role="dialog" aria-modal="true" aria-labelledby="ss-modal-titulo">
        <header className="ss-modal__cabecera">
          <div>
            <h2 id="ss-modal-titulo" className="ss-modal__titulo">Buscar repuestos</h2>
            <span className="ss-meta">
              Para {solicitud.vehiculoDescripcion} · inventario de todas las tiendas aprobadas
            </span>
          </div>
          <button type="button" className="ss-btn-icono" onClick={onCerrar} aria-label="Cerrar buscador"><Icon name="close" size={16} /></button>
        </header>

        <form className="ss-modal__filtros" onSubmit={enviar}>
          <div className="sv-field ss-modal__texto">
            <label htmlFor="ss-bus-texto">Nombre o referencia</label>
            <input id="ss-bus-texto" ref={inputRef} value={texto} onChange={(e) => setTexto(e.target.value)}
              placeholder="Pastillas, filtro de aceite, BP-85020..." />
          </div>
          <div className="sv-field">
            <label htmlFor="ss-bus-cantidad">Cantidad</label>
            <input id="ss-bus-cantidad" type="number" min={1} max={99} value={cantidad}
              onChange={(e) => setCantidad(Math.max(1, Math.min(99, Number(e.target.value) || 1)))} />
          </div>
          <button type="submit" className="sv-btn" disabled={buscando}>{buscando ? "Buscando..." : "Buscar"}</button>
          <label className="ss-switch">
            <input type="checkbox" role="switch" checked={soloCompatibles}
              onChange={(e) => cambiarFiltro(e.target.checked)} />
            <span className="ss-switch__pista" aria-hidden="true" />
            Solo compatibles
          </label>
        </form>

        <div className="ss-modal__cuerpo" aria-busy={buscando}>
          {error && <p className="ss-alerta" role="alert">{error}</p>}

          {buscado && !buscando && !error && resultados.length === 0 && (
            <div className="ss-vacio">
              <p><strong>No encontramos repuestos con esa búsqueda.</strong></p>
              <p className="ss-meta">
                {soloCompatibles
                  ? "Puede que la tienda no haya cargado la compatibilidad. Prueba otra referencia o mira también los no compatibles."
                  : "Ninguna tienda aprobada tiene ese repuesto. Prueba con otra referencia o un término más general."}
              </p>
              <div className="ss-acciones">
                <button type="button" className="sv-btn" onClick={otraReferencia}>Buscar otra referencia</button>
                {soloCompatibles && (
                  <button type="button" className="sv-btn-outline" onClick={() => cambiarFiltro(false)}>
                    Ver también no compatibles
                  </button>
                )}
              </div>
            </div>
          )}

          {resultados.length > 0 && (
            <>
              <p className="ss-ofertas__resumen">
                {resultados.length} {resultados.length === 1 ? "repuesto" : "repuestos"} ·{" "}
                {resultados.reduce((n, g) => n + g.ofertas.length, 0)} ofertas de tiendas
              </p>
              <div className="ss-ofertas">
                {resultados.flatMap((g) => g.ofertas.map((o) => (
                  <TarjetaOferta key={o.productoId} oferta={o} categoria={g.categoria} elegido={elegidos.has(o.productoId)}
                    onElegir={() => onElegir(g, o, cantidad)} />
                )))}
              </div>
            </>
          )}
        </div>

        <footer className="ss-modal__pie">
          <span className="ss-modal__contador" aria-live="polite">
            <strong>{elegidos.size}</strong> {elegidos.size === 1 ? "repuesto" : "repuestos"} en el diagnóstico
          </span>
          <button type="button" className="sv-btn" onClick={onCerrar}>Listo</button>
        </footer>
      </div>
    </div>,
    document.body
  );
}

function TarjetaOferta({ oferta: o, categoria, elegido, onElegir }) {
  const estado = ESTADO_OFERTA[o.estado] || { label: o.estado, tono: "info" };
  const ubicacion = [o.tiendaBarrio, o.tiendaCiudad].filter(Boolean).join(", ");
  const encargo = o.disponibilidad === "ENCARGO";

  return (
    <article className={`ss-oferta${o.seleccionable ? "" : " ss-oferta--bloqueada"}${elegido ? " ss-oferta--elegida" : ""}`}>
      <div className="ss-oferta__foto">
        {o.imagenUrl ? <img src={o.imagenUrl} alt="" loading="lazy" /> : <Icon name="tool" size={20} />}
        <span className={`ss-estado ss-estado--${estado.tono} ss-oferta__compat`}><span className="ss-estado__punto" />{estado.label}</span>
      </div>
      <div className="ss-oferta__cuerpo">
        <strong className="ss-oferta__nombre">{o.nombre}</strong>
        <span className="ss-oferta__ref">
          {[o.referencia && `Ref. ${o.referencia}`, o.marca, categoria].filter(Boolean).join(" · ")}
        </span>
        <span className="ss-oferta__tienda">{o.tiendaNombre}</span>
        <span className="ss-oferta__dato">
          {ubicacion || "Ubicación no registrada"}
          {o.distanciaKm != null && ` · a ${o.distanciaKm} km`}
        </span>
        <span className="ss-oferta__etiquetas">
          <span className={`ss-oferta__stock${!encargo && !o.stock ? " ss-oferta__stock--agotado" : ""}`}>
            {encargo
              ? `Por encargo · ${o.diasEntrega ? `${o.diasEntrega} día(s)` : "tiempo por confirmar"}`
              : `Stock: ${o.stock}`}
          </span>
          {o.mismaTiendaQueSeleccion && <span className="ss-chip">Ya surte otros repuestos</span>}
        </span>
        {/* El motivo va en el cuerpo para que precio y botón queden alineados entre tarjetas */}
        {o.motivo && (
          <span id={`motivo-${o.productoId}`}
            className={`ss-oferta__motivo${o.seleccionable ? " ss-oferta__motivo--aviso" : ""}`}>
            {o.motivo}
          </span>
        )}
      </div>
      <div className="ss-oferta__pie">
        <strong className="ss-oferta__precio">{formatPrecio(o.precio)}</strong>
        <button type="button" className="sv-btn ss-btn-elegir" disabled={!o.seleccionable || elegido}
          onClick={onElegir} aria-describedby={o.motivo ? `motivo-${o.productoId}` : undefined}>
          {elegido ? "Elegido" : "Elegir"}
        </button>
      </div>
    </article>
  );
}
