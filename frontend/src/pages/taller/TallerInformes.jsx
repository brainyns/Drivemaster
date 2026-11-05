import { useEffect, useState } from "react";
import { getToken } from "../../services/authService";
import { informesTaller } from "../../services/informesTallerService";
import { formatPrecio } from "../../components/servicio/estadosServicio";
import { ESTADOS_TALLER, faseDe } from "../../components/servicio/estadosTaller";
import { duracionMinutos, plural, textoPeriodo } from "../../utils/formato";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import InfoAyuda from "../../components/ui/InfoAyuda";
import Glosario from "../../components/ui/Glosario";
import BandaDinero from "../../components/ui/BandaDinero";
import EsteMes from "../../components/ui/EsteMes";
import { bandaDinero, loTuyoDelMes } from "../../utils/bandaDinero";
import Indicador from "../../components/ui/Indicador";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import Estrellas from "../../components/ui/Estrellas";
import BarraProgreso from "../../components/ui/BarraProgreso";
import { ResumenDinero } from "../../components/servicio/DesgloseDinero";
import { notaTotalPeriodo } from "../../components/servicio/reglasDesglose";
import Grafico from "../../components/ui/Grafico";
import { etiquetaMes, pesosCorto } from "../../components/ui/escalaGrafico";
import "../../css/informes-taller.css";
import { textoError } from "../../utils/errores";

const PERIODOS = [3, 6, 12];
// Menos calificaciones que esto: se muestran, pero se avisa que el promedio aún dice poco.
const MINIMO_REPRESENTATIVO = 5;

