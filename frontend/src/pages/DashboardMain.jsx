import { useEffect, useState } from "react";
import { getDashboard } from "../services/reporteService";
import "../css/dashboard.css";

/* ─── Íconos SVG (sin emojis) ─────────────────────────────────────── */
const Ic = {
  trend: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
      <polyline points="17 6 23 6 23 12" />
    </svg>
  ),
  trendDwn: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <polyline points="23 18 13.5 8.5 8.5 13.5 1 6" />
      <polyline points="17 18 23 18 23 12" />
    </svg>
  ),
  cash: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <rect x="1" y="4" width="22" height="16" rx="2" />
      <line x1="1" y1="10" x2="23" y2="10" />
    </svg>
  ),
  chart: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <line x1="18" y1="20" x2="18" y2="10" />
      <line x1="12" y1="20" x2="12" y2="4" />
      <line x1="6" y1="20" x2="6" y2="14" />
    </svg>
  ),
  ticket: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
    </svg>
  ),
  box: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
    </svg>
  ),
  users: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="18"
      height="18"
    >
      <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" />
    </svg>
  ),
  warn: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="16"
      height="16"
    >
      <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  ),
  info: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="16"
      height="16"
    >
      <circle cx="12" cy="12" r="10" />
      <line x1="12" y1="8" x2="12" y2="12" />
      <line x1="12" y1="16" x2="12.01" y2="16" />
    </svg>
  ),
  star: (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      width="16"
      height="16"
    >
      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
    </svg>
  ),
};

/* ─── Gráfica de barras verticales (ventas por día) ────────────────── */
function BarChart({ data, color = "var(--primary)" }) {
  if (!data || !Object.keys(data).length)
    return <div className="db-chart-empty">Sin datos en este período</div>;
  const vals = Object.values(data);
  const max = Math.max(...vals, 1);
  return (
    <div className="db-barchart">
      {Object.entries(data).map(([dia, val]) => (
        <div key={dia} className="db-barchart-col">
          <div className="db-barchart-tooltip">
            ${(val || 0).toLocaleString("es-CO", { minimumFractionDigits: 0 })}
          </div>
          <div className="db-barchart-bar-wrap">
            <div
              className="db-barchart-bar"
              style={{
                height: `${Math.round((val / max) * 100)}%`,
                background: color,
              }}
            />
          </div>
          <span className="db-barchart-label">{dia}</span>
        </div>
      ))}
    </div>
  );
}

