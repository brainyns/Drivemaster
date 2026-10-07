import { useState } from "react";
import { IconoRepuesto } from "./IconosMarket";

/**
 * Foto de un producto en un marco de proporción fija (cuadrado): la imagen se ajusta sin deformarse
 * (object-fit: contain). Si el producto no tiene foto, o la foto no carga, se muestra una imagen de
 * reemplazo neutra con el ícono de un repuesto; así la fila nunca cambia de tamaño ni muestra el ícono
 * de imagen rota del navegador.
 */
export default function ImagenProducto({ src, alt = "", className = "", tamanioIcono = 32 }) {
  // Se recuerda cuál URL falló: si el producto cambia de foto, se vuelve a intentar
  const [fallo, setFallo] = useState(null);
  const sinFoto = !src || fallo === src;

  return (
    <span className={`mk-imagen${sinFoto ? " mk-imagen--vacia" : ""} ${className}`.trim()}>
      {sinFoto ? (
        <span className="mk-imagen__reemplazo" role={alt ? "img" : undefined} aria-label={alt ? `${alt} (sin foto)` : undefined}>
          <IconoRepuesto size={tamanioIcono} />
        </span>
      ) : (
        <img src={src} alt={alt} loading="lazy" onError={() => setFallo(src)} />
      )}
    </span>
  );
}
