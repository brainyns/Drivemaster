import { useCallback, useEffect, useState } from "react";
import { pedidosPlataforma } from "../services/plataformaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import EncabezadoOrden from "../components/ui/EncabezadoOrden";
import SelectorOrden from "../components/ui/SelectorOrden";
import { errorRango, propsPaginacion, textoOrden } from "../components/ui/reglasTabla";
import SelectorFechas from "../components/ui/SelectorFechas";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import DetallePlataforma from "../components/admin/DetallePlataforma";
import {
  ENTREGAS_PEDIDO, ESTADOS_PEDIDO, GRUPOS_PEDIDO, chipsAlerta, conteoGrupos, filaConAlerta, filtrosInicialesPedidos,
  hayFiltrosPedidos, refPedido,
} from "./admin/plataformaAdmin";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/pedidos-plataforma.css";

const TAMANO = 20;
const REFRESCO_MS = 30000;

/** Columnas que se pueden ordenar (lista blanca de PlataformaConsultasService.ORDEN_PEDIDOS). */
const ORDENES = [
  { campo: "fecha", label: "Fecha de pago", asc: "(más antiguos primero)", desc: "(más recientes primero)" },
  { campo: "tienda", label: "Tienda", asc: "(A–Z)", desc: "(Z–A)" },
  { campo: "estado", label: "Estado", asc: "(A–Z)", desc: "(Z–A)" },
];

const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short" }) : "—");
const hora = (d) => d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;

/**
 * Monitor de pedidos y envíos de todas las tiendas (ADMIN y SUPERADMIN). Solo lectura y sin datos del cliente:
 * estado, tienda, destino (ciudad), días desde el pago y el semáforo de despacho y de retiro. Se actualiza solo cada
 * 30 segundos. Con puedeVerDetalle (SUPERADMIN), la referencia abre el detalle del pedido.
 */
