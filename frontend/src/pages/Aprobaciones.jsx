import { useCallback, useEffect, useMemo, useState } from "react";
import { listarTalleres, resolverAprobacionTaller, activarDesactivarTaller } from "../services/tallerService";
import { listarTiendas, resolverAprobacionTienda, activarDesactivarTienda } from "../services/tiendaService";
import { listarPlanes, activarPlan } from "../services/suscripcionService";
import DrawerAdmin from "../components/admin/DrawerAdmin";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import MiniMapa from "../components/admin/MiniMapa";
import { capitalizar, formatoCop, formatoFecha, unir } from "../utils/formato";
import "../css/admin-base.css";

const PESTANAS = [
  { estado: "PENDIENTE", label: "Pendientes" },
  { estado: "APROBADO",  label: "Aprobados" },
  { estado: "RECHAZADO", label: "Rechazados" },
];

// Los talleres anteriores al marketplace no tienen estado: cuentan como aprobados.
const estadoDe = (n) => n.estadoAprobacion || "APROBADO";
const clave = (n) => `${n.tipo}-${n.id}`;
const correoDe = (n) => n.correoUsuario || n.correoContacto || "";
const tieneUbicacion = (n) => n.latitud != null && n.longitud != null;

/** Sin tildes ni mayúsculas, para buscar "Bogotá" escribiendo "bogota". */
const normalizar = (t) => (t || "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();

function BadgeEstado({ negocio }) {
  const estado = estadoDe(negocio);
  if (estado === "PENDIENTE") return <span className="adm-badge adm-badge--pendiente">Pendiente</span>;
  if (estado === "RECHAZADO") return <span className="adm-badge adm-badge--peligro">Rechazado</span>;
  return negocio.activo
    ? <span className="adm-badge adm-badge--exito">Activo</span>
    : <span className="adm-badge adm-badge--neutro">Inactivo</span>;
}

const BadgeTipo = ({ tipo }) => (
  <span className="adm-badge adm-badge--chico adm-badge--info">{tipo === "TALLER" ? "Taller" : "Tienda"}</span>
);

/** Detalle completo del negocio en el panel lateral. Su estado (plan elegido) se reinicia con cada negocio. */
function DetalleNegocio({ negocio: n, planes, ocupado, error, onCerrar, onCambiarPlan, onPedir }) {
  const estado = estadoDe(n);
  const [plan, setPlan] = useState(n.planActual || "");
  const planCambio = plan !== "" && plan !== (n.planActual || "");

  const pie = estado === "APROBADO" ? (
    n.activo
      ? <button type="button" className="adm-btn adm-btn--peligro" onClick={() => onPedir("desactivar", n)} disabled={ocupado}>Desactivar</button>
      : <button type="button" className="adm-btn adm-btn--primario" onClick={() => onPedir("activar", n)} disabled={ocupado}>Activar</button>
  ) : (
    <>
      {estado === "PENDIENTE" && (
        <button type="button" className="adm-btn adm-btn--peligro" onClick={() => onPedir("rechazar", n)} disabled={ocupado}>Rechazar</button>
      )}
      <button type="button" className="adm-btn adm-btn--primario" onClick={() => onPedir("aprobar", n)} disabled={ocupado}>Aprobar</button>
    </>
  );

  return (
    <DrawerAdmin
      titulo={n.nombre}
      onCerrar={onCerrar}
      pie={pie}
      encabezado={<div className="adm-badges"><BadgeTipo tipo={n.tipo} /><BadgeEstado negocio={n} /></div>}
    >
      {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}

      <section className="adm-drawer__bloque">
        <h3 className="adm-drawer__subtitulo">Negocio</h3>
        <dl className="adm-datos">
          <div><dt>NIT</dt><dd>{n.nit || "No registrado"}</dd></div>
          <div><dt>Registro</dt><dd>{formatoFecha(n.fechaRegistro)}</dd></div>
          <div className="adm-datos__item--ancho"><dt>Dirección</dt><dd>{unir([n.direccion, capitalizar(n.barrio)]) || "Sin dirección"}</dd></div>
          <div><dt>Ciudad</dt><dd>{capitalizar(n.ciudad) || "—"}</dd></div>
          <div><dt>Horario</dt><dd>{n.horario || (n.tipo === "TIENDA" ? "Falta completarlo" : "—")}</dd></div>
          {n.tipo === "TIENDA" && (
            <div className="adm-datos__item--ancho">
              <dt>Envío</dt>
              <dd>{n.costoEnvio != null ? `${formatoCop(n.costoEnvio)} (solo en ${capitalizar(n.ciudad) || "su ciudad"})` : "Solo retiro en el local"}</dd>
            </div>
          )}
          {n.tipo === "TALLER" && (
            <>
              <div><dt>Mano de obra</dt><dd>{n.precioManoObra ? formatoCop(n.precioManoObra) : "—"}</dd></div>
              <div><dt>Tarifa por hora</dt><dd>{n.tarifaHora ? formatoCop(n.tarifaHora) : "—"}</dd></div>
              {n.especialidades?.length > 0 && (
                <div className="adm-datos__item--ancho"><dt>Especialidades</dt><dd>{n.especialidades.join(", ")}</dd></div>
              )}
            </>
          )}
          {estado === "RECHAZADO" && n.motivoRechazo && (
            <div className="adm-datos__item--ancho"><dt>Motivo del rechazo</dt><dd>{n.motivoRechazo}</dd></div>
          )}
        </dl>
        {n.tipo === "TIENDA" && n.datosCompletos === false && (
          <p className="adm-alerta adm-alerta--aviso">
            Le falta dirección, ciudad u horario: sus productos no se muestran en el catálogo hasta completarlos.
          </p>
        )}
      </section>

      <section className="adm-drawer__bloque">
        <h3 className="adm-drawer__subtitulo">Contacto</h3>
        <dl className="adm-datos">
          <div className="adm-datos__item--ancho"><dt>Correo</dt><dd>{correoDe(n) || "—"}</dd></div>
          <div><dt>Teléfono</dt><dd>{n.telefono || "—"}</dd></div>
        </dl>
      </section>

      <section className="adm-drawer__bloque">
        <h3 className="adm-drawer__subtitulo">Ubicación GPS</h3>
        {tieneUbicacion(n) ? (
          <>
            <MiniMapa latitud={n.latitud} longitud={n.longitud} titulo={n.nombre} />
            <span className="adm-mono">{n.latitud.toFixed(6)}, {n.longitud.toFixed(6)}</span>
          </>
        ) : (
          <div><span className="adm-badge adm-badge--pendiente">Sin ubicación</span></div>
        )}
      </section>

      {estado === "APROBADO" && (
        <section className="adm-drawer__bloque">
          <h3 className="adm-drawer__subtitulo">Plan</h3>
          <div className="adm-drawer__fila">
            <label className="adm-campo">
              <span>Plan vigente</span>
              <select value={plan} onChange={(e) => setPlan(e.target.value)} disabled={ocupado}>
                {!n.planActual && <option value="">Sin plan vigente</option>}
                {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
              </select>
            </label>
            <button
              type="button"
              className={`adm-btn adm-btn--primario${ocupado ? " adm-btn--cargando" : ""}`}
              onClick={() => onCambiarPlan(n, plan)}
              disabled={!planCambio || ocupado}
              aria-busy={ocupado}
            >
              {ocupado ? "Guardando…" : "Cambiar plan"}
            </button>
          </div>
          {n.planVence && <span className="adm-campo__ayuda adm-encabezado__sub">Vence el {formatoFecha(n.planVence)}</span>}
        </section>
      )}
    </DrawerAdmin>
  );
}

/** Panel del SUPERADMIN para validar y activar talleres y tiendas, y asignarles un plan. */
export default function Aprobaciones({ token }) {
  const [negocios, setNegocios] = useState([]);
  const [planes, setPlanes] = useState([]);
  const [filtro, setFiltro] = useState("PENDIENTE");
  const [tipo, setTipo] = useState("TODOS");
  const [busqueda, setBusqueda] = useState("");
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [procesando, setProcesando] = useState(null);
  const [seleccionado, setSeleccionado] = useState(null);
  // { accion: "aprobar" | "rechazar" | "desactivar" | "activar", negocio }
  const [confirmacion, setConfirmacion] = useState(null);
  const [planInicial, setPlanInicial] = useState("PRUEBA");
  const [motivo, setMotivo] = useState("");

  const cargar = useCallback(async () => {
    setError(null);
    try {
      const [talleres, tiendas, planesData] = await Promise.all([
        listarTalleres(token),
        listarTiendas(token),
        listarPlanes(token),
      ]);
      setNegocios([
        ...talleres.map((t) => ({ ...t, tipo: "TALLER" })),
        ...tiendas.map((t) => ({ ...t, tipo: "TIENDA" })),
      ]);
      setPlanes(planesData.filter((p) => p.activo));
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => { cargar(); }, [cargar]);

  const conteo = useMemo(() => {
    const c = { PENDIENTE: 0, APROBADO: 0, RECHAZADO: 0 };
    negocios.forEach((n) => {
      if (tipo === "TODOS" || n.tipo === tipo) c[estadoDe(n)] += 1;
    });
    return c;
  }, [negocios, tipo]);

  const visibles = useMemo(() => {
    const q = normalizar(busqueda.trim());
    return negocios
      .filter((n) => estadoDe(n) === filtro && (tipo === "TODOS" || n.tipo === tipo))
      .filter((n) => !q || [n.nombre, n.nit, correoDe(n)].some((v) => normalizar(v).includes(q)))
      .sort((a, b) => new Date(b.fechaRegistro || 0) - new Date(a.fechaRegistro || 0));
  }, [negocios, filtro, tipo, busqueda]);

  // Nombre visible del plan ("Básico") a partir de su código ("BASICO")
  const nombrePlan = (codigo) => planes.find((p) => p.codigo === codigo)?.nombre || codigo;

  // El detalle se lee de la lista para mostrar los datos frescos tras cada acción
  const detalle = seleccionado ? negocios.find((n) => clave(n) === seleccionado) : null;

  const ejecutar = async (n, accion, mensaje) => {
    setProcesando(clave(n));
    setError(null);
    setAviso(null);
    try {
      await accion();
      setAviso(mensaje);
      setConfirmacion(null);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const pedir = (accion, negocio) => {
    setError(null);
    setPlanInicial("PRUEBA");
    setMotivo("");
    setConfirmacion({ accion, negocio });
  };

  const confirmar = () => {
    const { accion, negocio: n } = confirmacion;
    const resolver = n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda;
    const activar = n.tipo === "TALLER" ? activarDesactivarTaller : activarDesactivarTienda;
    if (accion === "aprobar") {
      ejecutar(n, () => resolver(token, n.id, { aprobado: true, planCodigo: planInicial }), `${n.nombre} quedó aprobado y activo.`);
    } else if (accion === "rechazar") {
      ejecutar(n, () => resolver(token, n.id, { aprobado: false, motivo: motivo.trim() }), `${n.nombre} fue rechazado.`);
    } else {
      const activo = accion === "activar";
      ejecutar(n, () => activar(token, n.id, activo), `${n.nombre} quedó ${activo ? "activo" : "desactivado"}.`);
    }
  };

  const cambiarPlan = (n, plan) => {
    if (!plan || plan === n.planActual) return;
    ejecutar(n, () => activarPlan(token, n.tipo, n.id, plan), `Plan de ${n.nombre} actualizado a ${plan}.`);
  };

  const abrirConTeclado = (e, n) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      setSeleccionado(clave(n));
    }
  };

  // Los botones de la fila no abren el detalle
  const sinPropagar = (fn) => (e) => { e.stopPropagation(); fn(); };

  const textosConfirmacion = confirmacion && {
    aprobar: {
      titulo: `Aprobar ${confirmacion.negocio.nombre}`,
      texto: "La cuenta quedará activa y podrá operar en el marketplace con el plan que elijas.",
      boton: "Aprobar",
      variante: "primario",
    },
    rechazar: {
      titulo: `Rechazar ${confirmacion.negocio.nombre}`,
      texto: "El negocio recibirá el motivo por correo y no podrá operar.",
      boton: "Rechazar",
      variante: "peligro",
    },
    desactivar: {
      titulo: `Desactivar ${confirmacion.negocio.nombre}`,
      texto: "Dejará de aparecer para los clientes y no recibirá solicitudes ni pedidos hasta que lo actives otra vez.",
      boton: "Desactivar",
      variante: "peligro",
    },
    activar: {
      titulo: `Activar ${confirmacion.negocio.nombre}`,
      texto: "Volverá a aparecer para los clientes y a recibir solicitudes y pedidos.",
      boton: "Activar",
      variante: "primario",
    },
  }[confirmacion.accion];

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Aprobaciones</h1>
          <p className="adm-encabezado__sub">
            Valida los talleres y tiendas que se registran, asígnales un plan y controla qué cuentas pueden operar.
          </p>
        </div>
        {!cargando && (
          <span className="adm-encabezado__dato">{visibles.length} {visibles.length === 1 ? "negocio" : "negocios"}</span>
        )}
      </header>

      <div className="adm-filtros" role="search" aria-label="Filtros de aprobaciones">
        <nav className="adm-pestanas" aria-label="Estado de revisión">
          {PESTANAS.map((p) => (
            <button
              key={p.estado}
              type="button"
              className={`adm-pestana${filtro === p.estado ? " adm-pestana--activa" : ""}`}
              aria-pressed={filtro === p.estado}
              onClick={() => setFiltro(p.estado)}
            >
              {p.label}
              <span className="adm-pestana__n">{conteo[p.estado]}</span>
            </button>
          ))}
        </nav>
        <span className="adm-filtros__separador" />
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} placeholder="Nombre, NIT o correo" />
        </label>
        <label className="adm-campo">
          <span>Mostrar</span>
          <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="TODOS">Talleres y tiendas</option>
            <option value="TALLER">Solo talleres</option>
            <option value="TIENDA">Solo tiendas</option>
          </select>
        </label>
      </div>

      {error && !confirmacion && !detalle && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}
      {aviso && <p className="adm-alerta adm-alerta--exito" role="status">{aviso}</p>}

      <div className="adm-tarjeta" aria-busy={cargando}>
        {cargando ? (
          <div className="adm-cargando">Cargando negocios…</div>
        ) : visibles.length === 0 ? (
          <p className="adm-vacio">
            {busqueda.trim()
              ? "Ningún negocio coincide con la búsqueda."
              : filtro === "PENDIENTE" ? "No hay registros esperando revisión." : "No hay negocios en esta lista."}
          </p>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Negocio</th><th>Ciudad</th><th>Contacto</th><th>Plan</th><th>Estado</th><th>Registro</th>
                  <th className="adm-tabla__der">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((n) => {
                  const k = clave(n);
                  const ocupado = procesando === k;
                  return (
                    <tr
                      key={k}
                      className={`adm-tabla__fila--clic${seleccionado === k ? " adm-tabla__fila--seleccionada" : ""}`}
                      tabIndex={0}
                      onClick={() => setSeleccionado(k)}
                      onKeyDown={(e) => abrirConTeclado(e, n)}
                    >
                      <td data-label="Negocio">
                        <span className="adm-tabla__nombre"><strong>{n.nombre}</strong><BadgeTipo tipo={n.tipo} /></span>
                        <span className="adm-tabla__sub">{unir([n.direccion, capitalizar(n.barrio)]) || "Sin dirección"}</span>
                      </td>
                      <td data-label="Ciudad">{capitalizar(n.ciudad) || "—"}</td>
                      <td data-label="Contacto">
                        {correoDe(n) || "—"}
                        {n.telefono && <span className="adm-tabla__sub">{n.telefono}</span>}
                      </td>
                      <td data-label="Plan">
                        {n.planActual ? nombrePlan(n.planActual) : "—"}
                        {n.planVence && <span className="adm-tabla__sub">Vence {formatoFecha(n.planVence)}</span>}
                      </td>
                      <td data-label="Estado"><BadgeEstado negocio={n} /></td>
                      <td data-label="Registro">{formatoFecha(n.fechaRegistro)}</td>
                      <td className="adm-tabla__der adm-tabla__celda--ancha">
                        <div className="adm-tabla__acciones">
                          {estadoDe(n) === "PENDIENTE" ? (
                            <>
                              <button type="button" className="adm-btn adm-btn--chico adm-btn--peligro"
                                onClick={sinPropagar(() => pedir("rechazar", n))} disabled={ocupado}>Rechazar</button>
                              <button type="button" className="adm-btn adm-btn--chico adm-btn--primario"
                                onClick={sinPropagar(() => pedir("aprobar", n))} disabled={ocupado}>Aprobar</button>
                            </>
                          ) : (
                            <button type="button" className="adm-btn adm-btn--chico"
                              onClick={sinPropagar(() => setSeleccionado(k))}>Ver detalle</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {detalle && (
        <DetalleNegocio
          key={seleccionado}
          negocio={detalle}
          planes={planes}
          ocupado={procesando === seleccionado}
          error={confirmacion ? null : error}
          onCerrar={() => setSeleccionado(null)}
          onCambiarPlan={cambiarPlan}
          onPedir={pedir}
        />
      )}

      {confirmacion && (
        <ConfirmacionAdmin
          titulo={textosConfirmacion.titulo}
          texto={textosConfirmacion.texto}
          confirmar={textosConfirmacion.boton}
          variante={textosConfirmacion.variante}
          cargando={procesando === clave(confirmacion.negocio)}
          deshabilitado={confirmacion.accion === "rechazar" && !motivo.trim()}
          onConfirmar={confirmar}
          onCancelar={() => { setConfirmacion(null); setError(null); }}
        >
          {confirmacion.accion === "aprobar" && (
            <label className="adm-campo adm-campo--bloque">
              <span>Plan inicial</span>
              <select value={planInicial} onChange={(e) => setPlanInicial(e.target.value)}>
                {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
              </select>
            </label>
          )}
          {confirmacion.accion === "rechazar" && (
            <label className="adm-campo adm-campo--bloque">
              <span>Motivo del rechazo</span>
              <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={300}
                placeholder="Se le enviará al negocio por correo" required />
            </label>
          )}
          {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