const decimal = (n) => n.toLocaleString("es-CO", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Tarjeta de un informe: título, la ayuda "?" de una línea (si la hay) y contenido (ui-card de ui.css). */
function Tarjeta({ id, titulo, ayuda = null, children }) {
  return (
    <section className="ui-card ti-tarjeta" aria-labelledby={id}>
      <div className="ui-card__header"><h2 id={id} className="ui-card__titulo">{titulo}{ayuda}</h2></div>
      <div className="ui-card__cuerpo ti-tarjeta__cuerpo">{children}</div>
    </section>
  );
}

/**
 * Barras horizontales con el nombre y el valor en texto; la barra solo acompaña. fase (opcional, tercer valor de
 * cada fila) la pinta con el color de esa fase. Un valor 0 deja la pista vacía.
 */
function Barras({ filas }) {
  const tope = Math.max(1, ...filas.map(([, n]) => n));
  return (
    <ul className="ti-barras">
      {filas.map(([nombre, n, fase]) => (
        <li key={nombre} className={`ti-barras__fila${fase ? ` ui-fase--${fase}` : ""}`}>
          <span className="ti-barras__nombre">{nombre}</span>
          <span className="ti-barras__pista" aria-hidden="true">
            {n > 0 && <span className="ti-barras__barra" style={{ width: `${(n * 100) / tope}%` }} />}
          </span>
          <span className="ti-barras__valor">{n}</span>
        </li>
      ))}
    </ul>
  );
}

function ServiciosPorEstado({ servicios }) {
  const filas = Object.entries(servicios.porEstado).filter(([, n]) => n > 0)
    .map(([estado, n]) => [ESTADOS_TALLER[estado]?.label || estado, n, faseDe(estado)]);
  return (
    <Tarjeta id="ti-servicios" titulo="Servicios por estado">
      {filas.length === 0 ? (
        <p className="ti-vacio">
          Aún no hay solicitudes en este periodo. Cuando un cliente pida un diagnóstico en tu zona, verás aquí en qué
          etapa va cada una.
        </p>
      ) : <Barras filas={filas} />}
    </Tarjeta>
  );
}

function Dinero({ titulo, monto }) {
  const total = monto.liquidado + monto.pendiente;
  return (
    <div className="ti-dinero">
      <h3 className="ti-dinero__titulo">{titulo}</h3>
      <dl className="ti-dinero__cifras">
        <div>
          <dt>Ya te pagaron</dt>
          <dd>
            <strong className="ui-precio">{formatPrecio(monto.liquidado)}</strong>
            <span>{monto.lineasLiquidadas} {monto.lineasLiquidadas === 1 ? "pago" : "pagos"}</span>
          </dd>
        </div>
        <div>
          <dt>Falta que se entregue</dt>
          <dd>
            <strong className="ui-precio">{formatPrecio(monto.pendiente)}</strong>
            <span>{monto.lineasPendientes} {monto.lineasPendientes === 1 ? "pago" : "pagos"}</span>
          </dd>
        </div>
      </dl>
      {total > 0 && <BarraProgreso valor={(monto.liquidado * 100) / total} etiqueta={`${titulo}: parte pagada`} tono="ok" />}
    </div>
  );
}

function Ingresos({ ingresos, periodo }) {
  const { manoDeObra, diagnostico, anulado, anteriores, desglose } = ingresos;
  // Las cuentas anteriores al inventario del taller no traen esta cifra
  const repuestos = ingresos.repuestosTaller || { liquidado: 0, pendiente: 0, lineasLiquidadas: 0, lineasPendientes: 0 };
  const conRepuestos = repuestos.liquidado + repuestos.pendiente > 0;
  const conDiagnostico = diagnostico.liquidado + diagnostico.pendiente > 0;
  const hay = manoDeObra.liquidado + manoDeObra.pendiente > 0 || conDiagnostico || conRepuestos;
  return (
    <Tarjeta id="ti-ingresos" titulo="Ingresos del taller"
      ayuda={<InfoAyuda texto="Ya te pagaron: ya te llegó la transferencia. Falta que se entregue: el pago se habilita cuando el vehículo se entrega." />}>
      {hay && <ResumenDinero resumen={desglose} etiquetaVenta="Total generado" notaVenta={notaTotalPeriodo(ingresos)} />}
      {hay ? (
        <div className="ti-dineros">
          <Dinero titulo={conRepuestos ? "Mano de obra" : "Resta de la cotización"} monto={manoDeObra} />
          {conRepuestos && <Dinero titulo="Repuestos del taller" monto={repuestos} />}
          {/* Sin cobros de diagnóstico, dos $0 no dicen nada: se rotula sin costo */}
          {conDiagnostico ? <Dinero titulo="Diagnóstico" monto={diagnostico} /> : (
            <div className="ti-dinero">
              <h3 className="ti-dinero__titulo">Diagnóstico</h3>
              <p className="ti-nota">Sin costo: tus diagnósticos no han generado cobros.</p>
            </div>
          )}
        </div>
      ) : (
        <p className="ti-vacio">
          No hay pagos en {periodo}. La mano de obra se habilita cuando el cliente paga la cotización y el vehículo se
          entrega.
        </p>
      )}
      <p className="ti-nota">
        El total generado suma lo pagado por diagnóstico y cotización, con IVA.{conRepuestos ? " Tus repuestos van aparte de la mano de obra, aunque se paguen juntos." : ""} Las demás cifras {periodo} están en lo que recibes. “Ya te pagaron” cuenta por la fecha en que se te pagó y “Falta que se entregue” por la fecha en que pagó el cliente; cada pago está en uno solo de los dos.
        {anulado > 0 && ` Hay ${formatPrecio(anulado)} cancelados que no cuentan.`}
      </p>
      <Glosario claves={["Adelanto del diagnóstico", "Resta de la cotización", "Ya te pagaron", "Falta que se entregue", "Comisión"]} />
      {anteriores.ordenes > 0 && (
        <p className="ti-nota ti-nota--aparte">
          Aparte, del flujo anterior: {anteriores.ordenes} {anteriores.ordenes === 1 ? "orden finalizada" : "órdenes finalizadas"} por{" "}
          {formatPrecio(anteriores.manoDeObra)} de mano de obra. No se suman a lo de arriba.
        </p>
      )}
    </Tarjeta>
  );
}

function TiempoRespuesta({ tiempo }) {
  return (
    <Tarjeta id="ti-tiempo" titulo="Tiempo de respuesta">
      {tiempo.muestra === 0 ? (
        <p className="ti-vacio">
          Aún no hay respuestas que medir. Se calcula desde que se te asigna una solicitud hasta que la aceptas y
          agendas la revisión, o la rechazas.
        </p>
      ) : (
        <>
          <dl className="ti-duo">
            <div><dt>Promedio</dt><dd>{duracionMinutos(tiempo.minutosPromedio)}</dd></div>
            <div><dt>Mediana</dt><dd>{duracionMinutos(tiempo.minutosMediana)}</dd></div>
          </dl>
          <p className="ti-nota">
            Basado en {tiempo.muestra} {tiempo.muestra === 1 ? "solicitud" : "solicitudes"} del periodo, desde la asignación
            hasta que respondes (aceptas y agendas, o rechazas).
          </p>
        </>
      )}
    </Tarjeta>
  );
}

/** Un mes por columna (los meses sin nada van en 0). Pesos y conteos en gráficos separados: cada uno con su eje. */
function MesAMes({ serie }) {
  const etiquetas = serie.map((m) => etiquetaMes(m.mes));
  const detalles = serie.map((m) => etiquetaMes(m.mes, true));
  return (
    <Tarjeta id="ti-mesames" titulo="Mes a mes">
      <h3 className="ti-dinero__titulo">Ingresos ya pagados</h3>
      <Grafico titulo="Ingresos ya pagados por mes" etiquetas={etiquetas} detalles={detalles}
        series={[{ nombre: "Ingresos ya pagados", valores: serie.map((m) => m.ingresosLiquidados) }]}
        formato={formatPrecio} formatoEje={pesosCorto} />
      <h3 className="ti-dinero__titulo">Solicitudes y servicios entregados</h3>
      <Grafico titulo="Solicitudes y servicios entregados por mes" etiquetas={etiquetas} detalles={detalles}
        series={[
          { nombre: "Solicitudes", valores: serie.map((m) => m.solicitudes) },
          { nombre: "Entregados", valores: serie.map((m) => m.entregados) },
        ]} alto={120} />
      <p className="ti-nota">
        Ingresos: mano de obra, diagnóstico y repuestos de tu inventario, en el mes en que se te pagaron. Solicitudes, en el mes en que llegaron;
        entregados, en el mes de la entrega.
      </p>
    </Tarjeta>
  );
}

function Calificacion({ calificacion, periodo }) {
  const filas = Object.entries(calificacion.distribucion).sort(([a], [b]) => b - a)
    .map(([estrellas, n]) => [`${estrellas} ${estrellas === "1" ? "estrella" : "estrellas"}`, n]);
  return (
    <Tarjeta id="ti-calificacion" titulo="Calificación de los clientes">
      {calificacion.total === 0 ? (
        <p className="ti-vacio">
          Ningún cliente te calificó {periodo}. El cliente califica al confirmar que recibió su vehículo, así que
          aparecerá aquí después de tus entregas. No mostramos un promedio hasta que exista.
        </p>
      ) : (
        <>
          <div className="ti-nota-grande">
            <strong>{decimal(calificacion.promedio)}</strong>
            <Estrellas valor={calificacion.promedio} size={20} />
            <span>{calificacion.total} {calificacion.total === 1 ? "calificación" : "calificaciones"}</span>
          </div>
          {calificacion.total < MINIMO_REPRESENTATIVO && (
            <p className="ti-nota">Con pocas calificaciones el promedio todavía dice poco; se afina con cada entrega.</p>
          )}
          <Barras filas={filas} />
          {calificacion.recientes.length > 0 && (
            <ul className="ti-comentarios" aria-label="Comentarios recientes">
              {calificacion.recientes.map((c, i) => (
                <li key={`${c.fecha}-${i}`}>
                  <Estrellas valor={c.calificacion} size={14} />
                  <p>“{c.comentario}”</p>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </Tarjeta>
  );
}

/** Informes del taller: lo que hizo, lo que ganó y cómo lo califican. Cada taller ve solo lo suyo. */
export default function TallerInformes() {
  const token = getToken();
  const [meses, setMeses] = useState(6);
  const [intento, setIntento] = useState(0);
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });
  const clave = `${meses}-${intento}`;

  useEffect(() => {
    let cancelado = false;
    // El pedido va en un timeout para no actualizar el estado dentro del efecto
    const id = setTimeout(() => {
      informesTaller(token, meses)
        .then((datos) => { if (!cancelado) setEstado({ clave, datos, error: null }); })
        .catch((e) => { if (!cancelado) setEstado({ clave, datos: null, error: textoError(e) }); });
    }, 0);
    return () => { cancelado = true; clearTimeout(id); };
  }, [token, meses, clave]);

  const cargando = estado.clave !== clave;
  const d = !cargando ? estado.datos : null;
  const entregados = d?.servicios.porEstado.ENTREGADA ?? 0;
  const periodo = d ? textoPeriodo(d.periodoDesde, d.mesesConsultados) : "";

  return (
    <div className="ti-root">
      <EncabezadoPagina
        titulo="Informes"
        descripcion="Qué hiciste, cuánto te corresponde y cómo te califican tus clientes."
        acciones={(
          <div className="ui-filtros" role="group" aria-label="Periodo de los informes">
            {PERIODOS.map((m) => (
              <button key={m} type="button" className="ui-chip" aria-pressed={meses === m} onClick={() => setMeses(m)}>
                {m} meses
              </button>
            ))}
          </div>
        )}
      />

      <EsteMes version={intento} detalle="Lo tuyo, después de IVA y comisión"
        cargar={() => informesTaller(token, 1).then((m) => loTuyoDelMes(m.ingresos?.desglose, m.ingresos?.iva))} />

      {!cargando && estado.error && (
        <Alerta onReintentar={() => setIntento((n) => n + 1)}>{estado.error}</Alerta>
      )}

      {cargando && (
        <>
          <div className="ui-indicadores ti-indicadores" aria-hidden="true">
            {[0, 1, 2, 3].map((i) => <span key={i} className="ui-skeleton ti-esqueleto" />)}
          </div>
          <Esqueleto variante="lista" cantidad={2} texto="Cargando tus informes…" />
        </>
      )}

      {d && (
        <>
          <section aria-labelledby="ti-resumen">
            <h2 id="ti-resumen" className="ui-sr">Resumen del periodo</h2>
            <p className="ti-periodo">Todas las cifras {periodo}.</p>
            <BandaDinero titulo={`Tu dinero ${periodo}`} banda={bandaDinero(d.ingresos.desglose, d.ingresos.iva)} />
            <div className="ui-indicadores ti-indicadores">
              <Indicador icono="tool" titulo="Servicios" valor={d.servicios.total}
                pie={`${entregados} ${entregados === 1 ? "entregado" : "entregados"} en ${d.mesesConsultados} meses`} />
              {/* Mano de obra y repuestos por separado: cada uno con lo que ya te pagaron */}
              <Indicador titulo="Mano de obra" valor={formatPrecio(d.ingresos.manoDeObra.liquidado + d.ingresos.manoDeObra.pendiente)} tono="ok"
                pie={`Ya te pagaron ${formatPrecio(d.ingresos.manoDeObra.liquidado)}`} />
              <Indicador titulo="Repuestos" valor={formatPrecio((d.ingresos.repuestosTaller?.liquidado || 0) + (d.ingresos.repuestosTaller?.pendiente || 0))}
                pie={`Ya te pagaron ${formatPrecio(d.ingresos.repuestosTaller?.liquidado || 0)}`} />
              <Indicador icono="star" titulo="Calificación"
                valor={d.calificacion.promedio == null ? "Nuevo" : decimal(d.calificacion.promedio)}
                pie={d.calificacion.total > 0 ? `${plural(d.calificacion.total, "calificación", "calificaciones")} de clientes en ${d.mesesConsultados} meses` : "Sin calificaciones en el periodo"} />
            </div>
          </section>

          <div className="ti-rejilla">
            <ServiciosPorEstado servicios={d.servicios} />
            <Ingresos ingresos={d.ingresos} periodo={periodo} />
            {/* Columna izquierda: el tiempo de respuesta es corto y la serie llena el espacio junto a la calificación */}
            <div className="ti-columna">
              <TiempoRespuesta tiempo={d.tiempoRespuesta} />
              <MesAMes serie={d.serieMensual} />
            </div>
            <Calificacion calificacion={d.calificacion} periodo={periodo} />
          </div>
        </>
      )}
    </div>
  );
}
