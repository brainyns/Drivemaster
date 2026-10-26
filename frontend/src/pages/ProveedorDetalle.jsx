import { useCallback, useEffect, useMemo, useState } from "react";
import { obtenerProveedor } from "../services/proveedorService";
import { listarCompras } from "../services/compraService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import ChipEstado from "../components/ui/ChipEstado";
import Dato from "../components/ui/Dato";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import LogoNegocio from "../components/ui/LogoNegocio";
import Icon from "../components/public/Icon";
import { ESTADOS_COMPRA, comprasDeProveedor, estadoCompra, refCompra, unidades } from "./tienda/comprasTienda";
import { recibePdf, textoCondicionPago } from "./tienda/proveedoresTienda";
import { formatoFecha, plural } from "../utils/formato";
import "../css/proveedores.css";
import "../css/compras-tienda.css";

/**
 * Un proveedor con sus datos y todo lo que se le ha comprado: historial (fecha, referencia, factura, productos, estado y
 * total), total comprado sin las canceladas y la última compra. Los saldos por pagar quedan para una fase posterior.
 */
export default function ProveedorDetalle({ id, onVolver, onEditar, onCompra, token }) {
  const [proveedor, setProveedor] = useState(null);
  const [compras, setCompras] = useState(null);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);

  const cargar = useCallback(async () => {
    try {
      const [p, c] = await Promise.all([obtenerProveedor(id, token), listarCompras(token)]);
      setProveedor(p);
      setCompras(c || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [id, token]);

  useEffect(() => {
    const t = setTimeout(cargar, 0);
    return () => clearTimeout(t);
  }, [cargar]);

  const datos = useMemo(() => comprasDeProveedor(compras || [], id), [compras, id]);

  if (!proveedor) {
    return (
      <div className="ss-panel--tienda prv-root">
        <EncabezadoPagina titulo="Proveedor" onVolver={onVolver} textoVolver="Proveedores" />
        {error ? (
          <Alerta reintentando={reintentando} onReintentar={async () => { setReintentando(true); await cargar(); setReintentando(false); }}>
            No pudimos cargar el proveedor. {error}
          </Alerta>
        ) : <Esqueleto variante="lista" cantidad={3} texto="Cargando el proveedor…" />}
      </div>
    );
  }

  return (
    <div className="ss-panel--tienda prv-root">
      <EncabezadoPagina titulo={proveedor.nombre} onVolver={onVolver} textoVolver="Proveedores"
        descripcion={proveedor.contacto ? `Contacto: ${proveedor.contacto}` : "Sus datos y todo lo que le has comprado."}
        acciones={(
          <button type="button" className="ui-btn ui-btn--secundario" onClick={() => onEditar(proveedor.id)}>
            <Icon name="pencil" size={16} /> Editar
          </button>
        )} />

      <section aria-labelledby="prv-detalle-resumen" className="prv-alerta">
        <h2 id="prv-detalle-resumen" className="ui-sr">Resumen de compras a este proveedor</h2>
        <div className="ui-indicadores">
          <Indicador icono="box" titulo="Total comprado" valor={formatPrecio(datos.total)} pie="Sin las compras canceladas" />
          <Indicador titulo="Compras" valor={datos.compras.length}
            pie={datos.pendientes > 0 ? `${plural(datos.pendientes, "pendiente")} de recibir` : "Ninguna pendiente"} />
          <Indicador icono="calendar" titulo="Última compra" valor={datos.ultima ? formatoFecha(datos.ultima) : "—"}
            pie={datos.ultima ? null : "Aún no le compras"} />
        </div>
      </section>

      <div className="prv-detalle">
        <section className="ui-card ui-table-card" aria-labelledby="prv-historial-titulo">
          <div className="ui-card__header">
            <h2 id="prv-historial-titulo" className="ui-card__titulo">Historial de compras</h2>
            <span className="ui-card__sub">De la más reciente a la más antigua</span>
          </div>
          {datos.compras.length === 0 ? (
            <div className="ui-card__cuerpo">
              <EstadoVacio icono="box" nivel={3} compacto titulo="Aún no le has comprado"
                texto="Cuando registres una compra a este proveedor, aparece aquí con su total." />
            </div>
          ) : (
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--tarjetas">
                <thead>
                  <tr>
                    <th scope="col">Fecha</th>
                    <th scope="col">Compra</th>
                    <th scope="col">Factura</th>
                    <th scope="col" className="ui-table__num">Productos</th>
                    <th scope="col">Estado</th>
                    <th scope="col" className="ui-table__num">Total</th>
                    <th scope="col"><span className="ui-sr">Acciones</span></th>
                  </tr>
                </thead>
                <tbody>
                  {datos.compras.map((c) => (
                    <tr key={c.id}>
                      <td data-label="Fecha">{formatoFecha(c.fecha)}</td>
                      <td data-label="Compra" className="ui-table__titulo"><strong className="cmp-ref">{refCompra(c.id)}</strong></td>
                      <td data-label="Factura">{c.numeroFactura || <span className="prv-tenue">Sin factura</span>}</td>
                      <td data-label="Productos" className="ui-table__num">
                        {plural((c.productos || []).length, "producto")} · {plural(unidades(c), "unidad", "unidades")}
                      </td>
                      <td data-label="Estado"><ChipEstado estado={estadoCompra(c)} catalogo={ESTADOS_COMPRA} /></td>
                      <td data-label="Total" className="ui-table__num"><span className="ui-precio">{formatPrecio(c.total)}</span></td>
                      <td className="ui-table__acciones">
                        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onCompra(c.id)}
                          aria-label={`Ver la compra ${refCompra(c.id)}`}>
                          Ver compra
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <aside className="ui-card prv-detalle__datos" aria-labelledby="prv-datos-titulo">
          <div className="ui-card__header">
            <LogoNegocio nombre={proveedor.nombre} />
            <h2 id="prv-datos-titulo" className="ui-card__titulo">Datos</h2>
          </div>
          <div className="ui-card__cuerpo">
            <dl className="ui-datos prv-datos">
              <Dato etiqueta="NIT">{proveedor.nit}</Dato>
              <Dato etiqueta="Teléfono">{proveedor.telefono && <a className="prv-enlace" href={`tel:${proveedor.telefono}`}>{proveedor.telefono}</a>}</Dato>
              <Dato etiqueta="Correo">{proveedor.correo && <a className="prv-enlace" href={`mailto:${proveedor.correo}`}>{proveedor.correo}</a>}</Dato>
              <Dato etiqueta="Contacto comercial">{proveedor.contacto}</Dato>
              <Dato etiqueta="Dirección">{proveedor.direccion}</Dato>
              <Dato etiqueta="Condiciones de pago">{textoCondicionPago(proveedor)}</Dato>
              <Dato etiqueta="PDF de cada compra">{recibePdf(proveedor) ? "Se le envía al correo" : "No se le envía"}</Dato>
            </dl>
          </div>
        </aside>
      </div>
    </div>
  );
}
