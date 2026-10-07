import { useCallback, useEffect, useMemo, useState } from "react";
import { listarTalleres, resolverAprobacionTaller, activarDesactivarTaller } from "../services/tallerService";
import { listarTiendas, resolverAprobacionTienda, activarDesactivarTienda } from "../services/tiendaService";
import { listarPlanes, activarPlan } from "../services/suscripcionService";
import DatosTiendaPropia from "../components/DatosTiendaPropia";
import "../css/aprobaciones.css";

const FILTROS = [
  { estado: "PENDIENTE", label: "Pendientes" },
  { estado: "APROBADO",  label: "Aprobados" },
  { estado: "RECHAZADO", label: "Rechazados" },
];

const ETIQUETA_ESTADO = { PENDIENTE: "Pendiente", APROBADO: "Aprobado", RECHAZADO: "Rechazado" };

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

const formatFecha = (f) => (f ? new Date(f).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" }) : "—");

// Los talleres anteriores al marketplace no tienen estado: cuentan como aprobados.
const estadoDe = (n) => n.estadoAprobacion || "APROBADO";

/** Panel del ADMIN para validar y activar talleres y tiendas, y asignarles un plan. */
export default function Aprobaciones({ token, onTiendaPropiaIncompleta }) {
  const [negocios, setNegocios] = useState([]);
  const [tiendaPropia, setTiendaPropia] = useState(null);
  const [planes, setPlanes] = useState([]);
  const [filtro, setFiltro] = useState("PENDIENTE");
  const [tipo, setTipo] = useState("TODOS");
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [procesando, setProcesando] = useState(null);
  const [planElegido, setPlanElegido] = useState({});
  const [rechazando, setRechazando] = useState(null);
  const [motivo, setMotivo] = useState("");

  const cargar = useCallback(async () => {
    setError(null);
    try {
      const [talleres, tiendas, planesData] = await Promise.all([
        listarTalleres(token),
        listarTiendas(token),
        listarPlanes(token),
      ]);
      const propia = tiendas.find((t) => t.plataforma) || null;
      setTiendaPropia(propia);
      onTiendaPropiaIncompleta?.(!!propia && propia.datosCompletos === false);
      setNegocios([
        ...talleres.map((t) => ({ ...t, tipo: "TALLER" })),
        // La tienda propia de DriveMaster no pasa por aprobación.
        ...tiendas.filter((t) => !t.plataforma).map((t) => ({ ...t, tipo: "TIENDA" })),
      ]);
      setPlanes(planesData.filter((p) => p.activo));
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, onTiendaPropiaIncompleta]);

  useEffect(() => { cargar(); }, [cargar]);

  const conteo = useMemo(() => {
    const c = { PENDIENTE: 0, APROBADO: 0, RECHAZADO: 0 };
    negocios.forEach((n) => { c[estadoDe(n)] += 1; });
    return c;
  }, [negocios]);

  const visibles = useMemo(
    () => negocios
      .filter((n) => estadoDe(n) === filtro && (tipo === "TODOS" || n.tipo === tipo))
      .sort((a, b) => new Date(b.fechaRegistro || 0) - new Date(a.fechaRegistro || 0)),
    [negocios, filtro, tipo]
  );

  const clave = (n) => `${n.tipo}-${n.id}`;

  const ejecutar = async (n, accion, mensaje) => {
    setProcesando(clave(n));
    setError(null);
    setAviso(null);
    try {
      await accion();
      setAviso(mensaje);
      setRechazando(null);
      setMotivo("");
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const aprobar = (n) => {
    const decision = { aprobado: true, planCodigo: planElegido[clave(n)] || "PRUEBA" };
    const llamar = n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda;
    ejecutar(n, () => llamar(token, n.id, decision), `${n.nombre} quedó aprobado y activo.`);
  };

  const rechazar = (n) => {
    if (!motivo.trim()) {
      setError("Escribe el motivo del rechazo: se le enviará al negocio.");
      return;
    }
    const llamar = n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda;
    ejecutar(n, () => llamar(token, n.id, { aprobado: false, motivo: motivo.trim() }), `${n.nombre} fue rechazado.`);
  };

  const cambiarPlan = (n) => {
    const plan = planElegido[clave(n)];
    if (!plan || plan === n.planActual) return;
    ejecutar(n, () => activarPlan(token, n.tipo, n.id, plan), `Plan de ${n.nombre} actualizado a ${plan}.`);
  };

  const alternarActivo = (n) => {
    const llamar = n.tipo === "TALLER" ? activarDesactivarTaller : activarDesactivarTienda;
    const activo = !n.activo;
    ejecutar(n, () => llamar(token, n.id, activo), `${n.nombre} quedó ${activo ? "activo" : "desactivado"}.`);
  };

  return (
    <div className="ap-page">
      <header className="ap-header">
        <span className="ap-eyebrow">Marketplace</span>
        <h1 className="ap-title">Aprobaciones</h1>
        <p className="ap-sub">
          Valida los talleres y tiendas que se registran, asígnales un plan y controla qué cuentas
          pueden operar.
        </p>
      </header>

      <div className="ap-toolbar">
        <nav className="ap-tabs" aria-label="Estado de revisión">
          {FILTROS.map((f) => (
            <button
              key={f.estado}
              type="button"
              className={`ap-tab${filtro === f.estado ? " ap-tab--activo" : ""}`}
              aria-current={filtro === f.estado ? "page" : undefined}
              onClick={() => setFiltro(f.estado)}
            >
              {f.label}
              <span className="ap-tab__conteo">{conteo[f.estado]}</span>
            </button>
          ))}
        </nav>
        <label className="ap-tipo">
          <span>Mostrar</span>
          <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="TODOS">Talleres y tiendas</option>
            <option value="TALLER">Solo talleres</option>
            <option value="TIENDA">Solo tiendas</option>
          </select>
        </label>
      </div>

      {tiendaPropia && <DatosTiendaPropia key={tiendaPropia.id} token={token} tienda={tiendaPropia} onGuardado={cargar} />}

      {error && <p className="ap-alerta ap-alerta--error" role="alert">{error}</p>}
      {aviso && <p className="ap-alerta ap-alerta--ok" role="status">{aviso}</p>}

      {cargando ? (
        <div className="ap-lista">
          {[0, 1, 2].map((i) => <div className="ap-skel" key={i} />)}
        </div>
      ) : visibles.length === 0 ? (
        <p className="ap-vacio">
          {filtro === "PENDIENTE" ? "No hay registros esperando revisión." : "No hay negocios en esta lista."}
        </p>
      ) : (
        <div className="ap-lista">
          {visibles.map((n) => {
            const k = clave(n);
            const ocupado = procesando === k;
            const estado = estadoDe(n);
            return (
              <article className={`ap-card ap-card--${estado.toLowerCase()}`} key={k}>
                <div className="ap-card__cuerpo">
                  <div className="ap-card__chips">
                    <span className={`ap-chip ap-chip--${n.tipo.toLowerCase()}`}>{n.tipo === "TALLER" ? "Taller" : "Tienda"}</span>
                    <span className={`ap-chip ap-chip--${estado.toLowerCase()}`}>{ETIQUETA_ESTADO[estado]}</span>
                    {n.planActual && <span className="ap-chip">Plan {n.planActual}</span>}
                    {estado === "APROBADO" && !n.activo && <span className="ap-chip ap-chip--rechazado">Inactivo</span>}
                    {n.tipo === "TIENDA" && n.datosCompletos === false && (
                      <span className="ap-chip ap-chip--pendiente" title="Le falta dirección, ciudad u horario: sus productos no se muestran en el catálogo hasta completarlos">
                        Completar datos
                      </span>
                    )}
                  </div>

                  <h2 className="ap-card__nombre">{n.nombre}</h2>
                  <p className="ap-card__linea">
                    {[n.direccion, n.barrio, n.ciudad].filter(Boolean).join(" · ") || "Sin dirección"}
                  </p>

                  <dl className="ap-datos">
                    <div><dt>Correo</dt><dd>{n.correoUsuario || n.correoContacto || "—"}</dd></div>
                    <div><dt>Teléfono</dt><dd>{n.telefono || "—"}</dd></div>
                    {n.tipo === "TIENDA" && <div><dt>NIT</dt><dd>{n.nit || "—"}</dd></div>}
                    {n.tipo === "TIENDA" && <div><dt>Horario</dt><dd>{n.horario || "Falta completarlo"}</dd></div>}
                    {n.tipo === "TIENDA" && (
                      <div><dt>Envío</dt><dd>{n.costoEnvio != null ? `${formatPrecio(n.costoEnvio)} (solo en ${n.ciudad || "su ciudad"})` : "Solo retiro"}</dd></div>
                    )}
                    {n.tipo === "TALLER" && (
                      <div><dt>Mano de obra</dt><dd>{n.precioManoObra ? formatPrecio(n.precioManoObra) : "—"}</dd></div>
                    )}
                    <div><dt>Registro</dt><dd>{formatFecha(n.fechaRegistro)}</dd></div>
                    <div><dt>Ubicación GPS</dt><dd>{n.latitud != null && n.longitud != null ? "Sí" : "No"}</dd></div>
                  </dl>

                  {n.tipo === "TALLER" && n.especialidades?.length > 0 && (
                    <p className="ap-card__linea">Especialidades: {n.especialidades.join(", ")}</p>
                  )}
                  {estado === "RECHAZADO" && n.motivoRechazo && (
                    <p className="ap-card__motivo"><strong>Motivo:</strong> {n.motivoRechazo}</p>
                  )}
                </div>

                <div className="ap-card__acciones">
                  {estado !== "APROBADO" && (
                    <>
                      <label className="ap-campo">
                        <span>Plan inicial</span>
                        <select
                          value={planElegido[k] || "PRUEBA"}
                          onChange={(e) => setPlanElegido((prev) => ({ ...prev, [k]: e.target.value }))}
                          disabled={ocupado}
                        >
                          {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
                        </select>
                      </label>
                      <button type="button" className="ap-btn ap-btn--primario" onClick={() => aprobar(n)} disabled={ocupado}>
                        {ocupado ? "Procesando…" : "Aprobar"}
                      </button>
                    </>
                  )}

                  {estado === "PENDIENTE" && (rechazando === k ? (
                    <div className="ap-rechazo">
                      <label className="ap-campo" htmlFor={`motivo-${k}`}>
                        <span>Motivo del rechazo</span>
                      </label>
                      <textarea
                        id={`motivo-${k}`}
                        className="ap-textarea"
                        value={motivo}
                        onChange={(e) => setMotivo(e.target.value)}
                        placeholder="Se le enviará al negocio por correo"
                        maxLength={300}
                      />
                      <div className="ap-rechazo__botones">
                        <button type="button" className="ap-btn ap-btn--peligro" onClick={() => rechazar(n)} disabled={ocupado}>
                          Confirmar rechazo
                        </button>
                        <button type="button" className="ap-btn" onClick={() => { setRechazando(null); setMotivo(""); }} disabled={ocupado}>
                          Cancelar
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button type="button" className="ap-btn" onClick={() => { setRechazando(k); setMotivo(""); }} disabled={ocupado}>
                      Rechazar
                    </button>
                  ))}

                  {estado === "APROBADO" && (
                    <>
                      <label className="ap-campo">
                        <span>Plan</span>
                        <select
                          value={planElegido[k] || n.planActual || ""}
                          onChange={(e) => setPlanElegido((prev) => ({ ...prev, [k]: e.target.value }))}
                          disabled={ocupado}
                        >
                          {!n.planActual && <option value="">Sin plan vigente</option>}
                          {planes.map((p) => <option key={p.codigo} value={p.codigo}>{p.nombre}</option>)}
                        </select>
                      </label>
                      {n.planVence && <span className="ap-nota">Vence el {formatFecha(n.planVence)}</span>}
                      <button
                        type="button"
                        className="ap-btn ap-btn--primario"
                        onClick={() => cambiarPlan(n)}
                        disabled={ocupado || !planElegido[k] || planElegido[k] === n.planActual}
                      >
                        Cambiar plan
                      </button>
                      <button type="button" className="ap-btn" onClick={() => alternarActivo(n)} disabled={ocupado}>
                        {n.activo ? "Desactivar" : "Activar"}
                      </button>
                    </>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
