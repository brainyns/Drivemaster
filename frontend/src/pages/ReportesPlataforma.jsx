import { useEffect, useState } from "react";
import {
  reporteVentasPlataforma, reporteProductosPlataforma, reporteClientesPlataforma,
  reporteServiciosPlataforma, reporteComisionPlataforma,
} from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";
import "../css/admin-base.css";

const cop = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);
const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);
const mesLargo = (m) => {
  const [anio, mes] = String(m).split("-");
  return new Date(Number(anio), Number(mes) - 1, 1).toLocaleDateString("es-CO", { month: "short", year: "2-digit" });
};

const PESTANAS = [
  { id: "ventas", titulo: "Ventas por tienda", periodo: true,
    nota: "Lo que vendieron las tiendas, con IVA y envío. No es ingreso de DriveMaster. No cuenta pedidos sin despachar ni sin entregar.",
    cargar: (token, meses) => reporteVentasPlataforma(token, { meses }) },
  { id: "productos", titulo: "Productos más vendidos", periodo: true,
    nota: "Unidades vendidas por producto en pedidos que se despacharon o se entregaron.",
    cargar: (token, meses) => reporteProductosPlataforma(token, { meses, limite: 20 }) },
  { id: "clientes", titulo: "Clientes con más compras", periodo: true,
    nota: "Compras pagadas del catálogo. Se muestra solo el nombre abreviado y la ciudad.",
    cargar: (token, meses) => reporteClientesPlataforma(token, { meses, limite: 20 }) },
  { id: "servicios", titulo: "Servicios por taller", periodo: false,
    nota: "Órdenes de servicio por taller y lo repartido a cada uno por sus pagos. No es ingreso de DriveMaster.",
    cargar: (token) => reporteServiciosPlataforma(token) },
  { id: "comision", titulo: "Comisión registrada", periodo: true,
    nota: "Informativa, calculada en cada pago aprobado con el porcentaje vigente y menos lo anulado por reembolsos. No es dinero cobrado.",
    cargar: (token, meses) => reporteComisionPlataforma(token, { meses }) },
];

function Tabla({ columnas, filas, vacio, pie }) {
  if (filas.length === 0) return <p className="adm-vacio">{vacio}</p>;
  return (
    <div className="adm-tabla-wrap">
      <table className="adm-tabla adm-tabla--tarjetas">
        <thead><tr>{columnas.map((c) => <th key={c.t} className={c.der ? "adm-tabla__der" : ""}>{c.t}</th>)}</tr></thead>
        <tbody>
          {filas.map((f, i) => (
            <tr key={f.id ?? i}>{columnas.map((c) => <td key={c.t} className={c.der ? "adm-tabla__der" : ""} data-label={c.t}>{c.f(f)}</td>)}</tr>
          ))}
        </tbody>
        {pie && <tfoot><tr>{pie.map((c, i) => <td key={i} className={c.der ? "adm-tabla__der" : ""}><strong>{c.v}</strong></td>)}</tr></tfoot>}
      </table>
    </div>
  );
}

