import { useEffect, useMemo, useState } from "react";
import { informesTienda, resumenAsistente } from "../services/informesTiendaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import Alerta from "../components/ui/Alerta";
import Icon from "../components/public/Icon";
import "../css/informes-tienda.css";

const PERIODOS = [3, 6, 12];

const cop = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n || 0);
const copCorto = (n) => new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", notation: "compact", maximumFractionDigits: 1 }).format(n || 0);
const num = (n) => new Intl.NumberFormat("es-CO").format(n || 0);
const mesCorto = (m) => new Date(Number(m.slice(0, 4)), Number(m.slice(5)) - 1, 1).toLocaleDateString("es-CO", { month: "short" }).replace(".", "").slice(0, 3);
const mesLargo = (m) => {
  const t = new Date(Number(m.slice(0, 4)), Number(m.slice(5)) - 1, 1).toLocaleDateString("es-CO", { month: "long", year: "numeric" });
  return t.charAt(0).toUpperCase() + t.slice(1);
};

/** Tope "redondo" para el eje (1, 1,5, 2, 3, 5 o 10 por una potencia de diez): las etiquetas del eje quedan limpias. */
function topeLimpio(max) {
  const potencia = 10 ** Math.floor(Math.log10(Math.max(max, 1)));
  const paso = [1, 1.5, 2, 3, 4, 5, 6, 8, 10].find((p) => p * potencia >= max) || 10;
  return paso * potencia;
}

// Estados del pedido agrupados como los entiende la tienda. El color nunca va solo: cada grupo tiene su texto.
const GRUPOS_ESTADO = [
  { id: "porDespachar", etiqueta: "Por despachar", tono: "aviso", estados: ["PENDIENTE", "EN_PREPARACION"] },
  { id: "enCamino", etiqueta: "En camino", tono: "info", estados: ["DESPACHADO"] },
  { id: "listoRetiro", etiqueta: "Listos para retirar", tono: "info-suave", estados: ["LISTO_PARA_RETIRAR"] },
  { id: "entregados", etiqueta: "Entregados", tono: "ok", estados: ["RECIBIDO", "ENTREGADO", "RETIRADO"] },
  { id: "noCompletados", etiqueta: "No completados", tono: "malo", estados: ["NO_DESPACHADO", "NO_ENTREGADO"] },
];

// ─── Piezas ────────────────────────────────────────────────────────────────

function Esqueleto({ alto = 16, ancho = "100%" }) {
  return <span className="ui-skeleton it-esqueleto" style={{ height: alto, width: ancho }} aria-hidden="true" />;
}

function Panel({ titulo, nota, ancho, children, accion }) {
  return (
    <section className={`it-panel it-panel--${ancho}`}>
      <header className="it-panel__cabecera">
        <div>
          <h2>{titulo}</h2>
          {nota && <p>{nota}</p>}
        </div>
        {accion}
      </header>
      {children}
    </section>
  );
}

function Vacio({ titulo, texto, accion }) {
  return (
    <div className="it-vacio">
      <strong>{titulo}</strong>
      <p>{texto}</p>
      {accion}
    </div>
  );
}

