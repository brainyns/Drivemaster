import { useEffect, useState } from "react";
import DrawerAdmin from "./DrawerAdmin";
import ChipEstado from "../ui/ChipEstado";
import Alerta from "../ui/Alerta";
import Dato from "../ui/Dato";
import Esqueleto from "../ui/Esqueleto";
import LogoNegocio from "../ui/LogoNegocio";
import { detalleCliente, detalleNegocio, detallePedido } from "../../services/plataformaService";
import { ESTADOS as ESTADOS_ORDEN } from "../servicio/estadosServicio";
import { ESTADOS_PEDIDO, chipsAlerta, estadoNegocio, refPedido } from "../../pages/admin/plataformaAdmin";
import {
  BENEFICIARIO, ESTADOS_PAGO, ESTADOS_REPARTO, notaReparto, textoEntrega, textoVehiculo, ubicacionNegocio,
} from "../../pages/admin/detalleAdmin";
import { formatoCop } from "../../utils/formato";
import "../../css/admin-base.css";
import "../../css/detalle-plataforma.css";

const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : null);
const fechaHora = (iso) => (iso
  ? new Date(iso).toLocaleString("es-CO", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })
  : null);

const CARGAR = {
  pedido: (token, o) => detallePedido(token, o.id),
  cliente: (token, o) => detalleCliente(token, o.id),
  negocio: (token, o) => detalleNegocio(token, o.negocioTipo, o.id),
};

/**
 * Detalle de un pedido, un cliente o un negocio en el panel lateral (solo SUPERADMIN, solo lectura). objetivo es
 * { tipo: "pedido" | "cliente" | "negocio", id, negocioTipo }. Desde un detalle se abre el de sus partes (la tienda o
 * el cliente de un pedido, un pedido de un cliente…) y "Volver" regresa al anterior.
 */
export default function DetallePlataforma({ token, objetivo, onCerrar }) {
  const [pila, setPila] = useState([objetivo]);
  const actual = pila[pila.length - 1];
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });
  const clave = `${actual.tipo}:${actual.negocioTipo || ""}:${actual.id}`;

  useEffect(() => {
    let vivo = true;
    CARGAR[actual.tipo](token, actual)
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setEstado({ clave, datos: null, error: e.message }); });
    return () => { vivo = false; };
  }, [token, actual, clave]);

  const abrir = (o) => setPila((p) => [...p, o]);
  const volver = pila.length > 1 ? () => setPila((p) => p.slice(0, -1)) : null;
  const listo = estado.clave === clave;
  const d = listo ? estado.datos : null;

  const titulo = actual.tipo === "pedido" ? `Pedido ${refPedido(actual.id)}`
    : d?.nombre || (actual.tipo === "cliente" ? "Cliente" : "Negocio");

  return (
    <DrawerAdmin titulo={titulo} onCerrar={onCerrar}
      encabezado={volver && (
        <button type="button" className="ui-btn ui-btn--texto ui-btn--chico dtp-volver" onClick={volver}>← Volver</button>
      )}>
      {!listo && <Esqueleto variante="formulario" cantidad={4} texto="Cargando el detalle…" />}
      {listo && estado.error && <Alerta>{estado.error}</Alerta>}
      {d && actual.tipo === "pedido" && <VistaPedido p={d} abrir={abrir} />}
      {d && actual.tipo === "cliente" && <VistaCliente c={d} abrir={abrir} />}
      {d && actual.tipo === "negocio" && <VistaNegocio n={d} abrir={abrir} />}
    </DrawerAdmin>
  );
}

function Bloque({ id, titulo, children }) {
  return (
    <section className="dtp-bloque" aria-labelledby={id}>
      <h3 id={id} className="dtp-titulo">{titulo}</h3>
      {children}
    </section>
  );
}

/** Un nombre que abre el detalle de esa parte, o solo el texto si no hay a dónde ir. */
function Enlace({ texto, onAbrir }) {
  if (!texto) return null;
  return onAbrir
    ? <button type="button" className="dtp-enlace" onClick={onAbrir}>{texto}</button>
    : <>{texto}</>;
}

// ─── Pedido ─────────────────────────────────────────────────────────────

