import Interruptor from "../ui/Interruptor";
import Icon from "../public/Icon";
import { DIAS, MAX_FRANJAS, OPCIONES_HORA, errorDia, franjaSiguiente, textoDia } from "../../pages/taller/horarioTaller";

const NOMBRE = Object.fromEntries(DIAS.map((d) => [d.clave, d.nombre]));

/** Selector de hora cada 30 minutos en formato 12 h. */
function SelectorHora({ id, etiqueta, valor, onCambiar, invalido }) {
  return (
    <select id={id} className="ui-field__input mt-horario__hora" aria-label={etiqueta} aria-invalid={invalido || undefined}
      value={valor} onChange={(e) => onCambiar(e.target.value)}>
      {OPCIONES_HORA.map((o) => <option key={o.valor} value={o.valor}>{o.texto}</option>)}
    </select>
  );
}

/**
 * Editor del horario de la semana: una fila por día (en celular, una tarjeta) con el interruptor Abierto/Cerrado y sus
 * franjas. Valida en vivo y muestra el problema bajo la fila. semana: [{ dia, abierto, franjas: [{ apertura, cierre }] }].
 */
export default function HorarioSemanal({ semana, onCambiar }) {
  const cambiarDia = (clave, cambio) => onCambiar(semana.map((d) => (d.dia === clave ? { ...d, ...cambio } : d)));

  return (
    <ul className="mt-horario">
      {semana.map((d) => {
        const nombre = NOMBRE[d.dia];
        const error = errorDia(d);
        const idError = `mt-horario-error-${d.dia}`;
        return (
          <li key={d.dia} className={`mt-horario__dia${d.abierto ? "" : " mt-horario__dia--cerrado"}`}
            aria-describedby={error ? idError : undefined}>
            <div className="mt-horario__cabecera">
              <span className="mt-horario__nombre">{nombre}</span>
              <Interruptor activo={d.abierto} etiqueta={`${nombre} abierto`} textos={["Abierto", "Cerrado"]}
                onCambiar={(abierto) => cambiarDia(d.dia, {
                  abierto, franjas: abierto && !d.franjas.length ? [franjaSiguiente(d)] : d.franjas,
                })} />
            </div>

            {d.abierto ? (
              <div className="mt-horario__franjas">
                {d.franjas.map((f, i) => {
                  const cambiarFranja = (cambio) => cambiarDia(d.dia, {
                    franjas: d.franjas.map((x, j) => (j === i ? { ...x, ...cambio } : x)),
                  });
                  const sufijo = d.franjas.length > 1 ? `, franja ${i + 1}` : "";
                  return (
                    <div key={i} className="mt-horario__franja">
                      <SelectorHora id={`mt-h-${d.dia}-${i}-a`} etiqueta={`${nombre}${sufijo}: apertura`} valor={f.apertura}
                        invalido={!!error} onCambiar={(apertura) => cambiarFranja({ apertura })} />
                      <span className="mt-horario__a" aria-hidden="true">a</span>
                      <SelectorHora id={`mt-h-${d.dia}-${i}-c`} etiqueta={`${nombre}${sufijo}: cierre`} valor={f.cierre}
                        invalido={!!error} onCambiar={(cierre) => cambiarFranja({ cierre })} />
                      {d.franjas.length > 1 && (
                        <button type="button" className="ui-btn ui-btn--texto ui-btn--chico mt-horario__quitar"
                          aria-label={`Quitar la franja ${i + 1} del ${nombre.toLowerCase()}`}
                          onClick={() => cambiarDia(d.dia, { franjas: d.franjas.filter((_, j) => j !== i) })}>
                          <Icon name="close" size={16} />
                        </button>
                      )}
                    </div>
                  );
                })}
                {d.franjas.length < MAX_FRANJAS && (
                  <button type="button" className="ui-btn ui-btn--texto ui-btn--chico mt-horario__agregar"
                    onClick={() => cambiarDia(d.dia, { franjas: [...d.franjas, franjaSiguiente(d)] })}>
                    <Icon name="plus" size={16} /> Agregar franja
                  </button>
                )}
              </div>
            ) : (
              <span className="mt-horario__cerrado">Cerrado</span>
            )}

            {error && <p id={idError} className="mt-horario__error" role="alert">{error}</p>}
          </li>
        );
      })}
    </ul>
  );
}

/** El horario guardado, para leer: un día por línea. */
export function HorarioResumen({ semana }) {
  return (
    <dl className="mt-horario-resumen">
      {semana.map((d) => (
        <div key={d.dia} className={`mt-horario-resumen__dia${d.abierto ? "" : " mt-horario-resumen__dia--cerrado"}`}>
          <dt>{NOMBRE[d.dia]}</dt>
          <dd>{textoDia(d)}</dd>
        </div>
      ))}
    </dl>
  );
}
