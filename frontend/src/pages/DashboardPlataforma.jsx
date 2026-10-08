import { useCallback, useEffect, useState } from "react";
import { dashboardPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";

const REFRESCO_MS = 60000;

const cop = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);
const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);

const ESTADOS_ORDEN = {
  ENVIADA: "Enviada", ASIGNADA_A_TALLER: "Asignada a taller", REASIGNADA: "Reasignada", EN_REVISION: "En revisión",
  DIAGNOSTICADA: "Diagnosticada", COTIZADA: "Cotizada", APROBADA: "Aprobada", PAGADA: "Pagada",
  REPUESTOS_PEDIDOS: "Repuestos pedidos", REPUESTOS_RECIBIDOS: "Repuestos recibidos", EN_INSTALACION: "En instalación",
  LISTA: "Lista", ENTREGADA: "Entregada", RECHAZADA: "Rechazada", CANCELADA: "Cancelada",
};
const ORDEN_ESTADOS = Object.keys(ESTADOS_ORDEN);

function Kpi({ etiqueta, valor, detalle, tono }) {
  return (
    <div className="db-kpi dp-kpi">
      <p className="db-kpi-label">{etiqueta}</p>
      <p className={`db-kpi-val ${tono || ""}`}>{valor}</p>
      {detalle && <p className="dp-kpi__detalle">{detalle}</p>}
    </div>
  );
}

