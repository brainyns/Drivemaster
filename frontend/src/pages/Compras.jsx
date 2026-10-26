import { useCallback, useEffect, useMemo, useState } from "react";
import { listarCompras } from "../services/compraService";
import { listarProveedores } from "../services/proveedorService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import Indicador from "../components/ui/Indicador";
import Campo from "../components/ui/Campo";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import ChipEstado from "../components/ui/ChipEstado";
import { ESTADOS_COMPRA, estadoCompra, filtrarCompras, refCompra, resumenCompras, topProveedores, unidades } from "./tienda/comprasTienda";
import { plural } from "../utils/formato";
import "../css/compras-tienda.css";

const SIN_FILTROS = { busqueda: "", desde: "", hasta: "", minimo: "", maximo: "", estado: "" };
const FILTROS_ESTADO = [["", "Todas"], ["PENDIENTE", "Pendientes"], ["RECIBIDA", "Recibidas"], ["CANCELADA", "Canceladas"]];
const nombreMes = (ahora, menos) => new Date(new Date(ahora).getFullYear(), new Date(ahora).getMonth() - menos, 1)
  .toLocaleDateString("es-CO", { month: "long" });

/** "+12 % frente a septiembre" (sube el gasto) o "−8 % …"; sin gasto el mes anterior, null. */
function textoVariacion(v, mes) {
  if (v == null) return null;
  return `${v > 0 ? "+" : v < 0 ? "−" : ""}${Math.abs(v).toLocaleString("es-CO")} % frente a ${mes}`;
}

const fecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

