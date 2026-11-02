import { useEffect, useState } from "react";
import { resumenDeOrden } from "../../services/tallerService";
import { pagosDeServicio } from "../../services/solicitudServicioService";
import { ESTADOS_PAGO_NEGOCIO, filasOrden, filasPagosCliente, hayPagosCliente, textoConceptoPago, textoPago } from "./reglasDesglose";
import ChipEstado from "../ui/ChipEstado";
import Alerta from "../ui/Alerta";
import Esqueleto from "../ui/Esqueleto";
import "../../css/desglose-dinero.css";

/** Pide un resumen y lo vuelve a pedir cuando cambia la clave. Reintentar repite la misma consulta. */
function useResumen(pedir, clave) {
  const [intento, setIntento] = useState(0);
  const [carga, setCarga] = useState({ pedida: null, datos: null, error: null });
  const pedida = `${clave}|${intento}`;

  useEffect(() => {
    let vivo = true;
    pedir()
      .then((datos) => { if (vivo) setCarga({ pedida, datos, error: null }); })
      .catch((e) => { if (vivo) setCarga({ pedida, datos: null, error: e.message }); });
    return () => { vivo = false; };
    // pedir cambia en cada render: lo que decide volver a pedir es la clave
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pedida]);

  return { listo: carga.pedida === pedida, datos: carga.datos, error: carga.error, reintentar: () => setIntento((i) => i + 1) };
}

/** Filas de dinero como un recibo: se lee de arriba abajo y cabe en celular. */
export function Recibo({ filas }) {
  return (
    <dl className="dd-recibo">
      {filas.map((f) => (
        <div key={f.etiqueta} className={`dd-recibo__fila${f.tipo ? ` dd-recibo__fila--${f.tipo}` : ""}`}>
          <dt>{f.etiqueta}{f.nota && <span className="dd-nota">{f.nota}</span>}</dt>
          <dd>{f.valor}</dd>
        </div>
      ))}
    </dl>
  );
}

/** El total de una orden con su estado. Lo usan el taller ("Recibes") y el admin ("Recibe el taller"). */
export function ReciboOrden({ resumen, titulo = "Total del servicio", etiquetaRecibe }) {
  return (
    <div className="dd-orden">
      <div className="dd-linea__cabecera">
        <h3 className="dd-linea__titulo">{titulo}</h3>
        <ChipEstado catalogo={ESTADOS_PAGO_NEGOCIO} estado={resumen.estado} />
      </div>
      <Recibo filas={filasOrden(resumen, etiquetaRecibe)} />
    </div>
  );
}

/** Tu pago, para el taller: el total de la orden y, debajo, cómo va cada uno de sus pagos. children envuelve el contenido. */
export function ResumenOrdenTaller({ token, ordenId, estado, children }) {
  const { listo, datos, error, reintentar } = useResumen(() => resumenDeOrden(token, ordenId), `${ordenId}|${estado}`);

  if (!listo) return children(<Esqueleto variante="lista" cantidad={1} texto="Cargando tu pago…" />);
  if (error) return children(<Alerta onReintentar={reintentar}>{error}</Alerta>);
  if (!datos?.lineas?.length) return null;
  return children(
    <div className="dd-lineas">
      <ReciboOrden resumen={datos} etiquetaRecibe="Recibes" />
      <ul className="dd-pagos" aria-label="Estado de cada pago">
        {datos.lineas.map((l, i) => (
          <li key={i} className="dd-pago">
            <span className="dd-pago__concepto">
              <strong>{textoConceptoPago(l.concepto)}</strong>
              <span className="dd-nota">{textoPago(l)}</span>
            </span>
            <ChipEstado catalogo={ESTADOS_PAGO_NEGOCIO} estado={l.estado} />
          </li>
        ))}
      </ul>
    </div>,
  );
}

/** Lo que pagó el cliente por su servicio. Sin pagos no se muestra: antes de pagar no hay nada que resumir. */
export function PagosCliente({ token, ordenId, estado, children }) {
  const { listo, datos, error, reintentar } = useResumen(() => pagosDeServicio(token, ordenId), `${ordenId}|${estado}`);

  if (!listo) return null;
  if (error) return children(<Alerta onReintentar={reintentar}>{error}</Alerta>);
  if (!hayPagosCliente(datos)) return null;
  return children(<Recibo filas={filasPagosCliente(datos)} />);
}
