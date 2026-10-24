import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { getToken } from "../../services/authService";
import { citasDelTaller, obtenerHorario } from "../../services/horarioService";
import { ESTADOS, refSolicitud } from "../../components/servicio/estadosServicio";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import Icon from "../../components/public/Icon";
import { abreEseDia, semanaDesdeFranjas } from "./horarioTaller";
import {
  DIAS_CORTOS, citasPorDia, claveDia, desdeClave, primeroDelMes, rejillaMes, semanaDe, sumarDias, sumarMeses,
  tituloDia, tituloMes, tituloSemana,
} from "./calendario";
import "../../css/agenda-taller.css";

// Por debajo de este ancho se muestra la semana como lista en lugar del mes
const CONSULTA_CELULAR = "(max-width: 767px)";

function useEsCelular() {
  return useSyncExternalStore(
    (avisar) => {
      const mq = window.matchMedia(CONSULTA_CELULAR);
      mq.addEventListener("change", avisar);
      return () => mq.removeEventListener("change", avisar);
    },
    () => window.matchMedia(CONSULTA_CELULAR).matches,
    () => false,
  );
}

const hora = (f) => new Date(f).toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" });

/** Una cita del día: hora, vehículo, placa, barrio y cliente, con el enlace a la solicitud. */
function Cita({ c, onAbrir }) {
  return (
    <li className="ag-cita">
      <time className="ag-cita__hora" dateTime={c.fechaRevision}>{hora(c.fechaRevision)}</time>
      <div className="ag-cita__texto">
        <strong className="ag-cita__vehiculo">
          {c.vehiculoDescripcion || "Vehículo"}
          {c.vehiculoPlaca && <span className="ui-placa ui-placa--chica">{c.vehiculoPlaca}</span>}
        </strong>
        <span className="ag-cita__meta">
          {[c.barrio, c.clienteNombre].filter(Boolean).join(" · ") || "Sin datos del cliente"}
        </span>
        <span className="ag-cita__pie">
          <ChipEstado estado={c.estado} catalogo={ESTADOS} />
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => onAbrir(c)}
            aria-label={`Ver la solicitud #${refSolicitud(c.id)} de ${c.vehiculoDescripcion || "vehículo"}`}>
            Ver solicitud <Icon name="arrow" size={14} />
          </button>
        </span>
      </div>
    </li>
  );
}

/**
 * Agenda del taller: el mes con las citas de revisión de cada día (en celular, la semana como lista). Hoy va resaltado,
 * los días que el taller cierra según su horario van en gris y al tocar un día se listan sus citas.
 */
