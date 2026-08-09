import { useState, useEffect, useCallback } from "react";
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
import AdminLogin            from "./pages/AdminLogin";
import Register              from "./pages/Register";
import Usuarios              from "./pages/Usuarios";
import CatalogoPage          from "./pages/CatalogoPage";
import DashboardMain         from "./pages/DashboardMain";
import InventarioClientes    from "./pages/InventarioClientes";
import ReportesClientes      from "./pages/ReportesClientes";
import InventarioProductos   from "./pages/InventarioProductos";
import ReportesProductos     from "./pages/ReportesProductos";
import MisPedidos            from "./pages/MisPedidos";
import MisSolicitudes        from "./pages/MisSolicitudes";
import PagoResultado         from "./pages/PagoResultado";
import CheckoutPage          from "./pages/CheckoutPage";
import SolicitudesAdmin      from "./pages/SolicitudesAdmin";
import PerfilAdmin           from "./pages/PerfilAdmin";
import ChatWidget            from "./components/chat/ChatWidget";
import { getUser, getToken, clearSession, refreshToken, logout } from "./services/authService";

const storedUser  = getUser();
const storedToken = getToken();

const initialPage = window.location.pathname.startsWith("/pago-resultado")
  ? "pago-resultado"
  : "catalogo";

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
  "solicitudes":     "Solicitudes",
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
  "mis-solicitudes": "Mis Solicitudes",
  "pago-resultado":  "Resultado del pago",
  "mis-pedidos":     "Historial de compras",
  "checkout":        "Enviar solicitud",
  "mi-perfil":       "Mi Perfil",
};

const IVA_RATE = 0.19;

function calcularSubtotal(items) {
  return items.reduce((s, i) => s + (i.precioUnitario * i.cantidad), 0);
}

function loadSolicitud() {
  try {
    const saved = localStorage.getItem("solicitudItems");
    return saved ? JSON.parse(saved) : [];
  } catch { return []; }
}