function VistaPedido({ p, abrir }) {
  const alertas = chipsAlerta({ ...p, diasSinRetirar: p.entrega?.diasSinRetirar });
  const nota = notaReparto(p.pago, p.reparto);
  const e = p.entrega;
  return (
    <>
      <div className="dtp-chips">
        <ChipEstado catalogo={ESTADOS_PEDIDO} estado={p.estado} />
        {alertas.map((a) => <ChipEstado key={a.texto} tono={a.tono}>{a.texto}</ChipEstado>)}
      </div>

      <Bloque id="dtp-partes" titulo="Quién participa">
        <dl className="ui-datos">
          <Dato etiqueta="Tienda">
            <Enlace texto={p.tienda?.nombre} onAbrir={p.tienda?.id && (() => abrir({ tipo: "negocio", negocioTipo: "TIENDA", id: p.tienda.id }))} />
          </Dato>
          <Dato etiqueta="Cliente">
            <Enlace texto={p.cliente?.nombre} onAbrir={p.cliente?.id && (() => abrir({ tipo: "cliente", id: p.cliente.id }))} />
          </Dato>
          {p.taller && (
            <Dato etiqueta="Taller">
              <Enlace texto={p.taller.nombre} onAbrir={p.taller.id && (() => abrir({ tipo: "negocio", negocioTipo: "TALLER", id: p.taller.id }))} />
            </Dato>
          )}
          {p.vehiculo && <Dato etiqueta="Vehículo">{p.vehiculo}</Dato>}
          <Dato etiqueta="Origen">{p.origen === "TALLER" ? "Cotización de taller" : "Compra del catálogo"}</Dato>
          <Dato etiqueta="Plazo de despacho">{fechaHora(p.plazoDespacho)}</Dato>
        </dl>
      </Bloque>

      <Bloque id="dtp-productos" titulo="Productos">
        <ul className="dtp-lista">
          {p.lineas.map((l, i) => (
            <li key={i} className="dtp-fila">
              <span>
                <strong>{l.producto || "Producto"}</strong>
                <span className="dtp-sub">{[l.marca, l.referencia, l.disponibilidad === "ENCARGO" ? "Encargo" : null].filter(Boolean).join(" · ")}</span>
              </span>
              <span className="dtp-num">{l.cantidad} × {formatoCop(l.precioUnitario)}<strong>{formatoCop(l.subtotal)}</strong></span>
            </li>
          ))}
        </ul>
        <dl className="dtp-totales">
          <div><dt>Subtotal</dt><dd>{formatoCop(p.subtotal)}</dd></div>
          <div><dt>IVA</dt><dd>{formatoCop(p.iva)}</dd></div>
          {e?.costoEnvio > 0 && <div><dt>Envío</dt><dd>{formatoCop(e.costoEnvio)}</dd></div>}
          <div className="dtp-totales__total"><dt>Total</dt><dd>{formatoCop(p.total)}</dd></div>
        </dl>
      </Bloque>

      <Bloque id="dtp-tiempo" titulo="Línea de tiempo">
        {p.lineaDeTiempo.length === 0 ? <p className="dtp-vacio">Sin pasos registrados.</p> : (
          <ol className="dtp-tiempo">
            {p.lineaDeTiempo.map((ev, i) => (
              <li key={i}>
                <strong>{ev.etiqueta}</strong>
                <span className="dtp-sub">{fechaHora(ev.fecha)}{ev.detalle ? ` · ${ev.detalle}` : ""}</span>
              </li>
            ))}
          </ol>
        )}
      </Bloque>

      <Bloque id="dtp-pago" titulo="Pago en Wompi">
        {!p.pago ? <p className="dtp-vacio">Sin pago registrado.</p> : (
          <dl className="ui-datos">
            <Dato etiqueta="Estado"><ChipEstado catalogo={ESTADOS_PAGO} estado={p.pago.estado} /></Dato>
            <Dato etiqueta="Monto">{formatoCop(p.pago.monto)}</Dato>
            <Dato etiqueta="Referencia">{p.pago.referencia}</Dato>
            <Dato etiqueta="Transacción Wompi">{p.pago.transaccion}</Dato>
            <Dato etiqueta="Método">{p.pago.metodo}</Dato>
            <Dato etiqueta="Fecha">{fechaHora(p.pago.fechaPago)}</Dato>
          </dl>
        )}
      </Bloque>

      <Bloque id="dtp-reparto" titulo="Reparto">
        {p.reparto.length === 0 ? <p className="dtp-vacio">Todavía no hay reparto registrado.</p> : (
          <ul className="dtp-lista">
            {p.reparto.map((r, i) => (
              <li key={i} className="dtp-fila">
                <span>
                  <strong>{BENEFICIARIO[r.beneficiarioTipo] || r.beneficiarioTipo}</strong>
                  <span className="dtp-sub">{[r.beneficiarioTipo === "PLATAFORMA" ? null : r.beneficiario,
                    r.fechaLiquidacion && `liquidado el ${fecha(r.fechaLiquidacion)}${r.modoLiquidacion === "SIMULADO" ? " (simulado)" : ""}`,
                    r.nota].filter(Boolean).join(" · ")}</span>
                </span>
                <span className="dtp-num">
                  <ChipEstado catalogo={ESTADOS_REPARTO} estado={r.estado} />
                  <strong>{formatoCop(r.monto)}</strong>
                </span>
              </li>
            ))}
          </ul>
        )}
        {nota && <p className="dtp-nota">{nota}</p>}
        {p.reembolsos.length > 0 && (
          <ul className="dtp-lista">
            {p.reembolsos.map((b, i) => (
              <li key={i} className="dtp-fila">
                <span><strong>Reembolso</strong><span className="dtp-sub">{[b.motivo, fecha(b.fecha)].filter(Boolean).join(" · ")}</span></span>
                <span className="dtp-num"><ChipEstado tono={b.estado === "REALIZADO" ? "ok" : "aviso"}>{b.estado === "REALIZADO" ? "Realizado" : "Pendiente"}</ChipEstado><strong>{formatoCop(b.monto)}</strong></span>
              </li>
            ))}
          </ul>
        )}
      </Bloque>

      <Bloque id="dtp-entrega" titulo={textoEntrega(e)}>
        <dl className="ui-datos">
          {e?.tipo === "ENVIO" && (
            <>
              <Dato etiqueta="Recibe">{e.receptor}</Dato>
              <Dato etiqueta="Teléfono">{e.telefono}</Dato>
              <Dato etiqueta="Dirección" ancho>{[e.direccion, e.ciudad].filter(Boolean).join(", ")}</Dato>
              {e.indicaciones && <Dato etiqueta="Indicaciones" ancho>{e.indicaciones}</Dato>}
            </>
          )}
          {e?.tipo === "RETIRO" && (
            <>
              <Dato etiqueta="Dónde" ancho>{[e.direccion, e.ciudad].filter(Boolean).join(", ")}</Dato>
              <Dato etiqueta="Horario">{e.horarioTienda}</Dato>
              <Dato etiqueta="Listo desde">{fechaHora(e.listoDesde)}</Dato>
              <Dato etiqueta="Retirado">{fechaHora(e.retiradoEl) || (e.listoDesde ? "Todavía no" : null)}</Dato>
              {e.recordatorios > 0 && <Dato etiqueta="Recordatorios enviados">{e.recordatorios}</Dato>}
            </>
          )}
          {e?.tipo === "TALLER" && (
            <Dato etiqueta="Taller" ancho>{[e.receptor, e.direccion, e.ciudad].filter(Boolean).join(", ")}</Dato>
          )}
        </dl>
        {p.problema && (
          <Alerta tono={p.problema.resolucion ? "info" : "aviso"}>
            Problema reportado el {fecha(p.problema.fecha)}: {p.problema.detalle}
            {p.problema.resolucion ? ` Resuelto: ${p.problema.motivo || p.problema.resolucion}.` : " Sin resolver todavía."}
          </Alerta>
        )}
      </Bloque>
    </>
  );
}

