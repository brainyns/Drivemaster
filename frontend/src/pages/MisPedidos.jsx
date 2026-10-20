import { apiFetch } from "../services/http";
import { useState, useEffect, useMemo } from "react";
import { getToken } from "../services/authService";
import { misOrdenes } from "../services/ordenServicioService";
import "../css/mis-pedidos.css";
import Icon from "../components/public/Icon";

const API = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

const formatFecha = (f, conHora = false) => {
  if (!f) return null;
  const d = new Date(f);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleString("es-CO", conHora
    ? { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }
    : { day: "2-digit", month: "short", year: "numeric" });
};

function headers(token) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

// Estado de cada compra: texto y tono del sistema (ui-badge), que cambia con el tema
const ESTADOS_COMPRA = {
  PAGADA: { label: "Pagada", tono: "info" },
  APROBADO: { label: "Aprobado", tono: "ok" },
  COMPLETADA: { label: "Completada", tono: "ok" },
  ENTREGADO: { label: "Entregado", tono: "ok" },
  EN_CAMINO: { label: "En camino", tono: "aviso" },
  ANULADA: { label: "Anulada", tono: "neutro" },
  CANCELADA: { label: "Cancelada", tono: "error" },
  PENDIENTE_PAGO: { label: "Pendiente de pago", tono: "aviso" },
};

const PASOS_SEGUIMIENTO = [
  { key: "CONFIRMADO", label: "Confirmado", estado: ["PAGADA", "APROBADO", "COMPLETADA"] },
  { key: "EN_CAMINO", label: "En camino", estado: ["EN_CAMINO"] },
  { key: "ENTREGADO", label: "Entregado", estado: ["ENTREGADO"] },
];

function pasoSeguimiento(estado) {
  const e = (estado || "").toUpperCase();
  if (["ANULADA", "CANCELADA"].includes(e)) return -1;
  const idx = PASOS_SEGUIMIENTO.findIndex((p) => p.estado.includes(e));
  return idx >= 0 ? idx : 0;
}

// Estados del ciclo de una orden de taller. El estado vive en la orden, no en
// la venta: por eso el historial de compras mostraba todo congelado en PAGADA.
const ESTADOS_ORDEN = {
  SOLICITADA: { label: "Solicitada", tono: "aviso" },
  COTIZADA: { label: "Cotizada", tono: "acento" },
  PAGADA: { label: "Pagada", tono: "info" },
  EN_PROCESO: { label: "En proceso", tono: "aviso" },
  FINALIZADA: { label: "Finalizada", tono: "ok" },
  CANCELADA: { label: "Cancelada", tono: "error" },
};

/** Píldora de estado con su texto; un estado desconocido se muestra tal cual, en aviso. */
function Pildora({ catalogo, estado, className }) {
  const e = catalogo[estado] || { label: estado || "Pendiente", tono: "aviso" };
  return <span className={`ui-badge ui-badge--${e.tono} ${className}`}>{e.label}</span>;
}

const PASOS_TALLER = [
  { key: "COTIZADA", label: "Cotizada" },
  { key: "PAGADA", label: "Pagada" },
  { key: "EN_PROCESO", label: "En taller" },
  { key: "FINALIZADA", label: "Finalizada" },
];

function pasoTaller(estado) {
  if (estado === "CANCELADA") return -1;
  const idx = PASOS_TALLER.findIndex((p) => p.key === estado);
  return idx >= 0 ? idx : 0;
}

