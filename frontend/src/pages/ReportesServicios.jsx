import { useEffect, useState } from "react";
import { getReportesServicios } from "../services/reporteService";
import "../css/reportes.css";
import "../css/dashboard-plataforma.css";
import "../css/admin-base.css";

const PERIODOS = [
  { k: "dia", l: "Hoy" },
  { k: "semana", l: "Últimos 7 días" },
  { k: "mes", l: "Últimos 30 días" },
];

const fmt = (n) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);

const pct = (n) => `${Math.round(n || 0)}%`;

export default function ReportesServicios({ token }) {
  const [periodo, setPeriodo] = useState("mes");
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
  }, [periodo, token]);

  const cambiarPeriodo = (k) => {
    setError(null);
    setCargando(true);
    setPeriodo(k);
  };

  if (error) return <div className="adm-pagina rp-root--servicios"><p className="adm-alerta adm-alerta--error" role="alert">{error}</p></div>;

  const sinOrdenes = data && data.totalOrdenes === 0;
  const tasaCierre = data && data.ordenesFacturadas > 0
    ? (data.ordenesFinalizadas * 100) / data.ordenesFacturadas
    : 0;

  // El ranking se dibuja como barras: se lee el orden de un vistazo, sin tabular.
  const barrasTalleres = data?.talleresPreferidos?.slice(0, 6) || [];
  const maxTalleres = Math.max(...barrasTalleres.map((t) => t.ordenes), 1);
  const barrasProductos = data?.productosMasSolicitados?.slice(0, 6) || [];
  const maxProductos = Math.max(...barrasProductos.map((p) => p.ordenes), 1);

  return (
    <div className="adm-pagina rp-root--servicios">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Reporte de servicios</h1>
          <p className="adm-encabezado__sub">Órdenes de taller facturadas y estado del trabajo en cada una.</p>
        </div>
        <nav className="adm-pestanas" aria-label="Periodo">
          {PERIODOS.map((p) => (
            <button
              key={p.k}
              type="button"
              aria-pressed={periodo === p.k}
              className={`adm-pestana${periodo === p.k ? " adm-pestana--activa" : ""}`}
              onClick={() => cambiarPeriodo(p.k)}
            >
              {p.l}
            </button>
          ))}
        </nav>
      </header>

      {cargando ? (
        <div className="adm-tarjeta"><div className="adm-cargando">Cargando…</div></div>
      ) : sinOrdenes ? (
        <div className="adm-tarjeta">
          <p className="adm-vacio">No se registraron servicios de taller en este período.</p>
        </div>
      ) : (
        <>
          <div className="rp-kpi-grid">
            <div className="rp-kpi">
              <p className="rp-kpi-label">Órdenes creadas</p>
              <p className="rp-kpi-val">{data.totalOrdenes}</p>
              <p className="rp-kpi-sub">{data.ordenesFacturadas} ya facturadas</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">En proceso</p>
              <p className="rp-kpi-val orange">{data.ordenesEnProceso}</p>
              <p className="rp-kpi-sub">Talleres trabajando ahora</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Finalizadas</p>
              <p className="rp-kpi-val green">{data.ordenesFinalizadas}</p>
              <p className="rp-kpi-sub">{pct(tasaCierre)} de lo facturado</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Canceladas</p>
              <p className="rp-kpi-val red">{data.ordenesCanceladas}</p>
              <p className="rp-kpi-sub">No entraron a facturación</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Ingresos por servicios</p>
              <p className="rp-kpi-val" style={{ fontSize: "1.15rem" }}>{fmt(data.ingresosTotales)}</p>
              <p className="rp-kpi-sub">Repuestos y mano de obra</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Ticket promedio</p>
              <p className="rp-kpi-val" style={{ fontSize: "1.15rem" }}>{fmt(data.ticketPromedio)}</p>
              <p className="rp-kpi-sub">Por orden facturada</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Citas futuras</p>
              <p className="rp-kpi-val">{data.citasAgendadas}</p>
              <p className="rp-kpi-sub">Ingreso de vehículo agendado</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Esperando inicio</p>
              <p className="rp-kpi-val">{data.citasPendientes}</p>
              <p className="rp-kpi-sub">Cobradas, sin trabajar aún</p>
            </div>
            <div className="rp-kpi">
              <p className="rp-kpi-label">Con retraso</p>
              <p className={`rp-kpi-val${data.ordenesRetrasadas > 0 ? " red" : ""}`}>{data.ordenesRetrasadas}</p>
              <p className="rp-kpi-sub">Sin avance hace varios días</p>
            </div>
          </div>

          <div className="rp-two-col" style={{ marginTop: "1.25rem" }}>
            <section className="adm-tarjeta">
              <div className="adm-tarjeta__cabecera">
                <h2 className="adm-tarjeta__titulo">Talleres con más servicios</h2>
                <span className="adm-badge adm-badge--neutro">Órdenes vigentes</span>
              </div>
              <div className="rp-chart">
                {barrasTalleres.length === 0 ? (
                  <p className="adm-vacio">Sin datos</p>
                ) : barrasTalleres.map((t) => (
                  <div className="rp-chart-row" key={t.tallerId || t.nombre}>
                    <span className="rp-chart-label" title={t.nombre}>{t.nombre || "—"}</span>
                    <div className="rp-chart-bar-bg">
                      <div className="rp-chart-bar" style={{ width: `${(t.ordenes / maxTalleres) * 100}%` }}>
                        <span className="rp-chart-bar-val">{t.ordenes}</span>
                      </div>
                    </div>
                    <span className="rp-chart-label" style={{ width: 52, textAlign: "right" }}>
                      {pct(t.participacion)}
                    </span>
                  </div>
                ))}
              </div>
            </section>

            <section className="adm-tarjeta">
              <div className="adm-tarjeta__cabecera">
                <h2 className="adm-tarjeta__titulo">Repuestos más solicitados</h2>
                <span className="adm-badge adm-badge--neutro">Por orden</span>
              </div>
              <div className="rp-chart">
                {barrasProductos.length === 0 ? (
                  <p className="adm-vacio">Sin datos</p>
                ) : barrasProductos.map((p) => (
                  <div className="rp-chart-row" key={p.productoId || p.nombre}>
                    <span className="rp-chart-label" title={p.nombre}>{p.nombre || "—"}</span>
                    <div className="rp-chart-bar-bg">
                      <div className="rp-chart-bar" style={{ width: `${(p.ordenes / maxProductos) * 100}%` }}>
                        <span className="rp-chart-bar-val">{p.ordenes}</span>
                      </div>
                    </div>
                    <span className="rp-chart-label" style={{ width: 52, textAlign: "right" }}>
                      {pct((p.ordenes * 100) / (data.totalOrdenes || 1))}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
