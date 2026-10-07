import { useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { listarTalleres, crearTaller, actualizarTaller, activarDesactivarTaller } from "../services/tallerService";
import { ordenesPorTallerAdmin } from "../services/ordenServicioService";
import "../css/admin-talleres.css";

const ESTADO_LABEL = {
  SOLICITADA: "Solicitada", COTIZADA: "Cotizada", PAGADA: "Pagada",
  EN_PROCESO: "En proceso", FINALIZADA: "Finalizada", CANCELADA: "Cancelada",
  REASIGNADA: "Reasignada",
};

const VACIO = {
  nombre: "", ciudad: "", direccion: "", latitud: "", longitud: "",
  especialidades: "", rating: "", precioManoObra: "",
  correoUsuario: "", passwordUsuario: "",
};

const FILTROS = [
  { k: "todos", l: "Todos" },
  { k: "activos", l: "Activos" },
  { k: "inactivos", l: "Inactivos" },
];

function formatPrecio(v) {
  if (v == null) return "—";
  return v.toLocaleString("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
}

function monograma(nombre) {
  if (!nombre) return "??";
  return nombre.trim().split(/\s+/).slice(0, 2).map(p => p[0]).join("").toUpperCase();
}

function Estrellas({ valor }) {
  if (valor == null) return <span className="at-rating at-rating--vacio">Sin calificar</span>;
  const lleno = Math.round(valor);
  return (
    <span className="at-rating" title={`${valor} de 5`}>
      <span className="at-rating-estrellas" aria-hidden="true">
        {[1, 2, 3, 4, 5].map(i => (
          <svg key={i} viewBox="0 0 24 24" className={i <= lleno ? "on" : "off"} fill="currentColor">
            <path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.3 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8z" />
          </svg>
        ))}
      </span>
      <span className="at-rating-num">{Number(valor).toFixed(1)}</span>
    </span>
  );
}

function BadgeOrden({ estado }) {
  const clase = {
    FINALIZADA: "at-pill--ok",
    EN_PROCESO: "at-pill--info",
    PAGADA: "at-pill--warn",
    COTIZADA: "at-pill--muted",
    CANCELADA: "at-pill--bad",
  }[estado] || "at-pill--muted";
  return <span className={`at-pill ${clase}`}>{ESTADO_LABEL[estado] || estado}</span>;
}

export default function AdminTalleres() {
  const token = getToken();
  const [talleres, setTalleres] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [formAbierto, setFormAbierto] = useState(false);
  const [editandoId, setEditandoId] = useState(null);
  const [form, setForm] = useState(VACIO);
  const [guardando, setGuardando] = useState(false);

  const [busqueda, setBusqueda] = useState("");
  const [filtro, setFiltro] = useState("todos");

  const [pedidosDe, setPedidosDe] = useState(null);
  const [pedidos, setPedidos] = useState([]);
  const [cargandoPedidos, setCargandoPedidos] = useState(false);

  const cargar = async () => {
    setCargando(true);
    setError(null);
    try {
      setTalleres(await listarTalleres(token));
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const abrirNuevo = () => {
    setEditandoId(null);
    setForm(VACIO);
    setFormAbierto(true);
  };

  const abrirEditar = (t) => {
    setEditandoId(t.id);
    setForm({
      nombre: t.nombre || "", ciudad: t.ciudad || "", direccion: t.direccion || "",
      latitud: t.latitud ?? "", longitud: t.longitud ?? "",
      especialidades: (t.especialidades || []).join(", "),
      rating: t.rating ?? "", precioManoObra: t.precioManoObra ?? "",
      correoUsuario: "", passwordUsuario: "",
    });
    setFormAbierto(true);
  };

  const handleGuardar = async (e) => {
    e.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      const payload = {
        nombre: form.nombre,
        ciudad: form.ciudad,
        direccion: form.direccion,
        latitud: form.latitud === "" ? null : Number(form.latitud),
        longitud: form.longitud === "" ? null : Number(form.longitud),
        especialidades: form.especialidades
          ? form.especialidades.split(",").map(s => s.trim()).filter(Boolean)
          : [],
        rating: form.rating === "" ? null : Number(form.rating),
        precioManoObra: form.precioManoObra === "" ? null : Number(form.precioManoObra),
      };
      if (!editandoId) {
        payload.correoUsuario = form.correoUsuario;
        payload.passwordUsuario = form.passwordUsuario;
      }

      if (editandoId) {
        await actualizarTaller(token, editandoId, payload);
      } else {
        await crearTaller(token, payload);
      }
      setFormAbierto(false);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setGuardando(false);
    }
  };

  const verPedidos = async (t) => {
    setPedidosDe({ id: t.id, nombre: t.nombre });
    setCargandoPedidos(true);
    setError(null);
    try {
      setPedidos(await ordenesPorTallerAdmin(token, t.id));
    } catch (e) {
      setError(e.message);
    } finally {
      setCargandoPedidos(false);
    }
  };

  const handleToggleActivo = async (t) => {
    try {
      await activarDesactivarTaller(token, t.id, !t.activo);
      await cargar();
    } catch (e) {
      setError(e.message);
    }
  };

  const resumen = useMemo(() => {
    const activos = talleres.filter(t => t.activo).length;
    const calificados = talleres.filter(t => t.rating != null);
    const promedio = calificados.length
      ? calificados.reduce((s, t) => s + Number(t.rating), 0) / calificados.length
      : null;
    return { total: talleres.length, activos, inactivos: talleres.length - activos, promedio };
  }, [talleres]);

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    return talleres.filter(t => {
      if (filtro === "activos" && !t.activo) return false;
      if (filtro === "inactivos" && t.activo) return false;
      if (!q) return true;
      return [t.nombre, t.ciudad, t.direccion, ...(t.especialidades || [])]
        .filter(Boolean)
        .some(campo => String(campo).toLowerCase().includes(q));
    });
  }, [talleres, busqueda, filtro]);

  const cerrarForm = () => { if (!guardando) setFormAbierto(false); };

  return (
    <div className="at-root">
      <header className="at-header">
        <div className="at-header-txt">
          <span className="at-eyebrow">Red de talleres</span>
          <h1 className="at-title">Servicios</h1>
          <p className="at-subtitle">
            Talleres aliados conectados a DriveMaster. Desde aquí los activas, editas su
            tarifa de mano de obra y revisas los servicios que le hicieron.
          </p>
        </div>
        <button className="at-btn" onClick={abrirNuevo}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
            <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          Nuevo taller
        </button>
      </header>

      {error && <p className="at-error">{error}</p>}

      <section className="at-stats" aria-label="Resumen de talleres">
        <div className="at-stat">
          <span className="at-stat-value">{resumen.total}</span>
          <span className="at-stat-label">Talleres registrados</span>
        </div>
        <div className="at-stat">
          <span className="at-stat-value at-stat-value--ok">{resumen.activos}</span>
          <span className="at-stat-label">Recibiendo servicios</span>
        </div>
        <div className="at-stat">
          <span className="at-stat-value">{resumen.inactivos}</span>
          <span className="at-stat-label">Pausados</span>
        </div>
        <div className="at-stat">
          <span className="at-stat-value">
            {resumen.promedio != null ? resumen.promedio.toFixed(1) : "—"}
          </span>
          <span className="at-stat-label">Calificación promedio</span>
        </div>
      </section>

      <div className="at-toolbar">
        <div className="at-search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="7" /><line x1="20" y1="20" x2="16.5" y2="16.5" />
          </svg>
          <input
            type="search"
            placeholder="Buscar por nombre, ciudad o especialidad"
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
            aria-label="Buscar talleres"
          />
        </div>
        <div className="at-seg" role="tablist" aria-label="Filtrar por estado">
          {FILTROS.map(f => (
            <button
              key={f.k}
              role="tab"
              aria-selected={filtro === f.k}
              className={`at-seg-btn${filtro === f.k ? " is-active" : ""}`}
              onClick={() => setFiltro(f.k)}
            >
              {f.l}
            </button>
          ))}
        </div>
      </div>

      {cargando ? (
        <div className="at-skel-wrap">
          {[0, 1, 2, 3].map(i => (
            <div className="at-skel-row" key={i}>
              <div className="at-skel at-skel--mono" />
              <div className="at-skel" />
              <div className="at-skel" />
              <div className="at-skel" />
            </div>
          ))}
        </div>
      ) : visibles.length === 0 ? (
        <div className="at-empty-state">
          <div className="at-empty-ico">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
              <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />
            </svg>
          </div>
          <h3>
            {talleres.length === 0
              ? "Todavía no hay talleres"
              : "Ningún taller coincide con la búsqueda"}
          </h3>
          <p>
            {talleres.length === 0
              ? "Registra el primer taller aliado para que los clientes puedan cotizar servicios de taller."
              : "Prueba con otro nombre, ciudad o especialidad, o cambia el filtro de estado."}
          </p>
          {talleres.length === 0 ? (
            <button className="at-btn" onClick={abrirNuevo}>Registrar primer taller</button>
          ) : (
            <button className="at-btn-outline" onClick={() => { setBusqueda(""); setFiltro("todos"); }}>
              Limpiar filtros
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="at-table-wrap">
            <table className="at-table">
              <thead>
                <tr>
                  <th>Taller</th>
                  <th>Ubicación</th>
                  <th>Especialidades</th>
                  <th className="num">Mano de obra</th>
                  <th>Calificación</th>
                  <th>Estado</th>
                  <th className="acciones-col">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {visibles.map(t => (
                  <tr key={t.id} className={t.activo ? "" : "is-off"}>
                    <td data-label="Taller">
                      <div className="at-td-name">
                        <span className="at-mono">{monograma(t.nombre)}</span>
                        <div>
                          <strong>{t.nombre}</strong>
                          {t.direccion && <span className="at-td-sub">{t.direccion}</span>}
                        </div>
                      </div>
                    </td>
                    <td data-label="Ubicación">{t.ciudad || "—"}</td>
                    <td data-label="Especialidades">
                      <div className="at-tags">
                        {(t.especialidades || []).length === 0
                          ? <span className="at-td-sub">—</span>
                          : (t.especialidades || []).slice(0, 2).map(s => (
                              <span className="at-tag" key={s}>{s}</span>
                            ))}
                        {(t.especialidades || []).length > 2 && (
                          <span className="at-tag at-tag--more">
                            +{(t.especialidades || []).length - 2}
                          </span>
                        )}
                      </div>
                    </td>
                    <td data-label="Mano de obra" className="num mono">{formatPrecio(t.precioManoObra)}</td>
                    <td data-label="Calificación"><Estrellas valor={t.rating} /></td>
                    <td data-label="Estado">
                      <span className={`at-pill ${t.activo ? "at-pill--ok" : "at-pill--muted"}`}>
                        {t.activo ? "Activo" : "Inactivo"}
                      </span>
                    </td>
                    <td className="at-acciones">
                      <button className="at-link" onClick={() => verPedidos(t)}>Servicios</button>
                      <span className="at-sep" />
                      <button className="at-link" onClick={() => abrirEditar(t)}>Editar</button>
                      <span className="at-sep" />
                      <button className="at-link at-link--warn" onClick={() => handleToggleActivo(t)}>
                        {t.activo ? "Pausar" : "Activar"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="at-count">
            {visibles.length} de {talleres.length} taller{talleres.length === 1 ? "" : "es"}
          </p>
        </>
      )}

      {pedidosDe && (
        <div className="at-overlay" onClick={() => setPedidosDe(null)}>
          <div className="at-modal at-modal--wide" onClick={e => e.stopPropagation()} role="dialog" aria-modal="true">
            <div className="at-modal-head">
              <div>
                <span className="at-eyebrow">Historial del taller</span>
                <h2 className="at-modal-title">{pedidosDe.nombre}</h2>
              </div>
              <button className="at-x" onClick={() => setPedidosDe(null)} aria-label="Cerrar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>
            <div className="at-modal-body">
              {cargandoPedidos ? (
                <div className="at-skel-wrap">
                  {[0, 1, 2].map(i => <div className="at-skel" key={i} />)}
                </div>
              ) : pedidos.length === 0 ? (
                <p className="at-empty">Este taller todavía no tiene servicios registrados.</p>
              ) : (
                <div className="at-table-wrap">
                  <table className="at-table at-table--inner">
                    <thead>
                      <tr>
                        <th>Vehículo</th>
                        <th>Repuesto</th>
                        <th className="num">Total</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {pedidos.map(p => (
                        <tr key={p.id}>
                          <td data-label="Vehículo">{p.vehiculoDescripcion || "—"}</td>
                          <td data-label="Repuesto">{p.productoNombre || "—"}</td>
                          <td data-label="Total" className="num mono">{formatPrecio(p.total)}</td>
                          <td data-label="Estado"><BadgeOrden estado={p.estado} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {formAbierto && (
        <div className="at-overlay" onClick={cerrarForm}>
          <form className="at-modal" onClick={e => e.stopPropagation()} onSubmit={handleGuardar} role="dialog" aria-modal="true">
            <div className="at-modal-head">
              <div>
                <span className="at-eyebrow">{editandoId ? "Editar" : "Nuevo"}</span>
                <h2 className="at-modal-title">{editandoId ? "Datos del taller" : "Registrar taller aliado"}</h2>
              </div>
              <button type="button" className="at-x" onClick={cerrarForm} aria-label="Cerrar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            <div className="at-modal-body">
              <fieldset className="at-group">
                <legend className="at-group-legend">Identidad</legend>
                <div className="at-field">
                  <label htmlFor="at-nombre">Nombre</label>
                  <input id="at-nombre" required autoFocus value={form.nombre} onChange={e => setForm({ ...form, nombre: e.target.value })} />
                </div>
                <div className="at-field-row">
                  <div className="at-field">
                    <label htmlFor="at-ciudad">Ciudad</label>
                    <input id="at-ciudad" required value={form.ciudad} onChange={e => setForm({ ...form, ciudad: e.target.value })} />
                  </div>
                  <div className="at-field">
                    <label htmlFor="at-mano">Precio mano de obra</label>
                    <input id="at-mano" type="number" step="any" required value={form.precioManoObra} onChange={e => setForm({ ...form, precioManoObra: e.target.value })} />
                  </div>
                </div>
                <div className="at-field">
                  <label htmlFor="at-dir">Dirección</label>
                  <input id="at-dir" value={form.direccion} onChange={e => setForm({ ...form, direccion: e.target.value })} />
                </div>
                <div className="at-field">
                  <label htmlFor="at-esp">Especialidades</label>
                  <input id="at-esp" placeholder="Frenos, suspensión, electrical" value={form.especialidades} onChange={e => setForm({ ...form, especialidades: e.target.value })} />
                  <span className="at-hint">Sepáralas con comas.</span>
                </div>
              </fieldset>

              <fieldset className="at-group">
                <legend className="at-group-legend">Ubicación en el mapa</legend>
                <div className="at-field-row">
                  <div className="at-field">
                    <label htmlFor="at-lat">Latitud</label>
                    <input id="at-lat" type="number" step="any" value={form.latitud} onChange={e => setForm({ ...form, latitud: e.target.value })} />
                  </div>
                  <div className="at-field">
                    <label htmlFor="at-lng">Longitud</label>
                    <input id="at-lng" type="number" step="any" value={form.longitud} onChange={e => setForm({ ...form, longitud: e.target.value })} />
                  </div>
                </div>
                <div className="at-field">
                  <label htmlFor="at-rating">Rating inicial</label>
                  <input id="at-rating" type="number" step="0.1" min="0" max="5" value={form.rating} onChange={e => setForm({ ...form, rating: e.target.value })} />
                </div>
              </fieldset>

              {!editandoId && (
                <fieldset className="at-group">
                  <legend className="at-group-legend">Acceso del taller</legend>
                  <p className="at-hint">Se crea el usuario con el que el taller iniciará sesión.</p>
                  <div className="at-field-row">
                    <div className="at-field">
                      <label htmlFor="at-correo">Correo</label>
                      <input id="at-correo" type="email" required value={form.correoUsuario} onChange={e => setForm({ ...form, correoUsuario: e.target.value })} />
                    </div>
                    <div className="at-field">
                      <label htmlFor="at-pass">Contraseña</label>
                      <input id="at-pass" type="password" required value={form.passwordUsuario} onChange={e => setForm({ ...form, passwordUsuario: e.target.value })} />
                    </div>
                  </div>
                </fieldset>
              )}
            </div>

            <div className="at-modal-foot">
              <button type="button" className="at-btn-outline" onClick={cerrarForm}>Cancelar</button>
              <button type="submit" className="at-btn" disabled={guardando}>
                {guardando ? "Guardando…" : editandoId ? "Guardar cambios" : "Crear taller"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
