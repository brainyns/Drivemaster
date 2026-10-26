import { useCallback, useEffect, useMemo, useState } from "react";
import { listarProveedores, eliminarProveedor, exportarProveedores } from "../services/proveedorService";
import { listarCompras } from "../services/compraService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import { ESTADOS_COMPRA, estadoCompra, refCompra, topProveedores } from "./tienda/comprasTienda";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import Indicador from "../components/ui/Indicador";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import LogoNegocio from "../components/ui/LogoNegocio";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import Icon from "../components/public/Icon";
import { coincideProveedor, recibePdf } from "./tienda/proveedoresTienda";
import { formatoFecha, plural } from "../utils/formato";
import "../css/proveedores.css";


/** Directorio de los proveedores de la tienda: a quién le compra y a dónde llega el PDF de cada compra. */
export default function Proveedores({ onNuevo, onEditar, onDetalle, token }) {
  const [proveedores, setProveedores] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [busqueda, setBusqueda] = useState("");
  const [eliminando, setEliminando] = useState(null);
  const [exportando, setExportando] = useState(false);
  const [aviso, setAviso] = useState(null); // { tono, texto }
  // Las compras alimentan la actividad de abajo; si fallan, la lista de proveedores se muestra igual
  const [compras, setCompras] = useState([]);

  const cargar = useCallback(async () => {
    try {
      setProveedores((await listarProveedores(token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    let vivo = true;
    listarCompras(token).then((l) => { if (vivo) setCompras(l || []); }).catch(() => {});
    const id = setTimeout(cargar, 0);
    return () => { vivo = false; clearTimeout(id); };
  }, [cargar, token]);

  const nombres = useMemo(() => Object.fromEntries(proveedores.map((p) => [p.id, p.nombre])), [proveedores]);
  const top = useMemo(() => topProveedores(compras, (pid) => nombres[pid], 5), [compras, nombres]);
  const recientes = useMemo(() => [...compras].sort((a, b) => new Date(b.fecha || 0) - new Date(a.fecha || 0)).slice(0, 5), [compras]);

  const visibles = useMemo(() => proveedores.filter((p) => coincideProveedor(p, busqueda))
    .sort((a, b) => String(a.nombre || "").localeCompare(String(b.nombre || ""), "es")), [proveedores, busqueda]);
  // 20 por página; vuelve a la primera al cambiar la búsqueda
  const pag = usePaginacion(visibles.length, busqueda);
  const enPagina = paginar(visibles, pag.pagina);
  // El correo es opcional: la tarjeta cuenta a quién le llega el PDF y explica por qué a los demás no
  const recibenPdf = proveedores.filter(recibePdf).length;
  const sinCorreo = proveedores.filter((p) => !p.correo).length;
  const apagados = proveedores.length - recibenPdf - sinCorreo;

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const confirmarEliminar = async () => {
    await eliminarProveedor(eliminando.id, token);
    setProveedores((prev) => prev.filter((p) => p.id !== eliminando.id));
    setAviso({ tono: "ok", texto: `Eliminaste a ${eliminando.nombre}.` });
    setEliminando(null);
  };

  const exportar = async () => {
    setExportando(true);
    setAviso(null);
    try {
      await exportarProveedores(token);
    } catch (e) {
      setAviso({ tono: "error", texto: `No pudimos generar el reporte. ${e.message}` });
    } finally {
      setExportando(false);
    }
  };

  const nuevo = (
    <button type="button" className="ui-btn ui-btn--primario" onClick={onNuevo}><Icon name="plus" size={16} /> Nuevo proveedor</button>
  );

  if (cargando) {
    return (
      <div className="ss-panel--tienda prv-root">
        <EncabezadoPagina titulo="Proveedores" descripcion="A quién le compras para surtir tu inventario." />
        <Esqueleto variante="lista" cantidad={4} texto="Cargando proveedores…" />
      </div>
    );
  }

  return (
    <div className="ss-panel--tienda prv-root">
      <EncabezadoPagina titulo="Proveedores"
        descripcion="A quién le compras para surtir tu inventario. Si tiene correo, le puede llegar el PDF de cada compra."
        acciones={proveedores.length > 0 && (
          <>
            <button type="button" className="ui-btn ui-btn--secundario" onClick={exportar} disabled={exportando} aria-busy={exportando}>
              {!exportando && <Icon name="file" size={16} />} {exportando ? "Generando…" : "Descargar reporte"}
            </button>
            {nuevo}
          </>
        )} />

      {error && (
        <Alerta className="prv-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar tus proveedores. {error}
        </Alerta>
      )}
      {aviso && <Alerta tono={aviso.tono} className="prv-alerta" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>}

      {!error && proveedores.length === 0 ? (
        <EstadoVacio icono="users" titulo="Aún no tienes proveedores"
          texto="Agrega a quién le compras: lo eliges al registrar una compra y, si tiene correo, le enviamos el PDF.">
          {nuevo}
        </EstadoVacio>
      ) : proveedores.length > 0 && (
        <>
          <section aria-labelledby="prv-resumen" className="prv-alerta">
            <h2 id="prv-resumen" className="ui-sr">Resumen de proveedores</h2>
            <div className="ui-indicadores">
              <Indicador icono="users" titulo="Proveedores" valor={proveedores.length} />
              <Indicador icono="mail" titulo="Reciben el PDF" valor={recibenPdf} tono={recibenPdf === proveedores.length ? "ok" : undefined}
                pie={recibenPdf === proveedores.length ? "A todos les llega el PDF de cada compra"
                  : [sinCorreo > 0 && `${plural(sinCorreo, "proveedor", "proveedores")} sin correo`,
                    apagados > 0 && `${apagados} con el envío del PDF apagado`].filter(Boolean).join(" · ")} />
            </div>
          </section>

          <div className="prv-barra">
            <div className="ui-buscador">
              <Icon name="search" size={18} />
              <label htmlFor="prv-busqueda" className="ui-sr">Buscar por nombre, NIT, correo o teléfono</label>
              <input id="prv-busqueda" className="ui-field__input" type="search" placeholder="Nombre, NIT, correo o teléfono"
                value={busqueda} onChange={(e) => { setBusqueda(e.target.value); }} />
            </div>
          </div>

          {visibles.length === 0 ? (
            <EstadoVacio icono="search" titulo="Ningún proveedor coincide" texto={`No encontramos “${busqueda.trim()}”.`}>
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setBusqueda("")}>Limpiar búsqueda</button>
            </EstadoVacio>
          ) : (
            <>
              <div className="ui-card ui-table-card">
                <div className="ui-table-wrap">
                  <table className="ui-table ui-table--tarjetas">
                    <caption className="ui-sr">Proveedores, en orden alfabético</caption>
                    <thead>
                      <tr>
                        <th scope="col">Proveedor</th>
                        <th scope="col">NIT</th>
                        <th scope="col">Teléfono</th>
                        <th scope="col">Correo</th>
                        <th scope="col"><span className="ui-sr">Acciones</span></th>
                      </tr>
                    </thead>
                    <tbody>
                      {enPagina.map((p) => (
                        <tr key={p.id}>
                          <td data-label="Proveedor" className="ui-table__titulo">
                            <span className="prv-nombre">
                              <LogoNegocio nombre={p.nombre} />
                              <button type="button" className="prv-nombre__enlace" onClick={() => onDetalle(p.id)}>{p.nombre}</button>
                            </span>
                          </td>
                          <td data-label="NIT">{p.nit || <span className="prv-tenue">Sin NIT</span>}</td>
                          <td data-label="Teléfono">
                            {p.telefono ? <a className="prv-enlace" href={`tel:${p.telefono}`}>{p.telefono}</a> : <span className="prv-tenue">Sin teléfono</span>}
                          </td>
                          <td data-label="Correo">
                            {p.correo ? <a className="prv-enlace" href={`mailto:${p.correo}`}>{p.correo}</a> : <span className="prv-tenue">Sin correo</span>}
                            {p.correo && !recibePdf(p) && <> <ChipEstado tono="info">Sin PDF</ChipEstado></>}
                          </td>
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onDetalle(p.id)}
                              aria-label={`Ver el detalle de ${p.nombre}`}>
                              Ver detalle
                            </button>
                            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => onEditar(p.id)}
                              aria-label={`Editar a ${p.nombre}`}>
                              <Icon name="pencil" size={14} /> Editar
                            </button>
                            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico prv-eliminar" onClick={() => setEliminando(p)}
                              aria-label={`Eliminar a ${p.nombre}`}>
                              <Icon name="trash" size={14} /> Eliminar
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
              <Paginacion pagina={pag.pagina} total={visibles.length} onCambiar={pag.cambiar} etiqueta="Páginas de proveedores" />
            </>
          )}

          {compras.length > 0 && (
            <div className="prv-actividad">
              <section className="ui-card" aria-labelledby="prv-top-titulo">
                <div className="ui-card__header">
                  <h2 id="prv-top-titulo" className="ui-card__titulo">Compras por proveedor</h2>
                  <span className="ui-card__sub">Por monto, sin las canceladas</span>
                </div>
                <ol className="prv-actividad__lista">
                  {top.map((t) => (
                    <li key={t.proveedorId || t.nombre} className="prv-actividad__fila">
                      <span className="prv-actividad__texto">
                        {nombres[t.proveedorId]
                          ? <button type="button" className="prv-nombre__enlace" onClick={() => onDetalle(t.proveedorId)}>{t.nombre}</button>
                          : <strong>{t.nombre}</strong>}
                        <span>{plural(t.compras, "compra")}{t.ultima ? ` · la última el ${formatoFecha(t.ultima)}` : ""}</span>
                      </span>
                      <strong className="ui-precio">{formatPrecio(t.total)}</strong>
                    </li>
                  ))}
                </ol>
              </section>
              <section className="ui-card" aria-labelledby="prv-recientes-titulo">
                <div className="ui-card__header">
                  <h2 id="prv-recientes-titulo" className="ui-card__titulo">Actividad reciente</h2>
                  <span className="ui-card__sub">Tus últimas compras</span>
                </div>
                <ul className="prv-actividad__lista">
                  {recientes.map((c) => (
                    <li key={c.id} className="prv-actividad__fila">
                      <span className="prv-actividad__texto">
                        <strong>{nombres[c.proveedorId] || "Proveedor eliminado"}</strong>
                        <span>{refCompra(c.id)} · {formatoFecha(c.fecha)} · {formatPrecio(c.total)}</span>
                      </span>
                      <ChipEstado estado={estadoCompra(c)} catalogo={ESTADOS_COMPRA} />
                    </li>
                  ))}
                </ul>
              </section>
            </div>
          )}
        </>
      )}

      {eliminando && (
        <ConfirmarAccion titulo={`¿Eliminar a ${eliminando.nombre}?`} peligro textoConfirmar="Eliminar"
          mensaje="Sale de tu directorio y no podrás elegirlo en compras nuevas. Las compras que ya le hiciste quedan registradas, pero sin sus datos. No se puede deshacer."
          onConfirmar={confirmarEliminar} onCancelar={() => setEliminando(null)} />
      )}
    </div>
  );
}
