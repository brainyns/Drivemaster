import Icon from "../public/Icon";
import BarraProgreso from "./BarraProgreso";

/**
 * Cabecera de un perfil de negocio (Mi taller, Mi tienda): logo, nombre, chips de estado y datos breves a la
 * izquierda; a la derecha "Perfil completo al X %" con lo que falta. Cada faltante con `seccion` es un botón que
 * llama a onResolver(item); los que solo cambia el administrador (admin) se nombran sin acción. aviso va debajo.
 */
export default function CabeceraNegocio({
  logo, nombre, chips, meta, porcentaje, faltan = [], onResolver, textoListo, aviso,
}) {
  return (
    <header className="ui-negocio">
      <div className="ui-negocio__marca">
        {logo}
        <div className="ui-negocio__texto">
          <h1 className="ui-negocio__nombre">{nombre}</h1>
          {chips && <div className="ui-negocio__chips">{chips}</div>}
          {meta && <p className="ui-negocio__meta">{meta}</p>}
        </div>
      </div>

      <div className="ui-negocio__completo">
        <p className="ui-negocio__completo-titulo">Perfil completo al <strong>{porcentaje} %</strong></p>
        <BarraProgreso valor={porcentaje} etiqueta="Perfil completo" tono={porcentaje === 100 ? "ok" : "acento"} />
        {faltan.length === 0 ? (
          <p className="ui-negocio__listo">{textoListo}</p>
        ) : (
          <ul className="ui-negocio__faltan" aria-label="Lo que falta">
            {faltan.map((f) => (
              <li key={f.clave}>
                {f.seccion ? (
                  <button type="button" className="ui-negocio__accion" onClick={() => onResolver?.(f)}>
                    {f.texto} <Icon name="arrow" size={14} />
                  </button>
                ) : (
                  <span className="ui-negocio__admin">{f.texto}: lo registra el administrador</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      {aviso && <div className="ui-negocio__aviso">{aviso}</div>}
    </header>
  );
}
