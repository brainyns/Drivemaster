import { useEffect, useState } from "react";
import { getToken } from "../../services/authService";
import { cambiarMinimoPieza, cambiarPrecioPieza, confirmarLlegada, miInventario } from "../../services/inventarioTallerService";
import { piezasStockBajo } from "./movimientosTaller";
import { pedirCodigoRetiro, reportarProblema } from "../../services/catalogoService";
import { formatFechaHora, formatPrecio } from "../../components/servicio/estadosServicio";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Indicador from "../../components/ui/Indicador";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Icon from "../../components/public/Icon";
import { cifrasPieza, estadoEnCamino, pct } from "./reglasInventario";
import { plural } from "../../utils/formato";
import "../../css/inventario-taller.css";
import { textoError } from "../../utils/errores";
import ConfirmarAccion from "../../components/marketplace/ConfirmarAccion";

const fecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" }) : "—");

/** Un pedido comprado que aún no entra al inventario, con lo que el taller puede hacer ahora. */
function PedidoEnCamino({ p, token, onInventario, onAviso }) {
  const [ocupado, setOcupado] = useState(null);
  const [problema, setProblema] = useState(null);
  const [error, setError] = useState(null);
  const [confirmaLlegada, setConfirmaLlegada] = useState(false);
  const estado = estadoEnCamino(p);
  const unidades = p.lineas.reduce((s, l) => s + (Number(l.cantidad) || 0), 0);

  const hacer = async (accion, fn) => {
    setOcupado(accion);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(textoError(e));
    } finally {
      setOcupado(null);
    }
  };

  return (
    <li className="iv-pedido">
      <div className="iv-pedido__cabecera">
        <div>
          <h3 className="iv-pedido__tienda">{p.tiendaNombre}</h3>
          <span className="iv-meta">{p.entrega === "RETIRO" ? "Retiro en la tienda" : "Envío al taller"} · comprado el {fecha(p.fechaCreacion)}</span>
        </div>
        <span className={`iv-estado iv-estado--${estado.tono}`}>{estado.texto}</span>
      </div>
      <ul className="iv-pedido__lineas">
        {p.lineas.map((l, i) => (
          <li key={i}>
            <span><strong className="iv-cifra">{l.cantidad} ×</strong> {l.nombre}{l.referencia ? <span className="iv-meta"> · Ref. {l.referencia}</span> : null}</span>
          </li>
        ))}
      </ul>
      {p.entrega === "RETIRO" && p.tiendaDireccion && !p.puedeConfirmar && (
        <p className="iv-meta">{[p.tiendaDireccion, p.tiendaCiudad].filter(Boolean).join(", ")}{p.tiendaHorario ? ` · ${p.tiendaHorario}` : ""}</p>
      )}
      <p className="iv-pedido__detalle">{estado.detalle} Mientras no lo confirmes, no se puede usar en una cotización.</p>

      {problema !== null && (
        <div className="iv-problema">
          <label htmlFor={`iv-problema-${p.id}`} className="iv-etiqueta">¿Qué pasó con el pedido?</label>
          <textarea id={`iv-problema-${p.id}`} className="ui-field__input" rows={3} maxLength={1000} value={problema}
            onChange={(e) => setProblema(e.target.value)} placeholder="No llegó, llegó incompleto, llegó dañado…" />
          <div className="iv-acciones">
            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={!problema.trim() || ocupado}
              onClick={() => hacer("problema", async () => {
                await reportarProblema(token, p.id, problema.trim());
                setProblema(null);
                onAviso("Reportamos el problema. DriveMaster lo revisa con la tienda y el pago a la tienda queda en pausa.");
                onInventario();
              })}>
              {ocupado === "problema" ? "Enviando…" : "Enviar reporte"}
            </button>
            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => setProblema(null)}>Cancelar</button>
          </div>
        </div>
      )}

      {error && <Alerta onCerrar={() => setError(null)}>{error}</Alerta>}

      {confirmaLlegada && (
        <ConfirmarAccion titulo={`¿Llegaron ${unidades === 1 ? "1 unidad" : `${unidades} unidades`}?`} textoConfirmar="Sí, llegaron"
          mensaje={`El stock de tu inventario se actualiza y ${p.tiendaNombre} queda habilitada para cobrar. Si llegó algo incompleto o dañado, mejor reporta un problema.`}
          onCancelar={() => setConfirmaLlegada(false)}
          onConfirmar={() => hacer("llego", async () => {
            setConfirmaLlegada(false);
            onInventario(await confirmarLlegada(token, p.id));
            onAviso(`Las piezas de ${p.tiendaNombre} ya están en tu inventario.`);
          })} />
      )}

      <div className="iv-acciones">
        {p.puedeConfirmar && (
          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" disabled={!!ocupado} aria-busy={ocupado === "llego"}
            onClick={() => setConfirmaLlegada(true)}>
            {ocupado !== "llego" && <Icon name="check" size={16} />}<span>{ocupado === "llego" ? "Confirmando…" : "Confirmar que llegó"}</span>
          </button>
        )}
        {p.puedeReportar && problema === null && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => setProblema("")}>Reportar un problema</button>
        )}
        {p.puedePedirCodigo && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" disabled={!!ocupado}
            onClick={() => hacer("codigo", async () => {
              await pedirCodigoRetiro(token, p.id);
              onAviso("Te enviamos un código de retiro nuevo a tu correo.");
            })}>
            <span>{ocupado === "codigo" ? "Enviando…" : "Pedir un código de retiro nuevo"}</span>
          </button>
        )}
      </div>
    </li>
  );
}

