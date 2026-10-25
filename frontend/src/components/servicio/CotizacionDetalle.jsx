import Etiqueta from "./Etiqueta";
import { ESTADOS_COTIZACION, formatFechaHora, formatPrecio } from "./estadosServicio";
import "../../css/cotizacion.css";

const formatPct = (v) => (v == null ? "" : `${Number.isInteger(v) ? v : v.toFixed(1)} %`);

/**
 * Desglose de una cotización (repuestos, mano de obra, IVA, anticipo y total a
 * pagar). Lo usan el cliente, el taller y la página pública del correo.
 * Los montos vienen calculados del backend; aquí solo se muestran.
 */
export default function CotizacionDetalle({ cotizacion: c, mostrarComision = false, sinEstado = false }) {
  const repuestos = (c.items || []).filter((i) => i.tipo === "REPUESTO");
  const manoObra = (c.items || []).filter((i) => i.tipo === "MANO_OBRA");
  // Lo del taller es la mano de obra con su IVA (la comisión es informativa: no se le resta)
  const recibe = c.subtotalManoObra != null
    ? c.subtotalManoObra + (c.ivaManoObra ?? Math.round((c.subtotalManoObra * (c.ivaPct || 0)) / 100))
    : null;

  return (
    <div className="ct-detalle">
      <div className="ct-detalle__cabecera">
        <span className="ss-eyebrow">
          Cotización{c.version ? ` v${c.version}` : ""}{c.tallerNombre ? ` · ${c.tallerNombre}` : ""}
        </span>
        {!sinEstado && c.estado && <Etiqueta estado={c.estado} catalogo={ESTADOS_COTIZACION} />}
      </div>

      {repuestos.length > 0 && (
        <>
          <h4 className="ct-detalle__grupo">Repuestos</h4>
          <ul className="ct-items">
            {repuestos.map((i, n) => (
              <li key={`r${n}`} className="ct-item">
                <span className="ct-item__desc">
                  {i.cantidad} × {i.descripcion}
                  <span className="ss-meta">
                    {[i.marca, i.referencia, i.tiendaNombre].filter(Boolean).join(" · ")}
                    {i.disponibilidad === "ENCARGO" && ` · Encargo${i.diasEntrega ? ` (${i.diasEntrega} días)` : ""}`}
                  </span>
                </span>
                <span className="ct-item__valor">{formatPrecio(i.subtotal)}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      {manoObra.length > 0 && (
        <>
          <h4 className="ct-detalle__grupo">Mano de obra</h4>
          <ul className="ct-items">
            {manoObra.map((i, n) => (
              <li key={`m${n}`} className="ct-item">
                <span className="ct-item__desc">
                  {i.descripcion}
                  {i.modalidad === "HORAS" && (
                    <span className="ss-meta">{i.horas} h × {formatPrecio(i.precioUnitario)}</span>
                  )}
                </span>
                <span className="ct-item__valor">{formatPrecio(i.subtotal)}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      <dl className="ct-totales">
        <div className="ct-totales__fila"><dt>Subtotal</dt><dd>{formatPrecio(c.subtotal)}</dd></div>
        <div className="ct-totales__fila"><dt>IVA ({formatPct(c.ivaPct)})</dt><dd>{formatPrecio(c.iva)}</dd></div>
        {/* Sin anticipo, el total y el total a pagar son la misma cifra: solo queda el segundo */}
        {c.anticipoDiagnostico > 0 && (
          <>
            <div className="ct-totales__fila"><dt>Total</dt><dd>{formatPrecio(c.total)}</dd></div>
            <div className="ct-totales__fila ct-totales__fila--descuento">
              <dt>Diagnóstico ya pagado</dt><dd>− {formatPrecio(c.anticipoDiagnostico)}</dd>
            </div>
          </>
        )}
        <div className="ct-totales__fila ct-totales__fila--final"><dt>Total a pagar</dt><dd>{formatPrecio(c.totalAPagar)}</dd></div>
        {mostrarComision && recibe != null && (
          <div className="ct-totales__fila ct-totales__fila--recibe">
            <dt>
              Recibes
              <span className="ss-meta">
                Mano de obra con su IVA{c.anticipoDiagnostico > 0 ? ", incluido el diagnóstico ya pagado" : ""}. Los repuestos van a la tienda.
              </span>
            </dt>
            <dd>{formatPrecio(recibe)}</dd>
          </div>
        )}
        {mostrarComision && c.comisionInformativa != null && (
          <div className="ct-totales__fila ct-totales__fila--info">
            <dt>Comisión DriveMaster (informativa, hoy no se descuenta)</dt><dd>{formatPrecio(c.comisionInformativa)}</dd>
          </div>
        )}
      </dl>

      <p className="ss-meta">
        {c.tiempoEstimadoHoras ? `Tiempo estimado del trabajo: ${c.tiempoEstimadoHoras} h · ` : ""}
        {c.diasEntregaRepuestos ? `Repuestos por encargo: hasta ${c.diasEntregaRepuestos} días · ` : ""}
        {c.vigenciaHasta && `Válida hasta ${formatFechaHora(c.vigenciaHasta)}`}
      </p>
      {c.observaciones && <p className="ss-texto">Nota del taller: {c.observaciones}</p>}
      {c.motivoRespuesta && <p className="ss-meta">Comentario: {c.motivoRespuesta}</p>}
    </div>
  );
}
