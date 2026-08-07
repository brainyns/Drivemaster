import { useState, useEffect, useRef } from "react";
import "../css/navbar.css";
import ThemeToggle from "./ThemeToggle";
import SolicitudDrawer from "./SolicitudDrawer";
import { getUser, getToken, actualizarMiNombre } from "../services/authService";

const Navbar = ({
  busqueda, onBusquedaChange, onIrLogin, onIrAdmin, onIrSolicitud, onIrCatalogo, onLogout,
  solicitudItems, solicitudCount, solicitudSubtotal, solicitudIva, solicitudTotal,
  solicitudAbierta, onToggleSolicitud,
  onUpdateQuantity, onRemoveProduct,
  theme, onToggleTheme
}) => {
  const user = getUser();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [editandoNombre, setEditandoNombre] = useState(false);
  const [nuevoNombre, setNuevoNombre] = useState(user?.nombre || "");
  const [mensaje, setMensaje] = useState(null);
  const menuRef = useRef();

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuAbierto(false);
    };
    if (menuAbierto) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [menuAbierto]);

  const handleCartClick = () => {
    if (!user) { (onIrLogin || onIrAdmin)(); return; }
    onToggleSolicitud && onToggleSolicitud();
  };

  const handleGuardarNombre = async () => {
    try {
      await actualizarMiNombre(nuevoNombre);
      setEditandoNombre(false);
      setMensaje("Nombre actualizado");
      setTimeout(() => setMensaje(null), 2000);
    } catch (e) {
      setMensaje("Error: " + e.message);
      setTimeout(() => setMensaje(null), 3000);
    }
  };

  return (
    <>
      <nav className="navbar">
        <div className="navbar__logo" onClick={onIrCatalogo} style={{ cursor: "pointer" }}>
          <div className="navbar__logo-icon">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06
                a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09
                A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83
                l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09
                A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83
                l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09
                a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83
                l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09
                a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </div>
          <span className="navbar__logo-text">
            DRIVE<span>MASTER</span>
          </span>
        </div>

        <div className="navbar__search">
          <span className="navbar__search-icon">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </span>
          <input
            className="navbar__search-input"
            type="text"
            placeholder="Buscar repuesto, marca, categoría..."
            value={busqueda}
            onChange={(e) => onBusquedaChange(e.target.value)}
          />
        </div>

        <div className="navbar__actions">
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
          {user && (
            <button className="navbar__solicitud-btn" onClick={handleCartClick} title="Solicitud">
              <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              {solicitudCount > 0 && <span className="navbar__solicitud-badge">{solicitudCount}</span>}
            </button>
          )}
          {user ? (
            <div className="navbar__user-menu" ref={menuRef}>
              <button className="navbar__user-btn" onClick={() => setMenuAbierto(!menuAbierto)}>
                <span className="navbar__user-name">{user.nombre}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </button>
              {menuAbierto && (
                <div className="navbar__user-dropdown">
                  {user.rol === "CLIENTE" && (
                    <>
                      <button className="navbar__dropdown-item" onClick={() => { setMenuAbierto(false); onIrSolicitud("mis-pedidos"); }}>
                        Historial de compras
                      </button>
                      <button className="navbar__dropdown-item" onClick={() => { setMenuAbierto(false); onIrSolicitud("mis-solicitudes"); }}>
                        Mis Solicitudes
                      </button>
                      <button className="navbar__dropdown-item" onClick={() => { setMenuAbierto(false); setEditandoNombre(true); setNuevoNombre(user.nombre); }}>
                        Editar nombre de usuario
                      </button>
                    </>
                  )}
                  {user.rol !== "CLIENTE" && (
                    <button className="navbar__dropdown-item" onClick={() => { setMenuAbierto(false); onIrAdmin && onIrAdmin(); }}>
                      Panel de administración
                    </button>
                  )}
                  <div className="navbar__dropdown-divider" />
                  <button className="navbar__dropdown-item danger" onClick={() => { setMenuAbierto(false); onLogout && onLogout(); }}>
                    Cerrar sesión
                  </button>
                </div>
              )}
            </div>
          ) : (
            <button className="navbar__login-btn" onClick={onIrLogin || onIrAdmin}>
              Iniciar Sesión
            </button>
          )}
        </div>
      </nav>

      <SolicitudDrawer
        abierto={solicitudAbierta}
        onCerrar={() => onToggleSolicitud && onToggleSolicitud()}
        onEnviarSolicitud={() => { onToggleSolicitud && onToggleSolicitud(); onIrSolicitud && onIrSolicitud("checkout"); }}
        items={solicitudItems}
        subtotal={solicitudSubtotal}
        iva={solicitudIva}
        total={solicitudTotal}
        onUpdateQuantity={onUpdateQuantity}
        onRemoveProduct={onRemoveProduct}
      />

      {editandoNombre && (
        <div className="navbar-modal-overlay" onClick={() => setEditandoNombre(false)}>
          <div className="navbar-modal" onClick={(e) => e.stopPropagation()}>
            <h3>Editar nombre de usuario</h3>
            <input
              type="text"
              value={nuevoNombre}
              onChange={(e) => setNuevoNombre(e.target.value)}
              className="navbar-modal-input"
              autoFocus
            />
            {mensaje && <p className="navbar-modal-mensaje">{mensaje}</p>}
            <div className="navbar-modal-actions">
              <button className="navbar-modal-btn primary" onClick={handleGuardarNombre}>Guardar</button>
              <button className="navbar-modal-btn secondary" onClick={() => setEditandoNombre(false)}>Cancelar</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default Navbar;