function Tabla({ titulo, vacio, filas, columnas }) {
  return (
    <div className="db-card dp-tabla">
      <h2 className="db-card-title">{titulo}</h2>
      {filas.length === 0 ? (
        <p className="db-empty">{vacio}</p>
      ) : (
        <div className="db-table-wrap">
          <table className="db-table">
            <thead><tr>{columnas.map((c) => <th key={c.k} className={c.der ? "dp-der" : ""}>{c.t}</th>)}</tr></thead>
            <tbody>
              {filas.map((f, i) => (
                <tr key={f.id ?? i}>
                  {columnas.map((c) => <td key={c.k} className={c.der ? "dp-der" : ""}>{c.f ? c.f(f) : f[c.k]}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * Panel de la plataforma (SUPERADMIN). DriveMaster administra el marketplace pero no vende: el dinero que pagan
 * los clientes se reparte entre talleres y tiendas. Por eso se muestra separado lo que "movió" la plataforma
 * (no es suyo) de la comisión registrada (informativa) y de las suscripciones.
 */
export default function DashboardPlataforma({ token, onIr }) {
  const [estado, setEstado] = useState({ datos: null, error: null });
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    dashboardPlataforma(token)
      .then((datos) => setEstado({ datos, error: null }))
      .catch((e) => setEstado((previo) => ({ datos: previo.datos, error: e.message })))
      .finally(() => setCargando(false));
  }, [token]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const intervalo = setInterval(cargar, REFRESCO_MS);
    return () => { clearTimeout(inicial); clearInterval(intervalo); };
  }, [cargar]);

  const { datos: d, error } = estado;

  if (!d) {
    return (
      <div className="db-root">
        {error ? <div className="db-error" role="alert">{error}</div>
          : <div className="db-loading"><div className="db-spinner" /> Cargando el panel…</div>}
      </div>
    );
  }

  const a = d.alertas;
  const alertas = [
    { n: a.negociosPorAprobar, texto: "negocios por aprobar", ir: "aprobaciones" },
    { n: a.despachosAtrasados, texto: "despachos atrasados", ir: "pedidos-envios", preset: "atrasados" },
    { n: a.problemasEnvioAbiertos, texto: "problemas de envío abiertos", ir: "problemas-envios" },
    { n: a.liquidacionesPendientes, texto: "liquidaciones por pagar", ir: "liquidaciones" },
    { n: a.liquidacionesFallidas, texto: "liquidaciones fallidas", ir: "liquidaciones", critica: true },
    { n: a.reembolsosPendientes, texto: `reembolsos pendientes (${cop(a.montoReembolsosPendientes)})`, ir: "liquidaciones", critica: true },
  ].filter((x) => x.n > 0);

  const estadosOrdenes = [
    ...ORDEN_ESTADOS.filter((e) => d.ordenesPorEstado[e]),
    ...Object.keys(d.ordenesPorEstado).filter((e) => !ESTADOS_ORDEN[e]),
  ];
  const totalOrdenes = estadosOrdenes.reduce((s, e) => s + d.ordenesPorEstado[e], 0);
  const planes = Object.entries(d.suscripcionesPorPlan);
  const totalSuscripciones = planes.reduce((s, [, n]) => s + n, 0);

  return (
    <div className="db-root dp-root">
      <header className="db-header">
        <div>
          <h1 className="db-h1">Panel de la plataforma</h1>
          <p className="db-sub">
            DriveMaster administra el marketplace, no vende. Sus ingresos son la comisión y las suscripciones;
            lo que pagan los clientes es de los talleres y las tiendas.
          </p>
        </div>
        <div className="db-header-right dp-acciones">
          <span className="db-date">
            Actualizado {new Date(d.generadoEn).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" })}
          </span>
          <button type="button" className="dp-btn" onClick={cargar} disabled={cargando}>
            {cargando ? "Actualizando…" : "Actualizar"}
          </button>
        </div>
      </header>

      {error && <div className="db-error" role="alert">{error} (se muestran los últimos datos)</div>}

      <section aria-label="Alertas" className="dp-alertas">
        {alertas.length === 0 ? (
          <p className="dp-alertas__ok">Sin alertas: no hay despachos atrasados, problemas abiertos ni pagos pendientes.</p>
        ) : alertas.map((x) => (
          <button key={x.texto} type="button" className={`dp-alerta${x.critica ? " dp-alerta--critica" : ""}`}
            onClick={() => onIr?.(x.ir, x.preset)}>
            <strong>{num(x.n)}</strong> {x.texto}
          </button>
        ))}
      </section>

      <h2 className="dp-seccion">Plataforma</h2>
      <div className="db-kpi-grid">
        <Kpi etiqueta="Tiendas registradas" valor={num(d.tiendas.registradas)}
          detalle={`${num(d.tiendas.aprobadas)} aprobadas · ${num(d.tiendas.pendientes)} pendientes`} />
        <Kpi etiqueta="Talleres" valor={num(d.talleres.registradas)}
          detalle={`${num(d.talleres.aprobadas)} aprobados · ${num(d.talleres.pendientes)} pendientes`} />
        <Kpi etiqueta="Clientes" valor={num(d.clientes.total)}
          detalle={`+${num(d.clientes.nuevos7Dias)} en 7 días · +${num(d.clientes.nuevos30Dias)} en 30`} />
        <Kpi etiqueta="Productos" valor={num(d.productos.total)}
          detalle={`${num(d.productos.nuevos7Dias)} nuevos en 7 días · ${num(d.productos.nuevos30Dias)} en 30`} />
      </div>

      <h2 className="dp-seccion">Dinero</h2>
      <div className="db-kpi-grid">
        <Kpi etiqueta="Total movido" valor={cop(d.dinero.movido)} detalle="Pagos aprobados que pasaron por la plataforma. No es ingreso de DriveMaster." />
        <Kpi etiqueta="Comisión registrada" tono="orange" valor={cop(d.dinero.comisionRegistrada)}
          detalle={`Informativa, al ${d.dinero.comisionPct}% vigente en cada pago. No es dinero cobrado.`} />
        <Kpi etiqueta="Por liquidar a negocios" valor={cop(d.dinero.porLiquidar)} detalle="Ya habilitado para pagar a talleres y tiendas." />
      </div>

      <div className="dp-dos">
        <Tabla titulo="Productos más vendidos" vacio="Todavía no hay ventas."
          filas={d.productosMasVendidos.map((p) => ({ ...p, id: p.productoId }))}
          columnas={[{ k: "nombre", t: "Producto" }, { k: "tienda", t: "Tienda" },
            { k: "unidades", t: "Unid.", der: true, f: (p) => num(p.unidades) },
            { k: "ventas", t: "Ventas", der: true, f: (p) => cop(p.ventas) }]} />
        <Tabla titulo="Tiendas más vendedoras" vacio="Todavía no hay ventas."
          filas={d.tiendasMasVendedoras.map((t) => ({ ...t, id: t.tiendaId }))}
          columnas={[{ k: "nombre", t: "Tienda" },
            { k: "pedidos", t: "Pedidos", der: true, f: (t) => num(t.pedidos) },
            { k: "ventas", t: "Ventas", der: true, f: (t) => cop(t.ventas) }]} />
      </div>

      <div className="dp-dos">
        <div className="db-card">
          <h2 className="db-card-title">Órdenes de servicio por estado</h2>
          {estadosOrdenes.length === 0 ? <p className="db-empty">Todavía no hay órdenes de servicio.</p> : (
            <ul className="dp-barras">
              {estadosOrdenes.map((e) => (
                <li key={e}>
                  <span>{ESTADOS_ORDEN[e] || e}</span>
                  <span className="dp-barras__pista" aria-hidden="true">
                    <span style={{ width: `${Math.max(4, (d.ordenesPorEstado[e] / totalOrdenes) * 100)}%` }} />
                  </span>
                  <strong>{num(d.ordenesPorEstado[e])}</strong>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="db-card">
          <h2 className="db-card-title">Suscripciones activas por plan</h2>
          {planes.length === 0 ? <p className="db-empty">Todavía no hay suscripciones activas.</p> : (
            <ul className="dp-barras">
              {planes.map(([plan, n]) => (
                <li key={plan}>
                  <span>{plan}</span>
                  <span className="dp-barras__pista" aria-hidden="true">
                    <span style={{ width: `${Math.max(4, (n / totalSuscripciones) * 100)}%` }} />
                  </span>
                  <strong>{num(n)}</strong>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
