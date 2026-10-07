import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { misSolicitudesServicio, cancelarSolicitudServicio } from "../services/solicitudServicioService";
import EstadoServicio from "../components/servicio/EstadoServicio";
import LineaTiempo from "../components/servicio/LineaTiempo";
import {
  ESTADOS_CANCELABLES_CLIENTE, URGENCIAS, formatFecha, formatFechaHora, formatPrecio, refSolicitud,
} from "../components/servicio/estadosServicio";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";

const ETIQUETA_URGENCIA = Object.fromEntries(URGENCIAS.map((u) => [u.valor, u.label]));

/** Solicitudes de diagnóstico del cliente y el avance de cada una. */
export default function MisServicios({ onVolver, onSolicitar }) {
  const token = getToken();
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [seleccionadaId, setSeleccionadaId] = useState(null);
  const [cancelando, setCancelando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [procesando, setProcesando] = useState(false);

  const cargar = useCallback(async () => {
    try {
      const data = await misSolicitudesServicio(token);
      setSolicitudes(data || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => { cargar(); }, [cargar]);

  const seleccionada = solicitudes.find((s) => s.id === seleccionadaId) || null;

  const cancelar = async () => {
    setProcesando(true);
    setError(null);
    try {
      await cancelarSolicitudServicio(token, seleccionada.id, motivo.trim() || null);
      setCancelando(false);
      setMotivo("");
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  if (cargando) return <div className="sv-root"><p className="sv-empty">Cargando...</p></div>;

  if (seleccionada) {
    const s = seleccionada;
    const cancelable = ESTADOS_CANCELABLES_CLIENTE.includes(s.estado);
    return (
      <div className="sv-root">
        <div className="sv-header-top">
          <div>
            <span className="ss-eyebrow">Solicitud #{refSolicitud(s.id)} · {formatFecha(s.fechaCreacion)}</span>
            <h1 className="sv-title">{s.vehiculoDescripcion}</h1>
            <p className="sv-subtitle">{s.vehiculoPlaca ? `Placa ${s.vehiculoPlaca} · ` : ""}<EstadoServicio estado={s.estado} /></p>
          </div>
          <button className="sv-volver" onClick={() => { setSeleccionadaId(null); setCancelando(false); }}>← Mis servicios</button>
        </div>

        {error && <p className="ss-alerta" role="alert">{error}</p>}

        <div className="ss-detalle">
          <div className="ss-detalle__principal">
            <section className="ss-bloque">
              <h2 className="ss-bloque__titulo">Lo que reportaste</h2>
              <p className="ss-texto">{s.sintomas}</p>
              {s.fotos?.length > 0 && (
                <div className="ss-galeria">
                  {s.fotos.map((f) => <a key={f} href={f} target="_blank" rel="noreferrer"><img src={f} alt="Foto del problema" loading="lazy" /></a>)}
                </div>
              )}
              <p className="ss-meta">{[s.barrio, s.ciudad].filter(Boolean).join(", ")} · {s.preferenciaHorario}</p>
            </section>

            {s.tallerNombre && (
              <section className="ss-bloque">
                <h2 className="ss-bloque__titulo">Taller</h2>
                <p className="ss-texto"><strong>{s.tallerNombre}</strong></p>
                {s.tallerDireccion && <p className="ss-meta">{s.tallerDireccion}</p>}
                {s.aceptadaPorTaller && s.tallerTelefono && <p className="ss-meta">Teléfono: {s.tallerTelefono}</p>}
                {!s.aceptadaPorTaller && s.estado === "ASIGNADA_A_TALLER" && (
                  <p className="ss-meta">Esperando que el taller confirme y te proponga una fecha.</p>
                )}
              </section>
            )}

            {s.fechaRevision && (
              <section className="ss-bloque ss-bloque--destacado">
                <h2 className="ss-bloque__titulo">Cita de revisión</h2>
                <p className="ss-texto"><strong>{formatFechaHora(s.fechaRevision)}</strong></p>
                {s.mecanicoNombre && <p className="ss-meta">Mecánico: {s.mecanicoNombre}</p>}
                {s.tarifaDiagnostico != null && (
                  <p className="ss-meta">
                    Tarifa de diagnóstico: {formatPrecio(s.tarifaDiagnostico)} (se descuenta si aceptas la reparación)
                  </p>
                )}
                {s.pagoDiagnosticoEstado === "PENDIENTE" && (
                  <p className="ss-aviso">
                    Para confirmar la cita debes pagar el diagnóstico antes del {formatFechaHora(s.fechaLimitePagoDiagnostico)}.
                    El pago en línea estará disponible muy pronto.
                  </p>
                )}
              </section>
            )}

            {s.diagnostico && (
              <section className="ss-bloque">
                <h2 className="ss-bloque__titulo">Diagnóstico</h2>
                <p className="ss-meta">
                  Urgencia: <strong className={`ss-urgencia ss-urgencia--${s.diagnostico.urgencia?.toLowerCase()}`}>
                    {ETIQUETA_URGENCIA[s.diagnostico.urgencia] || s.diagnostico.urgencia}
                  </strong>
                  {s.diagnostico.mecanicoNombre && ` · ${s.diagnostico.mecanicoNombre}`}
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
              </section>
            )}

            {s.repuestos?.length > 0 && (
              <section className="ss-bloque">
                <h2 className="ss-bloque__titulo">Repuestos propuestos</h2>
                <ul className="ss-lista">
                  {s.repuestos.map((r) => (
                    <li key={r.productoId}>
                      {r.cantidad} × {r.descripcion} <span className="ss-meta">({r.marca} · {r.tiendaNombre})</span>
                    </li>
                  ))}
                </ul>
                <p className="ss-meta">El taller está preparando tu cotización con precios finales y mano de obra.</p>
              </section>
            )}

            {s.motivoCierre && ["CANCELADA", "RECHAZADA"].includes(s.estado) && (
              <p className="ss-aviso">{s.motivoCierre}</p>
            )}

            {cancelable && (
              <section className="ss-bloque">
                {cancelando ? (
                  <>
                    <label className="ss-bloque__titulo" htmlFor="ss-motivo">¿Por qué cancelas? (opcional)</label>
                    <textarea id="ss-motivo" className="ss-textarea" rows={3} maxLength={300}
                      value={motivo} onChange={(e) => setMotivo(e.target.value)} />
                    <div className="ss-acciones">
                      <button className="sv-btn ss-btn-peligro" onClick={cancelar} disabled={procesando}>
                        {procesando ? "Cancelando..." : "Confirmar cancelación"}
                      </button>
                      <button className="sv-btn-outline" onClick={() => setCancelando(false)} disabled={procesando}>Volver</button>
                    </div>
                  </>
                ) : (
                  <button className="sv-btn-outline" onClick={() => setCancelando(true)}>Cancelar solicitud</button>
                )}
              </section>
            )}
          </div>

          <aside className="ss-detalle__lateral">
            <h2 className="ss-bloque__titulo">Avance</h2>
            <LineaTiempo eventos={s.eventos} />
          </aside>
        </div>
      </div>
    );
  }

  return (
    <div className="sv-root">
      <div className="sv-header-top">
        <div>
          <h1 className="sv-title">Mis servicios</h1>
          <p className="sv-subtitle">Tus solicitudes de diagnóstico y el avance de cada una.</p>
        </div>
        <div className="ss-acciones">
          {onSolicitar && <button className="sv-btn" onClick={onSolicitar}>Solicitar diagnóstico</button>}
          {onVolver && <button className="sv-volver" onClick={onVolver}>← Volver</button>}
        </div>
      </div>

      {error && <p className="ss-alerta" role="alert">{error}</p>}

      {solicitudes.length === 0 ? (
        <div className="ss-vacio">
          <p>Aún no has solicitado diagnósticos.</p>
          {onSolicitar && <button className="sv-btn" onClick={onSolicitar}>Solicitar mi primer diagnóstico</button>}
        </div>
      ) : (
        <div className="sv-list">
          {solicitudes.map((s) => (
            <button key={s.id} className="ss-tarjeta" onClick={() => setSeleccionadaId(s.id)}>
              <div className="ss-tarjeta__cabecera">
                <span className="ss-eyebrow">#{refSolicitud(s.id)} · {formatFecha(s.fechaCreacion)}</span>
                <EstadoServicio estado={s.estado} />
              </div>
              <strong className="ss-tarjeta__titulo">{s.vehiculoDescripcion}</strong>
              <span className="ss-meta ss-tarjeta__sintomas">{s.sintomas}</span>
              <span className="ss-meta">
                {s.tallerNombre ? `Taller: ${s.tallerNombre}` : "Buscando taller"}
                {s.fechaRevision ? ` · Revisión: ${formatFechaHora(s.fechaRevision)}` : ""}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