/* ─── Pie chart SVG ────────────────────────────────────────────────── */
function PieChart({ items }) {
  if (!items?.length || items.every((i) => i.val === 0))
    return <div className="db-chart-empty">Sin datos</div>;

  const total = items.reduce((s, i) => s + i.val, 0);
  const COLORS = [
    "var(--primary)",
    "var(--blue)",
    "var(--purple)",
    "var(--green)",
    "var(--amber)",
  ];
  const R = 60,
    CX = 70,
    CY = 70;

  let angle = -Math.PI / 2;
  const slices = items.map((item, idx) => {
    const pct = item.val / total;
    const start = angle;
    angle += pct * 2 * Math.PI;
    const x1 = CX + R * Math.cos(start);
    const y1 = CY + R * Math.sin(start);
    const x2 = CX + R * Math.cos(angle);
    const y2 = CY + R * Math.sin(angle);
    const large = pct > 0.5 ? 1 : 0;
    return {
      ...item,
      path: `M${CX},${CY} L${x1},${y1} A${R},${R} 0 ${large} 1 ${x2},${y2} Z`,
      color: COLORS[idx % COLORS.length],
      pct,
    };
  });

  return (
    <div className="db-pie-wrap">
      <svg viewBox="0 0 140 140" width="140" height="140">
        {slices.map((s, i) => (
          <path key={i} d={s.path} fill={s.color} opacity=".9" />
        ))}
        <circle cx={CX} cy={CY} r="35" fill="var(--surface)" />
      </svg>
      <div className="db-pie-legend">
        {slices.map((s, i) => (
          <div key={i} className="db-pie-legend-item">
            <span className="db-pie-dot" style={{ background: s.color }} />
            <span className="db-pie-lbl">{s.label}</span>
            <span className="db-pie-pct">{Math.round(s.pct * 100)}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─── Barra horizontal (top productos) ────────────────────────────── */
function HBarChart({ items }) {
  if (!items?.length)
    return <div className="db-chart-empty">Sin ventas registradas</div>;
  const max = Math.max(...items.map((i) => i.val), 1);
  return (
    <div className="db-hbar">
      {items.slice(0, 6).map((item, i) => (
        <div key={i} className="db-hbar-row">
          <span className="db-hbar-label" title={item.label}>
            {item.label}
          </span>
          <div className="db-hbar-bg">
            <div
              className="db-hbar-fill"
              style={{ width: `${Math.round((item.val / max) * 100)}%` }}
            />
          </div>
          <span className="db-hbar-val">{item.val}</span>
        </div>
      ))}
    </div>
  );
}

/* ─── COMPONENTE PRINCIPAL ─────────────────────────────────────────── */
function DashboardMain({ token }) {
  const [data, setData] = useState(null);
  const [ventas, setVentas] = useState([]);
  const [prods, setProds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const cargar = async () => {
      try {
        // Dashboard general
        const dash = await getDashboard(token);
        setData(dash);

        // Últimas ventas
        const rv = await fetch("http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/ventas", {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (rv.ok) {
          const vs = await rv.json();
          setVentas(
            vs
              .filter((v) => !["ANULADA", "CANCELADA"].includes(v.estado))
              .sort((a, b) => new Date(b.fecha) - new Date(a.fecha))
              .slice(0, 8),
          );
        }

        // Productos (para top vendidos)
        const rp = await fetch("http://${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api/productos", {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (rp.ok) setProds(await rp.json());
      } catch (e) {
        setError(e.message);
      } finally {
        setLoading(false);
      }
    };
    cargar();
  }, [token]);

  if (loading)
    return (
      <div className="db-root">
        <div className="db-loading">
          <div className="db-spinner" />
          Cargando dashboard...
        </div>
      </div>
    );
  if (error)
    return (
      <div className="db-root">
        <p className="db-error">{error}</p>
      </div>
    );

  const fmt = (n) =>
    (n || 0).toLocaleString("es-CO", { minimumFractionDigits: 2 });
  const fmtInt = (n) => (n || 0).toLocaleString("es-CO");
  const fmtDate = (f) =>
    f
      ? new Date(f).toLocaleDateString("es-CO", {
          day: "2-digit",
          month: "short",
          hour: "2-digit",
          minute: "2-digit",
        })
      : "—";
  const pctCls = (v) => (v > 0 ? "pos" : v < 0 ? "neg" : "neu");

  // ── Datos para gráficas ──
  const metodoPagoData = [
    {
      label: "Efectivo",
      val: ventas.filter((v) => v.metodoPago === "EFECTIVO").length,
    },
    {
      label: "Tarjeta",
      val: ventas.filter((v) => v.metodoPago === "TARJETA").length,
    },
    {
      label: "Transferencia",
      val: ventas.filter((v) => v.metodoPago === "TRANSFERENCIA").length,
    },
  ].filter((i) => i.val > 0);

  // Top productos: contar cuántas veces aparece cada producto en ventas
  const prodCount = {};
  ventas.forEach((v) => {
    v.productos?.forEach((d) => {
      prodCount[d.nombre] = (prodCount[d.nombre] || 0) + (d.cantidad || 1);
    });
  });
  const topProds = Object.entries(prodCount)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 6)
    .map(([label, val]) => ({ label, val }));

  // Ventas por día (últimos 7 días para la gráfica)
  const ventasPorDia = {};
  const hoy = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(hoy);
    d.setDate(d.getDate() - i);
    const key = d.toLocaleDateString("es-CO", {
      day: "2-digit",
      month: "2-digit",
    });
    ventasPorDia[key] = 0;
  }
  ventas.forEach((v) => {
    if (v.fecha) {
      const d = new Date(v.fecha);
      const key = d.toLocaleDateString("es-CO", {
        day: "2-digit",
        month: "2-digit",
      });
      if (key in ventasPorDia) ventasPorDia[key] += v.total || 0;
    }
  });

  // Alertas
  const stockCritico = prods.filter(
    (p) => (p.stockActual || 0) <= (p.stockMinimo || 0) && p.stockActual > 0,
  ).length;
  const agotados = prods.filter((p) => (p.stockActual || 0) === 0).length;
  const variacion = data?.variacionSemana || 0;

  const alertas = [];
  if (agotados > 0)
    alertas.push({
      tipo: "danger",
      msg: `${agotados} producto${agotados > 1 ? "s" : ""} agotado${agotados > 1 ? "s" : ""}. Reabastece ya.`,
    });
  if (stockCritico > 0)
    alertas.push({
      tipo: "warn",
      msg: `${stockCritico} producto${stockCritico > 1 ? "s" : ""} con stock crítico.`,
    });
  if (variacion < -5)
    alertas.push({
      tipo: "warn",
      msg: `Ventas bajaron ${Math.abs(variacion).toFixed(1)}% vs semana anterior.`,
    });
  if (variacion > 10)
    alertas.push({
      tipo: "info",
      msg: `Ventas subieron ${variacion.toFixed(1)}% respecto a la semana pasada.`,
    });

  const kpis = [
    {
      ico: Ic.cash,
      label: "Ventas del Día",
      val: `$${fmt(data?.totalVentasDia)}`,
      cls: "orange",
      delta: { label: "Hoy", cls: "neu" },
    },
    {
      ico: Ic.chart,
      label: "Ventas del Mes",
      val: `$${fmt(data?.totalVentasMes)}`,
      cls: "orange",
      delta: { label: "30 días", cls: "neu" },
    },
    {
      ico: Ic.ticket,
      label: "Transacciones",
      val: fmtInt(data?.totalTransacciones),
      cls: "",
      delta: { label: "Total", cls: "neu" },
    },
    {
      ico: Ic.trend,
      label: "Ticket Promedio",
      val: `$${fmt(data?.promedioTicket)}`,
      cls: "blue",
      delta: { label: "Avg", cls: "neu" },
    },
    {
      ico: variacion >= 0 ? Ic.trend : Ic.trendDwn,
      label: "Variación Semana",
      val: `${variacion >= 0 ? "+" : ""}${(variacion || 0).toFixed(1)}%`,
      cls: variacion >= 0 ? "green" : "red",
      delta: {
        label: variacion >= 0 ? "Sube" : "Baja",
        cls: pctCls(variacion),
      },
    },
    {
      ico: Ic.users,
      label: "Pago más usado",
      val: data?.metodoPagoMasUsado || "—",
      cls: "",
      delta: { label: "Top", cls: "neu" },
    },
  ];

  const METODO_ICO = { TARJETA: "💳", EFECTIVO: "💵", TRANSFERENCIA: "🏦" };
  const ESTADO_CSS = {
    PAGADA: "st-pagada",
    COMPLETADA: "st-pagada",
    PENDIENTE: "st-pendiente",
    CANCELADA: "st-cancelada",
    ANULADA: "st-anulada",
  };

  return (
    <div className="db-root">
      {/* Header */}
      <div className="db-header">
        <div>
          <h1 className="db-h1">Dashboard</h1>
          <p className="db-sub">Resumen general del negocio en tiempo real</p>
        </div>
        <span className="db-date">
          {new Date().toLocaleDateString("es-CO", {
            weekday: "long",
            day: "numeric",
            month: "long",
          })}
        </span>
      </div>

      {/* Alertas inteligentes */}
      {alertas.length > 0 && (
        <div className="db-alertas">
          {alertas.map((a, i) => (
            <div key={i} className={`db-alerta db-alerta--${a.tipo}`}>
              <span className="db-alerta-ico">
                {a.tipo === "info" ? Ic.info : Ic.warn}
              </span>
              {a.msg}
            </div>
          ))}
        </div>
      )}

      {/* KPIs */}
      <div className="db-kpi-grid">
        {kpis.map((k, i) => (
          <div className="db-kpi" key={i}>
            <div className="db-kpi-top">
              <span className="db-kpi-ico">{k.ico}</span>
              <span className={`db-kpi-delta ${k.delta.cls}`}>
                {k.delta.label}
              </span>
            </div>
            <p className="db-kpi-label">{k.label}</p>
            <p className={`db-kpi-val ${k.cls}`}>{k.val}</p>
          </div>
        ))}
      </div>

      {/* Highlights */}
      <div className="db-highlights">
        <div className="db-highlight">
          <span className="db-hl-ico">{Ic.star}</span>
          <div>
            <p className="db-hl-label">Producto más vendido</p>
            <p className="db-hl-val">{data?.productoMasVendido || "—"}</p>
          </div>
        </div>
        <div className="db-highlight">
          <span className="db-hl-ico">{Ic.users}</span>
          <div>
            <p className="db-hl-label">Cliente más importante</p>
            <p className="db-hl-val">{data?.clienteMasImportante || "—"}</p>
          </div>
        </div>
      </div>

      {/* Gráficas fila 1 */}
      <div className="db-charts-row">
        {/* Ventas por día */}
        <div className="db-card db-card--lg">
          <div className="db-card-header">
            <span className="db-card-title">Ventas últimos 7 días</span>
          </div>
          <BarChart data={ventasPorDia} />
        </div>

        {/* Métodos de pago */}
        <div className="db-card">
          <div className="db-card-header">
            <span className="db-card-title">Métodos de pago</span>
          </div>
          <PieChart items={metodoPagoData} />
        </div>
      </div>

      {/* Gráficas fila 2 */}
      <div className="db-charts-row">
        {/* Top productos */}
        <div className="db-card">
          <div className="db-card-header">
            <span className="db-card-title">Top productos vendidos</span>
          </div>
          <HBarChart items={topProds} />
        </div>

        {/* Estado inventario */}
        <div className="db-card">
          <div className="db-card-header">
            <span className="db-card-title">Estado del inventario</span>
          </div>
          <PieChart
            items={[
              {
                label: "Normal",
                val: prods.filter(
                  (p) => (p.stockActual || 0) > (p.stockMinimo || 0) * 2,
                ).length,
              },
              {
                label: "Bajo",
                val: prods.filter(
                  (p) =>
                    (p.stockActual || 0) <= (p.stockMinimo || 0) * 2 &&
                    (p.stockActual || 0) > (p.stockMinimo || 0),
                ).length,
              },
              { label: "Crítico", val: stockCritico },
              { label: "Agotado", val: agotados },
            ].filter((i) => i.val > 0)}
          />
        </div>
      </div>

      {/* Últimas ventas */}
      <div className="db-card db-card--full">
        <div className="db-card-header">
          <span className="db-card-title">Últimas ventas</span>
          <span className="db-card-sub">
            {ventas.length} registros recientes
          </span>
        </div>
        <div className="db-table-wrap">
          <table className="db-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Cliente</th>
                <th>Productos</th>
                <th>Método</th>
                <th>Estado</th>
                <th>Fecha</th>
                <th style={{ textAlign: "right" }}>Total</th>
              </tr>
            </thead>
            <tbody>
              {ventas.length === 0 ? (
                <tr>
                  <td colSpan={7} className="db-empty">
                    Sin ventas registradas
                  </td>
                </tr>
              ) : (
                ventas.map((v) => (
                  <tr key={v.id}>
                    <td className="db-id">#{String(v.id).toUpperCase()}</td>
                    <td className="db-cliente">
                      {v.clienteNombre || v.clienteId}
                    </td>
                    <td className="db-muted">
                      {v.productos?.length || 0} item
                      {(v.productos?.length || 0) !== 1 ? "s" : ""}
                    </td>
                    <td>
                      <span className="db-metodo">{v.metodoPago || "—"}</span>
                    </td>
                    <td>
                      <span
                        className={`db-estado ${ESTADO_CSS[v.estado?.toUpperCase()] || "st-pendiente"}`}
                      >
                        {v.estado || "—"}
                      </span>
                    </td>
                    <td className="db-muted db-fecha">{fmtDate(v.fecha)}</td>
                    <td className="db-total">${fmt(v.total)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default DashboardMain;
