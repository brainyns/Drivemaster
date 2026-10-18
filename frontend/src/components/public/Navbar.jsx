import { useState, useEffect } from "react";
import Logo from "../Logo";
import Icon from "./Icon";
import ThemeToggle from "../ThemeToggle";
import MenuUsuario from "./MenuUsuario";
import CampanitaCliente from "../CampanitaCliente";
import { OPCIONES_CLIENTE } from "./opcionesCliente";
import { getUser } from "../../services/authService";
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
  onSolicitarDiagnostico,
  onRegistroNegocio,
  onIrMisServicios,
  onIrMisCompras,
  onAbrirAviso,
  carritoCount = 0,
  onIrCarrito,
  theme,
  onToggleTheme,
  vehiculoActivo,
  activo,
}) {
  const user = getUser();
  const [mobileAbierto, setMobileAbierto] = useState(false);

  useEffect(() => {
    document.body.style.overflow = mobileAbierto ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [mobileAbierto]);

  // Accesos de la cuenta: Mis compras y Mis servicios tienen su propio manejo (sesión y retorno)
  const irA = (destino) => {
    setMobileAbierto(false);
    if (destino === "mis-compras") onIrMisCompras?.();
    else if (destino === "mis-servicios") onIrMisServicios?.();
    else onIrSolicitud?.(destino);
  };

  const cerrarY = (accion) => () => { setMobileAbierto(false); accion?.(); };

  // clave: la vista con la que se marca como activa (aria-current); secundario: se oculta en pantallas medianas;
  // soloSinSesion: registrar un negocio crea una cuenta nueva, así que no se ofrece a quien ya inició sesión
  const enlaces = [
    { label: "Inicio", clave: "inicio", onClick: cerrarY(() => onIrInicio?.()) },
    { label: "Solicitar diagnóstico", clave: "diagnostico", onClick: cerrarY(onSolicitarDiagnostico), secundario: true },
    { label: "Cómo funciona", onClick: cerrarY(() => onIrInicio?.("dm-pasos")), secundario: true },
    { label: "Catálogo", clave: "catalogo", onClick: cerrarY(onIrCatalogo) },
    { label: "Registra tu negocio", clave: "negocio", onClick: cerrarY(onRegistroNegocio), destacado: true, soloSinSesion: true },
    { label: "Mis servicios", clave: "servicios", onClick: cerrarY(onIrMisServicios), soloCliente: true },
  ];
  const actual = (e) => (e.clave && e.clave === activo ? "page" : undefined);
  const claseEnlace = (e) => [e.destacado && "dm-header__nav-negocio", e.secundario && "dm-header__nav-secundario"]
    .filter(Boolean).join(" ") || undefined;

  const enlacesVisibles = enlaces.filter((e) =>
    (!e.soloCliente || user?.rol === "CLIENTE") && (!e.soloSinSesion || !user));

  return (
    <>
      <header className="dm-header">
        <div className="dm-header__inner">
          <Logo onClick={() => onIrInicio?.()} />

          <nav className="dm-header__nav" aria-label="Navegación principal">
            {enlacesVisibles.map((e) => (
              <button key={e.label} onClick={e.onClick} className={claseEnlace(e)} aria-current={actual(e)}>{e.label}</button>
            ))}
          </nav>

          <div className="dm-header__actions">
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />

            <button className="dm-header__cart" onClick={() => onIrCarrito?.()}
              aria-label={`Carrito, ${carritoCount} ${carritoCount === 1 ? "artículo" : "artículos"}`}>
              <Icon name="box" size={21} />
              {carritoCount > 0 && <span className="dm-header__cart-badge">{carritoCount}</span>}
            </button>

            {user?.rol === "CLIENTE" && <CampanitaCliente onAbrir={onAbrirAviso} />}

            {user ? (
              <MenuUsuario onIr={irA} onIrAdmin={onIrAdmin} onLogout={onLogout} />
            ) : (
              <button className="dm-header__login" onClick={onIrLogin || onIrAdmin}>
                Iniciar sesión <Icon name="user" size={16} />
              </button>
            )}

            <button
              className="dm-btn dm-btn--dark dm-header__cta"
              onClick={cerrarY(onSolicitarDiagnostico)}
            >
              Solicitar diagnóstico <Icon name="arrow" size={16} />
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
          {/* Los accesos de la cuenta van abajo, con la misma lista del menú de usuario */}
          {enlacesVisibles.filter((e) => !e.soloCliente).map((e) => (
            <button key={e.label} onClick={e.onClick} aria-current={actual(e)}>{e.label}</button>
          ))}
          {user ? (
            <>
              {user.rol === "CLIENTE" && OPCIONES_CLIENTE.map((o) => (
                <button key={o.destino} onClick={() => irA(o.destino)}>{o.label}</button>
              ))}
              <button onClick={() => { setMobileAbierto(false); onLogout?.(); }}>Cerrar sesión</button>
            </>
          ) : (
            <button onClick={() => { setMobileAbierto(false); onIrLogin?.(); }}>Iniciar sesión</button>
          )}
        </nav>
      )}
    </>
  );
}