// ─── Cliente ────────────────────────────────────────────────────────────

function VistaCliente({ c, abrir }) {
  return (
    <>
      <div className="dtp-chips">
        <ChipEstado tono={c.activo ? "ok" : "apagado"}>{c.activo ? "Activo" : "Inactivo"}</ChipEstado>
        {!c.ciudad && <ChipEstado tono="aviso">Sin ciudad</ChipEstado>}
      </div>
      <Bloque id="dtp-datos" titulo="Datos">
        <dl className="ui-datos">
          <Dato etiqueta="Correo">{c.correo}</Dato>
          <Dato etiqueta="Teléfono">{c.telefono}</Dato>
          <Dato etiqueta="Ciudad">{c.ciudad}</Dato>
          <Dato etiqueta="Documento">{c.identificacion}</Dato>
          <Dato etiqueta="Dirección" ancho>{c.direccion}</Dato>
          <Dato etiqueta="Entra con">{c.proveedor === "GOOGLE" ? "Google" : "Correo y contraseña"}</Dato>
          <Dato etiqueta="Registro">{fecha(c.fechaCreacion)}</Dato>
          <Dato etiqueta="Último ingreso">{fechaHora(c.ultimoLogin)}</Dato>
        </dl>
      </Bloque>

      <Bloque id="dtp-vehiculos" titulo={`Vehículos (${c.vehiculos.length})`}>
        {c.vehiculos.length === 0 ? <p className="dtp-vacio">No ha registrado vehículos.</p> : (
          <ul className="dtp-lista">{c.vehiculos.map((v) => <li key={v.id} className="dtp-fila"><span>{textoVehiculo(v)}</span></li>)}</ul>
        )}
      </Bloque>

      <Bloque id="dtp-pedidos" titulo={`Pedidos (${c.totalPedidos}) · ${formatoCop(c.totalComprado)} comprados`}>
        <ListaPedidos pedidos={c.pedidos} total={c.totalPedidos} abrir={abrir} vacio="No ha comprado en el catálogo." />
      </Bloque>

      <Bloque id="dtp-solicitudes" titulo={`Solicitudes de diagnóstico (${c.totalSolicitudes})`}>
        <ListaSolicitudes solicitudes={c.solicitudes} total={c.totalSolicitudes} vacio="No ha pedido diagnósticos." />
      </Bloque>
    </>
  );
}

