import { useEffect, useState } from "react";
import { liquidacionDePedido } from "../../services/pedidoTiendaService";
import { formatPrecio } from "./estadosServicio";
import { ESTADOS_PAGO_NEGOCIO, filaComision, filaComisionResumen, textoConceptoPago, textoPago } from "./reglasDesglose";
import ChipEstado from "../ui/ChipEstado";
import Alerta from "../ui/Alerta";
import Esqueleto from "../ui/Esqueleto";
import "../../css/desglose-dinero.css";
import { textoError } from "../../utils/errores";

/** Venta → comisión → lo que recibe el negocio, como un recibo: se lee de arriba abajo y cabe en celular. */
export function ReciboDinero({ venta, etiquetaVenta = "Venta", notaVenta, comision, recibes }) {
  return (
    <dl className="dd-recibo">
      <div className="dd-recibo__fila">
        <dt>{etiquetaVenta}{notaVenta && <span className="dd-nota">{notaVenta}</span>}</dt>
        <dd>{formatPrecio(venta)}</dd>
      </div>
      <div className="dd-recibo__fila">
        <dt>{comision.etiqueta}{comision.nota && <span className="dd-nota">{comision.nota}</span>}</dt>
        <dd>{comision.valor}</dd>
      </div>
      <div className="dd-recibo__fila dd-recibo__fila--final">
        <dt>Recibes</dt>
        <dd>{formatPrecio(recibes)}</dd>
      </div>
    </dl>
  );
}

/** Los totales de un informe. Sin ventas no dice nada: el panel ya lo explica. */
export function ResumenDinero({ resumen, etiquetaVenta, notaVenta }) {
  if (!resumen || resumen.venta <= 0) return null;
  return <ReciboDinero venta={resumen.venta} etiquetaVenta={etiquetaVenta} notaVenta={notaVenta} comision={filaComisionResumen(resumen)} recibes={resumen.recibes} />;
}

/**
 * El dinero de un pedido para su tienda. estado recarga al avanzar.
 * children envuelve el contenido en la tarjeta de cada pantalla; sin líneas no se muestra nada.
 */
export default function DesgloseDinero({ token, pedidoId, estado, children }) {
  const clave = `${pedidoId}|${estado}`;
  const [intento, setIntento] = useState(0);
  const [carga, setCarga] = useState({ clave: null, datos: null, error: null });
  const pedida = `${clave}|${intento}`;

  useEffect(() => {
    let vivo = true;
    liquidacionDePedido(token, pedidoId)
      .then((datos) => { if (vivo) setCarga({ clave: pedida, datos, error: null }); })
      .catch((e) => { if (vivo) setCarga({ clave: pedida, datos: null, error: textoError(e) }); });
    return () => { vivo = false; };
  }, [token, pedidoId, pedida]);

  if (carga.clave !== pedida) return children(<Esqueleto variante="lista" cantidad={1} texto="Cargando tu pago…" />);
  if (carga.error) return children(<Alerta onReintentar={() => setIntento((i) => i + 1)}>{carga.error}</Alerta>);
  const lineas = carga.datos?.lineas || [];
  if (lineas.length === 0) return null;
  return children(
    <div className="dd-lineas">
      {lineas.map((l, i) => (
        <article key={i} className="dd-linea">
          <header className="dd-linea__cabecera">
            {lineas.length > 1 && <h3 className="dd-linea__titulo">{textoConceptoPago(l.concepto)}</h3>}
            <ChipEstado catalogo={ESTADOS_PAGO_NEGOCIO} estado={l.estado} />
          </header>
          <ReciboDinero venta={l.venta} comision={filaComision(l)} recibes={l.recibes} />
          <p className="dd-nota">{textoPago(l)}</p>
        </article>
      ))}
    </div>,
  );
}
