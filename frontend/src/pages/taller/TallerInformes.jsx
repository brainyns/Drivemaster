import { useEffect, useState } from "react";
import { getToken } from "../../services/authService";
import { informesTaller } from "../../services/informesTallerService";
import { ESTADOS, formatPrecio } from "../../components/servicio/estadosServicio";

const PERIODOS = [3, 6, 12];
// Menos calificaciones que esto: se muestran, pero se avisa que el promedio aún dice poco.
const MINIMO_REPRESENTATIVO = 5;

const horas = (h) => {
  if (h == null) return "—";
  if (h < 1) return `${Math.round(h * 60)} min`;
  return h < 48 ? `${h.toLocaleString("es-CO", { maximumFractionDigits: 1 })} h` : `${Math.round(h / 24)} d`;
};

function Resumen({ titulo, valor, pie, tono }) {
  return (
    <div className={`tp-resumen${tono ? ` tp-resumen--${tono}` : ""}`}>
      <span className="tp-resumen__titulo">{titulo}</span>
      <span className="tp-resumen__valor">{valor}</span>
      <span className="tp-resumen__pie">{pie}</span>
    </div>
  );
}

function Estrellas({ valor }) {
  const llenas = Math.round(valor || 0);
  return (
    <span className="tp-estrellas" role="img" aria-label={`${valor ?? 0} de 5`}>
      {[1, 2, 3, 4, 5].map((n) => <span key={n} className={n <= llenas ? "tp-estrellas__on" : ""}>★</span>)}
    </span>
  );
}

