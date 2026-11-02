import { useEffect, useState } from "react";
import { getToken } from "../../services/authService";
import { cambiarPrecioPieza, confirmarLlegada, miInventario } from "../../services/inventarioTallerService";
import { pedirCodigoRetiro, reportarProblema } from "../../services/catalogoService";
import { formatFechaHora, formatPrecio } from "../../components/servicio/estadosServicio";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import Indicador from "../../components/ui/Indicador";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Icon from "../../components/public/Icon";
import { cifrasPieza, estadoEnCamino, pct, precioConRecargo } from "./reglasInventario";
import "../../css/inventario-taller.css";

const fecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" }) : "—");

/** Un pedido comprado que aún no entra al inventario, con lo que el taller puede hacer ahora. */
function PedidoEnCamino({ p, token, onInventario, onAviso }) {
  const [ocupado, setOcupado] = useState(null);
  const [problema, setProblema] = useState(null);
  const [error, setError] = useState(null);
  const estado = estadoEnCamino(p);

  const hacer = async (accion, fn) => {
    setOcupado(accion);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e.message);
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

      <div className="iv-acciones">
        {p.puedeConfirmar && (
          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" disabled={!!ocupado} aria-busy={ocupado === "llego"}
            onClick={() => hacer("llego", async () => {
              onInventario(await confirmarLlegada(token, p.id));
              onAviso(`Las piezas de ${p.tiendaNombre} ya están en tu inventario.`);
            })}>
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
 * El precio se escribe directo o como recargo sobre el costo; las dos casillas van juntas. Las cifras se ven mientras se
 * escribe y se guardan al confirmar. Vender en pérdida se avisa, pero no se bloquea: es decisión del taller.
 */
function EditorPrecio({ pieza, comisionPct, recargoSugerido, token, onGuardada }) {
  const [precio, setPrecio] = useState(pieza.precioVenta ? String(Math.round(pieza.precioVenta)) : "");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const numero = Number(precio);
  const costo = pieza.costoUnitario;
  const cambio = numero > 0 && numero !== Math.round(pieza.precioVenta || 0);
  const vista = cifrasPieza(numero, costo, comisionPct);
  const recargo = vista?.recargoPct ?? "";

  const guardar = async () => {
    setGuardando(true);
    setError(null);
    try {
      onGuardada(await cambiarPrecioPieza(token, pieza.id, numero));
    } catch (e) {
      setError(e.message);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="iv-precio">
      <div className="iv-precio__campos">
        <label className="iv-campo">
          <span className="iv-etiqueta">Precio de venta</span>
          <span className="iv-moneda">
            <span aria-hidden="true">$</span>
            <input inputMode="numeric" value={precio} aria-label={`Precio de venta de ${pieza.nombre}, antes de IVA`}
              onChange={(e) => setPrecio(e.target.value.replace(/\D/g, "").slice(0, 9))} />
          </span>
        </label>
        {costo > 0 && (
          <label className="iv-campo iv-campo--recargo">
            <span className="iv-etiqueta">Recargo</span>
            <span className="iv-moneda">
              <input inputMode="decimal" value={recargo === "" ? "" : String(recargo).replace(".", ",")}
                aria-label={`Recargo sobre el costo de ${pieza.nombre}`}
                onChange={(e) => {
                  const v = Number(e.target.value.replace(",", ".").replace(/[^\d.-]/g, ""));
                  if (Number.isFinite(v)) setPrecio(String(Math.max(0, precioConRecargo(costo, v))));
                }} />
              <span aria-hidden="true">%</span>
            </span>
          </label>
        )}
      </div>
      {vista && (
        <dl className="iv-cifras" aria-live="polite">
          <div><dt>Margen</dt><dd className="iv-cifra">{pct(vista.margenPct)}</dd></div>
          <div className={vista.enPerdida ? "iv-cifras__perdida" : ""}>
            <dt>Ganancia después de comisión</dt>
            <dd className="iv-cifra">{formatPrecio(vista.ganancia)}</dd>
          </div>
        </dl>
      )}
      {vista?.enPerdida && (
        <p className="iv-aviso" role="status"><Icon name="alert" size={14} />Con este precio pierdes {formatPrecio(-vista.ganancia)} por unidad. Puedes guardarlo igual.</p>
      )}
      {error && <p className="iv-error" role="alert">{error}</p>}
      <div className="iv-acciones">
        {cambio && (
          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" onClick={guardar} disabled={guardando} aria-busy={guardando}>
            <span>{guardando ? "Guardando…" : "Guardar precio"}</span>
          </button>
        )}
        {costo > 0 && numero !== precioConRecargo(costo, recargoSugerido) && (
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => setPrecio(String(precioConRecargo(costo, recargoSugerido)))}>
            Usar el sugerido (+{recargoSugerido} %)
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

function Pieza({ pieza, datos, token, onGuardada }) {
  const [abierta, setAbierta] = useState(false);
  const cifras = cifrasPieza(pieza.precioVenta, pieza.costoUnitario, datos.comisionPct);
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
          <div><dt>En stock</dt><dd className="iv-cifra iv-cifra--grande">{agotada ? "Agotada" : pieza.stock}</dd></div>
          <div><dt>Costo</dt><dd className="iv-cifra">{pieza.costoUnitario != null ? formatPrecio(pieza.costoUnitario) : "—"}</dd></div>
          <div><dt>Precio</dt><dd className="iv-cifra">{pieza.precioVenta ? formatPrecio(pieza.precioVenta) : "Sin precio"}</dd></div>
          <div><dt>Recargo</dt><dd className="iv-cifra">{pct(cifras?.recargoPct)}</dd></div>
          <div><dt>Margen</dt><dd className="iv-cifra">{pct(cifras?.margenPct)}</dd></div>
          <div className={cifras?.enPerdida ? "iv-cifras__perdida" : ""}>
            <dt>Ganancia</dt><dd className="iv-cifra">{cifras ? formatPrecio(cifras.ganancia) : "—"}</dd>
          </div>
        </dl>
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico iv-pieza__abrir" aria-expanded={abierta}
          aria-controls={`iv-detalle-${pieza.id}`} onClick={() => setAbierta((a) => !a)}>
          <span>{abierta ? "Cerrar" : "Precio y lotes"}</span>
          <Icon name="chevron" size={16} className={abierta ? "iv-girado" : ""} />
        </button>
      </div>
      {cifras?.enPerdida && !abierta && (
        <p className="iv-aviso"><Icon name="alert" size={14} />Se vende en pérdida: revisa el precio.</p>
      )}
      {abierta && (
        <div className="iv-pieza__detalle" id={`iv-detalle-${pieza.id}`}>
          <EditorPrecio pieza={pieza} comisionPct={datos.comisionPct} recargoSugerido={datos.recargoSugeridoPct}
            token={token} onGuardada={onGuardada} />
          <div className="iv-lotes">
            <h4 className="iv-etiqueta">De dónde vino</h4>
            <table className="iv-tabla">
              <thead><tr><th scope="col">Compra</th><th scope="col">Tienda</th><th scope="col" className="iv-der">Quedan</th><th scope="col" className="iv-der">Costo</th></tr></thead>
              <tbody>
                {pieza.lotes.map((l) => (
                  <tr key={l.id} className={l.disponible === 0 ? "iv-tabla__vacio" : ""}>
                    <td>{fecha(l.fechaEntrada)}<span className="iv-meta"> · {l.cantidad} u.</span></td>
                    <td>{l.tiendaNombre}</td>
                    <td className="iv-der iv-cifra">{l.disponible}</td>
                    <td className="iv-der iv-cifra">{formatPrecio(l.costoUnitario)}{l.ivaEnCosto ? <span className="iv-meta"> con IVA</span> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="iv-meta">Se usa primero lo que entró antes. Si una venta se cancela, cada unidad vuelve a su compra.</p>
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
export default function TallerInventario({ onComprar }) {
  const token = getToken();
  const [estado, setEstado] = useState({ datos: null, error: null, cargando: true });
  const [intento, setIntento] = useState(0);
  const [aviso, setAviso] = useState(null);

  useEffect(() => {
    let vivo = true;
    miInventario(token)
      .then((datos) => { if (vivo) setEstado({ datos, error: null, cargando: false }); })
      .catch((e) => { if (vivo) setEstado((s) => ({ ...s, error: e.message, cargando: false })); });
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

  return (
    <div className="iv-root">
      <EncabezadoPagina titulo="Mi inventario"
        descripcion="Las piezas que compraste para tu taller. Úsalas en tus cotizaciones antes de pedir a una tienda."
        acciones={(
          <button type="button" className="ui-btn ui-btn--primario" onClick={onComprar}>
            <Icon name="plus" size={16} /><span>Comprar para mi inventario</span>
          </button>
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
                pie={`${d.piezas.filter((p) => p.stock > 0).length} ${d.piezas.length === 1 ? "pieza" : "piezas"} con stock`} />
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

          <section className="ui-card iv-seccion" aria-labelledby="iv-piezas">
            <div className="ui-card__header">
              <h2 id="iv-piezas" className="ui-card__titulo">Piezas</h2>
              <span className="ui-card__sub">
                Precios antes de IVA. La ganancia resta la comisión del {d.comisionPct} %{d.comisionSeDescuenta ? "." : ", que todavía no se descuenta."}
              </span>
            </div>
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
                {d.piezas.map((p) => <Pieza key={p.id} pieza={p} datos={d} token={token} onGuardada={reemplazarPieza} />)}
              </ul>
            )}
          </section>
          <p className="iv-meta iv-pie">Actualizado {formatFechaHora(new Date())}. Tus clientes ven el precio y la etiqueta “Disponible en el taller”, nunca el costo ni el margen.</p>
        </>
      )}
    </div>
  );
}