export default function PedidosEnvios({ token, filtroInicial, puedeVerDetalle = false }) {
  const [filtros, setFiltros] = useState(() => filtrosInicialesPedidos(filtroInicial));
  const [abierto, setAbierto] = useState(null);
  const [pagina, setPagina] = useState(0);
  const [orden, setOrden] = useState(null); // null: del más reciente al más antiguo
  const [fechas, setFechas] = useState({ desde: "", hasta: "" }); // fecha de pago, en hora de Colombia
  const [estado, setEstado] = useState({ datos: null, error: null, actualizado: null });
  const [cargando, setCargando] = useState(true);
  const errorFechas = errorRango(fechas);

  const cargar = useCallback(() => {
    if (errorFechas) return Promise.resolve(); // el selector de fechas ya lo avisa
    setCargando(true);
    return pedidosPlataforma(token, { ...filtros, ...fechas, page: pagina, size: TAMANO, sort: textoOrden(orden) })
      .then((datos) => setEstado({ datos, error: null, actualizado: new Date() }))
      .catch((e) => setEstado((previo) => ({ ...previo, error: e.message })))
      .finally(() => setCargando(false));
  }, [token, filtros, fechas, pagina, orden, errorFechas]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const intervalo = setInterval(cargar, REFRESCO_MS);
    return () => { clearTimeout(inicial); clearInterval(intervalo); };
  }, [cargar]);

  const { datos, error, actualizado } = estado;
  const lista = datos?.pedidos;
  const items = lista?.items || [];
  const hayFiltros = hayFiltrosPedidos(filtros, fechas);
  // Los avisos son los filtros de alerta: clic para filtrar, otro clic para quitar
  const alternarAlerta = (valor) => poner({ alerta: filtros.alerta === valor ? "" : valor });
  const porGrupo = conteoGrupos(datos?.porEstado);
  const verAviso = (alerta, n) => n > 0 || filtros.alerta === alerta;

  // Cambiar filtro, fechas u orden vuelve a la primera página
  const poner = (cambio) => { setPagina(0); setFiltros((f) => ({ ...f, ...cambio })); };
  const ponerFechas = (r) => { setPagina(0); setFechas(r); };
  const ordenar = (o) => { setPagina(0); setOrden(o); };
  const limpiar = () => { setPagina(0); setFiltros(filtrosInicialesPedidos()); setFechas({ desde: "", hasta: "" }); };

  return (
    <div className="ss-panel--plataforma pp-root ped-root">
      <EncabezadoPagina titulo="Pedidos y envíos"
        descripcion="Lo que se vende en las tiendas y los envíos y retiros en curso. Se actualiza cada 30 segundos."
        acciones={(
          <>
            {actualizado && <span className="pp-actualizado">Actualizado a las {hora(actualizado)}</span>}
            <button type="button" className="ui-btn ui-btn--secundario" onClick={cargar} disabled={cargando} aria-busy={cargando}>
              <Icon name="refresh" size={16} /> {cargando && datos ? "Actualizando…" : "Actualizar"}
            </button>
          </>
        )} />

      {/* Cada aviso aparece si hay casos o si es el filtro puesto (para poder quitarlo aunque llegue a 0) */}
      {datos && (verAviso("ATRASADO", datos.atrasados) || verAviso("POR_VENCER", datos.porVencer)
        || verAviso("SIN_RETIRAR", datos.sinRetirar) || datos.problemasAbiertos > 0 || filtros.estado === "PROBLEMA") && (
        <div className="ui-filtros pp-bloque-sep ped-deslizable" role="group" aria-label="Pedidos que piden atención">
          {verAviso("ATRASADO", datos.atrasados) && (
            <button type="button" className="ui-chip ped-alerta" aria-pressed={filtros.alerta === "ATRASADO"}
              onClick={() => alternarAlerta("ATRASADO")}>
              <Icon name="clock" size={15} />
              {plural(datos.atrasados, "pedido sin despachar", "pedidos sin despachar")} fuera del plazo
            </button>
          )}
          {verAviso("POR_VENCER", datos.porVencer) && (
            <button type="button" className="ui-chip ped-alerta ped-alerta--aviso" aria-pressed={filtros.alerta === "POR_VENCER"}
              onClick={() => alternarAlerta("POR_VENCER")}>
              <Icon name="clock" size={15} />
              {datos.porVencer} por vencer
            </button>
          )}
          {verAviso("SIN_RETIRAR", datos.sinRetirar) && (
            <button type="button" className="ui-chip ped-alerta ped-alerta--aviso" aria-pressed={filtros.alerta === "SIN_RETIRAR"}
              onClick={() => alternarAlerta("SIN_RETIRAR")}>
              <Icon name="box" size={15} />
              {plural(datos.sinRetirar, "pedido sin retirar", "pedidos sin retirar")} en la tienda
            </button>
          )}
          {(datos.problemasAbiertos > 0 || filtros.estado === "PROBLEMA") && (
            <button type="button" className="ui-chip ped-alerta" aria-pressed={filtros.estado === "PROBLEMA"}
              onClick={() => poner({ estado: filtros.estado === "PROBLEMA" ? "" : "PROBLEMA" })}>
              <Icon name="alert" size={15} />
              {plural(datos.problemasAbiertos, "problema de envío abierto", "problemas de envío abiertos")}
            </button>
          )}
        </div>
      )}

      {datos && (
        <div className="ui-filtros pp-bloque-sep ped-deslizable" role="group" aria-label="Estado del pedido">
          {GRUPOS_PEDIDO.map((g) => (
            <button key={g.valor || "todos"} type="button" className="ui-chip" aria-pressed={filtros.estado === g.valor}
              onClick={() => poner({ estado: g.valor })}>
              {g.label} <span className="ui-chip__contador">{porGrupo[g.valor] || 0}</span>
            </button>
          ))}
        </div>
      )}

      <div className="ped-selectores pp-bloque-sep" role="group" aria-label="Filtrar pedidos">
        <label htmlFor="ped-tienda" className="ped-selector">
          Tienda
          <select id="ped-tienda" className="ui-field__input" value={filtros.tiendaId} onChange={(e) => poner({ tiendaId: e.target.value })}>
            <option value="">Todas</option>
            {(datos?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <label htmlFor="ped-entrega" className="ped-selector">
          Entrega
          <select id="ped-entrega" className="ui-field__input" value={filtros.entrega} onChange={(e) => poner({ entrega: e.target.value })}>
            {ENTREGAS_PEDIDO.map((x) => <option key={x.valor || "todas"} value={x.valor}>{x.label}</option>)}
          </select>
        </label>
        <SelectorFechas id="ped-fecha" etiqueta="Fecha" valor={fechas} onCambiar={ponerFechas} className="ped-selector ped-selector--fecha" />
        {hayFiltros && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico ped-limpiar" onClick={limpiar}>Limpiar filtros</button>
        )}
      </div>
      <SelectorOrden id="ped-orden" opciones={ORDENES} orden={orden} onOrdenar={ordenar} porDefecto="Más recientes primero" />

      {error && (
        <Alerta tono={datos ? "aviso" : "error"} className="pp-bloque-sep" onReintentar={cargar} reintentando={cargando}>
          {datos ? `No pudimos actualizar los pedidos: se muestran los de las ${hora(actualizado)}.` : error}
        </Alerta>
      )}

      {!lista ? (
        !error && <Esqueleto variante="lista" cantidad={6} texto="Cargando pedidos…" />
      ) : items.length === 0 ? (
        hayFiltros ? (
          <EstadoVacio icono="search" titulo="Ningún pedido coincide" texto="Prueba con otra tienda, estado o forma de entrega.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        ) : (
          <EstadoVacio icono="box" titulo="Todavía no hay pedidos"
            texto="Cuando un cliente compre en el catálogo o un taller pida repuestos de una cotización, el pedido aparece aquí." />
        )
      ) : (
        <>
          <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--tarjetas ped-tabla">
                <caption className="ui-sr">Pedidos de las tiendas con su estado, entrega y alertas</caption>
                <thead>
                  <tr>
                    <EncabezadoOrden campo="fecha" orden={orden} onOrdenar={ordenar}>Pedido</EncabezadoOrden>
                    <EncabezadoOrden campo="tienda" orden={orden} onOrdenar={ordenar}>Tienda</EncabezadoOrden>
                    <EncabezadoOrden campo="estado" orden={orden} onOrdenar={ordenar}>Estado</EncabezadoOrden>
                    <th scope="col">Entrega</th>
                    <th scope="col">Destino</th>
                    <th scope="col" className="pp-num">Artículos</th>
                    <th scope="col" className="pp-num">Días desde el pago</th>
                    <th scope="col">Alerta</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((p) => {
                    const chips = chipsAlerta(p);
                    return (
                      <tr key={p.id} className={filaConAlerta(p) ? "ped-fila--alerta" : undefined}>
                        <td className="ui-table__titulo">
                          {puedeVerDetalle ? (
                            <button type="button" className="ped-ref ped-ref--boton" onClick={() => setAbierto(p.id)}
                              aria-label={`Ver el detalle del pedido ${refPedido(p.id)}`}>{refPedido(p.id)}</button>
                          ) : (
                            <strong className="ped-ref">{refPedido(p.id)}</strong>
                          )}
                          <span className="pp-sub">{p.origen === "TALLER" ? "Cotización de taller" : "Compra del catálogo"} · pagado {fecha(p.fechaPago)}</span>
                        </td>
                        <td data-label="Tienda">{p.tienda || <span className="pp-tenue">Sin tienda</span>}</td>
                        <td data-label="Estado"><ChipEstado catalogo={ESTADOS_PEDIDO} estado={p.estado} /></td>
                        <td data-label="Entrega">{p.entrega === "ENVIO" ? "Envío" : p.entrega === "RETIRO" ? "Retiro" : p.origen === "TALLER" ? "Al taller" : <span className="pp-tenue">Sin definir</span>}</td>
                        <td data-label="Destino" className="ped-destino">{p.destino || <span className="pp-tenue">Sin destino</span>}</td>
                        <td data-label="Artículos" className="pp-num">{p.articulos}</td>
                        <td data-label="Días desde el pago" className="pp-num">{p.diasDesdePago}</td>
                        <td data-label="Alerta">
                          <span className="ped-alertas">
                            {chips.map((c) => <ChipEstado key={c.texto} tono={c.tono}>{c.texto}</ChipEstado>)}
                            {chips.length === 0 && <span className="pp-tenue">Sin alertas</span>}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
          <Paginacion {...propsPaginacion(lista)} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de pedidos" />
        </>
      )}

      {abierto && <DetallePlataforma key={abierto} token={token} objetivo={{ tipo: "pedido", id: abierto }} onCerrar={() => setAbierto(null)} />}
    </div>
  );
}
