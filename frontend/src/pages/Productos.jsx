import { useCallback, useEffect, useMemo, useState } from "react";
import { listarProductos, listarProductosMiTienda, eliminarProducto, cambiarPublicadoProducto } from "../services/productoService";
import Interruptor from "../components/ui/Interruptor";
import { miTienda } from "../services/tiendaService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import EstadoNegocio from "../components/EstadoNegocio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
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

// esTienda: el usuario es una tienda del marketplace y solo ve su propio catálogo.
function Productos({ onNuevo, onEditar, token, esTienda = false }) {
  const [productos, setProductos] = useState([]);
  const [tienda, setTienda] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [filtro, setFiltro] = useState("TODOS");
  const [busqueda, setBusqueda] = useState("");
  const [eliminando, setEliminando] = useState(null); // producto a confirmar
  const [aviso, setAviso] = useState(null);
  const [cambiando, setCambiando] = useState(null); // id del producto que se publica u oculta

  const cargar = useCallback(async () => {
    try {
      if (esTienda) {
        // Si la tienda aún no está aprobada, sus productos responden 403 y se muestra
        // el aviso de estado en lugar del error.
        const [t, p] = await Promise.allSettled([miTienda(token), listarProductosMiTienda(token)]);
        if (t.status === "fulfilled") setTienda(t.value);
        if (p.status === "fulfilled") { setProductos(p.value || []); setError(null); }
        else setError(p.reason.message);
      } else {
        setProductos((await listarProductos(token)) || []);
        setError(null);
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, esTienda]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const reintentar = async () => {
    setReintentando(true);
    await cargar();
    setReintentando(false);
  };

  const confirmarEliminar = async () => {
    await eliminarProducto(eliminando.id, token);
    setProductos((prev) => prev.filter((p) => p.id !== eliminando.id));
    setAviso({ tono: "ok", texto: `Eliminaste “${eliminando.nombre}”.` });
    setEliminando(null);
  };

  // Publicar u ocultar: el cambio se ve en la fila cuando el backend lo confirma; el aviso dice qué pasó
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
  // 20 por página; vuelve a la primera al cambiar de filtro o de búsqueda (los contadores siguen contando todo)
  const pag = usePaginacion(visibles.length, filtro + "|" + busqueda);
  const enPagina = paginar(visibles, pag.pagina);

  const titulo = "Productos";
  const descripcion = tienda
    ? `El catálogo de ${tienda.nombre}: precio, stock y si se ve en el catálogo. Lo que necesita atención va primero.`
    : "Inventario centralizado de refacciones y consumibles automotrices.";
  const agregar = (
    <button type="button" className="ui-btn ui-btn--primario" onClick={onNuevo}>
      <Icon name="plus" size={16} /> Agregar producto
    </button>
  );

  if (cargando) {
    return (
      <div className="ss-panel--tienda prt-root">
        <EncabezadoPagina titulo={titulo} descripcion="Tu catálogo e inventario." />
        <Esqueleto variante="lista" cantidad={4} texto="Cargando productos…" />
      </div>
    );
  }

  return (
    <EstadoNegocio negocio={tienda} tipo="tienda">
      <div className="ss-panel--tienda prt-root">
        <EncabezadoPagina titulo={titulo} descripcion={descripcion} acciones={productos.length > 0 && agregar} />

        {error && (
          <Alerta className="prt-alerta" onReintentar={reintentar} reintentando={reintentando}>
            No pudimos cargar tus productos. {error}
          </Alerta>
        )}
        {aviso && <Alerta tono={aviso.tono} className="prt-alerta" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>}

        {!error && productos.length === 0 ? (
          <EstadoVacio icono="box" titulo="Aún no tienes productos"
            texto="Agrega tus repuestos con foto, precio y stock: aparecen en el catálogo y los talleres pueden pedirlos.">
            {agregar}
          </EstadoVacio>
        ) : productos.length > 0 && (
          <>
            <section aria-labelledby="prt-resumen" className="prt-indicadores">
              <h2 id="prt-resumen" className="ui-sr">Resumen del catálogo</h2>
              <div className="ui-indicadores">
                <Indicador icono="box" titulo="Productos" valor={datos.total} pie={`${datos.publicados} publicados en el catálogo`} />
                <Indicador icono="alert" titulo="Necesitan atención" valor={datos.atencion}
                  tono={datos.atencion > 0 ? "accion" : undefined}
                  pie={datos.atencion > 0 ? "Agotados o con poco stock" : "Todo con stock suficiente"}
                  onClick={datos.atencion > 0 ? () => setFiltro(conteo.AGOTADOS > 0 ? "AGOTADOS" : "POCO") : undefined} />
                <Indicador titulo="Valor del inventario" valor={formatPrecio(datos.valor)}
                  ayuda="Lo que recibirías si vendes todo el stock a tu precio de venta (sin IVA). No es lo que te costó comprarlo."
                  pie={datos.valorCosto > 0 ? `Precio de venta × stock · A costo: ${formatPrecio(datos.valorCosto)}` : "Precio de venta × stock"} />
              </div>
            </section>

            <div className="prt-barra">
              <div className="ui-filtros" role="group" aria-label="Filtrar productos">
                {FILTROS.map((f) => (
                  <button key={f.key} type="button" className="ui-chip" aria-pressed={filtro === f.key} onClick={() => setFiltro(f.key)}>
                    {f.label}
                    {conteo[f.key] > 0 && <span className="ui-chip__contador">{conteo[f.key]}</span>}
                  </button>
                ))}
              </div>
              <div className="ui-buscador prt-buscador">
                <Icon name="search" size={18} />
                <label htmlFor="prt-busqueda" className="ui-sr">Buscar por nombre, código, marca o categoría</label>
                <input id="prt-busqueda" className="ui-field__input" type="search" value={busqueda} autoComplete="off"
                  onChange={(e) => setBusqueda(e.target.value)} placeholder="Nombre, código, marca o categoría" />
              </div>
            </div>

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
                        <tr key={p.id}>
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
                                etiqueta={`Publicar “${p.nombre}” en el catálogo`} onCambiar={(v) => cambiarPublicado(p, v)} />
                              {publicado(p) && !porEncargo(p) && nivelStock(p) === "agotado" && <ChipEstado tono="aviso">Sin stock</ChipEstado>}
                            </span>
                          </td>
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onEditar(p.id)}
                              aria-label={`Editar ${p.nombre}`}>
                              <Icon name="pencil" size={14} /> Editar
                            </button>
                            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico prt-eliminar" onClick={() => setEliminando(p)}
                              aria-label={`Eliminar ${p.nombre}`}>
                              <Icon name="trash" size={14} /> Eliminar
                            </button>
                          </td>
                        </tr>
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
    </EstadoNegocio>
  );
}

export default Productos;