/**
 * El precio se escribe antes de IVA y nunca puede pasar el tope: el precio público de la tienda llevado al IVA del taller,
 * para que el cliente no pague más que en la tienda. El backend también lo valida. Las cifras se ven mientras se escribe
 * y se guardan al confirmar. Vender en pérdida se avisa, pero no se bloquea: es decisión del taller.
 */
function EditorPrecio({ pieza, comisionPct, token, onGuardada }) {
  const [precio, setPrecio] = useState(pieza.precioVenta ? String(Math.round(pieza.precioVenta)) : "");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const numero = Number(precio);
  const tope = pieza.tope;
  const supera = tope != null && numero > tope;
  const cambio = numero > 0 && numero !== Math.round(pieza.precioVenta || 0);
  const vista = cifrasPieza(numero, pieza.costoUnitario, comisionPct);

  const guardar = async () => {
    setGuardando(true);
    setError(null);
    try {
      onGuardada(await cambiarPrecioPieza(token, pieza.id, numero));
    } catch (e) {
      setError(textoError(e));
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="iv-precio">
      <label className="iv-campo">
        <span className="iv-etiqueta">Tu precio al cliente, antes de IVA</span>
        <span className={`iv-moneda${supera ? " iv-moneda--error" : ""}`}>
          <span aria-hidden="true">$</span>
          <input inputMode="numeric" value={precio} aria-label={`Precio de venta de ${pieza.nombre}, antes de IVA`}
            aria-invalid={supera || undefined} aria-describedby={`iv-tope-${pieza.id}`}
            onChange={(e) => setPrecio(e.target.value.replace(/\D/g, "").slice(0, 9))} />
        </span>
      </label>
      {tope != null && (
        <p id={`iv-tope-${pieza.id}`} className={supera ? "iv-error" : "iv-meta"}>
          {supera ? "Supera el precio público. " : ""}Máximo {formatPrecio(tope)}: así tu cliente paga {formatPrecio(pieza.precioPublicoFinal)} con IVA, lo mismo que en {pieza.tiendaReferencia}.
        </p>
      )}
      {vista && !supera && (
        <dl className="iv-cifras" aria-live="polite">
          <div><dt>Margen</dt><dd className="iv-cifra">{pct(vista.margenPct)}</dd></div>
          <div className={vista.enPerdida ? "iv-cifras__perdida" : ""}>
            <dt>Ganancia después de comisión</dt>
            <dd className="iv-cifra">{formatPrecio(vista.ganancia)}</dd>
          </div>
        </dl>
      )}
      {vista?.enPerdida && !supera && (
        <p className="iv-aviso" role="status"><Icon name="alert" size={14} />Con este precio pierdes {formatPrecio(-vista.ganancia)} por unidad. Puedes guardarlo igual.</p>
      )}
      {error && <p className="iv-error" role="alert">{error}</p>}
      <div className="iv-acciones">
        {cambio && (
          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" onClick={guardar} disabled={guardando || supera} aria-busy={guardando}>
            <span>{guardando ? "Guardando…" : "Guardar precio"}</span>
          </button>
        )}
        {tope != null && numero !== tope && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => setPrecio(String(Math.round(tope)))}>
            Usar el precio público ({formatPrecio(tope)})
          </button>
        )}
      </div>
    </div>
  );
}


