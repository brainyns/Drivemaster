import { useEffect, useState } from "react";
import {
  reporteVentasPlataforma, reporteProductosPlataforma, reporteClientesPlataforma,
  reporteServiciosPlataforma, reporteComisionPlataforma,
} from "../services/plataformaService";
import { marcarSaldoCobrado } from "../services/liquidacionService";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/reportes-plataforma.css";

const cop = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);
const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);
const mesLargo = (m) => {
  const [anio, mes] = String(m).split("-");
  return new Date(Number(anio), Number(mes) - 1, 1).toLocaleDateString("es-CO", { month: "short", year: "2-digit" });
};

const PERIODOS = [3, 6, 12, 24];

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
  { id: "comision", titulo: "Comisión", periodo: true,
    nota: "La cobrada es dinero retenido en pagos ya transferidos. La informativa solo deja constancia de lo que correspondía y no es dinero cobrado.",
    cargar: (token, meses) => reporteComisionPlataforma(token, { meses }) },
];

/** Tabla de un reporte: en celular cada fila es una tarjeta; la primera columna hace de título. */
function Tabla({ titulo, columnas, filas, vacio, pie }) {
  if (filas.length === 0) return <EstadoVacio icono="file" titulo={vacio} texto="Prueba con un período más largo." />;
  return (
    <div className="ui-card ui-table-card">
      <div className="ui-table-wrap">
        <table className="ui-table ui-table--tarjetas rep-tabla">
          <caption className="ui-sr">{titulo}</caption>
          <thead>
            <tr>{columnas.map((c) => <th key={c.t} scope="col" className={c.der ? "pp-num" : undefined}>{c.t}</th>)}</tr>
          </thead>
          <tbody>
            {filas.map((f, i) => (
              <tr key={f.id ?? i}>
                {columnas.map((c, j) => (
                  <td key={c.t} data-label={j === 0 ? undefined : c.t}
                    className={j === 0 ? "ui-table__titulo" : c.der ? "pp-num" : undefined}>{c.f(f)}</td>
                ))}
              </tr>
            ))}
          </tbody>
          {pie && (
            <tfoot>
              <tr>
                {pie.map((c, i) => (
                  <td key={i} data-label={i === 0 ? undefined : columnas[i]?.t}
                    className={i === 0 ? "ui-table__titulo" : c.der ? "pp-num" : undefined}><strong>{c.v}</strong></td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </div>
  );
}

const sinNombre = (v) => v || <span className="pp-tenue">Sin nombre</span>;

/** Reembolsos de líneas ya pagadas: se le cobra al negocio por fuera y aquí se anota. */
function SaldosAFavor({ saldos, token, onCambio }) {
  const [marcando, setMarcando] = useState(null);
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState(null);
  if (!saldos || saldos.items.length === 0) return null;

  const confirmar = async () => {
    setProcesando(true);
    setError(null);
    try {
      await marcarSaldoCobrado(token, marcando.id);
      setMarcando(null);
      onCambio();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  return (
    <section className="pp-seccion" aria-labelledby="rep-saldos">
      <h2 id="rep-saldos" className="pp-titulo">Saldos a favor pendientes</h2>
      <Tabla titulo="Saldos a favor pendientes" vacio="No hay saldos por cobrar." filas={saldos.items}
        columnas={[
          { t: "Pago", f: (s) => <>{s.referenciaPago}<span className="pp-sub">{s.motivo}</span></> },
          { t: "Saldo", der: true, f: (s) => <strong>{cop(s.monto)}</strong> },
          { t: "Acción", der: true, f: (s) => (
            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => { setError(null); setMarcando(s); }}>Marcar cobrado</button>
          ) },
        ]} />
      {marcando && (
        <ConfirmacionAdmin titulo={`Marcar cobrado · ${cop(marcando.monto)}`}
          texto={`Confirma que el negocio ya devolvió este dinero (pago ${marcando.referenciaPago}). Aquí no se mueve dinero, solo se anota.`}
          confirmar="Marcar cobrado" cargando={procesando} onConfirmar={confirmar} onCancelar={() => setMarcando(null)}>
          {error && <Alerta>{error}</Alerta>}
        </ConfirmacionAdmin>
      )}
    </section>
  );
}

function Contenido({ id, titulo, datos, token, onCambio }) {
  if (id === "ventas") {
    const filas = datos.tiendas.map((t) => ({ ...t, id: t.tiendaId }));
    return (
      <Tabla titulo={titulo} vacio="Todavía no hay ventas en el período."
        columnas={[
          { t: "Tienda", f: (t) => sinNombre(t.tienda) },
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
      <Tabla titulo={titulo} vacio="Todavía no hay ventas en el período."
        filas={datos.productos.map((p) => ({ ...p, id: p.productoId }))}
        columnas={[
          { t: "Producto", f: (p) => <>{sinNombre(p.nombre)}<span className="pp-sub">{p.tienda || "Tienda sin nombre"}</span></> },
          { t: "Unidades", der: true, f: (p) => num(p.unidades) }, { t: "Ventas", der: true, f: (p) => cop(p.ventas) },
        ]} />
    );
  }
  if (id === "clientes") {
    return (
      <Tabla titulo={titulo} vacio="Todavía no hay compras en el período."
        filas={datos.clientes.map((c) => ({ ...c, id: c.clienteId }))}
        columnas={[
          { t: "Cliente", f: (c) => <>{sinNombre(c.cliente)}<span className="pp-sub">{c.ciudad || "Sin ciudad"}</span></> },
          { t: "Compras", der: true, f: (c) => num(c.compras) }, { t: "Total comprado", der: true, f: (c) => cop(c.total) },
        ]} />
    );
  }
  if (id === "servicios") {
    return (
      <Tabla titulo={titulo} vacio="Todavía no hay órdenes de servicio."
        filas={datos.talleres.map((t) => ({ ...t, id: t.tallerId }))}
        columnas={[{ t: "Taller", f: (t) => sinNombre(t.taller) }, { t: "Órdenes", der: true, f: (t) => num(t.total) },
          { t: "Entregadas", der: true, f: (t) => num(t.entregadas) }, { t: "En curso", der: true, f: (t) => num(t.enCurso) },
          { t: "Canceladas", der: true, f: (t) => num(t.canceladas) }, { t: "Repartido al taller", der: true, f: (t) => cop(t.movidoAlTaller) }]} />
    );
  }
  return (
    <>
      <section className="pp-seccion" aria-labelledby="rep-cobrada">
        <h2 id="rep-cobrada" className="pp-titulo">Comisión cobrada</h2>
        <div className="ui-indicadores pp-indicadores-dinero">
          <Indicador titulo="Cobrada" valor={cop(datos.retenida?.cobrada)} tono="ok"
            pie={datos.comisionSeDescuenta ? "Retenida en líneas liquidadas con su id de Wompi. Total acumulado." : "El cobro está apagado: la comisión no se descuenta todavía."} />
          <Indicador titulo="Por cobrar" valor={cop(datos.retenida?.porCobrar)} pie="Retenida en pagos que aún no se transfieren." />
          <Indicador titulo="Anulada" valor={cop(datos.retenida?.anulada)} pie="Era de pagos anulados por reembolso: no se cobró." />
          {datos.retenida?.sinIdWompi > 0 && (
            <Indicador titulo="Sin id de Wompi" valor={cop(datos.retenida.sinIdWompi)} tono="accion"
              pie="Liquidada sin id del pago: no cuenta como cobrada hasta que llegue el evento de Wompi." />
          )}
          {datos.retenida?.cobradaSimulada > 0 && (
            <Indicador titulo="Simulada" valor={cop(datos.retenida.cobradaSimulada)} pie="De líneas SIM-: no movió dinero ni cuenta como cobrada." />
          )}
          <Indicador titulo="Saldos a favor pendientes" valor={cop(datos.saldosAFavor?.total ?? 0)}
            tono={datos.saldosAFavor?.total > 0 ? "accion" : undefined}
            pie="Reembolsos de pagos ya transferidos al negocio: se le cobran a mano." />
        </div>
      </section>
      <SaldosAFavor saldos={datos.saldosAFavor} token={token} onCambio={onCambio} />
      <section className="pp-seccion" aria-labelledby="rep-informativa">
        <h2 id="rep-informativa" className="pp-titulo">Comisión informativa</h2>
        <div className="ui-indicadores">
          <Indicador titulo="Registrada" valor={cop(datos.registrada)} pie="En los pagos aprobados del período sin descuento." />
          <Indicador titulo="Anulada por reembolsos" valor={cop(datos.anulada)} pie="Se descuenta cuando se reembolsa un pago." />
          <Indicador titulo="Comisión neta" valor={cop(datos.neta)} pie="Registrada menos anulada. No es dinero cobrado." />
        </div>
      </section>
      <Tabla titulo={titulo} vacio="Todavía no hay comisión registrada."
        filas={datos.meses.map((m) => ({ ...m, id: m.mes }))}
        columnas={[{ t: "Mes", f: (m) => mesLargo(m.mes) }, { t: "Cobrada", der: true, f: (m) => <strong>{cop(m.cobrada)}</strong> },
          { t: "Registrada", der: true, f: (m) => cop(m.registrada) }, { t: "Anulada", der: true, f: (m) => cop(m.anulada) },
          { t: "Neta informativa", der: true, f: (m) => cop(m.neta) }]} />
    </>
  );
}

/**
 * Reportes de la plataforma (solo SUPERADMIN). Se calculan sobre los pedidos de las tiendas, los pagos y las
 * órdenes de servicio; no sobre las ventas del flujo anterior (eso está en "Historial anterior").
 * filtroInicial: el id de un reporte (p. ej. "comision", desde el dashboard).
 */
export default function ReportesPlataforma({ token, filtroInicial }) {
  const [pestana, setPestana] = useState(() => (PESTANAS.some((p) => p.id === filtroInicial) ? filtroInicial : "ventas"));
  const [meses, setMeses] = useState(12);
  const [version, setVersion] = useState(0);
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });

  const def = PESTANAS.find((p) => p.id === pestana);
  const clave = `${pestana}|${def.periodo ? meses : 0}|${version}`;

  useEffect(() => {
    let vivo = true;
    def.cargar(token, meses)
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setEstado({ clave, datos: null, error: e.message }); });
    return () => { vivo = false; };
  }, [token, def, meses, clave]);

  const cargando = estado.clave !== clave;

  return (
    <div className="ss-panel--plataforma pp-root rep-root">
      <EncabezadoPagina titulo="Reportes de la plataforma"
        descripcion="Calculados sobre los pedidos de las tiendas, los pagos y las órdenes de servicio." />

      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Reporte">
        {PESTANAS.map((p) => (
          <button key={p.id} type="button" className="ui-chip" aria-pressed={pestana === p.id} onClick={() => setPestana(p.id)}>{p.titulo}</button>
        ))}
      </div>

      <div className="rep-cabecera pp-bloque-sep">
        <p className="rep-nota">{def.nota}</p>
        {def.periodo && (
          <label className="pp-filtros__select">
            Período
            <select className="ui-field__input" value={meses} onChange={(e) => setMeses(Number(e.target.value))}>
              {PERIODOS.map((m) => <option key={m} value={m}>Últimos {m} meses</option>)}
            </select>
          </label>
        )}
      </div>

      {estado.error && !cargando && (
        <Alerta className="pp-bloque-sep" onReintentar={() => setVersion((v) => v + 1)}>{estado.error}</Alerta>
      )}

      <div aria-busy={cargando}>
        {cargando ? (
          <Esqueleto variante="lista" cantidad={5} texto="Calculando el reporte…" />
        ) : estado.datos ? <Contenido id={pestana} titulo={def.titulo} datos={estado.datos} token={token} onCambio={() => setVersion((v) => v + 1)} /> : null}
      </div>
    </div>
  );
}
