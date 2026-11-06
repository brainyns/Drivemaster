import { useState } from "react";
import { ESTADOS, formatFechaHora } from "./estadosServicio";

const ACTOR = {
  CLIENTE: "Cliente",
  TALLER: "Taller",
  MECANICO: "Mecánico",
  ADMIN: "DriveMaster",
  SISTEMA: "DriveMaster",
};

// El backend agrega el mecánico al final de la nota de cada etapa ("Instalación iniciada · Mecánico: Ana")
const SUFIJO_MECANICO = /\s·\sMecánico:\s(.+)$/;

/**
 * Eventos del más reciente al más antiguo, listos para mostrar. En la vista del taller no se repite su propio
 * nombre (el actor TALLER es él mismo) y el mecánico solo aparece cuando cambia respecto a la etapa anterior.
 */
function prepararEventos(eventos, vistaTaller) {
  let mecanicoAnterior = null;
  const cronologicos = [...eventos].sort((a, b) => new Date(a.fecha) - new Date(b.fecha)).map((e) => {
    let nota = e.nota || "";
    let mecanico = null;
    if (vistaTaller) {
      const m = nota.match(SUFIJO_MECANICO);
      if (m) {
        nota = nota.replace(SUFIJO_MECANICO, "");
        if (m[1] !== mecanicoAnterior) mecanico = m[1];
        mecanicoAnterior = m[1];
      }
    }
    const actor = vistaTaller && e.actorRol === "TALLER" ? null
      : e.actorNombre && e.actorRol !== "SISTEMA" ? e.actorNombre : ACTOR[e.actorRol] || e.actorRol;
    return { ...e, nota, mecanico, actor };
  });
  return cronologicos.reverse();
}

/** Junta los eventos seguidos con el mismo estado: un solo título y, debajo, cada evento con su hora, nota y quién. */
function agruparPorEstado(ordenados) {
  const grupos = [];
  for (const e of ordenados) {
    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.estado === e.estado) ultimo.eventos.push(e);
    else grupos.push({ estado: e.estado, eventos: [e] });
  }
  return grupos;
}

function DetalleEvento({ e, conHora }) {
  return (
    <>
      {conHora && <time className="ss-timeline__hora" dateTime={e.fecha}>{formatFechaHora(e.fecha)}</time>}
      {e.nota && <p className="ss-timeline__nota">{e.nota}</p>}
      {(e.actor || e.mecanico) && (
        <span className="ss-timeline__actor">
          {[e.actor, e.mecanico && `Mecánico: ${e.mecanico}`].filter(Boolean).join(" · ")}
        </span>
      )}
    </>
  );
}

/**
 * Avance de la solicitud: cada evento con su fecha, quién lo hizo y la nota. catalogo da el nombre de cada estado.
 * vistaTaller quita del historial lo que el taller ya sabe (su nombre, el mecánico repetido). Los eventos seguidos con
 * el mismo estado van bajo un solo título. resumida muestra solo el último grupo y un botón para ver el historial.
 */
export default function LineaTiempo({ eventos = [], catalogo = ESTADOS, vistaTaller = false, resumida = false }) {
  const [abierta, setAbierta] = useState(false);
  if (!eventos.length) return null;
  const ordenados = prepararEventos(eventos, vistaTaller);
  const grupos = agruparPorEstado(ordenados);
  const visibles = resumida && !abierta ? grupos.slice(0, 1) : grupos;

  return (
    <>
      <ol className="ss-timeline">
        {visibles.map((g, i) => (
          <li key={`${g.eventos[0].fecha}-${i}`} className={`ss-timeline__item${i === 0 ? " ss-timeline__item--actual" : ""}`}>
            <span className="ss-timeline__marca" aria-hidden="true" />
            <div className="ss-timeline__cuerpo">
              <div className="ss-timeline__cabecera">
                <strong>{catalogo[g.estado]?.label || g.estado}</strong>
                <time dateTime={g.eventos[0].fecha}>{formatFechaHora(g.eventos[0].fecha)}</time>
              </div>
              {g.eventos.map((e, j) => (
                <div key={`${e.fecha}-${j}`} className={j > 0 ? "ss-timeline__sub" : undefined}>
                  <DetalleEvento e={e} conHora={j > 0} />
                </div>
              ))}
            </div>
          </li>
        ))}
      </ol>
      {resumida && ordenados.length > 1 && (
        <button type="button" className="ui-btn ui-btn--texto ui-btn--chico ss-timeline__ver" aria-expanded={abierta}
          onClick={() => setAbierta((v) => !v)}>
          {abierta ? "Ocultar historial" : `Ver historial (${ordenados.length})`}
        </button>
      )}
    </>
  );
}