/**
 * Mínimo para la alerta de stock bajo: cuando el stock llega a ese número, avisa en la campanita (una vez por cruce).
 * Vacío = sin alerta. Devuelve el inventario completo: el aviso de arriba también cambia.
 */
function EditorMinimo({ pieza, token, onInventario }) {
  const [minimo, setMinimo] = useState(pieza.stockMinimo != null ? String(pieza.stockMinimo) : "");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const actual = pieza.stockMinimo != null ? String(pieza.stockMinimo) : "";
  const cambio = minimo !== actual;

  const guardar = async (valor) => {
    setGuardando(true);
    setError(null);
    try {
      onInventario(await cambiarMinimoPieza(token, pieza.id, valor === "" ? null : Number(valor)));
      if (valor === "") setMinimo("");
    } catch (e) {
      setError(textoError(e));
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="iv-minimo">
      <label className="iv-campo">
        <span className="iv-etiqueta">Avísame cuando queden</span>
        <span className="iv-minimo__fila">
          <span className="iv-moneda iv-moneda--corta">
            <input inputMode="numeric" value={minimo} placeholder="—" aria-label={`Stock mínimo de ${pieza.nombre}`}
              aria-describedby={`iv-minimo-${pieza.id}`} onChange={(e) => setMinimo(e.target.value.replace(/\D/g, "").slice(0, 4))} />
          </span>
          <span className="iv-meta">unidades o menos</span>
        </span>
      </label>
      <p id={`iv-minimo-${pieza.id}`} className="iv-meta">
        {pieza.stockMinimo != null ? "Te avisamos en la campanita una vez cada vez que llegue a ese número." : "Sin alerta. Escribe un número para que te avisemos antes de quedarte sin la pieza."}
      </p>
      {error && <p className="iv-error" role="alert">{error}</p>}
      <div className="iv-acciones">
        {cambio && minimo !== "" && (
          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" onClick={() => guardar(minimo)} disabled={guardando} aria-busy={guardando}>
            <span>{guardando ? "Guardando…" : "Guardar mínimo"}</span>
          </button>
        )}
        {pieza.stockMinimo != null && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => guardar("")} disabled={guardando}>
            Quitar la alerta
          </button>
        )}
      </div>
    </div>
  );
}

function Compatibilidad({ pieza }) {
  if (pieza.compatibilidadUniversal) return <span className="iv-compat">Sirve a cualquier vehículo</span>;
  const modelos = pieza.modelosCompatibles || [];
  if (modelos.length === 0) return <span className="iv-compat iv-compat--confirmar">Sin compatibilidades: se confirma al cotizar</span>;
  const texto = modelos.slice(0, 3).map((m) => [m.marca, m.modelo, m.anoDesde && `${m.anoDesde}${m.anoHasta ? `–${m.anoHasta}` : "+"}`].filter(Boolean).join(" ")).join(" · ");
  return <span className="iv-compat" title={texto}>{texto}{modelos.length > 3 ? ` y ${modelos.length - 3} más` : ""}</span>;
}

