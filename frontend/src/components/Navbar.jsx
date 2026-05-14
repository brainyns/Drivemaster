import "../css/navbar.css";
import ThemeToggle from "./ThemeToggle";

const Navbar = ({ busqueda, onBusquedaChange, onIrAdmin, theme, onToggleTheme }) => {
  return (
    <nav className="navbar">
      <div className="navbar__logo">
        <div className="navbar__logo-icon">⚙️</div>
        <span className="navbar__logo-text">
          DRIVE<span>MASTER</span>
        </span>
      </div>

      <div className="navbar__search">
        <span className="navbar__search-icon">🔍</span>
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
       
        <button
          className="navbar__login-btn"
          onClick={onIrAdmin}
        >
          Iniciar Sesión
        </button>
      </div>
    </nav>
  );
};

export default Navbar;