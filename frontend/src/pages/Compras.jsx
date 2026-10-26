import { useCallback, useEffect, useMemo, useState } from "react";
import { listarCompras } from "../services/compraService";
import { listarProveedores } from "../services/proveedorService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import Campo from "../components/ui/Campo";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import { filtrarCompras, refCompra, resumenCompras, unidades } from "./tienda/comprasTienda";
import { plural } from "../utils/formato";
import "../css/compras-tienda.css";

const POR_PAGINA = 15;
const SIN_FILTROS = { busqueda: "", desde: "", hasta: "", minimo: "", maximo: "" };

const fecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

/** Compras a proveedores de la tienda: cada una suma el stock al registrarla. */
function Compras({ onNueva, onDetalle, token }) {
  const [compras, setCompras] = useState([]);
  const [proveedores, setProveedores] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [filtros, setFiltros] = useState(SIN_FILTROS);
  const [masFiltros, setMasFiltros] = useState(false);
  const [pagina, setPagina] = useState(1);
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
  const totalPaginas = Math.max(1, Math.ceil(visibles.length / POR_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas);
  const enPagina = visibles.slice((paginaActual - 1) * POR_PAGINA, paginaActual * POR_PAGINA);
  const filtrosExtra = [filtros.desde || filtros.hasta, filtros.minimo !== "" || filtros.maximo !== ""].filter(Boolean).length;
  const hayFiltros = filtros.busqueda || filtrosExtra > 0;

  const filtrar = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(1); };
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
        descripcion="Lo que compras a tus proveedores. Al registrar una compra se suma el stock y, si el proveedor tiene correo, le enviamos el PDF." />

      {error && (
        <Alerta className="cmp-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar tus compras. {error}
        </Alerta>
      )}

      {!error && compras.length === 0 ? (
        <EstadoVacio icono="box" titulo="Aún no registras compras"
          texto="Registra lo que te llega de un proveedor: el stock de cada producto se suma solo y el proveedor recibe el PDF.">
          {nueva}
        </EstadoVacio>
      ) : compras.length > 0 && (
        <>
          <section aria-labelledby="cmp-resumen" className="cmp-alerta">
            <h2 id="cmp-resumen" className="ui-sr">Resumen de compras</h2>
            <div className="ui-indicadores">
              <Indicador titulo="Compras" valor={datos.total} pie={`${plural(datos.unidades, "unidad", "unidades")} en total`} />
              <Indicador titulo="Este mes" valor={formatPrecio(datos.mes)}
                pie={datos.comprasMes > 0 ? `${datos.comprasMes} ${datos.comprasMes === 1 ? "compra" : "compras"}` : "Sin compras este mes"} />
              <Indicador titulo="Total gastado" valor={formatPrecio(datos.gastado)} pie="En todo el historial" />
            </div>
          </section>

          <div className="cmp-filtros" role="search" aria-label="Filtrar compras">
            <div className="cmp-filtros__fila">
              <div className="ui-buscador">
                <Icon name="search" size={18} />
                <label htmlFor="cmp-busqueda" className="ui-sr">Buscar por referencia, proveedor o producto</label>
                <input id="cmp-busqueda" className="ui-field__input" type="search" placeholder="Referencia, proveedor o producto"
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
                            <span className="cmp-tenue"> · {(c.productos || []).length} {(c.productos || []).length === 1 ? "producto" : "productos"}</span>
                          </td>
                          <td data-label="Proveedor">{nombreProveedor(c.proveedorId) || <span className="cmp-tenue">Proveedor eliminado</span>}</td>
                          <td data-label="Fecha">{fecha(c.fecha)}</td>
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
              <nav className="cmp-paginas" aria-label="Páginas de compras">
                <span>Mostrando {(paginaActual - 1) * POR_PAGINA + 1}–{Math.min(paginaActual * POR_PAGINA, visibles.length)} de {visibles.length}</span>
                {totalPaginas > 1 && (
                  <span className="cmp-paginas__botones">
                    <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={paginaActual === 1}
                      onClick={() => setPagina(paginaActual - 1)}>Anterior</button>
                    <span aria-current="page">Página {paginaActual} de {totalPaginas}</span>
                    <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={paginaActual === totalPaginas}
                      onClick={() => setPagina(paginaActual + 1)}>Siguiente</button>
                  </span>
                )}
              </nav>
            </>
          )}
        </>
      )}
    </div>
  );
}

export default Compras;
