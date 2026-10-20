import { useCallback, useEffect, useState } from "react";
import { dashboardPlataforma } from "../services/plataformaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import BarraProgreso from "../components/ui/BarraProgreso";
import Icon from "../components/public/Icon";
import { barras, urgentesDashboard } from "./admin/plataformaAdmin";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";

const REFRESCO_MS = 60000;

const cop = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);
const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);
const hora = (iso) => new Date(iso).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" });

const ESTADOS_ORDEN = {
  ENVIADA: "Enviada", ASIGNADA_A_TALLER: "Asignada a taller", REASIGNADA: "Reasignada", EN_REVISION: "En revisión",
  DIAGNOSTICADA: "Diagnosticada", COTIZADA: "Cotizada", APROBADA: "Aprobada", PAGADA: "Pagada",
  REPUESTOS_PEDIDOS: "Repuestos pedidos", REPUESTOS_RECIBIDOS: "Repuestos recibidos", EN_INSTALACION: "En instalación",
  LISTA: "Lista", ENTREGADA: "Entregada", RECHAZADA: "Rechazada", CANCELADA: "Cancelada",
};

/** Ranking de ventas: tabla en escritorio, tarjetas en celular. */
function Ranking({ id, titulo, vacio, filas, columnas }) {
  return (
    <section className="pp-bloque" aria-labelledby={id}>
      <h2 id={id} className="pp-bloque__titulo">{titulo}</h2>
      {filas.length === 0 ? (
        <p className="pp-bloque__vacio">{vacio}</p>
      ) : (
        <div className="ui-table-wrap">
          <table className="ui-table ui-table--tarjetas">
            <thead>
              <tr>{columnas.map((c) => <th key={c.k} scope="col" className={c.num ? "pp-num" : undefined}>{c.t}</th>)}</tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.id}>
                  {columnas.map((c, i) => (
                    <td key={c.k} data-label={i === 0 ? undefined : c.t}
                      className={i === 0 ? "ui-table__titulo" : c.num ? "pp-num" : undefined}>
                      {c.f ? c.f(f) : f[c.k] || <span className="pp-tenue">Sin nombre</span>}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/** Lista de barras con su número; la barra es BarraProgreso (parte del total). */
function Barras({ id, titulo, vacio, filas }) {
  return (
    <section className="pp-bloque" aria-labelledby={id}>
      <h2 id={id} className="pp-bloque__titulo">{titulo}</h2>
      {filas.length === 0 ? <p className="pp-bloque__vacio">{vacio}</p> : (
        <ul className="pp-barras">
          {filas.map((f) => (
            <li key={f.clave} className="pp-barras__fila">
              <span>{f.etiqueta}</span>
              <BarraProgreso valor={Math.max(2, f.pct)} etiqueta={`${f.etiqueta}: ${num(f.n)}`} />
              <strong>{num(f.n)}</strong>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * Panel de la plataforma (SUPERADMIN). DriveMaster administra el marketplace pero no vende: el dinero que pagan
 * los clientes se reparte entre talleres y tiendas. Primero lo que pide atención, con acceso directo a cada lista;
 * después las cifras, separando lo que "movió" la plataforma (no es suyo) de la comisión registrada (informativa).
 */
export default function DashboardPlataforma({ token, onIr }) {
  const [estado, setEstado] = useState({ datos: null, error: null });
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    return dashboardPlataforma(token)
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
  const ir = (pagina, preset) => onIr?.(pagina, preset);

  const encabezado = (
    <EncabezadoPagina titulo="Panel de la plataforma"
      descripcion="DriveMaster administra el marketplace, no vende: sus ingresos son la comisión y las suscripciones."
      acciones={(
        <>
          {d && <span className="pp-actualizado">Actualizado a las {hora(d.generadoEn)}</span>}
          <button type="button" className="ui-btn ui-btn--secundario" onClick={cargar} disabled={cargando} aria-busy={cargando}>
            <Icon name="refresh" size={16} /> {cargando && d ? "Actualizando…" : "Actualizar"}
          </button>
        </>
      )} />
  );

  if (!d) {
    return (
      <div className="ss-panel--plataforma pp-root">
        {encabezado}
        {error ? (
          <Alerta onReintentar={cargar} reintentando={cargando}>{error}</Alerta>
        ) : (
          <>
            <div className="ui-indicadores pp-seccion" aria-hidden="true">
              {[0, 1, 2, 3].map((i) => <span key={i} className="ui-skeleton pp-esqueleto-indicador" />)}
            </div>
            <Esqueleto variante="lista" cantidad={3} texto="Cargando el panel…" />
          </>
        )}
      </div>
    );
  }

  const urgentes = urgentesDashboard(d.alertas);
  const ordenes = barras(d.ordenesPorEstado, ESTADOS_ORDEN, Object.keys(ESTADOS_ORDEN));
  const planes = barras(d.suscripcionesPorPlan);

  return (
    <div className="ss-panel--plataforma pp-root">
      {encabezado}

      {error && (
        <Alerta tono="aviso" className="pp-bloque-sep" onReintentar={cargar} reintentando={cargando}>
          No pudimos actualizar el panel: se muestran los datos de las {hora(d.generadoEn)}.
        </Alerta>
      )}

      <section className="pp-bloque pp-seccion" aria-labelledby="pp-urgente">
        <div className="pp-bloque__cabecera">
          <h2 id="pp-urgente" className="pp-bloque__titulo">Lo urgente primero</h2>
          {urgentes.length > 0 && <span className="pp-bloque__nota">{urgentes.length} {urgentes.length === 1 ? "tema" : "temas"} por atender</span>}
        </div>
        {urgentes.length === 0 ? (
          <p className="pp-bloque__vacio">
            <Icon name="check" size={18} /> Nada pide tu atención: no hay negocios por aprobar, problemas de envío,
            despachos atrasados ni pagos pendientes.
          </p>
        ) : (
          <ul className="pp-urgentes">
            {urgentes.map((u) => (
              <li key={u.clave} className="pp-urgente">
                <div className="pp-urgente__texto">
                  <strong className="pp-urgente__titulo">{u.titulo}</strong>
                  <span className="pp-urgente__detalle">{u.detalle}</span>
                </div>
                <ChipEstado tono={u.tono}>{u.estado}</ChipEstado>
                <button type="button" className={`ui-btn ui-btn--chico pp-urgente__accion ${u.tono === "error" ? "ui-btn--primario" : "ui-btn--secundario"}`}
                  onClick={() => ir(u.pagina, u.preset)} aria-label={`${u.accion}: ${u.titulo}`}>
                  {u.accion} <Icon name="arrow" size={14} />
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="pp-seccion" aria-labelledby="pp-plataforma">
        <h2 id="pp-plataforma" className="pp-titulo">Plataforma</h2>
        <div className="ui-indicadores">
          <Indicador icono="box" titulo="Tiendas" valor={num(d.tiendas.registradas)}
            tono={d.tiendas.pendientes > 0 ? "accion" : undefined}
            pie={`${num(d.tiendas.aprobadas)} aprobadas · ${num(d.tiendas.pendientes)} por aprobar`}
            onClick={() => ir("negocios")} />
          <Indicador icono="tool" titulo="Talleres" valor={num(d.talleres.registradas)}
            tono={d.talleres.pendientes > 0 ? "accion" : undefined}
            pie={`${num(d.talleres.aprobadas)} aprobados · ${num(d.talleres.pendientes)} por aprobar`}
            onClick={() => ir("negocios")} />
          <Indicador icono="users" titulo="Clientes" valor={num(d.clientes.total)}
            pie={`+${num(d.clientes.nuevos7Dias)} en 7 días · +${num(d.clientes.nuevos30Dias)} en 30`}
            onClick={() => ir("clientes")} />
          <Indicador icono="layers" titulo="Productos" valor={num(d.productos.total)}
            pie={`${num(d.productos.nuevos7Dias)} nuevos en 7 días · ${num(d.productos.nuevos30Dias)} en 30`}
            onClick={() => ir("productos")} />
        </div>
      </section>

      <section className="pp-seccion" aria-labelledby="pp-dinero">
        <h2 id="pp-dinero" className="pp-titulo">Dinero</h2>
        <div className="ui-indicadores">
          <Indicador titulo="Total movido" valor={cop(d.dinero.movido)}
            pie="Pagos aprobados que pasaron por la plataforma. No es ingreso de DriveMaster." />
          <Indicador titulo="Comisión registrada" valor={cop(d.dinero.comisionRegistrada)} tono="ok"
            pie={`Informativa, al ${d.dinero.comisionPct}% vigente en cada pago. No es dinero cobrado.`}
            onClick={() => ir("reportes-plataforma")} />
          <Indicador titulo="Por liquidar a negocios" valor={cop(d.dinero.porLiquidar)}
            pie="Ya habilitado para pagar a talleres y tiendas." onClick={() => ir("liquidaciones")} />
        </div>
      </section>

      <div className="pp-columnas pp-seccion">
        <Ranking id="pp-productos" titulo="Productos más vendidos" vacio="Todavía no hay ventas en el catálogo."
          filas={d.productosMasVendidos.map((p) => ({ ...p, id: p.productoId }))}
          columnas={[
            { k: "nombre", t: "Producto", f: (p) => (
              <>{p.nombre || "Sin nombre"}<span className="pp-sub">{p.tienda || "Tienda sin nombre"}</span></>
            ) },
            { k: "unidades", t: "Unidades", num: true, f: (p) => num(p.unidades) },
            { k: "ventas", t: "Ventas", num: true, f: (p) => cop(p.ventas) },
          ]} />
        <Ranking id="pp-tiendas" titulo="Tiendas más vendedoras" vacio="Todavía no hay ventas en el catálogo."
          filas={d.tiendasMasVendedoras.map((t) => ({ ...t, id: t.tiendaId }))}
          columnas={[
            { k: "nombre", t: "Tienda" },
            { k: "pedidos", t: "Pedidos", num: true, f: (t) => num(t.pedidos) },
            { k: "ventas", t: "Ventas", num: true, f: (t) => cop(t.ventas) },
          ]} />
      </div>

      <div className="pp-columnas">
        <Barras id="pp-ordenes" titulo="Órdenes de servicio por estado" vacio="Todavía no hay órdenes de servicio." filas={ordenes} />
        <Barras id="pp-planes" titulo="Suscripciones activas por plan" vacio="Todavía no hay suscripciones activas." filas={planes} />
      </div>
    </div>
  );
}