export default function TallerAgenda({ taller, irA }) {
  const token = getToken();
  const tallerId = taller?.id;
  const esCelular = useEsCelular();
  const [hoy] = useState(() => new Date());
  const [ancla, setAncla] = useState(() => new Date()); // un día del mes (o de la semana) que se está viendo
  const [seleccionado, setSeleccionado] = useState(() => claveDia(new Date()));
  const [citas, setCitas] = useState([]);
  // undefined mientras carga; null si el taller aún no configura su horario por días
  const [semanaHorario, setSemanaHorario] = useState(undefined);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const dias = useMemo(() => (esCelular ? semanaDe(ancla) : rejillaMes(ancla).flat()), [esCelular, ancla]);
  const desde = claveDia(dias[0]);
  const hasta = claveDia(dias.at(-1));

  const cargar = useCallback(async () => {
    if (!tallerId) return;
    try {
      setCitas((await citasDelTaller(token, tallerId, desde, hasta)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, tallerId, desde, hasta]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  // Sin horario configurado (o si falla), ningún día se marca cerrado
  useEffect(() => {
    if (!tallerId) return undefined;
    let vivo = true;
    obtenerHorario(token, tallerId)
      .then((filas) => { if (vivo) setSemanaHorario(semanaDesdeFranjas(filas)); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [token, tallerId]);

  const porDia = useMemo(() => citasPorDia(citas), [citas]);
  const claveHoy = claveDia(hoy);

  const mover = (paso) => {
    const nueva = esCelular ? sumarDias(ancla, 7 * paso) : sumarMeses(ancla, paso);
    setAncla(nueva);
    setSeleccionado(claveDia(esCelular ? semanaDe(nueva)[0] : primeroDelMes(nueva)));
  };
  const irAHoy = () => { setAncla(new Date()); setSeleccionado(claveHoy); };

  const abrir = (c) => irA("taller-solicitudes", { filtro: "agendadas", abrir: c.id, volverA: "taller-agenda" });

  const titulo = esCelular ? tituloSemana(dias) : tituloMes(ancla);
  const delDia = porDia[seleccionado] || [];
  const fechaSel = desdeClave(seleccionado);
  const cerradoSel = !abreEseDia(semanaHorario, fechaSel);

  const navegacion = (
    <div className="ag-nav">
      <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico ag-nav__flecha" onClick={() => mover(-1)}
        aria-label={esCelular ? "Semana anterior" : "Mes anterior"}>
        <Icon name="chevron" size={18} className="ag-nav__izq" />
      </button>
      <h2 className="ag-nav__titulo" aria-live="polite">{titulo}</h2>
      <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico ag-nav__flecha" onClick={() => mover(1)}
        aria-label={esCelular ? "Semana siguiente" : "Mes siguiente"}>
        <Icon name="chevron" size={18} className="ag-nav__der" />
      </button>
      <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={irAHoy}>Hoy</button>
    </div>
  );

  if (!taller) {
    return (
      <div className="ag-root">
        <EncabezadoPagina titulo="Agenda" descripcion="Tus citas de revisión por día." />
        <Esqueleto variante="lista" cantidad={3} texto="Cargando la agenda…" />
      </div>
    );
  }

  return (
    <div className="ag-root">
      <EncabezadoPagina titulo="Agenda" onVolver={() => irA("taller-inicio")} textoVolver="Inicio"
        descripcion="Tus citas de revisión por día. Los días en gris tu taller está cerrado según tu horario." />

      {error && <Alerta className="ag-alerta" onReintentar={cargar} onCerrar={() => setError(null)}>{error}</Alerta>}
      {semanaHorario === null && (
        <Alerta tono="info" className="ag-alerta">
          Aún no configuras tu horario por días: todos los días se ven abiertos.{" "}
          <button type="button" className="ag-enlace" onClick={() => irA("taller-mitaller")}>Configurarlo en Mi taller</button>
        </Alerta>
      )}

      {navegacion}

      {esCelular ? (
        <ol className="ag-semana" aria-busy={cargando}>
          {dias.map((d) => {
            const clave = claveDia(d);
            const lista = porDia[clave] || [];
            const cerrado = !abreEseDia(semanaHorario, d);
            return (
              <li key={clave} className={`ag-semana__dia${clave === claveHoy ? " ag-semana__dia--hoy" : ""}${cerrado ? " ag-semana__dia--cerrado" : ""}`}>
                <h3 className="ag-semana__titulo">
                  {tituloDia(d)}
                  {clave === claveHoy && <span className="ag-hoy">Hoy</span>}
                  {cerrado && <span className="ag-semana__nota">Cerrado</span>}
                </h3>
                {lista.length > 0
                  ? <ul className="ag-citas">{lista.map((c) => <Cita key={c.id} c={c} onAbrir={abrir} />)}</ul>
                  : <p className="ag-vacio">Sin citas.</p>}
              </li>
            );
          })}
        </ol>
      ) : (
        <div className="ag-cuerpo">
          <div className="ag-mes" aria-busy={cargando}>
            <div className="ag-mes__fila ag-mes__cabecera" aria-hidden="true">
              {DIAS_CORTOS.map((n) => <span key={n} className="ag-mes__dia-semana">{n}</span>)}
            </div>
            {rejillaMes(ancla).map((semana) => (
              <div key={claveDia(semana[0])} className="ag-mes__fila">
                {semana.map((d) => {
                  const clave = claveDia(d);
                  const n = (porDia[clave] || []).length;
                  const clases = ["ag-dia"];
                  if (d.getMonth() !== ancla.getMonth()) clases.push("ag-dia--fuera");
                  if (!abreEseDia(semanaHorario, d)) clases.push("ag-dia--cerrado");
                  if (clave === claveHoy) clases.push("ag-dia--hoy");
                  return (
                    <button key={clave} type="button" className={clases.join(" ")}
                      aria-pressed={clave === seleccionado} aria-controls="ag-panel" onClick={() => setSeleccionado(clave)}
                      aria-label={`${tituloDia(d)}${clave === claveHoy ? ", hoy" : ""}: ${n === 0 ? "sin citas" : n === 1 ? "1 cita" : `${n} citas`}${!abreEseDia(semanaHorario, d) ? ", cerrado" : ""}`}>
                      <span className="ag-dia__numero">{d.getDate()}</span>
                      {n > 0 && <span className="ag-dia__citas" aria-hidden="true">{n}</span>}
                    </button>
                  );
                })}
              </div>
            ))}
          </div>

          <aside id="ag-panel" className="ag-panel" aria-labelledby="ag-panel-titulo">
            <h2 id="ag-panel-titulo" className="ag-panel__titulo">
              {tituloDia(fechaSel)}
              {seleccionado === claveHoy && <span className="ag-hoy">Hoy</span>}
            </h2>
            {cerradoSel && <p className="ag-panel__nota">Tu taller está cerrado este día.</p>}
            {delDia.length > 0 ? (
              <ul className="ag-citas">{delDia.map((c) => <Cita key={c.id} c={c} onAbrir={abrir} />)}</ul>
            ) : (
              <p className="ag-vacio">{cargando ? "Cargando citas…" : "Sin citas este día."}</p>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}
