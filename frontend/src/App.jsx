import { useState, useEffect } from "react";
import Sidebar               from "./components/Sidebar";
import TopBar                from "./components/TopBar";
import Productos             from "./pages/Productos";
import ProductoForm          from "./pages/ProductoForm";
import AjusteInventarioForm  from "./pages/AjusteInventarioForm";
import Clientes              from "./pages/Clientes";
import ClienteForm           from "./pages/ClienteForm";
import Ventas                from "./pages/Ventas";
import VentaForm             from "./pages/VentaForm";
import VentaDetalle          from "./pages/VentaDetalle";
import Compras               from "./pages/Compras";
import CompraForm            from "./pages/CompraForm";
import CompraDetalle         from "./pages/CompraDetalle";
import Proveedores           from "./pages/Proveedores";
import ProveedorForm         from "./pages/ProveedorForm";
import Movimientos           from "./pages/Movimientos";
import Login                 from "./pages/Login";
import Register              from "./pages/Register";
import Usuarios              from "./pages/Usuarios";
import CatalogoPage          from "./pages/CatalogoPage";
import DashboardMain         from "./pages/DashboardMain";
import InventarioClientes    from "./pages/InventarioClientes";
import ReportesClientes      from "./pages/ReportesClientes";
import InventarioProductos   from "./pages/InventarioProductos";
import ReportesProductos     from "./pages/ReportesProductos";
import ChatWidget            from "./components/chat/ChatWidget";
import { getUser, getToken, clearSession, refreshToken, logout } from "./services/authService";

const storedUser  = getUser();
const storedToken = getToken();

const initialPage = storedUser ? "dashboard-main" : "catalogo";

// ── Mapa de títulos por página ─────────────────────────────────────────────
const TITULOS = {
  "dashboard-main":  "Dashboard",
  "productos":       "Gestión de Productos",
  "productoNuevo":   "Nuevo Producto",
  "productoEditar":  "Editar Producto",
  "clientes":        "Gestión de Clientes",
  "clienteNuevo":    "Nuevo Cliente",
  "clienteEditar":   "Editar Cliente",
  "ventas":          "Gestión de Ventas",
  "ventaNueva":      "Nueva Venta",
  "ventaDetalle":    "Detalle de Venta",
  "compras":         "Gestión de Compras",
  "compraNueva":     "Nueva Compra",
  "compraDetalle":   "Detalle de Compra",
  "proveedores":     "Proveedores",
  "proveedorNuevo":  "Nuevo Proveedor",
  "proveedorEditar": "Editar Proveedor",
  "movimientos":     "Inventario",
  "usuarios":        "Usuarios",
  "inv-clientes":    "Inventario Clientes",
  "rep-clientes":    "Reportes Clientes",
  "inv-productos":   "Inventario Productos",
  "rep-productos":   "Reportes Productos",
};

