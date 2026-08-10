import { useState, useEffect, useRef, useCallback } from "react";
import NotificationDropdown from "./NotificationDropdown";
import { getToken } from "../services/authService";
import { connectNotifications, disconnectNotifications, subscribeNotifications } from "../services/websocketService";
import "../css/topbar.css";

const COLORES_TEMA = [
  { label: "Naranja", valor: "#FF3D00", hover: "#e63600" },
  { label: "Azul",    valor: "#2563EB", hover: "#1d4ed8" },
  { label: "Violeta", valor: "#7C3AED", hover: "#6d28d9" },
  { label: "Verde",   valor: "#059669", hover: "#047857" },
  { label: "Rosa",    valor: "#DB2777", hover: "#be185d" },
  { label: "Cyan",    valor: "#0891B2", hover: "#0e7490" },
  { label: "Ámbar",   valor: "#D97706", hover: "#b45309" },
];

const IcoSettings = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3"/>
    <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>
  </svg>
);

const IcoLogout = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/>
    <polyline points="16 17 21 12 16 7"/>
    <line x1="21" y1="12" x2="9" y2="12"/>
  </svg>
);

const IcoUser = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>
    <circle cx="12" cy="7" r="4"/>
  </svg>
);

const IcoSun = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="5"/>
    <line x1="12" y1="1"  x2="12" y2="3"/>
    <line x1="12" y1="21" x2="12" y2="23"/>
    <line x1="4.22" y1="4.22"   x2="5.64" y2="5.64"/>
    <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
    <line x1="1"  y1="12" x2="3"  y2="12"/>
    <line x1="21" y1="12" x2="23" y2="12"/>
    <line x1="4.22" y1="19.78"  x2="5.64" y2="18.36"/>
    <line x1="18.36" y1="5.64"  x2="19.78" y2="4.22"/>
  </svg>
);

const IcoMoon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/>
  </svg>
);

const getRolLabel = (rol) => {
  if (rol === "SUPERADMIN") return "Superadmin";
  if (rol === "ADMIN")      return "Administrador";
  return "Vendedor";
};

const getIniciales = (nombre) => {
  if (!nombre) return "U";
  return nombre.split(" ").map(x => x[0]).join("").toUpperCase().slice(0, 2);
};

// ── Dropdown Configuración ────────────────────────────────────────────────────
function DropdownConfig({ onClose, colorActual, onColorChange, theme, onToggleTheme }) {
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) onClose(); };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  const isDark = theme === "dark";

  return (
    <div className="tb-dropdown tb-dropdown--config" ref={ref}>
      <div className="tb-dropdown-header">
        <span className="tb-dropdown-title">Personalización</span>
        <span className="tb-dropdown-sub">Apariencia del sistema</span>
      </div>

      {/* Toggle dark / light */}
      <div className="tb-section tb-section--border">
        <p className="tb-section-label">Modo de interfaz</p>
        <div className="tb-theme-toggle">
          <button
            className={`tb-theme-opt${isDark ? " tb-theme-opt--active" : ""}`}
            onClick={() => { if (!isDark) onToggleTheme(); }}
          >
            <span className="tb-theme-ico"><IcoMoon /></span>
            <span>Oscuro</span>
          </button>
          <button
            className={`tb-theme-opt${!isDark ? " tb-theme-opt--active" : ""}`}
            onClick={() => { if (isDark) onToggleTheme(); }}
          >
            <span className="tb-theme-ico"><IcoSun /></span>
            <span>Claro</span>
          </button>
        </div>
      </div>

      {/* Selector de color */}
      <div className="tb-section">
        <p className="tb-section-label">Color principal</p>
        <div className="tb-colors-grid">
          {COLORES_TEMA.map(c => (
            <button
              key={c.valor}
              className={`tb-color-btn${colorActual === c.valor ? " tb-color-btn--active" : ""}`}
              style={{ "--c": c.valor }}
              onClick={() => onColorChange(c)}
              title={c.label}
            >
              <span className="tb-color-swatch" />
              {colorActual === c.valor && <span className="tb-color-check">✓</span>}
            </button>
          ))}
        </div>
        <p className="tb-color-name">
          {COLORES_TEMA.find(c => c.valor === colorActual)?.label ?? "Personalizado"}
        </p>
      </div>

      <div className="tb-dropdown-footer">
        <div className="tb-preview-bar" style={{ background: colorActual }} />
        <span className="tb-preview-label">Vista previa del color activo</span>
      </div>
    </div>
  );
}

// ── Dropdown Perfil ───────────────────────────────────────────────────────────
function DropdownPerfil({ user, onClose, onLogout, onNavigate }) {
  const ref = useRef(null);

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) onClose(); };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  return (
    <div className="tb-dropdown tb-dropdown--perfil" ref={ref}>
      <div className="tb-perfil-head">
        <div className="tb-perfil-avatar">{getIniciales(user?.nombre)}</div>
        <div className="tb-perfil-info">
          <p className="tb-perfil-nombre">{user?.nombre || "Usuario"}</p>
          <p className="tb-perfil-rol">{getRolLabel(user?.rol)}</p>
          {user?.correo && <p className="tb-perfil-correo">{user.correo}</p>}
        </div>
      </div>

      <div className="tb-dropdown-divider" />

      <div className="tb-perfil-menu">
        <button className="tb-perfil-item" onClick={() => { onClose(); onNavigate?.("mi-perfil"); }}>
          <span className="tb-perfil-item-ico"><IcoUser /></span>
          <span>Mi perfil</span>
        </button>
      </div>

      <div className="tb-dropdown-divider" />

      <button className="tb-perfil-logout" onClick={() => { onClose(); onLogout(); }}>
        <span className="tb-perfil-item-ico"><IcoLogout /></span>
        <span>Cerrar sesión</span>
      </button>
    </div>
  );
}