function Pieza({ pieza, datos, token, onGuardada, onInventario, irA }) {
  const [abierta, setAbierta] = useState(false);
  // Las cifras de la fila son las del backend: sobre el precio que se cobraría hoy (el del taller o el tope)
  const agotada = pieza.stock === 0;
  return (
    <li className={`iv-pieza${agotada ? " iv-pieza--agotada" : ""}`}>
      <div className="iv-pieza__principal">
        <div className="iv-pieza__nombre">
          <h3>{pieza.nombre}</h3>
          <span className="iv-meta">{[pieza.marca, pieza.referencia && `Ref. ${pieza.referencia}`].filter(Boolean).join(" · ")}</span>
          <Compatibilidad pieza={pieza} />
        </div>
        <dl className="iv-pieza__numeros">
          <div className={pieza.stockBajo ? "iv-cifras__bajo" : ""}>
            <dt>En stock</dt>
            <dd className="iv-cifra iv-cifra--grande">{agotada ? "Agotada" : pieza.stock}{pieza.stockBajo && !agotada && <span className="iv-bajo">Stock bajo</span>}</dd>
          </div>
          <div><dt>Precio público</dt><dd className="iv-cifra">{pieza.precioPublico != null ? formatPrecio(pieza.precioPublico) : "—"}</dd></div>
          <div><dt>Costo</dt><dd className="iv-cifra">{pieza.costoUnitario != null ? formatPrecio(pieza.costoUnitario) : "—"}</dd></div>
          <div className={pieza.topado ? "iv-cifras__topado" : ""}><dt>Tu precio</dt><dd className="iv-cifra">{pieza.precioCobrado ? formatPrecio(pieza.precioCobrado) : "Sin precio"}</dd></div>
          <div><dt>Margen</dt><dd className="iv-cifra">{pct(pieza.margenPct)}</dd></div>
          <div className={pieza.enPerdida ? "iv-cifras__perdida" : ""}>
            <dt>Ganancia</dt><dd className="iv-cifra">{pieza.gananciaDespuesComision != null ? formatPrecio(pieza.gananciaDespuesComision) : "—"}</dd>
          </div>
        </dl>
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico iv-pieza__abrir" aria-expanded={abierta}
          aria-controls={`iv-detalle-${pieza.id}`} onClick={() => setAbierta((a) => !a)}>
          <span>{abierta ? "Cerrar" : "Precio y lotes"}</span>
          <Icon name="chevron" size={16} className={abierta ? "iv-girado" : ""} />
        </button>
      </div>
      {pieza.stockBajo && (
        <p className="iv-aviso"><Icon name="alert" size={14} />{agotada ? "Se agotó" : `Quedan ${pieza.stock}`} y tu mínimo es {pieza.stockMinimo}. Pide más en el catálogo.</p>
      )}
      {pieza.topado && (
        <p className="iv-aviso"><Icon name="alert" size={14} />El precio público bajó: al cotizar se cobra {formatPrecio(pieza.precioCobrado)}, no tus {formatPrecio(pieza.precioVenta)}.</p>
      )}
      {pieza.perdidaConTope ? (
        <p className="iv-aviso"><Icon name="alert" size={14} />Ni con el precio público máximo cubres costo y comisión: esta pieza se vende en pérdida.</p>
      ) : pieza.enPerdida && !abierta && (
        <p className="iv-aviso"><Icon name="alert" size={14} />Se vende en pérdida: revisa el precio.</p>
      )}
      {abierta && (
        <div className="iv-pieza__detalle" id={`iv-detalle-${pieza.id}`}>
          <div className="iv-pieza__ajustes">
            <EditorPrecio pieza={pieza} comisionPct={datos.comisionPct}
              token={token} onGuardada={onGuardada} />
            <EditorMinimo pieza={pieza} token={token} onInventario={onInventario} />
            {irA && (
              <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico iv-pieza__mover"
                onClick={() => irA("taller-movimientos", { abrir: pieza.id })}>
                <Icon name="plus" size={14} /><span>Registrar un movimiento de esta pieza</span>
              </button>
            )}
          </div>
          <div className="iv-lotes">
            <h4 className="iv-etiqueta">De dónde vino</h4>
            <table className="iv-tabla">
              <thead><tr><th scope="col">Compra</th><th scope="col">Tienda</th><th scope="col" className="iv-der">Quedan</th><th scope="col" className="iv-der">Pagaste</th><th scope="col" className="iv-der">Costo</th></tr></thead>
              <tbody>
                {pieza.lotes.map((l) => (
                  <tr key={l.id} className={l.disponible === 0 ? "iv-tabla__vacio" : ""}>
                    <td>{fecha(l.fechaEntrada)}<span className="iv-meta"> · {l.cantidad} u.</span></td>
                    <td>{l.id?.startsWith("CARGA-") ? <>Carga inicial<span className="iv-meta"> · {l.tiendaNombre || "sin tienda"}</span></> : l.id?.startsWith("MANUAL-") ? <>Ajuste<span className="iv-meta"> · al costo del último lote</span></> : l.tiendaNombre}</td>
                    <td className="iv-der iv-cifra">{l.disponible}</td>
                    <td className="iv-der iv-cifra">{l.precioPagado != null ? formatPrecio(l.precioPagado) : "—"}{l.descuentoPct > 0 ? <span className="iv-meta"> · público {formatPrecio(l.precioPublico)}, −{String(l.descuentoPct).replace(".", ",")} %</span> : null}</td>
                    <td className="iv-der iv-cifra">{formatPrecio(l.costoUnitario)}{l.ivaEnCosto ? <span className="iv-meta"> con IVA</span> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="iv-meta">Se usa primero lo que entró antes. El costo queda como se compró aunque la tienda cambie su precio o su descuento. Si una venta se cancela, cada unidad vuelve a su compra.</p>
          </div>
        </div>
      )}
    </li>
  );
}

/**
 * Mi inventario del taller: lo que compró en el catálogo, lo que aún viene en camino y a cuánto lo vende. Costo y
 * margen son solo del taller: el cliente ve el precio y la etiqueta "Disponible en el taller".
 */
export default function TallerInventario({ onComprar, irA }) {
  const token = getToken();
  const [estado, setEstado] = useState({ datos: null, error: null, cargando: true });
  const [intento, setIntento] = useState(0);
  const [aviso, setAviso] = useState(null);

  useEffect(() => {
    let vivo = true;
    miInventario(token)
      .then((datos) => { if (vivo) setEstado({ datos, error: null, cargando: false }); })
      .catch((e) => { if (vivo) setEstado((s) => ({ ...s, error: textoError(e), cargando: false })); });
    return () => { vivo = false; };
  }, [token, intento]);

  const recargar = (datos) => {
    if (datos) setEstado({ datos, error: null, cargando: false });
    else setIntento((n) => n + 1);
  };
  const reemplazarPieza = (pieza) => setEstado((s) => ({
    ...s, datos: { ...s.datos, piezas: s.datos.piezas.map((p) => (p.id === pieza.id ? pieza : p)) },
  }));

  const d = estado.datos;
  const unidades = d ? d.piezas.reduce((n, p) => n + p.stock, 0) : 0;
  const enPerdida = d ? d.piezas.filter((p) => p.enPerdida && p.stock > 0).length : 0;
  const bajas = d ? piezasStockBajo(d.piezas) : [];

  return (
    <div className="iv-root">
      <EncabezadoPagina titulo="Mi inventario"
        descripcion="Las piezas que compraste para tu taller. Úsalas en tus cotizaciones antes de pedir a una tienda."
        acciones={(
          <>
            {irA && (
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => irA("taller-movimientos")}>
                <Icon name="refresh" size={16} /><span>Movimientos</span>
              </button>
            )}
            <button type="button" className="ui-btn ui-btn--primario" onClick={onComprar}>
              <Icon name="plus" size={16} /><span>Comprar para mi inventario</span>
            </button>
          </>
        )} />

      {aviso && <Alerta tono="ok" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}
      {estado.error && <Alerta onReintentar={() => setIntento((n) => n + 1)}>{estado.error}</Alerta>}
      {estado.cargando && !d && <Esqueleto variante="lista" cantidad={3} texto="Cargando tu inventario…" />}

      {d && (
        <>
          <section aria-labelledby="iv-resumen">
            <h2 id="iv-resumen" className="ui-sr">Resumen del inventario</h2>
            <div className="ui-indicadores">
              <Indicador icono="box" titulo="Unidades en stock" valor={unidades}
                pie={plural(d.piezas.filter((p) => p.stock > 0).length, "pieza con stock", "piezas con stock")} />
              <Indicador titulo="Valor a costo" valor={formatPrecio(d.valorACosto)} pie="Lo que te costó lo que tienes" />
              <Indicador titulo="Valor a precio de venta" valor={formatPrecio(d.valorAVenta)} pie="Antes de IVA" tono={d.valorAVenta > d.valorACosto ? "ok" : undefined} />
              <Indicador icono="clock" titulo="En camino" valor={d.enCamino.length} tono={d.enCamino.some((p) => p.puedeConfirmar) ? "accion" : undefined}
                pie={d.enCamino.some((p) => p.puedeConfirmar) ? "Hay pedidos por confirmar" : "Pedidos que aún no llegan"} />
            </div>
          </section>

          {d.enCamino.length > 0 && (
            <section className="ui-card iv-seccion" aria-labelledby="iv-camino">
              <div className="ui-card__header">
                <h2 id="iv-camino" className="ui-card__titulo">En camino</h2>
                <span className="ui-card__sub">Aún no son stock: entran cuando confirmas que llegaron.</span>
              </div>
              <ul className="iv-pedidos">
                {d.enCamino.map((p) => (
                  <PedidoEnCamino key={p.id} p={p} token={token} onInventario={recargar} onAviso={setAviso} />
                ))}
              </ul>
            </section>
          )}

          {d.saldos?.length > 0 && (
            <section className="ui-card iv-seccion" aria-labelledby="iv-saldos">
              <div className="ui-card__header">
                <h2 id="iv-saldos" className="ui-card__titulo">Lo que debes a DriveMaster</h2>
                <span className="ui-card__sub iv-cifra">{formatPrecio(d.saldos.reduce((s, x) => s + x.monto, 0))}</span>
              </div>
              <ul className="iv-saldos">
                {d.saldos.map((s) => (
                  <li key={s.id}>
                    <span>
                      Repuestos que te quedaron de una orden cancelada · {fecha(s.fecha)}
                      <span className="iv-meta"> · ya están en tu inventario</span>
                    </span>
                    <strong className="iv-cifra">{formatPrecio(s.monto)}</strong>
                  </li>
                ))}
              </ul>
              <p className="iv-meta iv-saldos__nota">DriveMaster te contactará para cobrarlo. Por ahora no se descuenta de tus pagos.</p>
            </section>
          )}

          <section className="ui-card iv-seccion" aria-labelledby="iv-piezas">
            <div className="ui-card__header">
              <h2 id="iv-piezas" className="ui-card__titulo">Piezas</h2>
              <span className="ui-card__sub">
                Precios antes de IVA. La ganancia resta la comisión del {d.comisionPct} %{d.comisionSeDescuenta ? "." : ", que todavía no se descuenta."}
              </span>
            </div>
            {bajas.length > 0 && (
              <Alerta tono="aviso">
                {bajas.length === 1 ? `${bajas[0].nombre} llegó a su mínimo.` : `${bajas.length} piezas llegaron a su mínimo.`} Pide más antes de quedarte sin stock.
              </Alerta>
            )}
            {enPerdida > 0 && (
              <Alerta tono="aviso">
                {enPerdida === 1 ? "Una pieza se vende" : `${enPerdida} piezas se venden`} en pérdida después de la comisión. Revisa su precio.
              </Alerta>
            )}
            {d.piezas.length === 0 ? (
              <EstadoVacio icono="box" titulo="Tu inventario está vacío"
                texto="Compra en el catálogo las piezas que más usas. Cuando confirmes que llegaron, aparecen aquí y en el buscador de tus cotizaciones.">
                <button type="button" className="ui-btn ui-btn--primario" onClick={onComprar}>Comprar para mi inventario</button>
              </EstadoVacio>
            ) : (
              <ul className="iv-piezas">
                {d.piezas.map((p) => <Pieza key={p.id} pieza={p} datos={d} token={token} onGuardada={reemplazarPieza} onInventario={recargar} irA={irA} />)}
              </ul>
            )}
          </section>
          <p className="iv-meta iv-pie">Actualizado {formatFechaHora(new Date())}. Tus clientes ven el precio y la etiqueta “Disponible en el taller”, nunca el costo ni el margen.</p>
        </>
      )}
    </div>
  );
}
