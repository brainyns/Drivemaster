import { useMemo, useState } from "react";
import { confirmarPagoOrden, iniciarServicio, finalizarServicio, programarCita } from "../../services/ordenServicioService";
import { getToken } from "../../services/authService";
import { formatFecha, formatFechaHora, formatPrecio, refSolicitud } from "../../components/servicio/estadosServicio";
import { ESTADOS_ORDEN_ANTERIOR } from "../../components/servicio/ordenesAnteriores";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Indicador from "../../components/ui/Indicador";
import ChipEstado from "../../components/ui/ChipEstado";
import Campo from "../../components/ui/Campo";
import Alerta from "../../components/ui/Alerta";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Esqueleto from "../../components/ui/Esqueleto";
import Icon from "../../components/public/Icon";
import "../../css/servicios.css";
import "../../css/panel-taller.css";

/**
 * Órdenes del flujo anterior (cotizar → pagar → iniciar → finalizar). Se conservan solo
 * mientras queden órdenes por atender; las pestañas son las de siempre, sin cambios.
 */
const TABS = [
  { key: "resumen",     label: "Resumen" },
  { key: "ordenes",     label: "Por iniciar" },
  { key: "proceso",     label: "En proceso" },
  { key: "finalizadas", label: "Finalizadas" },
];

const ETAPAS = [
  { estado: "COTIZADA",   label: "Cotizadas" },
  { estado: "PAGADA",     label: "Por iniciar" },
  { estado: "EN_PROCESO", label: "En proceso" },
  { estado: "FINALIZADA", label: "Finalizadas" },
];

function diasDesde(f) {
  if (!f) return null;
  const dias = Math.floor((Date.now() - new Date(f).getTime()) / 86400000);
  return dias < 0 ? 0 : dias;
}

function FotoVehiculo({ o }) {
  return o.vehiculoImagenUrl ? (
    <img className="pt-card-foto" src={o.vehiculoImagenUrl} alt="" loading="lazy" />
  ) : (
    <div className="pt-card-foto pt-card-foto--ph" aria-hidden="true"><Icon name="car" size={34} /></div>
  );
}

/** Referencia, estado (con texto) y, si aplica, el aviso de retraso. */
function CabeceraOrden({ o }) {
  return (
    <div className="pt-card-head">
      <span className="pt-card-ref">#{refSolicitud(o.id)}</span>
      <span className="pt-card-chips">
        <ChipEstado estado={o.estado} catalogo={ESTADOS_ORDEN_ANTERIOR} />
        {o.retrasada && <ChipEstado tono="error">Retrasada</ChipEstado>}
      </span>
    </div>
  );
}

const VACIOS = {
  ordenes: ["Nada por iniciar", "No tienes servicios pagados esperando inicio."],
  proceso: ["Nada en proceso", "No tienes servicios en el taller ahora."],
  finalizadas: ["Aún sin finalizar", "Los servicios que finalices quedan aquí."],
};

