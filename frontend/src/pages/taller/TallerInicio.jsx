import { useMemo } from "react";
import EstadoServicio from "../../components/servicio/EstadoServicio";
import { formatFechaHora, refSolicitud } from "../../components/servicio/estadosServicio";
import { enTaller, esperaRepuestos, porResponder, proximaRevision, tiempoRelativo } from "./resumenTaller";

function Tarjeta({ titulo, valor, pie, tono, onClick }) {
  return (
    <button type="button" className={`tp-tarjeta${tono ? ` tp-tarjeta--${tono}` : ""}`} onClick={onClick}>
      <span className="tp-tarjeta__titulo">{titulo}</span>
      <span className="tp-tarjeta__valor">{valor}</span>
      <span className="tp-tarjeta__pie">{pie}</span>
      <span className="tp-tarjeta__flecha" aria-hidden="true">→</span>
    </button>
  );
}

function Esqueleto() {
  return (
    <div className="tp-esqueleto" aria-hidden="true">
      <div className="tp-esqueleto__fila">
        {[0, 1, 2].map((i) => <div className="tp-esqueleto__tarjeta" key={i} />)}
      </div>
      <div className="tp-esqueleto__bloque" />
    </div>
  );
}

/** Inicio del taller: lo que pide acción hoy, con acceso directo a cada sección. */
export default function TallerInicio({ taller, solicitudes, anteriores, cargando, error, irA }) {
  const datos = useMemo(() => {
    const responder = solicitudes.filter(porResponder)
      .sort((a, b) => new Date(a.fechaLimiteRespuesta || 0) - new Date(b.fechaLimiteRespuesta || 0));
    return {
      responder,
      enTaller: solicitudes.filter(enTaller),
      repuestos: solicitudes.filter(esperaRepuestos),
      proxima: proximaRevision(solicitudes),
    };
  }, [solicitudes]);

  const sinActividad = solicitudes.length === 0;

  return (
    <>
      <header className="pt-header tp-cabecera">
        <div>
          <h1 className="pt-title">{taller?.nombre || "Panel del taller"}</h1>
          <p className="pt-subtitle">Lo que necesita tu atención hoy: solicitudes por responder, trabajos en curso y repuestos que esperas.</p>
        </div>
      </header>

      {error && <p className="pt-error" role="alert">{error}</p>}

      {anteriores.length > 0 && (
        <button type="button" className="tp-aviso" onClick={() => irA("taller-anteriores")}>
          <strong>Tienes {anteriores.length} {anteriores.length === 1 ? "orden" : "órdenes"} del flujo anterior por atender.</strong>
          <span>Ábrelas en “Órdenes anteriores”. La sección se oculta sola cuando no quede ninguna.</span>
        </button>
      )}

      {cargando ? <Esqueleto /> : sinActividad ? (
        <div className="tp-vacio">
          <h2>Aún no tienes solicitudes</h2>
          <p>
            Cuando un cliente pida un diagnóstico en tu zona, la solicitud llega aquí y te avisamos por correo.
            Mientras tanto, deja listo tu equipo para poder asignar mecánicos desde el primer día.
          </p>
          <button type="button" className="sv-btn" onClick={() => irA("taller-mitaller")}>Completar Mi taller</button>
        </div>
      ) : (
        <>
          <div className="tp-tarjetas">
            <Tarjeta
              titulo="Solicitudes por responder"
              valor={datos.responder.length}
              pie={datos.responder.length > 0
                ? `La más urgente vence ${tiempoRelativo(datos.responder[0].fechaLimiteRespuesta)}`
                : "Estás al día"}
              tono={datos.responder.length > 0 ? "accion" : undefined}
              onClick={() => irA("taller-solicitudes", "responder")}
            />
            <Tarjeta
              titulo="Trabajos en curso"
              valor={datos.enTaller.length}
              pie={datos.enTaller.length > 0 ? "Vehículos con los repuestos ya en el taller" : "Ningún vehículo en el taller ahora"}
              tono={datos.enTaller.length > 0 ? "vivo" : undefined}
              onClick={() => irA("taller-trabajos", "entaller")}
            />
            <Tarjeta
              titulo="Pendientes de repuestos"
              valor={datos.repuestos.length}
              pie={datos.repuestos.length > 0 ? "Confirma la recepción cuando lleguen" : "No esperas ningún pedido"}
              onClick={() => irA("taller-trabajos", "repuestos")}
            />
          </div>

          <div className="tp-columnas">
            <section className="tp-bloque" aria-labelledby="tp-responder-t">
              <h2 id="tp-responder-t" className="tp-bloque__titulo">Responder primero</h2>
              {datos.responder.length === 0 ? (
                <p className="tp-bloque__vacio">No hay solicitudes esperando tu respuesta.</p>
              ) : (
                <ul className="tp-lista">
                  {datos.responder.slice(0, 4).map((s) => (
                    <li key={s.id} className="tp-lista__fila">
                      <div>
                        <strong>{s.vehiculoDescripcion}</strong>
                        <span className="tp-lista__meta">
                          #{refSolicitud(s.id)} · {[s.barrio, s.ciudad].filter(Boolean).join(", ")}
                          {s.distanciaTallerKm != null && ` · ${s.distanciaTallerKm.toFixed(1)} km`}
                        </span>
                        <span className="tp-lista__meta tp-lista__meta--limite">
                          Responde antes de {formatFechaHora(s.fechaLimiteRespuesta)} ({tiempoRelativo(s.fechaLimiteRespuesta)})
                        </span>
                      </div>
                      <button type="button" className="sv-btn-outline" onClick={() => irA("taller-solicitudes", "responder")}>
                        Responder
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="tp-bloque" aria-labelledby="tp-proxima-t">
              <h2 id="tp-proxima-t" className="tp-bloque__titulo">Próxima revisión</h2>
              {datos.proxima ? (
                <div className="tp-proxima">
                  {datos.proxima.vehiculoImagenUrl && (
                    <img src={datos.proxima.vehiculoImagenUrl} alt="" loading="lazy" className="tp-proxima__foto" />
                  )}
                  <strong className="tp-proxima__cuando">{formatFechaHora(datos.proxima.fechaRevision)}</strong>
                  <span>{datos.proxima.vehiculoDescripcion}</span>
                  <span className="tp-lista__meta">
                    {datos.proxima.mecanicoNombre ? `Mecánico: ${datos.proxima.mecanicoNombre}` : "Sin mecánico asignado"}
                  </span>
                  <EstadoServicio estado={datos.proxima.estado} />
                </div>
              ) : (
                <p className="tp-bloque__vacio">No tienes revisiones agendadas. Al aceptar una solicitud eliges la fecha.</p>
              )}
            </section>
          </div>
        </>
      )}
    </>
  );
}
