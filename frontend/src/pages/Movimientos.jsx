import { useCallback, useEffect, useMemo, useState } from "react";
import { listarMovimientos, registrarMovimiento } from "../services/movimientoService";
import { listarProductos, listarProductosMiTienda } from "../services/productoService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import SeccionEditable from "../components/ui/SeccionEditable";
import Campo from "../components/ui/Campo";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import PieFormulario from "../components/ui/PieFormulario";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import Icon from "../components/public/Icon";
import { FILTROS_TIPO, TIPOS, erroresMovimiento, filtrarMovimientos, resumenMovimientos, stockResultante } from "./tienda/inventarioTienda";
import "../css/movimientos.css";

const POR_PAGINA = 15;
const VACIO = { productoId: "", tipo: "ENTRADA", cantidad: "", motivo: "" };

const fecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");
const hora = (f) => (f ? new Date(f).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" }) : "");

/**
 * Movimiento manual: entrada, salida o ajuste por conteo, con el stock que resulta a la vista antes de guardar.
 * Va en la página (no en un modal): es una tarea corta y conviene ver la lista al terminar.
 */
function FormMovimiento({ productos, token, onGuardado, onCancelar }) {
  const [form, setForm] = useState(VACIO);
  const [tocado, setTocado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const producto = productos.find((p) => p.id === form.productoId);
  const errores = tocado ? erroresMovimiento(form, producto?.stockActual) : {};
  const resultado = producto ? stockResultante(form.tipo, producto.stockActual, form.cantidad) : null;
  const tipo = TIPOS[form.tipo];

  const cambiar = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const enviar = async (e) => {
    e.preventDefault();
    setTocado(true);
    if (Object.keys(erroresMovimiento(form, producto?.stockActual)).length) return;
    setGuardando(true);
    setError(null);
    try {
      await registrarMovimiento({ ...form, cantidad: Number(form.cantidad), motivo: form.motivo.trim() }, token);
      onGuardado(`${tipo.label} registrada: ${producto.nombre} quedó con ${resultado} ${resultado === 1 ? "unidad" : "unidades"}.`);
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <fieldset className="mov-tipos">
        <legend className="ui-field__label">Tipo de movimiento</legend>
        {Object.entries(TIPOS).map(([valor, t]) => (
          <label key={valor} className={`ui-opcion mov-tipo${form.tipo === valor ? " ui-opcion--elegida" : ""}`}>
            <input type="radio" className="ui-sr" name="tipo" value={valor} checked={form.tipo === valor} onChange={cambiar} />
            <span><strong>{t.label}</strong><span>{t.ayuda}</span></span>
          </label>
        ))}
      </fieldset>
      <div className="ui-form__campos">
        <Campo id="mov-producto" label="Producto" error={errores.productoId}>
          <select className="ui-field__input" name="productoId" value={form.productoId} onChange={cambiar} autoFocus>
            <option value="">Elegir producto</option>
            {productos.map((p) => <option key={p.id} value={p.id}>{p.nombre} · stock {p.stockActual ?? 0}</option>)}
          </select>
        </Campo>
        <Campo id="mov-cantidad" label={tipo.campo} error={errores.cantidad}>
          <input className="ui-field__input" name="cantidad" type="text" inputMode="numeric" placeholder="0"
            value={form.cantidad} onChange={cambiar} />
        </Campo>
        <Campo id="mov-motivo" label="Motivo" error={errores.motivo}>
          <input className="ui-field__input" name="motivo" value={form.motivo} onChange={cambiar} maxLength={200}
            placeholder={form.tipo === "AJUSTE" ? "Ej. Conteo de fin de mes" : form.tipo === "ENTRADA" ? "Ej. Llegó pedido del proveedor" : "Ej. Unidad dañada"} />
        </Campo>
      </div>
      {producto && resultado != null && !errores.cantidad && (
        <p className="mov-resultado" role="status">
          El stock de <strong>{producto.nombre}</strong> pasa de <strong>{producto.stockActual ?? 0}</strong> a <strong>{resultado}</strong>.
        </p>
      )}
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar={`Registrar ${tipo.label.toLowerCase()}`} />
    </form>
  );
}

// esTienda: la tienda solo ve y ajusta su propio inventario (el backend ya filtra los movimientos).
function Movimientos({ token, esTienda = false }) {
  const [movimientos, setMovimientos] = useState([]);
  const [productos, setProductos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [registrando, setRegistrando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const [filtros, setFiltros] = useState({ busqueda: "", tipo: "", desde: "", hasta: "" });
  const [pagina, setPagina] = useState(1);

  const cargar = useCallback(async () => {
    try {
      setMovimientos((await listarMovimientos({}, token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    let vivo = true;
    (esTienda ? listarProductosMiTienda(token) : listarProductos(token))
      .then((l) => { if (vivo) setProductos(l || []); })
      .catch(() => {});
    const id = setTimeout(cargar, 0);
    return () => { vivo = false; clearTimeout(id); };
  }, [token, esTienda, cargar]);

  const porId = useMemo(() => Object.fromEntries(productos.map((p) => [p.id, p])), [productos]);
  const visibles = useMemo(() => filtrarMovimientos(movimientos, filtros, (id) => porId[id]), [movimientos, filtros, porId]);
  const datos = useMemo(() => resumenMovimientos(movimientos), [movimientos]);
  const totalPaginas = Math.max(1, Math.ceil(visibles.length / POR_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas);
  const enPagina = visibles.slice((paginaActual - 1) * POR_PAGINA, paginaActual * POR_PAGINA);
  const hayFiltros = filtros.busqueda || filtros.tipo || filtros.desde || filtros.hasta;

  const filtrar = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(1); };
  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
  const guardado = async (texto) => {
    setRegistrando(false);
    setAviso(texto);
    await cargar();
    (esTienda ? listarProductosMiTienda(token) : listarProductos(token)).then((l) => setProductos(l || [])).catch(() => {});
  };

  const botonRegistrar = !registrando && (
    <button type="button" className="ui-btn ui-btn--primario" onClick={() => { setAviso(null); setRegistrando(true); }}>
      <Icon name="plus" size={16} /> Registrar movimiento
    </button>
  );

  return (
    <div className="ss-panel--tienda mov-root">
      <EncabezadoPagina titulo="Inventario"
        descripcion="Entradas, salidas y ajustes de stock de tus productos, con su motivo. Las ventas descuentan solas."
        acciones={botonRegistrar} />

      {error && (
        <Alerta className="mov-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar los movimientos. {error}
        </Alerta>
      )}
      {aviso && <Alerta tono="ok" className="mov-alerta" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}

      {registrando && (
        <div className="mov-alerta">
          <SeccionEditable id="mov-registrar" icono="box" titulo="Registrar movimiento" editando
            descripcion="Para lo que no pasa por un pedido: mercancía que llega, unidades dañadas o un conteo físico.">
            <FormMovimiento productos={productos} token={token} onGuardado={guardado} onCancelar={() => setRegistrando(false)} />
          </SeccionEditable>
        </div>
      )}

      {cargando ? (
        <Esqueleto variante="lista" cantidad={4} texto="Cargando movimientos…" />
      ) : (
        <>
          <section aria-labelledby="mov-resumen" className="mov-alerta">
            <h2 id="mov-resumen" className="ui-sr">Resumen de movimientos</h2>
            <div className="ui-indicadores">
              <Indicador titulo="Movimientos" valor={datos.total.toLocaleString("es-CO")} pie="En todo el historial" />
              <Indicador titulo="Unidades que entraron" valor={datos.entradas.toLocaleString("es-CO")} tono="ok" />
              <Indicador titulo="Unidades que salieron" valor={datos.salidas.toLocaleString("es-CO")} />
              <Indicador titulo="Ajustes por conteo" valor={datos.ajustes.toLocaleString("es-CO")} />
            </div>
          </section>

          <div className="mov-filtros" role="search" aria-label="Filtrar movimientos">
            <div className="ui-filtros" role="group" aria-label="Tipo de movimiento">
              {FILTROS_TIPO.map((f) => (
                <button key={f.key || "todos"} type="button" className="ui-chip" aria-pressed={filtros.tipo === f.key}
                  onClick={() => filtrar({ tipo: f.key })}>
                  {f.label}
                </button>
              ))}
            </div>
            <div className="mov-filtros__campos">
              <div className="ui-buscador">
                <Icon name="search" size={18} />
                <label htmlFor="mov-busqueda" className="ui-sr">Buscar por producto o código</label>
                <input id="mov-busqueda" className="ui-field__input" type="search" placeholder="Producto o código"
                  value={filtros.busqueda} onChange={(e) => filtrar({ busqueda: e.target.value })} />
              </div>
              <Campo id="mov-desde" label="Desde" className="mov-fecha">
                <input className="ui-field__input" type="date" value={filtros.desde} max={filtros.hasta || undefined}
                  onChange={(e) => filtrar({ desde: e.target.value })} />
              </Campo>
              <Campo id="mov-hasta" label="Hasta" className="mov-fecha">
                <input className="ui-field__input" type="date" value={filtros.hasta} min={filtros.desde || undefined}
                  onChange={(e) => filtrar({ hasta: e.target.value })} />
              </Campo>
            </div>
          </div>

          {visibles.length === 0 ? (
            !error && (hayFiltros ? (
              <EstadoVacio icono="search" titulo="Ningún movimiento con estos filtros"
                texto="Prueba con otro tipo, otras fechas o el código del producto.">
                <button type="button" className="ui-btn ui-btn--secundario" onClick={() => filtrar({ busqueda: "", tipo: "", desde: "", hasta: "" })}>
                  Quitar filtros
                </button>
              </EstadoVacio>
            ) : (
              <EstadoVacio icono="box" titulo="Aún no hay movimientos"
                texto="Cada venta descuenta el stock sola; aquí también quedan las entradas y los ajustes que registres.">
                {botonRegistrar}
              </EstadoVacio>
            ))
          ) : (
            <>
              <div className="ui-card ui-table-card">
                <div className="ui-table-wrap">
                  <table className="ui-table ui-table--tarjetas mov-tabla">
                    <caption className="ui-sr">Movimientos de inventario, del más reciente al más antiguo</caption>
                    <thead>
                      <tr>
                        <th scope="col">Producto</th>
                        <th scope="col">Fecha</th>
                        <th scope="col">Tipo</th>
                        <th scope="col" className="ui-table__num">Cantidad</th>
                        <th scope="col" className="ui-table__num">Stock</th>
                        <th scope="col">Motivo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {enPagina.map((m) => {
                        const p = porId[m.productoId];
                        const tipo = TIPOS[m.tipo] || { label: m.tipo, tono: "info", signo: "" };
                        return (
                          <tr key={m.id}>
                            <td data-label="Producto" className="ui-table__titulo">
                              <span className="mov-producto">
                                <ImagenProducto src={p?.imagenUrl} className="mov-producto__imagen" tamanioIcono={18} />
                                <span className="mov-producto__texto">
                                  <strong>{p?.nombre || "Producto eliminado"}</strong>
                                  <span>{p?.codigo ? `Código ${p.codigo}` : "Sin código"}</span>
                                </span>
                              </span>
                            </td>
                            <td data-label="Fecha" className="mov-fecha-celda">
                              <span>{fecha(m.fecha)}</span> <span className="mov-tenue">{hora(m.fecha)}</span>
                            </td>
                            <td data-label="Tipo"><ChipEstado tono={tipo.tono}>{tipo.label}</ChipEstado></td>
                            <td data-label="Cantidad" className="ui-table__num"><strong>{tipo.signo}{m.cantidad}</strong></td>
                            <td data-label="Stock" className="ui-table__num">
                              <span className="mov-tenue">{m.stockAnterior}</span> <Icon name="arrow" size={12} /> <strong>{m.stockNuevo}</strong>
                              <span className="ui-sr"> (antes {m.stockAnterior}, después {m.stockNuevo})</span>
                            </td>
                            <td data-label="Motivo" className="mov-motivo">
                              {m.motivo || "—"}{m.referencia && <span className="mov-tenue"> · #{m.referencia}</span>}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
              <nav className="mov-paginas" aria-label="Páginas de movimientos">
                <span>
                  Mostrando {(paginaActual - 1) * POR_PAGINA + 1}–{Math.min(paginaActual * POR_PAGINA, visibles.length)} de {visibles.length}
                </span>
                {totalPaginas > 1 && (
                  <span className="mov-paginas__botones">
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

export default Movimientos;
