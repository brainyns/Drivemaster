import { useEffect, useState } from "react";
import { getReportesClientes } from "../services/reporteService";

const AVATARES = ["#2563eb","#7c3aed","#db2777","#059669","#d97706","#dc2626","#0891b2"];
const getColor = (n) => n ? AVATARES[n.charCodeAt(0) % AVATARES.length] : AVATARES[0];
const getIniciales = (n) => n ? n.split(" ").map(x=>x[0]).join("").toUpperCase().slice(0,2) : "?";

const PERIODOS = [
  { k: "dia",    l: "Hoy" },
  { k: "semana", l: "Semana" },
  { k: "mes",    l: "Mes" },
];

function BarChart({ data }) {
  if (!data || Object.keys(data).length === 0) return (
    <div className="rp-empty-state"><span className="icon">📊</span><p>Sin datos</p></div>
  );
  const max = Math.max(...Object.values(data), 1);
  return (
    <div className="rp-linechart">
      <div className="rp-linechart-bars">
        {Object.entries(data).map(([dia, val]) => (
          <div key={dia} className="rp-linechart-col">
            <div
              className="rp-linechart-bar"
              style={{ height: `${Math.round((val / max) * 100)}%` }}
              title={`$${val.toLocaleString("es-CO",{minimumFractionDigits:2})}`}
            />
            <span className="rp-linechart-day">{dia}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function RankingChart({ clientes }) {
  if (!clientes?.length) return (
    <div className="rp-empty-state"><span className="icon">🏆</span><p>Sin datos</p></div>
  );
  const max = Math.max(...clientes.map(c => c.totalGastado), 1);
  return (
    <div className="rp-chart">
      {clientes.slice(0, 8).map((c, i) => (
        <div key={c.id} className="rp-chart-row">
          <span className="rp-chart-label" title={c.nombre}>{c.nombre}</span>
          <div className="rp-chart-bar-bg">
            <div
              className="rp-chart-bar"
              style={{ width: `${Math.round((c.totalGastado / max) * 100)}%` }}
            >
              <span className="rp-chart-bar-val">
                ${c.totalGastado.toLocaleString("es-CO",{minimumFractionDigits:0})}
              </span>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function ReportesClientes({ token }) {
  const [periodo,  setPeriodo]  = useState("mes");
  const [data,     setData]     = useState(null);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState(null);

  const cargar = (p) => {
    setLoading(true); setError(null);
    getReportesClientes(token, p)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { cargar(periodo); }, [periodo]);

  if (loading) return <div className="rp-loading"><div className="rp-spinner" />Cargando reportes...</div>;
  if (error)   return <p className="rp-error">⚠ {error}</p>;
  if (!data)   return null;

  const fmt = (n) => (n||0).toLocaleString("es-CO",{minimumFractionDigits:2});

  return (
    <>
      {/* Filtro período */}
      <div style={{ display:"flex", alignItems:"center", gap:".75rem", marginBottom:"1rem" }}>
        <span style={{ fontSize:".78rem", color:"var(--muted)", fontWeight:600 }}>Período:</span>
        <div className="rp-period-tabs">
          {PERIODOS.map(p => (
            <button
              key={p.k}
              className={`rp-period-tab${periodo === p.k ? " active" : ""}`}
              onClick={() => setPeriodo(p.k)}
            >{p.l}</button>
          ))}
        </div>
      </div>

      {/* KPIs */}
      <div className="rp-kpi-grid" style={{ marginBottom:"1rem" }}>
        <div className="rp-kpi">
          <div className="rp-kpi-top"><span className="rp-kpi-ico">💰</span></div>
          <p className="rp-kpi-label">Total Recaudado</p>
          <p className="rp-kpi-val orange">${fmt(data.totalRecaudado)}</p>
        </div>
        <div className="rp-kpi">
          <div className="rp-kpi-top"><span className="rp-kpi-ico">🏆</span></div>
          <p className="rp-kpi-label">Más compró hoy</p>
          <p className="rp-kpi-val" style={{ fontSize:"1rem" }}>{data.clienteMasComproHoy || "—"}</p>
        </div>
        <div className="rp-kpi">
          <div className="rp-kpi-top"><span className="rp-kpi-ico">📅</span></div>
          <p className="rp-kpi-label">Más compró semana</p>
          <p className="rp-kpi-val" style={{ fontSize:"1rem" }}>{data.clienteMasComproSemana || "—"}</p>
        </div>
        <div className="rp-kpi">
          <div className="rp-kpi-top"><span className="rp-kpi-ico">😴</span></div>
          <p className="rp-kpi-label">Clientes inactivos</p>
          <p className="rp-kpi-val red">{data.clientesInactivos?.length || 0}</p>
        </div>
      </div>

      <div className="rp-two-col">
        {/* Gráfica de ventas por día */}
        <div className="rp-panel">
          <div className="rp-panel-header">
            <span className="rp-panel-title">📈 Ventas últimos 7 días</span>
          </div>
          <BarChart data={data.ventasPorDia} />
        </div>

        {/* Top clientes gráfica */}
        <div className="rp-panel">
          <div className="rp-panel-header">
            <span className="rp-panel-title">🏆 Ranking por gasto</span>
          </div>
          <RankingChart clientes={data.rankingClientes} />
        </div>
      </div>

      {/* Tabla ranking */}
      <div className="rp-panel" style={{ marginBottom:"1rem" }}>
        <div className="rp-panel-header">
          <span className="rp-panel-title">⭐ Top Clientes — {periodo === "dia" ? "Hoy" : periodo === "semana" ? "Esta Semana" : "Este Mes"}</span>
        </div>
        <div className="rp-table-wrap">
          <table className="rp-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Cliente</th>
                <th>Compras</th>
                <th style={{ textAlign:"right" }}>Total Gastado</th>
                <th>Promedio x Compra</th>
                <th>Clasificación</th>
              </tr>
            </thead>
            <tbody>
              {!data.rankingClientes?.length ? (
                <tr><td colSpan={6} className="rp-empty">Sin datos en este período</td></tr>
              ) : data.rankingClientes.map((c, i) => {
                const rankCls = i === 0 ? "gold" : i === 1 ? "silver" : i === 2 ? "bronze" : "normal";
                const promedio = c.totalCompras > 0 ? c.totalGastado / c.totalCompras : 0;
                return (
                  <tr key={c.id}>
                    <td><span className={`rp-rank ${rankCls}`}>{i+1}</span></td>
                    <td>
                      <div className="rp-avatar-cell">
                        <div className="rp-avatar-ico" style={{ background: getColor(c.nombre) }}>
                          {getIniciales(c.nombre)}
                        </div>
                        <div>
                          <p className="rp-avatar-name">{c.nombre}</p>
                          <p className="rp-avatar-sub">{c.identificacion}</p>
                        </div>
                      </div>
                    </td>
                    <td style={{ fontWeight:700 }}>{c.totalCompras}</td>
                    <td style={{ textAlign:"right", fontWeight:800, color:"var(--primary)" }}>
                      ${fmt(c.totalGastado)}
                    </td>
                    <td style={{ color:"var(--muted)", fontSize:".78rem" }}>
                      ${fmt(promedio)}
                    </td>
                    <td>
                      <span className={`rp-badge ${c.clasificacion?.toLowerCase()}`}>
                        {c.clasificacion}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Clientes inactivos */}
      {data.clientesInactivos?.length > 0 && (
        <div className="rp-panel">
          <div className="rp-panel-header">
            <span className="rp-panel-title">😴 Clientes Inactivos (+30 días sin comprar)</span>
          </div>
          <div className="rp-inactivos">
            {data.clientesInactivos.slice(0,10).map(c => (
              <div key={c.id} className="rp-inactivo-item">
                <div className="rp-avatar-ico" style={{ width:30, height:30, borderRadius:"50%", background:"var(--surface3)", display:"grid", placeItems:"center", fontSize:".7rem", fontWeight:800, color:"var(--muted)" }}>
                  {getIniciales(c.nombre)}
                </div>
                <span className="rp-inactivo-name">{c.nombre}</span>
                <span className="rp-inactivo-fecha">Última compra: {c.ultimaCompra}</span>
                <span className="rp-inactivo-dias">{c.diasSinComprar} días sin comprar</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </>
  );
}

export default ReportesClientes;