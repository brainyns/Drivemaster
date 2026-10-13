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

  return (
    <header className="dm-header mk-header">
      <div className="dm-header__inner">
        <Logo onClick={() => onIrInicio?.()} />

        <nav className="dm-header__nav" aria-label="Navegación del catálogo">
          <button onClick={() => onIrInicio?.()}>Inicio</button>
          <button className={activo === "catalogo" ? "mk-header__activo" : ""} aria-current={activo === "catalogo" ? "page" : undefined}
            onClick={() => onIrCatalogo?.()}>Catálogo</button>
          {esCliente && (
            <button className={activo === "compras" ? "mk-header__activo" : ""} aria-current={activo === "compras" ? "page" : undefined}
              onClick={() => onIrMisCompras?.()}>Mis compras</button>
          )}
          <button onClick={() => onSolicitarDiagnostico?.()}>Solicitar diagnóstico</button>
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
