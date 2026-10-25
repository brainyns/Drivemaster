import { useEffect, useState } from "react";
import { getToken } from "../../services/authService";
import { informesTaller } from "../../services/informesTallerService";
import { formatPrecio } from "../../components/servicio/estadosServicio";
import { ESTADOS_TALLER } from "../../components/servicio/estadosTaller";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Indicador from "../../components/ui/Indicador";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import Estrellas from "../../components/ui/Estrellas";
import BarraProgreso from "../../components/ui/BarraProgreso";
import "../../css/informes-taller.css";

const PERIODOS = [3, 6, 12];
// Menos calificaciones que esto: se muestran, pero se avisa que el promedio aún dice poco.
const MINIMO_REPRESENTATIVO = 5;

const horas = (h) => {
  if (h == null) return "—";
  if (h < 1) return `${Math.round(h * 60)} min`;
  return h < 48 ? `${h.toLocaleString("es-CO", { maximumFractionDigits: 1 })} h` : `${Math.round(h / 24)} d`;
};

const decimal = (n) => n.toLocaleString("es-CO", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Tarjeta de un informe: título y contenido (ui-card de ui.css). */
function Tarjeta({ id, titulo, children }) {
  return (
    <section className="ui-card ti-tarjeta" aria-labelledby={id}>
      <div className="ui-card__header"><h2 id={id} className="ui-card__titulo">{titulo}</h2></div>
      <div className="ui-card__cuerpo ti-tarjeta__cuerpo">{children}</div>
    </section>
  );
}

/** Barras horizontales con el nombre y el valor en texto; la barra solo acompaña. */
function Barras({ filas }) {
  const tope = Math.max(1, ...filas.map(([, n]) => n));
  return (
    <ul className="ti-barras">
      {filas.map(([nombre, n]) => (
        <li key={nombre} className="ti-barras__fila">
          <span className="ti-barras__nombre">{nombre}</span>
          <span className="ti-barras__pista" aria-hidden="true">
            <span className="ti-barras__barra" style={{ width: `${(n * 100) / tope}%` }} />
          </span>
          <span className="ti-barras__valor">{n}</span>
        </li>
      ))}
    </ul>
  );
}

function ServiciosPorEstado({ servicios }) {
  const filas = Object.entries(servicios.porEstado).filter(([, n]) => n > 0)
    .map(([estado, n]) => [ESTADOS_TALLER[estado]?.label || estado, n]);
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
          <dt>Liquidado</dt>
          <dd>
            <strong className="ui-precio">{formatPrecio(monto.liquidado)}</strong>
            <span>{monto.lineasLiquidadas} {monto.lineasLiquidadas === 1 ? "pago" : "pagos"}</span>
          </dd>
        </div>
        <div>
          <dt>Pendiente</dt>
          <dd>
            <strong className="ui-precio">{formatPrecio(monto.pendiente)}</strong>
            <span>{monto.lineasPendientes} {monto.lineasPendientes === 1 ? "pago" : "pagos"}</span>
          </dd>
        </div>
      </dl>
      {total > 0 && <BarraProgreso valor={(monto.liquidado * 100) / total} etiqueta={`${titulo}: parte liquidada`} tono="ok" />}
    </div>
  );
}

