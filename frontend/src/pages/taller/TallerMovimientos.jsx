import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../../services/authService";
import {
  exportarMovimientosTaller, miInventario, movimientosTaller, registrarMovimientoTaller,
} from "../../services/inventarioTallerService";
import { cargarTodosLosProductos } from "../../services/catalogoService";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Paginacion from "../../components/ui/Paginacion";
import { errorRango, propsPaginacion, textoOrden, useDiferido } from "../../components/ui/reglasTabla";
import EncabezadoOrden from "../../components/ui/EncabezadoOrden";
import FiltroFechas from "../../components/ui/FiltroFechas";
import SelectorOrden from "../../components/ui/SelectorOrden";
import Indicador from "../../components/ui/Indicador";
import SeccionEditable from "../../components/ui/SeccionEditable";
import Campo from "../../components/ui/Campo";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Esqueleto from "../../components/ui/Esqueleto";
import PieFormulario from "../../components/ui/PieFormulario";
import ImagenProducto from "../../components/marketplace/ImagenProducto";
import BuscadorProducto from "../../components/ui/BuscadorProducto";
import Icon from "../../components/public/Icon";
import { avisar } from "../../components/ui/avisar";
import { formatPrecio } from "../../components/servicio/estadosServicio";
import {
  CLASES, CLASES_MANUALES, FILTROS_TIPO, cantidadConSigno, erroresMovimientoTaller, stockResultante,
} from "./movimientosTaller";
import { plural } from "../../utils/formato";
import { destinoReferencia } from "../../utils/navegacion";
import "../../css/movimientos.css";
import { textoError } from "../../utils/errores";
import ConfirmarAccion from "../../components/marketplace/ConfirmarAccion";

