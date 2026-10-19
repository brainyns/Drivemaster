import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { listarMecanicos, miTaller } from "../services/tallerService";
import {
  bandejaSolicitudesTaller, aceptarSolicitudServicio, rechazarSolicitudServicio, asignarMecanicoSolicitud,
  iniciarInstalacion, marcarEnPruebas, marcarLista, marcarEntregada,
} from "../services/solicitudServicioService";
import { cotizacionesDeOrden } from "../services/cotizacionService";
import { pedidosDeOrden, confirmarRepuestosRecibidos } from "../services/pedidoTiendaService";
import EstadoServicio from "../components/servicio/EstadoServicio";
import LineaTiempo from "../components/servicio/LineaTiempo";
import DiagnosticoForm from "../components/servicio/DiagnosticoForm";
import CotizacionForm from "../components/servicio/CotizacionForm";
import CotizacionDetalle from "../components/servicio/CotizacionDetalle";
import Etiqueta from "../components/servicio/Etiqueta";
import {
  ESTADOS, ESTADOS_PEDIDO, URGENCIAS, ahoraParaInput, formatFecha, formatFechaHora, formatPrecio, refSolicitud,
} from "../components/servicio/estadosServicio";
import { tiempoRelativo } from "./taller/resumenTaller";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Pasos from "../components/ui/Pasos";
import Icon from "../components/public/Icon";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";
import "../css/solicitudes-taller.css";

const ETIQUETA_URGENCIA = Object.fromEntries(URGENCIAS.map((u) => [u.valor, u.label]));

const porResponder = (s) => s.estado === "ASIGNADA_A_TALLER" && !s.aceptadaPorTaller;
const agendada = (s) => (s.estado === "ASIGNADA_A_TALLER" && s.aceptadaPorTaller) || s.estado === "EN_REVISION";

const esperaRepuestos = (s) => s.estado === "PAGADA" || s.estado === "REPUESTOS_PEDIDOS";
const enTaller = (s) => ["REPUESTOS_RECIBIDOS", "EN_INSTALACION", "LISTA"].includes(s.estado);

// Solicitudes: del primer aviso hasta el diagnóstico. La cotización y lo que sigue tienen su propia sección.
const FILTROS_SOLICITUDES = [
  { key: "responder", label: "Por responder", filtro: porResponder },
  { key: "agendadas", label: "Agendadas", filtro: agendada },
  { key: "diagnosticadas", label: "Diagnosticadas", filtro: (s) => s.estado === "DIAGNOSTICADA" },
  { key: "cerradas", label: "Cerradas", filtro: (s) => ["CANCELADA", "RECHAZADA", "REASIGNADA"].includes(s.estado) },
];

// Trabajos: de la espera de repuestos hasta la entrega.
const FILTROS_TRABAJOS = [
  { key: "repuestos", label: "Esperando repuestos", filtro: esperaRepuestos },
  { key: "entaller", label: "En el taller", filtro: enTaller },
  { key: "entregados", label: "Entregados", filtro: (s) => s.estado === "ENTREGADA" },
];

const TEXTOS = {
  solicitudes: {
    titulo: "Solicitudes",
    descripcion: "Diagnósticos que te asignan: respóndelos, agenda la revisión y registra lo que encuentres.",
    volver: "Solicitudes",
    icono: "message",
    vacio: {
      responder: ["Estás al día", "No tienes solicitudes nuevas. Te avisaremos por correo cuando llegue una."],
      agendadas: ["Sin revisiones agendadas", "Al aceptar una solicitud eliges la fecha y la hora de la revisión."],
      diagnosticadas: ["Nada por cotizar", "Las solicitudes aparecen aquí cuando registras el diagnóstico."],
      cerradas: ["Sin solicitudes cerradas", "Aquí quedan las canceladas, las rechazadas y las que pasaron a otro taller."],
    },
  },
  trabajos: {
    titulo: "Trabajos",
    descripcion: "Vehículos con cotización aprobada: repuestos, instalación, pruebas y entrega.",
    volver: "Trabajos",
    icono: "tool",
    vacio: {
      repuestos: ["No esperas repuestos", "Cuando un cliente pague una cotización, el pedido a las tiendas aparece aquí."],
      entaller: ["Ningún vehículo en el taller", "Pasan aquí al confirmar que los repuestos llegaron."],
      entregados: ["Aún no has entregado trabajos", "Los vehículos entregados quedan aquí como historial."],
    },
  },
};

