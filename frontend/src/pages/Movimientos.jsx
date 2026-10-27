import { useCallback, useEffect, useMemo, useState } from "react";
import { exportarMovimientos, movimientosTabla, registrarMovimiento } from "../services/movimientoService";
import { listarProductos, listarProductosMiTienda } from "../services/productoService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { errorRango, propsPaginacion, textoOrden, useDiferido } from "../components/ui/reglasTabla";
import EncabezadoOrden from "../components/ui/EncabezadoOrden";
import FiltroFechas from "../components/ui/FiltroFechas";
import SelectorOrden from "../components/ui/SelectorOrden";
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
import BuscadorProducto from "../components/ui/BuscadorProducto";
import { avisar } from "../components/ui/avisar";
import {
  FILTROS_TIPO, TIPOS, erroresMovimiento, stockResultante, textoDiferencia,
} from "./tienda/inventarioTienda";
import { plural } from "../utils/formato";
import "../css/movimientos.css";

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
  const disponible = Math.max(0, Number(producto?.stockActual) || 0);

  const cambiar = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const enviar = async (e) => {
    e.preventDefault();
    if (guardando) return;
    setTocado(true);
    if (Object.keys(erroresMovimiento(form, producto?.stockActual)).length) return;
    setGuardando(true);
    setError(null);
    try {
      await registrarMovimiento({ ...form, cantidad: Number(form.cantidad), motivo: form.motivo.trim() }, token);
      onGuardado(`${tipo.label === "Ajuste" ? "Ajuste registrado" : `${tipo.label} registrada`}: ${producto.nombre} quedó con ${plural(resultado, "unidad", "unidades")}.`);
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
        <BuscadorProducto id="mov-producto" productos={productos} elegido={producto} error={errores.productoId} autoFocus
          onElegir={(p) => setForm((f) => ({ ...f, productoId: p ? p.id : "" }))} />
        <Campo id="mov-cantidad" label={tipo.campo} error={errores.cantidad}
          ayuda={form.tipo === "SALIDA" && producto ? `Puedes sacar hasta ${plural(disponible, "unidad", "unidades")}.` : undefined}>
          <input className="ui-field__input" name="cantidad" type="text" inputMode="numeric" placeholder="0"
            value={form.cantidad} onChange={cambiar} />
        </Campo>
        <Campo id="mov-motivo" label="Motivo" error={errores.motivo}>
          <input className="ui-field__input" name="motivo" value={form.motivo} onChange={cambiar} maxLength={200}
            placeholder={form.tipo === "AJUSTE" ? "Ej. Conteo de fin de mes" : form.tipo === "ENTRADA" ? "Ej. Devolución de un cliente" : "Ej. Unidad dañada"} />
        </Campo>
      </div>
      {producto && resultado != null && !errores.cantidad && (
        <p className="mov-resultado" role="status">
          {form.tipo === "AJUSTE" ? (
            <>Stock de <strong>{producto.nombre}</strong>: <strong>{textoDiferencia(producto.stockActual, form.cantidad)}</strong>.</>
          ) : (
            <>El stock de <strong>{producto.nombre}</strong> pasa de <strong>{producto.stockActual ?? 0}</strong> a <strong>{resultado}</strong>.</>
          )}
        </p>
      )}
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar={`Registrar ${tipo.label.toLowerCase()}`} />
    </form>
  );
}

const ORDENES_MOVIMIENTOS = [
  { campo: "fecha", label: "Fecha", asc: "(más antiguos primero)", desc: "(más recientes primero)" },
  { campo: "tipo", label: "Tipo" },
  { campo: "cantidad", label: "Cantidad" },
];

/**
 * Movimientos de la tienda, paginados en el servidor (/api/movimientos/tabla): tipo, producto, fechas (en hora de
 * Colombia), orden y página se resuelven allá; el resumen de arriba es de todo el historial. El backend ya filtra
 * por la tienda de la sesión. esTienda elige la lista de productos (nombres y el formulario).
 */