function App() {
  const [pagina,         setPagina]         = useState(initialPage);
  const [user,           setUser]           = useState(storedUser);
  const [token,          setToken]          = useState(storedToken);
  const [idSeleccionado, setIdSeleccionado] = useState(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  // Tema oscuro / claro
  const [theme, setTheme] = useState(() => localStorage.getItem("theme") || "dark");

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("theme", theme);
  }, [theme]);

  const toggleTheme = () => setTheme(prev => prev === "dark" ? "light" : "dark");

  // Color del tema — se persiste en localStorage
  const [colorTema, setColorTema] = useState(
    () => localStorage.getItem("colorTema") || "#FF3D00"
  );

  // Aplicar color guardado al arrancar
  useEffect(() => {
    document.documentElement.style.setProperty("--primary", colorTema);
  }, []);

  const handleColorChange = (c) => {
    setColorTema(c.valor);
    localStorage.setItem("colorTema", c.valor);
    document.documentElement.style.setProperty("--primary",   c.valor);
    document.documentElement.style.setProperty("--primary-h", c.hover);
  };

  // Refresh automático de token
  useEffect(() => {
    if (!user) return;
    const interval = setInterval(async () => {
      try { await refreshToken(); }
      catch { setSessionExpired(true); }
    }, 25 * 60 * 1000);
    return () => clearInterval(interval);
  }, [user]);

  useEffect(() => { if (sessionExpired) handleLogout(); }, [sessionExpired]);

  const irA = (p, id = null) => { setIdSeleccionado(id); setPagina(p); };

  const handleLogin = (auth) => {
    const newUser = { id: auth.id, nombre: auth.nombre, correo: auth.correo, rol: auth.rol };
    setUser(newUser);
    setToken(auth.token);
    setPagina("dashboard-main");
    setSessionExpired(false);
  };

  const handleLogout = async () => {
    await logout();
    setUser(null); setToken(null);
    setPagina("catalogo");
    setSessionExpired(false);
  };

  const allowedPages = {
    VENDEDOR: [
      "dashboard-main",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
    ],
    ADMIN: [
      "dashboard-main",
      "productos", "productoNuevo", "productoEditar",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "compras", "compraNueva", "compraDetalle",
      "proveedores", "proveedorNuevo", "proveedorEditar",
      "movimientos",
      "inv-clientes", "rep-clientes", "inv-productos", "rep-productos",
    ],
    SUPERADMIN: [
      "dashboard-main",
      "productos", "productoNuevo", "productoEditar",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "compras", "compraNueva", "compraDetalle",
      "proveedores", "proveedorNuevo", "proveedorEditar",
      "movimientos",
      "inv-clientes", "rep-clientes", "inv-productos", "rep-productos",
      "usuarios",
    ],
  };

  const renderPagina = () => {
    // Catálogo público — ahora recibe theme y onToggleTheme
    if (pagina === "catalogo") return (
      <CatalogoPage
        onIrAdmin={() => setPagina("login")}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
    );

    // Auth
    if (!user) {
      if (pagina === "register") return <Register onRegister={handleLogin} onBack={() => setPagina("login")} />;
      return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
    }

    // Sesión expirada
    if (sessionExpired) return (
      <div style={{ padding: "40px", textAlign: "center" }}>
        <h2>Sesión expirada</h2>
        <p>Tu sesión ha expirado. Inicia sesión nuevamente.</p>
        <button onClick={() => { clearSession(); setPagina("login"); }}>Volver al login</button>
      </div>
    );

    // Control de acceso
    if (!allowedPages[user.rol]?.includes(pagina)) return (
      <div style={{ padding: "40px" }}>Acceso no autorizado a esta sección.</div>
    );

    // Páginas
    if (pagina === "dashboard-main")   return <DashboardMain        token={token} />;

    if (pagina === "productos")        return <Productos            onNuevo={() => irA("productoNuevo")} onEditar={id => irA("productoEditar", id)} token={token} />;
    if (pagina === "productoNuevo")    return <ProductoForm         onVolver={() => irA("productos")} token={token} />;
    if (pagina === "productoEditar")   return <ProductoForm         id={idSeleccionado} onVolver={() => irA("productos")} token={token} />;
    if (pagina === "inventarioAjuste") return <AjusteInventarioForm productoId={idSeleccionado} onVolver={() => irA("productos")} token={token} />;

    if (pagina === "clientes")         return <Clientes             onNuevo={() => irA("clienteNuevo")} onEditar={id => irA("clienteEditar", id)} token={token} />;
    if (pagina === "clienteNuevo")     return <ClienteForm          onVolver={() => irA("clientes")} token={token} />;
    if (pagina === "clienteEditar")    return <ClienteForm          id={idSeleccionado} onVolver={() => irA("clientes")} token={token} />;

    if (pagina === "ventas")           return <Ventas               onNueva={() => irA("ventaNueva")} onDetalle={id => irA("ventaDetalle", id)} token={token} />;
    if (pagina === "ventaNueva")       return <VentaForm            onVolver={() => irA("ventas")} token={token} />;
    if (pagina === "ventaDetalle")     return <VentaDetalle         id={idSeleccionado} onVolver={() => irA("ventas")} token={token} />;

    if (pagina === "compras")          return <Compras              onNueva={() => irA("compraNueva")} onDetalle={id => irA("compraDetalle", id)} token={token} />;
    if (pagina === "compraNueva")      return <CompraForm           onVolver={() => irA("compras")} token={token} />;
    if (pagina === "compraDetalle")    return <CompraDetalle        id={idSeleccionado} onVolver={() => irA("compras")} token={token} />;

    if (pagina === "proveedores")      return <Proveedores          onNuevo={() => irA("proveedorNuevo")} onEditar={id => irA("proveedorEditar", id)} token={token} />;
    if (pagina === "proveedorNuevo")   return <ProveedorForm        onVolver={() => irA("proveedores")} token={token} />;
    if (pagina === "proveedorEditar")  return <ProveedorForm        id={idSeleccionado} onVolver={() => irA("proveedores")} token={token} />;

    if (pagina === "movimientos")      return <Movimientos          token={token} />;
    if (pagina === "usuarios")         return <Usuarios             token={token} />;

    if (pagina === "inv-clientes")     return <InventarioClientes   token={token} />;
    if (pagina === "rep-clientes")     return <ReportesClientes     token={token} />;
    if (pagina === "inv-productos")    return <InventarioProductos  token={token} />;
    if (pagina === "rep-productos")    return <ReportesProductos    token={token} />;

    return <div style={{ padding: "40px" }}>Seleccione una sección válida.</div>;
  };

  const mostrarPanel = !!user && pagina !== "catalogo";

  return (
    <div className={mostrarPanel ? "app-layout" : ""}>
      {mostrarPanel && (
        <Sidebar
          paginaActual={pagina}
          irA={irA}
          userRole={user.rol}
        />
      )}

      <main className={mostrarPanel ? "app-content" : ""}>
        {/* TopBar global — visible en todas las páginas del panel */}
        {mostrarPanel && (
          <TopBar
            titulo={TITULOS[pagina] ?? "DriveMaster"}
            user={user}
            onLogout={handleLogout}
            colorTema={colorTema}
            theme={theme}
            onToggleTheme={toggleTheme}
            onColorChange={handleColorChange}
          />
        )}

        {/* Contenido de la página */}
        {renderPagina()}
      </main>

      <ChatWidget />
    </div>
  );
}

export default App;