/** Barras por mes. HTML y no SVG: cada columna es un botón con foco, y el mes activo se lee arriba. */
function VentasPorMes({ ventas }) {
  const [activo, setActivo] = useState(null);
  const meses = ventas.meses;
  const max = topeLimpio(Math.max(1, ...meses.map((m) => m.ventas)));
  // Al pasar de 12 a 3 meses el mes elegido puede quedar fuera de la lista nueva.
  const indice = activo != null && activo < meses.length ? activo : meses.length - 1;
  const seleccionado = meses[indice];

  return (
    <div className="it-barras">
      <p className="it-barras__lectura" aria-live="polite">
        <strong>{mesLargo(seleccionado.mes)}</strong>
        <span>{num(seleccionado.pedidos)} {seleccionado.pedidos === 1 ? "pedido" : "pedidos"}</span>
        <span className="it-cifra">{cop(seleccionado.ventas)}</span>
      </p>
      <div className="it-barras__cuerpo">
        <div className="it-barras__eje" aria-hidden="true">
          <span>{copCorto(max)}</span>
          <span>{copCorto(max / 2)}</span>
          <span>0</span>
        </div>
        <ul className="it-barras__columnas" style={{ "--n": meses.length }}>
          {meses.map((m, i) => (
            <li key={m.mes}>
              <button
                type="button"
                className={`it-barra${i === indice ? " is-activa" : ""}${i === meses.length - 1 ? " is-actual" : ""}`}
                style={{ "--h": `${Math.max(m.ventas > 0 ? 3 : 0, (m.ventas / max) * 100)}%`, "--i": i }}
                aria-label={`${mesLargo(m.mes)}: ${num(m.pedidos)} ${m.pedidos === 1 ? "pedido" : "pedidos"}, ${cop(m.ventas)}`}
                aria-pressed={i === indice}
                onClick={() => setActivo(i)}
                onMouseEnter={() => setActivo(i)}
                onFocus={() => setActivo(i)}
              >
                <span className="it-barra__relleno" />
              </button>
              <span className="it-barras__mes">{mesCorto(m.mes)}</span>
            </li>
          ))}
        </ul>
      </div>
      <table className="it-solo-lectores">
        <caption>Ventas por mes</caption>
        <thead><tr><th>Mes</th><th>Pedidos</th><th>Ventas</th></tr></thead>
        <tbody>{meses.map((m) => <tr key={m.mes}><td>{mesLargo(m.mes)}</td><td>{m.pedidos}</td><td>{cop(m.ventas)}</td></tr>)}</tbody>
      </table>
    </div>
  );
}

