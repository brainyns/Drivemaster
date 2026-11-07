import Etiqueta from "./Etiqueta";
import AlertaPrediccionCotizacion from "./AlertaPrediccionCotizacion";
import { ESTADOS_COTIZACION, formatFechaHora, formatPrecio } from "./estadosServicio";
import { aclaracionUnidad, explicacionAdelanto, totalesCotizacion, totalLinea } from "./desgloseIva";
import { esDelTaller, etiquetaOrigen } from "./reglasRepuestos";
import "../../css/cotizacion.css";
import { horas } from "../../utils/formato";

/**
 * Desglose de una cotización (repuestos, mano de obra, IVA, anticipo y total a
 * pagar). Lo usan el cliente, el taller y la página pública del correo.
 * Los montos vienen calculados del backend; aquí solo se muestran.
 * horaMasIva: la tarifa por hora se lee "+ IVA" (solo la cotización de ejemplo del inicio).
 */
export default function CotizacionDetalle({ cotizacion: c, mostrarComision = false, sinEstado = false, horaMasIva = false }) {
  const repuestos = (c.items || []).filter((i) => i.tipo === "REPUESTO");
  const delTaller = repuestos.filter(esDelTaller);
  const deTiendas = repuestos.filter((i) => !esDelTaller(i));
  const manoObra = (c.items || []).filter((i) => i.tipo === "MANO_OBRA");
  // Solo el taller recibe estas cifras; lo que se descuenta de verdad se fija al aprobarse el pago
  const recibe = c.recibeTaller;
  const seDescuenta = c.comisionSeDescuenta === true;

  return (
    <div className="ct-detalle">
      <div className="ct-detalle__cabecera">
        <span className="ss-eyebrow">
          Cotización{c.version ? ` v${c.version}` : ""}{c.tallerNombre ? ` · ${c.tallerNombre}` : ""}
        </span>
        {!sinEstado && c.estado && <Etiqueta estado={c.estado} catalogo={ESTADOS_COTIZACION} />}
      </div>

      {[
        { clave: "taller", titulo: "Repuestos del taller", items: delTaller },
        { clave: "tiendas", titulo: delTaller.length > 0 ? "Repuestos de tiendas" : "Repuestos", items: deTiendas },
      ].filter((g) => g.items.length > 0).map((g) => (
        <div key={g.clave}>
          <h4 className="ct-detalle__grupo">{g.titulo}</h4>
          <ul className="ct-items">
            {g.items.map((i, n) => (
              <li key={`${g.clave}${n}`} className="ct-item">
                <span className="ct-item__desc">
                  {i.cantidad} × {i.descripcion}
                  <span className="ss-meta">
                    {[i.marca, i.referencia, !esDelTaller(i) && i.tiendaNombre].filter(Boolean).join(" · ")}
                    {i.disponibilidad === "ENCARGO" && ` · Encargo${i.diasEntrega ? ` (${i.diasEntrega} días)` : ""}`}
                  </span>
                  {aclaracionUnidad(c, i, formatPrecio) && <span className="ss-meta ct-item__final">{aclaracionUnidad(c, i, formatPrecio)}</span>}
                  <span className={`ct-origen ct-origen--${g.clave}`}>{etiquetaOrigen(i)}</span>
                </span>
                <span className="ct-item__valor">{formatPrecio(totalLinea(c, i))}</span>
              </li>
            ))}
          </ul>
        </div>
      ))}

      {manoObra.length > 0 && (
        <>
          <h4 className="ct-detalle__grupo">Mano de obra</h4>
          <ul className="ct-items">
            {manoObra.map((i, n) => (
              <li key={`m${n}`} className="ct-item">
                <span className="ct-item__desc">
                  {i.descripcion}
                  {i.modalidad === "HORAS" && (
                    <span className="ss-meta">{i.horas} h × {formatPrecio(i.precioUnitario)}{horaMasIva ? " + IVA" : ""}</span>
                  )}
                </span>
                <span className="ct-item__valor">{formatPrecio(totalLinea(c, i))}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      <dl className="ct-totales">
        {totalesCotizacion(c).map((f) => (
          <div key={f.clave} className={`ct-totales__fila${f.tono === "info" ? " ct-totales__fila--info" : f.tono === "descuento" ? " ct-totales__fila--descuento" : f.tono === "final" ? " ct-totales__fila--final" : ""}`}>
            <dt>{f.texto}</dt><dd>{f.monto < 0 ? `− ${formatPrecio(-f.monto)}` : formatPrecio(f.monto)}</dd>
          </div>
        ))}
        {mostrarComision && c.comisionTaller != null && (
          <div className="ct-totales__fila ct-totales__fila--info">
            <dt>Comisión DriveMaster{seDescuenta ? (delTaller.length > 0 ? " sobre tu mano de obra y tus repuestos" : " sobre tu mano de obra") : " (no se descuenta todavía)"}</dt>
            <dd>{seDescuenta ? `− ${formatPrecio(c.comisionTaller)}` : formatPrecio(c.comisionTaller)}</dd>
          </div>
        )}
        {mostrarComision && recibe != null && (
          <div className="ct-totales__fila ct-totales__fila--recibe">
            <dt>
              Recibes
              <span className="ss-meta">
                {delTaller.length > 0 ? "Mano de obra y tus repuestos, con su IVA" : "Mano de obra con su IVA"}{seDescuenta ? ", menos la comisión" : ""}{c.anticipoDiagnostico > 0 ? ", incluido el adelanto del diagnóstico" : ""}.{deTiendas.length > 0 ? " Los repuestos de tiendas van a cada tienda." : ""}
              </span>
            </dt>
            <dd>{formatPrecio(recibe)}</dd>
          </div>
        )}
      </dl>
      {explicacionAdelanto(c, formatPrecio) && <p className="ss-meta ct-adelanto">{explicacionAdelanto(c, formatPrecio)}</p>}

      <p className="ss-meta">
        {c.tiempoEstimadoHoras ? `Tiempo estimado del trabajo: ${horas(c.tiempoEstimadoHoras)} · ` : ""}
        {c.diasEntregaRepuestos ? `Repuestos por encargo: hasta ${c.diasEntregaRepuestos} días · ` : ""}
        {c.vigenciaHasta && `Válida hasta ${formatFechaHora(c.vigenciaHasta)}`}
      </p>
      {c.observaciones && <p className="ss-texto">Nota del taller: {c.observaciones}</p>}
      {c.motivoRespuesta && <p className="ss-meta">Comentario: {c.motivoRespuesta}</p>}

      {/* Diagnóstico permanente del Modelo Predictivo con XAI para el taller */}
      {mostrarComision && c.prediccionAprobacion && (
        <div className="ct-prediccion-seccion">
          <AlertaPrediccionCotizacion prediccion={c.prediccionAprobacion} estadoReal={c.estado} />
        </div>
      )}
    </div>
  );
}