/** Citas future agendas por el taller para este cliente. */
function CitasAgendadas({ ordenes, ahora }) {
  const proximas = useMemo(() => {
    return ordenes
      .filter((o) => o.fechaProgramada && new Date(o.fechaProgramada).getTime() >= ahora)
      .sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada));
  }, [ordenes, ahora]);

  if (proximas.length === 0) return null;

  return (
    <section className="mis-citas">
      <h2 className="mis-citas-titulo">Citas agendadas</h2>
      <p className="mis-citas-sub">Te enviamos el aviso al correo cuando el taller programa tu ingreso.</p>
      <div className="mis-citas-lista">
        {proximas.map((o) => (
          <div key={o.id} className="mis-citas-card">
            <div className="mis-citas-when">
              <span className="mis-citas-fecha">{formatFecha(o.fechaProgramada)}</span>
              <span className="mis-citas-hora">{formatFecha(o.fechaProgramada, true)?.split(", ").pop()}</span>
            </div>
            <div className="mis-citas-detalle">
              <strong>{o.vehiculoDescripcion}</strong>
              <span>{o.tallerNombre} · {o.productoNombre}</span>
              <Pildora catalogo={ESTADOS_ORDEN} estado={o.estado} className="mis-citas-estado" />
            </div>
            <button
              className="mis-citas-cancelar"
              disabled={!["SOLICITADA", "COTIZADA", "PAGADA"].includes(o.estado)}
              onClick={() => window.alert("La cancelación de citas aún no está habilitada. Contacta al taller.")}
            >
              ¿Necesitas cambiarla?
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}

export default function MisPedidos({ onVolver }) {
  const token = getToken();
  const [ventas, setVentas] = useState([]);
  const [ordenes, setOrdenes] = useState([]);
  const [cargando, setCargando] = useState(true);
  // Se fija una vez al cargar para no impurity el render y evitar que el filtro
  // de citas cambie de resultado en cada re-render.
  const [ahora] = useState(() => Date.now());

  useEffect(() => {
    (async () => {
      setCargando(true);
      try {
        const [resVentas, resOrdenes] = await Promise.allSettled([
          apiFetch(`${API}/ventas/mis-ventas`, { headers: headers(token) }),
          misOrdenes(token),
        ]);
        if (resVentas.status === "fulfilled" && resVentas.value.ok) {
          setVentas(await resVentas.value.json());
        }
        if (resOrdenes.status === "fulfilled") {
          setOrdenes(resOrdenes.value || []);
        }
      } catch (e) {
        console.error(e);
      } finally {
        setCargando(false);
      }
    })();
  }, []);

  // Las ventas de taller se muestran en su propia sección usando el estado real
  // de la orden, así que se excluyen del historial de compras de producto.
  const ventasProducto = useMemo(
    () => ventas.filter((v) => v.tipoVenta !== "TALLER"),
    [ventas],
  );

  const hayTodo = ventasProducto.length === 0 && ordenes.length === 0;

  return (
    <div className="mis-pedidos-page">
      <div className="mis-pedidos-header">
        <h1>Historial de compras</h1>
        <button className="mis-pedidos-volver" onClick={onVolver}><Icon name="arrow" size={16} className="ui-volver__icono ui-icono-linea" />Volver al catálogo</button>
      </div>

      {cargando ? (
        <div className="mis-pedidos-loading">Cargando...</div>
      ) : hayTodo ? (
        <div className="mis-pedidos-empty">No tienes compras realizadas</div>
      ) : (
        <>
          <CitasAgendadas ordenes={ordenes} ahora={ahora} />

          {ordenes.length > 0 && (
            <section className="mis-pedidos-lista">
              <h2 className="mis-pedidos-seccion">Servicios de taller</h2>
              {ordenes.map((o) => {
                const paso = pasoTaller(o.estado);
                return (
                  <div key={o.id} className="mis-pedidos-card">
                    <div className="mis-pedidos-card-top">
                      <span className="mis-pedidos-card-id">#{o.id?.slice(-8).toUpperCase() || "N/A"}</span>
                      <Pildora catalogo={ESTADOS_ORDEN} estado={o.estado} className="mis-pedidos-card-estado" />
                    </div>

                    {paso >= 0 && (
                      <div className="mis-pedidos-track">
                        {PASOS_TALLER.map((p, i) => (
                          <div key={p.key} className={`mis-pedidos-step${i <= paso ? " on" : ""}`}>
                            <span className="mis-pedidos-step-dot" />
                            <span className="mis-pedidos-step-label">{p.label}</span>
                          </div>
                        ))}
                      </div>
                    )}

                    <div className="mis-pedidos-card-body">
                      <div className="mis-pedidos-card-productos">
                        <div className="mis-pedidos-card-producto">
                          <span>{o.vehiculoDescripcion}</span>
                          <span />
                        </div>
                        <div className="mis-pedidos-card-producto">
                          <span>Repuesto: {o.productoNombre}</span>
                          <span>{formatPrecio(o.precioRepuesto)}</span>
                        </div>
                        <div className="mis-pedidos-card-producto">
                          <span>Mano de obra ({o.tallerNombre})</span>
                          <span>{formatPrecio(o.precioManoObra)}</span>
                        </div>
                      </div>

                      <div className="mis-pedidos-card-total">
                        <span>Total</span>
                        <span>{formatPrecio(o.total)}</span>
                      </div>

                      <div className="mis-pedidos-card-info">
                        {o.fechaProgramada && <span>Ingreso: {formatFecha(o.fechaProgramada, true)}</span>}
                        {o.fechaInicio && <span>Iniciado: {formatFecha(o.fechaInicio, true)}</span>}
                        {o.fechaFinalizacion && <span>Finalizado: {formatFecha(o.fechaFinalizacion, true)}</span>}
                        {o.observaciones && <span>{o.observaciones}</span>}
                      </div>
                    </div>
                  </div>
                );
              })}
            </section>
          )}

          {ventasProducto.length > 0 && (
            <section className="mis-pedidos-lista">
              <h2 className="mis-pedidos-seccion">Compras de productos</h2>
              {ventasProducto.map((v) => {
                const paso = pasoSeguimiento(v.estado);
                return (
                  <div key={v.id} className="mis-pedidos-card">
                    <div className="mis-pedidos-card-top">
                      <span className="mis-pedidos-card-id">#{v.id?.slice(-8).toUpperCase() || "N/A"}</span>
                      <Pildora catalogo={ESTADOS_COMPRA} estado={v.estado} className="mis-pedidos-card-estado" />
                    </div>
                    {paso >= 0 && (
                      <div className="mis-pedidos-track">
                        {PASOS_SEGUIMIENTO.map((p, i) => (
                          <div key={p.key} className={`mis-pedidos-step${i <= paso ? " on" : ""}`}>
                            <span className="mis-pedidos-step-dot" />
                            <span className="mis-pedidos-step-label">{p.label}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    <div className="mis-pedidos-card-body">
                      <div className="mis-pedidos-card-productos">
                        {v.productos?.map((p, i) => (
                          <div key={i} className="mis-pedidos-card-producto">
                            <span>{p.nombre} x{p.cantidad}</span>
                            <span>{formatPrecio(p.subtotal)}</span>
                          </div>
                        ))}
                      </div>
                      <div className="mis-pedidos-card-total">
                        <span>Total</span>
                        <span>{formatPrecio(v.total)}</span>
                      </div>
                      <div className="mis-pedidos-card-info">
                        <span>Método: {v.pagos?.[0]?.metodo || "—"}</span>
                        {v.fechaEnvio && <span>Enviado: {formatFecha(v.fechaEnvio)}</span>}
                        {v.fechaEntrega && <span>Entregado: {formatFecha(v.fechaEntrega)}</span>}
                        {v.fecha && <span>{formatFecha(v.fecha, true)}</span>}
                      </div>
                    </div>
                  </div>
                );
              })}
            </section>
          )}
        </>
      )}
    </div>
  );
}