export default function OrdenesAnteriores({ ordenes, cargando, error: errorCarga, onRecargar }) {
  const [error, setError] = useState(null);
  const [tab, setTab] = useState("resumen");
  const [procesando, setProcesando] = useState(null);
  const [observaciones, setObservaciones] = useState({});
  const [fechasCita, setFechasCita] = useState({});
  const [reintentando, setReintentando] = useState(false);

  const token = getToken();
  const cargar = onRecargar;

  // El cliente reportó el pago y el taller debe confirmar que lo recibió.
  const porConfirmar = useMemo(
    () => ordenes.filter(o => o.estado === "COTIZADA" && o.fechaPagoReportado),
    [ordenes]
  );
  const pagadas     = useMemo(() => ordenes.filter(o => o.estado === "PAGADA"), [ordenes]);
  const enProceso   = useMemo(() => ordenes.filter(o => o.estado === "EN_PROCESO"), [ordenes]);
  const finalizadas = useMemo(() => ordenes.filter(o => o.estado === "FINALIZADA"), [ordenes]);
  const retrasadas  = useMemo(() => ordenes.filter(o => o.retrasada), [ordenes]);

  const ingresosFinalizadas = useMemo(
    () => finalizadas.reduce((s, o) => s + (o.precioManoObra || 0), 0),
    [finalizadas]
  );

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

  const reintentar = async () => {
    setReintentando(true);
    await cargar();
    setReintentando(false);
  };

  const conteoPor = (estado) => ordenes.filter(o => o.estado === estado).length;
  const sinNada = ordenes.length === 0;
  // El flujo se reparte solo sobre lo que no está cancelado, para que la barra cierre en 100%.
  const totalFlujo = ETAPAS.reduce((s, e) => s + conteoPor(e.estado), 0);
  const canceladas = ordenes.length - totalFlujo;
  const conteoTab = (key) => (key === "ordenes" ? pagadas.length + porConfirmar.length
    : key === "proceso" ? enProceso.length
    : key === "finalizadas" ? finalizadas.length
    : 0);
  const vacio = (key) => <EstadoVacio icono="tool" titulo={VACIOS[key][0]} texto={VACIOS[key][1]} />;

  return (
    <div className="pt-anteriores">
      <EncabezadoPagina
        titulo="Órdenes anteriores"
        descripcion="Son órdenes del flujo anterior. Esta sección desaparece sola cuando no quede ninguna por atender."
      />

      {errorCarga && (
        <Alerta className="pt-anteriores__alerta" onReintentar={cargar ? reintentar : undefined} reintentando={reintentando}>
          {errorCarga}
        </Alerta>
      )}
      {error && <Alerta className="pt-anteriores__alerta" onCerrar={() => setError(null)}>{error}</Alerta>}

      <div className="ui-filtros pt-anteriores__filtros" role="group" aria-label="Órdenes anteriores">
        {TABS.map(t => (
          <button key={t.key} type="button" className="ui-chip" aria-pressed={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}
            {conteoTab(t.key) > 0 && <span className="ui-chip__contador">{conteoTab(t.key)}</span>}
          </button>
        ))}
      </div>

      {cargando ? (
        <Esqueleto variante="lista" cantidad={3} texto="Cargando las órdenes anteriores…" />
      ) : sinNada ? (
        <EstadoVacio icono="tool" titulo="Todavía no te llega ningún servicio"
          texto="Cuando un cliente pague una cotización, la orden aparece aquí lista para programar el ingreso del vehículo." />
      ) : (
        <>
          {tab === "resumen" && (
            <>
              <div className="ui-indicadores pt-anteriores__indicadores">
                <Indicador titulo="Por iniciar" valor={pagadas.length} pie="Pagadas y sin trabajar"
                  tono={pagadas.length > 0 ? "accion" : undefined} />
                <Indicador titulo="En proceso" valor={enProceso.length} pie="Vehículos en el taller ahora" />
                <Indicador titulo="Finalizadas" valor={finalizadas.length} pie="Servicios entregados" />
                <Indicador icono="calendar" titulo="Citas agendadas" valor={conCita} pie={`${agenda.length} aún por llegar`} />
                <Indicador icono="alert" titulo="Con retraso" valor={retrasadas.length}
                  tono={retrasadas.length > 0 ? "accion" : undefined}
                  pie={retrasadas.length === 0 ? "Nada se quedó quieto" : "Servicios sin avance hace varios días"} />
                <Indicador titulo="Ingresos por mano de obra" valor={formatPrecio(ingresosFinalizadas)} tono="ok"
                  pie="Suma de los servicios finalizados" />
              </div>

              <section className="pt-flow" aria-labelledby="pt-flow-titulo">
                <div className="pt-flow-head">
                  <h2 id="pt-flow-titulo" className="pt-flow-title">Dónde están tus servicios</h2>
                  <span className="pt-flow-total">
                    {totalFlujo} en taller{canceladas > 0 && ` · ${canceladas} canceladas`}
                  </span>
                </div>
                <div className="pt-flow-bar" aria-hidden="true">
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
                      />
                    ) : null;
                  })}
                </div>
                <ul className="pt-flow-legend">
                  {ETAPAS.map(e => (
                    <li className="pt-flow-key" key={e.estado}>
                      <i className={`pt-flow-dot pt-flow-seg--${e.estado.toLowerCase().replace("_", "-")}`} aria-hidden="true" />
                      {e.label}
                      <strong>{conteoPor(e.estado)}</strong>
                    </li>
                  ))}
                </ul>
              </section>
            </>
          )}

          {tab === "ordenes" && (
            porConfirmar.length + pagadas.length === 0 ? vacio("ordenes") : (
              <div className="pt-list">
                {porConfirmar.map(o => (
                  <article className="pt-card" key={o.id}>
                    <div className="pt-card-media"><FotoVehiculo o={o} /></div>
                    <div className="pt-card-body">
                      <CabeceraOrden o={o} />
                      <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                      <p className="pt-card-meta">
                        Pago reportado: {o.metodoPago === "TRANSFERENCIA" ? "Transferencia" : "Efectivo"}
                        {o.referenciaPago ? ` · Comprobante ${o.referenciaPago}` : ""}
                      </p>
                      <p className="pt-card-total">{formatPrecio(o.total)}</p>
                    </div>
                    <div className="pt-card-actions">
                      <button type="button" className="ui-btn ui-btn--primario ui-btn--bloque"
                        disabled={procesando === o.id} aria-busy={procesando === o.id}
                        onClick={() => handleConfirmarPago(o.id)}>
                        {procesando === o.id ? "Procesando…" : "Confirmar pago recibido"}
                      </button>
                    </div>
                  </article>
                ))}
                {pagadas.map(o => (
                  <article className="pt-card" key={o.id}>
                    <div className="pt-card-media"><FotoVehiculo o={o} /></div>
                    <div className="pt-card-body">
                      <CabeceraOrden o={o} />
                      <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                      <p className="pt-card-meta">
                        {o.productoNombre || "Sin repuesto asociado"} · Mano de obra {formatPrecio(o.precioManoObra)}
                      </p>
                      <p className="pt-card-total">{formatPrecio(o.total)}</p>
                    </div>
                    <div className="pt-card-actions">
                      <Campo id={`cita-${o.id}`} label="Ingreso del vehículo"
                        ayuda={o.fechaProgramada ? `Ya agendado: ${formatFechaHora(o.fechaProgramada)}` : "El cliente aún no tiene cita."}>
                        <input className="ui-field__input" type="datetime-local" value={fechasCita[o.id] || ""}
                          onChange={(e) => setFechasCita(prev => ({ ...prev, [o.id]: e.target.value }))} />
                      </Campo>
                      <button type="button" className="ui-btn ui-btn--secundario ui-btn--bloque"
                        disabled={procesando === o.id || !fechasCita[o.id]}
                        onClick={() => handleProgramar(o.id)}>
                        {procesando === o.id ? "Enviando…" : "Programar y avisar"}
                      </button>
                      <button type="button" className="ui-btn ui-btn--primario ui-btn--bloque"
                        disabled={procesando === o.id} aria-busy={procesando === o.id}
                        onClick={() => handleIniciar(o.id)}>
                        {procesando === o.id ? "Procesando…" : "Iniciar servicio"}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )
          )}

          {tab === "proceso" && (
            enProceso.length === 0 ? vacio("proceso") : (
              <div className="pt-list">
                {enProceso.map(o => {
                  const dias = diasDesde(o.fechaInicio);
                  return (
                    <article className="pt-card" key={o.id}>
                      <div className="pt-card-media"><FotoVehiculo o={o} /></div>
                      <div className="pt-card-body">
                        <CabeceraOrden o={o} />
                        <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                        <p className="pt-card-meta">{o.productoNombre || "Sin repuesto asociado"}</p>
                        <p className="pt-card-meta">
                          Iniciado el {formatFecha(o.fechaInicio)}
                          {dias !== null && dias > 0 && ` · hace ${dias} día${dias === 1 ? "" : "s"}`}
                        </p>
                        <p className="pt-card-total">{formatPrecio(o.total)}</p>
                      </div>
                      <div className="pt-card-actions">
                        <Campo id={`obs-${o.id}`} label="Observaciones del servicio" opcional>
                          <textarea className="ui-field__input" rows={3}
                            placeholder="Qué se hizo, qué se reemplaza, si el cliente debe volver…"
                            value={observaciones[o.id] || ""}
                            onChange={(e) => setObservaciones(prev => ({ ...prev, [o.id]: e.target.value }))} />
                        </Campo>
                        <button type="button" className="ui-btn ui-btn--primario ui-btn--bloque"
                          disabled={procesando === o.id} aria-busy={procesando === o.id}
                          onClick={() => handleFinalizar(o.id)}>
                          {procesando === o.id ? "Procesando…" : "Finalizar servicio"}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )
          )}

          {tab === "finalizadas" && (
            finalizadas.length === 0 ? vacio("finalizadas") : (
              <div className="pt-list">
                {finalizadas.map(o => (
                  <article className="pt-card" key={o.id}>
                    <div className="pt-card-media"><FotoVehiculo o={o} /></div>
                    <div className="pt-card-body">
                      <CabeceraOrden o={o} />
                      <h3 className="pt-card-title">{o.vehiculoDescripcion || "Vehículo por identificar"}</h3>
                      <p className="pt-card-meta">{o.productoNombre || "Sin repuesto asociado"}</p>
                      <p className="pt-card-meta">Finalizado el {formatFecha(o.fechaFinalizacion)}</p>
                      {o.observaciones && <p className="pt-card-note">“{o.observaciones}”</p>}
                    </div>
                    <div className="pt-card-done">
                      <span className="pt-done-ico" aria-hidden="true"><Icon name="check" size={18} /></span>
                      <span className="pt-card-total">{formatPrecio(o.total)}</span>
                    </div>
                  </article>
                ))}
              </div>
            )
          )}
        </>
      )}
    </div>
  );
}
