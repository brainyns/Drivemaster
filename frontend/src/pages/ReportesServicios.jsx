import { useEffect, useState } from "react";
import { getReportesServicios } from "../services/reporteService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import BarraProgreso from "../components/ui/BarraProgreso";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";

const PERIODOS = [
  { k: "dia", l: "Hoy" },
  { k: "semana", l: "Últimos 7 días" },
  { k: "mes", l: "Últimos 30 días" },
];

const fmt = (n) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);

const pct = (n) => `${Math.round(n || 0)}%`;

/** Ranking como barras: se lee el orden de un vistazo, sin tabular. La barra es relativa al primero. */
function Ranking({ id, titulo, nota, filas }) {
  const max = Math.max(...filas.map((f) => f.n), 1);
  return (
    <section className="pp-bloque" aria-labelledby={id}>
      <div className="pp-bloque__cabecera">
        <h2 id={id} className="pp-bloque__titulo">{titulo}</h2>
        <span className="pp-bloque__nota">{nota}</span>
      </div>
      {filas.length === 0 ? <p className="pp-bloque__vacio">Sin datos en este período.</p> : (
        <ul className="pp-barras">
          {filas.map((f) => (
            <li key={f.clave} className="pp-barras__fila pp-barras__fila--ranking">
              <span className="pp-barras__etiqueta" title={f.nombre}>{f.nombre || "Sin nombre"}</span>
              <BarraProgreso valor={(f.n / max) * 100} etiqueta={`${f.nombre || "Sin nombre"}: ${f.n} órdenes`} />
              <strong>{f.n}</strong>
              <span className="pp-tenue">{pct(f.participacion)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default function ReportesServicios({ token }) {
  const [periodo, setPeriodo] = useState("mes");
  const [version, setVersion] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    let cancelado = false;
    getReportesServicios(token, periodo)
      .then((d) => { if (!cancelado) setData(d); })
      .catch((e) => { if (!cancelado) setError(e.message); })
      .finally(() => { if (!cancelado) setCargando(false); });
    return () => { cancelado = true; };
  }, [periodo, token, version]);

  const cambiarPeriodo = (k) => {
    setError(null);
    setCargando(true);
    setPeriodo(k);
  };
  const reintentar = () => { setError(null); setCargando(true); setVersion((v) => v + 1); };

  const sinOrdenes = data && data.totalOrdenes === 0;
  const tasaCierre = data && data.ordenesFacturadas > 0
    ? (data.ordenesFinalizadas * 100) / data.ordenesFacturadas
    : 0;

  const talleres = (data?.talleresPreferidos?.slice(0, 6) || [])
    .map((t) => ({ clave: t.tallerId || t.nombre, nombre: t.nombre, n: t.ordenes, participacion: t.participacion }));
  const productos = (data?.productosMasSolicitados?.slice(0, 6) || [])
    .map((p) => ({ clave: p.productoId || p.nombre, nombre: p.nombre, n: p.ordenes, participacion: (p.ordenes * 100) / (data.totalOrdenes || 1) }));

  return (
    <div className="ss-panel--plataforma pp-root">
      <EncabezadoPagina titulo="Reporte de servicios"
        descripcion="Órdenes de taller facturadas y estado del trabajo en cada una." />

      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Periodo">
        {PERIODOS.map((p) => (
          <button key={p.k} type="button" className="ui-chip" aria-pressed={periodo === p.k} onClick={() => cambiarPeriodo(p.k)}>{p.l}</button>
        ))}
      </div>

      {error ? (
        <Alerta onReintentar={reintentar}>{error}</Alerta>
      ) : cargando ? (
        <>
          <div className="ui-indicadores pp-seccion" aria-hidden="true">
            {[0, 1, 2, 3].map((i) => <span key={i} className="ui-skeleton pp-esqueleto-indicador" />)}
          </div>
          <Esqueleto variante="lista" cantidad={3} texto="Calculando el reporte…" />
        </>
      ) : sinOrdenes ? (
        <EstadoVacio icono="tool" titulo="Sin servicios en este período"
          texto="No se registraron servicios de taller. Prueba con un período más largo." />
      ) : (
        <>
          <section className="pp-seccion" aria-labelledby="rsv-ordenes">
            <h2 id="rsv-ordenes" className="pp-titulo">Órdenes</h2>
            <div className="ui-indicadores">
              <Indicador titulo="Órdenes creadas" valor={data.totalOrdenes} pie={`${data.ordenesFacturadas} ya facturadas`} />
              <Indicador titulo="En proceso" valor={data.ordenesEnProceso} pie="Talleres trabajando ahora" />
              <Indicador titulo="Finalizadas" valor={data.ordenesFinalizadas} tono="ok" pie={`${pct(tasaCierre)} de lo facturado`} />
              <Indicador titulo="Canceladas" valor={data.ordenesCanceladas} pie="No entraron a facturación" />
            </div>
          </section>

          <section className="pp-seccion" aria-labelledby="rsv-dinero">
            <h2 id="rsv-dinero" className="pp-titulo">Dinero</h2>
            <div className="ui-indicadores">
              <Indicador titulo="Ingresos por servicios" valor={fmt(data.ingresosTotales)} pie="Repuestos y mano de obra" />
              <Indicador titulo="Ticket promedio" valor={fmt(data.ticketPromedio)} pie="Por orden facturada" />
            </div>
          </section>

          <section className="pp-seccion" aria-labelledby="rsv-agenda">
            <h2 id="rsv-agenda" className="pp-titulo">Agenda</h2>
            <div className="ui-indicadores">
              <Indicador icono="calendar" titulo="Citas futuras" valor={data.citasAgendadas} pie="Ingreso de vehículo agendado" />
              <Indicador titulo="Esperando inicio" valor={data.citasPendientes} pie="Cobradas, sin trabajar aún" />
              <Indicador icono="clock" titulo="Con retraso" valor={data.ordenesRetrasadas}
                tono={data.ordenesRetrasadas > 0 ? "accion" : undefined} pie="Sin avance hace varios días" />
            </div>
          </section>

          <div className="pp-columnas">
            <Ranking id="rsv-talleres" titulo="Talleres con más servicios" nota="Órdenes vigentes" filas={talleres} />
            <Ranking id="rsv-productos" titulo="Repuestos más solicitados" nota="Por orden" filas={productos} />
          </div>
        </>
      )}
    </div>
  );
}
