import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getToken } from "../services/authService";
import {
  misPedidosTienda, prepararPedido, despacharPedido, noPuedoDespachar, marcarListoParaRetirar, validarCodigoRetiro,
} from "../services/pedidoTiendaService";
import { listarProductosMiTienda } from "../services/productoService";
import { ESTADOS_PEDIDO, MOTIVOS_NO_DESPACHO, formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import Historial from "../components/ui/Historial";
import BarraAccionMovil from "../components/ui/BarraAccionMovil";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import Icon from "../components/public/Icon";
import {
  FILTROS, VACIOS, accionEntrega, accionFila, accionPrincipal, cantidadProductos, coincide, contar, destinatario, diasSinRetirar,
  esCatalogo, esEnvio, esRetiro, historialPedido, metodoEntrega, ordenarPorUrgencia, plazoVencido, porDespachar, sinRetirar,
  subtotal, textoPlazo,
} from "./tienda/pedidosTienda";
import { avisar } from "../components/ui/avisar";
import { destinoSeccion } from "../utils/navegacion";
import { plural } from "../utils/formato";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/pedidos-tienda.css";

/**
 * Chip del plazo de un pedido por despachar (vencido en rojo, si no en ámbar) o, en uno listo que el cliente no recoge,
 * "Sin retirar hace N días" (desde los 3 días; a los 15 DriveMaster lo revisa).
 */
function ChipPlazo({ pedido, ahora }) {
  if (sinRetirar(pedido, ahora)) {
    const dias = diasSinRetirar(pedido, ahora);
    return pedido.revisionRetiroDesde && !pedido.resolucionRetiro
      ? <ChipEstado tono="error">Sin retirar hace {dias} días · en revisión</ChipEstado>
      : <ChipEstado tono="aviso">Sin retirar hace {dias} días</ChipEstado>;
  }
  if (!porDespachar(pedido) || !pedido.plazoDespachoHasta) return null;
  return plazoVencido(pedido, ahora)
    ? <ChipEstado tono="error">Plazo vencido</ChipEstado>
    : <ChipEstado tono="aviso">Antes del {formatFechaHora(pedido.plazoDespachoHasta)}</ChipEstado>;
}

/** Datos de entrega: solo lo necesario para despachar (en un retiro, nada del cliente). */
function Entrega({ p }) {
  if (esEnvio(p)) {
    return (
      <dl className="ui-datos">
        <div className="ui-datos__item"><dt>Recibe</dt><dd>{p.envioReceptor}</dd></div>
        {p.envioTelefono && (
          <div className="ui-datos__item"><dt>Teléfono</dt><dd><a className="pdt-enlace" href={`tel:${p.envioTelefono}`}>{p.envioTelefono}</a></dd></div>
        )}
        <div className="ui-datos__item pdt-ancho">
          <dt>Dirección</dt><dd>{[p.envioDireccion, p.envioCiudad].filter(Boolean).join(", ")}</dd>
        </div>
        {p.envioIndicaciones && <div className="ui-datos__item pdt-ancho"><dt>Indicaciones</dt><dd>{p.envioIndicaciones}</dd></div>}
      </dl>
    );
  }
  if (esRetiro(p)) {
    return (
      <p className="pdt-texto">
        El cliente retira en tu tienda ({[p.tiendaDireccion, p.tiendaCiudad].filter(Boolean).join(", ")}). Cuando llegue,
        pídele su <strong>código de retiro</strong> y valídalo en este pedido.
      </p>
    );
  }
  return (
    <dl className="ui-datos">
      <div className="ui-datos__item"><dt>Taller</dt><dd>{p.tallerNombre}</dd></div>
      {p.tallerTelefono && (
        <div className="ui-datos__item"><dt>Teléfono</dt><dd><a className="pdt-enlace" href={`tel:${p.tallerTelefono}`}>{p.tallerTelefono}</a></dd></div>
      )}
      {(p.tallerDireccion || p.tallerCiudad) && (
        <div className="ui-datos__item pdt-ancho"><dt>Dirección</dt><dd>{[p.tallerDireccion, p.tallerCiudad].filter(Boolean).join(", ")}</dd></div>
      )}
      {p.vehiculoDescripcion && <div className="ui-datos__item pdt-ancho"><dt>Vehículo</dt><dd>{p.vehiculoDescripcion}</dd></div>}
    </dl>
  );
}

/** En qué va un pedido sin acción pendiente de la tienda. */
function textoEstado(p) {
  if (p.estado === "DESPACHADO") {
    if (p.problemaReportado) return "El cliente reportó un problema: DriveMaster lo está revisando y tu pago de este pedido está en pausa.";
    if (esEnvio(p)) {
      return `Esperando que el cliente confirme que lo recibió${p.autoconfirmaDespuesDe ? `; si no responde, se confirma solo el ${formatFechaHora(p.autoconfirmaDespuesDe)}` : ""}. Tu pago se habilita al confirmarse.`;
    }
    return "Esperando que el taller confirme que recibió los repuestos.";
  }
  if (p.estado === "LISTO_PARA_RETIRAR" && p.revisionRetiroDesde && !p.resolucionRetiro) {
    return "Lleva 15 días o más sin retirarse y DriveMaster lo está revisando. Si el cliente llega, entrégaselo con su código.";
  }
  if (p.estado === "NO_ENTREGADO" && p.resolucionRetiro === "REEMBOLSO") {
    return `DriveMaster lo canceló porque el cliente no lo retiró${p.motivoResolucionRetiro ? `: ${p.motivoResolucionRetiro}` : ""}. Las unidades en stock volvieron a tu inventario y este pedido no se paga.`;
  }
  if (p.estado === "RETIRADO") return "El cliente retiró el pedido. El pago quedó habilitado para liquidar.";
  if (p.estado === "ENTREGADO" || p.estado === "RECIBIDO") return "Pedido entregado. El pago quedó habilitado para liquidar.";
  if (p.estado === "NO_DESPACHADO") return `No se despachó: ${p.motivoNoDespacho || "sin motivo registrado"}. DriveMaster reembolsa al cliente.`;
  if (p.estado === "NO_ENTREGADO") return "DriveMaster resolvió el problema con reembolso al cliente: este pedido no se paga.";
  return null;
}

/**
 * Pedidos pagados que la tienda debe atender: los de talleres (se llevan al taller) y las compras
 * directas del catálogo (envío a domicilio o retiro en la tienda).
 *
 * De quien compró solo se muestra lo necesario para despachar: en un envío, quién recibe, su teléfono y la
 * dirección; en un retiro, nada (el cliente se identifica con su código, que se valida aquí).
 * "No puedo despachar" exige el motivo; el reembolso al cliente lo gestiona DriveMaster.
 */
export default function PedidosTienda({ param, irA }) {
  const token = getToken();
  // Desde un aviso (o desde otra sección) se abre directo un pedido: { abrir, volverA }
  const destino = destinoSeccion(param);
  const [pedidos, setPedidos] = useState([]);
  const [imagenes, setImagenes] = useState({});
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const [filtro, setFiltro] = useState("ATENDER");
  const [busqueda, setBusqueda] = useState("");
  const [abiertoId, setAbiertoId] = useState(destino.abrir);
  const [modo, setModo] = useState(null); // null | DESPACHAR | NO_DESPACHAR
  const [nota, setNota] = useState("");
  const [tipoMotivo, setTipoMotivo] = useState("");
  const [errores, setErrores] = useState({}); // motivo, nota, codigo, accion
  const [procesando, setProcesando] = useState(false);
  const [codigo, setCodigo] = useState("");
  const [ahora] = useState(() => Date.now());
  const campoCodigo = useRef(null);

  const cargar = useCallback(async () => {
    try {
      setPedidos((await misPedidosTienda(token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => { cargar(); }, [cargar]);

  // Abierto desde un aviso: el detalle empieza arriba
  useEffect(() => {
    if (destino.abrir) window.scrollTo({ top: 0 });
  }, [destino.abrir]);

  // Las fotos salen del catálogo de la tienda (el pedido guarda solo el nombre); si falla, quedan las de reemplazo
  useEffect(() => {
    let vivo = true;
    listarProductosMiTienda(token)
      .then((lista) => { if (vivo) setImagenes(Object.fromEntries((lista || []).map((p) => [p.id, p.imagenUrl]))); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [token]);

  const conteo = useMemo(() => contar(pedidos), [pedidos]);
  const visibles = useMemo(() => {
    const f = FILTROS.find((x) => x.key === filtro) || FILTROS[0];
    return ordenarPorUrgencia(pedidos.filter(f.filtro).filter((p) => coincide(p, busqueda)), ahora);
  }, [pedidos, filtro, busqueda, ahora]);
  const abierto = pedidos.find((p) => p.id === abiertoId) || null;
  // 20 por página; vuelve a la primera al cambiar de pestaña o de búsqueda (los contadores siguen contando todo)
  const pag = usePaginacion(visibles.length, filtro + "|" + busqueda);
  const enPagina = paginar(visibles, pag.pagina);

  const reiniciarFormularios = () => {
    setModo(null);
    setNota("");
    setTipoMotivo("");
    setErrores({});
    setCodigo("");
  };

  const abrir = (id) => {
    reiniciarFormularios();
    setAviso(null);
    setAbiertoId(id);
    window.scrollTo({ top: 0 });
  };

  const [ocupadoFila, setOcupadoFila] = useState(null); // id del pedido cuya acción corre desde la lista

  const accionarFila = async (p, accion) => {
    if (accion.tipo === "DESPACHAR") { abrir(p.id); setModo("DESPACHAR"); return; }
    if (accion.tipo === "VALIDAR") { abrir(p.id); requestAnimationFrame(() => campoCodigo.current?.focus()); return; }
    if (accion.tipo !== "PREPARAR" && accion.tipo !== "LISTO") { abrir(p.id); return; }
    setOcupadoFila(p.id);
    try {
      if (accion.tipo === "PREPARAR") await prepararPedido(token, p.id);
      else await marcarListoParaRetirar(token, p.id);
      avisar(accion.tipo === "PREPARAR"
        ? `Pedido #${refSolicitud(p.id)} en preparación.`
        : `Pedido #${refSolicitud(p.id)} listo para retirar: avisamos al cliente.`);
      await cargar();
    } catch (e) {
      avisar(e.message, "error");
    } finally {
      setOcupadoFila(null);
    }
  };

  const desdeAfuera = destino.abrir != null && abiertoId === destino.abrir;

  const cerrar = () => {
    if (desdeAfuera && destino.volverA && irA) { irA(destino.volverA); return; }
    reiniciarFormularios();
    setAbiertoId(null);
    window.scrollTo({ top: 0 });
  };

  const reintentar = async () => {
    setReintentando(true);
    await cargar();
    setReintentando(false);
  };

  /** Ejecuta una acción sobre el pedido abierto, recarga y deja el aviso (el detalle queda abierto con el nuevo estado). */
  const ejecutar = async (accion, mensajeOk) => {
    setProcesando(true);
    setErrores({});
    setAviso(null);
    try {
      await accion();
      reiniciarFormularios();
      setAviso(mensajeOk);
      await cargar();
    } catch (e) {
      setErrores({ accion: e.message });
    } finally {
      setProcesando(false);
    }
  };

  const confirmar = (pedido) => {
    if (modo === "NO_DESPACHAR") {
      const faltan = {};
      if (!tipoMotivo) faltan.motivo = "Elige el motivo.";
      if (tipoMotivo === "OTRO" && !nota.trim()) faltan.nota = "Cuéntanos por qué no puedes despachar.";
      if (Object.keys(faltan).length) { setErrores(faltan); return; }
      ejecutar(() => noPuedoDespachar(token, pedido.id, tipoMotivo, nota.trim() || null),
        "Registramos que no puedes despachar este pedido. DriveMaster reembolsará al cliente.");
      return;
    }
    ejecutar(() => despacharPedido(token, pedido.id, nota.trim() || null), "Pedido despachado. Avisamos al cliente.");
  };

  const validar = async (pedido) => {
    const limpio = codigo.trim();
    if (!limpio) {
      setErrores({ codigo: "Escribe el código de retiro que te muestra el cliente." });
      campoCodigo.current?.focus();
      return;
    }
    setProcesando(true);
    setErrores({});
    setAviso(null);
    try {
      await validarCodigoRetiro(token, pedido.id, limpio);
      setCodigo("");
      setAviso(`Entrega registrada: el pedido #${refSolicitud(pedido.id)} quedó como retirado y su pago quedó habilitado para liquidar.`);
      await cargar();
    } catch (e) {
      const extra = /no es correcto/i.test(e.message) ? " Si el cliente te muestra un código de otra tienda o de otro pedido, no sirve aquí." : "";
      setErrores({ codigo: `${e.message}${extra}` });
      campoCodigo.current?.focus();
    } finally {
      setProcesando(false);
    }
  };

  if (cargando) {
    return (
      <div className="ss-panel ss-panel--tienda pdt-root">
        <EncabezadoPagina titulo="Pedidos" descripcion="Pedidos de clientes y talleres a tu tienda." />
        <Esqueleto variante="lista" cantidad={4} texto="Cargando pedidos…" />
      </div>
    );
  }

  // ─── Detalle ────────────────────────────────────────────────────────────
  if (abierto) {
    const p = abierto;
    const accion = accionPrincipal(p);
    const plazo = textoPlazo(p, ahora);
    const estado = textoEstado(p);
    const historial = historialPedido(p);
    const formularioAbierto = modo !== null;

    // La acción del estado, también en la barra inferior del celular
    const irASigue = () => document.getElementById("pdt-sigue")?.scrollIntoView({ block: "start" });
    const entrega = accionEntrega(p);
    const accionBarra = !accion || formularioAbierto ? null
      : accion.tipo === "VALIDAR" ? () => { irASigue(); campoCodigo.current?.focus({ preventScroll: true }); }
        : accion.tipo === "PREPARAR" ? () => ejecutar(() => prepararPedido(token, p.id), "Pedido en preparación.")
          : accion.tipo === "LISTO"
            ? () => ejecutar(() => marcarListoParaRetirar(token, p.id), "Marcado como listo para retirar: avisamos al cliente.")
            : () => { setModo("DESPACHAR"); irASigue(); };

    return (
      <div className="ss-panel ss-panel--tienda pdt-root">
        <EncabezadoPagina titulo={`Pedido #${refSolicitud(p.id)}`} onVolver={cerrar}
          textoVolver={desdeAfuera && destino.volverA ? "Volver" : "Pedidos"}>
          <p className="pdt-meta">
            <ChipEstado estado={p.estado} catalogo={ESTADOS_PEDIDO} />
            <ChipPlazo pedido={p} ahora={ahora} />
            <span>{formatFechaHora(p.fechaCreacion)}</span>
            <span>{metodoEntrega(p)}</span>
            {!esCatalogo(p) && <span>Para un taller</span>}
          </p>
        </EncabezadoPagina>

        {aviso && <Alerta tono="ok" className="pdt-alerta" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}
        {error && <Alerta className="pdt-alerta" onReintentar={reintentar} reintentando={reintentando}>{error}</Alerta>}

        <div className="pdt-detalle">
          <div className="pdt-detalle__principal">
            <section className="ui-card pdt-bloque" aria-labelledby="pdt-productos">
              <div className="ui-card__header">
                <h2 id="pdt-productos" className="ui-card__titulo">Productos</h2>
                <span className="ui-card__sub">{cantidadProductos(p)} {cantidadProductos(p) === 1 ? "unidad" : "unidades"}</span>
              </div>
              <div className="ui-card__cuerpo">
                <ul className="pdt-items">
                  {p.items.map((i, n) => (
                    <li key={`${i.productoId}-${n}`} className="pdt-item">
                      <ImagenProducto src={imagenes[i.productoId]} className="pdt-item__imagen" tamanioIcono={22} />
                      <span className="pdt-item__texto">
                        <strong>{i.productoNombre}</strong>
                        <span className="pdt-item__meta">
                          {[i.marca, i.referencia].filter(Boolean).join(" · ")}
                        </span>
                        <span className="pdt-item__meta">
                          {i.cantidad} × {formatPrecio(i.precioUnitario)}
                          {i.disponibilidad === "ENCARGO" && (
                            <ChipEstado tono="info">Encargo{i.diasEntrega ? ` · ${plural(i.diasEntrega, "día")}` : ""}</ChipEstado>
                          )}
                        </span>
                      </span>
                      <span className="pdt-item__valor ui-precio">{formatPrecio(i.subtotal)}</span>
                    </li>
                  ))}
                </ul>
                <dl className="pdt-totales">
                  <div><dt>Subtotal</dt><dd>{formatPrecio(subtotal(p))}</dd></div>
                  {esEnvio(p) && <div><dt>Envío</dt><dd>{formatPrecio(p.costoEnvio)}</dd></div>}
                  <div><dt>IVA</dt><dd>{formatPrecio(p.iva)}</dd></div>
                  <div className="pdt-totales__final"><dt>Total del pedido</dt><dd>{formatPrecio(p.total)}</dd></div>
                </dl>
              </div>
            </section>

            <section className="ui-card pdt-bloque" aria-labelledby="pdt-entrega">
              <div className="ui-card__header">
                <h2 id="pdt-entrega" className="ui-card__titulo">{esRetiro(p) ? "Retiro" : esEnvio(p) ? "Envío" : "Entrega en el taller"}</h2>
              </div>
              <div className="ui-card__cuerpo"><Entrega p={p} /></div>
            </section>
          </div>

          <aside className="pdt-detalle__lateral">
            <section className={`ui-card pdt-bloque${accion ? " pdt-accion" : ""}`} aria-labelledby="pdt-sigue">
              <div className="ui-card__header">
                <h2 id="pdt-sigue" className="ui-card__titulo">{accion ? "Qué sigue" : "Estado"}</h2>
              </div>
              <div className="ui-card__cuerpo pdt-sigue">
                {plazo && (
                  <p className={`pdt-plazo${plazoVencido(p, ahora) ? " pdt-plazo--vencido" : ""}`}>
                    <Icon name="clock" size={16} /> {plazo}{plazoVencido(p, ahora) && ". DriveMaster ya fue alertado."}
                  </p>
                )}
                {estado && <p className="pdt-texto">{estado}</p>}
                {errores.accion && <Alerta onCerrar={() => setErrores({})}>{errores.accion}</Alerta>}

                {accion?.tipo === "VALIDAR" && (
                  <form className="pdt-codigo" onSubmit={(e) => { e.preventDefault(); validar(p); }} noValidate>
                    <Campo id={`codigo-${p.id}`} label="Código de retiro del cliente" error={errores.codigo}
                      ayuda="Sirve una sola vez y solo para este pedido. Tras 5 intentos fallidos el pedido se bloquea 15 minutos.">
                      <input ref={campoCodigo} className="ui-field__input ui-field__input--codigo" placeholder="XXXX-XXXX" maxLength={12}
                        autoComplete="off" autoCapitalize="characters" spellCheck={false} inputMode="text"
                        value={codigo} onChange={(e) => { setCodigo(e.target.value.toUpperCase()); if (errores.codigo) setErrores({}); }} />
                    </Campo>
                    <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande ui-btn--bloque" disabled={procesando} aria-busy={procesando}>
                      {procesando ? "Validando…" : "Validar y entregar"}
                    </button>
                  </form>
                )}

                {porDespachar(p) && !formularioAbierto && (
                  <div className="pdt-botones">
                    {accion.tipo === "PREPARAR" && (
                      <button type="button" className="ui-btn ui-btn--primario ui-btn--bloque" disabled={procesando} aria-busy={procesando}
                        onClick={() => ejecutar(() => prepararPedido(token, p.id), "Pedido en preparación.")}>
                        {accion.texto}
                      </button>
                    )}
                    {/* Si ya consiguió el encargo, puede entregarlo sin pasar por preparación */}
                    {entrega.tipo === "LISTO" ? (
                      <button type="button" className={`ui-btn ${accion.tipo === "PREPARAR" ? "ui-btn--secundario" : "ui-btn--primario"} ui-btn--bloque`}
                        disabled={procesando} aria-busy={procesando}
                        onClick={() => ejecutar(() => marcarListoParaRetirar(token, p.id), "Marcado como listo para retirar: avisamos al cliente.")}>
                        {entrega.texto}
                      </button>
                    ) : (
                      <button type="button" className={`ui-btn ${accion.tipo === "PREPARAR" ? "ui-btn--secundario" : "ui-btn--primario"} ui-btn--bloque`}
                        onClick={() => setModo("DESPACHAR")}>
                        <Icon name="box" size={16} /> {entrega.texto}
                      </button>
                    )}
                    <button type="button" className="ui-btn ui-btn--texto pdt-peligro" onClick={() => setModo("NO_DESPACHAR")}>
                      No puedo despachar
                    </button>
                  </div>
                )}

                {porDespachar(p) && formularioAbierto && (
                  <form className="pdt-form" onSubmit={(e) => { e.preventDefault(); confirmar(p); }} noValidate>
                    {modo === "NO_DESPACHAR" && (
                      <fieldset className="pdt-motivos" aria-describedby={errores.motivo ? "pdt-motivo-error" : undefined}>
                        <legend className="ui-field__label">¿Por qué no puedes despachar?</legend>
                        {MOTIVOS_NO_DESPACHO.map((m) => (
                          <label key={m.valor} className={`ui-opcion${tipoMotivo === m.valor ? " ui-opcion--elegida" : ""}`}>
                            <input type="radio" className="ui-sr" name={`motivo-${p.id}`} value={m.valor}
                              checked={tipoMotivo === m.valor} onChange={() => { setTipoMotivo(m.valor); setErrores({}); }} />
                            {m.label}
                          </label>
                        ))}
                        {errores.motivo && <p id="pdt-motivo-error" className="ui-field__error"><Icon name="alert" size={14} />{errores.motivo}</p>}
                      </fieldset>
                    )}
                    <Campo id={`nota-${p.id}`} error={errores.nota}
                      opcional={modo === "DESPACHAR" || tipoMotivo !== "OTRO"}
                      label={modo === "DESPACHAR" ? (esEnvio(p) ? "Guía o datos del envío" : "Nota del despacho") : "Detalle"}
                      ayuda={modo === "DESPACHAR"
                        ? (esEnvio(p) ? "El cliente la ve en su compra." : "Por ejemplo, el mensajero o la guía.")
                        : `Se cancela tu parte del pedido y DriveMaster reembolsa ${formatPrecio(p.total)} al cliente.`}>
                      <textarea className="ui-field__input" rows={3} maxLength={500} autoFocus={modo === "DESPACHAR"}
                        value={nota} onChange={(e) => { setNota(e.target.value); if (errores.nota) setErrores({}); }} />
                    </Campo>
                    <div className="ui-form-pie">
                      <button type="submit" className={`ui-btn ${modo === "NO_DESPACHAR" ? "ui-btn--peligro" : "ui-btn--primario"}`}
                        disabled={procesando} aria-busy={procesando}>
                        {procesando ? "Guardando…" : modo === "DESPACHAR" ? "Confirmar despacho" : "Confirmar: no puedo despachar"}
                      </button>
                      <button type="button" className="ui-btn ui-btn--secundario" onClick={reiniciarFormularios} disabled={procesando}>
                        Cancelar
                      </button>
                    </div>
                  </form>
                )}
              </div>
            </section>

            {historial.length > 0 && (
              <section className="ui-card pdt-bloque" aria-labelledby="pdt-historial">
                <div className="ui-card__header"><h2 id="pdt-historial" className="ui-card__titulo">Historial</h2></div>
                <div className="ui-card__cuerpo"><Historial pasos={historial} etiqueta={`Historial del pedido #${refSolicitud(p.id)}`} /></div>
              </section>
            )}
          </aside>
        </div>

        {accionBarra && (
          <BarraAccionMovil etiqueta="Total del pedido" valor={formatPrecio(p.total)} nota={plazo || undefined}>
            <button type="button" className="ui-btn ui-btn--primario" onClick={accionBarra} disabled={procesando} aria-busy={procesando}>
              {accion.tipo === "VALIDAR" ? "Validar código" : accion.tipo === "PREPARAR" ? "Empezar preparación"
                : accion.tipo === "LISTO" ? "Marcar como listo" : "Despachar"}
            </button>
          </BarraAccionMovil>
        )}
      </div>
    );
  }

  // ─── Lista ──────────────────────────────────────────────────────────────
  const [tituloVacio, textoVacio] = VACIOS[filtro];
  return (
    <div className="ss-panel ss-panel--tienda pdt-root">
      <EncabezadoPagina titulo="Pedidos"
        descripcion={conteo.ATENDER > 0
          ? `${conteo.ATENDER} ${conteo.ATENDER === 1 ? "pedido espera" : "pedidos esperan"} que lo prepares, despaches o entregues. Lo más urgente va primero.`
          : "Pedidos de clientes y talleres a tu tienda: prepáralos, despáchalos o entrégalos con el código de retiro."} />

      {error && (
        <Alerta className="pdt-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar tus pedidos. {error}
        </Alerta>
      )}
      {!error && abiertoId && !abierto && (
        <Alerta tono="aviso" className="pdt-alerta" onCerrar={() => setAbiertoId(null)}>
          No encontramos el pedido del aviso entre los tuyos. Puede que ya no esté en tu lista.
        </Alerta>
      )}
      {aviso && <Alerta tono="ok" className="pdt-alerta" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}

      <div className="pdt-barra">
        <div className="pdt-pestanas">
          {/* Principales: el trabajo del día, siempre con su cifra (0 si no hay) */}
          <div className="ui-filtros" role="group" aria-label="Pedidos en curso">
            {FILTROS.filter((f) => f.principal).map((f) => (
              <button key={f.key} type="button" className="ui-chip pdt-pestana" aria-pressed={filtro === f.key} onClick={() => setFiltro(f.key)}>
                {f.label}
                <span className={`ui-chip__contador${conteo[f.key] ? "" : " pdt-pestana__cero"}`}>{conteo[f.key] || 0}</span>
              </button>
            ))}
          </div>
          <div className="ui-filtros pdt-secundarios" role="group" aria-label="Otros filtros de pedidos">
            <span className="pdt-secundarios__titulo">Ver también:</span>
            {FILTROS.filter((f) => !f.principal).map((f) => (
              <button key={f.key} type="button" className="ui-btn ui-btn--texto ui-btn--chico pdt-secundario" aria-pressed={filtro === f.key}
                onClick={() => setFiltro(f.key)}>
                {f.label}{conteo[f.key] > 0 ? ` (${conteo[f.key]})` : ""}
              </button>
            ))}
          </div>
          {FILTROS.find((f) => f.key === filtro)?.ayuda && (
            <p className="pdt-ayuda">{FILTROS.find((f) => f.key === filtro).ayuda}</p>
          )}
        </div>
        <div className="ui-buscador pdt-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="pdt-busqueda" className="ui-sr">Buscar por número de pedido, cliente, taller o producto</label>
          <input id="pdt-busqueda" className="ui-field__input" type="search" value={busqueda} autoComplete="off"
            onChange={(e) => setBusqueda(e.target.value)} placeholder="Número, cliente, taller o producto" />
        </div>
      </div>

      {visibles.length === 0 ? (
        busqueda.trim() ? (
          <EstadoVacio icono="search" titulo="Nada coincide con tu búsqueda"
            texto={`No encontramos “${busqueda.trim()}” en esta pestaña. Prueba con el número del pedido.`}>
            <button type="button" className="ui-btn ui-btn--secundario" onClick={() => setBusqueda("")}>Limpiar búsqueda</button>
          </EstadoVacio>
        ) : !error && <EstadoVacio icono="box" titulo={tituloVacio} texto={textoVacio} />
      ) : (
        <ul className="pdt-lista">
          {enPagina.map((p) => {
            const accion = accionFila(p);
            return (
            <li key={p.id}>
              <div className={`pdt-fila${plazoVencido(p, ahora) || (p.revisionRetiroDesde && !p.resolucionRetiro) ? " pdt-fila--vencido" : ""}`}>
                <span className="pdt-fila__id">
                  <button type="button" className="pdt-fila__abrir" onClick={() => abrir(p.id)}
                    aria-label={`Pedido #${refSolicitud(p.id)}, ${ESTADOS_PEDIDO[p.estado]?.label || p.estado}, ${formatPrecio(p.total)}`}>
                    <strong className="pdt-fila__numero">#{refSolicitud(p.id)}</strong>
                  </button>
                  <span className="pdt-fila__meta">{formatFechaHora(p.fechaCreacion)}</span>
                </span>
                <span className="pdt-fila__quien">
                  <strong>{destinatario(p)}</strong>
                  <span className="pdt-fila__meta">
                    {cantidadProductos(p)} {cantidadProductos(p) === 1 ? "producto" : "productos"} · {metodoEntrega(p)}
                  </span>
                </span>
                <span className="pdt-fila__estado">
                  <ChipEstado estado={p.estado} catalogo={ESTADOS_PEDIDO} />
                  <ChipPlazo pedido={p} ahora={ahora} />
                </span>
                <strong className="pdt-fila__total ui-precio">{formatPrecio(p.total)}</strong>
                <span className="pdt-fila__accion">
                  {accion.tipo === "ESPERA" ? (
                    <span className="pdt-fila__espera">{accion.texto}</span>
                  ) : (
                    <button type="button" disabled={ocupadoFila === p.id} aria-busy={ocupadoFila === p.id}
                      className={`ui-btn ui-btn--chico ${accion.tipo === "VER" ? "ui-btn--texto" : "ui-btn--primario"}`}
                      onClick={() => accionarFila(p, accion)} aria-label={`${accion.texto}: pedido #${refSolicitud(p.id)}`}>
                      {ocupadoFila === p.id ? "Guardando…" : accion.texto}
                    </button>
                  )}
                </span>
              </div>
            </li>
            );
          })}
        </ul>
      )}
      {visibles.length > 0 && (
        <Paginacion pagina={pag.pagina} total={visibles.length} onCambiar={pag.cambiar} etiqueta="Páginas de pedidos" />
      )}
    </div>
  );
}
