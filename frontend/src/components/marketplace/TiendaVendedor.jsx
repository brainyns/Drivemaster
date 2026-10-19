import { formatPrecio } from "../servicio/estadosServicio";

/**
 * "Vendido por": la tienda de un producto como la ve el cliente en el catálogo (ciudad, dirección de retiro,
 * horario y envío). La misma en el detalle del producto y en la vista previa de Mi tienda.
 */
export default function TiendaVendedor({ tienda: t, idTitulo = "mk-tienda-titulo", className = "mk-tienda" }) {
  return (
    <section className={className} aria-labelledby={idTitulo}>
      <h2 id={idTitulo}>Vendido por {t.nombre}</h2>
      <dl className="mk-datos">
        <div><dt>Ciudad</dt><dd>{t.ciudad}{t.barrio ? ` · ${t.barrio}` : ""}</dd></div>
        <div><dt>Dirección (retiro)</dt><dd>{t.direccion}</dd></div>
        <div><dt>Horario</dt><dd>{t.horario}</dd></div>
        <div><dt>Envío</dt><dd>{t.ofreceEnvio ? `${formatPrecio(t.costoEnvio)} · solo dentro de ${t.ciudad}` : "Solo retiro en la tienda"}</dd></div>
      </dl>
    </section>
  );
}
