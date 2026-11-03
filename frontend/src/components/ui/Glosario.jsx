import InfoAyuda from "./InfoAyuda";
import { terminos } from "../../utils/glosario";
import "../../css/info-ayuda.css";

/**
 * Fila "¿Qué significa?" con los términos de dinero de la pantalla: cada uno con su "?" (InfoAyuda) que dice la frase
 * del glosario. Va bajo las cifras para que nadie tenga que adivinar.
 */
export default function Glosario({ claves, className = "" }) {
  const items = terminos(...claves);
  if (items.length === 0) return null;
  return (
    <p className={`ia-glosario ${className}`}>
      <span className="ia-glosario__titulo">¿Qué significa?</span>
      {items.map((t) => (
        <span key={t.termino} className="ia-glosario__item">
          {t.termino}
          <InfoAyuda texto={t.texto} etiqueta={`Qué significa ${t.termino}`} />
        </span>
      ))}
    </p>
  );
}
