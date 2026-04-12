import "../css/navbar.css";

function Navbar({ paginaActual, irA }) {
  const links = [
    { key: "productos",    label: "Productos",    icon: "📦" },
    { key: "clientes",     label: "Clientes",     icon: "👤" },
    { key: "proveedores",  label: "Proveedores",  icon: "🏭" },
    { key: "compras",      label: "Compras",      icon: "🛒" },
    { key: "ventas",       label: "Ventas",       icon: "💰" },
    { key: "movimientos",  label: "Movimientos",  icon: "📊" },
  ];

  return (
    <nav className="navbar">
      <div className="navbar-brand">⚙️ Drive<span>Master</span></div>
      <ul className="navbar-links">
        {links.map(link => (
          <li key={link.key}>
            <button
              onClick={() => irA(link.key)}
              className={paginaActual.startsWith(link.key) ? "active" : ""}
            >
              {link.icon} {link.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export default Navbar;