import { Fragment, useEffect, useState } from "react";
import { negociosPlataforma } from "../services/plataformaService";
import { resolverAprobacionTaller, activarDesactivarTaller } from "../services/tallerService";
import { resolverAprobacionTienda, activarDesactivarTienda } from "../services/tiendaService";
import { listarPlanes, activarPlan } from "../services/suscripcionService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";

const TAMANO = 20;
const ESTADOS = { PENDIENTE: "Pendiente", APROBADO: "Aprobado", RECHAZADO: "Rechazado" };
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");
const FILTROS_INICIALES = { tipo: "", estado: "", q: "" };

/**
 * Tiendas y talleres en una sola lista (SUPERADMIN): estado de aprobación, plan, calificación, productos u órdenes
 * y qué datos les faltan. Desde aquí se aprueba, rechaza, pausa y cambia el plan. El ADMIN no ve esta pantalla:
 * solo aprueba o rechaza desde "Aprobaciones".
 */
export default function NegociosPlataforma({ token }) {
  const [borrador, setBorrador] = useState(FILTROS_INICIALES);
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [pagina, setPagina] = useState(0);
  const [version, setVersion] = useState(0);
  const [estado, setEstado] = useState({ datos: null, error: null });
  const [planes, setPlanes] = useState([]);
  const [abierto, setAbierto] = useState(null);
  const [planElegido, setPlanElegido] = useState({});
  const [motivo, setMotivo] = useState("");
  const [rechazando, setRechazando] = useState(null);
  const [procesando, setProcesando] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);

  useEffect(() => {
    let vivo = true;
    negociosPlataforma(token, { ...filtros, pagina, tamano: TAMANO })
      .then((datos) => { if (vivo) setEstado({ datos, error: null }); })
      .catch((e) => { if (vivo) setEstado((p) => ({ ...p, error: e.message })); });
    return () => { vivo = false; };
  }, [token, filtros, pagina, version]);

  useEffect(() => {
    let vivo = true;
    listarPlanes(token).then((p) => { if (vivo) setPlanes(p.filter((x) => x.activo)); }).catch(() => {});
    return () => { vivo = false; };
  }, [token]);

  const { datos, error } = estado;
  const lista = datos?.negocios;
  const paginas = lista ? Math.max(1, Math.ceil(lista.total / lista.tamano)) : 1;
  const clave = (n) => `${n.tipo}-${n.id}`;
  const hayFiltros = filtros.tipo !== "" || filtros.estado !== "" || filtros.q !== "";

  const poner = (cambio) => {
    const siguiente = { ...borrador, ...cambio };
    setBorrador(siguiente);
    setPagina(0);
    setFiltros(siguiente);
  };

  const ejecutar = async (n, accion, mensaje) => {
    setProcesando(clave(n));
    setErrorAccion(null);
    setAviso(null);
    try {
      await accion();
      setAviso(mensaje);
      setRechazando(null);
      setMotivo("");
      setVersion((v) => v + 1);
    } catch (e) {
      setErrorAccion(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const aprobar = (n) => ejecutar(n,
    () => (n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda)(token, n.id,
      { aprobado: true, planCodigo: planElegido[clave(n)] || "PRUEBA" }),
    `${n.nombre} quedó aprobado.`);

  const rechazar = (n) => {
    if (!motivo.trim()) { setErrorAccion("Escribe el motivo del rechazo: se le enviará al negocio."); return; }
    ejecutar(n, () => (n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda)(token, n.id,
      { aprobado: false, motivo: motivo.trim() }), `${n.nombre} fue rechazado.`);
  };

  const alternar = (n) => ejecutar(n,
    () => (n.tipo === "TALLER" ? activarDesactivarTaller : activarDesactivarTienda)(token, n.id, !n.activo),
    n.activo ? `${n.nombre} quedó en pausa.` : `${n.nombre} volvió a estar activo.`);

  const cambiarPlan = (n) => {
    const plan = planElegido[clave(n)];
    if (!plan || plan === n.plan?.codigo) return;
    ejecutar(n, () => activarPlan(token, n.tipo, n.id, plan), `Plan de ${n.nombre} actualizado.`);
  };

  return (
    <div className="db-root dp-root">
      <header className="db-header">
        <div>
          <h1 className="db-h1">Tiendas y talleres</h1>
          <p className="db-sub">Aprobaciones, plan, calificación, actividad y datos por completar de cada negocio.</p>
        </div>
        {lista && <span className="db-date">{lista.total} {lista.total === 1 ? "negocio" : "negocios"}</span>}
      </header>

      {datos && (
        <div className="dp-pestanas" role="tablist" aria-label="Estado de aprobación">
          <button type="button" role="tab" aria-selected={filtros.estado === ""} className={`dp-pestana${filtros.estado === "" ? " dp-pestana--activa" : ""}`}
            onClick={() => poner({ estado: "" })}>Todos</button>
          {Object.keys(ESTADOS).map((e) => (
            <button key={e} type="button" role="tab" aria-selected={filtros.estado === e}
              className={`dp-pestana${filtros.estado === e ? " dp-pestana--activa" : ""}`} onClick={() => poner({ estado: e })}>
              {ESTADOS[e]}s <span className="dp-pestana__n">{datos.porEstado[e] ?? 0}</span>
            </button>
          ))}
        </div>
      )}

      <form className="dp-filtros" role="search" aria-label="Filtros de negocios"
        onSubmit={(e) => { e.preventDefault(); setPagina(0); setFiltros(borrador); }}>
        <label className="dp-campo dp-campo--ancho">
          <span>Buscar</span>
          <input type="search" value={borrador.q} onChange={(e) => setBorrador({ ...borrador, q: e.target.value })} placeholder="Nombre o ciudad" />
        </label>
        <label className="dp-campo">
          <span>Tipo</span>
          <select value={borrador.tipo} onChange={(e) => poner({ tipo: e.target.value })}>
            <option value="">Tiendas y talleres</option>
            <option value="TIENDA">Tiendas</option>
            <option value="TALLER">Talleres</option>
          </select>
        </label>
        <div className="dp-filtros__acciones">
          <button type="submit" className="dp-btn">Buscar</button>
          {hayFiltros && <button type="button" className="dp-btn dp-btn--sutil" onClick={() => { setBorrador(FILTROS_INICIALES); setFiltros(FILTROS_INICIALES); setPagina(0); }}>Limpiar</button>}
        </div>
      </form>

      {aviso && <div className="dp-aviso" role="status">{aviso}</div>}
      {(error || errorAccion) && <div className="db-error" role="alert">{errorAccion || error}</div>}

      <div className="db-card dp-lista">
        {!lista && !error ? (
          <div className="db-loading"><div className="db-spinner" /> Cargando negocios…</div>
        ) : lista && lista.items.length === 0 ? (
          <p className="db-empty">{hayFiltros ? "Ningún negocio coincide con los filtros." : "Todavía no hay negocios registrados."}</p>
        ) : lista && (
          <div className="db-table-wrap">
            <table className="db-table">
              <thead>
                <tr>
                  <th>Negocio</th><th>Estado</th><th>Plan</th><th className="dp-der">Calificación</th>
                  <th className="dp-der">Actividad</th><th>Datos por completar</th><th>Registro</th><th><span className="dp-oculto">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {lista.items.map((n) => {
                  const k = clave(n);
                  const completo = n.datosPorCompletar.length === 0;
                  return (
                    <Fragment key={k}>
                      <tr>
                        <td>
                          <strong>{n.nombre || "Sin nombre"}</strong>
                          <span className="dp-sub-celda">{n.tipo === "TIENDA" ? "Tienda" : "Taller"} · {n.ciudad || "sin ciudad"}</span>
                        </td>
                        <td>
                          <span className={`dp-etiqueta${n.estado === "APROBADO" ? " dp-etiqueta--ok" : n.estado === "RECHAZADO" ? " dp-etiqueta--alerta" : ""}`}>{ESTADOS[n.estado] || n.estado}</span>
                          {n.estado === "APROBADO" && !n.activo && <span className="dp-sub-celda">En pausa</span>}
                        </td>
                        <td>{n.plan ? n.plan.nombre : "—"}</td>
                        <td className="dp-der">{n.calificacion != null ? `${n.calificacion.toFixed(1)} ★${n.calificaciones ? ` (${n.calificaciones})` : ""}` : "Sin calificar"}</td>
                        <td className="dp-der">
                          {n.tipo === "TIENDA" ? `${n.productos} producto${n.productos === 1 ? "" : "s"}` : `${n.ordenes} orden${n.ordenes === 1 ? "" : "es"}`}
                        </td>
                        <td>{completo ? <span className="dp-etiqueta dp-etiqueta--ok">Completos</span>
                          : <span className="dp-etiqueta dp-etiqueta--alerta">Falta: {n.datosPorCompletar.join(", ")}</span>}</td>
                        <td>{fecha(n.registrado)}</td>
                        <td>
                          <button type="button" className="dp-btn dp-btn--sutil" aria-expanded={abierto === k}
                            onClick={() => { setAbierto(abierto === k ? null : k); setRechazando(null); setErrorAccion(null); }}>
                            {abierto === k ? "Cerrar" : "Gestionar"}
                          </button>
                        </td>
                      </tr>
                      {abierto === k && (
                        <tr className="dp-gestion">
                          <td colSpan={8}>
                            {n.motivoRechazo && n.estado === "RECHAZADO" && <p className="dp-nota">Motivo del rechazo: {n.motivoRechazo}</p>}
                            {n.tipo === "TIENDA" && <p className="dp-nota">{n.productosActivos} de {n.productos} productos activos.</p>}
                            {n.tipo === "TALLER" && <p className="dp-nota">{n.ordenesEntregadas} de {n.ordenes} órdenes entregadas.</p>}

                            {n.estado === "PENDIENTE" && (
                              <div className="dp-acciones-fila">
                                <label className="dp-campo">
                                  <span>Plan al aprobar</span>
                                  <select value={planElegido[k] || "PRUEBA"} onChange={(e) => setPlanElegido({ ...planElegido, [k]: e.target.value })}>
                                    {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
                                  </select>
                                </label>
                                <button type="button" className="dp-btn" disabled={procesando === k} onClick={() => aprobar(n)}>Aprobar</button>
                                <button type="button" className="dp-btn dp-btn--sutil" onClick={() => setRechazando(rechazando === k ? null : k)}>Rechazar…</button>
                              </div>
                            )}
                            {rechazando === k && (
                              <div className="dp-acciones-fila">
                                <label className="dp-campo dp-campo--ancho">
                                  <span>Motivo (se le envía al negocio)</span>
                                  <input value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={300} />
                                </label>
                                <button type="button" className="dp-btn" disabled={procesando === k} onClick={() => rechazar(n)}>Confirmar rechazo</button>
                              </div>
                            )}
                            {n.estado === "APROBADO" && (
                              <div className="dp-acciones-fila">
                                <label className="dp-campo">
                                  <span>Plan</span>
                                  <select value={planElegido[k] || n.plan?.codigo || ""} onChange={(e) => setPlanElegido({ ...planElegido, [k]: e.target.value })}>
                                    {!n.plan && <option value="">Sin plan</option>}
                                    {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
                                  </select>
                                </label>
                                <button type="button" className="dp-btn" disabled={procesando === k || !planElegido[k] || planElegido[k] === n.plan?.codigo}
                                  onClick={() => cambiarPlan(n)}>Cambiar plan</button>
                                <button type="button" className="dp-btn dp-btn--sutil" disabled={procesando === k} onClick={() => alternar(n)}>
                                  {n.activo ? "Pausar" : "Activar"}
                                </button>
                              </div>
                            )}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {lista && lista.total > lista.tamano && (
        <nav className="dp-paginacion" aria-label="Paginación">
          <button type="button" className="dp-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {paginas}</span>
          <button type="button" className="dp-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  );
}
