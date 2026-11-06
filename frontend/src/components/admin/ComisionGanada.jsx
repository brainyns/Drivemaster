import { useEffect, useState } from "react";
import { comisionGanadaPlataforma } from "../../services/plataformaService";
import FiltroFechas from "../ui/FiltroFechas";
import Esqueleto from "../ui/Esqueleto";
import { rangoRapido } from "../ui/reglasTabla";
import { tarjetasDinero, textoPeriodoComision } from "../../pages/admin/plataformaAdmin";
import Indicador from "../ui/Indicador";
import { formatoCop } from "../../utils/formato";
import { textoError } from "../../utils/errores";

// "Todas las fechas" del filtro: desde antes de que existiera la comisión (sin fechas el backend da el mes en curso)
const DESDE_SIEMPRE = "2020-01-01";

/**
 * Comisión ganada por DriveMaster en el periodo (por defecto, este mes): la retenida en los pagos aprobados menos la
 * anulada por reembolsos. Debajo, en pequeño, la ya cobrada (pagos ya transferidos) y la informativa. Sale de
 * GET /api/plataforma/comision-ganada, con las mismas líneas que Liquidaciones.
 *
 * El mismo selector manda en todas las tarjetas de "Dinero": las del periodo vienen en la misma respuesta (dinero) y las
 * de estado actual (ahora: lo que falta pagar o entregar, del dashboard) no dependen de fechas. onIr(destino) las abre.
 */
export default function ComisionGanada({ token, onVerReporte, ahora, onIr }) {
  const [periodo, setPeriodo] = useState(() => rangoRapido("mes"));
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });
  const clave = `${periodo.desde}|${periodo.hasta}`;

  useEffect(() => {
    let vivo = true;
    const filtros = periodo.desde || periodo.hasta ? periodo : { desde: DESDE_SIEMPRE, hasta: rangoRapido("hoy").hasta };
    comisionGanadaPlataforma(token, filtros)
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setEstado({ clave, datos: null, error: textoError(e, "No pudimos cargar la comisión.") }); });
    return () => { vivo = false; };
    // periodo cambia junto con clave: basta con ella
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, clave]);

  const cargando = estado.clave !== clave;
  const d = estado.datos;
  const tarjetas = tarjetasDinero(cargando || estado.error ? null : d?.dinero, ahora, textoPeriodoComision(periodo), formatoCop);

  return (
    <>
    <section className="ui-card pp-comision" aria-labelledby="pp-comision-titulo" aria-busy={cargando}>
      <div className="pp-comision__cabecera">
        <h3 id="pp-comision-titulo" className="pp-comision__titulo">Comisión ganada {textoPeriodoComision(periodo)}</h3>
        <FiltroFechas id="pp-comision-fechas" etiqueta="Periodo de la comisión" valor={periodo} onCambiar={setPeriodo} />
      </div>
      {cargando ? (
        <Esqueleto variante="indicadores" cantidad={1} texto="Cargando la comisión…" />
      ) : estado.error ? (
        <p className="pp-comision__error" role="alert">{estado.error}</p>
      ) : (
        <>
          <p className="pp-comision__valor">{formatoCop(d.ganada)}</p>
          <ul className="pp-comision__detalle">
            <li>Ya cobrada: <strong>{formatoCop(d.liquidada)}</strong></li>
            <li>Informativa, no cobrada: <strong>{formatoCop(d.informativa)}</strong></li>
          </ul>
          <p className="pp-comision__nota">
            {d.comisionSeDescuenta
              ? "Descontada de los pagos aprobados en el periodo, menos lo que se devolvió por reembolsos."
              : "El cobro está apagado: los pagos nuevos solo registran la comisión informativa."}
            {onVerReporte && (
              <> <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={onVerReporte}>Ver el detalle por mes</button></>
            )}
          </p>
        </>
      )}
    </section>
    {ahora && (
      <div className="ui-indicadores pp-indicadores-dinero" aria-busy={cargando}>
        {tarjetas.map((t) => (
          <Indicador key={t.clave} titulo={t.titulo} valor={cargando ? "…" : t.valor} pie={t.pie} tono={t.tono}
            onClick={t.destino && onIr ? () => onIr(t.destino) : undefined} />
        ))}
      </div>
    )}
    </>
  );
}
