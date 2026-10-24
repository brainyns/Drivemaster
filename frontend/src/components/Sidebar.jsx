import { useState } from "react";
import Logo from "./Logo";
import "../css/sidebar.css";
import Icon from "./public/Icon";

const Icons = {
  dashboard:   <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>,
  productos:   <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 7H4a2 2 0 00-2 2v10a2 2 0 002 2h16a2 2 0 002-2V9a2 2 0 00-2-2z"/><path d="M16 3H8L6 7h12l-2-4z"/></svg>,
  clientes:    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>,
  ventas:      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/></svg>,
  compras:     <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>,
  proveedores: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="1" y="3" width="15" height="13"/><path d="M16 8h4l3 4v4h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>,
  movimientos: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 014-4h14M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 01-4 4H3"/></svg>,
  reportes:    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>,
  admin:       <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>,
  invClientes: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/></svg>,
  repClientes: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>,
  invProductos:<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6M9 12h6M9 15h4"/></svg>,
  repProductos:<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><path d="M12 8v4l3 3"/></svg>,
  taller:      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></svg>,
  repServicios:<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6"/><path d="M9 15l2 2 4-4"/></svg>,
  asistente:   <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 3l1.9 4.6L18.5 9.5l-4.6 1.9L12 16l-1.9-4.6L5.5 9.5l4.6-1.9z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"/></svg>,
  bandeja:     <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M5.5 5h13L21 13v6H3v-6z"/><path d="M3 13h5l1.5 3h5L16 13h5"/></svg>,
  garaje:      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 21V9l9-6 9 6v12"/><path d="M7 21v-8h10v8M7 17h10"/></svg>,
  aprobaciones:<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></svg>,
};

const menu = [
  { key: "dashboard-main", label: "Inicio",   icon: "dashboard"   },
  { key: "productos",      label: "Productos",   icon: "productos"   },
  { key: "clientes",       label: "Clientes",    icon: "clientes"    },
  { key: "pedidos-envios", label: "Pedidos y envíos", icon: "compras" },
  { key: "compras",        label: "Compras",     icon: "compras"     },
  { key: "proveedores",    label: "Proveedores", icon: "proveedores" },
  { key: "movimientos",    label: "Inventario",  icon: "movimientos" },
  { key: "informes-tienda", label: "Informes",    icon: "reportes"    },
  { key: "asistente-tienda", label: "Asistente",  icon: "asistente"   },
  {
    key: "reportes", label: "Reportes", icon: "reportes",
    subItems: [
      { key: "reportes-plataforma", label: "Reportes de plataforma", icon: "repProductos" },
      { key: "rep-servicios",       label: "Reporte de servicios",      icon: "repServicios" },
    ],
  },
  { key: "usuarios", label: "Usuarios", icon: "admin" },
  { key: "admin-talleres", label: "Servicios", icon: "taller" },
  { key: "negocios", label: "Tiendas y talleres", icon: "aprobaciones" },
  { key: "aprobaciones", label: "Aprobaciones", icon: "aprobaciones" },
  {
    key: "finanzas", label: "Finanzas", icon: "reportes",
    subItems: [
      { key: "liquidaciones",    label: "Liquidaciones",      icon: "reportes" },
      { key: "problemas-envios", label: "Problemas de envío", icon: "aprobaciones" },
    ],
  },
  { key: "mi-tienda", label: "Mi tienda", icon: "admin" },
  { key: "pedidos-tienda", label: "Pedidos", icon: "proveedores" },
];

// Taller: del primer aviso a la entrega. "Órdenes anteriores" solo aparece mientras quede alguna por atender.
const MENU_TALLER = [
  { key: "taller-inicio",      label: "Inicio",      icon: "dashboard" },
  { key: "taller-solicitudes", label: "Solicitudes", icon: "bandeja" },
  { key: "taller-trabajos",    label: "Trabajos",    icon: "taller" },
  { key: "taller-cotizaciones", label: "Cotizaciones", icon: "compras" },
  { key: "taller-mitaller",    label: "Mi taller",   icon: "garaje" },
  { key: "taller-informes",     label: "Informes",    icon: "reportes" },
  { key: "taller-anteriores",  label: "Órdenes anteriores", icon: "ventas" },
];

// Tienda del marketplace: sus pedidos, su catálogo y los movimientos de su inventario.
const MENU_TIENDA = ["pedidos-tienda", "productos", "compras", "proveedores", "movimientos", "informes-tienda", "asistente-tienda", "mi-tienda"];