const VACIO = { clase: "AJUSTE", piezaId: "", productoId: "", cantidad: "", costo: "", motivo: "" };
const fecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");
const hora = (f) => (f ? new Date(f).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" }) : "");
const refPieza = (p) => (p.referencia ? `Ref. ${p.referencia}` : p.marca || "Sin referencia");

/**
 * Movimiento manual del taller. Conteo físico, pérdidas y devoluciones eligen una pieza del inventario; el stock inicial elige
 * una del catálogo (así funcionan el precio público y el tope) y pide el costo. El stock que resulta se ve antes de
 * guardar. Va en la página, como en la tienda: es corto y conviene ver la lista al terminar.
 */
function FormMovimiento({ piezas, responsableIva, piezaInicial, token, onGuardado, onCancelar }) {
  const [form, setForm] = useState({ ...VACIO, piezaId: piezaInicial || "" });
  const [tocado, setTocado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const [catalogo, setCatalogo] = useState(null); // productos del catálogo para el stock inicial
  const [confirmando, setConfirmando] = useState(false); // el resumen del movimiento antes de guardarlo
  const carga = form.clase === "CARGA_INICIAL";

  useEffect(() => {
    if (!carga || catalogo) return;
    let vivo = true;
    cargarTodosLosProductos()
      .then((r) => { if (vivo) setCatalogo(r.items || []); })
      .catch(() => { if (vivo) setCatalogo([]); });
    return () => { vivo = false; };
  }, [carga, catalogo]);

  const pieza = carga ? (catalogo || []).find((p) => p.id === form.productoId) : piezas.find((p) => p.id === form.piezaId);
  const errores = tocado ? erroresMovimientoTaller(form, pieza) : {};
  const tipo = CLASES_MANUALES[form.clase];
  const resultado = pieza && !carga ? stockResultante(form.clase, pieza.stock, form.cantidad) : null;
  const cambiar = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const enviar = async (e) => {
    e.preventDefault();
    if (guardando) return;
    setTocado(true);
    if (Object.keys(erroresMovimientoTaller(form, pieza)).length) return;
    setConfirmando(true);
  };

  const guardar = async () => {
    setConfirmando(false);
    setGuardando(true);
    setError(null);
    try {
      await registrarMovimientoTaller(token, {
        clase: form.clase, piezaId: carga ? null : form.piezaId, productoId: carga ? form.productoId : null,
        cantidad: Number(form.cantidad), costo: carga ? Number(String(form.costo).replace(/\./g, "")) : null, motivo: form.motivo.trim(),
      });
      onGuardado(carga
        ? `Stock inicial registrado: ${plural(Number(form.cantidad), "unidad", "unidades")} de ${pieza.nombre}.`
        : `${tipo.label} registrado: ${pieza.nombre} quedó con ${plural(resultado, "unidad", "unidades")}.`);
    } catch (err) {
      setError(textoError(err));
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      {confirmando && pieza && (
        <ConfirmarAccion titulo={`¿Registrar ${tipo.label.toLowerCase()} de ${pieza.nombre}?`} textoConfirmar="Sí, registrar"
          peligro={tipo.sentido === "sale"}
          mensaje={carga
            ? `Entran ${plural(Number(form.cantidad), "unidad", "unidades")} como un lote nuevo, a ${formatPrecio(Number(String(form.costo).replace(/\./g, "")))} cada una. No mueve dinero.`
            : `El stock pasa de ${pieza.stock} a ${resultado}. No mueve dinero y queda en el historial con tu nombre.`}
          onCancelar={() => setConfirmando(false)} onConfirmar={guardar}>
          <figure className="ui-motivo-cita">
            <figcaption>Motivo</figcaption>
            <blockquote>{form.motivo.trim()}</blockquote>
          </figure>
        </ConfirmarAccion>
      )}
      <fieldset className="mov-tipos">
        <legend className="ui-field__label">Tipo de movimiento</legend>
        {Object.entries(CLASES_MANUALES).map(([valor, t]) => (
          <label key={valor} className={`ui-opcion mov-tipo${form.clase === valor ? " ui-opcion--elegida" : ""}`}>
            <input type="radio" className="ui-sr" name="clase" value={valor} checked={form.clase === valor} onChange={cambiar} />
            <span><strong>{t.label}</strong><span>{t.ayuda}</span></span>
          </label>
        ))}
      </fieldset>
      <div className="ui-form__campos">
        {carga ? (
          catalogo === null ? (
            <Esqueleto variante="lista" cantidad={1} texto="Cargando el catálogo…" />
          ) : (
            <BuscadorProducto id="mov-producto" label="Pieza del catálogo" productos={catalogo} elegido={pieza}
              error={errores.pieza} stockDe={null} detalle={(p) => [refPieza(p), p.tienda?.nombre].filter(Boolean).join(" · ")}
              ayuda="Busca la misma pieza en el catálogo: de ahí salen su nombre, su compatibilidad y el precio público."
              textoVacio="El catálogo no tiene productos por ahora."
              onElegir={(p) => setForm((f) => ({ ...f, productoId: p ? p.id : "" }))} />
          )
        ) : (
          <BuscadorProducto id="mov-pieza" label="Pieza" productos={piezas} elegido={pieza} error={errores.pieza}
            autoFocus={!piezaInicial} etiquetaStock="En tu taller" stockDe={(p) => p.stock} detalle={refPieza}
            ayuda="Escribe el nombre o la referencia." textoVacio="Aún no tienes piezas: usa el stock inicial o compra en el catálogo."
            onElegir={(p) => setForm((f) => ({ ...f, piezaId: p ? p.id : "" }))} />
        )}
        <Campo id="mov-cantidad" label={tipo.campo} error={errores.cantidad}
          ayuda={tipo.sentido === "sale" && pieza ? `Puedes sacar hasta ${plural(Number(pieza.stock) || 0, "unidad", "unidades")}.` : undefined}>
          <input className="ui-field__input" name="cantidad" type="text" inputMode="numeric" placeholder="0"
            value={form.cantidad} onChange={cambiar} />
        </Campo>
        {carga && (
          <Campo id="mov-costo" label={responsableIva ? "Costo por unidad, sin IVA" : "Costo por unidad, con el IVA que pagaste"}
            error={errores.costo} ayuda="Con este costo se calculan tu margen y tu ganancia.">
            <input className="ui-field__input" name="costo" type="text" inputMode="numeric" placeholder="Ej. 21000"
              value={form.costo} onChange={cambiar} />
          </Campo>
        )}
        <Campo id="mov-motivo" label="Motivo" error={errores.motivo}>
          <input className="ui-field__input" name="motivo" value={form.motivo} onChange={cambiar} maxLength={200}
            placeholder={form.clase === "AJUSTE" ? "Ej. Conteo de fin de mes" : carga ? "Ej. Lo tenía antes de DriveMaster"
              : form.clase === "MERMA" ? "Ej. Se rompió al instalar" : "Ej. Vino con un defecto"} />
        </Campo>
      </div>
      {pieza && !carga && resultado != null && !errores.cantidad && (
        <p className="mov-resultado" role="status">
          El stock de <strong>{pieza.nombre}</strong> pasa de <strong>{pieza.stock}</strong> a <strong>{resultado}</strong>.
          {tipo.sentido === "sale" || (form.clase === "AJUSTE" && resultado < pieza.stock) ? " Sale del lote más antiguo." : ""}
        </p>
      )}
      {pieza && carga && pieza.precio > 0 && (
        <p className="mov-resultado" role="status">
          Precio público de referencia: <strong>{formatPrecio(pieza.precio)}</strong> antes de IVA. Si es una pieza nueva en tu inventario, empieza con ese tope como precio.
        </p>
      )}
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Registrar movimiento" />
    </form>
  );
}

const ORDENES = [
  { campo: "fecha", label: "Fecha", asc: "(más antiguos primero)", desc: "(más recientes primero)" },
  { campo: "tipo", label: "Tipo" },
  { campo: "cantidad", label: "Cantidad" },
];

/**
 * Movimientos del inventario del taller: la misma pantalla que "Inventario" de la tienda. Las compras, ventas y
 * cancelaciones se anotan solas; aquí también quedan los ajustes, el stock inicial, las pérdidas o daños y las devoluciones.
 * piezaInicial: abre el formulario con esa pieza (desde "Mi inventario").
 */
export default function TallerMovimientos({ irA, piezaInicial = null }) {
  const token = getToken();
  const [datos, setDatos] = useState(null);
  const [inventario, setInventario] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [registrando, setRegistrando] = useState(Boolean(piezaInicial));
  const [filtros, setFiltros] = useState({ busqueda: "", tipo: "", desde: "", hasta: "" });
  const q = useDiferido(filtros.busqueda.trim());
  const [orden, setOrden] = useState(null);
  const [pagina, setPagina] = useState(0);
  const [exportando, setExportando] = useState(null);
  const errorFechas = errorRango(filtros);

  const cargar = useCallback(async (vivo = () => true) => {
    if (errorFechas) return;
    setCargando(true);
    try {
      const r = await movimientosTaller(token, { tipo: filtros.tipo, q, desde: filtros.desde, hasta: filtros.hasta,
        page: pagina, size: 20, sort: textoOrden(orden) });
      if (!vivo()) return;
      setDatos(r);
      setError(null);
    } catch (e) {
      if (vivo()) setError(textoError(e));
    } finally {
      if (vivo()) setCargando(false);
    }
  }, [token, filtros.tipo, filtros.desde, filtros.hasta, q, pagina, orden, errorFechas]);

  useEffect(() => {
    let activo = true;
    const id = setTimeout(() => cargar(() => activo), 0);
    return () => { activo = false; clearTimeout(id); };
  }, [cargar]);

  const cargarInventario = useCallback(() => miInventario(token).then(setInventario).catch(() => {}), [token]);
  useEffect(() => { cargarInventario(); }, [cargarInventario]);

  const piezas = useMemo(() => inventario?.piezas || [], [inventario]);
  const responsableIva = inventario?.responsableIva !== false;
  const lista = datos?.movimientos;
  const enPagina = lista?.items || [];
  const totalFiltrado = lista?.total || 0;
  const resumen = datos?.resumen || { total: 0, entradas: 0, salidas: 0, ajustes: 0 };
  const hayFiltros = filtros.busqueda || filtros.tipo || filtros.desde || filtros.hasta;

  const filtrar = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(0); };
  const ordenar = (o) => { setOrden(o); setPagina(0); };
  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
  const guardado = async (texto) => {
    setRegistrando(false);
    avisar(texto);
    await Promise.all([cargar(), cargarInventario()]);
  };
  const exportar = async (formato) => {
    setExportando(formato);
    try {
      await exportarMovimientosTaller(token, filtros, formato);
      avisar(`Descargamos ${plural(totalFiltrado, "movimiento")} en ${formato === "pdf" ? "PDF" : "Excel"}.`);
    } catch (e) {
      avisar(`No pudimos generar el archivo. ${textoError(e, "Inténtalo de nuevo.")}`, "error");
    } finally {
      setExportando(null);
    }
  };

  const botonRegistrar = !registrando && (
    <button type="button" className="ui-btn ui-btn--primario" onClick={() => setRegistrando(true)}>
      <Icon name="plus" size={16} /> Registrar movimiento
    </button>
  );

  return (
    <div className="mov-root">
      <EncabezadoPagina titulo="Movimientos de mi inventario" onVolver={() => irA("taller-inventario")} textoVolver="Mi inventario"
        descripcion="Lo que entra y sale de tu inventario, con su motivo. Compras, ventas y cancelaciones se anotan solas."
        acciones={botonRegistrar} />

      {error && (
        <Alerta className="mov-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar los movimientos. {error}
        </Alerta>
      )}

      {registrando && (
        <div className="mov-alerta">
          <SeccionEditable id="mov-registrar" icono="box" titulo="Registrar movimiento" editando
            descripcion="Para lo que no pasa por una compra o una cotización. No mueve dinero: solo tu stock.">
            <FormMovimiento piezas={piezas} responsableIva={responsableIva} piezaInicial={piezaInicial} token={token}
              onGuardado={guardado} onCancelar={() => setRegistrando(false)} />
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
                <label htmlFor="mov-busqueda" className="ui-sr">Buscar por pieza o referencia</label>
                <input id="mov-busqueda" className="ui-field__input" type="search" placeholder="Pieza o referencia"
                  value={filtros.busqueda} onChange={(e) => filtrar({ busqueda: e.target.value })} />
              </div>
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
            <SelectorOrden id="mov-orden" opciones={ORDENES} orden={orden} onOrdenar={ordenar} porDefecto="Más recientes primero" />
          </div>

          {enPagina.length === 0 ? (
            !error && (hayFiltros ? (
              <EstadoVacio icono="search" titulo="Ningún movimiento con estos filtros" texto="Prueba con otro tipo, otras fechas o el nombre de la pieza.">
                <button type="button" className="ui-btn ui-btn--secundario" onClick={() => filtrar({ busqueda: "", tipo: "", desde: "", hasta: "" })}>
                  Quitar filtros
                </button>
              </EstadoVacio>
            ) : (
              <EstadoVacio icono="box" titulo="Aún no hay movimientos"
                texto="Cuando confirmes la llegada de una compra o vendas una pieza en una cotización, queda aquí. Si ya tenías piezas, regístralas con el stock inicial.">
                {botonRegistrar}
              </EstadoVacio>
            ))
          ) : (
            <>
              <div className={`ui-card ui-table-card${cargando ? " ui-actualizando" : ""}`} aria-busy={cargando} id="mov-tabla">
                <div className="ui-table-wrap">
                  <table className="ui-table ui-table--tarjetas mov-tabla">
                    <caption className="ui-sr">Movimientos de tu inventario ({totalFiltrado.toLocaleString("es-CO")})</caption>
                    <thead>
                      <tr>
                        <th scope="col">Pieza</th>
                        <EncabezadoOrden campo="fecha" orden={orden} onOrdenar={ordenar}>Fecha</EncabezadoOrden>
                        <EncabezadoOrden campo="tipo" orden={orden} onOrdenar={ordenar}>Tipo</EncabezadoOrden>
                        <EncabezadoOrden campo="cantidad" orden={orden} onOrdenar={ordenar} className="ui-table__num">Cantidad</EncabezadoOrden>
                        <th scope="col" className="ui-table__num">Stock</th>
                        <th scope="col">Motivo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {enPagina.map((m) => {
                        const clase = CLASES[m.clase] || { label: m.clase, tono: "info" };
                        const destino = destinoReferencia(m.referencia, "taller-movimientos");
                        return (
                          <tr key={m.id}>
                            <td data-label="Pieza" className="ui-table__titulo">
                              <span className="mov-producto">
                                <ImagenProducto src={m.imagenUrl} className="mov-producto__imagen" tamanioIcono={18} />
                                <span className="mov-producto__texto">
                                  <strong>{m.piezaNombre || "Pieza"}</strong>
                                  <span>{m.piezaReferencia ? `Ref. ${m.piezaReferencia}` : "Sin referencia"}</span>
                                </span>
                              </span>
                            </td>
                            <td data-label="Fecha" className="mov-fecha-celda">
                              <span>{fecha(m.fecha)}</span> <span className="mov-tenue">{hora(m.fecha)}</span>
                            </td>
                            <td data-label="Tipo"><ChipEstado tono={clase.tono}>{clase.label}</ChipEstado></td>
                            <td data-label="Cantidad" className="ui-table__num"><strong>{cantidadConSigno(m)}</strong></td>
                            <td data-label="Stock" className="ui-table__num">
                              <span className="mov-tenue">{m.stockAnterior}</span> <Icon name="arrow" size={12} /> <strong>{m.stockNuevo}</strong>
                              <span className="ui-sr"> (antes {m.stockAnterior}, después {m.stockNuevo})</span>
                            </td>
                            <td data-label="Motivo" className="mov-motivo">
                              <span className="mov-motivo__cuerpo">
                              <span>{m.motivo || "—"}</span>
                              {m.referencia && (destino ? (
                                <> · <button type="button" className="mov-ref" onClick={() => irA(destino.pagina, destino.param)}>{m.referencia.texto}</button></>
                              ) : <span className="mov-tenue"> · {m.referencia.texto}</span>)}
                              {m.lotes?.length > 0 && <span className="mov-lotes">{m.lotes.join(" · ")}</span>}
                              <span className="mov-lotes">{m.usuario}</span>
                              </span>
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
