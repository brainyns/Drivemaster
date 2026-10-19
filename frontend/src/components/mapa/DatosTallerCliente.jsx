import Calificacion from "../ui/Calificacion";
import Icon from "../public/Icon";

// Servicios a la vista antes de resumir el resto en "+N"
const MAX_SERVICIOS = 3;

/**
 * Lo que el cliente ve de un taller para elegirlo (TallerMapaDTO): calificación, horario y servicios. El mismo
 * bloque en la lista, en el popup del mapa y en la vista previa de Mi taller. Solo contenido de frase (span): va
 * dentro de botones. completo: el horario entero (popup); si no, su primera línea.
 */
export default function DatosTallerCliente({ taller, completo = false }) {
  const servicios = taller.especialidades || [];
  const horario = taller.horario?.trim();
  const primeraLinea = horario?.split(/\r?\n/)[0];
  return (
    <span className="mapa-taller">
      <Calificacion rating={taller.calificacion} calificaciones={taller.calificaciones} compacta />
      {horario && (
        <span className={`mapa-taller__horario${completo ? " mapa-taller__horario--completo" : ""}`}
          title={completo ? undefined : horario}>
          <Icon name="clock" size={13} />
          <span>{completo ? horario : primeraLinea}{!completo && primeraLinea !== horario ? "…" : ""}</span>
        </span>
      )}
      {servicios.length > 0 && (
        <span className="mapa-taller__servicios">
          <span className="ui-sr">Servicios: </span>
          {servicios.slice(0, MAX_SERVICIOS).map((s) => <span key={s} className="ui-badge ui-badge--neutro">{s}</span>)}
          {servicios.length > MAX_SERVICIOS && (
            <span className="ui-badge ui-badge--neutro" title={servicios.slice(MAX_SERVICIOS).join(", ")}>
              +{servicios.length - MAX_SERVICIOS}<span className="ui-sr"> más: {servicios.slice(MAX_SERVICIOS).join(", ")}</span>
            </span>
          )}
        </span>
      )}
    </span>
  );
}
