import { apiFetch } from "../services/http";
import { useState, useEffect, useRef, useCallback } from "react";
import { subscribeNotifications, subscribeEstadoConexion } from "../services/websocketService";
import { listarMisAvisos, marcarAvisoLeido, marcarTodosLeidos } from "../services/notificacionesService";
import {
  MAX_AVISOS, textoContador, haceCuanto, tonoDeAviso, agregarAviso, marcarLeidaEnLista, marcarTodasEnLista, productosConStockBajo,
} from "../services/notificacionesUtil";
import "../css/notifications.css";

const API_BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api`;

const TEXTO_ESTADO = { conectado: "En vivo", conectando: "Reconectando…", desconectado: "Sin conexión en vivo" };

/**
 * Lista de avisos del usuario (guardados en el servidor y los que llegan en vivo).
 * La tienda ve además sus productos con stock bajo.
 */
export default function NotificationDropdown({ rol, onClose, onNavigate, onNoLeidas }) {
  const ref = useRef(null);
  const [avisos, setAvisos] = useState([]);
  const [noLeidas, setNoLeidas] = useState(0);
  const [stockBajo, setStockBajo] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(false);
  const [estado, setEstado] = useState("desconectado");

  const actualizarNoLeidas = useCallback((n) => {
    const valor = Math.max(0, n);
    setNoLeidas(valor);
    onNoLeidas?.(valor);
  }, [onNoLeidas]);

  const cargar = useCallback(async () => {
    setError(false);
    try {
      const data = await listarMisAvisos(MAX_AVISOS);
      setAvisos(data?.items || []);
      actualizarNoLeidas(data?.noLeidas || 0);
    } catch {
      setError(true);
    }
    if (rol === "TIENDA") {
      try {
        const res = await apiFetch(`${API_BASE}/productos/mi-tienda`);
        if (res.ok) setStockBajo(productosConStockBajo(await res.json()));
      } catch { /* el inventario es secundario */ }
    }
    setCargando(false);
  }, [rol, actualizarNoLeidas]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const unsub = subscribeNotifications((msg) => {
      if (msg?.type !== "notificacion" || !msg.notificacion) return;
      setAvisos((lista) => agregarAviso(lista, msg.notificacion));
      if (typeof msg.noLeidas === "number") actualizarNoLeidas(msg.noLeidas);
    });
    const unsubEstado = subscribeEstadoConexion(setEstado);
    return () => { clearTimeout(inicial); unsub(); unsubEstado(); };
  }, [cargar, actualizarNoLeidas]);

  useEffect(() => {
    const fuera = (e) => { if (ref.current && !ref.current.contains(e.target)) onClose(); };
    const escape = (e) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("mousedown", fuera);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", fuera);
      document.removeEventListener("keydown", escape);
    };
  }, [onClose]);

  const abrir = (aviso) => {
    if (!aviso.leida) {
      setAvisos((lista) => marcarLeidaEnLista(lista, aviso.id));
      actualizarNoLeidas(noLeidas - 1);
      marcarAvisoLeido(aviso.id).catch(() => { /* se corrige en la próxima carga */ });
    }
    if (aviso.enlace) {
      onClose();
      onNavigate?.(aviso.enlace, null);
    }
  };

  const marcarTodas = async () => {
    setAvisos((lista) => marcarTodasEnLista(lista));
    actualizarNoLeidas(0);
    try { await marcarTodosLeidos(); } catch { cargar(); }
  };

  const vacio = avisos.length === 0 && stockBajo.length === 0;

  return (
    <div className="nt-dropdown" ref={ref} role="dialog" aria-label="Notificaciones">
      <div className="nt-header">
        <div className="nt-header__titulo">
          <span className="nt-title">Notificaciones</span>
          {noLeidas > 0 && <span className="nt-count" title={`${noLeidas} sin leer`}>{textoContador(noLeidas)}<span className="nt-sr"> sin leer</span></span>}
        </div>
        {noLeidas > 0 && (
          <button type="button" className="nt-link" onClick={marcarTodas}>Marcar todas como leídas</button>
        )}
      </div>

      {cargando && vacio ? (
        <div className="nt-empty">Cargando…</div>
      ) : error && vacio ? (
        <div className="nt-empty">
          <p>No pudimos cargar tus notificaciones.</p>
          <button type="button" className="nt-link" onClick={cargar}>Reintentar</button>
        </div>
      ) : vacio ? (
        <div className="nt-empty">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
            <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 01-3.46 0"/>
          </svg>
          <p>No tienes notificaciones</p>
        </div>
      ) : (
        <div className="nt-scroll">
          {avisos.length > 0 && (
            <ul className="nt-lista">
              {avisos.map((a) => (
                <li key={a.id}>
                  <button
                    type="button"
                    className={`nt-item nt-item--${tonoDeAviso(a.tipo)}${a.leida ? "" : " nt-item--nueva"}`}
                    onClick={() => abrir(a)}
                  >
                    <span className="nt-dot" aria-hidden="true" />
                    <span className="nt-body">
                      <span className="nt-item-label">
                        {a.titulo}
                        {!a.leida && <span className="nt-sr">(sin leer)</span>}
                      </span>
                      {a.mensaje && <span className="nt-item-detail">{a.mensaje}</span>}
                    </span>
                    <span className="nt-time">{haceCuanto(a.fechaCreacion)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {stockBajo.length > 0 && (
            <div className="nt-group">
              <div className="nt-group-label">Inventario</div>
              <ul className="nt-lista">
                {stockBajo.slice(0, 10).map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      className={`nt-item nt-item--${p.stockActual === 0 ? "error" : "marca"}`}
                      onClick={() => { onClose(); onNavigate?.("productos", p.id); }}
                    >
                      <span className="nt-dot" aria-hidden="true" />
                      <span className="nt-body">
                        <span className="nt-item-label">{p.stockActual === 0 ? "Producto agotado" : "Stock bajo"}</span>
                        <span className="nt-item-detail">
                          {p.nombre} {p.stockActual === 0 ? "— sin stock" : `(${p.stockActual} restantes)`}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      <div className="nt-footer">
        <span className={`nt-estado nt-estado--${estado}`}>
          <span className="nt-estado__punto" aria-hidden="true" />
          {TEXTO_ESTADO[estado]}
        </span>
        <button type="button" className="nt-link" onClick={cargar}>Actualizar</button>
      </div>
    </div>
  );
}
