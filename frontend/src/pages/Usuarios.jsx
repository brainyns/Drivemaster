import { apiFetch } from "../services/http";
import { useEffect, useState } from "react";
import { crearUsuario, eliminarUsuario, getUser } from "../services/authService";
import { usuariosPlataforma } from "../services/plataformaService";
import DrawerAdmin from "../components/admin/DrawerAdmin";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import "../css/Usuarios.css";
import "../css/admin-base.css";

const API_BASE = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api`;

// Variante del badge (admin-base.css): el equipo en info, los negocios y clientes en neutro
const ROL_CONFIG = {
  SUPERADMIN: { label: "Super admin", variante: "info" },
  ADMIN: { label: "Admin", variante: "info" },
  TIENDA: { label: "Tienda", variante: "neutro" },
  TALLER: { label: "Taller", variante: "neutro" },
  CLIENTE: { label: "Cliente", variante: "neutro" },
  MECANICO: { label: "Mecánico", variante: "neutro" },
  // Rol retirado: las cuentas que quedan se siguen listando para poder reasignarlas o desactivarlas
  VENDEDOR: { label: "Vendedor (retirado)", variante: "pendiente" },
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

  // Desactivar pide confirmación en un diálogo del panel (antes, window.confirm)
  const [desactivando, setDesactivando] = useState(null);
  const [desactivandoEnCurso, setDesactivandoEnCurso] = useState(false);
  const confirmarDesactivar = async () => {
    setDesactivandoEnCurso(true);
    try {
      await eliminarUsuario(desactivando.id);
      recargar();
    } catch (err) {
      setError(err.message);
    } finally {
      setDesactivandoEnCurso(false);
      setDesactivando(null);
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
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Usuarios</h1>
          <p className="adm-encabezado__sub">Gestiona los usuarios y roles de acceso al sistema.</p>
        </div>
        {lista.usuarios && <span className="adm-encabezado__dato">{total} {total === 1 ? "usuario" : "usuarios"}</span>}
      </header>

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

      <form className="adm-filtros" role="search" aria-label="Filtros de usuarios"
        onSubmit={(e) => { e.preventDefault(); setPagina(0); setQ(busqueda.trim()); }}>
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" placeholder="Nombre o correo" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </label>
        <label className="adm-campo">
          <span>Rol</span>
          <select value={filterRol} onChange={(e) => { setPagina(0); setFilterRol(e.target.value); }}>
            <option value="TODOS">Todos los roles</option>
            <option value="TIENDA">Tiendas</option>
            <option value="TALLER">Talleres</option>
            <option value="CLIENTE">Clientes</option>
            <option value="ADMIN">Administradores</option>
            <option value="SUPERADMIN">Super Admins</option>
          </select>
        </label>
        <label className="adm-campo">
          <span>Estado</span>
          <select value={filtroEstado} onChange={(e) => { setPagina(0); setFiltroEstado(e.target.value); }}>
            <option value="">Todos</option>
            <option value="activos">Activos</option>
            <option value="inactivos">Inactivos</option>
          </select>
        </label>
        <div className="adm-filtros__acciones">
          <button type="submit" className="adm-btn">Buscar</button>
        </div>
      </form>

      {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}

      <div className="us-main">
        {/* ── Tabla ── */}
        <div>
          <div className="adm-tarjeta" aria-busy={!lista.usuarios}>
            {!lista.usuarios && !error ? (
              <div className="adm-cargando">Cargando usuarios…</div>
            ) : lista.usuarios && usuariosFiltrados.length === 0 ? (
              <p className="adm-vacio">Ningún usuario coincide con los filtros.</p>
            ) : lista.usuarios && (
              <div className="adm-tabla-wrap">
                <table className="adm-tabla adm-tabla--tarjetas">
                  <thead>
                    <tr>
                      <th>Nombre</th><th>Correo</th><th>Rol</th><th>Estado</th>
                      {isSuperadmin && <th className="adm-tabla__der">Acciones</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {usuariosFiltrados.map((u) => {
                      const cfg = ROL_CONFIG[u.rol] || { label: u.rol, variante: "neutro" };
                      return (
                        <tr key={u.id}>
                          <td data-label="Nombre">
                            <div className="us-user">
                              <div className="us-avatar" aria-hidden="true">{getInitials(u.nombre)}</div>
                              <strong>{u.nombre}</strong>
                            </div>
                          </td>
                          <td data-label="Correo">{u.correo}</td>
                          <td data-label="Rol"><span className={`adm-badge adm-badge--${cfg.variante}`}>{cfg.label}</span></td>
                          <td data-label="Estado">
                            <span className={`adm-badge ${u.activo ? "adm-badge--exito" : "adm-badge--neutro"}`}>
                              {u.activo ? "Activo" : "Inactivo"}
                            </span>
                          </td>
                          {isSuperadmin && (
                            <td className="adm-tabla__der adm-tabla__celda--ancha">
                              <div className="adm-tabla__acciones">
                                <button type="button" className="adm-btn adm-btn--chico" onClick={() => handleEditClick(u)}>Editar</button>
                                {u.activo ? (
                                  <button type="button" className="adm-btn adm-btn--chico adm-btn--peligro" onClick={() => setDesactivando(u)}>Desactivar</button>
                                ) : (
                                  <button type="button" className="adm-btn adm-btn--chico adm-btn--primario" onClick={() => handleActivar(u.id)}>Activar</button>
                                )}
                              </div>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          {total > TAMANO && (
            <nav className="adm-paginacion" aria-label="Paginación">
              <button type="button" className="adm-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
              <span>Página {pagina + 1} de {paginas}</span>
              <button type="button" className="adm-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
            </nav>
          )}
        </div>

        {/* ── Formulario ── */}
        <section className="adm-tarjeta" aria-labelledby="us-nuevo-titulo">
          <header className="adm-tarjeta__cabecera">
            <h2 id="us-nuevo-titulo" className="adm-tarjeta__titulo">Crear nuevo usuario</h2>
          </header>
          <form onSubmit={handleSubmit} className="adm-tarjeta__cuerpo adm-drawer__bloque">
            {success && <p className="adm-alerta adm-alerta--exito" role="status">Usuario creado correctamente.</p>}

            <label className="adm-campo adm-campo--bloque">
              <span>Nombre completo</span>
              <input type="text" placeholder="Ej: Juan Pérez" value={nuevoUsuario.nombre}
                onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, nombre: e.target.value })} required />
            </label>

            <label className="adm-campo adm-campo--bloque">
              <span>Correo electrónico</span>
              <input type="email" placeholder="email@drivemaster.co" value={nuevoUsuario.correo}
                onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, correo: e.target.value })} required />
            </label>

            <div className="adm-campo adm-campo--bloque">
              <label htmlFor="us-password">Contraseña temporal</label>
              <div className="us-input-wrap">
                <input id="us-password" className="us-input" type={showPassword ? "text" : "password"} placeholder="••••••••"
                  value={nuevoUsuario.password} onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, password: e.target.value })} required />
                <button type="button" className="us-eye" onClick={() => setShowPassword((p) => !p)}
                  aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}>
                  {showPassword ? (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" /><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" /><line x1="1" y1="1" x2="23" y2="23" /></svg>
                  ) : (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" /></svg>
                  )}
                </button>
              </div>
            </div>

            <label className="adm-campo adm-campo--bloque">
              <span>Rol del sistema</span>
              <select value={nuevoUsuario.rol} onChange={(e) => setNuevoUsuario({ ...nuevoUsuario, rol: e.target.value })}>
                <option value="ADMIN">Administrador</option>
                <option value="SUPERADMIN">Superadmin</option>
              </select>
            </label>

            <button type="submit" className="adm-btn adm-btn--primario adm-btn--bloque">Registrar usuario</button>

            <p className="adm-encabezado__sub adm-campo__ayuda">
              Aquí solo se crean cuentas del equipo (administrador o superadmin). Las tiendas y los talleres se registran
              desde «Registra tu negocio» y se aprueban en Aprobaciones, porque necesitan perfil y aprobación.
            </p>
          </form>
        </section>
      </div>

      {/* ── Edición (solo SUPERADMIN) ── */}
      {editando && isSuperadmin && (
        <DrawerAdmin
          titulo="Editar usuario"
          encabezado={(
            <span className={`adm-badge adm-badge--${(ROL_CONFIG[editando.rol] || { variante: "neutro" }).variante}`}>
              {(ROL_CONFIG[editando.rol] || { label: editando.rol }).label}
            </span>
          )}
          onCerrar={() => setEditando(null)}
          pie={(
            <>
              <button type="button" className="adm-btn" onClick={() => setEditando(null)}>Cancelar</button>
              <button type="submit" form="us-editar" className="adm-btn adm-btn--primario">Guardar cambios</button>
            </>
          )}
        >
          <form id="us-editar" onSubmit={handleEditSubmit} className="adm-drawer__bloque">
            <label className="adm-campo adm-campo--bloque">
              <span>Nombre completo</span>
              <input type="text" value={editForm.nombre} onChange={(e) => setEditForm({ ...editForm, nombre: e.target.value })} required />
            </label>
            <label className="adm-campo adm-campo--bloque">
              <span>Correo</span>
              <input type="email" value={editando.correo} disabled />
            </label>
            <label className="adm-campo adm-campo--bloque">
              <span>Nueva contraseña <span className="adm-campo__ayuda">(déjala vacía para mantenerla)</span></span>
              <input type="password" placeholder="••••••••" value={editForm.password}
                onChange={(e) => setEditForm({ ...editForm, password: e.target.value })} />
            </label>
            <label className="adm-campo adm-campo--bloque">
              <span>Rol del sistema</span>
              <select value={editForm.rol} disabled={!rolEditable} onChange={(e) => setEditForm({ ...editForm, rol: e.target.value })}>
                {!ROLES_DEL_EQUIPO.includes(editando.rol) && (
                  <option value={editando.rol} disabled>{(ROL_CONFIG[editando.rol] || { label: editando.rol }).label}</option>
                )}
                <option value="ADMIN">Administrador</option>
                <option value="SUPERADMIN">Superadmin</option>
              </select>
            </label>

            {!rolEditable && (
              <p className="adm-alerta">El rol de un cliente, una tienda o un taller no se cambia: su perfil depende de él.</p>
            )}
            {editando.rol === "VENDEDOR" && (
              <p className="adm-alerta adm-alerta--aviso">El rol de vendedor se retiró. Reasígnalo a administrador o superadmin, o desactiva la cuenta.</p>
            )}
            {editError && <p className="adm-alerta adm-alerta--error" role="alert">{editError}</p>}
            {editSuccess && <p className="adm-alerta adm-alerta--exito" role="status">{editSuccess}</p>}
          </form>
        </DrawerAdmin>
      )}

      {desactivando && (
        <ConfirmacionAdmin
          titulo={`Desactivar a ${desactivando.nombre}`}
          texto="La cuenta no podrá iniciar sesión. Puedes volver a activarla desde esta lista."
          confirmar="Desactivar"
          variante="peligro"
          cargando={desactivandoEnCurso}
          onConfirmar={confirmarDesactivar}
          onCancelar={() => setDesactivando(null)}
        />
      )}
    </div>
  );
}
