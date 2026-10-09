import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import Icon from "./Icon";
import EditarNombre from "./EditarNombre";
import { getUser } from "../../services/authService";
import { OPCIONES_CLIENTE } from "./opcionesCliente";
import "../../css/menu-usuario.css";

const ETIQUETA_PANEL = { TALLER: "Panel de taller", TIENDA: "Panel de tienda" };

/**
 * Menú de la cuenta en la barra superior (Navbar del sitio y MarketHeader del catálogo).
 * Patrón de menú: el foco entra al abrir, flechas arriba y abajo para moverse, Escape
 * cierra y devuelve el foco al botón, y salir con Tab o hacer clic fuera también cierra.
 *
 * onIr(destino) recibe el nombre de la página (ver opcionesCliente.js).
 */
export default function MenuUsuario({ onIr, onIrAdmin, onLogout }) {
  const user = getUser();
  const [abierto, setAbierto] = useState(false);
  const [editandoNombre, setEditandoNombre] = useState(false);
  const raizRef = useRef(null);
  const botonRef = useRef(null);
  const menuRef = useRef(null);
  const id = useId();

  const elementos = () => [...(menuRef.current?.querySelectorAll('[role="menuitem"]') ?? [])];

  // El foco entra al primer elemento al abrir
  useEffect(() => {
    if (abierto) menuRef.current?.querySelector('[role="menuitem"]')?.focus();
  }, [abierto]);

  useEffect(() => {
    if (!abierto) return;
    const onClickFuera = (e) => {
      if (raizRef.current && !raizRef.current.contains(e.target)) setAbierto(false);
    };
    document.addEventListener("mousedown", onClickFuera);
    return () => document.removeEventListener("mousedown", onClickFuera);
  }, [abierto]);

  if (!user) return null;
  const esCliente = user.rol === "CLIENTE";

  const cerrarYVolver = () => { setAbierto(false); botonRef.current?.focus(); };
  const elegir = (accion) => () => { setAbierto(false); accion?.(); };

  const onTeclaBoton = (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); setAbierto(true); }
  };

  const onTeclaMenu = (e) => {
    const lista = elementos();
    const i = lista.indexOf(document.activeElement);
    if (e.key === "ArrowDown") { e.preventDefault(); lista[(i + 1) % lista.length]?.focus(); }
    else if (e.key === "ArrowUp") { e.preventDefault(); lista[(i - 1 + lista.length) % lista.length]?.focus(); }
    else if (e.key === "Home") { e.preventDefault(); lista[0]?.focus(); }
    else if (e.key === "End") { e.preventDefault(); lista[lista.length - 1]?.focus(); }
    else if (e.key === "Escape") { e.preventDefault(); cerrarYVolver(); }
    else if (e.key === "Tab" && e.shiftKey) { e.preventDefault(); cerrarYVolver(); }
  };

  // Tab hacia adelante: el foco sale del menú y este se cierra
  const onSalirFoco = (e) => {
    if (abierto && !raizRef.current?.contains(e.relatedTarget)) setAbierto(false);
  };

  return (
    <div className="um" ref={raizRef} onBlur={onSalirFoco}>
      <button
        ref={botonRef}
        id={`${id}-boton`}
        className="um__boton"
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-controls={abierto ? `${id}-menu` : undefined}
        aria-label={`${user.nombre?.split(" ")[0] || "Cuenta"}, menú de la cuenta`}
        onClick={() => setAbierto((v) => !v)}
        onKeyDown={onTeclaBoton}
      >
        <Icon name="user" size={16} />
        <span className="um__boton-nombre">{user.nombre?.split(" ")[0] || "Cuenta"}</span>
        <Icon name="chevron" size={14} className="um__chevron" />
      </button>

      {abierto && (
        <div className="um__panel">
          <div className="um__cabecera">
            <span className="um__nombre">{user.nombre || "Mi cuenta"}</span>
            {user.correo && <span className="um__correo">{user.correo}</span>}
          </div>
          <ul className="um__lista" role="menu" id={`${id}-menu`} aria-labelledby={`${id}-boton`}
            ref={menuRef} onKeyDown={onTeclaMenu}>
            {esCliente && OPCIONES_CLIENTE.map((o) => (
              <li role="none" key={o.destino}>
                <button role="menuitem" tabIndex={-1} className="um__item" onClick={elegir(() => onIr?.(o.destino))}>
                  <Icon name={o.icono} size={17} className="um__icono" />
                  {o.label}
                </button>
              </li>
            ))}
            {esCliente && (
              <li role="none">
                <button role="menuitem" tabIndex={-1} className="um__item" onClick={elegir(() => setEditandoNombre(true))}>
                  <Icon name="user" size={17} className="um__icono" />
                  Editar nombre de usuario
                </button>
              </li>
            )}
            {!esCliente && (
              <li role="none">
                <button role="menuitem" tabIndex={-1} className="um__item" onClick={elegir(onIrAdmin)}>
                  <Icon name="layers" size={17} className="um__icono" />
                  {ETIQUETA_PANEL[user.rol] || "Panel de administración"}
                </button>
              </li>
            )}
            <li role="separator" className="um__divisor" />
            <li role="none">
              <button role="menuitem" tabIndex={-1} className="um__item um__item--salir" onClick={elegir(onLogout)}>
                <Icon name="arrow" size={17} className="um__icono" />
                Cerrar sesión
              </button>
            </li>
          </ul>
        </div>
      )}

      {/* Fuera de la cabecera: su backdrop-filter recortaría la ventana fija */}
      {editandoNombre && createPortal(
        <EditarNombre nombreInicial={user.nombre} onCerrar={() => setEditandoNombre(false)} />,
        document.body,
      )}
    </div>
  );
}
