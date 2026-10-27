import { useState } from "react";
import { RANGOS_RAPIDOS, claveDeRango, errorRango, rangoRapido } from "./reglasTabla";

/**
 * El rango de fechas como lista desplegable (Todas, Hoy, Últimos 7 días, Este mes, Personalizado), para las barras de
 * filtros en una sola fila. Personalizado abre las dos fechas debajo. Las mismas reglas que FiltroFechas (hora de
 * Colombia); valor es { desde, hasta } y onCambiar recibe el nuevo.
 */
export default function SelectorFechas({ id, etiqueta = "Fecha", valor, onCambiar, className = "" }) {
  const clave = claveDeRango(valor);
  const [personalizado, setPersonalizado] = useState(clave === "personalizado");
  const elegida = personalizado || clave === "personalizado" ? "personalizado" : clave;
  const error = errorRango(valor);

  const elegir = (c) => {
    if (c === "personalizado") { setPersonalizado(true); return; }
    setPersonalizado(false);
    onCambiar(c ? rangoRapido(c) : { desde: "", hasta: "" });
  };

  return (
    <div className={`ui-selector-fechas ${className}`}>
      <label htmlFor={id} className="ui-selector-fechas__lista">
        {etiqueta}
        <select id={id} className="ui-field__input" value={elegida} onChange={(e) => elegir(e.target.value)}
          aria-controls={elegida === "personalizado" ? `${id}-rango` : undefined}>
          <option value="">Todas</option>
          {RANGOS_RAPIDOS.map((r) => <option key={r.clave} value={r.clave}>{r.label}</option>)}
          <option value="personalizado">Personalizado</option>
        </select>
      </label>
      {elegida === "personalizado" && (
        <div id={`${id}-rango`} className="ui-fechas__rango">
          <label htmlFor={`${id}-desde`} className="ui-fechas__campo">
            Desde
            <input id={`${id}-desde`} type="date" className="ui-field__input" value={valor.desde || ""} max={valor.hasta || undefined}
              aria-invalid={error ? true : undefined} onChange={(e) => onCambiar({ ...valor, desde: e.target.value })} />
          </label>
          <label htmlFor={`${id}-hasta`} className="ui-fechas__campo">
            Hasta
            <input id={`${id}-hasta`} type="date" className="ui-field__input" value={valor.hasta || ""} min={valor.desde || undefined}
              aria-invalid={error ? true : undefined} onChange={(e) => onCambiar({ ...valor, hasta: e.target.value })} />
          </label>
          {error && <p className="ui-fechas__error" role="alert">{error}</p>}
        </div>
      )}
    </div>
  );
}