function Ingresos({ ingresos }) {
  const { manoDeObra, diagnostico, anulado, anteriores } = ingresos;
  const hay = manoDeObra.liquidado + manoDeObra.pendiente + diagnostico.liquidado + diagnostico.pendiente > 0;
  return (
    <Tarjeta id="ti-ingresos" titulo="Ingresos por mano de obra">
      {hay ? (
        <div className="ti-dineros">
          <Dinero titulo="Mano de obra" monto={manoDeObra} />
          <Dinero titulo="Diagnóstico" monto={diagnostico} />
        </div>
      ) : (
        <p className="ti-vacio">
          Todavía no tienes pagos repartidos. La mano de obra queda pendiente cuando el cliente paga la cotización y se
          habilita al entregar el vehículo.
        </p>
      )}
      <p className="ti-nota">
        Es el total de todos tus servicios, no solo del periodo. “Pendiente” es lo que aún no se te ha pagado.
        {anulado > 0 && ` Hay ${formatPrecio(anulado)} anulados que no cuentan.`}
      </p>
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
          Aún no hay revisiones que medir. Se calcula desde que se te asigna una solicitud hasta que pasa a revisión.
        </p>
      ) : (
        <>
          <dl className="ti-duo">
            <div><dt>Promedio</dt><dd>{horas(tiempo.horasPromedio)}</dd></div>
            <div><dt>Mediana</dt><dd>{horas(tiempo.horasMediana)}</dd></div>
          </dl>
          <p className="ti-nota">
            Basado en {tiempo.muestra} {tiempo.muestra === 1 ? "solicitud" : "solicitudes"} del periodo, desde la asignación hasta la revisión.
          </p>
        </>
      )}
    </Tarjeta>
  );
}

function Calificacion({ calificacion }) {
  const filas = Object.entries(calificacion.distribucion).sort(([a], [b]) => b - a)
    .map(([estrellas, n]) => [`${estrellas} ${estrellas === "1" ? "estrella" : "estrellas"}`, n]);
  return (
    <Tarjeta id="ti-calificacion" titulo="Calificación de los clientes">
      {calificacion.total === 0 ? (
        <p className="ti-vacio">
          Todavía ningún cliente te ha calificado. El cliente califica al confirmar que recibió su vehículo, así que
          aparecerá aquí después de tus primeras entregas. No mostramos un promedio hasta que exista.
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
        .catch((e) => { if (!cancelado) setEstado({ clave, datos: null, error: e.message }); });
    }, 0);
    return () => { cancelado = true; clearTimeout(id); };
  }, [token, meses, clave]);

  const cargando = estado.clave !== clave;
  const d = !cargando ? estado.datos : null;
  const entregados = d?.servicios.porEstado.ENTREGADA ?? 0;

  return (
    <div className="ti-root">
      <EncabezadoPagina
        titulo="Informes"
        descripcion="Qué hiciste, cuánto te corresponde y cómo te califican tus clientes."
        acciones={(
          <div className="ui-filtros" role="group" aria-label="Periodo de los servicios y tiempos">
            {PERIODOS.map((m) => (
              <button key={m} type="button" className="ui-chip" aria-pressed={meses === m} onClick={() => setMeses(m)}>
                {m} meses
              </button>
            ))}
          </div>
        )}
      />

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
            <div className="ui-indicadores ti-indicadores">
              <Indicador icono="tool" titulo="Servicios" valor={d.servicios.total}
                pie={`${entregados} ${entregados === 1 ? "entregado" : "entregados"} en ${d.mesesConsultados} meses`} />
              <Indicador titulo="Mano de obra liquidada" valor={formatPrecio(d.ingresos.manoDeObra.liquidado)} tono="ok"
                pie={`${formatPrecio(d.ingresos.manoDeObra.pendiente)} pendiente`} />
              <Indicador icono="clock" titulo="Respuesta promedio" valor={horas(d.tiempoRespuesta.horasPromedio)}
                pie={d.tiempoRespuesta.muestra > 0 ? `${d.tiempoRespuesta.muestra} medidas` : "Sin revisiones aún"} />
              <Indicador icono="star" titulo="Calificación"
                valor={d.calificacion.promedio == null ? "Nuevo" : decimal(d.calificacion.promedio)}
                pie={d.calificacion.total > 0 ? `${d.calificacion.total} de clientes` : "Sin calificaciones aún"} />
            </div>
          </section>

          <div className="ti-rejilla">
            <ServiciosPorEstado servicios={d.servicios} />
            <Ingresos ingresos={d.ingresos} />
            <TiempoRespuesta tiempo={d.tiempoRespuesta} />
            <Calificacion calificacion={d.calificacion} />
          </div>
        </>
      )}
    </div>
  );
}
