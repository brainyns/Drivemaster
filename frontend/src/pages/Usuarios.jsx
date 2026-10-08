import { apiFetch } from "../services/http";
import { useEffect, useState } from "react";
import { crearUsuario, eliminarUsuario, getUser } from "../services/authService";
import { usuariosPlataforma } from "../services/plataformaService";
import "../css/Usuarios.css";

const API_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

const ROL_CONFIG = {
  SUPERADMIN: { label: "SUPER ADMIN", className: "us-badge--superadmin" },
  ADMIN: { label: "ADMIN", className: "us-badge--admin" },
  TIENDA: { label: "TIENDA", className: "us-badge--tienda" },
  TALLER: { label: "TALLER", className: "us-badge--taller" },
  CLIENTE: { label: "CLIENTE", className: "us-badge--cliente" },
  MECANICO: { label: "MECÁNICO", className: "us-badge--taller" },
  // Rol retirado: las cuentas que quedan se siguen listando para poder reasignarlas o desactivarlas
  VENDEDOR: { label: "VENDEDOR (RETIRADO)", className: "us-badge--vendedor" },
};

// Solo estos roles se crean o se asignan desde aquí. Las tiendas y los talleres se registran con
// "Registra tu negocio" y se aprueban en Aprobaciones, porque necesitan perfil y aprobación.
const ROLES_DEL_EQUIPO = ["ADMIN", "SUPERADMIN"];

const IconoTarjeta = {
  TIENDA: <path d="M3 9l1.5-5h15L21 9M4 9v11h16V9M3 9a3 3 0 006 0 3 3 0 006 0 3 3 0 006 0M10 20v-5h4v5" />,
  TALLER: <path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z" />,
  CLIENTE: <><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75" /></>,
  ADMIN: <><circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" /></>,
  SUPERADMIN: <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />,
};

const TARJETAS = [
  { rol: "TIENDA", titulo: "Tiendas" },
  { rol: "TALLER", titulo: "Talleres" },
  { rol: "CLIENTE", titulo: "Clientes" },
  { rol: "ADMIN", titulo: "Administradores" },
  { rol: "SUPERADMIN", titulo: "Super Admins" },
];

const AVATAR_COLORS = ["#E8450A", "#7C3AED", "#0EA5E9", "#10B981", "#F59E0B"];