/** Compras a proveedores de la tienda: recibidas (sumaron el stock), pendientes (lo suman al recibirlas) y canceladas. */
function Compras({ onNueva, onDetalle, token }) {
  const [compras, setCompras] = useState([]);
  const [proveedores, setProveedores] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [filtros, setFiltros] = useState(SIN_FILTROS);
  const [masFiltros, setMasFiltros] = useState(false);
  const [ahora] = useState(() => Date.now());

  const cargar = useCallback(async () => {
    try {
      setCompras((await listarCompras(token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    let vivo = true;
    // La compra guarda solo el id del proveedor: el nombre sale de la lista de proveedores de la tienda
    listarProveedores(token).then((l) => { if (vivo) setProveedores(l || []); }).catch(() => {});
    const id = setTimeout(cargar, 0);
    return () => { vivo = false; clearTimeout(id); };
  }, [token, cargar]);

  const nombres = useMemo(() => Object.fromEntries(proveedores.map((p) => [p.id, p.nombre])), [proveedores]);
  const nombreProveedor = useCallback((id) => nombres[id] || null, [nombres]);
  const visibles = useMemo(() => filtrarCompras(compras, filtros, nombreProveedor), [compras, filtros, nombreProveedor]);
  const datos = useMemo(() => resumenCompras(compras, ahora), [compras, ahora]);
  const top = useMemo(() => topProveedores(compras, nombreProveedor), [compras, nombreProveedor]);
  const conteoEstado = useMemo(() => Object.fromEntries(FILTROS_ESTADO.map(([k]) => [k, k ? compras.filter((c) => estadoCompra(c) === k).length : compras.length])), [compras]);
  // 20 por página; vuelve a la primera al cambiar los filtros o la búsqueda
  const pag = usePaginacion(visibles.length, JSON.stringify(filtros));
  const enPagina = paginar(visibles, pag.pagina);
  const filtrosExtra = [filtros.desde || filtros.hasta, filtros.minimo !== "" || filtros.maximo !== ""].filter(Boolean).length;
  const hayFiltros = filtros.busqueda || filtros.estado || filtrosExtra > 0;

  const filtrar = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); };
  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const nueva = (
    <button type="button" className="ui-btn ui-btn--primario" onClick={onNueva}><Icon name="plus" size={16} /> Nueva compra</button>
  );

  if (cargando) {
    return (
      <div className="ss-panel--tienda cmp-root">
        <EncabezadoPagina titulo="Compras" descripcion="Compras a tus proveedores." />
        <Esqueleto variante="lista" cantidad={4} texto="Cargando compras…" />
      </div>
    );
  }

  return (
    <div className="ss-panel--tienda cmp-root">
      <EncabezadoPagina titulo="Compras" acciones={compras.length > 0 && nueva}
        descripcion="Lo que compras a tus proveedores. Una compra recibida suma el stock; una pendiente lo suma cuando la marques como recibida." />

      {error && (
        <Alerta className="cmp-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar tus compras. {error}
        </Alerta>
      )}

      {!error && compras.length === 0 ? (
        <EstadoVacio icono="box" titulo="Aún no registras compras"
          texto="Registra lo que le compras a un proveedor: cuando llega, el stock de cada producto se suma solo y, si tiene correo, recibe el PDF.">
          {nueva}
        </EstadoVacio>
      ) : compras.length > 0 && (
        <>
          <section aria-labelledby="cmp-resumen" className="cmp-alerta">
            <h2 id="cmp-resumen" className="ui-sr">Resumen de compras</h2>
            <div className="ui-indicadores">
              <Indicador titulo="Compras" valor={datos.total} pie={plural(datos.unidades, "unidad recibida", "unidades recibidas")} />
              <Indicador titulo="Este mes" valor={formatPrecio(datos.mes)}
                pie={[datos.comprasMes > 0 ? plural(datos.comprasMes, "compra") : "Sin compras este mes",
                  textoVariacion(datos.variacionMes, nombreMes(ahora, 1))].filter(Boolean).join(" · ")} />
              <Indicador titulo="Total gastado" valor={formatPrecio(datos.gastado)} pie="En todo el historial, sin las canceladas" />
              {datos.pendientes > 0 && (
                <Indicador icono="clock" titulo="Por recibir" valor={datos.pendientes} tono="accion"
                  pie="Compras pendientes: su stock aún no se suma" onClick={() => filtrar({ estado: "PENDIENTE" })} />
              )}
            </div>
          </section>

          {top.length > 0 && (
            <section className="ui-card cmp-alerta cmp-top" aria-labelledby="cmp-top-titulo">
              <div className="ui-card__header">
                <h2 id="cmp-top-titulo" className="ui-card__titulo">A quién más le compras</h2>
                <span className="ui-card__sub">Por monto, sin las canceladas</span>
              </div>
              <ol className="cmp-top__lista">
                {top.map((p, i) => (
                  <li key={p.proveedorId || i} className="cmp-top__fila">
                    <span className="cmp-top__puesto" aria-hidden="true">{i + 1}</span>
                    <span className="cmp-top__nombre"><strong>{p.nombre}</strong><span>{plural(p.compras, "compra")}</span></span>
                    <span className="cmp-top__barra" aria-hidden="true"><span style={{ width: `${(p.total / top[0].total) * 100}%` }} /></span>
                    <strong className="ui-precio">{formatPrecio(p.total)}</strong>
                  </li>
                ))}
              </ol>
            </section>
          )}

          <div className="cmp-filtros" role="search" aria-label="Filtrar compras">
            <div className="ui-filtros" role="group" aria-label="Estado de la compra">
              {FILTROS_ESTADO.map(([k, label]) => (
                <button key={k || "todas"} type="button" className="ui-chip" aria-pressed={filtros.estado === k} onClick={() => filtrar({ estado: k })}>
                  {label}
                  {k && conteoEstado[k] > 0 && <span className="ui-chip__contador">{conteoEstado[k]}</span>}
                </button>
              ))}
            </div>
            <div className="cmp-filtros__fila">
              <div className="ui-buscador">
                <Icon name="search" size={18} />
                <label htmlFor="cmp-busqueda" className="ui-sr">Buscar por referencia, factura, proveedor o producto</label>
                <input id="cmp-busqueda" className="ui-field__input" type="search" placeholder="Referencia, factura, proveedor o producto"
                  value={filtros.busqueda} onChange={(e) => filtrar({ busqueda: e.target.value })} />
              </div>
              <button type="button" className="ui-chip" aria-expanded={masFiltros} aria-controls="cmp-mas-filtros"
                onClick={() => setMasFiltros((v) => !v)}>
                <Icon name="sliders" size={16} /> Fechas y montos
                {filtrosExtra > 0 && <span className="ui-chip__contador">{filtrosExtra}</span>}
              </button>
              {hayFiltros && (
                <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => filtrar(SIN_FILTROS)}>Quitar filtros</button>
              )}
            </div>
            {masFiltros && (
              <div id="cmp-mas-filtros" className="cmp-filtros__extra">
                <Campo id="cmp-desde" label="Desde" className="cmp-filtro">
                  <input className="ui-field__input" type="date" value={filtros.desde} max={filtros.hasta || undefined}
                    onChange={(e) => filtrar({ desde: e.target.value })} />
                </Campo>
                <Campo id="cmp-hasta" label="Hasta" className="cmp-filtro">
                  <input className="ui-field__input" type="date" value={filtros.hasta} min={filtros.desde || undefined}
                    onChange={(e) => filtrar({ hasta: e.target.value })} />
                </Campo>
                <Campo id="cmp-minimo" label="Total mínimo (COP)" className="cmp-filtro">
                  <input className="ui-field__input" type="number" min="0" inputMode="numeric" value={filtros.minimo}
                    onChange={(e) => filtrar({ minimo: e.target.value })} />
                </Campo>
                <Campo id="cmp-maximo" label="Total máximo (COP)" className="cmp-filtro">
                  <input className="ui-field__input" type="number" min="0" inputMode="numeric" value={filtros.maximo}
                    onChange={(e) => filtrar({ maximo: e.target.value })} />
                </Campo>
              </div>
            )}
          </div>

          {visibles.length === 0 ? (
            <EstadoVacio icono="search" titulo="Ninguna compra con estos filtros" texto="Prueba con otra referencia, otro proveedor u otras fechas.">
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => filtrar(SIN_FILTROS)}>Quitar filtros</button>
            </EstadoVacio>
          ) : (
            <>
              <div className="ui-card ui-table-card">
                <div className="ui-table-wrap">
                  <table className="ui-table ui-table--tarjetas">
                    <caption className="ui-sr">Compras, de la más reciente a la más antigua</caption>
                    <thead>
                      <tr>
                        <th scope="col">Compra</th>
                        <th scope="col">Proveedor</th>
                        <th scope="col">Fecha</th>
                        <th scope="col">Estado</th>
                        <th scope="col" className="ui-table__num">Unidades</th>
                        <th scope="col" className="ui-table__num">Total</th>
                        <th scope="col"><span className="ui-sr">Acciones</span></th>
                      </tr>
                    </thead>
                    <tbody>
                      {enPagina.map((c) => (
                        <tr key={c.id}>
                          <td data-label="Compra" className="ui-table__titulo">
                            <strong className="cmp-ref">{refCompra(c.id)}</strong>
                            {c.numeroFactura && <span className="cmp-tenue"> · Factura {c.numeroFactura}</span>}
                            <span className="cmp-tenue"> · {(c.productos || []).length} {(c.productos || []).length === 1 ? "producto" : "productos"}</span>
                          </td>
                          <td data-label="Proveedor">{nombreProveedor(c.proveedorId) || <span className="cmp-tenue">Proveedor eliminado</span>}</td>
                          <td data-label="Fecha">{fecha(c.fecha)}</td>
                          <td data-label="Estado"><ChipEstado estado={estadoCompra(c)} catalogo={ESTADOS_COMPRA} /></td>
                          <td data-label="Unidades" className="ui-table__num">{unidades(c)}</td>
                          <td data-label="Total" className="ui-table__num"><span className="ui-precio">{formatPrecio(c.total)}</span></td>
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onDetalle(c.id)}
                              aria-label={`Ver la compra ${refCompra(c.id)}`}>
                              Ver detalle
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
              <Paginacion pagina={pag.pagina} total={visibles.length} onCambiar={pag.cambiar} etiqueta="Páginas de compras" />
            </>
          )}
        </>
      )}
    </div>
  );
}

export default Compras;
