import { useCallback, useEffect, useMemo, useState } from "react";
import { listarProductos, inventarioTabla, eliminarProducto, cambiarPublicadoProducto } from "../services/productoService";
import Interruptor from "../components/ui/Interruptor";
import { miTienda } from "../services/tiendaService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EstadoNegocio from "../components/EstadoNegocio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import { propsPaginacion, textoOrden, useDiferido } from "../components/ui/reglasTabla";
import EncabezadoOrden from "../components/ui/EncabezadoOrden";
import SelectorOrden from "../components/ui/SelectorOrden";
import Indicador from "../components/ui/Indicador";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import Icon from "../components/public/Icon";
import { FILTROS, NIVEL, coincide, contar, nivelStock, ordenar, porEncargo, publicado, resumen } from "./tienda/productosTienda";
import "../css/productos.css";
import { etiquetaCategoria } from "../utils/categorias";
import { plural } from "../utils/formato";

/** Stock con su número y, si pide atención, el chip con texto (el color nunca va solo). */
function Stock({ p }) {
  const nivel = nivelStock(p);
  if (nivel === "encargo") {
    return <ChipEstado tono="info">Por encargo{p.diasEntregaEncargo ? ` · ${plural(p.diasEntregaEncargo, "día")}` : ""}</ChipEstado>;
  }
  return (
    <span className="prt-stock">
      <strong className={`prt-stock__num prt-stock__num--${nivel}`}>{p.stockActual ?? 0}</strong>
      <span className="prt-stock__min">{p.stockMinimo != null ? `mín. ${p.stockMinimo}` : "sin mínimo"}</span>
      {nivel !== "ok" && <ChipEstado tono={NIVEL[nivel].tono}>{NIVEL[nivel].texto}</ChipEstado>}
    </span>
  );
}

/** Columnas que se pueden ordenar en el servidor (lista blanca de TablasTiendaService). */
const ORDENES_INVENTARIO = [
  { campo: "nombre", label: "Nombre", asc: "(A–Z)", desc: "(Z–A)" },
  { campo: "precio", label: "Precio" },
  { campo: "stock", label: "Stock" },
];

function FilaProducto({ p, cambiando, onPublicar, onEditar, onEliminar }) {
  return (
    <tr>
      <td data-label="Producto" className="ui-table__titulo">
        <span className="prt-producto">
          <ImagenProducto src={p.imagenUrl} className="prt-producto__imagen" tamanioIcono={20} />
          <span className="prt-producto__texto">
            <strong>{p.nombre}</strong>
            <span>{[p.marca, p.referencia, p.codigo].filter(Boolean).join(" · ") || "Sin marca ni código"}</span>
          </span>
        </span>
      </td>
      <td data-label="Categoría">{etiquetaCategoria(p.categoria) || <span className="prt-tenue">Sin categoría</span>}</td>
      <td data-label="Precio" className="ui-table__num"><span className="ui-precio">{formatPrecio(p.precioVenta)}</span></td>
      <td data-label="Stock"><Stock p={p} /></td>
      <td data-label="Catálogo">
        <span className="prt-catalogo">
          <Interruptor activo={publicado(p)} textos={["Publicado", "Oculto"]} ocupado={cambiando === p.id}
            etiqueta={`Publicar “${p.nombre}” en el catálogo`} onCambiar={(v) => onPublicar(p, v)} />
          {publicado(p) && !porEncargo(p) && nivelStock(p) === "agotado" && <ChipEstado tono="aviso">Sin stock</ChipEstado>}
        </span>
      </td>
      <td className="ui-table__acciones">
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onEditar(p.id)}
          aria-label={`Editar ${p.nombre}`}>
          <Icon name="pencil" size={14} /> Editar
        </button>
        <button type="button" className="ui-btn ui-btn--texto ui-btn--chico prt-eliminar" onClick={() => onEliminar(p)}
          aria-label={`Eliminar ${p.nombre}`}>
          <Icon name="trash" size={14} /> Eliminar
        </button>
      </td>
    </tr>
  );
}

function Indicadores({ datos, conteo, onFiltro }) {
  return (
    <section aria-labelledby="prt-resumen" className="prt-indicadores">
      <h2 id="prt-resumen" className="ui-sr">Resumen del catálogo</h2>
      <div className="ui-indicadores">
        <Indicador icono="box" titulo="Productos" valor={datos.total} pie={`${datos.publicados} publicados en el catálogo`} />
        <Indicador icono="alert" titulo="Necesitan atención" valor={datos.atencion}
          tono={datos.atencion > 0 ? "accion" : undefined}
          pie={datos.atencion > 0 ? "Agotados o con poco stock" : "Todo con stock suficiente"}
          onClick={datos.atencion > 0 ? () => onFiltro(conteo.AGOTADOS > 0 ? "AGOTADOS" : "POCO") : undefined} />
        <Indicador titulo="Valor del inventario" valor={formatPrecio(datos.valor)}
          ayuda="Lo que recibirías si vendes todo el stock a tu precio de venta (sin IVA). No es lo que te costó comprarlo."
          pie={datos.valorCosto > 0 ? `Precio de venta × stock · A costo: ${formatPrecio(datos.valorCosto)}` : "Precio de venta × stock"} />
      </div>
    </section>
  );
}

