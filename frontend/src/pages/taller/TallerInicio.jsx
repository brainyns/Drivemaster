import { useEffect, useMemo, useState } from "react";
import { formatFechaHora, formatPrecio, refSolicitud } from "../../components/servicio/estadosServicio";
import { informesTaller } from "../../services/informesTallerService";
import { obtenerHorario } from "../../services/horarioService";
import { getToken } from "../../services/authService";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Indicador from "../../components/ui/Indicador";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Esqueleto from "../../components/ui/Esqueleto";
import BarraAccionMovil from "../../components/ui/BarraAccionMovil";
import Icon from "../../components/public/Icon";
import Grafico from "../../components/ui/Grafico";
import { etiquetaSemana, pesosCorto } from "../../components/ui/escalaGrafico";
import { citasDeHoy, enTaller, esperaRepuestos, porResponder, proximasCitas, tiempoRelativo, urgentes } from "./resumenTaller";
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
  // Lo liquidado en las últimas semanas (serieSemanal de los informes)
  const [semanas, setSemanas] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  // null hasta saberlo; true si el taller aún no guarda su horario por días
  const [sinHorario, setSinHorario] = useState(null);
  const tallerId = taller?.id;
  const aprobado = taller?.estadoAprobacion === "APROBADO";

  // Mano de obra liquidada y por cobrar del mes en curso (informes de 1 mes: desde el día 1)
  useEffect(() => {
    let vivo = true;
    informesTaller(getToken(), 1)
      .then((d) => { if (vivo) { setIngresos(d.ingresos.manoDeObra); setSemanas(d.serieSemanal || []); } })
      .catch(() => {});
    return () => { vivo = false; };
  }, []);

  // Solo un taller aprobado puede guardar el horario; si la consulta falla, no se avisa nada
  useEffect(() => {
    if (!tallerId || !aprobado) return undefined;
    let vivo = true;
    obtenerHorario(getToken(), tallerId)
      .then((filas) => { if (vivo) setSinHorario(!filas?.length); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [tallerId, aprobado]);

  const datos = useMemo(() => {
    const responder = solicitudes.filter(porResponder)
      .sort((a, b) => new Date(a.fechaLimiteRespuesta || 0) - new Date(b.fechaLimiteRespuesta || 0));
    return {
      responder,
      hoy: citasDeHoy(solicitudes, ahora),
      porCotizar: solicitudes.filter((s) => s.estado === "DIAGNOSTICADA"),
      esperandoCliente: solicitudes.filter((s) => s.estado === "COTIZADA" || s.estado === "APROBADA"),
      // Lo listo para entregar tiene su propia tarjeta: en curso es lo que todavía se trabaja
      enCurso: solicitudes.filter((s) => (enTaller(s) || esperaRepuestos(s)) && s.estado !== "LISTA"),
      listos: solicitudes.filter((s) => s.estado === "LISTA"),
      urgentes: urgentes(solicitudes, ahora),
      proximas: proximasCitas(solicitudes, ahora),
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
  const mesActual = new Date(ahora).toLocaleDateString("es-CO", { month: "long" });

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
            <button type="button" className="ui-btn ui-btn--secundario" onClick={() => irA("taller-agenda")}>
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

      {sinHorario && (
        <div className="ini-aviso-horario">
          <Icon name="clock" size={20} />
          <p className="ini-aviso-horario__texto">
            Configura tu horario de atención para que tus citas se agenden en horas que sí puedes cumplir
          </p>
          <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => irA("taller-mitaller", "horario")}>
            Configurar
          </button>
        </div>
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
              <Indicador icono="message" titulo="Solicitudes nuevas" valor={datos.responder.length} fase="nueva"
                tono={datos.responder.length > 0 ? "accion" : undefined}
                pie={primera?.fechaLimiteRespuesta ? `La más urgente vence ${tiempoRelativo(primera.fechaLimiteRespuesta, ahora)}` : "Estás al día"}
                onClick={() => irA("taller-solicitudes", "responder")} />
              <Indicador icono="calendar" titulo="Citas de hoy" valor={datos.hoy.length} fase="nueva"
                pie={datos.hoy.length > 0 ? `La primera a las ${hora(datos.hoy[0].fechaRevision)}` : "Sin revisiones agendadas hoy"}
                onClick={() => irA("taller-agenda")} />
              <Indicador icono="file" titulo="Cotizaciones pendientes" valor={datos.porCotizar.length + datos.esperandoCliente.length}
                fase="cotizacion" tono={datos.porCotizar.length > 0 ? "accion" : undefined}
                pie={`${datos.porCotizar.length} por cotizar · ${datos.esperandoCliente.length} esperando al cliente`}
                onClick={() => irA(datos.porCotizar.length > 0 ? "taller-solicitudes" : "taller-cotizaciones",
                  datos.porCotizar.length > 0 ? "diagnosticadas" : undefined)} />
              <Indicador icono="tool" titulo="Servicios en curso" valor={datos.enCurso.length} fase="curso"
                pie={datos.enCurso.length > 0 ? "Esperando repuestos o en el taller" : "Ningún vehículo en curso"}
                onClick={() => irA("taller-trabajos", datos.enCurso.some(enTaller) ? "entaller" : "repuestos")} />
              <Indicador icono="check" titulo="Listos para entregar" valor={datos.listos.length} fase="lista"
                pie={datos.listos.length > 0 ? "El cliente ya fue avisado por correo" : "Ningún vehículo esperando entrega"}
                onClick={() => irA("taller-trabajos", "entaller")} />
              {ingresos && (
                <Indicador titulo={`Mano de obra · ${mesActual}`} pie="Del mes en curso; los meses anteriores están en Informes"
                  onClick={() => irA("taller-informes")}>
                  {/* Dentro de un botón solo cabe contenido en línea: spans en vez de dl */}
                  <span className="ini-dinero">
                    <span className="ini-dinero__fila ini-dinero__pendiente">
                      <span className="ini-dinero__etiqueta">Pendiente de cobro</span>
                      <span className="ini-dinero__valor">{formatPrecio(ingresos.pendiente)}</span>
                    </span>
                    <span className="ini-dinero__fila ini-dinero__recibido">
                      <span className="ini-dinero__etiqueta">Recibido este mes</span>
                      <span className="ini-dinero__valor">{formatPrecio(ingresos.liquidado)}</span>
                    </span>
                  </span>
                </Indicador>
              )}
              {taller && (
                <Indicador icono="star" titulo="Calificación" valor={calificacion.nuevo ? "Nuevo" : calificacion.promedio}
                  pie={calificacion.texto} onClick={() => irA("taller-informes")} />
              )}
            </div>
          </section>

          <div className="ini-columnas">
            {/* Columna izquierda: lo urgente y, debajo, los ingresos de las últimas semanas (llena el espacio junto a la agenda) */}
            <div className="ini-columna">
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

              {semanas?.length > 0 && (
                <section className="ini-bloque" aria-labelledby="ini-semanas">
                  <div className="ini-bloque__cabecera">
                    <h2 id="ini-semanas" className="ini-bloque__titulo">Ingresos de las últimas {semanas.length} semanas</h2>
                    <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => irA("taller-informes")}>
                      Ver informes
                    </button>
                  </div>
                  <Grafico titulo={`Ingresos liquidados por semana, últimas ${semanas.length} semanas`}
                    etiquetas={semanas.map((s) => etiquetaSemana(s.inicio))}
                    detalles={semanas.map((s) => `Semana del ${etiquetaSemana(s.inicio)}`)}
                    series={[{ nombre: "Ingresos liquidados", valores: semanas.map((s) => s.ingresosLiquidados) }]}
                    formato={formatPrecio} formatoEje={pesosCorto} alto={120} />
                  <p className="ini-bloque__nota">Mano de obra y diagnóstico ya pagados, por la semana en que se te pagaron.</p>
                </section>
              )}
            </div>

            <section className="ini-bloque" aria-labelledby="ini-agenda">
              <div className="ini-bloque__cabecera">
                <h2 id="ini-agenda" className="ini-bloque__titulo">Agenda de hoy</h2>
                <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => irA("taller-agenda")}>
                  Ver agenda
                </button>
              </div>
              <h3 className="ini-agenda__grupo">Citas de hoy</h3>
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
              ) : (
                <p className="ini-bloque__vacio">Hoy no tienes revisiones agendadas.</p>
              )}

              <h3 className="ini-agenda__grupo">Próximas citas</h3>
              {datos.proximas.length > 0 ? (
                <ol className="ini-agenda">
                  {datos.proximas.map((s) => (
                    <li key={s.id} className="ini-agenda__cita ini-agenda__cita--proxima">
                      <time className="ini-agenda__hora" dateTime={s.fechaRevision}>{formatFechaHora(s.fechaRevision)}</time>
                      <span className="ini-agenda__texto">
                        <strong>{s.vehiculoDescripcion}</strong>
                        <span>{s.mecanicoNombre ? `Mecánico: ${s.mecanicoNombre}` : "Sin mecánico asignado"}</span>
                      </span>
                    </li>
                  ))}
                </ol>
              ) : (
                <p className="ini-bloque__vacio">Nada agendado para los próximos días. Al aceptar una solicitud eliges la fecha.</p>
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
