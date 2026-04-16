import { useState } from "react";
import "../css/sidebar.css";

// SVG icons inline - no need for image files
const Icons = {
  productos:    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 7H4a2 2 0 00-2 2v10a2 2 0 002 2h16a2 2 0 002-2V9a2 2 0 00-2-2z"/><path d="M16 3H8L6 7h12l-2-4z"/></svg>,
  clientes:     <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>,
  inventario:   <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6M9 12h6M9 15h4"/></svg>,
  ventas:       <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"/></svg>,
  compras:      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>,
  proveedores:  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="1" y="3" width="15" height="13"/><path d="M16 8h4l3 4v4h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>,
  reportes:     <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>,
  movimientos:  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 014-4h14M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 01-4 4H3"/></svg>,
  admin:        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>,
};

const menu = [
  { key: "ventas",      label: "Ventas",      icon: "ventas"      },
  { key: "clientes",    label: "Clientes",    icon: "clientes"    },
  { key: "productos",   label: "Productos",   icon: "productos",
    subItems: [
      { key: "productoNuevo", label: "Agregar Producto" },
      { key: "productos",     label: "Ver Productos" },
    ]
  },
  { key: "movimientos", label: "Inventario",  icon: "movimientos" },
  { key: "compras",     label: "Compras",     icon: "compras"     },
  { key: "proveedores", label: "Proveedores", icon: "proveedores" },
  { key: "reportes",    label: "Reportes",    icon: "reportes"    },
  { key: "usuarios",    label: "Usuarios",    icon: "admin"       },
];

function Sidebar({ paginaActual, irA, userRole, onLogout }) {
  const [abiertos, setAbiertos] = useState(["productos"]);

  const toggleAbierto = (key) => {
    setAbiertos((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
  };

  const visibleMenu = menu.filter((item) => {
    if (item.key === "usuarios") {
      return userRole === "SUPERADMIN";
    }
    if (userRole === "VENDEDOR") {
      return ["clientes", "ventas"].includes(item.key);
    }
    return true;
  });

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-brand-icon">⚙</div>
        <div>
          <p className="sidebar-brand-name">DriveMaster</p>
          <p className="sidebar-brand-sub">Automotive Engine</p>
        </div>
      </div>

      <nav className="sidebar-nav">
        {visibleMenu.map((item) => {
          const tieneSubItems = item.subItems?.length > 0;
          const estaAbierto = abiertos.includes(item.key);
          const activo =
            paginaActual === item.key ||
            (tieneSubItems && item.subItems.some((s) => s.key === paginaActual));

          return (
            <div key={item.key + item.label}>
              <button
                className={`sidebar-item ${activo && !tieneSubItems ? "activo" : activo ? "activo" : ""}`}
                onClick={() => (tieneSubItems ? toggleAbierto(item.key) : irA(item.key))}
              >
                <span className="sidebar-item-icon">{Icons[item.icon]}</span>
                <span>{item.label}</span>
                {tieneSubItems && <span className={`sidebar-arrow ${estaAbierto ? "abierto" : ""}`}>›</span>}
              </button>

              {tieneSubItems && (
                <div className={`sidebar-submenu ${estaAbierto ? "abierto" : ""}`}>
                  {item.subItems.map((sub) => (
                    <button
                      key={sub.key + sub.label}
                      className={`sidebar-subitem ${paginaActual === sub.key ? "activo" : ""}`}
                      onClick={() => irA(sub.key)}
                    >
                      {sub.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <div className="sidebar-avatar">A</div>
        <div>
          <p className="sidebar-user-name">{userRole || "Invitado"}</p>
          <p className="sidebar-user-role">{userRole === "SUPERADMIN" ? "Superadmin" : userRole === "ADMIN" ? "Administrador" : "Vendedor"}</p>
        </div>
        <button className="sidebar-logout" onClick={onLogout}>
          Salir
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;