const PASOS_TRABAJO = ["Repuestos", "Instalación", "Pruebas", "Lista", "Entregada"];

/** Paso actual de un trabajo en el taller (0 = repuestos recibidos … 4 = entregada), o -1 si aún espera repuestos. */
function pasoDeTrabajo(s) {
  if (s.estado === "REPUESTOS_RECIBIDOS") return 0;
  if (s.estado === "EN_INSTALACION") return s.subestado === "EN_PRUEBAS" ? 2 : 1;
  if (s.estado === "LISTA") return 3;
  if (s.estado === "ENTREGADA") return 4;
  return -1;
}

/** Texto corto de cuánto lleva la solicitud en su estado actual, según el último evento. */
function enEstadoDesde(s) {
  const fechas = (s.eventos || []).map((e) => new Date(e.fecha).getTime()).filter(Number.isFinite);
  if (!fechas.length) return "";
  const min = Math.max(0, Math.round((Date.now() - Math.max(...fechas)) / 60000));
  if (min < 60) return `hace ${Math.max(min, 1)} min`;
  if (min < 1440) return `hace ${Math.round(min / 60)} h`;
  return `hace ${Math.round(min / 1440)} d`;
}

/** Cotizaciones emitidas, pedidos a tiendas y recepción de repuestos. */
function SeguimientoTaller({ token, solicitud, onCambio }) {
  const [cotizaciones, setCotizaciones] = useState([]);
  const [pedidos, setPedidos] = useState([]);
  const [error, setError] = useState(null);
  const [procesando, setProcesando] = useState(false);

  // Se recarga cada vez que cambia el estado de la solicitud
  useEffect(() => {
    let cancelado = false;
    Promise.all([cotizacionesDeOrden(token, solicitud.id), pedidosDeOrden(token, solicitud.id)])
      .then(([c, p]) => {
        if (cancelado) return;
        setCotizaciones(c || []);
        setPedidos(p || []);
      })
      .catch((e) => { if (!cancelado) setError(e.message); });
    return () => { cancelado = true; };
  }, [token, solicitud.id, solicitud.estado]);

  const recibidos = async () => {
    setProcesando(true);
    setError(null);
    try {
      await confirmarRepuestosRecibidos(token, solicitud.id);
      await onCambio();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const vigente = cotizaciones[0];
  return (
    <>
      {error && <p className="ss-alerta" role="alert">{error}</p>}
      {vigente && (
        <section className="ss-bloque">
          <h3 className="ss-bloque__titulo">Cotización</h3>
          <CotizacionDetalle cotizacion={vigente} mostrarComision />
          {solicitud.estado === "COTIZADA" && <p className="ss-meta">Esperando la respuesta del cliente.</p>}
          {solicitud.estado === "APROBADA" && <p className="ss-meta">El cliente aprobó. Esperando el pago.</p>}
          {cotizaciones.length > 1 && <p className="ss-ayuda">{cotizaciones.length - 1} versión(es) anterior(es).</p>}
        </section>
      )}
      {pedidos.length > 0 && (
        <section className="ss-bloque">
          <h3 className="ss-bloque__titulo">Pedidos a tiendas</h3>
          <ul className="ct-items">
            {pedidos.map((p) => (
              <li key={p.id} className="ct-item">
                <span className="ct-item__desc">
                  <strong>{p.tiendaNombre}</strong>
                  <span className="ss-meta">
                    {p.items.map((i) => `${i.cantidad} × ${i.productoNombre}`).join(", ")}
                    {p.diasEntrega ? ` · encargo hasta ${p.diasEntrega} días` : ""}
                  </span>
                  {p.motivoNoDespacho && <span className="ss-meta">Motivo: {p.motivoNoDespacho}</span>}
                </span>
                <Etiqueta estado={p.estado} catalogo={ESTADOS_PEDIDO} />
              </li>
            ))}
          </ul>
          {solicitud.estado === "REPUESTOS_PEDIDOS" && (
            <div className="ss-acciones">
              <button className="sv-btn" onClick={recibidos} disabled={procesando}>
                {procesando ? "Confirmando..." : "Confirmar repuestos recibidos"}
              </button>
              <span className="ss-ayuda">Confírmalo cuando tengas los repuestos en el taller.</span>
            </div>
          )}
        </section>
      )}
    </>
  );
}

/**
 * Bandeja de solicitudes de diagnóstico del taller: responder (aceptar con cita o
 * rechazar), asignar mecánico, registrar el diagnóstico y elegir repuestos.
 */
export default function TallerSolicitudes({ modo = "solicitudes", filtroInicial }) {
  const token = getToken();
  const FILTROS = modo === "trabajos" ? FILTROS_TRABAJOS : FILTROS_SOLICITUDES;
  const textos = TEXTOS[modo];
  const [solicitudes, setSolicitudes] = useState([]);
  const [mecanicos, setMecanicos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [filtro, setFiltro] = useState(FILTROS.some((f) => f.key === filtroInicial) ? filtroInicial : FILTROS[0].key);
  const [busqueda, setBusqueda] = useState("");
  const [lineaAbierta, setLineaAbierta] = useState(null);
  const [seleccionadaId, setSeleccionadaId] = useState(null);

  const [fechaRevision, setFechaRevision] = useState("");
  const [mecanicoAceptar, setMecanicoAceptar] = useState("");
  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [editandoDiagnostico, setEditandoDiagnostico] = useState(false);
  const [cotizando, setCotizando] = useState(false);
  const [tarifaHora, setTarifaHora] = useState(null);
  const [procesando, setProcesando] = useState(false);
  const [reintentando, setReintentando] = useState(false);
  const [ahora] = useState(() => Date.now());

  const cargar = useCallback(async () => {
    try {
      const [bandeja, mecs, taller] = await Promise.all([
        bandejaSolicitudesTaller(token), listarMecanicos(token), miTaller(token).catch(() => null),
      ]);
      setSolicitudes(bandeja || []);
      setMecanicos(mecs || []);
      setTarifaHora(taller?.tarifaHora || null);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const conteo = useMemo(
    () => Object.fromEntries(FILTROS.map((f) => [f.key, solicitudes.filter(f.filtro).length])),
    [solicitudes, FILTROS]
  );
  const termino = busqueda.trim().toLowerCase();
  const visibles = solicitudes
    .filter(FILTROS.find((f) => f.key === filtro).filtro)
    .filter((s) => !termino || [s.vehiculoDescripcion, s.vehiculoPlaca, s.clienteNombre, refSolicitud(s.id)]
      .some((t) => t && String(t).toLowerCase().includes(termino)));
  const seleccionada = solicitudes.find((s) => s.id === seleccionadaId) || null;
  const mecanicosActivos = mecanicos.filter((m) => m.activo !== false);

  const abrir = (s) => {
    setSeleccionadaId(s.id);
    setFechaRevision("");
    setMecanicoAceptar(s.mecanicoId || "");
    setRechazando(false);
    setMotivo("");
    setEditandoDiagnostico(false);
    setCotizando(false);
    setError(null);
  };

  /** Reemplaza la solicitud actualizada en la lista sin recargar todo. */
  const reemplazar = (actualizada) =>
    setSolicitudes((prev) => prev.map((s) => (s.id === actualizada.id ? { ...s, ...actualizada } : s)));

  const ejecutar = async (accion) => {
    setProcesando(true);
    setError(null);
    try {
      await accion();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const aceptar = () => {
    if (!fechaRevision) return setError("Elige la fecha y hora de la revisión.");
    ejecutar(async () => {
      reemplazar(await aceptarSolicitudServicio(token, seleccionada.id, {
        fechaRevision, mecanicoId: mecanicoAceptar || null,
      }));
    });
  };

  const rechazar = () => ejecutar(async () => {
    await rechazarSolicitudServicio(token, seleccionada.id, motivo.trim() || null);
    // Pasa al siguiente taller: sale de esta bandeja.
    setSeleccionadaId(null);
    await cargar();
  });

  const cambiarMecanico = (mecanicoId) => ejecutar(async () => {
    reemplazar(await asignarMecanicoSolicitud(token, seleccionada.id, mecanicoId));
  });

  const reintentar = async () => {
    setReintentando(true);
    await cargar();
    setReintentando(false);
  };

  if (cargando) {
    return (
      <div className="ss-panel">
        <EncabezadoPagina titulo={textos.titulo} descripcion={textos.descripcion} />
        <Esqueleto variante="lista" cantidad={4} texto={modo === "trabajos" ? "Cargando trabajos…" : "Cargando solicitudes…"} />
      </div>
    );
  }

  // ─── Detalle ────────────────────────────────────────────────────────────
  if (seleccionada) {
    const s = seleccionada;
    const aceptada = !!s.aceptadaPorTaller;
    return (
      <div className="ss-panel">
        <div className="sv-header-top">
          <div>
            <span className="ss-eyebrow">Solicitud #{refSolicitud(s.id)} · {formatFecha(s.fechaCreacion)}</span>
            <h2 className="sv-title">{s.vehiculoDescripcion}</h2>
            <p className="sv-subtitle">{s.vehiculoPlaca ? `Placa ${s.vehiculoPlaca} · ` : ""}<EstadoServicio estado={s.estado} />{s.estado === "EN_INSTALACION" && s.subestado === "EN_PRUEBAS" ? " · En pruebas" : ""}</p>
          </div>
          <button className="sv-volver" onClick={() => setSeleccionadaId(null)}>{textos.volver}</button>
        </div>

        {error && <p className="ss-alerta" role="alert">{error}</p>}

        <div className="ss-detalle">
          <div className="ss-detalle__principal">
            <section className="ss-bloque">
              <h3 className="ss-bloque__titulo">Problema reportado</h3>
              <p className="ss-texto">{s.sintomas}</p>
              {s.fotos?.length > 0 && (
                <div className="ss-galeria">
                  {s.fotos.map((f) => <a key={f} href={f} target="_blank" rel="noreferrer"><img src={f} alt="Foto del cliente" loading="lazy" /></a>)}
                </div>
              )}
              <p className="ss-meta">
                {[s.barrio, s.ciudad].filter(Boolean).join(", ")}
                {s.distanciaTallerKm != null && ` · a ${s.distanciaTallerKm.toFixed(1)} km`}
                {" · "}{s.preferenciaHorario}
              </p>
              {aceptada && s.clienteNombre && (
                <p className="ss-meta">Cliente: <strong>{s.clienteNombre}</strong>{s.clienteTelefono ? ` · ${s.clienteTelefono}` : ""}</p>
              )}
            </section>

            {porResponder(s) && (
              <section className="ss-bloque ss-bloque--destacado">
                <h3 className="ss-bloque__titulo">Responder solicitud</h3>
                <p className="ss-meta">Responde antes del <strong>{formatFechaHora(s.fechaLimiteRespuesta)}</strong>; si no, pasa al siguiente taller.</p>
                {!rechazando ? (
                  <>
                    <div className="sv-form">
                      <div className="sv-field">
                        <label htmlFor="ss-fecha-revision">Fecha y hora de la revisión</label>
                        <input id="ss-fecha-revision" type="datetime-local" min={ahoraParaInput()}
                          value={fechaRevision} onChange={(e) => setFechaRevision(e.target.value)} />
                      </div>
                      <div className="sv-field">
                        <label htmlFor="ss-mecanico-aceptar">Mecánico (opcional)</label>
                        <select id="ss-mecanico-aceptar" value={mecanicoAceptar} onChange={(e) => setMecanicoAceptar(e.target.value)}>
                          <option value="">Asignar después</option>
                          {mecanicosActivos.map((m) => <option key={m.id} value={m.id}>{m.nombre}</option>)}
                        </select>
                      </div>
                    </div>
                    <div className="ss-acciones">
                      <button className="sv-btn" onClick={aceptar} disabled={procesando}>
                        {procesando ? "Procesando..." : "Aceptar y proponer cita"}
                      </button>
                      <button className="sv-btn-outline" onClick={() => setRechazando(true)} disabled={procesando}>Rechazar</button>
                    </div>
                  </>
                ) : (
                  <>
                    <label className="ss-meta" htmlFor="ss-motivo-rechazo">Motivo (opcional): se registra en el historial de la solicitud</label>
                    <textarea id="ss-motivo-rechazo" className="ss-textarea" rows={3} maxLength={300}
                      value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. Sin cupo esta semana" />
                    <div className="ss-acciones">
                      <button className="sv-btn ss-btn-peligro" onClick={rechazar} disabled={procesando}>
                        {procesando ? "Procesando..." : "Confirmar rechazo"}
                      </button>
                      <button className="sv-btn-outline" onClick={() => setRechazando(false)} disabled={procesando}>Volver</button>
                    </div>
                  </>
                )}
              </section>
            )}

            {aceptada && s.fechaRevision && (
              <section className="ss-bloque">
                <h3 className="ss-bloque__titulo">Cita de revisión</h3>
                <p className="ss-texto"><strong>{formatFechaHora(s.fechaRevision)}</strong></p>
                {s.tarifaDiagnostico != null && <p className="ss-meta">Tarifa de diagnóstico: {formatPrecio(s.tarifaDiagnostico)}</p>}
                {s.pagoDiagnosticoEstado === "PENDIENTE" && (
                  <p className="ss-aviso">Esperando el pago del diagnóstico (hasta el {formatFechaHora(s.fechaLimitePagoDiagnostico)}).</p>
                )}
                {["ASIGNADA_A_TALLER", "EN_REVISION", "DIAGNOSTICADA"].includes(s.estado) && (
                  <div className="sv-field ss-mecanico-actual">
                    <label htmlFor="ss-mecanico-cambiar">Mecánico</label>
                    <select id="ss-mecanico-cambiar" value={s.mecanicoId || ""} disabled={procesando}
                      onChange={(e) => e.target.value && cambiarMecanico(e.target.value)}>
                      <option value="">Sin asignar</option>
                      {mecanicos.filter((m) => m.activo !== false || m.id === s.mecanicoId)
                        .map((m) => <option key={m.id} value={m.id}>{m.nombre}</option>)}
                    </select>
                  </div>
                )}
              </section>
            )}

            {s.estado === "EN_REVISION" && (
              <DiagnosticoForm token={token} solicitud={s} mecanicos={mecanicos} onGuardado={reemplazar} />
            )}

            {s.diagnostico && s.estado !== "EN_REVISION" && (
              editandoDiagnostico ? (
                <DiagnosticoForm token={token} solicitud={s} mecanicos={mecanicos}
                  onGuardado={(a) => { reemplazar(a); setEditandoDiagnostico(false); }}
                  onCancelar={() => setEditandoDiagnostico(false)} />
              ) : (
                <section className="ss-bloque">
                  <h3 className="ss-bloque__titulo">Diagnóstico</h3>
                  <p className="ss-meta">
                    Urgencia: <strong className={`ss-urgencia ss-urgencia--${s.diagnostico.urgencia?.toLowerCase()}`}>
                      {ETIQUETA_URGENCIA[s.diagnostico.urgencia]}
                    </strong> · {s.diagnostico.mecanicoNombre} · {formatFechaHora(s.diagnostico.fecha)}
                  </p>
                  <p className="ss-texto">{s.diagnostico.hallazgos}</p>
                  {s.diagnostico.fallas?.length > 0 && (
                    <ul className="ss-lista">{s.diagnostico.fallas.map((f) => <li key={f}>{f}</li>)}</ul>
                  )}
                  {s.diagnostico.observaciones && <p className="ss-meta">{s.diagnostico.observaciones}</p>}
                  {s.diagnostico.fotos?.length > 0 && (
                    <div className="ss-galeria">
                      {s.diagnostico.fotos.map((f) => <a key={f} href={f} target="_blank" rel="noreferrer"><img src={f} alt="Foto del diagnóstico" loading="lazy" /></a>)}
                    </div>
                  )}
                  <h3 className="ss-bloque__titulo">Repuestos necesarios</h3>
                  {s.repuestos?.length > 0 ? (
                    <ul className="ss-lista">
                      {s.repuestos.map((r) => (
                        <li key={r.productoId}>
                          {r.cantidad} × {r.descripcion}{" "}
                          <span className="ss-meta">
                            {r.productoNombre} · {r.tiendaNombre} · {formatPrecio((r.precioReferencia || 0) * r.cantidad)}
                            {r.disponibilidad === "ENCARGO" && ` · Encargo${r.diasEntrega ? ` (${r.diasEntrega} días)` : ""}`}
                            {r.compatibilidadConfirmada && ` · Compatibilidad confirmada por ${r.confirmadoPorMecanicoNombre}`}
                          </span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="ss-meta">Sin repuestos: solo mano de obra.</p>
                  )}
                  {s.estado === "DIAGNOSTICADA" && !cotizando && (
                    <div className="ss-acciones">
                      <button className="sv-btn" onClick={() => setCotizando(true)}>Cotizar</button>
                      <button className="sv-btn-outline" onClick={() => setEditandoDiagnostico(true)}>Editar diagnóstico y repuestos</button>
                    </div>
                  )}
                </section>
              )
            )}

            {s.estado === "DIAGNOSTICADA" && cotizando && !editandoDiagnostico && (
              <CotizacionForm token={token} solicitud={s} tarifaHora={tarifaHora}
                onCancelar={() => setCotizando(false)}
                onEmitida={async () => { setCotizando(false); await cargar(); }} />
            )}

            {["REPUESTOS_RECIBIDOS", "EN_INSTALACION", "LISTA", "ENTREGADA"].includes(s.estado) && (
              <section className="ss-bloque ss-bloque--destacado">
                <h3 className="ss-bloque__titulo">Trabajo en el taller</h3>
                {s.estado === "REPUESTOS_RECIBIDOS" && (
                  <>
                    <p className="ss-meta">Repuestos en el taller. Inicia la instalación cuando empiece el trabajo.</p>
                    <div className="ss-acciones">
                      <button className="sv-btn" disabled={procesando}
                        onClick={() => ejecutar(async () => reemplazar(await iniciarInstalacion(token, s.id)))}>
                        Iniciar instalación
                      </button>
                    </div>
                  </>
                )}
                {s.estado === "EN_INSTALACION" && (
                  <>
                    <p className="ss-meta">
                      En instalación desde {formatFechaHora(s.fechaInicioInstalacion)}
                      {s.subestado === "EN_PRUEBAS" ? " · En pruebas" : ""}
                    </p>
                    <div className="ss-acciones">
                      {s.subestado !== "EN_PRUEBAS" && (
                        <button className="sv-btn-outline" disabled={procesando}
                          onClick={() => ejecutar(async () => reemplazar(await marcarEnPruebas(token, s.id)))}>
                          Marcar en pruebas (opcional)
                        </button>
                      )}
                      <button className="sv-btn" disabled={procesando}
                        onClick={() => ejecutar(async () => reemplazar(await marcarLista(token, s.id)))}>
                        Marcar lista
                      </button>
                    </div>
                  </>
                )}
                {s.estado === "LISTA" && (
                  <>
                    <p className="ss-meta">Lista desde {formatFechaHora(s.fechaLista)}. Avisamos al cliente por correo.</p>
                    <div className="ss-acciones">
                      <button className="sv-btn" disabled={procesando}
                        onClick={() => ejecutar(async () => reemplazar(await marcarEntregada(token, s.id)))}>
                        Marcar entregada
                      </button>
                    </div>
                  </>
                )}
                {s.estado === "ENTREGADA" && (
                  <p className="ss-meta">
                    Entregada el {formatFechaHora(s.fechaEntrega)}
                    {s.recepcionConfirmada
                      ? ` · El cliente confirmó la recepción${s.calificacionTaller ? ` y calificó ${s.calificacionTaller}/5` : ""}`
                      : " · Esperando la confirmación del cliente"}
                  </p>
                )}
              </section>
            )}

            {s.cotizacionId && <SeguimientoTaller token={token} solicitud={s} onCambio={cargar} />}
          </div>

          <aside className="ss-detalle__lateral">
            <h3 className="ss-bloque__titulo">Avance</h3>
            <LineaTiempo eventos={s.eventos} />
          </aside>
        </div>
      </div>
    );
  }

  // ─── Lista ──────────────────────────────────────────────────────────────
  /** Acción que corresponde a este trabajo, para resolverla sin abrir el detalle. */
  const accionRapida = (s) => {
    if (s.estado === "REPUESTOS_PEDIDOS") return {
      texto: "Confirmar repuestos recibidos",
      ejecutar: async () => { await confirmarRepuestosRecibidos(token, s.id); await cargar(); },
    };
    if (s.estado === "REPUESTOS_RECIBIDOS") return { texto: "Iniciar instalación", ejecutar: async () => reemplazar(await iniciarInstalacion(token, s.id)) };
    if (s.estado === "EN_INSTALACION") return { texto: "Marcar lista", ejecutar: async () => reemplazar(await marcarLista(token, s.id)) };
    if (s.estado === "LISTA") return { texto: "Marcar entregada", ejecutar: async () => reemplazar(await marcarEntregada(token, s.id)) };
    return null;
  };

  const tarjetaTrabajo = (s) => {
    const paso = pasoDeTrabajo(s);
    const accion = accionRapida(s);
    const abierta = lineaAbierta === s.id;
    return (
      <li key={s.id} className="ss-tarjeta st-tarjeta st-tarjeta--trabajo">
        <button type="button" className="st-tarjeta__abrir" onClick={() => abrir(s)}>
          <span className="st-tarjeta__cabecera">
            <strong className="st-tarjeta__titulo">{s.vehiculoDescripcion}</strong>
            <ChipEstado estado={s.estado} catalogo={ESTADOS} />
          </span>
          <span className="st-tarjeta__meta">
            {s.vehiculoPlaca && <span className="ui-placa ui-placa--chica">{s.vehiculoPlaca}</span>}
            #{refSolicitud(s.id)}
            {s.clienteNombre && ` · ${s.clienteNombre}`}
            {` · ${s.mecanicoNombre ? `Mecánico: ${s.mecanicoNombre}` : "Sin mecánico asignado"}`}
            {enEstadoDesde(s) && ` · en este estado ${enEstadoDesde(s)}`}
          </span>
        </button>

        {paso >= 0 ? (
          <Pasos pasos={PASOS_TRABAJO} actual={paso + 1} id={`st-pasos-${s.id}`} />
        ) : (
          <p className="st-tarjeta__nota">
            {s.estado === "PAGADA" ? "Pagada: el pedido a las tiendas está por salir." : "Pedido a las tiendas en camino."}
          </p>
        )}

        <div className="st-tarjeta__acciones">
          {accion && (
            <button type="button" className="ui-btn ui-btn--primario" disabled={procesando} aria-busy={procesando}
              onClick={() => ejecutar(accion.ejecutar)}>{accion.texto}</button>
          )}
          <button type="button" className="ui-btn ui-btn--secundario" aria-expanded={abierta} aria-controls={`st-linea-${s.id}`}
            onClick={() => setLineaAbierta(abierta ? null : s.id)}>
            {abierta ? "Ocultar avance" : "Ver avance"}
          </button>
        </div>
        {abierta && <div id={`st-linea-${s.id}`} className="st-tarjeta__linea"><LineaTiempo eventos={s.eventos} /></div>}
      </li>
    );
  };

  /** Chip del plazo para responder: crítico con menos de 2 horas. */
  const chipPlazo = (s) => {
    if (!porResponder(s) || !s.fechaLimiteRespuesta) return null;
    const horas = (new Date(s.fechaLimiteRespuesta).getTime() - ahora) / 3600000;
    return (
      <ChipEstado tono={horas < 2 ? "error" : "aviso"}>
        {horas < 0 ? "Plazo vencido" : `Responde ${tiempoRelativo(s.fechaLimiteRespuesta, ahora)}`}
      </ChipEstado>
    );
  };

  const [tituloVacio, textoVacio] = textos.vacio[filtro];

  return (
    <div className="ss-panel st-root">
      <EncabezadoPagina titulo={textos.titulo} descripcion={textos.descripcion} />

      {error && (
        <Alerta className="st-alerta" onReintentar={reintentar} reintentando={reintentando} onCerrar={() => setError(null)}>
          {error}
        </Alerta>
      )}

      <div className="st-barra">
        <div className="ui-filtros" role="group" aria-label={modo === "trabajos" ? "Trabajos por etapa" : "Solicitudes por estado"}>
          {FILTROS.map((f) => (
            <button key={f.key} type="button" className="ui-chip" aria-pressed={filtro === f.key} onClick={() => setFiltro(f.key)}>
              {f.label}
              {conteo[f.key] > 0 && <span className="ui-chip__contador">{conteo[f.key]}</span>}
            </button>
          ))}
        </div>
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="st-busqueda" className="ui-sr">Buscar por vehículo, placa, cliente o número</label>
          <input id="st-busqueda" className="ui-field__input" type="search" value={busqueda} autoComplete="off"
            onChange={(e) => setBusqueda(e.target.value)} placeholder="Vehículo, placa, cliente o #" />
        </div>
      </div>

      {modo === "solicitudes" && mecanicosActivos.length === 0 && (
        <Alerta tono="aviso" className="st-alerta">
          Registra a tus mecánicos en “Mi taller” para asignarlos a las solicitudes.
        </Alerta>
      )}

      {visibles.length === 0 ? (
        termino ? (
          <EstadoVacio icono="search" titulo="Nada coincide con tu búsqueda"
            texto={`No encontramos “${busqueda.trim()}” en esta pestaña. Prueba con la placa o el nombre del cliente.`}>
            <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setBusqueda("")}>Limpiar búsqueda</button>
          </EstadoVacio>
        ) : (
          <EstadoVacio icono={textos.icono} titulo={tituloVacio} texto={textoVacio} />
        )
      ) : modo === "trabajos" ? (
        <ul className="st-lista">{visibles.map(tarjetaTrabajo)}</ul>
      ) : (
        <ul className="st-lista">
          {visibles.map((s) => (
            <li key={s.id}>
              <button type="button" className="ss-tarjeta st-tarjeta st-tarjeta--boton" onClick={() => abrir(s)}>
                <span className="st-tarjeta__cabecera">
                  <strong className="st-tarjeta__titulo">{s.vehiculoDescripcion}</strong>
                  <span className="st-tarjeta__chips">
                    {chipPlazo(s) || <ChipEstado estado={s.estado} catalogo={ESTADOS} />}
                  </span>
                </span>
                <span className="st-tarjeta__sintomas">{s.sintomas}</span>
                <span className="st-tarjeta__meta">
                  #{refSolicitud(s.id)} · {formatFecha(s.fechaCreacion)}
                  {[s.barrio, s.ciudad].some(Boolean) && ` · ${[s.barrio, s.ciudad].filter(Boolean).join(", ")}`}
                  {s.distanciaTallerKm != null && ` · ${s.distanciaTallerKm.toFixed(1)} km`}
                </span>
                {(s.fechaRevision || s.mecanicoNombre || porResponder(s)) && (
                  <span className="st-tarjeta__meta">
                    {porResponder(s) && `Responde antes del ${formatFechaHora(s.fechaLimiteRespuesta)}`}
                    {s.fechaRevision && <><Icon name="calendar" size={14} /> Revisión: {formatFechaHora(s.fechaRevision)}</>}
                    {s.mecanicoNombre && ` · ${s.mecanicoNombre}`}
                  </span>
                )}
                <Icon name="arrow" size={18} className="st-tarjeta__flecha" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
