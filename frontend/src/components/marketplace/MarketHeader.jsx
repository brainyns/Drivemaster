import Logo from "../Logo";
import Icon from "../public/Icon";
import ThemeToggle from "../ThemeToggle";
import MenuUsuario from "../public/MenuUsuario";
import CampanitaCliente from "../CampanitaCliente";
import { getUser } from "../../services/authService";
import "../../css/navbar.css";
import "../../css/marketplace.css";

/**
 * Encabezado de las pantallas del catálogo marketplace. Usa el mismo diseño del sitio público
 * (dm-header) con los accesos de esta sección: repuestos de las tiendas, carrito y mis compras.
 */
export default function MarketHeader({
  activo, cantidadCarrito = 0,
  onIrInicio, onIrCatalogo, onIrCarrito, onIrMisCompras, onIrLogin, onLogout, onSolicitarDiagnostico,
  onIr, onIrAdmin, onAbrirAviso,
  theme, onToggleTheme,
}) {
  const user = getUser();
  const esCliente = user?.rol === "CLIENTE";
  const actual = (clave) => (activo === clave ? "page" : undefined);

  return (
    <header className="dm-header mk-header">
      <div className="dm-header__inner">
        <Logo onClick={() => onIrInicio?.()} />

        {/* La opción de la vista actual lleva aria-current: la línea naranja queda fija (navbar.css) */}
        <nav className="dm-header__nav" aria-label="Navegación del catálogo">
          <button onClick={() => onIrInicio?.()}>Inicio</button>
          <button aria-current={actual("catalogo")} onClick={() => onIrCatalogo?.()}>Catálogo</button>
          {esCliente && (
            <button aria-current={actual("compras")} onClick={() => onIrMisCompras?.()}>Mis compras</button>
          )}
          <button aria-current={actual("diagnostico")} onClick={() => onSolicitarDiagnostico?.()}>Solicitar diagnóstico</button>
          {esCliente && (
            <button aria-current={actual("servicios")} onClick={() => onIr?.("mis-servicios")}>Mis servicios</button>
          )}
        </nav>

        <div className="dm-header__actions">
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
          <button className="dm-header__cart" onClick={() => onIrCarrito?.()}
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
        </div>
      </div>
    </header>
  );
}