// Operación diaria del ADMIN: aprobar tiendas y talleres, servicios y problemas de envío.
const MENU_ADMIN = ["dashboard-main", "pedidos-envios", "aprobaciones", "admin-talleres", "finanzas"];

// Dentro de "Finanzas": las liquidaciones son solo del SUPERADMIN; los problemas de envío también los resuelve el ADMIN
const SUB_SOLO_SUPERADMIN = ["liquidaciones"];

function Sidebar({ paginaActual, irA, userRole, abierto, onCerrar, resumenTaller }) {
  const [abiertos, setAbiertos] = useState([]);

  const toggleAbierto = (key) => {
    setAbiertos(prev =>
      prev.includes(key) ? prev.filter(k => k !== key) : [...prev, key]
    );
  };

  const handleNav = (key) => {
    irA(key);
    if (onCerrar) onCerrar();
  };

  // Contador junto al nombre: lo que pide acción en esa sección
  const contador = (key) => (userRole === "TALLER" && key === "taller-solicitudes" ? resumenTaller?.porResponder : 0) || 0;

  const visibleMenu = (userRole === "TALLER" ? MENU_TALLER : menu).filter(item => {
    if (userRole === "TALLER") return item.key !== "taller-anteriores" || (resumenTaller?.anteriores || 0) > 0;
    if (userRole === "TIENDA") return MENU_TIENDA.includes(item.key);
    if (item.key === "pedidos-tienda" || item.key === "mi-tienda") return false;
    // ADMIN: solo la operación diaria; el resto del panel es del SUPERADMIN
    if (userRole === "ADMIN") return MENU_ADMIN.includes(item.key);
    // Compras, proveedores e inventario son de cada tienda: el personal interno ya no los ve
    if (["compras", "proveedores", "movimientos", "informes-tienda", "asistente-tienda"].includes(item.key)) return false;
    if (item.key === "admin-talleres" || item.key === "aprobaciones" || item.key === "negocios" || item.key === "finanzas" || item.key === "pedidos-envios") return userRole === "SUPERADMIN";
    if (item.key === "usuarios") return userRole === "SUPERADMIN";
    // VENDEDOR es un rol retirado: solo conserva su inicio (con un aviso)
    if (userRole === "VENDEDOR") return ["dashboard-main"].includes(item.key);
    return true;
  });

  return (
    <>
      <div className={`sidebar-overlay${abierto ? " visible" : ""}`} onClick={onCerrar} />
      <aside className={`sidebar${abierto ? " sidebar--open" : ""}`}>
      {/* Marca */}
      <div className="sidebar-brand">
        <Logo size="sm" onClick={() => handleNav("catalogo")} />
      </div>

      {/* Navegación */}
      <nav className="sidebar-nav">
        {visibleMenu.map(item => {
          const subItems = (item.subItems || []).filter((s) => !SUB_SOLO_SUPERADMIN.includes(s.key) || userRole === "SUPERADMIN");
          const tieneSubItems = subItems.length > 0;
          const estaAbierto   = abiertos.includes(item.key);
          const activo =
            paginaActual === item.key ||
            (tieneSubItems && subItems.some(s => s.key === paginaActual));

          return (
            <div key={item.key}>
              <button
                className={`sidebar-item${activo ? " activo" : ""}`}
                onClick={() => tieneSubItems ? toggleAbierto(item.key) : handleNav(item.key)}
              >
                <span className="sidebar-item-icon">{Icons[item.icon]}</span>
                <span>{item.label}</span>
                {contador(item.key) > 0 && <span className="sidebar-contador" aria-label={`${contador(item.key)} pendientes`}>{contador(item.key)}</span>}
                {tieneSubItems && (
                  <span className={`sidebar-arrow${estaAbierto ? " abierto" : ""}`}>›</span>
                )}
              </button>

              {tieneSubItems && (
                <div className={`sidebar-submenu${estaAbierto ? " abierto" : ""}`}>
                  {subItems.map(sub => (
                    <button
                      key={sub.key}
                      className={`sidebar-subitem${paginaActual === sub.key ? " activo" : ""}`}
                      onClick={() => handleNav(sub.key)}
                    >
                      {sub.icon && (
                        <span className="sidebar-subitem-icon">{Icons[sub.icon]}</span>
                      )}
                      {sub.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      {/* Volver al catálogo */}
      <div className="sidebar-footer">
        <button className="sidebar-catalogo-btn" onClick={() => handleNav("catalogo")}>
          <Icon name="arrow" size={16} className="ui-volver__icono ui-icono-linea" />Volver al catálogo
        </button>
      </div>
      </aside>
    </>
  );
}

export default Sidebar;