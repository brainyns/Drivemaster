import { useState, useEffect, useRef } from "react";
import Logo from "./Logo";
import Icon from "./Icon";
import ThemeToggle from "../ThemeToggle";
import SolicitudDrawer from "../SolicitudDrawer";
import { getUser, actualizarMiNombre } from "../../services/authService";
import "../../css/navbar.css";

/**
 * Header del sitio público. Mantiene la lógica del navbar anterior (carrito de
 * solicitud, menú de usuario, editar nombre y toggle de tema) y adopta la
 * composición del mockup: logo · nav · acciones. La búsqueda vive en la barra
 * de herramientas del catálogo, igual que en el prototipo.
 */
export default function Navbar({
  onIrLogin,
  onIrAdmin,
  onIrSolicitud,
  onIrCatalogo,
  onIrInicio,
  onLogout,
  solicitudItems,
  solicitudCount,
  solicitudSubtotal,
  solicitudIva,
  solicitudTotal,
  solicitudAbierta,
  onToggleSolicitud,
  onUpdateQuantity,
  onRemoveProduct,
  theme,
  onToggleTheme,
  vehiculoActivo,
}) {
  const user = getUser();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [mobileAbierto, setMobileAbierto] = useState(false);
  const [editandoNombre, setEditandoNombre] = useState(false);
  const [nuevoNombre, setNuevoNombre] = useState(user?.nombre || "");
  const [mensaje, setMensaje] = useState(null);
  const menuRef = useRef(null);

  useEffect(() => {
    const onClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuAbierto(false);
    };
    if (menuAbierto) document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [menuAbierto]);

  useEffect(() => {
    document.body.style.overflow = mobileAbierto ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileAbierto]);

  const irA = (destino) => {
    setMenuAbierto(false);
    setMobileAbierto(false);
    if (typeof destino === "string") onIrSolicitud?.(destino);
    else onIrCatalogo?.();
  };

  const handleCartClick = () => {
    if (!user) {
      (onIrLogin || onIrAdmin)?.();
      return;
    }
    onToggleSolicitud?.();
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

  const enlaces = [
    { label: "Inicio", onClick: () => { setMobileAbierto(false); onIrInicio?.(); } },
    { label: "Catálogo", onClick: () => { setMobileAbierto(false); onIrCatalogo?.(); } },
    { label: "Encuentra tu repuesto", onClick: () => { setMobileAbierto(false); onIrInicio?.("dm-vehicle"); } },
    { label: "Mis vehículos", onClick: () => irA("mis-vehiculos"), soloCliente: true },
    { label: "Cómo funciona", onClick: () => { setMobileAbierto(false); onIrInicio?.("dm-pasos"); } },
  ];

  const enlacesVisibles = enlaces.filter((e) => !e.soloCliente || user?.rol === "CLIENTE");

  return (
    <>
      <header className="dm-header">
        <div className="dm-header__inner">
          <Logo onClick={() => onIrInicio?.()} />

          <nav className="dm-header__nav" aria-label="Navegación principal">
            {enlacesVisibles.map((e) => (
              <button key={e.label} onClick={e.onClick}>{e.label}</button>
            ))}
          </nav>

          <div className="dm-header__actions">
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />

            {user && (
              <button className="dm-header__cart" onClick={handleCartClick} aria-label={`Solicitud, ${solicitudCount} artículos`}>
                <Icon name="box" size={21} />
                {solicitudCount > 0 && <span className="dm-header__cart-badge">{solicitudCount}</span>}
              </button>
            )}

            {user ? (
              <div className="dm-header__user" ref={menuRef}>
                <button
                  className="dm-header__user-btn"
                  onClick={() => setMenuAbierto(!menuAbierto)}
                  aria-expanded={menuAbierto}
                >
                  <Icon name="user" size={16} />
                  <span>{user.nombre?.split(" ")[0] || "Cuenta"}</span>
                  <Icon name="chevron" size={14} />
                </button>

                {menuAbierto && (
                  <div className="dm-header__dropdown">
                    {user.rol === "CLIENTE" && (
                      <>
                        <button onClick={() => irA("mis-pedidos")}>Historial de compras</button>
                        <button onClick={() => irA("mis-solicitudes")}>Mis solicitudes</button>
                        <button onClick={() => irA("mis-vehiculos")}>Mis vehículos</button>
                        <button onClick={() => { setMenuAbierto(false); setEditandoNombre(true); setNuevoNombre(user.nombre); }}>
                          Editar nombre de usuario
                        </button>
                      </>
                    )}
                    {user.rol !== "CLIENTE" && (
                      <button onClick={() => { setMenuAbierto(false); onIrAdmin?.(); }}>
                        {user.rol === "TALLER" ? "Panel de taller" : "Panel de administración"}
                      </button>
                    )}
                    <div className="dm-header__dropdown-divider" />
                    <button className="is-danger" onClick={() => { setMenuAbierto(false); onLogout?.(); }}>
                      Cerrar sesión
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <button className="dm-header__login" onClick={onIrLogin || onIrAdmin}>
                Iniciar sesión <Icon name="user" size={16} />
              </button>
            )}

            <button
              className="dm-btn dm-btn--dark dm-header__cta"
              onClick={() => { setMobileAbierto(false); onIrCatalogo?.(); }}
            >
              Explorar catálogo <Icon name="arrow" size={16} />
            </button>

            <button
              className="dm-icon-btn dm-header__toggle"
              aria-label={mobileAbierto ? "Cerrar menú" : "Abrir menú"}
              aria-expanded={mobileAbierto}
              onClick={() => setMobileAbierto((v) => !v)}
            >
              <Icon name={mobileAbierto ? "close" : "menu"} size={22} />
            </button>
          </div>
        </div>

        {vehiculoActivo && (
          <div className="dm-header__vehicle">
            <Icon name="car" size={15} />
            <span>
              {vehiculoActivo.marca} {vehiculoActivo.modelo} {vehiculoActivo.anio}
              {vehiculoActivo.motor ? ` · ${vehiculoActivo.motor}` : ""}
            </span>
          </div>
        )}
      </header>

      {mobileAbierto && (
        <nav className="dm-mobile-nav" aria-label="Navegación móvil">
          {enlacesVisibles.map((e) => (
            <button key={e.label} onClick={e.onClick}>{e.label}</button>
          ))}
          {user ? (
            <>
              {user.rol === "CLIENTE" && <button onClick={() => irA("mis-pedidos")}>Historial de compras</button>}
              <button onClick={() => { setMobileAbierto(false); onLogout?.(); }}>Cerrar sesión</button>
            </>
          ) : (
            <button onClick={() => { setMobileAbierto(false); onIrLogin?.(); }}>Iniciar sesión</button>
          )}
        </nav>
      )}

      <SolicitudDrawer
        abierto={solicitudAbierta}
        onCerrar={() => onToggleSolicitud?.()}
        onEnviarSolicitud={() => { onToggleSolicitud?.(); onIrSolicitud?.("checkout"); }}
        items={solicitudItems}
        subtotal={solicitudSubtotal}
        iva={solicitudIva}
        total={solicitudTotal}
        onUpdateQuantity={onUpdateQuantity}
        onRemoveProduct={onRemoveProduct}
      />

      {editandoNombre && (
        <div className="dm-modal-overlay" onClick={() => setEditandoNombre(false)}>
          <div className="dm-modal" onClick={(e) => e.stopPropagation()}>
            <div className="dm-modal-header">
              <span className="dm-eyebrow">DRIVEMASTER / PERFIL</span>
              <button className="dm-icon-btn" onClick={() => setEditandoNombre(false)} aria-label="Cerrar">
                <Icon name="close" />
              </button>
            </div>
            <h2>Editar nombre de usuario</h2>
            <label className="dm-form-label">
              Nombre
              <input
                type="text"
                value={nuevoNombre}
                onChange={(e) => setNuevoNombre(e.target.value)}
                autoFocus
              />
            </label>
            {mensaje && <p className="dm-form-error">{mensaje}</p>}
            <button className="dm-btn dm-btn--primary dm-btn--full" onClick={handleGuardarNombre}>
              Guardar
            </button>
          </div>
        </div>
      )}
    </>
  );
}