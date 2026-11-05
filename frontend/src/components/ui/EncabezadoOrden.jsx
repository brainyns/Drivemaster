import { ariaOrden, flechaOrden, siguienteOrden } from "./reglasTabla";

/**
 * Encabezado de una columna que se puede ordenar (en el servidor): un clic ordena ascendente, otro descendente. La
 * flecha lo muestra y aria-sort lo anuncia. campo es el nombre que acepta el sort del backend (su lista blanca).
 */
export default function EncabezadoOrden({ campo, orden, onOrdenar, className = "", children }) {
  const activo = orden?.campo === campo;
  const siguiente = siguienteOrden(orden, campo);
  return (
    <th scope="col" className={className} aria-sort={ariaOrden(orden, campo)}>
      <button type="button" className={`ui-orden${activo ? " ui-orden--activo" : ""}`} onClick={() => onOrdenar(siguiente)}
        title={`Ordenar ${siguiente.dir === "asc" ? "de menor a mayor" : "de mayor a menor"}`}>
        {children}
        <span className="ui-orden__flecha" aria-hidden="true">{flechaOrden(orden, campo)}</span>
      </button>
    </th>
  );
}
