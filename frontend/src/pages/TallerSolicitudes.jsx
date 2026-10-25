import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { listarMecanicos } from "../services/tallerService";
import { obtenerHorario } from "../services/horarioService";
import {
  aceptarSolicitudServicio, rechazarSolicitudServicio, asignarMecanicoSolicitud,
  iniciarInstalacion, marcarEnPruebas, marcarLista, marcarEntregada,
} from "../services/solicitudServicioService";
import { cotizacionesDeOrden } from "../services/cotizacionService";
import { pedidosDeOrden, confirmarRepuestosRecibidos } from "../services/pedidoTiendaService";
import LineaTiempo from "../components/servicio/LineaTiempo";
import DiagnosticoForm from "../components/servicio/DiagnosticoForm";
import CotizacionForm from "../components/servicio/CotizacionForm";
import CotizacionDetalle from "../components/servicio/CotizacionDetalle";
import {
  ESTADOS_PEDIDO, URGENCIAS, ahoraParaInput, formatFecha, formatFechaHora, formatPrecio, refSolicitud,
} from "../components/servicio/estadosServicio";
import { ESTADOS_TALLER, estadoDeSolicitud } from "../components/servicio/estadosTaller";
import { tiempoRelativo } from "./taller/resumenTaller";
import { errorDiaCita, hora12, horasDeCita, semanaDesdeFranjas } from "./taller/horarioTaller";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Pasos from "../components/ui/Pasos";
import Campo from "../components/ui/Campo";
import BarraAccionMovil from "../components/ui/BarraAccionMovil";
import Icon from "../components/public/Icon";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";
import "../css/solicitudes-taller.css";

const ETIQUETA_URGENCIA = Object.fromEntries(URGENCIAS.map((u) => [u.valor, u.label]));
const TONO_URGENCIA = { BAJA: "ok", MEDIA: "info", ALTA: "aviso", CRITICA: "error" };

const porResponder = (s) => s.estado === "ASIGNADA_A_TALLER" && !s.aceptadaPorTaller;
const agendada = (s) => (s.estado === "ASIGNADA_A_TALLER" && s.aceptadaPorTaller) || s.estado === "EN_REVISION";

const esperaRepuestos = (s) => s.estado === "PAGADA" || s.estado === "REPUESTOS_PEDIDOS";
const enTaller = (s) => ["REPUESTOS_RECIBIDOS", "EN_INSTALACION", "LISTA"].includes(s.estado);

