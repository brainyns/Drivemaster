import { useEffect, useState, useMemo } from "react";
import { getToken } from "../services/authService";
import { miTaller } from "../services/tallerService";
import EstadoNegocio from "../components/EstadoNegocio";
import MecanicosTaller from "../components/servicio/MecanicosTaller";
import TallerSolicitudes from "./TallerSolicitudes";
import { ordenesDelTaller, iniciarServicio, finalizarServicio, programarCita, confirmarPagoOrden } from "../services/ordenServicioService";
import "../css/servicios.css";
import "../css/panel-taller.css";

const TABS = [
  { key: "solicitudes", label: "Solicitudes" },
  { key: "resumen",     label: "Resumen" },
  { key: "ordenes",     label: "Por iniciar" },
  { key: "proceso",     label: "En proceso" },
  { key: "finalizadas", label: "Finalizadas" },
  { key: "mitaller",    label: "Mi taller" },
];

const ETAPAS = [
  { estado: "COTIZADA",   label: "Cotizadas" },
  { estado: "PAGADA",     label: "Por iniciar" },
  { estado: "EN_PROCESO", label: "En proceso" },
  { estado: "FINALIZADA", label: "Finalizadas" },
];

function formatPrecio(v) {
  if (v == null) return "—";
  return v.toLocaleString("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
}

function formatFecha(f) {
  if (!f) return "-";
  return new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" });
}

function formatFechaHora(f) {
  if (!f) return "-";
  return new Date(f).toLocaleString("es-CO", {
    day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit",
  });
}

function diasDesde(f) {
  if (!f) return null;
  const dias = Math.floor((Date.now() - new Date(f).getTime()) / 86400000);
  return dias < 0 ? 0 : dias;
}

function refOrden(id) {
  return id ? id.slice(-6).toUpperCase() : "—";
}

function Badge({ estado }) {
  const map = {
    COTIZADA:   ["sv-badge-cotizada",   "Cotizada"],
    PAGADA:     ["sv-badge-pagada",     "Pagada"],
    EN_PROCESO: ["sv-badge-proceso",    "En proceso"],
    FINALIZADA: ["sv-badge-finalizada", "Finalizada"],
    CANCELADA:  ["sv-badge-cancelada",  "Cancelada"],
  };
  const [cls, label] = map[estado] || ["sv-badge-cotizada", estado];
  return <span className={`sv-badge ${cls}`}><span className="sv-badge-dot" />{label}</span>;
}

export default function PanelTaller() {
  const [ordenes, setOrdenes] = useState([]);
  const [taller, setTaller] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState("solicitudes");
  const [procesando, setProcesando] = useState(null);
  const [observaciones, setObservaciones] = useState({});
  const [fechasCita, setFechasCita] = useState({});

  const token = getToken();

  const cargar = async () => {
    setCargando(true);
    setError(null);
    try {
      const data = await ordenesDelTaller(token);
      setOrdenes(data || []);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  // Estado de revisión del taller (pendiente, rechazado...). Si falla, el panel sigue igual.
  useEffect(() => {
    miTaller(token).then(setTaller).catch(() => {});
  }, [token]);

  // El cliente reportó el pago y el taller debe confirmar que lo recibió.
  const porConfirmar = useMemo(
    () => ordenes.filter(o => o.estado === "COTIZADA" && o.fechaPagoReportado),
    [ordenes]
  );
  const pagadas     = useMemo(() => ordenes.filter(o => o.estado === "PAGADA"), [ordenes]);
  const enProceso   = useMemo(() => ordenes.filter(o => o.estado === "EN_PROCESO"), [ordenes]);
  const finalizadas = useMemo(() => ordenes.filter(o => o.estado === "FINALIZADA"), [ordenes]);
  const retrasadas  = useMemo(() => ordenes.filter(o => o.retrasada), [ordenes]);

  function FotoVehiculo({ o }) {
    return o.vehiculoImagenUrl ? (
      <img
        className="pt-card-foto"
        src={o.vehiculoImagenUrl}
        alt={o.vehiculoDescripcion || "Vehículo"}
        loading="lazy"
      />
    ) : (
      <div className="pt-card-foto pt-card-foto--ph" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
          <rect x="3" y="6" width="18" height="13" rx="2" />
          <circle cx="9" cy="13" r="1.5" />
          <path d="M21 17l-4.5-4.5-6 6" />
        </svg>
      </div>
    );
  }

  function ChipEstado({ o }) {
    return (
      <span className="pt-card-chips">
        <Badge estado={o.estado} />
        {o.retrasada && <span className="pt-pill-late">Retrasada</span>}
      </span>
    );
  }

  const ingresosFinalizadas = useMemo(
    () => finalizadas.reduce((s, o) => s + (o.precioManoObra || 0), 0),
    [finalizadas]
  );

  // Datos del taller: del perfil (mi-taller) y, si aún no carga, de sus órdenes.
  const tallerInfo = taller
    ? { nombre: taller.nombre, precioManoObra: taller.precioManoObra }
    : ordenes[0]
      ? { nombre: ordenes[0].tallerNombre, precioManoObra: ordenes[0].precioManoObra }
      : null;

  // Agenda del taller: lo primero que entra por la puerta.
  const agenda = useMemo(() => {
    const ahora = Date.now();
    return ordenes
      .filter(o => o.fechaProgramada && new Date(o.fechaProgramada).getTime() > ahora)
      .sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada));
  }, [ordenes]);

  const conCita = useMemo(
    () => ordenes.filter(o => o.fechaProgramada).length,
    [ordenes]
  );

  const handleConfirmarPago = async (id) => {
    setProcesando(id);
    setError(null);
    try {
      await confirmarPagoOrden(token, id);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const handleIniciar = async (id) => {
    setProcesando(id);
    try {
      await iniciarServicio(token, id);
      await cargar();
      setTab("proceso");
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const handleFinalizar = async (id) => {
    setProcesando(id);
    try {
      await finalizarServicio(token, id, observaciones[id] || "");
      await cargar();
      setTab("finalizadas");
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const handleProgramar = async (id) => {
    setProcesando(id);
    setError(null);
    try {
      await programarCita(token, id, fechasCita[id]);
      setFechasCita(prev => ({ ...prev, [id]: "" }));
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const conteoPor = (estado) => ordenes.filter(o => o.estado === estado).length;
  const sinNada = ordenes.length === 0;
  // El flujo se reparte solo sobre lo que no está cancelado, para que la barra cierre en 100%.
  const totalFlujo = ETAPAS.reduce((s, e) => s + conteoPor(e.estado), 0);
  const canceladas = ordenes.length - totalFlujo;

  return (
    <EstadoNegocio negocio={taller} tipo="taller">
    <div className="pt-root">
      <header className="pt-header">
        <div>
          <span className="pt-eyebrow">Taller aliado</span>
          <h1 className="pt-title">Panel del taller</h1>
          <p className="pt-subtitle">
            Responde las solicitudes de diagnóstico de tu zona, registra el diagnóstico y elige
            los repuestos; luego da seguimiento a cada trabajo hasta entregarlo.
          </p>
        </div>
        {agenda.length > 0 && (
          <div className="pt-next">
            {agenda[0].vehiculoImagenUrl && (
              <img className="pt-next-photo" src={agenda[0].vehiculoImagenUrl} alt="" loading="lazy" />
            )}
            <span className="pt-next-label">Próximo ingreso</span>
            <span className="pt-next-when">{formatFechaHora(agenda[0].fechaProgramada)}</span>
            <span className="pt-next-what">
              {agenda[0].vehiculoDescripcion || "Vehículo"} · Orden #{refOrden(agenda[0].id)}
            </span>
            {agenda.length > 1 && (
              <span className="pt-next-more">+{agenda.length - 1} más en la agenda</span>
            )}
          </div>
        )}
      </header>

      {error && <p className="pt-error">{error}</p>}

      <nav className="pt-tabs" aria-label="Secciones del panel">
        {TABS.map(t => {
          const n = t.key === "ordenes" ? pagadas.length + porConfirmar.length
            : t.key === "proceso" ? enProceso.length
            : t.key === "finalizadas" ? finalizadas.length
            : 0;
          return (
            <button
              key={t.key}
              className={`pt-tab${tab === t.key ? " pt-tab--activo" : ""}`}
              aria-current={tab === t.key ? "page" : undefined}
              onClick={() => setTab(t.key)}
            >
              {t.label}
              {n > 0 && <span className="pt-tab-count">{n}</span>}
            </button>
          );
        })}
      </nav>

      {/* Solicitudes y Mi taller no dependen de las órdenes del flujo anterior. */}
      {tab === "solicitudes" ? (
        <TallerSolicitudes />
      ) : tab === "mitaller" ? (
        <div className="pt-perfil">
          {tallerInfo ? (
            <>
              <span className="pt-eyebrow">Datos del taller</span>
              <h2 className="pt-perfil-nombre">{tallerInfo.nombre}</h2>
              <div className="pt-perfil-facts">
                <div className="pt-fact">
                  <span className="pt-fact-label">Precio de mano de obra</span>
                  <span className="pt-fact-value">{formatPrecio(tallerInfo.precioManoObra)}</span>
                </div>
                <div className="pt-fact">
                  <span className="pt-fact-label">Servicios recibidos</span>
                  <span className="pt-fact-value">{ordenes.length}</span>
                </div>
                <div className="pt-fact">
                  <span className="pt-fact-label">Finalizados</span>
                  <span className="pt-fact-value">{finalizadas.length}</span>
                </div>
              </div>
              <p className="pt-hint">
                Para actualizar el nombre, la ciudad, la dirección o el precio de mano de
                obra de tu taller, escríbele al administrador de DriveMaster.
              </p>
            </>
          ) : (
            <p className="sv-empty">Tu taller todavía no aparece en el reporte.</p>
          )}
          <MecanicosTaller token={token} />
        </div>
      ) : cargando ? (
        <div className="pt-skel-wrap">
          <div className="pt-skel-grid">
            {[0, 1, 2, 3].map(i => <div className="pt-skel-card" key={i} />)}
          </div>
          <div className="pt-skel" />
          <div className="pt-skel" />
        </div>
      ) : sinNada ? (
        <div className="pt-empty-state">
          <div className="pt-empty-ico">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
              <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
            </svg>
          </div>
          <h3>Todavía no te llega ningún servicio</h3>
          <p>
            Cuando un cliente pague una cotización, la orden aparece aquí lista para
            programar el ingreso del vehículo.
          </p>
        </div>
      ) : (
        <>
          {tab === "resumen" && (
            <>
              <div className="pt-stats">
                <div className="pt-stat-card">
                  <span className="pt-stat-label">Por iniciar</span>
                  <span className="pt-stat-value">{pagadas.length}</span>
                  <span className="pt-stat-foot">Pagadas y sin trabajar</span>
                </div>
                <div className="pt-stat-card pt-stat-card--live">
                  <span className="pt-stat-label">En proceso</span>
                  <span className="pt-stat-value">{enProceso.length}</span>
                  <span className="pt-stat-foot">Vehículos en el taller ahora</span>
                </div>
                <div className="pt-stat-card">
                  <span className="pt-stat-label">Finalizadas</span>
                  <span className="pt-stat-value">{finalizadas.length}</span>
                  <span className="pt-stat-foot">Servicios entregados</span>
                </div>
                <div className="pt-stat-card">
                  <span className="pt-stat-label">Citas agendadas</span>
                  <span className="pt-stat-value">{conCita}</span>
                  <span className="pt-stat-foot">{agenda.length} aún por llegar</span>
                </div>
                <div className={`pt-stat-card${retrasadas.length > 0 ? " pt-stat-card--late" : ""}`}>
                  <span className="pt-stat-label">Con retraso</span>
                  <span className="pt-stat-value">{retrasadas.length}</span>
                  <span className="pt-stat-foot">
                    {retrasadas.length === 0
                      ? "Nada se quedó quieto"
                      : "Servicios sin avance hace varios días"}
                  </span>
                </div>
                <div className="pt-stat-card pt-stat-card--wide">
                  <span className="pt-stat-label">Ingresos por mano de obra</span>
                  <span className="pt-stat-value pt-stat-value--money">{formatPrecio(ingresosFinalizadas)}</span>
                  <span className="pt-stat-foot">Suma de los servicios finalizados</span>
                </div>
              </div>

              <section className="pt-flow">
                <div className="pt-flow-head">
                  <h2 className="pt-flow-title">Dónde están tus servicios</h2>
                  <span className="pt-flow-total">
                    {totalFlujo} en taller{canceladas > 0 && ` · ${canceladas} canceladas`}
                  </span>
                </div>
                <div className="pt-flow-bar">
                  {totalFlujo === 0 ? (
                    <span className="pt-flow-seg pt-flow-seg--cotizada" style={{ width: "100%" }} />
                  ) : ETAPAS.map(e => {
                    const n = conteoPor(e.estado);
                    const pct = (n * 100) / totalFlujo;
                    return pct > 0 ? (
                      <span
                        key={e.estado}
                        className={`pt-flow-seg pt-flow-seg--${e.estado.toLowerCase().replace("_", "-")}`}
                        style={{ width: `${pct}%` }}
                        title={`${e.label}: ${n}`}
                      />
                    ) : null;
                  })}
                </div>
                <div className="pt-flow-legend">
                  {ETAPAS.map(e => (
                    <span className="pt-flow-key" key={e.estado}>
                      <i className={`pt-flow-dot pt-flow-seg--${e.estado.toLowerCase().replace("_", "-")}`} />
                      {e.label}
                      <strong>{conteoPor(e.estado)}</strong>
                    </span>
                  ))}
                </div>
              </section>
            </>
          )}

          {tab === "ordenes" && (
            <div className="pt-list">
              {porConfirmar.map(o => (
                <article className="pt-card" key={o.id}>
                  <div className="pt-card-media">
                    <FotoVehiculo o={o} />
                  </div>
                  <div className="pt-card-body">
                    <div className="pt-card-head">
                      <span className="pt-card-ref">#{refOrden(o.id)}</span>
                      <ChipEstado o={o} />
                    </div>
                    <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                    <p className="pt-card-meta">
                      Pago reportado: {o.metodoPago === "TRANSFERENCIA" ? "Transferencia" : "Efectivo"}
                      {o.referenciaPago ? ` · Comprobante ${o.referenciaPago}` : ""}
                    </p>
                    <p className="pt-card-total">{formatPrecio(o.total)}</p>
                  </div>
                  <div className="pt-card-actions">
                    <button
                      className="sv-btn pt-btn-block"
                      disabled={procesando === o.id}
                      onClick={() => handleConfirmarPago(o.id)}
                    >
                      {procesando === o.id ? "Procesando…" : "Confirmar pago recibido"}
                    </button>
                  </div>
                </article>
              ))}
              {pagadas.length === 0 ? (
                porConfirmar.length === 0 && <p className="sv-empty">No tienes servicios pagados esperando inicio.</p>
              ) : pagadas.map(o => (
                <article className="pt-card" key={o.id}>
                  <div className="pt-card-media">
                    <FotoVehiculo o={o} />
                  </div>
                  <div className="pt-card-body">
                    <div className="pt-card-head">
                      <span className="pt-card-ref">#{refOrden(o.id)}</span>
                      <ChipEstado o={o} />
                    </div>
                    <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                    <p className="pt-card-meta">
                      {o.productoNombre || "Sin repuesto asociado"} · Mano de obra {formatPrecio(o.precioManoObra)}
                    </p>
                    <p className="pt-card-total">{formatPrecio(o.total)}</p>
                  </div>
                  <div className="pt-card-actions">
                    <div className="pt-programar">
                      <label className="pt-label" htmlFor={`cita-${o.id}`}>Ingreso del vehículo</label>
                      <input
                        id={`cita-${o.id}`}
                        className="pt-input"
                        type="datetime-local"
                        value={fechasCita[o.id] || ""}
                        onChange={(e) => setFechasCita(prev => ({ ...prev, [o.id]: e.target.value }))}
                      />
                      {o.fechaProgramada ? (
                        <span className="pt-programado">Ya agendado: {formatFechaHora(o.fechaProgramada)}</span>
                      ) : (
                        <span className="pt-vacio-hint">El cliente aún no tiene cita</span>
                      )}
                      <button
                        className="sv-btn-outline pt-btn-block"
                        disabled={procesando === o.id || !fechasCita[o.id]}
                        onClick={() => handleProgramar(o.id)}
                      >
                        {procesando === o.id ? "Enviando…" : "Programar y avisar"}
                      </button>
                    </div>
                    <button
                      className="sv-btn pt-btn-block"
                      disabled={procesando === o.id}
                      onClick={() => handleIniciar(o.id)}
                    >
                      {procesando === o.id ? "Procesando…" : "Iniciar servicio"}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}

          {tab === "proceso" && (
            <div className="pt-list">
              {enProceso.length === 0 ? (
                <p className="sv-empty">No tienes servicios en proceso.</p>
              ) : enProceso.map(o => {
                const dias = diasDesde(o.fechaInicio);
                return (
                  <article className="pt-card pt-card--live" key={o.id}>
                    <div className="pt-card-media">
                      <FotoVehiculo o={o} />
                    </div>
                    <div className="pt-card-body">
                      <div className="pt-card-head">
                        <span className="pt-card-ref">#{refOrden(o.id)}</span>
                        <ChipEstado o={o} />
                      </div>
                      <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                      <p className="pt-card-meta">{o.productoNombre || "Sin repuesto asociado"}</p>
                      <p className="pt-card-meta">
                        Iniciado el {formatFecha(o.fechaInicio)}
                        {dias !== null && dias > 0 && <span className="pt-dias">hace {dias} día{dias === 1 ? "" : "s"}</span>}
                      </p>
                      <p className="pt-card-total">{formatPrecio(o.total)}</p>
                    </div>
                    <div className="pt-card-actions">
                      <div className="pt-finalizar">
                        <label className="pt-label" htmlFor={`obs-${o.id}`}>
                          Observaciones del servicio
                        </label>
                        <textarea
                          id={`obs-${o.id}`}
                          className="pt-textarea"
                          placeholder="Qué se hizo, qué se reemplaza, si el cliente debe volver…"
                          value={observaciones[o.id] || ""}
                          onChange={(e) => setObservaciones(prev => ({ ...prev, [o.id]: e.target.value }))}
                        />
                        <button
                          className="sv-btn pt-btn-block"
                          disabled={procesando === o.id}
                          onClick={() => handleFinalizar(o.id)}
                        >
                          {procesando === o.id ? "Procesando…" : "Finalizar servicio"}
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}

          {tab === "finalizadas" && (
            <div className="pt-list">
              {finalizadas.length === 0 ? (
                <p className="sv-empty">Aún no has finalizado servicios.</p>
              ) : finalizadas.map(o => (
                <article className="pt-card pt-card--done" key={o.id}>
                  <div className="pt-card-media">
                    <FotoVehiculo o={o} />
                  </div>
                  <div className="pt-card-body">
                    <div className="pt-card-head">
                      <span className="pt-card-ref">#{refOrden(o.id)}</span>
                      <ChipEstado o={o} />
                    </div>
                    <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                    <p className="pt-card-meta">{o.productoNombre || "Sin repuesto asociado"}</p>
                    <p className="pt-card-meta">Finalizado el {formatFecha(o.fechaFinalizacion)}</p>
                    {o.observaciones && <p className="pt-card-note">“{o.observaciones}”</p>}
                  </div>
                  <div className="pt-card-done">
                    <span className="pt-done-ico">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    </span>
                    <span className="pt-card-total">{formatPrecio(o.total)}</span>
                  </div>
                </article>
              ))}
            </div>
          )}

        </>
      )}
    </div>
    </EstadoNegocio>
  );
}
