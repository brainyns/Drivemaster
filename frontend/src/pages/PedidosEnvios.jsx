import { useCallback, useEffect, useState } from "react";
import { pedidosPlataforma } from "../services/plataformaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import DetallePlataforma from "../components/admin/DetallePlataforma";
import {
  ALERTAS_PEDIDO, ESTADOS_PEDIDO, chipsAlerta, estadosConPedidos, filaConAlerta, filtrosInicialesPedidos, refPedido,
} from "./admin/plataformaAdmin";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/pedidos-plataforma.css";

const TAMANO = 20;
const REFRESCO_MS = 30000;

const ENTREGAS = [
  { valor: "", label: "Envío y retiro" },
  { valor: "ENVIO", label: "Envío a domicilio" },
  { valor: "RETIRO", label: "Retiro en tienda" },
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
  const [estado, setEstado] = useState({ datos: null, error: null, actualizado: null });
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    return pedidosPlataforma(token, { ...filtros, pagina, tamano: TAMANO })
      .then((datos) => setEstado({ datos, error: null, actualizado: new Date() }))
      .catch((e) => setEstado((previo) => ({ ...previo, error: e.message })))
      .finally(() => setCargando(false));
  }, [token, filtros, pagina]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const intervalo = setInterval(cargar, REFRESCO_MS);
    return () => { clearTimeout(inicial); clearInterval(intervalo); };
  }, [cargar]);

  const { datos, error, actualizado } = estado;
  const lista = datos?.pedidos;
  const items = lista?.items || [];
  const total = lista?.total || 0;
  const hayFiltros = filtros.estado !== "" || filtros.tiendaId !== "" || filtros.entrega !== "" || filtros.alerta !== "";
  const alternarAlerta = (valor) => poner({ alerta: filtros.alerta === valor ? "" : valor });
  const totalPedidos = Object.values(datos?.porEstado || {}).reduce((s, n) => s + n, 0);

  const poner = (cambio) => { setPagina(0); setFiltros((f) => ({ ...f, ...cambio })); };

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

      {datos && (datos.atrasados > 0 || datos.porVencer > 0 || datos.sinRetirar > 0 || datos.problemasAbiertos > 0) && (
        <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Pedidos que piden atención">
          {datos.atrasados > 0 && (
            <button type="button" className="ui-chip ped-alerta" aria-pressed={filtros.alerta === "ATRASADO"}
              onClick={() => alternarAlerta("ATRASADO")}>
              <Icon name="clock" size={15} />
              {plural(datos.atrasados, "pedido sin despachar", "pedidos sin despachar")} fuera del plazo
            </button>
          )}
          {datos.porVencer > 0 && (
            <button type="button" className="ui-chip ped-alerta ped-alerta--aviso" aria-pressed={filtros.alerta === "POR_VENCER"}
              onClick={() => alternarAlerta("POR_VENCER")}>
              <Icon name="clock" size={15} />
              {plural(datos.porVencer, "despacho por vencer", "despachos por vencer")}
            </button>
          )}
          {datos.sinRetirar > 0 && (
            <button type="button" className="ui-chip ped-alerta ped-alerta--aviso" aria-pressed={filtros.alerta === "SIN_RETIRAR"}
              onClick={() => alternarAlerta("SIN_RETIRAR")}>
              <Icon name="box" size={15} />
              {plural(datos.sinRetirar, "pedido sin retirar", "pedidos sin retirar")} en la tienda
            </button>
          )}
          {datos.problemasAbiertos > 0 && (
            <button type="button" className="ui-chip ped-alerta" aria-pressed={filtros.estado === "PROBLEMA"}
              onClick={() => poner({ estado: filtros.estado === "PROBLEMA" ? "" : "PROBLEMA" })}>
              <Icon name="alert" size={15} />
              {plural(datos.problemasAbiertos, "problema de envío abierto", "problemas de envío abiertos")}
            </button>
          )}
        </div>
      )}

      {datos && (
        <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Estado del pedido">
          <button type="button" className="ui-chip" aria-pressed={filtros.estado === ""} onClick={() => poner({ estado: "" })}>
            Todos <span className="ui-chip__contador">{totalPedidos}</span>
          </button>
          {estadosConPedidos(datos.porEstado).map((e) => (
            <button key={e} type="button" className="ui-chip" aria-pressed={filtros.estado === e} onClick={() => poner({ estado: e })}>
              {ESTADOS_PEDIDO[e]?.label || e} <span className="ui-chip__contador">{datos.porEstado[e]}</span>
            </button>
          ))}
        </div>
      )}

      <div className="pp-filtros">
        <label className="pp-filtros__select">
          Tienda
          <select className="ui-field__input" value={filtros.tiendaId} onChange={(e) => poner({ tiendaId: e.target.value })}>
            <option value="">Todas</option>
            {(datos?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <div className="ui-filtros" role="group" aria-label="Entrega">
          {ENTREGAS.map((x) => (
            <button key={x.valor || "todas"} type="button" className="ui-chip" aria-pressed={filtros.entrega === x.valor}
              onClick={() => poner({ entrega: x.valor })}>{x.label}</button>
          ))}
        </div>
        <div className="ui-filtros" role="group" aria-label="Alerta">
          {ALERTAS_PEDIDO.map((a) => (
            <button key={a.valor || "todas"} type="button" className="ui-chip" aria-pressed={filtros.alerta === a.valor}
              onClick={() => poner({ alerta: a.valor })}>{a.valor ? a.label : "Todas las alertas"}</button>
          ))}
        </div>
        {hayFiltros && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => poner(filtrosInicialesPedidos())}>Limpiar filtros</button>
        )}
      </div>

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
            <button type="button" className="ui-btn ui-btn--secundario" onClick={() => poner(filtrosInicialesPedidos())}>Limpiar filtros</button>
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
                    <th scope="col">Pedido</th>
                    <th scope="col">Tienda</th>
                    <th scope="col">Estado</th>
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
          <Paginacion pagina={pagina + 1} total={total} porPagina={lista?.tamano || TAMANO} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de pedidos" />
        </>
      )}

      {abierto && <DetallePlataforma key={abierto} token={token} objetivo={{ tipo: "pedido", id: abierto }} onCerrar={() => setAbierto(null)} />}
    </div>
  );
}