// ── TopBar principal ──────────────────────────────────────────────────────────
function TopBar({
  titulo,
  subtitulo,
  tabs,
  tabActiva,
  onTab,
  busqueda,
  onBusqueda,
  user,
  onLogout,
  onNavigate,
  colorTema,
  onColorChange,
  theme,
  onToggleTheme,
  children,
}) {
  const [configOpen, setConfigOpen] = useState(false);
  const [perfilOpen, setPerfilOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifCount, setNotifCount] = useState(0);

  const refreshCount = useCallback(async () => {
    try {
      const sols = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/solicitudes`, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${getToken()}`,
        },
      }).then(r => r.json());
      const pending = (sols || []).filter(s => s.estado === "PENDIENTE" || s.estado === "PENDIENTE_PAGO" || s.estado === "PAGO_VERIFICADO").length;
      setNotifCount(pending);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    if (!user || user.rol === "CLIENTE") return;
    const tok = getToken();
    if (!tok) return;
    connectNotifications(tok);
    refreshCount();
    const unsub = subscribeNotifications((msg) => {
      if (msg?.type === "solicitud" || msg?.type === "venta_estado") refreshCount();
    });
    return () => { unsub(); disconnectNotifications(); };
  }, [user, refreshCount]);

  useEffect(() => {
    if (!notifOpen) return;
    refreshCount();
    const interval = setInterval(refreshCount, 30000);
    return () => clearInterval(interval);
  }, [notifOpen, refreshCount]);

  const handleColorChange = (c) => {
    document.documentElement.style.setProperty("--primary",   c.valor);
    document.documentElement.style.setProperty("--primary-h", c.hover);
    onColorChange?.(c);
  };

  return (
    <header className="tb-root">
      <div className="tb-left">
        <span className="tb-brand">DriveMaster</span>
        <span className="tb-divider">|</span>
        <span className="tb-breadcrumb">{titulo}</span>
      </div>

      {tabs && tabs.length > 0 && (
        <div className="tb-tabs">
          {tabs.map(t => (
            <button
              key={t.key}
              className={`tb-tab${tabActiva === t.key ? " tb-tab--active" : ""}`}
              onClick={() => onTab?.(t.key)}
            >
              {t.label}
              {t.dot && <span className="tb-tab-dot" />}
            </button>
          ))}
        </div>
      )}

      <div className="tb-right">
        {onBusqueda !== undefined && (
          <div className="tb-search">
            <svg className="tb-search-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/>
            </svg>
            <input
              className="tb-search-input"
              placeholder={`Buscar ${subtitulo ?? ""}...`}
              value={busqueda}
              onChange={e => onBusqueda(e.target.value)}
            />
          </div>
        )}

        <div className="tb-ico-wrap tb-notif-wrap">
          <button
            className={`tb-ico-btn${notifOpen ? " tb-ico-btn--active" : ""}`}
            title="Notificaciones"
            onClick={() => { setNotifOpen(p => !p); setConfigOpen(false); setPerfilOpen(false); }}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/>
              <path d="M13.73 21a2 2 0 01-3.46 0"/>
            </svg>
          </button>
          {notifCount > 0 && <span className="tb-notif-badge">{notifCount}</span>}
          {notifOpen && (
            <NotificationDropdown
              onClose={() => setNotifOpen(false)}
              onNavigate={(page, id) => onNavigate?.(page, id)}
            />
          )}
        </div>

        <div className="tb-ico-wrap">
          <button
            className={`tb-ico-btn${configOpen ? " tb-ico-btn--active" : ""}`}
            title="Configuración"
            onClick={() => { setConfigOpen(p => !p); setNotifOpen(false); setPerfilOpen(false); }}
          >
            <IcoSettings />
          </button>
          {configOpen && (
            <DropdownConfig
              onClose={() => setConfigOpen(false)}
              colorActual={colorTema}
              onColorChange={handleColorChange}
              theme={theme}
              onToggleTheme={onToggleTheme}
            />
          )}
        </div>

        <div className="tb-ico-wrap">
          <button
            className={`tb-avatar${perfilOpen ? " tb-avatar--active" : ""}`}
            title="Perfil"
            onClick={() => { setPerfilOpen(p => !p); setNotifOpen(false); setConfigOpen(false); }}
          >
            {getIniciales(user?.nombre)}
          </button>
          {perfilOpen && (
            <DropdownPerfil
              user={user}
              onClose={() => setPerfilOpen(false)}
              onLogout={onLogout}
              onNavigate={onNavigate}
            />
          )}
        </div>

        {children}
      </div>
    </header>
  );
}

export default TopBar;