function Contenido({ id, datos }) {
  if (id === "ventas") {
    const filas = datos.tiendas.map((t) => ({ ...t, id: t.tiendaId }));
    return (
      <Tabla vacio="Todavía no hay ventas en el período."
        columnas={[
          { t: "Tienda", f: (t) => t.tienda || "—" },
          ...datos.meses.map((m, i) => ({ t: mesLargo(m), der: true, f: (t) => cop(t.porMes[i].ventas) })),
          { t: "Pedidos", der: true, f: (t) => num(t.pedidos) },
          { t: "Total", der: true, f: (t) => <strong>{cop(t.ventas)}</strong> },
        ]}
        filas={filas}
        pie={filas.length ? [
          { v: "Todas las tiendas" },
          ...datos.totalesPorMes.map((m) => ({ v: cop(m.ventas), der: true })),
          { v: num(datos.totalesPorMes.reduce((s, m) => s + m.pedidos, 0)), der: true },
          { v: cop(datos.totalesPorMes.reduce((s, m) => s + m.ventas, 0)), der: true },
        ] : null} />
    );
  }
  if (id === "productos") {
    return (
      <Tabla vacio="Todavía no hay ventas en el período."
        filas={datos.productos.map((p) => ({ ...p, id: p.productoId }))}
        columnas={[{ t: "Producto", f: (p) => p.nombre || "—" }, { t: "Tienda", f: (p) => p.tienda || "—" },
          { t: "Unidades", der: true, f: (p) => num(p.unidades) }, { t: "Ventas", der: true, f: (p) => cop(p.ventas) }]} />
    );
  }
  if (id === "clientes") {
    return (
      <Tabla vacio="Todavía no hay compras en el período."
        filas={datos.clientes.map((c) => ({ ...c, id: c.clienteId }))}
        columnas={[{ t: "Cliente", f: (c) => c.cliente || "—" }, { t: "Ciudad", f: (c) => c.ciudad || "—" },
          { t: "Compras", der: true, f: (c) => num(c.compras) }, { t: "Total comprado", der: true, f: (c) => cop(c.total) }]} />
    );
  }
  if (id === "servicios") {
    return (
      <Tabla vacio="Todavía no hay órdenes de servicio."
        filas={datos.talleres.map((t) => ({ ...t, id: t.tallerId }))}
        columnas={[{ t: "Taller", f: (t) => t.taller || "—" }, { t: "Órdenes", der: true, f: (t) => num(t.total) },
          { t: "Entregadas", der: true, f: (t) => num(t.entregadas) }, { t: "En curso", der: true, f: (t) => num(t.enCurso) },
          { t: "Canceladas", der: true, f: (t) => num(t.canceladas) }, { t: "Repartido al taller", der: true, f: (t) => cop(t.movidoAlTaller) }]} />
    );
  }
  return (
    <>
      <div className="db-kpi-grid">
        <div className="db-kpi dp-kpi"><p className="db-kpi-label">Registrada</p><p className="db-kpi-val">{cop(datos.registrada)}</p></div>
        <div className="db-kpi dp-kpi"><p className="db-kpi-label">Anulada por reembolsos</p><p className="db-kpi-val">{cop(datos.anulada)}</p></div>
        <div className="db-kpi dp-kpi"><p className="db-kpi-label">Comisión neta</p><p className="db-kpi-val orange">{cop(datos.neta)}</p></div>
      </div>
      <Tabla vacio="Todavía no hay comisión registrada."
        filas={datos.meses.map((m) => ({ ...m, id: m.mes }))}
        columnas={[{ t: "Mes", f: (m) => mesLargo(m.mes) }, { t: "Registrada", der: true, f: (m) => cop(m.registrada) },
          { t: "Anulada", der: true, f: (m) => cop(m.anulada) }, { t: "Neta", der: true, f: (m) => <strong>{cop(m.neta)}</strong> }]} />
    </>
  );
}

/**
 * Reportes de la plataforma (solo SUPERADMIN). Se calculan sobre los pedidos de las tiendas, los pagos y las
 * órdenes de servicio; no sobre las ventas del flujo anterior (eso está en "Historial anterior").
 */
export default function ReportesPlataforma({ token }) {
  const [pestana, setPestana] = useState("ventas");
  const [meses, setMeses] = useState(12);
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });

  const def = PESTANAS.find((p) => p.id === pestana);
  const clave = `${pestana}|${def.periodo ? meses : 0}`;

  useEffect(() => {
    let vivo = true;
    def.cargar(token, meses)
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setEstado({ clave, datos: null, error: e.message }); });
    return () => { vivo = false; };
  }, [token, def, meses, clave]);

  const cargando = estado.clave !== clave;

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Reportes de la plataforma</h1>
          <p className="adm-encabezado__sub">Calculados sobre los pedidos de las tiendas, los pagos y las órdenes de servicio.</p>
        </div>
      </header>

      <div className="adm-pestanas" role="tablist" aria-label="Reportes">
        {PESTANAS.map((p) => (
          <button key={p.id} type="button" role="tab" aria-selected={pestana === p.id}
            className={`adm-pestana${pestana === p.id ? " adm-pestana--activa" : ""}`} onClick={() => setPestana(p.id)}>{p.titulo}</button>
        ))}
      </div>

      <div className="adm-filtros">
        {def.periodo && (
          <label className="adm-campo">
            <span>Período</span>
            <select value={meses} onChange={(e) => setMeses(Number(e.target.value))}>
              <option value={3}>Últimos 3 meses</option>
              <option value={6}>Últimos 6 meses</option>
              <option value={12}>Últimos 12 meses</option>
              <option value={24}>Últimos 24 meses</option>
            </select>
          </label>
        )}
        <p className="dp-nota">{def.nota}</p>
      </div>

      {estado.error && !cargando && <div className="adm-alerta adm-alerta--error" role="alert">{estado.error}</div>}

      <div className="adm-tarjeta" aria-busy={cargando}>
        {cargando ? (
          <div className="adm-cargando">Calculando…</div>
        ) : estado.datos ? <Contenido id={pestana} datos={estado.datos} /> : null}
      </div>
    </div>
  );
}
