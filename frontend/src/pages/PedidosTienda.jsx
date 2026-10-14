import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import {
  misPedidosTienda, prepararPedido, despacharPedido, noPuedoDespachar, marcarListoParaRetirar, validarCodigoRetiro,
} from "../services/pedidoTiendaService";
import Etiqueta from "../components/servicio/Etiqueta";
import { ESTADOS_PEDIDO, MOTIVOS_NO_DESPACHO, formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";
import "../css/pedidos-tienda.css";

const FILTROS = [
  { key: "POR_DESPACHAR", label: "Por despachar", estados: ["PENDIENTE", "EN_PREPARACION"] },
  { key: "LISTOS", label: "Listos para retirar", estados: ["LISTO_PARA_RETIRAR"] },
  { key: "EN_CAMINO", label: "En camino", estados: ["DESPACHADO"] },
  { key: "CERRADOS", label: "Cerrados", estados: ["RECIBIDO", "RETIRADO", "ENTREGADO", "NO_DESPACHADO", "NO_ENTREGADO"] },
  { key: "TODOS", label: "Todos", estados: null },
];

const porDespachar = (p) => p.estado === "PENDIENTE" || p.estado === "EN_PREPARACION";
const tieneEncargo = (p) => (p.items || []).some((i) => i.disponibilidad === "ENCARGO");
const plazoVencido = (p) => porDespachar(p) && p.plazoDespachoHasta && new Date(p.plazoDespachoHasta) < new Date();
const esCatalogo = (p) => p.origen === "CATALOGO";
const esRetiro = (p) => esCatalogo(p) && p.entrega === "RETIRO";
const esEnvio = (p) => esCatalogo(p) && p.entrega === "ENVIO";

/**
 * Pedidos pagados que la tienda debe atender: los de talleres (se llevan al taller) y las compras
 * directas del catálogo (envío a domicilio o retiro en la tienda).
 *
 * De quien compró solo se muestra lo necesario para despachar: en un envío, quién recibe, su teléfono y la
 * dirección; en un retiro, nada (el cliente se identifica con su código, que se valida aquí).
 * "No puedo despachar" exige el motivo; el reembolso al cliente lo gestiona DriveMaster.
 */
export default function PedidosTienda() {
  const token = getToken();
  const [pedidos, setPedidos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [filtro, setFiltro] = useState("POR_DESPACHAR");
  const [abiertoId, setAbiertoId] = useState(null);
  const [modo, setModo] = useState(null); // null | DESPACHAR | NO_DESPACHAR
  const [nota, setNota] = useState("");
  const [tipoMotivo, setTipoMotivo] = useState("");
  const [procesando, setProcesando] = useState(false);
  // Validación de retiro: un código y un error por pedido
  const [codigos, setCodigos] = useState({});
  const [erroresCodigo, setErroresCodigo] = useState({});

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

  const abrir = (id) => {
    setAbiertoId(abiertoId === id ? null : id);
    setModo(null);
    setNota("");
    setTipoMotivo("");
    setError(null);
  };

  const confirmar = async (pedido) => {
    if (modo === "NO_DESPACHAR") {
      if (!tipoMotivo) return setError("Elige el motivo.");
      if (tipoMotivo === "OTRO" && !nota.trim()) return setError("Cuéntanos por qué no puedes despachar.");
    }
    setProcesando(true);
    setError(null);
    try {
      if (modo === "DESPACHAR") await despacharPedido(token, pedido.id, nota.trim() || null);
      else await noPuedoDespachar(token, pedido.id, tipoMotivo, nota.trim() || null);
      setAbiertoId(null);
      setModo(null);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const accionSimple = async (accion, pedido, mensajeOk) => {
    setProcesando(true);
    setError(null);
    try {
      await accion(token, pedido.id);
      if (mensajeOk) setAviso(mensajeOk);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const validar = async (pedido) => {
    const codigo = (codigos[pedido.id] || "").trim();
    if (!codigo) return setErroresCodigo((m) => ({ ...m, [pedido.id]: "Escribe el código de retiro que te muestra el cliente." }));
    setProcesando(true);
    setErroresCodigo((m) => ({ ...m, [pedido.id]: null }));
    try {
      await validarCodigoRetiro(token, pedido.id, codigo);
      setCodigos((m) => ({ ...m, [pedido.id]: "" }));
      setAviso("Entrega registrada. El pago de este pedido quedó habilitado para liquidar.");
      await cargar();
    } catch (e) {
      setErroresCodigo((m) => ({ ...m, [pedido.id]: e.message }));
    } finally {
      setProcesando(false);
    }
  };

  if (cargando) return <p className="sv-empty">Cargando pedidos...</p>;

  const enFiltro = (key) => {
    const estados = FILTROS.find((f) => f.key === key)?.estados;
    return estados ? pedidos.filter((p) => estados.includes(p.estado)) : pedidos;
  };
  const visibles = enFiltro(filtro);

  return (
    <div className="ss-panel ss-panel--tienda">
      <header className="pd-encabezado">
        <h1 className="pd-encabezado__titulo">Pedidos</h1>
        <p className="pd-encabezado__sub">Pedidos de clientes y talleres a tu tienda: prepáralos, despáchalos o entrégalos con el código de retiro.</p>
      </header>

      {error && !abiertoId && <p className="ss-alerta" role="alert">{error}</p>}
      {aviso && <p className="ss-exito" role="status">{aviso} <button className="sv-btn-outline" onClick={() => setAviso(null)}>Cerrar</button></p>}

      <nav className="ss-filtros" aria-label="Pedidos por estado">
        {FILTROS.map((f) => (
          <button key={f.key} className={`ss-filtro${filtro === f.key ? " ss-filtro--activo" : ""}`}
            aria-current={filtro === f.key ? "page" : undefined} onClick={() => setFiltro(f.key)}>
            {f.label}
            {enFiltro(f.key).length > 0 && <span className="ss-filtro__conteo">{enFiltro(f.key).length}</span>}
          </button>
        ))}
      </nav>

      {visibles.length === 0 ? (
        <p className="sv-empty">
          {filtro === "POR_DESPACHAR" ? "No tienes pedidos por despachar. Te avisaremos por correo cuando llegue uno." : "No hay pedidos en esta lista."}
        </p>
      ) : (
        <div className="pd-lista">
          {visibles.map((p) => {
            const retiro = esRetiro(p);
            const envio = esEnvio(p);
            const pideAccion = porDespachar(p) || p.estado === "LISTO_PARA_RETIRAR";
            return (
              <article key={p.id} className={`pd-pedido${pideAccion ? " pd-pedido--accion" : ""}`} aria-labelledby={`pedido-${p.id}`}>
                {/* Cabecera: número, cuándo y cómo, estado y total */}
                <header className="pd-pedido__cabecera">
                  <div className="pd-pedido__id">
                    <h2 id={`pedido-${p.id}`} className="pd-pedido__numero">Pedido #{refSolicitud(p.id)}</h2>
                    <span className="pd-pedido__meta">
                      {formatFechaHora(p.fechaCreacion)}
                      {esCatalogo(p) ? <> · Compra directa · {retiro ? "Retiro en tu tienda" : "Envío a domicilio"}</> : " · Para un taller"}
                    </span>
                  </div>
                  <div className="pd-pedido__resumen">
                    <Etiqueta estado={p.estado} catalogo={ESTADOS_PEDIDO} />
                    <strong className="pd-pedido__total">{formatPrecio(p.total)}</strong>
                  </div>
                </header>

                <div className="pd-pedido__cuerpo">
                  {/* Productos y totales */}
                  <div className="pd-pedido__productos">
                    <ul className="pd-items">
                      {p.items.map((i, n) => (
                        <li key={n} className="pd-item">
                          <span className="pd-item__desc">
                            <span className="pd-item__nombre">{i.cantidad} × {i.productoNombre}</span>
                            <span className="pd-item__meta">
                              {[i.marca, i.referencia].filter(Boolean).join(" · ")}
                              {i.disponibilidad === "ENCARGO" && ` · Encargo${i.diasEntrega ? ` (${i.diasEntrega} días)` : ""}`}
                            </span>
                          </span>
                          <span className="pd-item__valor">{formatPrecio(i.subtotal)}</span>
                        </li>
                      ))}
                    </ul>
                    <dl className="pd-totales">
                      <div><dt>IVA</dt><dd>{formatPrecio(p.iva)}</dd></div>
                      {envio && <div><dt>Envío</dt><dd>{formatPrecio(p.costoEnvio)}</dd></div>}
                      <div className="pd-totales__final"><dt>Total del pedido</dt><dd>{formatPrecio(p.total)}</dd></div>
                    </dl>
                  </div>

                  {/* Entrega y seguimiento: solo lo necesario para despachar */}
                  <div className="pd-pedido__entrega">
                    <h3 className="pd-pedido__subtitulo">{retiro ? "Retiro" : envio ? "Envío" : "Entrega en taller"}</h3>
                    {envio && (
                      <p className="pd-destino">
                        Enviar a <strong>{p.envioReceptor}</strong>
                        <br />{[p.envioDireccion, p.envioCiudad].filter(Boolean).join(", ")}
                        {p.envioTelefono && <> · Tel. {p.envioTelefono}</>}
                        {p.envioIndicaciones && <><br />Indicaciones: {p.envioIndicaciones}</>}
                      </p>
                    )}
                    {retiro && (
                      <p className="pd-destino">
                        El cliente retira en tu tienda ({[p.tiendaDireccion, p.tiendaCiudad].filter(Boolean).join(", ")}).
                        Cuando llegue, pídele su <strong>código de retiro</strong> y valídalo aquí.
                      </p>
                    )}
                    {!esCatalogo(p) && (
                      <p className="pd-destino">
                        Entregar en <strong>{p.tallerNombre}</strong>
                        {(p.tallerDireccion || p.tallerCiudad) && <> · {[p.tallerDireccion, p.tallerCiudad].filter(Boolean).join(", ")}</>}
                        {p.tallerTelefono && <> · Tel. {p.tallerTelefono}</>}
                        <br />Vehículo: {p.vehiculoDescripcion}
                      </p>
                    )}

                    {porDespachar(p) && p.plazoDespachoHasta && (
                      <p className={plazoVencido(p) ? "pd-nota pd-nota--error" : "pd-nota pd-nota--plazo"} role={plazoVencido(p) ? "alert" : undefined}>
                        {plazoVencido(p)
                          ? `Plazo vencido el ${formatFechaHora(p.plazoDespachoHasta)}. DriveMaster ya fue alertado.`
                          : `${retiro ? "Déjalo listo" : "Despáchalo"} antes del ${formatFechaHora(p.plazoDespachoHasta)}.`}
                      </p>
                    )}
                    {p.estado === "EN_PREPARACION" && p.fechaPreparacion && (
                      <p className="pd-nota">En preparación desde el {formatFechaHora(p.fechaPreparacion)}</p>
                    )}
                    {p.estado === "LISTO_PARA_RETIRAR" && p.fechaListoRetiro && (
                      <p className="pd-nota">Listo para retirar desde el {formatFechaHora(p.fechaListoRetiro)}</p>
                    )}
                    {p.estado === "DESPACHADO" && (
                      <p className="pd-nota">Despachado el {formatFechaHora(p.fechaDespacho)}{p.notaDespacho ? ` · ${p.notaDespacho}` : ""}</p>
                    )}
                    {envio && p.estado === "DESPACHADO" && !p.problemaReportado && (
                      <p className="pd-nota">
                        Esperando que el cliente confirme que lo recibió
                        {p.autoconfirmaDespuesDe ? `; si no responde, se confirma solo el ${formatFechaHora(p.autoconfirmaDespuesDe)}` : ""}.
                        Tu pago se habilita al confirmarse.
                      </p>
                    )}
                    {envio && p.problemaReportado && (
                      <p className="ss-alerta" role="alert">
                        El cliente reportó un problema con este envío{p.problemaDetalle ? `: «${p.problemaDetalle}»` : ""}.
                        {p.estado === "DESPACHADO"
                          ? " DriveMaster lo está revisando; tu pago de este pedido está en pausa."
                          : p.estado === "NO_ENTREGADO"
                            ? " DriveMaster lo resolvió con reembolso al cliente: este pedido no se paga."
                            : " DriveMaster lo resolvió: la entrega quedó confirmada."}
                      </p>
                    )}
                    {p.estado === "ENTREGADO" && <p className="pd-nota">Entregado{p.recepcionAutomatica ? " (confirmado solo al vencer el plazo)" : " (confirmado por el cliente)"} el {formatFechaHora(p.fechaRecibidoCliente)}</p>}
                    {p.estado === "RETIRADO" && <p className="pd-nota">Retirado el {formatFechaHora(p.fechaRetirado)}</p>}
                    {p.estado === "NO_DESPACHADO" && <p className="pd-nota">No despachado: {p.motivoNoDespacho}</p>}
                  </div>
                </div>

                {/* Validar el código de retiro */}
                {p.estado === "LISTO_PARA_RETIRAR" && (
                  <form className="pd-pedido__pie pd-form" onSubmit={(e) => { e.preventDefault(); validar(p); }} aria-label="Validar código de retiro">
                    <div className="pd-form__campo">
                      <label htmlFor={`codigo-${p.id}`}>Código de retiro del cliente</label>
                      <input id={`codigo-${p.id}`} className="ss-input" placeholder="XXXX-XXXX" maxLength={12} autoComplete="off"
                        value={codigos[p.id] || ""} onChange={(e) => setCodigos((m) => ({ ...m, [p.id]: e.target.value.toUpperCase() }))} />
                      <p className="pd-form__ayuda">
                        Cada código sirve una sola vez y solo para este pedido. Tras 5 intentos fallidos el pedido se bloquea 15 minutos.
                      </p>
                    </div>
                    {erroresCodigo[p.id] && (
                      <p className="ss-alerta" role="alert">
                        {erroresCodigo[p.id]}
                        {/no es correcto/i.test(erroresCodigo[p.id]) &&
                          " Si el cliente te muestra un código de otra tienda o de otro pedido, no sirve aquí."}
                      </p>
                    )}
                    <div className="pd-acciones">
                      <button className="sv-btn" type="submit" disabled={procesando}>
                        {procesando ? "Validando..." : "Validar y entregar"}
                      </button>
                    </div>
                  </form>
                )}

                {/* Despacho */}
                {porDespachar(p) && (abiertoId !== p.id ? (
                  <div className="pd-pedido__pie pd-acciones">
                    {p.estado === "PENDIENTE" && tieneEncargo(p) && (
                      <button className="sv-btn-outline" onClick={() => accionSimple(prepararPedido, p)} disabled={procesando}>
                        Iniciar preparación
                      </button>
                    )}
                    {retiro ? (
                      <button className="sv-btn" onClick={() => accionSimple(marcarListoParaRetirar, p, "Marcado como listo para retirar: avisamos al cliente.")} disabled={procesando}>
                        Listo para retirar
                      </button>
                    ) : (
                      <button className="sv-btn" onClick={() => { abrir(p.id); setModo("DESPACHAR"); }}>Marcar despachado</button>
                    )}
                    <button className="sv-btn-outline pd-btn-peligro" onClick={() => { abrir(p.id); setModo("NO_DESPACHAR"); }}>No puedo despachar</button>
                  </div>
                ) : (
                  <div className="pd-pedido__pie pd-form">
                    {error && <p className="ss-alerta" role="alert">{error}</p>}
                    {modo === "NO_DESPACHAR" && (
                      <fieldset className="ct-motivos">
                        <legend className="pd-form__etiqueta">¿Por qué no puedes despachar?</legend>
                        {MOTIVOS_NO_DESPACHO.map((m) => (
                          <label key={m.valor}>
                            <input type="radio" name={`motivo-${p.id}`} value={m.valor}
                              checked={tipoMotivo === m.valor} onChange={() => setTipoMotivo(m.valor)} />
                            {m.label}
                          </label>
                        ))}
                      </fieldset>
                    )}
                    <div className="pd-form__campo">
                      <label htmlFor={`nota-${p.id}`}>
                        {modo === "DESPACHAR" ? (envio ? "Guía o información del envío (opcional, el cliente la ve)" : "Nota del despacho (opcional, ej. mensajero o guía)")
                          : tipoMotivo === "OTRO" ? "Detalle (obligatorio)" : "Detalle (opcional)"}
                      </label>
                      <textarea id={`nota-${p.id}`} className="ss-textarea" rows={2} maxLength={500}
                        value={nota} onChange={(e) => setNota(e.target.value)} />
                    </div>
                    {modo === "NO_DESPACHAR" && (
                      <p className="pd-form__ayuda">Se cancelará tu parte del pedido y DriveMaster reembolsará {formatPrecio(p.total)} al cliente.</p>
                    )}
                    <div className="pd-acciones">
                      <button className={`sv-btn${modo === "NO_DESPACHAR" ? " ss-btn-peligro" : ""}`}
                        onClick={() => confirmar(p)} disabled={procesando}>
                        {procesando ? "Guardando..." : modo === "DESPACHAR" ? "Confirmar despacho" : "Confirmar: no puedo despachar"}
                      </button>
                      <button className="sv-btn-outline" onClick={() => abrir(p.id)} disabled={procesando}>Volver</button>
                    </div>
                  </div>
                ))}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