function getInitials(nombre) {
  return (nombre || "")
    .split(" ")
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

export default function Usuarios({ token }) {
  const [lista, setLista] = useState({ usuarios: null, porRol: {} });
  const [version, setVersion] = useState(0); // al subir, la lista se vuelve a pedir
  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [filtroEstado, setFiltroEstado] = useState("");
  const [pagina, setPagina] = useState(0);
  const usuarios = lista.usuarios?.items || [];
  const recargar = () => setVersion((v) => v + 1);
  const [nuevoUsuario, setNuevoUsuario] = useState({
    nombre: "",
    correo: "",
    password: "",
    rol: "ADMIN",
  });
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [filterRol, setFilterRol] = useState("TODOS");
  const TAMANO = 20;

  const currentUser = getUser();
  const isSuperadmin = currentUser?.rol === "SUPERADMIN";

  const [editando, setEditando] = useState(null);
  const [editForm, setEditForm] = useState({ nombre: "", rol: "", password: "" });
  const [editError, setEditError] = useState(null);
  const [editSuccess, setEditSuccess] = useState(null);

  useEffect(() => {
    if (!token) return undefined;
    let vivo = true;
    usuariosPlataforma(token, { q, rol: filterRol === "TODOS" ? "" : filterRol, estado: filtroEstado, pagina, tamano: TAMANO })
      .then((r) => { if (vivo) { setLista({ usuarios: r.usuarios, porRol: r.porRol }); setError(null); } })
      .catch((err) => { if (vivo) setError(err.message); });
    return () => { vivo = false; };
  }, [token, q, filterRol, filtroEstado, pagina, version]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    try {
      await crearUsuario(nuevoUsuario, token);
      setNuevoUsuario({ nombre: "", correo: "", password: "", rol: "ADMIN" });
      recargar();
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err) {
      setError(err.message || "Error al crear usuario");
    }
  };

  const handleEditClick = (u) => {
    setEditando(u);
    setEditForm({ nombre: u.nombre || "", rol: u.rol || "", password: "" });
    setEditError(null);
    setEditSuccess(null);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    setEditError(null);
    setEditSuccess(null);
    try {
      const body = { nombre: editForm.nombre };
      if (editForm.password) body.password = editForm.password;
      if (editForm.rol && editForm.rol !== editando.rol) body.rol = editForm.rol;
      const res = await apiFetch(`${API_BASE}/usuarios/${editando.id}`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || data.message || "Error al actualizar");
      }
      setEditSuccess("Usuario actualizado correctamente");
      recargar();
      setTimeout(() => { setEditando(null); setEditSuccess(null); }, 1500);
    } catch (err) {
      setEditError(err.message);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("¿Desactivar este usuario? Esta acción no se puede deshacer.")) return;
    try {
      await eliminarUsuario(id);
      recargar();
    } catch (err) {
      setError(err.message);
    }
  };

  // Volver a activar una cuenta desactivada (solo SUPERADMIN: lo valida el backend)
  const handleActivar = async (id) => {
    try {
      const res = await apiFetch(`${API_BASE}/usuarios/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ activo: true }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || data.message || "No se pudo activar el usuario");
      }
      recargar();
    } catch (err) {
      setError(err.message);
    }
  };

  // Los conteos de las tarjetas vienen del servidor (no dependen de la página que se ve)
  const conteo = lista.porRol;
  // Cambiar el rol solo aplica a cuentas del equipo (ADMIN, SUPERADMIN) y al vendedor retirado, que se reasigna
  const rolEditable = editando && (ROLES_DEL_EQUIPO.includes(editando.rol) || editando.rol === "VENDEDOR");
  const elegirTarjeta = (rol) => { setPagina(0); setFilterRol(filterRol === rol ? "TODOS" : rol); };

  const usuariosFiltrados = usuarios;
  const total = lista.usuarios?.total || 0;
  const paginas = Math.max(1, Math.ceil(total / TAMANO));

  return (
    <>
    <div className="us-root">
      {/* ── Page header ── */}
      <div className="us-page-header">
        <div>
          <h1 className="us-page-title">Usuarios</h1>
          <p className="us-page-sub">Gestiona los usuarios y roles de acceso al sistema.</p>
        </div>
      </div>

      {/* ── Tarjetas por rol: también son filtros de la lista ── */}
      <div className="us-stats" role="group" aria-label="Usuarios por rol">
        {TARJETAS.map((t) => (
          <button
            key={t.rol}
            type="button"
            className={`us-stat us-stat--${t.rol.toLowerCase()}${filterRol === t.rol ? " is-activa" : ""}`}
            aria-pressed={filterRol === t.rol}
            onClick={() => elegirTarjeta(t.rol)}
          >
            <span className="us-stat__icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                {IconoTarjeta[t.rol]}
              </svg>
            </span>
            <span className="us-stat__label">{t.titulo}</span>
            <span className="us-stat__value">{lista.usuarios ? (conteo[t.rol] ?? 0) : "–"}</span>
            <span className={`us-stat__bar us-stat__bar--${t.rol.toLowerCase()}`} />
          </button>
        ))}
      </div>

      {/* ── Main layout ── */}
      <div className="us-main">
        {/* ── Tabla ── */}
        <div className="us-panel us-panel--table">
          <div className="us-panel__header">
            <h2 className="us-panel__title">Usuarios registrados</h2>
            <div className="us-filter">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" /></svg>
              <select
                className="us-filter__select"
                aria-label="Filtrar por rol"
                value={filterRol}
                onChange={(e) => { setPagina(0); setFilterRol(e.target.value); }}
              >
                <option value="TODOS">Todos los roles</option>
                <option value="TIENDA">Tiendas</option>
                <option value="TALLER">Talleres</option>
                <option value="CLIENTE">Clientes</option>
                <option value="ADMIN">Administradores</option>
                <option value="SUPERADMIN">Super Admins</option>
              </select>
              <select
                className="us-filter__select"
                aria-label="Filtrar por estado"
                value={filtroEstado}
                onChange={(e) => { setPagina(0); setFiltroEstado(e.target.value); }}
              >
                <option value="">Todos los estados</option>
                <option value="activos">Activos</option>
                <option value="inactivos">Inactivos</option>
              </select>
            </div>
          </div>

          <form className="us-search" role="search" onSubmit={(e) => { e.preventDefault(); setPagina(0); setQ(busqueda.trim()); }}>
            <input
              className="us-input"
              type="search"
              placeholder="Buscar por nombre o correo"
              aria-label="Buscar usuarios"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
            />
            <button type="submit" className="us-btn-submit us-btn-submit--sm">Buscar</button>
          </form>

          {error && <p className="us-error">{error}</p>}

          <div className="us-table-wrap">
              <table className="us-table">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Correo</th>
                    <th>Rol</th>
                    <th>Estado</th>
                    {isSuperadmin && <th>Acciones</th>}
                  </tr>
                </thead>
              <tbody>
                {usuariosFiltrados.map((u, i) => {
                  const cfg = ROL_CONFIG[u.rol] || { label: u.rol, className: "" };
                  const color = AVATAR_COLORS[i % AVATAR_COLORS.length];
                  return (
                    <tr key={u.id} className="us-row">
                      <td>
                        <div className="us-user">
                          <div className="us-avatar" style={{ background: color }}>
                            {getInitials(u.nombre)}
                          </div>
                          <span className="us-user__name">{u.nombre}</span>
                        </div>
                      </td>
                      <td className="us-cell--muted">{u.correo}</td>
                      <td>
                        <span className={`us-badge ${cfg.className}`}>{cfg.label}</span>
                      </td>
                      <td>
                        <span className={`us-status ${u.activo ? "us-status--active" : "us-status--inactive"}`}>
                          <span className="us-status__dot" />
                          {u.activo ? "Activo" : "Inactivo"}
                        </span>
                      </td>
                      {isSuperadmin && (
                        <td>
                          <div className="us-actions">
                            <button className="us-action-btn us-action-btn--edit" title="Editar" onClick={() => handleEditClick(u)}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/>
                                <path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/>
                              </svg>
                            </button>
                            {u.activo ? (
                              <button className="us-action-btn us-action-btn--delete" title="Desactivar" onClick={() => handleDelete(u.id)}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                  <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                                </svg>
                              </button>
                            ) : (
                              <button className="us-action-btn us-action-btn--edit" title="Activar" onClick={() => handleActivar(u.id)}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="20 6 9 17 4 12" /></svg>
                              </button>
                            )}
                          </div>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {lista.usuarios && usuariosFiltrados.length === 0 && (
              <p className="us-cell--muted" style={{ padding: "1rem" }}>Ningún usuario coincide con los filtros.</p>
            )}
          </div>
          {total > TAMANO && (
            <div className="us-pager" style={{ display: "flex", justifyContent: "center", gap: "1rem", padding: ".75rem" }}>
              <button type="button" className="us-action-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
              <span className="us-cell--muted">Página {pagina + 1} de {paginas}</span>
              <button type="button" className="us-action-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
            </div>
          )}
        </div>

        {/* ── Formulario ── */}
        <div className="us-panel us-panel--form">
          <h2 className="us-panel__title">Crear nuevo usuario</h2>

          {success && (
            <div className="us-toast us-toast--success">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="20 6 9 17 4 12" /></svg>
              Usuario creado correctamente
            </div>
          )}

          <form onSubmit={handleSubmit} className="us-form">
            <div className="us-field">
              <label className="us-label">Nombre Completo</label>
              <input
                className="us-input"
                type="text"
                placeholder="Ej: Juan Pérez"
                value={nuevoUsuario.nombre}
                onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, nombre: e.target.value })}
                required
              />
            </div>

            <div className="us-field">
              <label className="us-label">Correo Electrónico</label>
              <input
                className="us-input"
                type="email"
                placeholder="email@kinetic.com"
                value={nuevoUsuario.correo}
                onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, correo: e.target.value })}
                required
              />
            </div>

            <div className="us-field">
              <label className="us-label">Contraseña Temporal</label>
              <div className="us-input-wrap">
                <input
                  className="us-input"
                  type={showPassword ? "text" : "password"}
                  placeholder="••••••••"
                  value={nuevoUsuario.password}
                  onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, password: e.target.value })}
                  required
                />
                <button
                  type="button"
                  className="us-eye"
                  onClick={() => setShowPassword((p) => !p)}
                  tabIndex={-1}
                >
                  {showPassword ? (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" /><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" /><line x1="1" y1="1" x2="23" y2="23" /></svg>
                  ) : (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" /></svg>
                  )}
                </button>
              </div>
            </div>

            <div className="us-field">
              <label className="us-label">Rol del Sistema</label>
              <div className="us-select-wrap">
                <select
                  className="us-select"
                  value={nuevoUsuario.rol}
                  onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, rol: e.target.value })}
                >
                  <option value="ADMIN">Administrador</option>
                  <option value="SUPERADMIN">Superadmin</option>
                </select>
                <svg className="us-select-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9" /></svg>
              </div>
            </div>

            <button type="submit" className="us-btn-submit">
              Registrar Usuario
            </button>

            <p className="us-hint">
              Aquí solo se crean cuentas del equipo (administrador o superadmin). Las tiendas y los talleres se registran
              desde «Registra tu negocio» y se aprueban en Aprobaciones, porque necesitan perfil y aprobación.
            </p>
          </form>
        </div>
      </div>
    </div>

      {/* ── Edit Modal (SUPERADMIN only) ── */}
      {editando && isSuperadmin && (
        <div className="us-modal-overlay" onClick={() => setEditando(null)}>
          <div className="us-modal" onClick={(e) => e.stopPropagation()}>
            <div className="us-modal-header">
              <h3>Editar usuario</h3>
              <button className="us-modal-close" onClick={() => setEditando(null)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
            <form onSubmit={handleEditSubmit} className="us-form">
              <div className="us-field">
                <label className="us-label">Nombre Completo</label>
                <input
                  className="us-input"
                  type="text"
                  value={editForm.nombre}
                  onChange={(e) => setEditForm({ ...editForm, nombre: e.target.value })}
                  required
                />
              </div>
              <div className="us-field">
                <label className="us-label">Correo</label>
                <input
                  className="us-input"
                  type="email"
                  value={editando.correo}
                  disabled
                  style={{ opacity: 0.5 }}
                />
              </div>
              <div className="us-field">
                <label className="us-label">Nueva contraseña <span className="us-hint-inline">(dejar vacío para mantener)</span></label>
                <input
                  className="us-input"
                  type="password"
                  placeholder="••••••••"
                  value={editForm.password}
                  onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
                />
              </div>
              <div className="us-field">
                <label className="us-label">Rol del Sistema</label>
                <div className="us-select-wrap">
                  <select
                    className="us-select"
                    value={editForm.rol}
                    disabled={!rolEditable}
                    onChange={(e) => setEditForm({ ...editForm, rol: e.target.value })}
                  >
                    {!ROLES_DEL_EQUIPO.includes(editando.rol) && (
                      <option value={editando.rol} disabled>{(ROL_CONFIG[editando.rol] || { label: editando.rol }).label}</option>
                    )}
                    <option value="ADMIN">Administrador</option>
                    <option value="SUPERADMIN">Superadmin</option>
                  </select>
                  <svg className="us-select-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9" /></svg>
                </div>
              </div>

              {!rolEditable && (
                <p className="us-hint">El rol de un cliente, una tienda o un taller no se cambia: su perfil depende de él.</p>
              )}
              {editando.rol === "VENDEDOR" && (
                <p className="us-hint">El rol de vendedor se retiró. Reasígnalo a administrador o superadmin, o desactiva la cuenta.</p>
              )}

              {editError && <div className="us-error">{editError}</div>}
              {editSuccess && <div className="us-toast us-toast--success">{editSuccess}</div>}

              <div className="us-modal-actions">
                <button type="button" className="us-btn-cancel" onClick={() => setEditando(null)}>Cancelar</button>
                <button type="submit" className="us-btn-submit">Guardar cambios</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}