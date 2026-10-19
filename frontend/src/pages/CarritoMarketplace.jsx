import { useEffect, useRef, useState } from "react";
import { getUser } from "../services/authService";
import { detalleProducto } from "../services/catalogoService";
import { formatPrecio } from "../components/servicio/estadosServicio";
import { AvisoCompatibilidad } from "../components/marketplace/EtiquetasProducto";
import { textoDisponibilidad } from "../components/marketplace/disponibilidad";
import ImagenProducto from "../components/marketplace/ImagenProducto";
import Icon from "../components/public/Icon";
import {
  IconoCamion, IconoDevolucion, IconoEscudo, IconoPin, IconoTienda,
} from "../components/marketplace/IconosMarket";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import EstadoVacio from "../components/ui/EstadoVacio";
import Alerta from "../components/ui/Alerta";
import BarraAccionMovil from "../components/ui/BarraAccionMovil";
import "../css/marketplace.css";

/** Agrupa por tienda conservando el orden en que se agregaron. */
function porTienda(items) {
  const grupos = new Map();
  items.forEach((x) => {
    const id = x.producto.tienda.id;
    if (!grupos.has(id)) grupos.set(id, { tienda: x.producto.tienda, items: [] });
    grupos.get(id).items.push(x);
  });
  return [...grupos.values()];
}

const unidades = (items) => items.reduce((s, x) => s + x.cantidad, 0);
const etiquetaUnidades = (n) => `${n} ${n === 1 ? "unidad" : "unidades"}`;

/** Límite de la cantidad: el stock visible si es bajo (menos de 20), si no 99. */
const tope = (p) => (p.tipo === "STOCK" && p.stock != null && p.stock < 20 ? Math.max(1, p.stock) : 99);

/**
 * "Otras tiendas con este repuesto" de cada producto del carrito, desde la comparación del catálogo
 * (detalleProducto → otrasTiendas). Se consulta una vez por producto; si falla, esa fila simplemente
 * no muestra la comparación (el carrito no depende de ella).
 */