function Movimientos({ token, esTienda = false }) {
  const [datos, setDatos] = useState(null); // { movimientos: Pagina, resumen }
  const [productos, setProductos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [registrando, setRegistrando] = useState(false);
  const [filtros, setFiltros] = useState({ busqueda: "", tipo: "", desde: "", hasta: "" });
  const q = useDiferido(filtros.busqueda.trim());
  const [orden, setOrden] = useState(null); // null: del más reciente al más antiguo
  const [pagina, setPagina] = useState(0);
  const errorFechas = errorRango(filtros);

  const cargar = useCallback(async (vivo = () => true) => {
    if (errorFechas) return; // el selector ya lo avisa; no se pide un rango invertido
    setCargando(true);
    try {
      const r = await movimientosTabla(token, { tipo: filtros.tipo, q, desde: filtros.desde, hasta: filtros.hasta,
        page: pagina, size: 20, sort: textoOrden(orden) });
      if (!vivo()) return;
      setDatos(r);
      setError(null);
    } catch (e) {
      if (vivo()) setError(e.message);
    } finally {
      if (vivo()) setCargando(false);
    }
  }, [token, filtros.tipo, filtros.desde, filtros.hasta, q, pagina, orden, errorFechas]);

  useEffect(() => {
    let activo = true;
    const id = setTimeout(() => cargar(() => activo), 0);
    return () => { activo = false; clearTimeout(id); };
  }, [cargar]);

  useEffect(() => {
    let vivo = true;
    (esTienda ? listarProductosMiTienda(token) : listarProductos(token))
      .then((l) => { if (vivo) setProductos(l || []); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [token, esTienda]);

  const porId = useMemo(() => Object.fromEntries(productos.map((p) => [p.id, p])), [productos]);
  const lista = datos?.movimientos;
  const enPagina = lista?.items || [];
  const totalFiltrado = lista?.total || 0;
  const resumen = datos?.resumen || { total: 0, entradas: 0, salidas: 0, ajustes: 0 };
  const hayFiltros = filtros.busqueda || filtros.tipo || filtros.desde || filtros.hasta;
  const [exportando, setExportando] = useState(null); // "xlsx" | "pdf" mientras se genera
  const exportar = async (formato) => {
    setExportando(formato);
    try {
      await exportarMovimientos(filtros, formato, token);
      avisar(`Descargamos ${plural(totalFiltrado, "movimiento")} en ${formato === "pdf" ? "PDF" : "Excel"}.`);
    } catch (e) {
      avisar(`No pudimos generar el archivo. ${e.message}`, "error");
    } finally {
      setExportando(null);
    }
  };

  // Cambiar filtro, búsqueda, fechas u orden vuelve a la primera página
  const filtrar = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(0); };
  const ordenar = (o) => { setOrden(o); setPagina(0); };
  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
  const guardado = async (texto) => {
    setRegistrando(false);
    avisar(texto);
    await cargar();
    (esTienda ? listarProductosMiTienda(token) : listarProductos(token)).then((l) => setProductos(l || [])).catch(() => {});
  };

  const botonRegistrar = !registrando && (
    <button type="button" className="ui-btn ui-btn--primario" onClick={() => setRegistrando(true)}>
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

      {registrando && (
        <div className="mov-alerta">
          <SeccionEditable id="mov-registrar" icono="box" titulo="Registrar movimiento" editando
            descripcion="Para lo que no pasa por un pedido: mercancía que llega, unidades dañadas o un conteo físico.">
            <FormMovimiento productos={productos} token={token} onGuardado={guardado} onCancelar={() => setRegistrando(false)} />
          </SeccionEditable>
        </div>
      )}

      {!datos && !error ? (
        <Esqueleto variante="lista" cantidad={4} texto="Cargando movimientos…" />
      ) : (
        <>
          <section aria-labelledby="mov-resumen" className="mov-alerta">
            <h2 id="mov-resumen" className="ui-sr">Resumen de movimientos</h2>
            <div className="ui-indicadores">
              <Indicador titulo="Movimientos" valor={resumen.total.toLocaleString("es-CO")} pie="En todo el historial" />
              <Indicador titulo="Unidades que entraron" valor={resumen.entradas.toLocaleString("es-CO")} tono="ok" />
              <Indicador titulo="Unidades que salieron" valor={resumen.salidas.toLocaleString("es-CO")} />
              <Indicador titulo="Ajustes por conteo" valor={resumen.ajustes.toLocaleString("es-CO")} />
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

              {/* Descarga exactamente lo que muestra la lista con estos filtros (lo arma el backend) */}
              <div className="mov-exportar" role="group" aria-label="Descargar los movimientos filtrados">
                {[["xlsx", "Excel"], ["pdf", "PDF"]].map(([formato, texto]) => (
                  <button key={formato} type="button" className="ui-btn ui-btn--secundario" onClick={() => exportar(formato)}
                    disabled={exportando !== null || totalFiltrado === 0} aria-busy={exportando === formato}>
                    {exportando !== formato && <Icon name="file" size={16} />}
                    {exportando === formato ? "Generando…" : texto}
                  </button>
                ))}
              </div>
            </div>
            <FiltroFechas id="mov-fechas" etiqueta="Fecha del movimiento" valor={{ desde: filtros.desde, hasta: filtros.hasta }}
              onCambiar={(r) => filtrar(r)} />
            <SelectorOrden id="mov-orden" opciones={ORDENES_MOVIMIENTOS} orden={orden} onOrdenar={ordenar}
              porDefecto="Más recientes primero" />
          </div>

          {enPagina.length === 0 ? (
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
              <div className={`ui-card ui-table-card${cargando ? " ui-actualizando" : ""}`} aria-busy={cargando} id="mov-tabla">
                <div className="ui-table-wrap">
                  <table className="ui-table ui-table--tarjetas mov-tabla">
                    <caption className="ui-sr">Movimientos de inventario ({totalFiltrado.toLocaleString("es-CO")})</caption>
                    <thead>
                      <tr>
                        <th scope="col">Producto</th>
                        <EncabezadoOrden campo="fecha" orden={orden} onOrdenar={ordenar}>Fecha</EncabezadoOrden>
                        <EncabezadoOrden campo="tipo" orden={orden} onOrdenar={ordenar}>Tipo</EncabezadoOrden>
                        <EncabezadoOrden campo="cantidad" orden={orden} onOrdenar={ordenar} className="ui-table__num">Cantidad</EncabezadoOrden>
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
              <Paginacion {...propsPaginacion(lista)} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de movimientos" ancla="mov-tabla" />
            </>
          )}
        </>
      )}
    </div>
  );
}

export default Movimientos;
