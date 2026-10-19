import { useEffect, useMemo, useState } from "react";
import { formatFechaHora, formatPrecio, refSolicitud } from "../../components/servicio/estadosServicio";
import { informesTaller } from "../../services/informesTallerService";
import { getToken } from "../../services/authService";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Indicador from "../../components/ui/Indicador";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Esqueleto from "../../components/ui/Esqueleto";
import BarraAccionMovil from "../../components/ui/BarraAccionMovil";
import Icon from "../../components/public/Icon";
import { citasDeHoy, enTaller, esperaRepuestos, porResponder, proximaRevision, tiempoRelativo, urgentes } from "./resumenTaller";
import { datosCalificacion } from "../../utils/calificacion";
import "../../css/inicio-taller.css";

// Filas de "Lo urgente primero" antes de mandar a cada sección
const MAX_URGENTES = 6;

const hora = (f) => new Date(f).toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" });

/** Saludo según la hora: el panel se abre a primera hora y al final del día. */
function saludo(ahora) {
  const h = new Date(ahora).getHours();
  return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
}

/** Inicio del taller: el resumen del día, las acciones rápidas y lo urgente primero. */
export default function TallerInicio({ taller, solicitudes, anteriores, cargando, error, onRecargar, irA }) {
  const [ahora] = useState(() => Date.now());
  const [ingresos, setIngresos] = useState(null);
  const [reintentando, setReintentando] = useState(false);

  // Mano de obra liquidada y por cobrar (el backend la da acumulada, no por periodo)
  useEffect(() => {
    let vivo = true;
    informesTaller(getToken(), 1)
      .then((d) => { if (vivo) setIngresos(d.ingresos.manoDeObra); })
      .catch(() => {});
    return () => { vivo = false; };
  }, []);

  const datos = useMemo(() => {
    const responder = solicitudes.filter(porResponder)
      .sort((a, b) => new Date(a.fechaLimiteRespuesta || 0) - new Date(b.fechaLimiteRespuesta || 0));
    return {
      responder,
      hoy: citasDeHoy(solicitudes, ahora),
      porCotizar: solicitudes.filter((s) => s.estado === "DIAGNOSTICADA"),
      esperandoCliente: solicitudes.filter((s) => s.estado === "COTIZADA" || s.estado === "APROBADA"),
      enCurso: solicitudes.filter((s) => enTaller(s) || esperaRepuestos(s)),
      urgentes: urgentes(solicitudes, ahora),
      proxima: proximaRevision(solicitudes, ahora),
    };
  }, [solicitudes, ahora]);

  const reintentar = async () => {
    setReintentando(true);
    await onRecargar?.();
    setReintentando(false);
  };

  const sinActividad = solicitudes.length === 0;
  const calificacion = datosCalificacion(taller?.rating, taller?.calificaciones);
  const primera = datos.responder[0];

  return (
    <div className="ini-root">
      <EncabezadoPagina
        titulo={taller?.nombre ? `${saludo(ahora)}, ${taller.nombre}` : "Inicio del taller"}
        descripcion={cargando ? "Revisando lo que tienes para hoy…" : resumenDelDia(datos)}
        acciones={!cargando && !sinActividad && (
          <>
            {datos.responder.length > 0 && (
              <button type="button" className="ui-btn ui-btn--primario" onClick={() => irA("taller-solicitudes", "responder")}>
                Responder solicitudes <span className="ini-contador">{datos.responder.length}</span>
              </button>
            )}
            {datos.porCotizar.length > 0 && (
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => irA("taller-solicitudes", "diagnosticadas")}>
                <Icon name="file" size={16} /> Crear cotización
              </button>
            )}
            <button type="button" className="ui-btn ui-btn--secundario" onClick={() => irA("taller-solicitudes", "agendadas")}>
              <Icon name="calendar" size={16} /> Ver agenda
            </button>
          </>
        )}
      />

      {error && (
        <Alerta className="ini-alerta" onReintentar={onRecargar ? reintentar : undefined} reintentando={reintentando}>
          No pudimos cargar tus solicitudes. {error}
        </Alerta>
      )}

      {anteriores.length > 0 && (
        <Alerta tono="aviso" className="ini-alerta">
          Tienes {anteriores.length} {anteriores.length === 1 ? "orden" : "órdenes"} del flujo anterior por atender.{" "}
          <button type="button" className="ini-enlace" onClick={() => irA("taller-anteriores")}>Ver órdenes anteriores</button>
        </Alerta>
      )}

      {cargando ? (
        <>
          <div className="ui-indicadores ini-indicadores" aria-hidden="true">
            {[0, 1, 2, 3].map((i) => <span key={i} className="ui-skeleton ini-esqueleto" />)}
          </div>
          <Esqueleto variante="lista" cantidad={3} texto="Cargando el resumen del día…" />
        </>
      ) : sinActividad && !error ? (
        <EstadoVacio icono="tool" titulo="Aún no tienes solicitudes"
          texto="Cuando un cliente pida un diagnóstico en tu zona, llega aquí y te avisamos por correo. Mientras tanto, deja listo tu perfil y tu equipo.">
          <button type="button" className="ui-btn ui-btn--primario" onClick={() => irA("taller-mitaller")}>Completar Mi taller</button>
        </EstadoVacio>
      ) : (
        <>
          <section aria-labelledby="ini-dia">
            <h2 id="ini-dia" className="ui-sr">Resumen del día</h2>
            <div className="ui-indicadores ini-indicadores">
              <Indicador icono="message" titulo="Solicitudes nuevas" valor={datos.responder.length}
                tono={datos.responder.length > 0 ? "accion" : undefined}
                pie={primera?.fechaLimiteRespuesta ? `La más urgente vence ${tiempoRelativo(primera.fechaLimiteRespuesta, ahora)}` : "Estás al día"}
                onClick={() => irA("taller-solicitudes", "responder")} />
              <Indicador icono="calendar" titulo="Citas de hoy" valor={datos.hoy.length}
                pie={datos.hoy.length > 0 ? `La primera a las ${hora(datos.hoy[0].fechaRevision)}` : "Sin revisiones agendadas hoy"}
                onClick={() => irA("taller-solicitudes", "agendadas")} />
              <Indicador icono="file" titulo="Cotizaciones pendientes" valor={datos.porCotizar.length + datos.esperandoCliente.length}
                tono={datos.porCotizar.length > 0 ? "accion" : undefined}
                pie={`${datos.porCotizar.length} por cotizar · ${datos.esperandoCliente.length} esperando al cliente`}
                onClick={() => irA(datos.porCotizar.length > 0 ? "taller-solicitudes" : "taller-cotizaciones",
                  datos.porCotizar.length > 0 ? "diagnosticadas" : undefined)} />
              <Indicador icono="tool" titulo="Servicios en curso" valor={datos.enCurso.length}
                pie={datos.enCurso.length > 0 ? "Esperando repuestos o en el taller" : "Ningún vehículo en curso"}
                onClick={() => irA("taller-trabajos", datos.enCurso.some(enTaller) ? "entaller" : "repuestos")} />
              {ingresos && (
                <Indicador titulo="Mano de obra por cobrar" valor={formatPrecio(ingresos.pendiente)}
                  tono={ingresos.pendiente > 0 ? "ok" : undefined}
                  pie={`${formatPrecio(ingresos.liquidado)} ya liquidados en total`}
                  onClick={() => irA("taller-informes")} />
              )}
              {taller && (
                <Indicador icono="star" titulo="Calificación" valor={calificacion.nuevo ? "Nuevo" : calificacion.promedio}
                  pie={calificacion.texto} onClick={() => irA("taller-informes")} />
              )}
            </div>
          </section>

          <div className="ini-columnas">
            <section className="ini-bloque" aria-labelledby="ini-urgente">
              <div className="ini-bloque__cabecera">
                <h2 id="ini-urgente" className="ini-bloque__titulo">Lo urgente primero</h2>
                {datos.urgentes.length > MAX_URGENTES && (
                  <span className="ini-bloque__total">{MAX_URGENTES} de {datos.urgentes.length}</span>
                )}
              </div>
              {datos.urgentes.length === 0 ? (
                <p className="ini-bloque__vacio">
                  <Icon name="check" size={18} /> Nada pide tu atención ahora. Las solicitudes nuevas aparecerán aquí.
                </p>
              ) : (
                <ul className="ini-urgentes">
                  {datos.urgentes.slice(0, MAX_URGENTES).map((u) => (
                    <li key={`${u.s.id}-${u.orden}`} className="ini-urgente">
                      <div className="ini-urgente__texto">
                        <strong className="ini-urgente__vehiculo">{u.s.vehiculoDescripcion || "Vehículo"}</strong>
                        <span className="ini-urgente__meta">
                          #{refSolicitud(u.s.id)}
                          {[u.s.barrio, u.s.ciudad].some(Boolean) && ` · ${[u.s.barrio, u.s.ciudad].filter(Boolean).join(", ")}`}
                          {u.orden === 1 && ` · ${hora(u.s.fechaRevision)}`}
                          {u.s.mecanicoNombre && ` · ${u.s.mecanicoNombre}`}
                        </span>
                      </div>
                      <ChipEstado tono={u.tono}>{u.estado}</ChipEstado>
                      <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico ini-urgente__accion"
                        onClick={() => irA(u.pagina, { filtro: u.filtro, abrir: u.s.id, volverA: "taller-inicio" })}
                        aria-label={`${u.accion}: ${u.s.vehiculoDescripcion || "vehículo"} #${refSolicitud(u.s.id)}`}>
                        {u.accion}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="ini-bloque" aria-labelledby="ini-agenda">
              <div className="ini-bloque__cabecera">
                <h2 id="ini-agenda" className="ini-bloque__titulo">Agenda de hoy</h2>
                <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => irA("taller-solicitudes", "agendadas")}>
                  Ver agenda
                </button>
              </div>
              {datos.hoy.length > 0 ? (
                <ol className="ini-agenda">
                  {datos.hoy.map((s) => (
                    <li key={s.id} className="ini-agenda__cita">
                      <time className="ini-agenda__hora" dateTime={s.fechaRevision}>{hora(s.fechaRevision)}</time>
                      <span className="ini-agenda__texto">
                        <strong>{s.vehiculoDescripcion}</strong>
                        <span>{s.mecanicoNombre ? `Mecánico: ${s.mecanicoNombre}` : "Sin mecánico asignado"}</span>
                      </span>
                    </li>
                  ))}
                </ol>
              ) : datos.proxima ? (
                <p className="ini-bloque__vacio">
                  Hoy no tienes citas. La próxima es el <strong>{formatFechaHora(datos.proxima.fechaRevision)}</strong>
                  {" "}({datos.proxima.vehiculoDescripcion}).
                </p>
              ) : (
                <p className="ini-bloque__vacio">No tienes revisiones agendadas. Al aceptar una solicitud eliges la fecha.</p>
              )}
            </section>
          </div>

          {primera && (
            <BarraAccionMovil etiqueta="Solicitudes por responder" valor={datos.responder.length}
              nota={primera.fechaLimiteRespuesta ? `La más urgente vence ${tiempoRelativo(primera.fechaLimiteRespuesta, ahora)}` : undefined}>
              <button type="button" className="ui-btn ui-btn--primario" onClick={() => irA("taller-solicitudes", "responder")}>
                Responder
              </button>
            </BarraAccionMovil>
          )}
        </>
      )}
    </div>
  );
}

/** Una línea con lo que importa hoy, en lenguaje del taller. */
function resumenDelDia({ responder, hoy, porCotizar }) {
  const partes = [];
  if (responder.length) partes.push(`${responder.length} ${responder.length === 1 ? "solicitud por responder" : "solicitudes por responder"}`);
  if (hoy.length) partes.push(`${hoy.length} ${hoy.length === 1 ? "cita" : "citas"} hoy`);
  if (porCotizar.length) partes.push(`${porCotizar.length} por cotizar`);
  if (!partes.length) return "Estás al día. Aquí verás lo que pida tu atención en cuanto llegue.";
  return `Hoy tienes ${partes.length > 1 ? `${partes.slice(0, -1).join(", ")} y ${partes.at(-1)}` : partes[0]}.`;
}
