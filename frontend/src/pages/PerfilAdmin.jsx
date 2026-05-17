import { useState, useEffect } from "react";
import { getToken, getUser } from "../services/authService";
import { getPerfil, cambiarPassword } from "../services/perfilService";
import "../css/perfil.css";

const ROL_LABELS = {
  SUPERADMIN: "Superadmin",
  ADMIN: "Administrador",
  VENDEDOR: "Vendedor",
};

const ROL_CLASSES = {
  SUPERADMIN: "pf-rol--superadmin",
  ADMIN: "pf-rol--admin",
  VENDEDOR: "pf-rol--vendedor",
};

function getInitials(nombre) {
  return (nombre || "").split(" ").map((x) => x[0]).join("").toUpperCase().slice(0, 2);
}

function formatCOP(valor) {
  if (valor == null) return "$0";
  return new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(valor);
}

function formatFecha(iso) {
  if (!iso) return "No disponible";
  const d = new Date(iso);
  return d.toLocaleDateString("es-CO", {
    year: "numeric", month: "long", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

export default function PerfilAdmin() {
  const token = getToken();
  const user = getUser();
  const [perfil, setPerfil] = useState(null);
  const [loading, setLoading] = useState(true);

  const [passActual, setPassActual] = useState("");
  const [passNueva, setPassNueva] = useState("");
  const [passConfirm, setPassConfirm] = useState("");
  const [passMsg, setPassMsg] = useState(null);
  const [passError, setPassError] = useState(null);
  const [showPass, setShowPass] = useState(false);

  useEffect(() => {
    if (!token) return;
    getPerfil(token)
      .then(setPerfil)
      .catch(() => setPerfil(null))
      .finally(() => setLoading(false));
  }, [token]);

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setPassMsg(null);
    setPassError(null);
    if (passNueva !== passConfirm) {
      setPassError("Las contraseñas no coinciden");
      return;
    }
    if (passNueva.length < 6) {
      setPassError("La nueva contraseña debe tener al menos 6 caracteres");
      return;
    }
    try {
      const res = await cambiarPassword(token, passActual, passNueva);
      setPassMsg(res.message || "Contraseña actualizada");
      setPassActual(""); setPassNueva(""); setPassConfirm("");
      setTimeout(() => setPassMsg(null), 3000);
    } catch (err) {
      setPassError(err.message);
    }
  };

  if (loading) return <div className="pf-root"><div className="pf-loading">Cargando perfil...</div></div>;
  if (!perfil) return <div className="pf-root"><div className="pf-loading">No se pudo cargar el perfil</div></div>;

  const stats = perfil.stats || {};
  const isAdminOrSuper = user?.rol === "ADMIN" || user?.rol === "SUPERADMIN";
  const isVendedor = user?.rol === "VENDEDOR";
  const isSuper = user?.rol === "SUPERADMIN";

  return (
    <div className="pf-root">
      <div className="pf-header">
        <h1 className="pf-title">Mi Perfil</h1>
        <p className="pf-subtitle">Información personal y actividad en el sistema</p>
      </div>

      <div className="pf-grid">
        {/* ── Personal Info Card ── */}
        <div className="pf-card pf-card--info">
          <div className="pf-card-header">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/>
            </svg>
            <h2>Información Personal</h2>
          </div>
          <div className="pf-avatar-row">
            <div className={`pf-avatar ${ROL_CLASSES[user?.rol] || ""}`}>
              {getInitials(perfil.nombre)}
            </div>
            <div className="pf-avatar-info">
              <p className="pf-name">{perfil.nombre}</p>
              <span className={`pf-rol-badge ${ROL_CLASSES[perfil.rol] || ""}`}>
                {ROL_LABELS[perfil.rol] || perfil.rol}
              </span>
            </div>
          </div>
          <div className="pf-fields">
            <div className="pf-field">
              <span className="pf-field-label">Correo electrónico</span>
              <span className="pf-field-value">{perfil.correo}</span>
            </div>
            <div className="pf-field">
              <span className="pf-field-label">Rol del sistema</span>
              <span className={`pf-rol-badge ${ROL_CLASSES[perfil.rol] || ""}`}>
                {ROL_LABELS[perfil.rol] || perfil.rol}
              </span>
            </div>
            <div className="pf-field">
              <span className="pf-field-label">Miembro desde</span>
              <span className="pf-field-value">{formatFecha(perfil.fechaCreacion)}</span>
            </div>
          </div>
        </div>

        {/* ── Security Card ── */}
        <div className="pf-card pf-card--security">
          <div className="pf-card-header">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0110 0v4"/>
            </svg>
            <h2>Seguridad</h2>
          </div>

          <div className="pf-field">
            <span className="pf-field-label">Último acceso</span>
            <span className="pf-field-value">{formatFecha(perfil.ultimoLogin)}</span>
          </div>

          <form onSubmit={handleChangePassword} className="pf-pass-form">
            <h3 className="pf-pass-title">Cambiar contraseña</h3>

            <div className="pf-field">
              <label className="pf-field-label">Contraseña actual</label>
              <div className="pf-pass-input-wrap">
                <input
                  type={showPass ? "text" : "password"}
                  className="pf-pass-input"
                  value={passActual}
                  onChange={(e) => setPassActual(e.target.value)}
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <div className="pf-field">
              <label className="pf-field-label">Nueva contraseña</label>
              <div className="pf-pass-input-wrap">
                <input
                  type={showPass ? "text" : "password"}
                  className="pf-pass-input"
                  value={passNueva}
                  onChange={(e) => setPassNueva(e.target.value)}
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <div className="pf-field">
              <label className="pf-field-label">Confirmar nueva contraseña</label>
              <div className="pf-pass-input-wrap">
                <input
                  type={showPass ? "text" : "password"}
                  className="pf-pass-input"
                  value={passConfirm}
                  onChange={(e) => setPassConfirm(e.target.value)}
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <label className="pf-show-pass">
              <input type="checkbox" checked={showPass} onChange={() => setShowPass((p) => !p)} />
              Mostrar contraseñas
            </label>

            {passMsg && <div className="pf-msg pf-msg--success">{passMsg}</div>}
            {passError && <div className="pf-msg pf-msg--error">{passError}</div>}

            <button type="submit" className="pf-btn-primary">
              Actualizar contraseña
            </button>
          </form>
        </div>
      </div>

      {/* ── Activity Stats ── */}
      <div className="pf-section">
        <div className="pf-card-header">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
          <h2>Actividad</h2>
        </div>

        <div className="pf-stats-grid">
          {isAdminOrSuper && (
            <>
              <div className="pf-stat pf-stat--orange">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Ventas realizadas</p>
                <p className="pf-stat-value">{stats.totalVentas || 0}</p>
              </div>
              <div className="pf-stat pf-stat--green">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Solicitudes aprobadas</p>
                <p className="pf-stat-value">{stats.solicitudesAprobadas || 0}</p>
              </div>
              <div className="pf-stat pf-stat--blue">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Total generado</p>
                <p className="pf-stat-value">{formatCOP(stats.totalIngresos)}</p>
              </div>
              <div className="pf-stat pf-stat--purple">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M20 7H4a2 2 0 00-2 2v10a2 2 0 002 2h16a2 2 0 002-2V9a2 2 0 00-2-2z"/><path d="M16 3H8L6 7h12l-2-4z"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Productos agregados</p>
                <p className="pf-stat-value">{stats.productosAgregados || stats.totalProductos || 0}</p>
              </div>
            </>
          )}

          {isVendedor && (
            <>
              <div className="pf-stat pf-stat--blue">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Clientes atendidos</p>
                <p className="pf-stat-value">{stats.clientesAtendidos || 0}</p>
              </div>
              <div className="pf-stat pf-stat--green">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Ventas del día</p>
                <p className="pf-stat-value">{stats.ventasDelDia || 0}</p>
              </div>
              <div className="pf-stat pf-stat--orange">
                <div className="pf-stat-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/>
                  </svg>
                </div>
                <p className="pf-stat-label">Ventas realizadas</p>
                <p className="pf-stat-value">{stats.ventasRealizadas || 0}</p>
              </div>
            </>
          )}

          {isSuper && (
            <div className="pf-stat pf-stat--red">
              <div className="pf-stat-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
              </div>
              <p className="pf-stat-label">Staff del sistema</p>
              <p className="pf-stat-value">{stats.totalUsuariosStaff || 0}</p>
            </div>
          )}
        </div>

        <div className="pf-stats-secondary">
          <div className="pf-stat-simple">
            <span className="pf-stat-simple-label">Stock bajo</span>
            <span className="pf-stat-simple-value pf-badge--danger">{stats.stockBajo || 0} productos</span>
          </div>
          <div className="pf-stat-simple">
            <span className="pf-stat-simple-label">Solicitudes pendientes</span>
            <span className="pf-stat-simple-value pf-badge--warn">{stats.solicitudesPendientes || 0} solicitudes</span>
          </div>
          <div className="pf-stat-simple">
            <span className="pf-stat-simple-label">Clientes registrados</span>
            <span className="pf-stat-simple-value pf-badge--info">{stats.totalClientes || 0} clientes</span>
          </div>
        </div>
      </div>
    </div>
  );
}