function BarraFiltros({ filtro, conteo, onFiltro, busqueda, onBuscar }) {
  return (
    <div className="prt-barra">
      <div className="ui-filtros" role="group" aria-label="Filtrar productos">
        {FILTROS.map((f) => (
          <button key={f.key} type="button" className="ui-chip" aria-pressed={filtro === f.key} onClick={() => onFiltro(f.key)}>
            {f.label}
            {conteo[f.key] > 0 && <span className="ui-chip__contador">{conteo[f.key]}</span>}
          </button>
        ))}
      </div>
      <div className="ui-buscador prt-buscador">
        <Icon name="search" size={18} />
        <label htmlFor="prt-busqueda" className="ui-sr">Buscar por nombre, código, marca o categoría</label>
        <input id="prt-busqueda" className="ui-field__input" type="search" value={busqueda} autoComplete="off"
          onChange={(e) => onBuscar(e.target.value)} placeholder="Nombre, código, marca o categoría" />
      </div>
    </div>
  );
}

const agregarBoton = (onNuevo) => (
  <button type="button" className="ui-btn ui-btn--primario" onClick={onNuevo}>
    <Icon name="plus" size={16} /> Agregar producto
  </button>
);

/**
 * Inventario de la tienda, paginado en el servidor (/api/productos/mi-tienda/tabla): filtro, búsqueda, orden y página
 * se resuelven allá sobre todos sus productos; los contadores y los indicadores siguen contando todo el catálogo.
 */
