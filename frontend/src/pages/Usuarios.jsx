import { useEffect, useState } from "react";
import { crearUsuario, listarUsuarios, eliminarUsuario, getUser, getToken } from "../services/authService";
import "../css/Usuarios.css";

const API_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

const ROL_CONFIG = {
  SUPERADMIN: { label: "SUPER ADMIN", className: "us-badge--superadmin" },
  ADMIN: { label: "ADMIN", className: "us-badge--admin" },
  VENDEDOR: { label: "SELLER", className: "us-badge--vendedor" },
};

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
  const [usuarios, setUsuarios] = useState([]);
  const [nuevoUsuario, setNuevoUsuario] = useState({
    nombre: "",
    correo: "",
    password: "",
    rol: "VENDEDOR",
  });
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [filterRol, setFilterRol] = useState("TODOS");

  const currentUser = getUser();
  const isSuperadmin = currentUser?.rol === "SUPERADMIN";

  const [editando, setEditando] = useState(null);
  const [editForm, setEditForm] = useState({ nombre: "", rol: "", password: "" });
  const [editError, setEditError] = useState(null);
  const [editSuccess, setEditSuccess] = useState(null);

  useEffect(() => {
    if (!token) return;
    listarUsuarios(token)
      .then(setUsuarios)
      .catch((err) => setError(err.message));
  }, [token]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    try {
      await crearUsuario(nuevoUsuario, token);
      setNuevoUsuario({ nombre: "", correo: "", password: "", rol: "VENDEDOR" });
      const updated = await listarUsuarios(token);
      setUsuarios(updated);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err) {
      setError(err.message || "Error al crear usuario");
    }
  };

  const handleEditClick = (u) => {
    setEditando(u);
    setEditForm({ nombre: u.nombre || "", rol: u.rol || "VENDEDOR", password: "" });
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
      const res = await fetch(`${API_BASE}/usuarios/${editando.id}`, {
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
      const updated = await listarUsuarios(token);
      setUsuarios(updated);
      setTimeout(() => { setEditando(null); setEditSuccess(null); }, 1500);
    } catch (err) {
      setEditError(err.message);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("¿Desactivar este usuario? Esta acción no se puede deshacer.")) return;
    try {
      await eliminarUsuario(id);
      const updated = await listarUsuarios(token);
      setUsuarios(updated);
    } catch (err) {
      setError(err.message);
    }
  };

  const conteo = {
    SUPERADMIN: usuarios.filter((u) => u.rol === "SUPERADMIN").length,
    ADMIN: usuarios.filter((u) => u.rol === "ADMIN").length,
    VENDEDOR: usuarios.filter((u) => u.rol === "VENDEDOR").length,
  };

  const usuariosFiltrados =
    (filterRol === "TODOS" ? usuarios : usuarios.filter((u) => u.rol === filterRol))
      .filter((u) => u.rol !== "CLIENTE");

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

      {/* ── Stat cards ── */}
      <div className="us-stats">
        <div className="us-stat us-stat--superadmin">
          <div className="us-stat__icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>
          </div>
          <span className="us-stat__priority">PRIORIDAD ALTA</span>
          <p className="us-stat__label">Super Admins</p>
          <p className="us-stat__value">{String(conteo.SUPERADMIN).padStart(2, "0")}</p>
          <div className="us-stat__bar us-stat__bar--superadmin" />
        </div>

        <div className="us-stat us-stat--admin">
          <div className="us-stat__icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" /></svg>
          </div>
          <p className="us-stat__label">Administradores</p>
          <p className="us-stat__value">{String(conteo.ADMIN).padStart(2, "0")}</p>
          <div className="us-stat__bar us-stat__bar--admin" />
        </div>

        <div className="us-stat us-stat--vendedor">
          <div className="us-stat__icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="7" width="20" height="14" rx="2" /><path d="M16 7V5a2 2 0 0 0-4 0v2M8 7V5a2 2 0 0 0-4 0v2" /></svg>
          </div>
          <p className="us-stat__label">Vendedores</p>
          <p className="us-stat__value">{String(conteo.VENDEDOR).padStart(2, "0")}</p>
          <div className="us-stat__bar us-stat__bar--vendedor" />
        </div>
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
                value={filterRol}
                onChange={(e) => setFilterRol(e.target.value)}
              >
                <option value="TODOS">Filtrar por rol</option>
                <option value="SUPERADMIN">Super Admin</option>
                <option value="ADMIN">Admin</option>
                <option value="VENDEDOR">Vendedor</option>
              </select>
            </div>
          </div>

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
                            <button className="us-action-btn us-action-btn--delete" title="Desactivar" onClick={() => handleDelete(u.id)}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>
                              </svg>
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
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
                  <option value="VENDEDOR">Vendedor</option>
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
              Se enviará un correo de invitación al usuario para activar su cuenta y establecer su contraseña definitiva.
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
                    onChange={(e) => setEditForm({ ...editForm, rol: e.target.value })}
                  >
                    <option value="VENDEDOR">Vendedor</option>
                    <option value="ADMIN">Administrador</option>
                    <option value="SUPERADMIN">Superadmin</option>
                  </select>
                  <svg className="us-select-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="6 9 12 15 18 9" /></svg>
                </div>
              </div>

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