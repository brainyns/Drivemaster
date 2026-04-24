import { useState, useEffect } from "react";
import Sidebar from "./components/Sidebar";
import Productos from "./pages/Productos";
import ProductoForm from "./pages/ProductoForm";
import AjusteInventarioForm from "./pages/AjusteInventarioForm";
import Clientes from "./pages/Clientes";
import ClienteForm from "./pages/ClienteForm";
import Ventas from "./pages/Ventas";
import VentaForm from "./pages/VentaForm";
import VentaDetalle from "./pages/VentaDetalle";
import Compras from "./pages/Compras";
import CompraForm from "./pages/CompraForm";
import CompraDetalle from "./pages/CompraDetalle";
import Proveedores from "./pages/Proveedores";
import ProveedorForm from "./pages/ProveedorForm";
import Movimientos from "./pages/Movimientos";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Usuarios from "./pages/Usuarios";
import CatalogoPage from "./pages/CatalogoPage";
import { getUser, getToken, clearSession, refreshToken, logout } from "./services/authService";

const storedUser  = getUser();
const storedToken = getToken();

// Sin sesión → catálogo público. Con sesión → panel según rol
const initialPage = storedUser
  ? storedUser.rol === "VENDEDOR" ? "ventas" : "productos"
  : "catalogo";

