import { useState } from "react";
import Icon from "../public/Icon";
import { datosVehiculo } from "../../utils/vehiculo";

/**
 * Tarjeta de un vehículo, la misma en Mis vehículos y en Solicitar diagnóstico: foto o imagen de respaldo (el
 * dibujo del carro y la marca), nombre, placa y datos con su etiqueta. Con `seleccion` es un radio nativo
 * (flechas del teclado): { name, elegido, onElegir, describedBy }. children va al final (acciones, mantenimiento);
 * accionMedia, sobre la imagen (p. ej. cambiar la foto).
 */
export default function TarjetaVehiculo({ vehiculo: v, seleccion, accionMedia, nivelTitulo = 3, children }) {
  const [fotoRota, setFotoRota] = useState(false);
  const datos = datosVehiculo(v);
  // Dentro del <label> solo va contenido de frase (span); en el <article> el cuerpo puede llevar botones y formularios
  const Titulo = seleccion ? "span" : `h${nivelTitulo}`;
  const Raiz = seleccion ? "label" : "article";
  const Bloque = seleccion ? "span" : "div";

  return (
    <Raiz className={`ui-vehiculo${seleccion?.elegido ? " ui-vehiculo--elegido" : ""}`}>
      {seleccion && (
        <input
          type="radio"
          name={seleccion.name}
          id={`${seleccion.name}-${v.id}`}
          className="ui-sr"
          value={v.id}
          checked={seleccion.elegido}
          onChange={() => seleccion.onElegir(v.id)}
          aria-describedby={seleccion.describedBy}
        />
      )}
      <span className="ui-vehiculo__media">
        {v.imagenUrl && !fotoRota
          ? <img src={v.imagenUrl} alt="" loading="lazy" onError={() => setFotoRota(true)} />
          : (
            <span className="ui-vehiculo__respaldo" aria-hidden="true">
              <Icon name="car" size={34} />
              <span>{v.marca}</span>
            </span>
          )}
        {seleccion && <span className="ui-vehiculo__check" aria-hidden="true"><Icon name="check" size={14} /></span>}
        {accionMedia && <span className="ui-vehiculo__media-accion">{accionMedia}</span>}
      </span>
      <Bloque className="ui-vehiculo__cuerpo">
        <Titulo className="ui-vehiculo__nombre">{v.marca} {v.modelo} <span className="ui-vehiculo__anio">{v.anio}</span></Titulo>
        {v.placa && <span className="ui-placa"><span className="ui-sr">Placa </span>{v.placa}</span>}
        {datos.length > 0 && <span className="ui-vehiculo__datos">{datos.join(" · ")}</span>}
        {children && <Bloque className="ui-vehiculo__extra">{children}</Bloque>}
      </Bloque>
    </Raiz>
  );
}