function InventarioTienda({ onNuevo, onEditar, token }) {
  const [tienda, setTienda] = useState(null);
  const [datos, setDatos] = useState(null); // { productos: Pagina, conteo, resumen }
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [filtro, setFiltro] = useState("TODOS");
  const [busqueda, setBusqueda] = useState("");
  const q = useDiferido(busqueda.trim());
  const [orden, setOrden] = useState(null); // null: lo que pide atención primero
  const [pagina, setPagina] = useState(0);
  const [eliminando, setEliminando] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [cambiando, setCambiando] = useState(null);

  // Si la tienda aún no está aprobada, sus productos responden 403 y se muestra el aviso de estado
  useEffect(() => {
    let vivo = true;
    miTienda(token).then((t) => { if (vivo) setTienda(t); }).catch(() => {});
    return () => { vivo = false; };
  }, [token]);

  const cargar = useCallback(async (vivo = () => true) => {
    setCargando(true);
    try {
      const r = await inventarioTabla(token, { filtro, q, page: pagina, size: 20, sort: textoOrden(orden) });
      if (!vivo()) return;
      setDatos(r);
      setError(null);
    } catch (e) {
      if (vivo()) setError(e.message);
    } finally {
      if (vivo()) setCargando(false);
    }
  }, [token, filtro, q, pagina, orden]);

  useEffect(() => {
    let activo = true;
    const id = setTimeout(() => cargar(() => activo), 0);
    return () => { activo = false; clearTimeout(id); };
  }, [cargar]);

  // Cambiar filtro, búsqueda u orden vuelve a la primera página
  const poner = (accion) => { accion(); setPagina(0); };
  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const confirmarEliminar = async () => {
    await eliminarProducto(eliminando.id, token);
    setAviso({ tono: "ok", texto: `Eliminaste “${eliminando.nombre}”.` });
    setEliminando(null);
    await cargar();
  };

  const cambiarPublicado = async (p, publicar) => {
    setCambiando(p.id);
    setAviso(null);
    try {
      await cambiarPublicadoProducto(p.id, publicar, token);
      setAviso(publicar
        ? { tono: "ok", texto: `“${p.nombre}” ya se ve en el catálogo.` }
        : { tono: "ok", texto: `“${p.nombre}” quedó oculto: no sale en el catálogo ni se puede comprar.` });
      await cargar();
    } catch (e) {
      setAviso({ tono: "error", texto: e.message });
    } finally {
      setCambiando(null);
    }
  };

  const lista = datos?.productos;
  const items = lista?.items || [];
  const conteo = datos?.conteo || {};
  const resumenTienda = datos?.resumen || { total: 0, publicados: 0, atencion: 0, valor: 0, valorCosto: 0 };
  const sinProductos = datos && resumenTienda.total === 0;
  const descripcion = tienda
    ? `El catálogo de ${tienda.nombre}: precio, stock y si se ve en el catálogo. Lo que necesita atención va primero.`
    : "Tu catálogo e inventario.";

  if (!datos && !error) {
    return (
      <div className="ss-panel--tienda prt-root">
        <EncabezadoPagina titulo="Productos" descripcion="Tu catálogo e inventario." />
        <Esqueleto variante="lista" cantidad={4} texto="Cargando productos…" />
      </div>
    );
  }

  return (
    <EstadoNegocio negocio={tienda} tipo="tienda">
      <div className="ss-panel--tienda prt-root">
        <EncabezadoPagina titulo="Productos" descripcion={descripcion} acciones={!sinProductos && datos && agregarBoton(onNuevo)} />

        {error && (
          <Alerta className="prt-alerta" onReintentar={reintentar} reintentando={reintentando}>
            No pudimos cargar tus productos. {error}
          </Alerta>
        )}
        {aviso && <Alerta tono={aviso.tono} className="prt-alerta" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>}

        {sinProductos ? (
          <EstadoVacio icono="box" titulo="Aún no tienes productos"
            texto="Agrega tus repuestos con foto, precio y stock: aparecen en el catálogo y los talleres pueden pedirlos.">
            {agregarBoton(onNuevo)}
          </EstadoVacio>
        ) : datos && (
          <>
            <Indicadores datos={resumenTienda} conteo={conteo} onFiltro={(f) => poner(() => setFiltro(f))} />
            <BarraFiltros filtro={filtro} conteo={conteo} onFiltro={(f) => poner(() => setFiltro(f))}
              busqueda={busqueda} onBuscar={(v) => poner(() => setBusqueda(v))} />
            <SelectorOrden id="prt-orden" opciones={ORDENES_INVENTARIO} orden={orden} porDefecto="Lo que pide atención primero"
              onOrdenar={(o) => poner(() => setOrden(o))} />

            {items.length === 0 ? (
              <EstadoVacio icono="search" titulo="Ningún producto coincide"
                texto={q ? `No encontramos “${q}” con este filtro.` : "No hay productos con este filtro."}>
                <button type="button" className="ui-btn ui-btn--secundario" onClick={() => poner(() => { setBusqueda(""); setFiltro("TODOS"); })}>
                  Ver todos los productos
                </button>
              </EstadoVacio>
            ) : (
              <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando} id="prt-tabla">
                <div className="ui-table-wrap">
                  <table className="ui-table ui-table--tarjetas prt-tabla">
                    <caption className="ui-sr">Productos ({lista.total} de {resumenTienda.total})</caption>
                    <thead>
                      <tr>
                        <EncabezadoOrden campo="nombre" orden={orden} onOrdenar={(o) => poner(() => setOrden(o))}>Producto</EncabezadoOrden>
                        <th scope="col">Categoría</th>
                        <EncabezadoOrden campo="precio" orden={orden} onOrdenar={(o) => poner(() => setOrden(o))} className="ui-table__num">Precio</EncabezadoOrden>
                        <EncabezadoOrden campo="stock" orden={orden} onOrdenar={(o) => poner(() => setOrden(o))}>Stock</EncabezadoOrden>
                        <th scope="col">Catálogo</th>
                        <th scope="col"><span className="ui-sr">Acciones</span></th>
                      </tr>
                    </thead>
                    <tbody>
                      {items.map((p) => (
                        <FilaProducto key={p.id} p={p} cambiando={cambiando} onPublicar={cambiarPublicado}
                          onEditar={onEditar} onEliminar={setEliminando} />
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
            <Paginacion {...propsPaginacion(lista)} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de productos" ancla="prt-tabla" />
          </>
        )}

        {eliminando && (
          <ConfirmarAccion titulo={`¿Eliminar “${eliminando.nombre}”?`} peligro textoConfirmar="Eliminar"
            mensaje="Sale de tu catálogo y de tu inventario. Esta acción no se puede deshacer."
            onConfirmar={confirmarEliminar} onCancelar={() => setEliminando(null)} />
        )}
      </div>
    </EstadoNegocio>
  );
}

/** El inventario general de antes (SUPERADMIN y VENDEDOR del modelo anterior): lista completa, paginada en el navegador. */
function ProductosGenerales({ onNuevo, onEditar, token }) {
  const [productos, setProductos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [filtro, setFiltro] = useState("TODOS");
  const [busqueda, setBusqueda] = useState("");
  const [eliminando, setEliminando] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [cambiando, setCambiando] = useState(null);

  const cargar = useCallback(async () => {
    try {
      setProductos((await listarProductos(token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const confirmarEliminar = async () => {
    await eliminarProducto(eliminando.id, token);
    setProductos((prev) => prev.filter((p) => p.id !== eliminando.id));
    setAviso({ tono: "ok", texto: `Eliminaste “${eliminando.nombre}”.` });
    setEliminando(null);
  };

  const cambiarPublicado = async (p, publicar) => {
    setCambiando(p.id);
    setAviso(null);
    try {
      const actualizado = await cambiarPublicadoProducto(p.id, publicar, token);
      setProductos((prev) => prev.map((x) => (x.id === p.id ? { ...x, activo: actualizado.activo } : x)));
      setAviso(publicar
        ? { tono: "ok", texto: `“${p.nombre}” ya se ve en el catálogo.` }
        : { tono: "ok", texto: `“${p.nombre}” quedó oculto: no sale en el catálogo ni se puede comprar.` });
    } catch (e) {
      setAviso({ tono: "error", texto: e.message });
    } finally {
      setCambiando(null);
    }
  };

  const conteo = useMemo(() => contar(productos), [productos]);
  const datos = useMemo(() => resumen(productos), [productos]);
  const visibles = useMemo(() => {
    const f = FILTROS.find((x) => x.key === filtro) || FILTROS[0];
    return ordenar(productos.filter(f.filtro).filter((p) => coincide(p, busqueda)));
  }, [productos, filtro, busqueda]);
  const pag = usePaginacion(visibles.length, filtro + "|" + busqueda);
  const enPagina = paginar(visibles, pag.pagina);

  if (cargando) {
    return (
      <div className="ss-panel--tienda prt-root">
        <EncabezadoPagina titulo="Productos" descripcion="Tu catálogo e inventario." />
        <Esqueleto variante="lista" cantidad={4} texto="Cargando productos…" />
      </div>
    );
  }

  return (
    <div className="ss-panel--tienda prt-root">
      <EncabezadoPagina titulo="Productos" descripcion="Inventario centralizado de refacciones y consumibles automotrices."
        acciones={productos.length > 0 && agregarBoton(onNuevo)} />
      {error && (
        <Alerta className="prt-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar tus productos. {error}
        </Alerta>
      )}
      {aviso && <Alerta tono={aviso.tono} className="prt-alerta" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>}
      {!error && productos.length === 0 ? (
        <EstadoVacio icono="box" titulo="Aún no tienes productos"
          texto="Agrega tus repuestos con foto, precio y stock: aparecen en el catálogo y los talleres pueden pedirlos.">
          {agregarBoton(onNuevo)}
        </EstadoVacio>
      ) : productos.length > 0 && (
        <>
          <Indicadores datos={datos} conteo={conteo} onFiltro={setFiltro} />
          <BarraFiltros filtro={filtro} conteo={conteo} onFiltro={setFiltro} busqueda={busqueda} onBuscar={setBusqueda} />
          {visibles.length === 0 ? (
            <EstadoVacio icono="search" titulo="Ningún producto coincide"
              texto={busqueda.trim() ? `No encontramos “${busqueda.trim()}” con este filtro.` : "No hay productos con este filtro."}>
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => { setBusqueda(""); setFiltro("TODOS"); }}>
                Ver todos los productos
              </button>
            </EstadoVacio>
          ) : (
            <div className="ui-card ui-table-card">
              <div className="ui-table-wrap">
                <table className="ui-table ui-table--tarjetas prt-tabla">
                  <caption className="ui-sr">Productos ({visibles.length} de {productos.length})</caption>
                  <thead>
                    <tr>
                      <th scope="col">Producto</th>
                      <th scope="col">Categoría</th>
                      <th scope="col" className="ui-table__num">Precio</th>
                      <th scope="col">Stock</th>
                      <th scope="col">Catálogo</th>
                      <th scope="col"><span className="ui-sr">Acciones</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {enPagina.map((p) => (
                      <FilaProducto key={p.id} p={p} cambiando={cambiando} onPublicar={cambiarPublicado}
                        onEditar={onEditar} onEliminar={setEliminando} />
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
          <Paginacion pagina={pag.pagina} total={visibles.length} onCambiar={pag.cambiar} etiqueta="Páginas de productos" />
        </>
      )}
      {eliminando && (
        <ConfirmarAccion titulo={`¿Eliminar “${eliminando.nombre}”?`} peligro textoConfirmar="Eliminar"
          mensaje="Sale de tu catálogo y de tu inventario. Esta acción no se puede deshacer."
          onConfirmar={confirmarEliminar} onCancelar={() => setEliminando(null)} />
      )}
    </div>
  );
}

// esTienda: el usuario es una tienda del marketplace y solo ve su propio catálogo (paginado en el servidor).
function Productos(props) {
  return props.esTienda ? <InventarioTienda {...props} /> : <ProductosGenerales {...props} />;
}

export default Productos;
