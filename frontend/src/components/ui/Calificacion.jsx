import Estrellas from "./Estrellas";
import ChipEstado from "./ChipEstado";
import { datosCalificacion } from "../../utils/calificacion";

/**
 * Calificación de un taller: estrellas, promedio y cuántas calificaciones; sin ninguna, el chip "Nuevo". La
 * misma en Mi taller, el inicio del taller, la lista y el mapa del cliente y la administración.
 */
export default function Calificacion({ rating, calificaciones, compacta = false }) {
  const d = datosCalificacion(rating, calificaciones);
  if (d.nuevo) {
    return (
      <span className="ui-calificacion">
        <ChipEstado tono="acento">Nuevo</ChipEstado>
        {!compacta && <span className="ui-calificacion__texto">{d.texto}</span>}
      </span>
    );
  }
  return (
    <span className="ui-calificacion" title={d.etiqueta}>
      <Estrellas valor={Number(rating)} size={compacta ? 13 : 16} />
      <strong className="ui-calificacion__promedio">{d.promedio}</strong>
      <span className="ui-calificacion__texto">({compacta ? calificaciones : d.texto})</span>
    </span>
  );
}