// Solicitudes: del primer aviso hasta el diagnóstico. La cotización y lo que sigue tienen su propia sección.
const FILTROS_SOLICITUDES = [
  { key: "responder", label: "Por responder", filtro: porResponder },
  { key: "agendadas", label: "Agendadas", filtro: agendada },
  { key: "diagnosticadas", label: "Por cotizar", filtro: (s) => s.estado === "DIAGNOSTICADA" },
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
    icono: "inbox",
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

/** Chip de estado con el nombre y el color de fase del panel del taller (estadosTaller.js). */
function ChipEstadoSolicitud({ s }) {
  const { label, tono } = estadoDeSolicitud(s);
  return <ChipEstado tono={tono}>{label}</ChipEstado>;
}

const PASOS_TRABAJO =["Repuestos", "Instalación", "Pruebas", "Lista", "Entregada"];

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
      {error && <Alerta onCerrar={() => setError(null)}>{error}</Alerta>}
      {vigente && (
        <section className="ss-bloque" aria-labelledby="st-cotizacion">
          <h2 id="st-cotizacion" className="ss-bloque__titulo">Cotización</h2>
          <CotizacionDetalle cotizacion={vigente} mostrarComision />
          {solicitud.estado === "COTIZADA" && <p className="ss-meta">Esperando la respuesta del cliente.</p>}
          {solicitud.estado === "APROBADA" && <p className="ss-meta">El cliente aprobó. Esperando el pago.</p>}
          {cotizaciones.length > 1 && (
            <p className="ss-ayuda">
              {cotizaciones.length - 1} {cotizaciones.length === 2 ? "versión anterior" : "versiones anteriores"}.
            </p>
          )}
        </section>
      )}
      {pedidos.length > 0 && (
        <section className="ss-bloque" aria-labelledby="st-pedidos">
          <h2 id="st-pedidos" className="ss-bloque__titulo">Pedidos a tiendas</h2>
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
                <ChipEstado estado={p.estado} catalogo={ESTADOS_PEDIDO} />
              </li>
            ))}
          </ul>
          {solicitud.estado === "REPUESTOS_PEDIDOS" && (
            <div className="ui-form-pie">
              <button type="button" className="ui-btn ui-btn--primario" onClick={recibidos} disabled={procesando} aria-busy={procesando}>
                {procesando ? "Confirmando…" : "Confirmar repuestos recibidos"}
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
 *
 * La bandeja y los datos del taller los carga PanelTaller una sola vez y llegan por props (solicitudes, taller);
 * onReemplazar actualiza una solicitud sin recargar y onRecargar vuelve a pedir la bandeja. Aquí solo se cargan los
 * mecánicos y el horario, que no usa ninguna otra sección.
 */
export default function TallerSolicitudes({
  modo = "solicitudes", filtroInicial, abrirInicial = null, onVolver, textoAfuera = "Inicio",
  solicitudes = [], taller = null, cargandoBandeja = false, errorBandeja = null, onRecargar, onReemplazar,
}) {
  const token = getToken();
  const FILTROS = modo === "trabajos" ? FILTROS_TRABAJOS : FILTROS_SOLICITUDES;
  const textos = TEXTOS[modo];
  const [mecanicos, setMecanicos] = useState([]);
  const [extrasListos, setExtrasListos] = useState(false);
  const cargando = cargandoBandeja || !extrasListos;
  const [error, setError] = useState(null);
  const [filtro, setFiltro] = useState(FILTROS.some((f) => f.key === filtroInicial) ? filtroInicial : FILTROS[0].key);
  const [busqueda, setBusqueda] = useState("");
  const [lineaAbierta, setLineaAbierta] = useState(null);
  // Desde Inicio se abre directamente una solicitud (abrirInicial); su "Volver" regresa allá (onVolver)
  const [seleccionadaId, setSeleccionadaId] = useState(abrirInicial);

  // La cita se elige en dos partes: el día y una hora dentro del horario del taller ("YYYY-MM-DD" y "HH:mm")
  const [fechaCita, setFechaCita] = useState("");
  const [horaCita, setHoraCita] = useState("");
  // Horario por días del taller; null si aún no lo configura (entonces cualquier día y hora)
  const [semanaHorario, setSemanaHorario] = useState(null);
  const [mecanicoAceptar, setMecanicoAceptar] = useState("");
  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [editandoDiagnostico, setEditandoDiagnostico] = useState(false);
  const [cotizando, setCotizando] = useState(false);
  const [procesando, setProcesando] = useState(false);
  const [reintentando, setReintentando] = useState(false);
  const [errorFecha, setErrorFecha] = useState(null);
  const [ahora] = useState(() => Date.now());

  const tallerId = taller?.id;
  const tarifaHora = taller?.tarifaHora || null;

  const cargarExtras = useCallback(async () => {
    try {
      const [mecs, filas] = await Promise.all([
        listarMecanicos(token), tallerId ? obtenerHorario(token, tallerId).catch(() => []) : [],
      ]);
      setMecanicos(mecs || []);
      setSemanaHorario(semanaDesdeFranjas(filas));
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setExtrasListos(true);
    }
  }, [token, tallerId]);

  // Espera a que el panel tenga el taller: así los mecánicos y el horario se piden una sola vez
  useEffect(() => {
    if (cargandoBandeja) return undefined;
    const id = setTimeout(cargarExtras, 0);
    return () => clearTimeout(id);
  }, [cargarExtras, cargandoBandeja]);

  /** Vuelve a pedir la bandeja (la tiene PanelTaller) tras una acción que cambia más de una solicitud. */
  const cargar = async () => { await onRecargar?.(); };

  // Abierta desde Inicio: el detalle empieza arriba, no a la altura donde estaba la lista de urgentes
  useEffect(() => {
    if (abrirInicial) window.scrollTo({ top: 0 });
  }, [abrirInicial]);

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

  // El detalle y la lista empiezan arriba: si no, se abren a la altura de la tarjeta tocada
  const alInicio = () => window.scrollTo({ top: 0 });

  const desdeAfuera = !!onVolver && abrirInicial != null && seleccionadaId === abrirInicial;

  const cerrarDetalle = () => {
    if (desdeAfuera) { onVolver(); return; }
    setSeleccionadaId(null);
    alInicio();
  };

  const abrir = (s) => {
    alInicio();
    setSeleccionadaId(s.id);
    setErrorFecha(null);
    setFechaCita("");
    setHoraCita("");
    setMecanicoAceptar(s.mecanicoId || "");
    setRechazando(false);
    setMotivo("");
    setEditandoDiagnostico(false);
    setCotizando(false);
    setError(null);
  };

  /** Reemplaza la solicitud actualizada en la lista sin recargar todo. */
  const reemplazar = (actualizada) => onReemplazar?.(actualizada);

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

  const errorDia = errorDiaCita(semanaHorario, fechaCita);
  const horasCita = fechaCita && !errorDia ? horasDeCita(semanaHorario, fechaCita) : [];

  const aceptar = () => {
    const problema = !fechaCita ? "Elige el día de la revisión." : errorDia || (!horaCita ? "Elige la hora de la revisión." : null);
    if (problema) {
      setErrorFecha(problema);
      document.getElementById(fechaCita && !errorDia ? "ss-hora-revision" : "ss-fecha-revision")?.focus();
      return;
    }
    ejecutar(async () => {
      reemplazar(await aceptarSolicitudServicio(token, seleccionada.id, {
        fechaRevision: `${fechaCita}T${horaCita}`, mecanicoId: mecanicoAceptar || null,
      }));
    });
  };

  const rechazar = () => ejecutar(async () => {
    await rechazarSolicitudServicio(token, seleccionada.id, motivo.trim() || null);
    // Pasa al siguiente taller: sale de esta bandeja. Si se abrió desde Inicio, se vuelve allá.
    if (desdeAfuera) { onVolver(); return; }
    setSeleccionadaId(null);
    await cargar();
  });

  const cambiarMecanico = (mecanicoId) => ejecutar(async () => {
    reemplazar(await asignarMecanicoSolicitud(token, seleccionada.id, mecanicoId));
  });

  const reintentar = async () => {
    setReintentando(true);
    await Promise.all([cargar(), cargarExtras()]);
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

  /** Acción que corresponde a este trabajo, para resolverla sin abrir el detalle (o desde la barra del celular). */
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

  // ─── Detalle ────────────────────────────────────────────────────────────
  if (seleccionada) {
    const s = seleccionada;
    const aceptada = !!s.aceptadaPorTaller;
    const enPruebas = s.estado === "EN_INSTALACION" && s.subestado === "EN_PRUEBAS";
    const accion = accionRapida(s);
    // Lo principal de este estado, a mano en la barra inferior del celular
    const barra = porResponder(s) && !rechazando
      ? { etiqueta: "Responde antes del", valor: formatFechaHora(s.fechaLimiteRespuesta), texto: "Aceptar y proponer cita", onClick: aceptar }
      : s.estado === "DIAGNOSTICADA" && !cotizando && !editandoDiagnostico
        ? { etiqueta: "Diagnóstico listo", texto: "Cotizar", onClick: () => setCotizando(true) }
        : accion
          ? { etiqueta: ESTADOS_TALLER[s.estado]?.label, texto: accion.texto, onClick: () => ejecutar(accion.ejecutar) }
          : null;

    return (
      <div className="ss-panel st-root st-detalle">
        <EncabezadoPagina titulo={s.vehiculoDescripcion} onVolver={cerrarDetalle}
          textoVolver={desdeAfuera ? textoAfuera : textos.volver}>
          <p className="st-detalle__meta">
            <ChipEstadoSolicitud s={s} />
            {enPruebas && <ChipEstado tono="info">En pruebas</ChipEstado>}
            {s.vehiculoPlaca && <span className="ui-placa">{s.vehiculoPlaca}</span>}
            <span>Solicitud #{refSolicitud(s.id)} · {formatFecha(s.fechaCreacion)}</span>
          </p>
        </EncabezadoPagina>

        {error && <Alerta className="st-alerta" onCerrar={() => setError(null)}>{error}</Alerta>}

        <div className="ss-detalle">
          <div className="ss-detalle__principal">
            <section className="ss-bloque" aria-labelledby="st-problema">
              <h2 id="st-problema" className="ss-bloque__titulo">Problema reportado</h2>
              <p className="ss-texto">{s.sintomas}</p>
              {s.fotos?.length > 0 && (
                <div className="ss-galeria">
                  {s.fotos.map((f, i) => (
                    <a key={f} href={f} target="_blank" rel="noreferrer">
                      <img src={f} alt={`Foto ${i + 1} del cliente`} loading="lazy" />
                    </a>
                  ))}
                </div>
              )}
              <dl className="ui-datos st-detalle__datos">
                <div className="ui-datos__item">
                  <dt>Ubicación</dt>
                  <dd>
                    {[s.barrio, s.ciudad].filter(Boolean).join(", ") || "Sin indicar"}
                    {s.distanciaTallerKm != null && ` · a ${s.distanciaTallerKm.toFixed(1)} km`}
                  </dd>
                </div>
                {s.preferenciaHorario && (
                  <div className="ui-datos__item"><dt>Prefiere</dt><dd>{s.preferenciaHorario}</dd></div>
                )}
                {aceptada && s.clienteNombre && (
                  <div className="ui-datos__item">
                    <dt>Cliente</dt>
                    <dd>
                      {s.clienteNombre}
                      {s.clienteTelefono && <> · <a className="st-detalle__tel" href={`tel:${s.clienteTelefono}`}>{s.clienteTelefono}</a></>}
                    </dd>
                  </div>
                )}
              </dl>
            </section>

            {porResponder(s) && (
              <section className="ss-bloque ss-bloque--destacado" aria-labelledby="st-responder">
                <h2 id="st-responder" className="ss-bloque__titulo">Responder solicitud</h2>
                <p className="ss-meta">
                  Responde antes del <strong>{formatFechaHora(s.fechaLimiteRespuesta)}</strong>
                  {s.fechaLimiteRespuesta && ` (${tiempoRelativo(s.fechaLimiteRespuesta, ahora)})`}; si no, pasa al siguiente taller.
                </p>
                {!rechazando ? (
                  <>
                    <div className="st-form">
                      <Campo id="ss-fecha-revision" label="Día de la revisión" error={errorDia || (!fechaCita && errorFecha)}
                        ayuda={semanaHorario ? "Solo los días que abre tu taller." : "Configura tu horario en Mi taller para limitar los días y las horas."}>
                        <input className="ui-field__input" type="date" min={ahoraParaInput().slice(0, 10)} value={fechaCita}
                          onChange={(e) => { setFechaCita(e.target.value); setHoraCita(""); setErrorFecha(null); }} />
                      </Campo>
                      <Campo id="ss-hora-revision" label="Hora" error={fechaCita && !errorDia && !horaCita ? errorFecha : null}>
                        <select className="ui-field__input" value={horaCita} disabled={!horasCita.length}
                          onChange={(e) => { setHoraCita(e.target.value); setErrorFecha(null); }}>
                          <option value="">{fechaCita ? "Elige la hora" : "Primero elige el día"}</option>
                          {horasCita.map((h) => <option key={h} value={h}>{hora12(h)}</option>)}
                        </select>
                      </Campo>
                      <Campo id="ss-mecanico-aceptar" label="Mecánico" opcional
                        ayuda={mecanicosActivos.length === 0 ? "Aún no tienes mecánicos activos: agrégalos en Mi taller." : undefined}>
                        <select className="ui-field__input" value={mecanicoAceptar} onChange={(e) => setMecanicoAceptar(e.target.value)}>
                          <option value="">Asignar después</option>
                          {mecanicosActivos.map((m) => <option key={m.id} value={m.id}>{m.nombre}</option>)}
                        </select>
                      </Campo>
                    </div>
                    <div className="ui-form-pie">
                      <button type="button" className="ui-btn ui-btn--primario" onClick={aceptar} disabled={procesando} aria-busy={procesando}>
                        {procesando ? "Enviando…" : "Aceptar y proponer cita"}
                      </button>
                      <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setRechazando(true)} disabled={procesando}>Rechazar</button>
                    </div>
                  </>
                ) : (
                  <>
                    <Campo id="ss-motivo-rechazo" label="Motivo del rechazo" opcional ayuda="Se registra en el historial de la solicitud.">
                      <textarea className="ui-field__input" rows={3} maxLength={300} autoFocus
                        value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. Sin cupo esta semana" />
                    </Campo>
                    <div className="ui-form-pie">
                      <button type="button" className="ui-btn ui-btn--peligro" onClick={rechazar} disabled={procesando} aria-busy={procesando}>
                        {procesando ? "Rechazando…" : "Confirmar rechazo"}
                      </button>
                      <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setRechazando(false)} disabled={procesando}>Volver</button>
                    </div>
                  </>
                )}
              </section>
            )}

            {aceptada && s.fechaRevision && (
              <section className="ss-bloque" aria-labelledby="st-cita">
                <h2 id="st-cita" className="ss-bloque__titulo">Cita de revisión</h2>
                <p className="st-detalle__cita"><Icon name="calendar" size={18} /> {formatFechaHora(s.fechaRevision)}</p>
                {s.tarifaDiagnostico != null && <p className="ss-meta">Tarifa de diagnóstico: {formatPrecio(s.tarifaDiagnostico)}</p>}
                {s.pagoDiagnosticoEstado === "PENDIENTE" && (
                  <Alerta tono="aviso">Esperando el pago del diagnóstico (hasta el {formatFechaHora(s.fechaLimitePagoDiagnostico)}).</Alerta>
                )}
                {["ASIGNADA_A_TALLER", "EN_REVISION", "DIAGNOSTICADA"].includes(s.estado) && (
                  <Campo id="ss-mecanico-cambiar" label="Mecánico" className="st-detalle__mecanico">
                    <select className="ui-field__input" value={s.mecanicoId || ""} disabled={procesando}
                      onChange={(e) => e.target.value && cambiarMecanico(e.target.value)}>
                      <option value="">Sin asignar</option>
                      {mecanicos.filter((m) => m.activo !== false || m.id === s.mecanicoId)
                        .map((m) => <option key={m.id} value={m.id}>{m.nombre}</option>)}
                    </select>
                  </Campo>
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
                <section className="ss-bloque" aria-labelledby="st-diagnostico">
                  <h2 id="st-diagnostico" className="ss-bloque__titulo">Diagnóstico</h2>
                  <p className="st-detalle__meta st-detalle__meta--bloque">
                    <ChipEstado tono={TONO_URGENCIA[s.diagnostico.urgencia] || "info"}>
                      Urgencia {(ETIQUETA_URGENCIA[s.diagnostico.urgencia] || "").toLowerCase()}
                    </ChipEstado>
                    <span>{s.diagnostico.mecanicoNombre} · {formatFechaHora(s.diagnostico.fecha)}</span>
                  </p>
                  <p className="ss-texto">{s.diagnostico.hallazgos}</p>
                  {s.diagnostico.fallas?.length > 0 && (
                    <ul className="ss-lista">{s.diagnostico.fallas.map((f) => <li key={f}>{f}</li>)}</ul>
                  )}
                  {s.diagnostico.observaciones && <p className="ss-meta">{s.diagnostico.observaciones}</p>}
                  {s.diagnostico.fotos?.length > 0 && (
                    <div className="ss-galeria">
                      {s.diagnostico.fotos.map((f, i) => (
                        <a key={f} href={f} target="_blank" rel="noreferrer">
                          <img src={f} alt={`Foto ${i + 1} del diagnóstico`} loading="lazy" />
                        </a>
                      ))}
                    </div>
                  )}
                  <h3 className="st-detalle__subtitulo">Repuestos necesarios</h3>
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
                    <div className="ui-form-pie">
                      <button type="button" className="ui-btn ui-btn--primario" onClick={() => setCotizando(true)}>
                        <Icon name="file" size={16} /> Cotizar
                      </button>
                      <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setEditandoDiagnostico(true)}>
                        Editar diagnóstico y repuestos
                      </button>
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
              <section className="ss-bloque ss-bloque--destacado" aria-labelledby="st-trabajo">
                <h2 id="st-trabajo" className="ss-bloque__titulo">Trabajo en el taller</h2>
                <Pasos pasos={PASOS_TRABAJO} actual={pasoDeTrabajo(s) + 1} id={`st-pasos-detalle-${s.id}`} />
                {s.estado === "REPUESTOS_RECIBIDOS" && (
                  <p className="ss-meta">Repuestos en el taller. Inicia la instalación cuando empiece el trabajo.</p>
                )}
                {s.estado === "EN_INSTALACION" && (
                  <p className="ss-meta">En instalación desde {formatFechaHora(s.fechaInicioInstalacion)}{enPruebas ? " · En pruebas" : ""}</p>
                )}
                {s.estado === "LISTA" && (
                  <p className="ss-meta">Lista desde {formatFechaHora(s.fechaLista)}. Avisamos al cliente por correo.</p>
                )}
                {s.estado === "ENTREGADA" && (
                  <p className="ss-meta">
                    Entregada el {formatFechaHora(s.fechaEntrega)}
                    {s.recepcionConfirmada
                      ? ` · El cliente confirmó la recepción${s.calificacionTaller ? ` y calificó ${s.calificacionTaller}/5` : ""}`
                      : " · Esperando la confirmación del cliente"}
                  </p>
                )}
                {(accion || (s.estado === "EN_INSTALACION" && !enPruebas)) && (
                  <div className="ui-form-pie">
                    {s.estado === "EN_INSTALACION" && !enPruebas && (
                      <button type="button" className="ui-btn ui-btn--secundario" disabled={procesando}
                        onClick={() => ejecutar(async () => reemplazar(await marcarEnPruebas(token, s.id)))}>
                        Marcar en pruebas (opcional)
                      </button>
                    )}
                    {accion && (
                      <button type="button" className="ui-btn ui-btn--primario" disabled={procesando} aria-busy={procesando}
                        onClick={() => ejecutar(accion.ejecutar)}>
                        {accion.texto}
                      </button>
                    )}
                  </div>
                )}
              </section>
            )}

            {s.cotizacionId && <SeguimientoTaller token={token} solicitud={s} onCambio={cargar} />}
          </div>

          <aside className="ss-detalle__lateral" aria-labelledby="st-avance">
            <h2 id="st-avance" className="ss-bloque__titulo">Avance</h2>
            <LineaTiempo eventos={s.eventos} catalogo={ESTADOS_TALLER} />
          </aside>
        </div>

        {barra && (
          <BarraAccionMovil etiqueta={barra.etiqueta} valor={barra.valor}>
            <button type="button" className="ui-btn ui-btn--primario" onClick={barra.onClick} disabled={procesando} aria-busy={procesando}>
              {barra.texto}
            </button>
          </BarraAccionMovil>
        )}
      </div>
    );
  }

  // ─── Lista ──────────────────────────────────────────────────────────────
  const tarjetaTrabajo = (s) => {
    const paso = pasoDeTrabajo(s);
    const accion = accionRapida(s);
    const abierta = lineaAbierta === s.id;
    return (
      <li key={s.id} className="ss-tarjeta st-tarjeta st-tarjeta--trabajo">
        <button type="button" className="st-tarjeta__abrir" onClick={() => abrir(s)}>
          <span className="st-tarjeta__cabecera">
            <strong className="st-tarjeta__titulo">{s.vehiculoDescripcion}</strong>
            <ChipEstadoSolicitud s={s} />
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
        {abierta && <div id={`st-linea-${s.id}`} className="st-tarjeta__linea"><LineaTiempo eventos={s.eventos} catalogo={ESTADOS_TALLER} /></div>}
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

      {(error || errorBandeja) && (
        <Alerta className="st-alerta" onReintentar={reintentar} reintentando={reintentando}
          onCerrar={error ? () => setError(null) : undefined}>
          {error || errorBandeja}
        </Alerta>
      )}

      <div className="st-barra">
        <div className="ui-filtros" role="group" aria-label={modo === "trabajos" ? "Trabajos por etapa" : "Solicitudes por estado"}>
          {FILTROS.map((f) => (
            <button key={f.key} type="button" className="ui-chip" aria-pressed={filtro === f.key} onClick={() => setFiltro(f.key)}>
              {f.label}
              <span className="ui-chip__contador">{conteo[f.key]}</span>
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
          <EstadoVacio icono={textos.icono} titulo={tituloVacio} texto={textoVacio} compacto>
            {/* Sin nada por responder, lo siguiente que pide trabajo son los diagnósticos por cotizar */}
            {modo === "solicitudes" && filtro === "responder" && conteo.diagnosticadas > 0 && (
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setFiltro("diagnosticadas")}>
                Tienes {conteo.diagnosticadas} {conteo.diagnosticadas === 1 ? "diagnóstico" : "diagnósticos"} por cotizar
                · Ir a cotizar <Icon name="arrow" size={16} />
              </button>
            )}
          </EstadoVacio>
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
                    {chipPlazo(s) || <ChipEstadoSolicitud s={s} />}
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
