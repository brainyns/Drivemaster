import { useState, useEffect } from "react";
import Logo from "../Logo";
import Icon from "../public/Icon";
import ThemeToggle from "../ThemeToggle";
import MenuUsuario from "../public/MenuUsuario";
import CampanitaCliente from "../CampanitaCliente";
import { OPCIONES_CLIENTE } from "../public/opcionesCliente";
import { getUser } from "../../services/authService";
import "../../css/navbar.css";
import "../../css/marketplace.css";

// Página de cada acceso de la cuenta del cliente → clave de "activo" con la que se marca
const CLAVE_DE_DESTINO = {
  "perfil-cliente": "perfil", "mis-compras": "compras", "mis-servicios": "servicios", "mis-vehiculos": "vehiculos",
};

/**
 * Encabezado de las pantallas del catálogo marketplace. Usa el mismo diseño del sitio público
 * (dm-header) con los accesos de esta sección: repuestos de las tiendas, carrito y mis compras.
 * En móvil las opciones pasan a un menú desplegable, igual que en el header público.
 */
export default function MarketHeader({
  activo, cantidadCarrito = 0,
  onIrInicio, onIrCatalogo, onIrCarrito, onIrMisCompras, onIrLogin, onLogout, onSolicitarDiagnostico,
  onIr, onIrAdmin, onAbrirAviso,
  theme, onToggleTheme,
}) {
  const user = getUser();
  const esCliente = user?.rol === "CLIENTE";
  const [mobileAbierto, setMobileAbierto] = useState(false);

  useEffect(() => {
    document.body.style.overflow = mobileAbierto ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileAbierto]);

  // La opción de la vista actual lleva aria-current: la línea naranja queda fija (navbar.css)
  const actual = (clave) => (clave && activo === clave ? "page" : undefined);
  const cerrarY = (accion) => () => { setMobileAbierto(false); accion?.(); };

  // soloCliente: en el menú móvil no se repiten, porque los accesos de la cuenta van abajo
  const enlaces = [
    { label: "Inicio", onClick: cerrarY(() => onIrInicio?.()) },
    { label: "Catálogo", clave: "catalogo", onClick: cerrarY(() => onIrCatalogo?.()) },
    { label: "Mis compras", clave: "compras", onClick: cerrarY(() => onIrMisCompras?.()), soloCliente: true },
    { label: "Solicitar diagnóstico", clave: "diagnostico", onClick: cerrarY(() => onSolicitarDiagnostico?.()) },
    { label: "Mis servicios", clave: "servicios", onClick: cerrarY(() => onIr?.("mis-servicios")), soloCliente: true },
  ].filter((e) => !e.soloCliente || esCliente);

  return (
    <>
      <header className="dm-header mk-header">
        <div className="dm-header__inner">
          <Logo onClick={cerrarY(() => onIrInicio?.())} />

          <nav className="dm-header__nav" aria-label="Navegación del catálogo">
            {enlaces.map((e) => (
              <button key={e.label} aria-current={actual(e.clave)} onClick={e.onClick}>{e.label}</button>
            ))}
          </nav>

          <div className="dm-header__actions">
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />
            <button className="dm-header__cart" onClick={cerrarY(() => onIrCarrito?.())}
              aria-label={`Carrito, ${cantidadCarrito} ${cantidadCarrito === 1 ? "artículo" : "artículos"}`}>
              <Icon name="box" size={21} />
              {cantidadCarrito > 0 && <span className="dm-header__cart-badge">{cantidadCarrito}</span>}
            </button>
            {esCliente && <CampanitaCliente onAbrir={onAbrirAviso} />}
            {user ? (
              <MenuUsuario onIr={onIr} onIrAdmin={onIrAdmin} onLogout={onLogout} />
            ) : (
              <button className="dm-header__login" onClick={() => onIrLogin?.()}>
                Iniciar sesión <Icon name="user" size={16} />
              </button>
            )}

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
      </header>

      {mobileAbierto && (
        <nav className="dm-mobile-nav" aria-label="Navegación móvil">
          {/* Los accesos de la cuenta van abajo, con la misma lista del menú de usuario */}
          {enlaces.filter((e) => !e.soloCliente).map((e) => (
            <button key={e.label} onClick={e.onClick} aria-current={actual(e.clave)}>{e.label}</button>
          ))}
          {user ? (
            <>
              {esCliente && OPCIONES_CLIENTE.map((o) => (
                <button key={o.destino} onClick={cerrarY(() => onIr?.(o.destino))}
                  aria-current={actual(CLAVE_DE_DESTINO[o.destino])}>{o.label}</button>
              ))}
              <button onClick={cerrarY(onLogout)}>Cerrar sesión</button>
            </>
          ) : (
            <button onClick={cerrarY(onIrLogin)}>Iniciar sesión</button>
          )}
        </nav>
      )}
    </>
  );
}