function App() {
  const [pagina,          setPagina]          = useState(initialPage);
  const [user,            setUser]            = useState(storedUser);
  const [token,           setToken]           = useState(storedToken);
  const [idSeleccionado,  setIdSeleccionado]  = useState(null);
  const [sessionExpired,  setSessionExpired]  = useState(false);

  // Refresco automático de token cada 25 min (solo si hay sesión)
  useEffect(() => {
    if (!user) return;
    const interval = setInterval(async () => {
      try {
        await refreshToken();
      } catch {
        setSessionExpired(true);
      }
    }, 25 * 60 * 1000);
    return () => clearInterval(interval);
  }, [user]);

  useEffect(() => {
    if (sessionExpired) handleLogout();
  }, [sessionExpired]);

  const irA = (p, id = null) => {
    setIdSeleccionado(id);
    setPagina(p);
  };

  const handleLogin = (auth) => {
    const newUser = {
      id:     auth.id,
      nombre: auth.nombre,
      correo: auth.correo,
      rol:    auth.rol,
    };
    setUser(newUser);
    setToken(auth.token);
    setPagina(newUser.rol === "VENDEDOR" ? "ventas" : "productos");
    setSessionExpired(false);
  };

  const handleLogout = async () => {
    await logout();
    setUser(null);
    setToken(null);
    setPagina("catalogo"); // al cerrar sesión vuelve al catálogo público
    setSessionExpired(false);
  };

  const allowedPages = {
    VENDEDOR: [
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
    ],
    ADMIN: [
      "productos", "productoNuevo", "productoEditar",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "compras", "compraNueva", "compraDetalle",
      "proveedores", "proveedorNuevo", "proveedorEditar",
      "movimientos", "reportes",
    ],
    SUPERADMIN: [
      "productos", "productoNuevo", "productoEditar",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "compras", "compraNueva", "compraDetalle",
      "proveedores", "proveedorNuevo", "proveedorEditar",
      "movimientos", "reportes", "usuarios",
    ],
  };

  const renderPagina = () => {

    // ── Catálogo público (sin login requerido) ────────────────────
    if (pagina === "catalogo") {
      return (
        <CatalogoPage
          onIrAdmin={() => setPagina("login")}
        />
      );
    }

    // ── Pantallas de auth ─────────────────────────────────────────
    if (!user) {
      if (pagina === "register") {
        return (
          <Register
            onRegister={handleLogin}
            onBack={() => setPagina("login")}
          />
        );
      }
      return (
        <Login
          onLogin={handleLogin}
          onGoRegister={() => setPagina("register")}
          onVolver={() => setPagina("catalogo")}
        />
      );
    }

    // ── Sesión expirada ───────────────────────────────────────────
    if (sessionExpired) {
      return (
        <div style={{ padding: "40px", textAlign: "center" }}>
          <h2>Sesión expirada</h2>
          <p>Tu sesión ha expirado. Por favor, inicia sesión nuevamente.</p>
          <button onClick={() => { clearSession(); setPagina("login"); }}>
            Volver al login
          </button>
        </div>
      );
    }

    // ── Control de acceso por rol ─────────────────────────────────
    if (!allowedPages[user.rol]?.includes(pagina)) {
      return (
        <div style={{ padding: "40px" }}>
          Acceso no autorizado a esta sección.
        </div>
      );
    }

    // ── Panel admin ───────────────────────────────────────────────
    if (pagina === "productos")        return <Productos           onNuevo={() => irA("productoNuevo")} onEditar={(id) => irA("productoEditar", id)} token={token} />;
    if (pagina === "productoNuevo")    return <ProductoForm        onVolver={() => irA("productos")} token={token} />;
    if (pagina === "productoEditar")   return <ProductoForm        id={idSeleccionado} onVolver={() => irA("productos")} token={token} />;
    if (pagina === "inventarioAjuste") return <AjusteInventarioForm productoId={idSeleccionado} onVolver={() => irA("productos")} token={token} />;
    if (pagina === "clientes")         return <Clientes            onNuevo={() => irA("clienteNuevo")} onEditar={(id) => irA("clienteEditar", id)} token={token} />;
    if (pagina === "clienteNuevo")     return <ClienteForm         onVolver={() => irA("clientes")} token={token} />;
    if (pagina === "clienteEditar")    return <ClienteForm         id={idSeleccionado} onVolver={() => irA("clientes")} token={token} />;
    if (pagina === "ventas")           return <Ventas              onNueva={() => irA("ventaNueva")} onDetalle={(id) => irA("ventaDetalle", id)} token={token} />;
    if (pagina === "ventaNueva")       return <VentaForm           onVolver={() => irA("ventas")} token={token} />;
    if (pagina === "ventaDetalle")     return <VentaDetalle        id={idSeleccionado} onVolver={() => irA("ventas")} token={token} />;
    if (pagina === "compras")          return <Compras             onNueva={() => irA("compraNueva")} onDetalle={(id) => irA("compraDetalle", id)} token={token} />;
    if (pagina === "compraNueva")      return <CompraForm          onVolver={() => irA("compras")} token={token} />;
    if (pagina === "compraDetalle")    return <CompraDetalle       id={idSeleccionado} onVolver={() => irA("compras")} token={token} />;
    if (pagina === "proveedores")      return <Proveedores         onNuevo={() => irA("proveedorNuevo")} onEditar={(id) => irA("proveedorEditar", id)} token={token} />;
    if (pagina === "proveedorNuevo")   return <ProveedorForm       onVolver={() => irA("proveedores")} token={token} />;
    if (pagina === "proveedorEditar")  return <ProveedorForm       id={idSeleccionado} onVolver={() => irA("proveedores")} token={token} />;
    if (pagina === "movimientos")      return <Movimientos         token={token} />;
    if (pagina === "usuarios")         return <Usuarios            token={token} />;

    return <div style={{ padding: "40px" }}>Seleccione una sección válida.</div>;
  };

  // Sidebar solo visible en panel admin (usuario autenticado, fuera del catálogo)
  const mostrarSidebar = !!user && pagina !== "catalogo";

  return (
    <div className={mostrarSidebar ? "app-layout" : ""}>
      {mostrarSidebar && (
        <Sidebar
          paginaActual={pagina}
          irA={irA}
          userRole={user.rol}
          onLogout={handleLogout}
        />
      )}
      <main className={mostrarSidebar ? "app-content" : ""}>
        {renderPagina()}
      </main>
    </div>
  );
}

export default App;