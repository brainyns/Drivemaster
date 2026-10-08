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
  ESTADOS_EN_CURSO, ESTADOS_PEDIDO, URGENCIAS, ahoraParaInput, formatFecha, formatFechaHora, formatPrecio, refSolicitud,
} from "../components/servicio/estadosServicio";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";

const ETIQUETA_URGENCIA = Object.fromEntries(URGENCIAS.map((u) => [u.valor, u.label]));

const porResponder = (s) => s.estado === "ASIGNADA_A_TALLER" && !s.aceptadaPorTaller;
const agendada = (s) => (s.estado === "ASIGNADA_A_TALLER" && s.aceptadaPorTaller) || s.estado === "EN_REVISION";
const enCurso = (s) => ESTADOS_EN_CURSO.includes(s.estado);

const FILTROS = [
  { key: "responder", label: "Por responder", filtro: porResponder },
  { key: "agendadas", label: "Agendadas", filtro: agendada },
  { key: "diagnosticadas", label: "Diagnosticadas", filtro: (s) => s.estado === "DIAGNOSTICADA" },
  { key: "encurso", label: "En curso", filtro: enCurso },
  { key: "historial", label: "Historial", filtro: (s) => !porResponder(s) && !agendada(s) && !enCurso(s) && s.estado !== "DIAGNOSTICADA" },
];

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
export default function TallerSolicitudes({ filtroInicial = "responder" }) {
  const token = getToken();
  const [solicitudes, setSolicitudes] = useState([]);
  const [mecanicos, setMecanicos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [filtro, setFiltro] = useState(FILTROS.some((f) => f.key === filtroInicial) ? filtroInicial : "responder");
  const [seleccionadaId, setSeleccionadaId] = useState(null);

  const [fechaRevision, setFechaRevision] = useState("");
  const [mecanicoAceptar, setMecanicoAceptar] = useState("");
  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [editandoDiagnostico, setEditandoDiagnostico] = useState(false);
  const [cotizando, setCotizando] = useState(false);
  const [tarifaHora, setTarifaHora] = useState(null);
  const [procesando, setProcesando] = useState(false);

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

  useEffect(() => { cargar(); }, [cargar]);

  const conteo = useMemo(
    () => Object.fromEntries(FILTROS.map((f) => [f.key, solicitudes.filter(f.filtro).length])),
    [solicitudes]
  );
  const visibles = solicitudes.filter(FILTROS.find((f) => f.key === filtro).filtro);
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

  if (cargando) return <p className="sv-empty">Cargando solicitudes...</p>;

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
          <button className="sv-volver" onClick={() => setSeleccionadaId(null)}>← Solicitudes</button>
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
  return (
    <div className="ss-panel">
      {error && <p className="ss-alerta" role="alert">{error}</p>}

      <nav className="ss-filtros" aria-label="Solicitudes por estado">
        {FILTROS.map((f) => (
          <button key={f.key} className={`ss-filtro${filtro === f.key ? " ss-filtro--activo" : ""}`}
            aria-current={filtro === f.key ? "page" : undefined} onClick={() => setFiltro(f.key)}>
            {f.label}
            {conteo[f.key] > 0 && <span className="ss-filtro__conteo">{conteo[f.key]}</span>}
          </button>
        ))}
      </nav>

      {mecanicosActivos.length === 0 && (
        <p className="ss-aviso">Registra a tus mecánicos en la pestaña “Mi taller” para asignarlos a las solicitudes.</p>
      )}

      {visibles.length === 0 ? (
        <p className="sv-empty">
          {filtro === "responder" ? "No tienes solicitudes nuevas. Te avisaremos por correo cuando llegue una." : "No hay solicitudes en esta lista."}
        </p>
      ) : (
        <div className="sv-list">
          {visibles.map((s) => (
            <button key={s.id} className="ss-tarjeta" onClick={() => abrir(s)}>
              <div className="ss-tarjeta__cabecera">
                <span className="ss-eyebrow">#{refSolicitud(s.id)} · {formatFecha(s.fechaCreacion)}</span>
                <EstadoServicio estado={s.estado} />
              </div>
              <strong className="ss-tarjeta__titulo">{s.vehiculoDescripcion}</strong>
              <span className="ss-meta ss-tarjeta__sintomas">{s.sintomas}</span>
              <span className="ss-meta">
                {[s.barrio, s.ciudad].filter(Boolean).join(", ")}
                {porResponder(s) && ` · Responde antes del ${formatFechaHora(s.fechaLimiteRespuesta)}`}
                {s.fechaRevision && ` · Revisión: ${formatFechaHora(s.fechaRevision)}`}
                {s.mecanicoNombre && ` · ${s.mecanicoNombre}`}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