function PedidosPorEstado({ estados }) {
  const grupos = GRUPOS_ESTADO.map((g) => ({ ...g, n: g.estados.reduce((s, e) => s + (estados[e] || 0), 0) }));
  const total = grupos.reduce((s, g) => s + g.n, 0);
  if (total === 0) {
    return <Vacio titulo="Sin pedidos en este período" texto="Cuando un cliente pague un pedido de tu tienda, verás aquí en qué estado va." />;
  }
  return (
    <div className="it-estados">
      <div className="it-segmentada" role="img"
        aria-label={grupos.filter((g) => g.n > 0).map((g) => `${g.etiqueta}: ${g.n}`).join(", ")}>
        {grupos.filter((g) => g.n > 0).map((g) => (
          <span key={g.id} className={`it-segmento it-tono--${g.tono}`} style={{ flexGrow: g.n }} />
        ))}
      </div>
      <ul className="it-leyenda">
        {grupos.map((g) => (
          <li key={g.id} className={g.n === 0 ? "is-cero" : ""}>
            <span className={`it-punto it-tono--${g.tono}`} aria-hidden="true" />
            <span>{g.etiqueta}</span>
            <strong className="it-cifra">{num(g.n)}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ProductosMasVendidos({ productos, onIr }) {
  if (productos.length === 0) {
    return (
      <Vacio titulo="Todavía no vendes productos en este período"
        texto="Cuando se pague un pedido, tus productos más vendidos aparecen aquí."
        accion={<button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onIr?.("productos")}>Revisar mis productos</button>} />
    );
  }
  const max = Math.max(...productos.map((p) => p.unidades));
  return (
    <ol className="it-ranking">
      {productos.map((p, i) => (
        <li key={p.productoId}>
          <span className="it-ranking__puesto it-cifra">{i + 1}</span>
          <div className="it-ranking__cuerpo">
            <div className="it-ranking__fila">
              <span className="it-ranking__nombre" title={p.nombre}>{p.nombre || "Producto sin nombre"}</span>
              <span className="it-cifra">{num(p.unidades)} {p.unidades === 1 ? "unidad" : "unidades"}</span>
            </div>
            <span className="it-hilo" aria-hidden="true"><span style={{ width: `${(p.unidades / max) * 100}%`, "--i": i }} /></span>
            <span className="it-ranking__ventas it-cifra">{cop(p.ventas)}</span>
          </div>
        </li>
      ))}
    </ol>
  );
}

function Despacho({ despacho, onIr }) {
  const { despachados, horasPromedio, porcentajeATiempo, pendientes, atrasados } = despacho;
  const dias = horasPromedio != null && horasPromedio >= 48;
  return (
    <div className="it-despacho">
      {despachados === 0 ? (
        <Vacio titulo="Aún no hay despachos medidos" texto="El tiempo se mide desde que el cliente paga hasta que despachas el envío o dejas listo el retiro." />
      ) : (
        <>
          <div className="it-despacho__dato">
            <span className="it-despacho__valor it-cifra">{dias ? (horasPromedio / 24).toFixed(1) : horasPromedio}</span>
            <span className="it-despacho__unidad">{dias ? "días" : "horas"} en promedio</span>
          </div>
          <p className="it-despacho__sub">{num(despachados)} {despachados === 1 ? "pedido medido" : "pedidos medidos"} en el período</p>
          {porcentajeATiempo != null && (
            <div className="it-meter" role="img" aria-label={`${porcentajeATiempo}% despachado a tiempo`}>
              <div className="it-meter__fila"><span>Despachados a tiempo</span><strong className="it-cifra">{porcentajeATiempo}%</strong></div>
              <span className="it-hilo it-hilo--grueso" aria-hidden="true"><span style={{ width: `${porcentajeATiempo}%` }} /></span>
            </div>
          )}
        </>
      )}
      <ul className="it-datos">
        <li><span>Esperando tu despacho</span><strong className="it-cifra">{num(pendientes)}</strong></li>
        <li className={atrasados > 0 ? "is-alerta" : ""}><span>Fuera de plazo</span><strong className="it-cifra">{num(atrasados)}</strong></li>
      </ul>
      {atrasados > 0 && (
        <button type="button" className="ui-btn ui-btn--primario" onClick={() => onIr?.("pedidos-tienda")}>Ir a despachar los atrasados</button>
      )}
    </div>
  );
}

function StockBajo({ stock, onIr }) {
  if (stock.total === 0) {
    return <Vacio titulo="Tu inventario está al día" texto="Ningún producto está en o bajo su stock mínimo. Te avisamos aquí cuando alguno lo esté." />;
  }
  return (
    <>
      <table className="it-tabla">
        <thead><tr><th scope="col">Producto</th><th scope="col" className="it-der">Stock</th><th scope="col" className="it-der">Mínimo</th></tr></thead>
        <tbody>
          {stock.items.map((p) => (
            <tr key={p.productoId} className={p.stock === 0 ? "is-agotado" : ""}>
              <td>
                <span className="it-tabla__nombre" title={p.nombre}>{p.nombre || "Producto sin nombre"}</span>
                <span className="it-tabla__sub">{p.codigo}</span>
              </td>
              <td className="it-der it-cifra">{p.stock === 0 ? "Agotado" : p.stock}</td>
              <td className="it-der it-cifra">{p.minimo}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <footer className="it-pie">
        <span>{stock.total > stock.items.length ? `Mostrando ${stock.items.length} de ${stock.total}` : `${stock.total} ${stock.total === 1 ? "producto" : "productos"}`}</span>
        <span className="it-pie__acciones">
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => onIr?.("productos")}>Ver productos</button>
          <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onIr?.("movimientos")}>Registrar entrada</button>
        </span>
      </footer>
    </>
  );
}

function Liquidacion({ liquidacion: l }) {
  const filas = [
    { clave: "espera", titulo: "En espera de la entrega", monto: l.enEspera, n: l.lineasEnEspera,
      texto: "Pagos de pedidos que aún no se entregan. Se habilitan cuando el cliente confirma la recepción o validas el retiro." },
    { clave: "listo", titulo: "Listo para liquidar", monto: l.porLiquidar, n: l.lineasPorLiquidar, destacada: true,
      texto: "Ya se entregaron: DriveMaster te los transfiere en la próxima liquidación." },
    { clave: "pagado", titulo: "Ya liquidado", monto: l.liquidado, n: l.lineasLiquidadas,
      texto: "Lo que ya te transfirieron." },
  ];
  const vacio = filas.every((f) => f.n === 0);
  if (vacio) {
    return <Vacio titulo="Todavía no tienes dinero por liquidar" texto="Cada pedido pagado aparece aquí desde que el cliente paga hasta que te lo transferimos." />;
  }
  return (
    <>
      <ul className="it-liquidacion">
        {filas.map((f) => (
          <li key={f.clave} className={f.destacada ? "is-destacada" : ""}>
            <div className="it-liquidacion__fila">
              <span>{f.titulo}</span>
              <strong className="it-cifra">{cop(f.monto)}</strong>
            </div>
            <p>{f.n > 0 ? `${num(f.n)} ${f.n === 1 ? "pedido" : "pedidos"}. ` : ""}{f.texto}</p>
          </li>
        ))}
      </ul>
      {l.anulado > 0 && <p className="it-nota">{cop(l.anulado)} se anularon por pedidos no despachados o reembolsados.</p>}
    </>
  );
}

/** Despacho promedio en horas o, desde 48 h, en días. */
const duracion = (h) => (h == null ? "—" : h >= 48 ? `${(h / 24).toLocaleString("es-CO", { maximumFractionDigits: 1 })} días` : `${h} h`);

function Resumen({ datos, onIr }) {
  const stock = datos.stockBajo.total;
  return (
    <section aria-labelledby="it-resumen-titulo" className="it-resumen-cifras">
      <h2 id="it-resumen-titulo" className="ui-sr">Resumen del período</h2>
      <div className="ui-indicadores">
        <Indicador titulo="Ventas del período" valor={cop(datos.ventas.ventas)}
          pie={`${num(datos.ventas.pedidos)} ${datos.ventas.pedidos === 1 ? "pedido" : "pedidos"}`} />
        <Indicador icono="clock" titulo="Despacho promedio" valor={duracion(datos.despacho.horasPromedio)}
          pie={datos.despacho.porcentajeATiempo != null ? `${datos.despacho.porcentajeATiempo} % a tiempo` : "Sin despachos medidos aún"} />
        <Indicador titulo="Por liquidar" valor={cop(datos.liquidacion.porLiquidar)} tono={datos.liquidacion.porLiquidar > 0 ? "ok" : undefined}
          pie={`${cop(datos.liquidacion.enEspera)} en espera de entrega`} />
        <Indicador icono="alert" titulo="Stock bajo" valor={num(stock)} tono={stock > 0 ? "accion" : undefined}
          pie={stock === 0 ? "Nada por reponer" : stock === 1 ? "Producto por reponer" : "Productos por reponer"}
          onClick={stock > 0 ? () => onIr?.("productos") : undefined} />
      </div>
    </section>
  );
}

/** Resumen del día escrito por la IA con los datos de la tienda. Solo lee: nada cambia en la tienda. */
function ResumenIA({ token }) {
  const [resumen, setResumen] = useState({ texto: null, error: null });

  useEffect(() => {
    let vivo = true;
    resumenAsistente(token)
      .then((texto) => { if (vivo) setResumen({ texto, error: null }); })
      .catch((e) => { if (vivo) setResumen({ texto: null, error: e.message }); });
    return () => { vivo = false; };
  }, [token]);

  return (
    <div className="it-asistente__resumen" aria-live="polite" aria-busy={!resumen.texto && !resumen.error}>
      {resumen.texto ? <p>{resumen.texto}</p>
        : resumen.error ? <p className="it-nota">{resumen.error}</p>
        : <><Esqueleto alto={12} /><Esqueleto alto={12} ancho="80%" /><Esqueleto alto={12} ancho="60%" /></>}
      <p className="it-nota">Lo escribe una IA con los datos de estos informes: revisa las cifras antes de decidir.</p>
    </div>
  );
}

function CargandoInformes() {
  return (
    <div className="it-cargando" role="status" aria-label="Cargando informes">
      <div className="ui-indicadores it-resumen-cifras">{[0, 1, 2, 3].map((i) => <Esqueleto key={i} alto={112} />)}</div>
      <div className="it-rejilla">
        <div className="it-panel it-panel--8"><Esqueleto alto={14} ancho="30%" /><Esqueleto alto={190} /></div>
        <div className="it-panel it-panel--4"><Esqueleto alto={14} ancho="40%" /><Esqueleto alto={120} /></div>
        <div className="it-panel it-panel--6"><Esqueleto alto={14} ancho="40%" /><Esqueleto alto={150} /></div>
        <div className="it-panel it-panel--6"><Esqueleto alto={14} ancho="40%" /><Esqueleto alto={150} /></div>
      </div>
    </div>
  );
}

// ─── Página ────────────────────────────────────────────────────────────────

/**
 * Informes de la tienda (rol TIENDA): ventas por mes, productos más vendidos, stock bajo, pedidos por estado, tiempo
 * de despacho y lo pendiente de liquidar. Solo muestra datos de la propia tienda; el backend filtra por dueño.
 */
export default function InformesTienda({ token, onIr }) {
  const [meses, setMeses] = useState(6);
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null, hora: null });
  const [version, setVersion] = useState(0);
  const clave = `${meses}|${version}`;

  useEffect(() => {
    let vivo = true;
    informesTienda(token, meses)
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null, hora: new Date() }); })
      .catch((e) => { if (vivo) setEstado((p) => ({ ...p, clave, error: e.message })); });
    return () => { vivo = false; };
  }, [token, meses, clave]);

  const cargando = estado.clave !== clave;
  const { datos, error } = estado;
  const sinActividad = useMemo(() => datos
    && datos.ventas.pedidos === 0 && datos.despacho.pendientes === 0 && datos.liquidacion.enEspera === 0
    && datos.liquidacion.porLiquidar === 0 && datos.liquidacion.liquidado === 0, [datos]);

  return (
    <div className="ss-panel--tienda it-root">
      <EncabezadoPagina titulo="Informes" descripcion="Cómo va tu tienda: ventas, despachos y lo que falta por cobrar."
        acciones={(
          <>
            <div className="ui-filtros" role="group" aria-label="Período">
              {PERIODOS.map((p) => (
                <button key={p} type="button" className="ui-chip" aria-pressed={meses === p} onClick={() => setMeses(p)}>{p} meses</button>
              ))}
            </div>
            <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setVersion((v) => v + 1)}
              disabled={cargando} aria-busy={cargando}>
              {!cargando && <Icon name="refresh" size={16} />}{cargando ? "Actualizando…" : "Actualizar"}
            </button>
          </>
        )} />

      {error && !cargando && (
        <Alerta className="it-alerta" onReintentar={() => setVersion((v) => v + 1)}>
          No pudimos cargar los informes. {error}{datos ? " Se muestran los últimos datos." : ""}
        </Alerta>
      )}

      {!datos && !error && <CargandoInformes />}

      {datos && (
        <div className={cargando ? "it-contenido is-recargando" : "it-contenido"} aria-busy={cargando}>
          {sinActividad && (
            <div className="it-bienvenida">
              <strong>Aún no hay actividad en tu tienda</strong>
              <p>Cuando recibas tu primer pedido pagado, aquí verás tus ventas, el tiempo de despacho y lo que te toca cobrar. Mientras, asegúrate de tener tus productos publicados y con stock.</p>
              <button type="button" className="ui-btn ui-btn--primario" onClick={() => onIr?.("productos")}>Ir a mis productos</button>
            </div>
          )}

          <Resumen datos={datos} onIr={onIr} />

          <div className="it-rejilla">
            <Panel ancho="12" titulo="Resumen de hoy" nota="Lo más importante de tu tienda hoy, escrito por la IA con tus informes de los últimos 6 meses.">
              <ResumenIA token={token} />
            </Panel>
            <Panel ancho="8" titulo="Ventas por mes" nota={`Pedidos despachados o entregados, con IVA y envío. Últimos ${datos.mesesConsultados} meses.`}>
              {datos.ventas.pedidos === 0
                ? <Vacio titulo="Sin ventas en este período" texto="Prueba con un período más largo o revisa que tus productos estén publicados." />
                : <VentasPorMes ventas={datos.ventas} />}
            </Panel>
            <Panel ancho="4" titulo="Pedidos por estado" nota="En qué va cada pedido del período.">
              <PedidosPorEstado estados={datos.pedidosPorEstado} />
            </Panel>
            <Panel ancho="6" titulo="Productos más vendidos" nota="Por unidades en el período.">
              <ProductosMasVendidos productos={datos.productosMasVendidos} onIr={onIr} />
            </Panel>
            <Panel ancho="6" titulo="Tiempo de despacho" nota="Desde que el cliente paga hasta que despachas o dejas listo el retiro.">
              <Despacho despacho={datos.despacho} onIr={onIr} />
            </Panel>
            <Panel ancho="7" titulo="Stock bajo" nota="Productos en o bajo su stock mínimo (o 5 unidades si no definiste uno).">
              <StockBajo stock={datos.stockBajo} onIr={onIr} />
            </Panel>
            <Panel ancho="5" titulo="Lo pendiente de liquidar" nota="El dinero de tus pedidos, según en qué etapa está.">
              <Liquidacion liquidacion={datos.liquidacion} />
            </Panel>
          </div>
        </div>
      )}
    </div>
  );
}