function ListaPedidos({ pedidos, total, abrir, vacio }) {
  if (pedidos.length === 0) return <p className="dtp-vacio">{vacio}</p>;
  return (
    <>
      <ul className="dtp-lista">
        {pedidos.map((x) => (
          <li key={x.id} className="dtp-fila">
            <span>
              <button type="button" className="dtp-enlace" onClick={() => abrir({ tipo: "pedido", id: x.id })}>{refPedido(x.id)}</button>
              <span className="dtp-sub">{[x.negocio, fecha(x.fecha)].filter(Boolean).join(" · ")}</span>
            </span>
            <span className="dtp-num"><ChipEstado catalogo={ESTADOS_PEDIDO} estado={x.estado} /><strong>{formatoCop(x.total)}</strong></span>
          </li>
        ))}
      </ul>
      {total > pedidos.length && <p className="dtp-nota">Se muestran los {pedidos.length} más recientes de {total}.</p>}
    </>
  );
}

function ListaSolicitudes({ solicitudes, total, vacio }) {
  if (solicitudes.length === 0) return <p className="dtp-vacio">{vacio}</p>;
  return (
    <>
      <ul className="dtp-lista">
        {solicitudes.map((s) => (
          <li key={s.id} className="dtp-fila">
            <span>
              <strong>{s.vehiculo || "Vehículo"}</strong>
              <span className="dtp-sub">{[s.taller || "Sin taller", fecha(s.fecha)].filter(Boolean).join(" · ")}</span>
            </span>
            <span className="dtp-num"><ChipEstado catalogo={ESTADOS_ORDEN} estado={s.estado} />{s.total > 0 && <strong>{formatoCop(s.total)}</strong>}</span>
          </li>
        ))}
      </ul>
      {total > solicitudes.length && <p className="dtp-nota">Se muestran las {solicitudes.length} más recientes de {total}.</p>}
    </>
  );
}

// ─── Negocio ────────────────────────────────────────────────────────────