function ServiciosPorEstado({ servicios }) {
  const filas = Object.entries(servicios.porEstado).filter(([, n]) => n > 0);
  const tope = Math.max(1, ...filas.map(([, n]) => n));
  return (
    <section className="tp-bloque tp-informe" aria-labelledby="ti-servicios">
      <h2 id="ti-servicios" className="tp-bloque__titulo">Servicios por estado</h2>
      {filas.length === 0 ? (
        <p className="tp-bloque__vacio">
          Aún no hay solicitudes en este periodo. Cuando un cliente pida un diagnóstico en tu zona, verás aquí en qué
          etapa va cada una.
        </p>
      ) : (
        <ul className="tp-barras">
          {filas.map(([estado, n]) => (
            <li key={estado} className="tp-barras__fila">
              <span className="tp-barras__nombre">{ESTADOS[estado]?.label || estado}</span>
              <span className="tp-barras__pista" aria-hidden="true">
                <span className="tp-barras__barra" style={{ width: `${(n * 100) / tope}%` }} />
              </span>
              <span className="tp-barras__valor">{n}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Dinero({ titulo, monto }) {
  const total = monto.liquidado + monto.pendiente;
  const pct = total > 0 ? (monto.liquidado * 100) / total : 0;
  return (
    <div className="tp-dinero">
      <h3>{titulo}</h3>
      <div className="tp-dinero__cifras">
        <div>
          <span className="tp-dinero__etiqueta">Liquidado</span>
          <strong>{formatPrecio(monto.liquidado)}</strong>
          <span className="tp-dinero__lineas">{monto.lineasLiquidadas} {monto.lineasLiquidadas === 1 ? "pago" : "pagos"}</span>
        </div>
        <div>
          <span className="tp-dinero__etiqueta">Pendiente</span>
          <strong>{formatPrecio(monto.pendiente)}</strong>
          <span className="tp-dinero__lineas">{monto.lineasPendientes} {monto.lineasPendientes === 1 ? "pago" : "pagos"}</span>
        </div>
      </div>
      {total > 0 && (
        <span className="tp-dinero__pista" aria-hidden="true"><span className="tp-dinero__hecho" style={{ width: `${pct}%` }} /></span>
      )}
    </div>
  );
}

function Ingresos({ ingresos }) {
  const { manoDeObra, diagnostico, anulado, anteriores } = ingresos;
  const hay = manoDeObra.liquidado + manoDeObra.pendiente + diagnostico.liquidado + diagnostico.pendiente > 0;
  return (
    <section className="tp-bloque tp-informe" aria-labelledby="ti-ingresos">
      <h2 id="ti-ingresos" className="tp-bloque__titulo">Ingresos por mano de obra</h2>
      {hay ? (
        <div className="tp-dineros">
          <Dinero titulo="Mano de obra" monto={manoDeObra} />
          <Dinero titulo="Diagnóstico" monto={diagnostico} />
        </div>
      ) : (
        <p className="tp-bloque__vacio">
          Todavía no tienes pagos repartidos. La mano de obra queda pendiente cuando el cliente paga la cotización y se
          habilita al entregar el vehículo.
        </p>
      )}
      <p className="tp-nota">
        Es el total de todos tus servicios, no solo del periodo. “Pendiente” es lo que aún no se te ha pagado.
        {anulado > 0 && ` Hay ${formatPrecio(anulado)} anulados que no cuentan.`}
      </p>
      {anteriores.ordenes > 0 && (
        <p className="tp-nota tp-nota--aparte">
          Aparte, del flujo anterior: {anteriores.ordenes} {anteriores.ordenes === 1 ? "orden finalizada" : "órdenes finalizadas"} por{" "}
          {formatPrecio(anteriores.manoDeObra)} de mano de obra. No se suman a lo de arriba.
        </p>
      )}
    </section>
  );
}

function TiempoRespuesta({ tiempo }) {
  return (
    <section className="tp-bloque tp-informe" aria-labelledby="ti-tiempo">
      <h2 id="ti-tiempo" className="tp-bloque__titulo">Tiempo de respuesta</h2>
      {tiempo.muestra === 0 ? (
        <p className="tp-bloque__vacio">
          Aún no hay revisiones que medir. Se calcula desde que se te asigna una solicitud hasta que pasa a revisión.
        </p>
      ) : (
        <>
          <div className="tp-duo">
            <div><span className="tp-duo__valor">{horas(tiempo.horasPromedio)}</span><span className="tp-duo__etiqueta">Promedio</span></div>
            <div><span className="tp-duo__valor">{horas(tiempo.horasMediana)}</span><span className="tp-duo__etiqueta">Mediana</span></div>
          </div>
          <p className="tp-nota">Basado en {tiempo.muestra} {tiempo.muestra === 1 ? "solicitud" : "solicitudes"} del periodo, desde la asignación hasta la revisión.</p>
        </>
      )}
    </section>
  );
}

function Calificacion({ calificacion }) {
  const tope = Math.max(1, ...Object.values(calificacion.distribucion));
  return (
    <section className="tp-bloque tp-informe" aria-labelledby="ti-calificacion">
      <h2 id="ti-calificacion" className="tp-bloque__titulo">Calificación de los clientes</h2>
      {calificacion.total === 0 ? (
        <p className="tp-bloque__vacio">
          Todavía ningún cliente te ha calificado. El cliente califica al confirmar que recibió su vehículo, así que
          aparecerá aquí después de tus primeras entregas. No mostramos un promedio hasta que exista.
        </p>
      ) : (
        <>
          <div className="tp-nota-grande">
            <strong>{calificacion.promedio.toLocaleString("es-CO", { minimumFractionDigits: 1 })}</strong>
            <Estrellas valor={calificacion.promedio} />
            <span>{calificacion.total} {calificacion.total === 1 ? "calificación" : "calificaciones"}</span>
          </div>
          {calificacion.total < MINIMO_REPRESENTATIVO && (
            <p className="tp-nota">Con pocas calificaciones el promedio todavía dice poco; se afina con cada entrega.</p>
          )}
          <ul className="tp-barras">
            {Object.entries(calificacion.distribucion).map(([estrellas, n]) => (
              <li key={estrellas} className="tp-barras__fila">
                <span className="tp-barras__nombre">{estrellas} ★</span>
                <span className="tp-barras__pista" aria-hidden="true">
                  <span className="tp-barras__barra" style={{ width: `${(n * 100) / tope}%` }} />
                </span>
                <span className="tp-barras__valor">{n}</span>
              </li>
            ))}
          </ul>
          {calificacion.recientes.length > 0 && (
            <ul className="tp-comentarios">
              {calificacion.recientes.map((c, i) => (
                <li key={`${c.fecha}-${i}`}>
                  <Estrellas valor={c.calificacion} />
                  <p>“{c.comentario}”</p>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}

function Cargando() {
  return (
    <div className="tp-esqueleto" aria-hidden="true">
      <div className="tp-esqueleto__fila">{[0, 1, 2, 3].map((i) => <div className="tp-esqueleto__tarjeta" key={i} />)}</div>
      <div className="tp-esqueleto__bloque" />
    </div>
  );
}

/** Informes del taller: lo que hizo, lo que ganó y cómo lo califican. Cada taller ve solo lo suyo. */
export default function TallerInformes() {
  const token = getToken();
  const [meses, setMeses] = useState(6);
  const [estado, setEstado] = useState({ meses: null, datos: null, error: null });

  useEffect(() => {
    let cancelado = false;
    // El pedido va en un timeout para no actualizar el estado dentro del efecto
    const id = setTimeout(() => {
      informesTaller(token, meses)
        .then((datos) => { if (!cancelado) setEstado({ meses, datos, error: null }); })
        .catch((e) => { if (!cancelado) setEstado({ meses, datos: null, error: e.message }); });
    }, 0);
    return () => { cancelado = true; clearTimeout(id); };
  }, [token, meses]);

  const cargando = estado.meses !== meses;
  const d = !cargando ? estado.datos : null;
  const entregados = d?.servicios.porEstado.ENTREGADA ?? 0;

  return (
    <>
      <header className="pt-header tp-cabecera">
        <div>
          <span className="pt-eyebrow">Taller aliado</span>
          <h1 className="pt-title">Informes</h1>
          <p className="pt-subtitle">Qué hiciste, cuánto te corresponde y cómo te califican tus clientes.</p>
        </div>
        <div className="tp-segmentos" role="group" aria-label="Periodo de los servicios y tiempos">
          {PERIODOS.map((m) => (
            <button key={m} type="button" className={`tp-segmentos__opcion${meses === m ? " tp-segmentos__opcion--activa" : ""}`}
              aria-pressed={meses === m} onClick={() => setMeses(m)}>
              {m} meses
            </button>
          ))}
        </div>
      </header>

      {!cargando && estado.error && <p className="pt-error" role="alert">{estado.error}</p>}
      {cargando && <Cargando />}

      {d && (
        <>
          <div className="tp-resumenes">
            <Resumen titulo="Servicios" valor={d.servicios.total} pie={`${entregados} entregados en ${d.mesesConsultados} meses`} />
            <Resumen titulo="Mano de obra liquidada" valor={formatPrecio(d.ingresos.manoDeObra.liquidado)}
              pie={`${formatPrecio(d.ingresos.manoDeObra.pendiente)} pendiente`} tono="ok" />
            <Resumen titulo="Respuesta promedio" valor={horas(d.tiempoRespuesta.horasPromedio)}
              pie={d.tiempoRespuesta.muestra > 0 ? `${d.tiempoRespuesta.muestra} medidas` : "Sin revisiones aún"} />
            <Resumen titulo="Calificación"
              valor={d.calificacion.promedio == null ? "—" : d.calificacion.promedio.toLocaleString("es-CO", { minimumFractionDigits: 1 })}
              pie={d.calificacion.total > 0 ? `${d.calificacion.total} de clientes` : "Sin calificaciones aún"} />
          </div>

          <div className="tp-informes">
            <ServiciosPorEstado servicios={d.servicios} />
            <Ingresos ingresos={d.ingresos} />
            <TiempoRespuesta tiempo={d.tiempoRespuesta} />
            <Calificacion calificacion={d.calificacion} />
          </div>
        </>
      )}
    </>
  );
}