function useOtrasTiendas(productos) {
  const [otras, setOtras] = useState({});
  const pedidos = useRef(new Set());
  const clave = productos.map((p) => p.id).join("|");

  useEffect(() => {
    let vivo = true;
    productos.forEach((p) => {
      if (pedidos.current.has(p.id)) return;
      pedidos.current.add(p.id);
      detalleProducto(p.id)
        .then((d) => { if (vivo) setOtras((m) => ({ ...m, [p.id]: d.otrasTiendas || [] })); })
        .catch(() => { if (vivo) setOtras((m) => ({ ...m, [p.id]: [] })); });
    });
    return () => { vivo = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave]);

  return otras;
}

function Etiqueta({ icono, tono, children }) {
  return <span className={`mk-chip${tono ? ` mk-chip--${tono}` : ""}`}>{icono}{children}</span>;
}

/** Cabecera de la tarjeta de una tienda: quién es, dónde está y cómo se recibe lo que vende. */
function CabeceraTienda({ tienda }) {
  return (
    <header className="mk-tienda-card__cabecera">
      <div className="mk-tienda-card__quien">
        <span className="mk-tienda-card__avatar" aria-hidden="true"><IconoTienda size={20} /></span>
        <div>
          <h3>{tienda.nombre}</h3>
          <span className="mk-tienda-card__ciudad"><IconoPin size={13} /> {tienda.ciudad}</span>
        </div>
      </div>
      <div className="mk-tienda-card__formas" aria-label="Formas de recibir">
        <Etiqueta icono={<IconoTienda size={14} />} tono="ok">Retiro sin costo</Etiqueta>
        {tienda.ofreceEnvio
          ? <Etiqueta icono={<IconoCamion size={14} />} tono="ok">Envío {formatPrecio(tienda.costoEnvio)} · solo en {tienda.ciudad}</Etiqueta>
          : <Etiqueta icono={<IconoCamion size={14} />} tono="apagado">Sin envíos</Etiqueta>}
      </div>
    </header>
  );
}

function OtrasTiendas({ producto, otras, onVer }) {
  if (!otras || otras.length === 0) return null;
  const ordenadas = [...otras].sort((a, b) => a.precio - b.precio).slice(0, 3);
  const masBarata = ordenadas.find((o) => o.precio < producto.precio);
  return (
    <details className="mk-otras" open={!!masBarata}>
      <summary>
        Otras tiendas con este repuesto ({otras.length})
        {masBarata && <span className="mk-otras__ahorro">Hasta {formatPrecio(producto.precio - ordenadas[0].precio)} menos</span>}
      </summary>
      <ul>
        {ordenadas.map((o) => {
          const dif = o.precio - producto.precio;
          return (
            <li key={o.id}>
              <div className="mk-otras__tienda">
                <strong>{o.tienda.nombre}</strong>
                <span>{o.tienda.ciudad} · {o.tienda.ofreceEnvio ? `envío ${formatPrecio(o.tienda.costoEnvio)}` : "solo retiro"}</span>
              </div>
              <div className="mk-otras__precio">
                <strong>{formatPrecio(o.precio)}</strong>
                <span className={dif < 0 ? "mk-dif mk-dif--menor" : dif > 0 ? "mk-dif mk-dif--mayor" : "mk-dif"}>
                  {dif === 0 ? "Mismo precio" : `${dif < 0 ? "−" : "+"}${formatPrecio(Math.abs(dif))}`}
                </span>
              </div>
              <button className="mk-enlace" onClick={() => onVer(o)}>Ver</button>
            </li>
          );
        })}
      </ul>
    </details>
  );
}

function Linea({ item, otras, onCambiar, onQuitar, onVer }) {
  const p = item.producto;
  const max = tope(p);
  const pocas = p.tipo === "STOCK" && p.stock != null && p.stock <= 5 && p.disponible;
  const limitada = max < 99;
  return (
    <li className="mk-linea">
      <button className="mk-linea__foto" onClick={() => onVer(p)} aria-label={`Ver ${p.nombre}`}>
        <ImagenProducto src={p.imagenUrl} />
      </button>

      <div className="mk-linea__info">
        <button className="mk-enlace mk-linea__nombre" onClick={() => onVer(p)}>{p.nombre}</button>
        <span className="mk-linea__meta">{[p.marca, p.referencia && `Ref. ${p.referencia}`].filter(Boolean).join(" · ") || "—"}</span>
        <span className={`mk-linea__estado${!p.disponible ? " mk-linea__estado--agotado" : pocas ? " mk-linea__estado--pocas" : ""}`}>
          <i aria-hidden="true" /> {textoDisponibilidad(p)}
        </span>
        <AvisoCompatibilidad producto={p} />
      </div>

      <div className="mk-linea__compra">
        <div className="mk-paso-grupo">
          <div className="mk-paso" role="group" aria-label={`Cantidad de ${p.nombre}`}>
            <button onClick={() => onCambiar(p.id, item.cantidad - 1)} disabled={item.cantidad <= 1} aria-label="Quitar una unidad">−</button>
            <output aria-live="polite">{item.cantidad}</output>
            <button onClick={() => onCambiar(p.id, item.cantidad + 1)} disabled={item.cantidad >= max} aria-label="Agregar una unidad">+</button>
          </div>
          {limitada && <span className="mk-linea__max">{item.cantidad >= max ? "Máximo disponible" : `Máx. ${max}`}</span>}
        </div>
        <div className="mk-linea__precios">
          <span className="mk-linea__unitario">{formatPrecio(p.precio)} c/u</span>
          <strong>{formatPrecio(p.precio * item.cantidad)}</strong>
        </div>
        <button className="mk-enlace mk-enlace--peligro" onClick={() => onQuitar(item)}>Quitar</button>
      </div>

      <div className="mk-linea__otras">
        <OtrasTiendas producto={p} otras={otras[p.id]} onVer={onVer} />
      </div>
    </li>
  );
}

function TarjetaTienda({ grupo, otras, onCambiar, onQuitar, onVer }) {
  const subtotal = grupo.items.reduce((s, x) => s + x.producto.precio * x.cantidad, 0);
  return (
    <article className="mk-tienda-card">
      <CabeceraTienda tienda={grupo.tienda} />
      <ul className="mk-lineas">
        {grupo.items.map((x) => (
          <Linea key={x.producto.id} item={x} otras={otras} onCambiar={onCambiar} onQuitar={onQuitar} onVer={onVer} />
        ))}
      </ul>
      <footer className="mk-tienda-card__pie">
        <span>{etiquetaUnidades(unidades(grupo.items))}</span>
        <span>Subtotal de la tienda <strong>{formatPrecio(subtotal)}</strong></span>
      </footer>
    </article>
  );
}

/**
 * Carrito del catálogo marketplace. Los productos de las tiendas se pagan juntos (un solo pago, un
 * pedido por tienda). Los precios, el IVA y el envío los calcula siempre el backend en el siguiente
 * paso: aquí solo se muestran subtotales de referencia.
 */
export default function CarritoMarketplace({ header, carrito, onVolver, onVerProducto, onContinuarCatalogo, onIrLogin }) {
  const user = getUser();
  const esCliente = user?.rol === "CLIENTE";
  const { catalogo } = carrito;
  const vacio = catalogo.length === 0;
  const tiendas = porTienda(catalogo);
  const otras = useOtrasTiendas(carrito.items.map((x) => x.producto));

  // Quitar un producto se puede deshacer unos segundos
  const [quitado, setQuitado] = useState(null);
  const temporizador = useRef(null);
  const quitar = (item) => {
    carrito.quitar(item.producto.id);
    setQuitado(item);
    clearTimeout(temporizador.current);
    temporizador.current = setTimeout(() => setQuitado(null), 7000);
  };
  const deshacer = () => {
    carrito.agregar(quitado.producto, quitado.cantidad);
    clearTimeout(temporizador.current);
    setQuitado(null);
  };
  // Vaciar no se puede deshacer: se confirma con el diálogo de la app (no el del navegador)
  const [confirmarVaciar, setConfirmarVaciar] = useState(false);
  const vaciar = () => {
    carrito.vaciar();
    setConfirmarVaciar(false);
  };
  useEffect(() => () => clearTimeout(temporizador.current), []);

  const accionPago = (texto, onPagar, clase = "ui-btn--bloque") => {
    if (!user) return <button className={`ui-btn ui-btn--primario ui-btn--grande ${clase}`} onClick={onIrLogin}>Inicia sesión para pagar</button>;
    if (!esCliente) return <p className="mk-nota">Para comprar entra con una cuenta de cliente.</p>;
    return (
      <button className={`ui-btn ui-btn--primario ui-btn--grande ${clase}`} onClick={onPagar}>
        {texto} <Icon name="arrow" size={16} />
      </button>
    );
  };

  const grupos = [
    catalogo.length > 0 && {
      clave: "tiendas", titulo: "Productos de las tiendas", detalle: `${tiendas.length} ${tiendas.length === 1 ? "tienda" : "tiendas"} · un solo pago`,
      cantidad: unidades(catalogo), subtotal: carrito.subtotalCatalogo, texto: "Continuar con el pago", onPagar: onContinuarCatalogo,
    },
  ].filter(Boolean);

  return (
    <div className="dm-page mk-pagina">
      {header}
      <main className="dm-container mk-contenido">
        <EncabezadoPagina
          titulo="Tu carrito"
          descripcion={vacio ? null : `${etiquetaUnidades(carrito.cantidad)} · revisa las cantidades antes de pagar.`}
          onVolver={onVolver}
          textoVolver="Seguir comprando"
          acciones={!vacio && (
            <button className="ui-btn ui-btn--texto mk-vaciar" onClick={() => setConfirmarVaciar(true)}>
              <Icon name="trash" size={16} /> Vaciar carrito
            </button>
          )}
        />

        {quitado && (
          <Alerta tono="info" className="mk-carrito__aviso" onCerrar={() => setQuitado(null)}>
            «{quitado.producto.nombre}» salió del carrito.{" "}
            <button className="ui-btn ui-btn--texto ui-btn--chico" onClick={deshacer}>Deshacer</button>
          </Alerta>
        )}

        {vacio ? (
          <EstadoVacio icono="box" titulo="Tu carrito está vacío"
            texto="Busca el repuesto que necesitas y compara precios entre tiendas de tu ciudad.">
            <button className="ui-btn ui-btn--primario" onClick={onVolver}>Explorar catálogo</button>
          </EstadoVacio>
        ) : (
          <div className="mk-carrito">
            <div className="mk-carrito__lista">
              {catalogo.length > 0 && (
                <section className="mk-seccion" aria-labelledby="mk-grupo-tiendas">
                  <h2 id="mk-grupo-tiendas">Productos de las tiendas</h2>
                  {tiendas.map((g) => (
                    <TarjetaTienda key={g.tienda.id} grupo={g} otras={otras} onCambiar={carrito.cambiarCantidad} onQuitar={quitar} onVer={onVerProducto} />
                  ))}
                </section>
              )}

            </div>

            <aside className="mk-resumen" aria-label="Resumen del carrito">
              <h2>Resumen</h2>
              {grupos.map((g) => (
                <div key={g.clave} className="mk-resumen__grupo">
                  <dl className="mk-totales mk-totales--carrito">
                    <div><dt>{g.titulo}</dt><dd className="ui-precio">{formatPrecio(g.subtotal)}</dd></div>
                    <div><dt>Envío</dt><dd>Se elige en el siguiente paso</dd></div>
                    <div><dt>IVA</dt><dd>Se calcula al pagar</dd></div>
                    <div className="mk-totales__final"><dt>Subtotal</dt><dd className="ui-precio">{formatPrecio(g.subtotal)}</dd></div>
                  </dl>
                  <span className="mk-resumen__detalle">{etiquetaUnidades(g.cantidad)} · {g.detalle}</span>
                  {g.onPagar && <div className="mk-resumen__accion mk-solo-escritorio">{accionPago(g.texto, g.onPagar)}</div>}
                </div>
              ))}
              <p className="mk-resumen__nota">El total final, con el envío o el retiro y el IVA, lo ves antes de pagar.</p>
              <ul className="mk-confianza">
                <li><IconoEscudo size={18} /><span>Pago seguro con Wompi</span></li>
                <li><IconoCamion size={18} /><span>Retiro en tienda o envío, según cada tienda</span></li>
                <li><IconoDevolucion size={18} /><span>Si algo se agota al pagar, te devolvemos todo</span></li>
              </ul>
            </aside>
          </div>
        )}
      </main>

      {!vacio && grupos[0] && (
        <BarraAccionMovil etiqueta="Subtotal" valor={formatPrecio(grupos[0].subtotal)} nota="Envío e IVA en el siguiente paso">
          {accionPago("Pagar", grupos[0].onPagar, "")}
        </BarraAccionMovil>
      )}

      {confirmarVaciar && (
        <ConfirmarAccion
          titulo="Vaciar el carrito"
          mensaje={`Se quitarán los ${etiquetaUnidades(carrito.cantidad)} del carrito. Esto no se puede deshacer.`}
          textoConfirmar="Sí, vaciar"
          peligro
          onConfirmar={vaciar}
          onCancelar={() => setConfirmarVaciar(false)}
        />
      )}
    </div>
  );
}
