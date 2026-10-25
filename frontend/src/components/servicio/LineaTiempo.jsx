import { ESTADOS, formatFechaHora } from "./estadosServicio";

const ACTOR = {
  CLIENTE: "Cliente",
  TALLER: "Taller",
  MECANICO: "Mecánico",
  ADMIN: "DriveMaster",
  SISTEMA: "DriveMaster",
};

/** Avance de la solicitud: cada evento con su fecha, quién lo hizo y la nota. catalogo da el nombre de cada estado. */
export default function LineaTiempo({ eventos = [], catalogo = ESTADOS }) {
  if (!eventos.length) return null;
  const ordenados = [...eventos].sort((a, b) => new Date(b.fecha) - new Date(a.fecha));

  return (
    <ol className="ss-timeline">
      {ordenados.map((e, i) => (
        <li key={`${e.fecha}-${i}`} className={`ss-timeline__item${i === 0 ? " ss-timeline__item--actual" : ""}`}>
          <span className="ss-timeline__marca" aria-hidden="true" />
          <div className="ss-timeline__cuerpo">
            <div className="ss-timeline__cabecera">
              <strong>{catalogo[e.estado]?.label || e.estado}</strong>
              <time>{formatFechaHora(e.fecha)}</time>
            </div>
            {e.nota && <p className="ss-timeline__nota">{e.nota}</p>}
            <span className="ss-timeline__actor">
              {e.actorNombre && e.actorRol !== "SISTEMA" ? e.actorNombre : ACTOR[e.actorRol] || e.actorRol}
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}