function App() {
  const [pagina,         setPagina]         = useState(initialPage);
  const [user,           setUser]           = useState(storedUser);
  const [token,          setToken]          = useState(storedToken);
  const [idSeleccionado, setIdSeleccionado] = useState(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [solicitudItems, setSolicitudItems] = useState(loadSolicitud);
  const [solicitudAbierta, setSolicitudAbierta] = useState(false);

  const [theme, setTheme] = useState(() => localStorage.getItem("theme") || "dark");

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("theme", theme);
  }, [theme]);

  const toggleTheme = () => setTheme(prev => prev === "dark" ? "light" : "dark");

  const [colorTema, setColorTema] = useState(
    () => localStorage.getItem("colorTema") || "#FF3D00"
  );

  useEffect(() => {
    document.documentElement.style.setProperty("--primary", colorTema);
  }, []);

  const handleColorChange = (c) => {
    setColorTema(c.valor);
    localStorage.setItem("colorTema", c.valor);
    document.documentElement.style.setProperty("--primary",   c.valor);
    document.documentElement.style.setProperty("--primary-h", c.hover);
  };

  useEffect(() => {
    localStorage.setItem("solicitudItems", JSON.stringify(solicitudItems));
  }, [solicitudItems]);

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
    const newUser = {
      id: auth.id,
      nombre: auth.nombre,
      correo: auth.correo,
      rol: auth.rol,
      proveedor: auth.proveedor,
      datosCompletos: auth.datosCompletos
    };
    setUser(newUser);
    setToken(auth.token);
    setSessionExpired(false);

    if (auth.rol === "CLIENTE") {
      setPagina("catalogo");
    } else {
      setPagina("dashboard-main");
    }
  };

  const handleLogout = async () => {
    await logout();
    setUser(null); setToken(null);
    setPagina("catalogo");
    setSolicitudItems([]);
    setSessionExpired(false);
  };

  const handleCompraExitosa = () => {
    setSolicitudItems([]);
    setPagina("catalogo");
  };

  const addProduct = useCallback((producto, cantidad) => {
    setSolicitudItems(prev => {
      const idx = prev.findIndex(i => i.productoId === producto.id);
      if (idx >= 0) {
        const next = [...prev];
        const item = { ...next[idx] };
        item.cantidad += cantidad;
        next[idx] = item;
        return next;
      }
      return [...prev, {
        productoId: producto.id,
        nombre: producto.nombre,
        imagenUrl: producto.imagenUrl || "",
        precioUnitario: producto.precioVenta,
        cantidad,
        tipo: producto.tipo || "STOCK",
        stockActual: producto.stockActual ?? 0,
      }];
    });
  }, []);

  const updateQuantity = useCallback((productoId, cantidad) => {
    setSolicitudItems(prev =>
      prev.map(i => i.productoId === productoId ? { ...i, cantidad } : i)
    );
  }, []);

  const removeProduct = useCallback((productoId) => {
    setSolicitudItems(prev => prev.filter(i => i.productoId !== productoId));
  }, []);

  const removeProductList = useCallback((productIds) => {
    setSolicitudItems(prev => prev.filter(i => !productIds.includes(i.productoId)));
  }, []);

  const solicitudCount = solicitudItems.reduce((s, i) => s + i.cantidad, 0);
  const solicitudSubtotal = calcularSubtotal(solicitudItems);
  const solicitudIva = solicitudSubtotal * IVA_RATE;
  const solicitudTotal = solicitudSubtotal + solicitudIva;

  const allowedPages = {
    VENDEDOR: [
      "dashboard-main",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "solicitudes",
      "mi-perfil",
    ],
    ADMIN: [
      "dashboard-main",
      "productos", "productoNuevo", "productoEditar",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "solicitudes",
      "compras", "compraNueva", "compraDetalle",
      "proveedores", "proveedorNuevo", "proveedorEditar",
      "movimientos",
      "inv-clientes", "rep-clientes", "inv-productos", "rep-productos",
      "mi-perfil",
    ],
    SUPERADMIN: [
      "dashboard-main",
      "productos", "productoNuevo", "productoEditar",
      "clientes", "clienteNuevo", "clienteEditar",
      "ventas", "ventaNueva", "ventaDetalle",
      "solicitudes",
      "compras", "compraNueva", "compraDetalle",
      "proveedores", "proveedorNuevo", "proveedorEditar",
      "movimientos",
      "inv-clientes", "rep-clientes", "inv-productos", "rep-productos",
      "usuarios",
      "mi-perfil",
    ],
    CLIENTE: ["catalogo", "mis-pedidos", "mis-solicitudes", "checkout"],
  };

  const renderPagina = () => {
    if (pagina === "catalogo") return (
      <CatalogoPage
        onIrLogin={() => setPagina("login")}
        onIrAdmin={() => setPagina("dashboard-main")}
        onIrSolicitud={(p) => setPagina(p || "checkout")}
        onIrCheckout={() => setPagina("checkout")}
        onLogout={handleLogout}
        solicitudItems={solicitudItems}
        solicitudCount={solicitudCount}
        solicitudSubtotal={solicitudSubtotal}
        solicitudIva={solicitudIva}
        solicitudTotal={solicitudTotal}
        solicitudAbierta={solicitudAbierta}
        onToggleSolicitud={() => setSolicitudAbierta(o => !o)}
        onAddProduct={addProduct}
        onUpdateQuantity={updateQuantity}
        onRemoveProduct={removeProduct}
        onClearSolicitud={() => setSolicitudItems([])}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
    );

    if (pagina === "checkout") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      if (user.rol === "CLIENTE") {
        return <CheckoutPage
          onCompraExitosa={handleCompraExitosa}
          onVolver={() => setPagina("catalogo")}
          solicitudItems={solicitudItems}
          solicitudSubtotal={solicitudSubtotal}
          solicitudIva={solicitudIva}
          solicitudTotal={solicitudTotal}
          onClearSolicitud={() => setSolicitudItems([])}
          onRemoveProductList={removeProductList}
        />;
      }
      return (
        <div className="app-layout">
          <Sidebar paginaActual={pagina} irA={irA} userRole={user.rol} />
          <main className="app-content">
            <TopBar titulo="Enviar solicitud" user={user} onLogout={handleLogout}
              colorTema={colorTema} theme={theme} onToggleTheme={toggleTheme} onColorChange={handleColorChange} />
            <CheckoutPage
              onCompraExitosa={handleCompraExitosa}
              solicitudItems={solicitudItems}
              solicitudSubtotal={solicitudSubtotal}
              solicitudIva={solicitudIva}
              solicitudTotal={solicitudTotal}
              onClearSolicitud={() => setSolicitudItems([])}
              onRemoveProductList={removeProductList}
            />
          </main>
        </div>
      );
    }

    if (pagina === "mis-solicitudes") return <MisSolicitudes onVolver={() => setPagina("catalogo")} />;

    if (pagina === "pago-resultado") return (
      <PagoResultado
        onPagoExitoso={() => setSolicitudItems([])}
        onIrSolicitudes={() => setPagina("mis-solicitudes")}
      />
    );

    if (pagina === "mis-pedidos") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return <MisPedidos onVolver={() => setPagina("catalogo")} />;
    }

    if (pagina === "admin-login") {
      return <AdminLogin onLogin={handleLogin} onVolver={() => setPagina("catalogo")} />;
    }

    if (!user) {
      if (pagina === "register") return <Register onRegister={handleLogin} onBack={() => setPagina("login")} />;
      return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} onGoAdminLogin={() => setPagina("admin-login")} />;
    }

    if (sessionExpired) return (
      <div style={{ padding: "40px", textAlign: "center" }}>
        <h2>Sesión expirada</h2>
        <p>Tu sesión ha expirado. Inicia sesión nuevamente.</p>
        <button onClick={() => { clearSession(); setPagina("login"); }}>Volver al login</button>
      </div>
    );

    if (!allowedPages[user.rol]?.includes(pagina)) return (
      <div style={{ padding: "40px" }}>Acceso no autorizado a esta sección.</div>
    );

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

    if (pagina === "solicitudes")      return <SolicitudesAdmin     token={token} />;

    if (pagina === "compras")          return <Compras              onNueva={() => irA("compraNueva")} onDetalle={id => irA("compraDetalle", id)} token={token} />;
    if (pagina === "compraNueva")      return <CompraForm           onVolver={() => irA("compras")} token={token} />;
    if (pagina === "compraDetalle")    return <CompraDetalle        id={idSeleccionado} onVolver={() => irA("compras")} token={token} />;

    if (pagina === "proveedores")      return <Proveedores          onNuevo={() => irA("proveedorNuevo")} onEditar={id => irA("proveedorEditar", id)} token={token} />;
    if (pagina === "proveedorNuevo")   return <ProveedorForm        onVolver={() => irA("proveedores")} token={token} />;
    if (pagina === "proveedorEditar")  return <ProveedorForm        id={idSeleccionado} onVolver={() => irA("proveedores")} token={token} />;

    if (pagina === "movimientos")      return <Movimientos          token={token} />;
    if (pagina === "usuarios")         return <Usuarios             token={token} />;
    if (pagina === "mi-perfil")        return <PerfilAdmin          />;

    if (pagina === "inv-clientes")     return <InventarioClientes   token={token} />;
    if (pagina === "rep-clientes")     return <ReportesClientes     token={token} />;
    if (pagina === "inv-productos")    return <InventarioProductos  token={token} />;
    if (pagina === "rep-productos")    return <ReportesProductos    token={token} />;

    return <div style={{ padding: "40px" }}>Seleccione una sección válida.</div>;
  };

  const esCliente = user?.rol === "CLIENTE";
  const usarPanel = pagina === "catalogo" || (esCliente && ["checkout", "mis-pedidos", "mis-solicitudes"].includes(pagina)) ? false
    : !!user && pagina !== "catalogo";

  return (
    <div className={usarPanel ? "app-layout" : ""}>
      {usarPanel && (
        <Sidebar
          paginaActual={pagina}
          irA={irA}
          userRole={user?.rol}
        />
      )}

      <main className={usarPanel ? "app-content" : ""}>
        {usarPanel && (
          <TopBar
            titulo={TITULOS[pagina] ?? "DriveMaster"}
            user={user}
            onLogout={handleLogout}
            onNavigate={irA}
            colorTema={colorTema}
            theme={theme}
            onToggleTheme={toggleTheme}
            onColorChange={handleColorChange}
          />
        )}

        {renderPagina()}
      </main>

      <ChatWidget />
    </div>
  );
}

export default App;
