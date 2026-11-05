import { useState } from "react";
import { RANGOS_RAPIDOS, claveDeRango, errorRango, rangoRapido } from "./reglasTabla";

/**
 * Rango de fechas de una tabla: accesos rápidos (Hoy, Últimos 7 días, Este mes, en hora de Colombia) y un rango
 * personalizado con dos fechas. valor es { desde, hasta } (AAAA-MM-DD o ""); onCambiar recibe el nuevo.
 */
export default function FiltroFechas({ id, valor, onCambiar, etiqueta = "Fechas" }) {
  const clave = claveDeRango(valor);
  const [personalizado, setPersonalizado] = useState(clave === "personalizado");
  const verCampos = personalizado || clave === "personalizado";
  const error = errorRango(valor);

  const elegir = (c) => {
    setPersonalizado(false);
    onCambiar(c ? rangoRapido(c) : { desde: "", hasta: "" });
  };

  return (
    <div className="ui-fechas" role="group" aria-label={etiqueta}>
      <div className="ui-filtros">
        <button type="button" className="ui-chip" aria-pressed={!clave && !verCampos} onClick={() => elegir("")}>Todas las fechas</button>
        {RANGOS_RAPIDOS.map((r) => (
          <button key={r.clave} type="button" className="ui-chip" aria-pressed={clave === r.clave && !verCampos}
            onClick={() => elegir(r.clave)}>{r.label}</button>
        ))}
        <button type="button" className="ui-chip" aria-pressed={verCampos} aria-expanded={verCampos}
          aria-controls={`${id}-rango`} onClick={() => setPersonalizado(true)}>Personalizado</button>
      </div>
      {verCampos && (
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