function VistaNegocio({ n, abrir }) {
  const est = estadoNegocio({ estado: n.estadoAprobacion || "APROBADO", activo: n.activo });
  const { texto, mapa } = ubicacionNegocio(n);
  const tienda = n.tipo === "TIENDA";
  const t = n.totales;
  return (
    <>
      <div className="dtp-cabecera">
        <LogoNegocio url={n.logoUrl} nombre={n.nombre} tamano="mediano" />
        <span className="dtp-chips">
          <ChipEstado tono="apagado">{tienda ? "Tienda" : "Taller"}</ChipEstado>
          <ChipEstado tono={est.tono}>{est.label}</ChipEstado>
        </span>
      </div>

      <Bloque id="dtp-negocio" titulo="Datos">
        <dl className="ui-datos">
          <Dato etiqueta="NIT">{n.nit}</Dato>
          <Dato etiqueta="Teléfono">{n.telefono}</Dato>
          <Dato etiqueta="Cuenta">{n.cuenta && [n.cuenta.nombre, n.cuenta.correo].filter(Boolean).join(" · ")}</Dato>
          <Dato etiqueta="Registro">{fecha(n.fechaRegistro)}</Dato>
          <Dato etiqueta="Aprobación">{fecha(n.fechaAprobacion)}</Dato>
          <Dato etiqueta="Horario">{n.horario}</Dato>
          {n.motivoRechazo && <Dato etiqueta="Motivo del rechazo" ancho>{n.motivoRechazo}</Dato>}
        </dl>
      </Bloque>

      <Bloque id="dtp-ubicacion" titulo="Ubicación">
        <p className="dtp-texto">{texto || "Sin dirección registrada."}</p>
        {mapa && <a className="ui-btn ui-btn--texto ui-btn--chico" href={mapa} target="_blank" rel="noreferrer">Ver en el mapa</a>}
      </Bloque>

      <Bloque id="dtp-totales" titulo="Totales">
        <dl className="ui-datos">
          <Dato etiqueta={tienda ? "Pedidos" : "Órdenes"}>{String(t.pedidos)}</Dato>
          <Dato etiqueta="Vendido">{formatoCop(t.vendido)}</Dato>
          <Dato etiqueta="Liquidado">{formatoCop(t.liquidado)}</Dato>
          <Dato etiqueta="Por liquidar">{formatoCop(t.porLiquidar)}</Dato>
          <Dato etiqueta="En espera de entrega">{formatoCop(t.enEspera)}</Dato>
        </dl>
        <p className="dtp-nota">Liquidado, por liquidar y en espera son su parte del reparto, sin la comisión.</p>
      </Bloque>

      {tienda ? (
        <>
          <Bloque id="dtp-productos-n" titulo={`Productos (${n.productosActivos} activos de ${n.totalProductos})`}>
            {n.productos.length === 0 ? <p className="dtp-vacio">No tiene productos.</p> : (
              <ul className="dtp-lista">
                {n.productos.map((x) => (
                  <li key={x.id} className="dtp-fila">
                    <span>
                      <strong>{x.nombre}</strong>
                      <span className="dtp-sub">{[x.marca, x.tipo === "ENCARGO" ? "Bajo pedido" : `Stock ${x.stock ?? 0}`].filter(Boolean).join(" · ")}</span>
                    </span>
                    <span className="dtp-num">
                      {!x.activo && <ChipEstado tono="apagado">Inactivo</ChipEstado>}
                      <strong>{formatoCop(x.precio)}</strong>
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {n.totalProductos > n.productos.length && <p className="dtp-nota">Se muestran los {n.productos.length} más recientes.</p>}
          </Bloque>
          <Bloque id="dtp-pedidos-n" titulo="Últimos pedidos">
            <ListaPedidos pedidos={n.pedidos} total={t.pedidos} abrir={abrir} vacio="Todavía no tiene pedidos." />
          </Bloque>
        </>
      ) : (
        <>
          <Bloque id="dtp-servicios" titulo="Servicios">
            <dl className="ui-datos">
              <Dato etiqueta="Especialidades" ancho>{n.especialidades?.length ? n.especialidades.join(", ") : null}</Dato>
              <Dato etiqueta="Mano de obra">{n.precioManoObra ? formatoCop(n.precioManoObra) : null}</Dato>
              <Dato etiqueta="Tarifa por hora">{n.tarifaHora ? formatoCop(n.tarifaHora) : null}</Dato>
              <Dato etiqueta="Calificación">{n.calificaciones ? `${(n.calificacion || 0).toFixed(1)} (${n.calificaciones})` : "Sin calificaciones"}</Dato>
              <Dato etiqueta="Mecánicos" ancho>{n.mecanicos?.length
                ? n.mecanicos.map((m) => `${m.nombre}${m.especialidad ? ` (${m.especialidad})` : ""}${m.activo ? "" : " · inactivo"}`).join(", ")
                : null}</Dato>
            </dl>
          </Bloque>
          <Bloque id="dtp-ordenes" titulo="Últimas órdenes">
            <ListaSolicitudes solicitudes={n.ordenes} total={t.pedidos} vacio="Todavía no tiene órdenes." />
          </Bloque>
        </>
      )}
    </>
  );
}
