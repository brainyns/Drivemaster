import { useEffect, useState } from "react";
import { actualizarMiNombre, getToken, getUser } from "../services/authService";
import { actualizarMiPerfil, obtenerMiPerfil } from "../services/clienteService";
import { cambiarPassword } from "../services/perfilService";
import { misCompras } from "../services/catalogoService";
import { misVentas } from "../services/ventaService";
import { misSolicitudesServicio } from "../services/solicitudServicioService";
import { cotizacionesDeOrden } from "../services/cotizacionService";
import { PEDIDOS_ACTIVOS, clasificarCompras, estadoVentaAnterior } from "../components/marketplace/clasificarCompras";
import { ESTADO_COMPRA, estadoPedido } from "../components/marketplace/estadosCompra";
import { ESTADOS, formatFecha, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import Icon from "../components/public/Icon";
import Campo from "../components/ui/Campo";
import CampoClave from "../components/ui/CampoClave";
import Alerta from "../components/ui/Alerta";
import ChipEstado from "../components/ui/ChipEstado";
import Esqueleto from "../components/ui/Esqueleto";
import { cuerpoPerfil, datosDesdePerfil, validarClave, validarDatos } from "./reglasPerfil";
import "../css/perfil-cliente.css";

const ULTIMOS = 3;

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

/** Una sección de ajustes: título, descripción breve y su contenido. */
function Seccion({ id, titulo, descripcion, children }) {
  return (
    <section className="ui-card pc-seccion" aria-labelledby={id}>
      <header className="pc-seccion__cabecera">
        <h2 id={id} className="pc-seccion__titulo">{titulo}</h2>
        {descripcion && <p className="pc-seccion__texto">{descripcion}</p>}
      </header>
      <div className="pc-seccion__cuerpo">{children}</div>
    </section>
  );
}

/** Nombre y datos de contacto: se cargan del perfil y se guardan con aviso. */
function DatosPersonales({ token, correo, onNombre }) {
  const [datos, setDatos] = useState(null);
  const [inicial, setInicial] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [intento, setIntento] = useState(0);
  const [intentado, setIntentado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [resultado, setResultado] = useState(null); // { tono, texto }

  useEffect(() => {
    let vivo = true;
    obtenerMiPerfil(token)
      .then((p) => {
        if (!vivo) return;
        const d = datosDesdePerfil(p, getUser()?.nombre);
        setDatos(d);
        setInicial(d);
        setErrorCarga(null);
      })
      .catch((e) => { if (vivo) setErrorCarga({ texto: e.message, estado: e.estado }); });
    return () => { vivo = false; };
  }, [token, intento]);

  // Un cliente nuevo llega con sus campos vacíos (200) y ve el formulario vacío. El 404 solo pasa si la cuenta
  // de la sesión ya no existe: reintentar no sirve, el mensaje dice qué hacer.
  if (errorCarga) {
    return errorCarga.estado === 404
      ? <Alerta tono="aviso">{errorCarga.texto}</Alerta>
      : (
        <Alerta onReintentar={() => { setErrorCarga(null); setIntento((n) => n + 1); }}>
          No pudimos cargar tus datos. {errorCarga.texto}
        </Alerta>
      );
  }
  if (!datos) return <Esqueleto variante="formulario" cantidad={3} texto="Cargando tus datos…" />;

  const errores = intentado ? validarDatos(datos) : {};
  const cambio = JSON.stringify(datos) !== JSON.stringify(inicial);
  const cambiar = (e) => {
    setResultado(null);
    setDatos((d) => ({ ...d, [e.target.name]: e.target.value }));
  };

  const guardar = async (e) => {
    e.preventDefault();
    setIntentado(true);
    const faltan = validarDatos(datos);
    const primero = Object.keys(faltan)[0];
    if (primero) {
      document.getElementById(`pc-${primero}`)?.focus();
      return;
    }
    setGuardando(true);
    setResultado(null);
    try {
      if (datos.nombre.trim() !== inicial.nombre) {
        await actualizarMiNombre(datos.nombre.trim());
        onNombre(datos.nombre.trim());
      }
      await actualizarMiPerfil(token, cuerpoPerfil(datos));
      const guardados = { ...datos, nombre: datos.nombre.trim() };
      setDatos(guardados);
      setInicial(guardados);
      setIntentado(false);
      setResultado({ tono: "ok", texto: "Guardamos tus datos." });
    } catch (err) {
      setResultado({ tono: "error", texto: err.message });
    } finally {
      setGuardando(false);
    }
  };

  const campo = (nombre, label, props = {}, extra = {}) => (
    <Campo id={`pc-${nombre}`} label={label} error={errores[nombre]} opcional={extra.opcional} ayuda={extra.ayuda}
      className={extra.ancho ? "pc-ancho" : ""}>
      <input name={nombre} className="ui-field__input" value={datos[nombre]} onChange={cambiar} {...props} />
    </Campo>
  );

  return (
    <form onSubmit={guardar} noValidate>
      <div className="pc-rejilla-campos">
        {campo("nombre", "Nombre", { autoComplete: "name" }, { ancho: true })}
        <Campo id="pc-correo" label="Correo" ayuda="Es tu usuario para entrar; no se puede cambiar aquí." className="pc-ancho">
          <input className="ui-field__input" value={correo || ""} readOnly />
        </Campo>
        {campo("telefono", "Teléfono", { type: "tel", inputMode: "tel", autoComplete: "tel", placeholder: "300 123 4567" })}
        {campo("identificacion", "Documento", { placeholder: "Cédula o NIT" }, { opcional: true })}
        {campo("ciudad", "Ciudad", { autoComplete: "address-level2", placeholder: "Bogotá D.C." })}
        {campo("direccion", "Dirección", { autoComplete: "street-address", placeholder: "Cra 13 # 45-20" })}
        {campo("referencia", "Punto de referencia", { placeholder: "Casa de rejas verdes, apto 302" },
          { opcional: true, ancho: true, ayuda: "Ayuda a las tiendas cuando te envían un pedido." })}
      </div>
      <div className="pc-acciones">
        {resultado && <Alerta tono={resultado.tono} className="pc-acciones__aviso">{resultado.texto}</Alerta>}
        <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando || !cambio} aria-busy={guardando}>
          {guardando ? "Guardando…" : "Guardar cambios"}
        </button>
      </div>
    </form>
  );
}

/** Cambio de contraseña; con cuenta de Google no aplica (no hay contraseña propia). */
function Seguridad({ token, conGoogle }) {
  const VACIA = { actual: "", nueva: "", confirmar: "" };
  const [clave, setClave] = useState(VACIA);
  const [intentado, setIntentado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [resultado, setResultado] = useState(null);

  if (conGoogle) {
    return (
      <p className="pc-seccion__texto pc-google">
        <Icon name="shield" size={18} />
        Entras con tu cuenta de Google, así que tu contraseña la administra Google.
      </p>
    );
  }

  const errores = intentado ? validarClave(clave) : {};
  const cambiar = (e) => {
    setResultado(null);
    setClave((c) => ({ ...c, [e.target.name]: e.target.value }));
  };

  const guardar = async (e) => {
    e.preventDefault();
    setIntentado(true);
    const faltan = validarClave(clave);
    const primero = Object.keys(faltan)[0];
    if (primero) {
      document.getElementById(`pc-clave-${primero}`)?.focus();
      return;
    }
    setGuardando(true);
    setResultado(null);
    try {
      await cambiarPassword(token, clave.actual, clave.nueva);
      setClave(VACIA);
      setIntentado(false);
      setResultado({ tono: "ok", texto: "Cambiamos tu contraseña. Úsala la próxima vez que entres." });
    } catch (err) {
      setResultado({ tono: "error", texto: err.message });
    } finally {
      setGuardando(false);
    }
  };

  return (
    <form onSubmit={guardar} noValidate>
      <div className="pc-rejilla-campos">
        <Campo id="pc-clave-actual" label="Contraseña actual" error={errores.actual} className="pc-ancho">
          <CampoClave name="actual" value={clave.actual} onChange={cambiar} autoComplete="current-password" />
        </Campo>
        <Campo id="pc-clave-nueva" label="Nueva contraseña" error={errores.nueva} ayuda="Mínimo 6 caracteres.">
          <CampoClave name="nueva" value={clave.nueva} onChange={cambiar} autoComplete="new-password" />
        </Campo>
        <Campo id="pc-clave-confirmar" label="Confirmar nueva contraseña" error={errores.confirmar}>
          <CampoClave name="confirmar" value={clave.confirmar} onChange={cambiar} autoComplete="new-password" />
        </Campo>
      </div>
      <div className="pc-acciones">
        {resultado && <Alerta tono={resultado.tono} className="pc-acciones__aviso">{resultado.texto}</Alerta>}
        <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
          {guardando ? "Cambiando…" : "Cambiar contraseña"}
        </button>
      </div>
    </form>
  );
}

/** Tarjeta de actividad reciente con sus estados: cargando, error con reintento, vacío con qué hacer y la lista. */
function Resumen({ titulo, idTitulo, estado, vacio, accionVacio, onVerTodo, onReintentar, children }) {
  return (
    <section className="ui-card pc-tarjeta" aria-labelledby={idTitulo} aria-busy={estado === "cargando"}>
      <header className="ui-card__header">
        <h2 id={idTitulo} className="ui-card__titulo">{titulo}</h2>
        {estado === "listo" && <button className="ui-btn ui-btn--texto ui-btn--chico" onClick={onVerTodo}>Ver todo</button>}
      </header>
      <div className="ui-card__cuerpo">
        {estado === "cargando" && <Esqueleto variante="lista" cantidad={2} texto={`Cargando ${titulo.toLowerCase()}…`} />}
        {estado === "error" && <Alerta onReintentar={onReintentar}>{vacio.error}</Alerta>}
        {estado === "vacio" && (
          <div className="ui-empty pc-vacio">
            <p className="ui-empty__titulo">{vacio.titulo}</p>
            <p>{vacio.texto}</p>
            <button className="ui-btn ui-btn--secundario ui-btn--chico" onClick={accionVacio}>{vacio.boton}</button>
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
        <ChipEstado tono={estado.tono}>{estado.label}</ChipEstado>
        <span className="pc-item__total">{total}</span>
      </div>
    </li>
  );
}

/**
 * Mi perfil del CLIENTE: datos personales, seguridad y preferencias, y al lado su actividad reciente (últimas
 * compras y servicios) con los accesos a Mis compras, Mis servicios y Mis vehículos.
 */
export default function PerfilCliente({ header, onIr, onIrCatalogo, onSolicitarDiagnostico, theme, onToggleTheme }) {
  const token = getToken();
  const user = getUser();
  const [nombre, setNombre] = useState(user?.nombre || "");
  const [compras, setCompras] = useState({ estado: "cargando", items: [] });
  const [servicios, setServicios] = useState({ estado: "cargando", items: [] });
  const [totales, setTotales] = useState({}); // id de la solicitud → total de su cotización vigente
  const conGoogle = (user?.proveedor || "").toUpperCase() === "GOOGLE";

  const [intentoCompras, setIntentoCompras] = useState(0);
  const [intentoServicios, setIntentoServicios] = useState(0);

  // Las consultas solo cambian el estado al responder; "Reintentar" vuelve a mostrar la carga
  useEffect(() => {
    let vivo = true;
    // Las ventas del sistema anterior son un extra: si fallan, se muestran solo las del catálogo
    Promise.all([misCompras(token), misVentas(token).catch(() => [])])
      .then(([c, v]) => {
        if (!vivo) return;
        const items = ultimasCompras(c || [], Array.isArray(v) ? v : []);
        setCompras({ estado: items.length ? "listo" : "vacio", items });
      })
      .catch(() => { if (vivo) setCompras({ estado: "error", items: [] }); });
    return () => { vivo = false; };
  }, [token, intentoCompras]);

  useEffect(() => {
    let vivo = true;
    misSolicitudesServicio(token)
      .then((s) => {
        if (!vivo) return;
        const items = (s || []).slice(0, ULTIMOS);
        setServicios({ estado: items.length ? "listo" : "vacio", items });
        // Total de cada servicio cotizado; sin cotización se muestra la tarifa del diagnóstico
        items.filter((x) => x.cotizacionId).forEach((x) => {
          // false: no se pudo saber; se muestra la tarifa del diagnóstico en su lugar
          cotizacionesDeOrden(token, x.id)
            .then((cs) => { if (vivo) setTotales((t) => ({ ...t, [x.id]: cs?.[0]?.total ?? false })); })
            .catch(() => { if (vivo) setTotales((t) => ({ ...t, [x.id]: false })); });
        });
      })
      .catch(() => { if (vivo) setServicios({ estado: "error", items: [] }); });
    return () => { vivo = false; };
  }, [token, intentoServicios]);

  const reintentarCompras = () => { setCompras({ estado: "cargando", items: [] }); setIntentoCompras((n) => n + 1); };
  const reintentarServicios = () => { setServicios({ estado: "cargando", items: [] }); setIntentoServicios((n) => n + 1); };

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
          <span className="pc-avatar" aria-hidden="true">{(nombre || "?").trim().charAt(0).toUpperCase()}</span>
          <div className="pc-cabecera__datos">
            <h1 className="pc-nombre">{nombre || "Mi perfil"}</h1>
            {user?.correo && <p className="pc-correo">{user.correo}</p>}
          </div>
        </header>

        <div className="pc-cuerpo">
          <div className="pc-ajustes">
            <Seccion id="pc-t-datos" titulo="Datos personales"
              descripcion="Los usamos para tus compras y para que el taller y las tiendas puedan contactarte.">
              <DatosPersonales token={token} correo={user?.correo} onNombre={setNombre} />
            </Seccion>

            <Seccion id="pc-t-seguridad" titulo="Seguridad" descripcion="Cambia la contraseña con la que entras a DriveMaster.">
              <Seguridad token={token} conGoogle={conGoogle} />
            </Seccion>

            <Seccion id="pc-t-preferencias" titulo="Preferencias">
              <fieldset className="pc-fieldset">
                <legend className="ui-field__label">Tema</legend>
                <div className="pc-opciones">
                  {[{ v: "light", t: "Claro" }, { v: "dark", t: "Oscuro" }].map((o) => (
                    <label key={o.v} className={`ui-opcion${theme === o.v ? " ui-opcion--elegida" : ""}`}>
                      <input type="radio" name="pc-tema" className="ui-sr" value={o.v} checked={theme === o.v}
                        onChange={() => { if (theme !== o.v) onToggleTheme?.(); }} />
                      {o.t}
                    </label>
                  ))}
                </div>
              </fieldset>
              <div className="pc-preferencia">
                <div>
                  <p className="pc-preferencia__titulo">Recordatorios de mantenimiento</p>
                  <p className="pc-seccion__texto">Se activan por vehículo, según su kilometraje.</p>
                </div>
                <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => onIr("mis-vehiculos")}>
                  Configurar en Mis vehículos
                </button>
              </div>
            </Seccion>
          </div>

          <aside className="pc-actividad" aria-label="Tu actividad">
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
          </aside>
        </div>
      </main>
    </div>
  );
}
