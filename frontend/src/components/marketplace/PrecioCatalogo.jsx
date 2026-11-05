import { formatPrecio } from "../servicio/estadosServicio";
import { textoDescuento } from "./reglasPrecioCatalogo";
import { conIva } from "./iva";
import "../../css/precio-catalogo.css";

/**
 * Precio de un producto del catálogo, con IVA incluido (los precios de base no lo llevan). A un taller
 * aprobado le muestra su precio con el descuento de la tienda y, tachado, el público; a los demás, solo el
 * público. El lector de pantalla oye las dos cifras con su nombre.
 */
export default function PrecioCatalogo({ producto, className = "", etiqueta = "span" }) {
  const Etiqueta = etiqueta;
  if (producto.precioTaller == null) {
    return <Etiqueta className={className}><span className="ui-sr">Precio con IVA incluido: </span>{formatPrecio(conIva(producto.precio))}</Etiqueta>;
  }
  return (
    <Etiqueta className={`pc-precio ${className}`}>
      <span className="pc-precio__taller">
        <span className="ui-sr">Precio para tu taller, con IVA incluido: </span>{formatPrecio(conIva(producto.precioTaller))}
      </span>
      <s className="pc-precio__publico"><span className="ui-sr">Precio público, con IVA incluido: </span>{formatPrecio(conIva(producto.precio))}</s>
      {producto.descuentoTallerPct > 0 && <span className="pc-precio__chip">{textoDescuento(producto.descuentoTallerPct)} taller</span>}
    </Etiqueta>
  );
}
