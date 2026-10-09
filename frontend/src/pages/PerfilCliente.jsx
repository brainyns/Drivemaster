import { useCallback, useEffect, useState } from "react";
import { getToken, getUser } from "../services/authService";
import { misCompras } from "../services/catalogoService";
import { misVentas } from "../services/ventaService";
import { misSolicitudesServicio } from "../services/solicitudServicioService";
import { cotizacionesDeOrden } from "../services/cotizacionService";
import { PEDIDOS_ACTIVOS, clasificarCompras, estadoVentaAnterior } from "../components/marketplace/clasificarCompras";
import { ESTADO_COMPRA, estadoPedido } from "../components/marketplace/estadosCompra";
import { ESTADOS, formatFecha, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import EditarNombre from "../components/public/EditarNombre";
import Icon from "../components/public/Icon";
import "../css/perfil-cliente.css";

const ULTIMOS = 3;

// Tono de estadosServicio/estadosCompra → modificador de .ui-badge
const BADGE = { ok: "ok", aviso: "aviso", error: "error", info: "info", proceso: "info" };

/** Estado que se muestra de una compra del catálogo: el del pedido que sigue activo, o el de la compra. */
function estadoCompra(c) {
  if (c.estado !== "PAGADA" || !c.pedidos?.length) return ESTADO_COMPRA[c.estado] || { label: c.estado, tono: "aviso" };
  return estadoPedido(c.pedidos.find((p) => PEDIDOS_ACTIVOS.includes(p.estado)) || c.pedidos[0]);
}

/** Las últimas compras: las del catálogo y las del sistema anterior, de la más reciente a la más antigua. */
function ultimasCompras(compras, ventas) {
  const { enCurso, anteriores } = clasificarCompras(compras, ventas);
  return [...enCurso, ...anteriores].sort((a, b) => b.fecha - a.fecha).slice(0, ULTIMOS).map((x) => {
    const e = x.origen === "catalogo" ? estadoCompra(x.dato) : estadoVentaAnterior(x.dato);
    return { id: x.id, titulo: `Compra #${refSolicitud(x.id)}`, anterior: x.origen === "anterior", estado: e,
      fecha: x.origen === "catalogo" ? (x.dato.fechaPago || x.dato.fechaCreacion) : x.dato.fecha, total: x.dato.total };
  });
}

function Esqueleto() {
  return (
    <ul className="pc-lista" aria-hidden="true">
      {[0, 1, 2].map((i) => (
        <li key={i} className="pc-item">
          <span className="ui-skeleton pc-esq pc-esq--titulo" />
          <span className="ui-skeleton pc-esq pc-esq--linea" />
        </li>
      ))}
    </ul>
  );
}

/** Tarjeta de resumen con sus estados: cargando, error en línea, vacío con qué hacer y la lista. */
function Resumen({ titulo, idTitulo, estado, vacio, accionVacio, onVerTodo, onReintentar, children }) {
  return (
    <section className="ui-card pc-tarjeta" aria-labelledby={idTitulo} aria-busy={estado === "cargando"}>
      <header className="ui-card__header">
        <h2 id={idTitulo} className="ui-card__titulo">{titulo}</h2>
        {estado === "listo" && <button className="ui-btn ui-btn--texto" onClick={onVerTodo}>Ver todo</button>}
      </header>
      <div className="ui-card__cuerpo">
        {estado === "cargando" && <Esqueleto />}
        {estado === "error" && (
          <p className="pc-error" role="alert">
            {vacio.error}{" "}
            <button className="ui-btn ui-btn--texto" onClick={onReintentar}>Reintentar</button>
          </p>
        )}
        {estado === "vacio" && (
          <div className="ui-empty pc-vacio">
            <p className="ui-empty__titulo">{vacio.titulo}</p>
            <p>{vacio.texto}</p>
            <button className="ui-btn ui-btn--primario" onClick={accionVacio}>{vacio.boton}</button>
          </div>
        )}
        {estado === "listo" && children}
      </div>
    </section>
  );
}

function Fila({ titulo, detalle, estado, fecha, total }) {
  return (
    <li className="pc-item">
      <div className="pc-item__principal">
        <strong className="pc-item__titulo">{titulo}</strong>
        <span className="pc-item__meta">{[detalle, formatFecha(fecha)].filter(Boolean).join(" · ")}</span>
      </div>
      <div className="pc-item__lado">
        <span className={`ui-badge ui-badge--${BADGE[estado.tono] || "info"}`}>{estado.label}</span>
        <span className="pc-item__total">{total}</span>
      </div>
    </li>
  );
}

/**
 * Mi perfil del CLIENTE: sus datos, lo último que compró y los últimos servicios, y los accesos
 * a Mis compras, Mis servicios y Mis vehículos. Usa las mismas consultas de esas pantallas.
 * El total de un servicio sale de su cotización vigente (la misma consulta de Mis servicios).
 */
export default function PerfilCliente({ header, onIr, onIrCatalogo, onSolicitarDiagnostico }) {
  const token = getToken();
  const user = getUser();
  const [editando, setEditando] = useState(false);
  const [compras, setCompras] = useState({ estado: "cargando", items: [] });
  const [servicios, setServicios] = useState({ estado: "cargando", items: [] });
  const [totales, setTotales] = useState({}); // id de la solicitud → total de su cotización vigente

  // Las consultas solo cambian el estado al responder; "Reintentar" vuelve a mostrar la carga
  const pedirCompras = useCallback(() => {
    // Las ventas del sistema anterior son un extra: si fallan, se muestran solo las del catálogo
    Promise.all([misCompras(token), misVentas(token).catch(() => [])])
      .then(([c, v]) => {
        const items = ultimasCompras(c || [], Array.isArray(v) ? v : []);
        setCompras({ estado: items.length ? "listo" : "vacio", items });
      })
      .catch(() => setCompras({ estado: "error", items: [] }));
  }, [token]);

  const pedirServicios = useCallback(() => {
    misSolicitudesServicio(token)
      .then((s) => {
        const items = (s || []).slice(0, ULTIMOS);
        setServicios({ estado: items.length ? "listo" : "vacio", items });
        // Total de cada servicio cotizado; sin cotización se muestra la tarifa del diagnóstico
        items.filter((x) => x.cotizacionId).forEach((x) => {
          // false: no se pudo saber; se muestra la tarifa del diagnóstico en su lugar
          cotizacionesDeOrden(token, x.id)
            .then((cs) => setTotales((t) => ({ ...t, [x.id]: cs?.[0]?.total ?? false })))
            .catch(() => setTotales((t) => ({ ...t, [x.id]: false })));
        });
      })
      .catch(() => setServicios({ estado: "error", items: [] }));
  }, [token]);

  useEffect(() => { pedirCompras(); pedirServicios(); }, [pedirCompras, pedirServicios]);
  const reintentarCompras = () => { setCompras({ estado: "cargando", items: [] }); pedirCompras(); };
  const reintentarServicios = () => { setServicios({ estado: "cargando", items: [] }); pedirServicios(); };

  const totalServicio = (s) => {
    if (typeof totales[s.id] === "number") return formatPrecio(totales[s.id]);
    if (s.cotizacionId && totales[s.id] === undefined) return "…";
    if (s.tarifaDiagnostico != null) return `Diagnóstico ${formatPrecio(s.tarifaDiagnostico)}`;
    return "Sin cotizar";
  };

  const accesos = [
    { destino: "mis-compras", label: "Mis compras", texto: "Pedidos en curso y anteriores", icono: "box" },
    { destino: "mis-servicios", label: "Mis servicios", texto: "Diagnósticos, cotizaciones y reparaciones", icono: "tool" },
    { destino: "mis-vehiculos", label: "Mis vehículos", texto: "Tus vehículos y su mantenimiento", icono: "car" },
  ];

  return (
    <div className="dm-page pc-pagina">
      {header}
      <main className="dm-container pc-contenido">
        <header className="pc-cabecera">
          <span className="pc-avatar" aria-hidden="true">{(user?.nombre || "?").trim().charAt(0).toUpperCase()}</span>
          <div className="pc-cabecera__datos">
            <h1 className="pc-nombre">{user?.nombre || "Mi perfil"}</h1>
            {user?.correo && <p className="pc-correo">{user.correo}</p>}
          </div>
          <button className="ui-btn ui-btn--secundario pc-cabecera__editar" onClick={() => setEditando(true)}>
            Editar nombre de usuario
          </button>
        </header>

        <div className="pc-rejilla">
          <Resumen titulo="Últimas compras" idTitulo="pc-compras" estado={compras.estado}
            vacio={{ titulo: "Aún no has hecho compras", texto: "Explora el catálogo y compra repuestos en las tiendas aliadas.",
              boton: "Explorar el catálogo", error: "No pudimos cargar tus compras." }}
            accionVacio={onIrCatalogo} onVerTodo={() => onIr("mis-compras")} onReintentar={reintentarCompras}>
            <ul className="pc-lista">
              {compras.items.map((c) => (
                <Fila key={c.id} titulo={c.titulo} detalle={c.anterior ? "Sistema anterior" : null}
                  estado={c.estado} fecha={c.fecha} total={formatPrecio(c.total)} />
              ))}
            </ul>
          </Resumen>

          <Resumen titulo="Últimos servicios" idTitulo="pc-servicios" estado={servicios.estado}
            vacio={{ titulo: "Aún no has pedido servicios", texto: "Solicita un diagnóstico y un taller cercano revisará tu vehículo.",
              boton: "Solicitar diagnóstico", error: "No pudimos cargar tus servicios." }}
            accionVacio={onSolicitarDiagnostico} onVerTodo={() => onIr("mis-servicios")} onReintentar={reintentarServicios}>
            <ul className="pc-lista">
              {servicios.items.map((s) => (
                <Fila key={s.id} titulo={s.vehiculoDescripcion || `Solicitud #${refSolicitud(s.id)}`}
                  detalle={s.tallerNombre || "Buscando taller"} estado={ESTADOS[s.estado] || { label: s.estado, tono: "info" }}
                  fecha={s.fechaCreacion} total={totalServicio(s)} />
              ))}
            </ul>
          </Resumen>
        </div>

        <nav className="pc-accesos" aria-label="Accesos de tu cuenta">
          {accesos.map((a) => (
            <button key={a.destino} className="pc-acceso" onClick={() => onIr(a.destino)}>
              <span className="pc-acceso__icono"><Icon name={a.icono} size={20} /></span>
              <span className="pc-acceso__texto">
                <strong>{a.label}</strong>
                <span>{a.texto}</span>
              </span>
              <Icon name="arrow" size={16} className="pc-acceso__flecha" />
            </button>
          ))}
        </nav>
      </main>

      {editando && <EditarNombre nombreInicial={user?.nombre} onCerrar={() => setEditando(false)} />}
    </div>
  );
}
