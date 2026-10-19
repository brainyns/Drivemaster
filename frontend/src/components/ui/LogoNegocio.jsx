import { useState } from "react";
import { iniciales } from "../../pages/taller/perfilTaller";

/**
 * Logo o foto de un negocio; sin logo (o si la imagen no carga), sus iniciales sobre el naranja. Es decorativo: el
 * nombre siempre va escrito al lado. tamano: "grande" (cabecera de Mi taller), "mediano" o "chico" (listas, mapa).
 */
export default function LogoNegocio({ url, nombre, tamano = "chico", className = "" }) {
  const [rota, setRota] = useState(null);
  const conImagen = url && rota !== url;
  return (
    <span className={`ui-logo ui-logo--${tamano}${conImagen ? " ui-logo--imagen" : ""} ${className}`} aria-hidden="true">
      {conImagen
        ? <img src={url} alt="" loading="lazy" onError={() => setRota(url)} />
        : iniciales(nombre)}
    </span>
  );
}
