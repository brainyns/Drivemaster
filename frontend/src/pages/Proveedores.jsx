import { useCallback, useEffect, useMemo, useState } from "react";
import { listarProveedores, eliminarProveedor, exportarProveedores } from "../services/proveedorService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import LogoNegocio from "../components/ui/LogoNegocio";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import Icon from "../components/public/Icon";
import { coincideProveedor } from "./tienda/proveedoresTienda";
import "../css/proveedores.css";

const POR_PAGINA = 15;

/** Directorio de los proveedores de la tienda: a quién le compra y a dónde llega el PDF de cada compra. */
export default function Proveedores({ onNuevo, onEditar, token }) {
  const [proveedores, setProveedores] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina] = useState(1);
  const [eliminando, setEliminando] = useState(null);
  const [exportando, setExportando] = useState(false);
  const [aviso, setAviso] = useState(null); // { tono, texto }

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
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const visibles = useMemo(() => proveedores.filter((p) => coincideProveedor(p, busqueda))
    .sort((a, b) => String(a.nombre || "").localeCompare(String(b.nombre || ""), "es")), [proveedores, busqueda]);
  const totalPaginas = Math.max(1, Math.ceil(visibles.length / POR_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas);
  const enPagina = visibles.slice((paginaActual - 1) * POR_PAGINA, paginaActual * POR_PAGINA);
  const conCorreo = proveedores.filter((p) => p.correo).length;
  const sinCorreo = proveedores.length - conCorreo;

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
        descripcion="A quién le compras para surtir tu inventario. Cada compra registrada le llega en PDF a su correo."
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
          texto="Agrega a quién le compras: lo eliges al registrar una compra y le enviamos el PDF a su correo.">
          {nuevo}
        </EstadoVacio>
      ) : proveedores.length > 0 && (
        <>
          <section aria-labelledby="prv-resumen" className="prv-alerta">
            <h2 id="prv-resumen" className="ui-sr">Resumen de proveedores</h2>
            <div className="ui-indicadores">
              <Indicador icono="users" titulo="Proveedores" valor={proveedores.length} />
              <Indicador icono="mail" titulo="Reciben el PDF" valor={conCorreo} tono={sinCorreo === 0 ? "ok" : undefined}
                pie={sinCorreo > 0 ? `${sinCorreo} sin correo registrado` : "Todos tienen correo"} />
            </div>
          </section>

          <div className="prv-barra">
            <div className="ui-buscador">
              <Icon name="search" size={18} />
              <label htmlFor="prv-busqueda" className="ui-sr">Buscar por nombre, NIT, correo o teléfono</label>
              <input id="prv-busqueda" className="ui-field__input" type="search" placeholder="Nombre, NIT, correo o teléfono"
                value={busqueda} onChange={(e) => { setBusqueda(e.target.value); setPagina(1); }} />
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
                            <span className="prv-nombre"><LogoNegocio nombre={p.nombre} /><strong>{p.nombre}</strong></span>
                          </td>
                          <td data-label="NIT">{p.nit || <span className="prv-tenue">Sin NIT</span>}</td>
                          <td data-label="Teléfono">
                            {p.telefono ? <a className="prv-enlace" href={`tel:${p.telefono}`}>{p.telefono}</a> : <span className="prv-tenue">Sin teléfono</span>}
                          </td>
                          <td data-label="Correo">
                            {p.correo ? <a className="prv-enlace" href={`mailto:${p.correo}`}>{p.correo}</a> : <ChipEstado tono="aviso">Sin correo</ChipEstado>}
                          </td>
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onEditar(p.id)}
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
              <nav className="prv-paginas" aria-label="Páginas de proveedores">
                <span>Mostrando {(paginaActual - 1) * POR_PAGINA + 1}–{Math.min(paginaActual * POR_PAGINA, visibles.length)} de {visibles.length}</span>
                {totalPaginas > 1 && (
                  <span className="prv-paginas__botones">
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

      {eliminando && (
        <ConfirmarAccion titulo={`¿Eliminar a ${eliminando.nombre}?`} peligro textoConfirmar="Eliminar proveedor"
          mensaje="Sale de tu directorio y no podrás elegirlo en compras nuevas. Las compras que ya le hiciste quedan registradas, pero sin sus datos. No se puede deshacer."
          onConfirmar={confirmarEliminar} onCancelar={() => setEliminando(null)} />
      )}
    </div>
  );
}
