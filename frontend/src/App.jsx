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
import ReportesServicios     from "./pages/ReportesServicios";
import MisPedidos            from "./pages/MisPedidos";
import MisSolicitudes        from "./pages/MisSolicitudes";
import PagoResultado         from "./pages/PagoResultado";
import CheckoutPage          from "./pages/CheckoutPage";
import SolicitudesAdmin      from "./pages/SolicitudesAdmin";
import PerfilAdmin           from "./pages/PerfilAdmin";
import PanelTaller           from "./pages/PanelTaller";
import AdminTalleres         from "./pages/AdminTalleres";
import MisVehiculos          from "./pages/MisVehiculos";
import VehiculoServicio      from "./pages/VehiculoServicio";
import Aprobaciones          from "./pages/Aprobaciones";
import RegistroNegocio       from "./pages/RegistroNegocio";
import SolicitarDiagnostico  from "./pages/SolicitarDiagnostico";
import MisServicios          from "./pages/MisServicios";
import CotizacionPublica     from "./pages/CotizacionPublica";
import PedidosTienda         from "./pages/PedidosTienda";
import Liquidaciones         from "./pages/Liquidaciones";
import ChatWidget            from "./components/chat/ChatWidget";
import { getUser, getToken, clearSession, refreshToken, logout, getTokenExpiracion } from "./services/authService";
import { useCarrito } from "./hooks/useCarrito";
import MarketHeader from "./components/marketplace/MarketHeader";
import CatalogoMarketplace from "./pages/CatalogoMarketplace";
import DetalleProductoMarketplace from "./pages/DetalleProductoMarketplace";
import CarritoMarketplace from "./pages/CarritoMarketplace";
import CheckoutMarketplace from "./pages/CheckoutMarketplace";
import MisCompras from "./pages/MisCompras";
import { consumirPagoPendiente } from "./components/marketplace/pagoPendiente";
import { guardarDestinoPostLogin, consumirDestinoPostLogin, limpiarDestinoPostLogin } from "./services/destinoPostLogin";

const storedUser  = getUser();
const storedToken = getToken();

// /cotizacion: enlace público del correo (el token viaja en el fragmento #token=…)
const initialPage =
  window.location.pathname.startsWith("/cotizacion")
    ? "cotizacion-publica"
    : window.location.pathname.startsWith("/pago-resultado") ||
      new URLSearchParams(window.location.search).get("pago") === "resultado"
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
  "rep-servicios":   "Reporte de Servicios",
  "mis-solicitudes": "Mis Solicitudes",
  "pago-resultado":  "Resultado del pago",
  "mis-pedidos":     "Historial de compras",
  "checkout":        "Enviar solicitud",
  "mi-perfil":       "Mi Perfil",
  "solicitar-diagnostico": "Solicitar diagnóstico",
  "mis-servicios":   "Mis servicios",
  "mis-compras":     "Mis compras",
  "checkout-marketplace": "Entrega y pago",
  "taller-panel":    "Panel de taller",
  "admin-talleres":  "Servicios",
  "aprobaciones":    "Aprobaciones",
  "pedidos-tienda":  "Pedidos",
  "liquidaciones":   "Liquidaciones y reembolsos",
};

// Páginas del cliente que se muestran sin el panel lateral.
const PAGINAS_CLIENTE = [
  "checkout", "mis-pedidos", "mis-solicitudes", "mis-vehiculos", "servicio-vehiculo",
  "solicitar-diagnostico", "mis-servicios", "checkout-marketplace", "mis-compras",
];

// Catálogo marketplace: se consulta sin sesión y no usa el panel lateral aunque haya una.
const PAGINAS_MARKETPLACE = ["marketplace", "producto-marketplace", "carrito-marketplace"];

// Página inicial de cada rol después de iniciar sesión.
const INICIO_POR_ROL = {
  CLIENTE: "catalogo",
  TALLER:  "taller-panel",
  TIENDA:  "productos",
};

const DEFAULT_IVA = 0.19;

// El token se renueva un minuto antes de vencer. La espera mínima evita un ciclo
// de renovaciones seguidas si el token dura menos que el margen.
const MARGEN_RENOVACION_MS = 60 * 1000;
const ESPERA_MINIMA_MS = 5 * 1000;

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
  const [sidebarAbierto, setSidebarAbierto] = useState(false);
  // true cuando el cliente llegó a "Mis servicios" iniciando sesión desde el enlace de la cotización
  const [vieneDeCotizacion, setVieneDeCotizacion] = useState(false);
  const carrito = useCarrito();

  const [theme, setTheme] = useState(() => localStorage.getItem("theme") || "dark");

  const [ivaRate, setIvaRate] = useState(DEFAULT_IVA);

  useEffect(() => {
    const cargarIva = async () => {
      try {
        const res = await fetch(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/config/parametros/IVA`);
        if (res.ok) {
          const p = await res.json();
          const v = parseFloat(p.valor);
          if (!isNaN(v)) setIvaRate(v / 100);
        }
      } catch { /* fallback 19% */ }
    };
    cargarIva();
  }, []);

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

  // Sesión expirada: limpiamos localmente pero conservamos el flag
  // para poder mostrar la pantalla de "sesión expirada".
  const expirarSesion = useCallback(() => {
    clearSession();
    setUser(null);
    setToken(null);
    setSolicitudItems([]);
    setPagina("catalogo");
    setSessionExpired(true);
  }, []);

  // Renueva el token según su vencimiento real. Antes era un intervalo fijo de
  // 25 min que se reiniciaba al recargar la página, y el token vencía antes de
  // renovarse (401 en el pago). Si al cargar ya está vencido, se renueva enseguida.
  useEffect(() => {
    if (!user || !token) return;
    const exp = getTokenExpiracion(token);
    const espera = exp ? Math.max(exp - Date.now() - MARGEN_RENOVACION_MS, ESPERA_MINIMA_MS) : ESPERA_MINIMA_MS;
    const timer = setTimeout(() => {
      refreshToken().catch(expirarSesion);
    }, espera);
    return () => clearTimeout(timer);
  }, [user, token, expirarSesion]);

  // apiFetch avisa cuando renovó el token o cuando la sesión ya no se pudo recuperar.
  useEffect(() => {
    const onRenovada = (e) => setToken(e.detail);
    window.addEventListener("auth:renovada", onRenovada);
    window.addEventListener("auth:expirada", expirarSesion);
    return () => {
      window.removeEventListener("auth:renovada", onRenovada);
      window.removeEventListener("auth:expirada", expirarSesion);
    };
  }, [expirarSesion]);

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

    // Desde el enlace de la cotización: el cliente vuelve a "Mis servicios". El destino se
    // consume siempre (no queda guardado para otro inicio de sesión) y solo vale si el login
    // se hizo desde esa pantalla; otros roles van a su inicio normal.
    const destino = consumirDestinoPostLogin();
    if (destino === "mis-servicios" && pagina === "mis-servicios" && auth.rol === "CLIENTE") {
      setVieneDeCotizacion(true);
      return;
    }
    // Quien inició sesión desde el carrito del catálogo vuelve a él (el carrito sigue guardado)
    if ((destino === "carrito-marketplace" || destino === "checkout-marketplace") && auth.rol === "CLIENTE") {
      setPagina(destino);
      return;
    }

    // Un cliente que inicia sesión desde una página de cliente (ej. Solicitar
    // diagnóstico) se queda en ella en lugar de volver al catálogo.
    if (auth.rol === "CLIENTE" && PAGINAS_CLIENTE.includes(pagina)) return;
    setPagina(INICIO_POR_ROL[auth.rol] || "dashboard-main");
  };

  const handleLogout = async () => {
    await logout();
    limpiarDestinoPostLogin();
    setVieneDeCotizacion(false);
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

  // Fija (no suma) la cantidad de un producto en la compra normal: así pagar dos veces no duplica unidades
  const fijarProducto = useCallback((producto, cantidad) => {
    setSolicitudItems(prev => {
      const item = {
        productoId: producto.id,
        nombre: producto.nombre,
        imagenUrl: producto.imagenUrl || "",
        precioUnitario: producto.precio,
        cantidad,
        tipo: producto.tipo || "STOCK",
        stockActual: producto.stock ?? 0,
      };
      const idx = prev.findIndex(i => i.productoId === producto.id);
      if (idx < 0) return [...prev, item];
      const next = [...prev];
      next[idx] = item;
      return next;
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
  const solicitudIva = solicitudSubtotal * ivaRate;
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
      "inv-clientes", "rep-clientes", "inv-productos", "rep-productos", "rep-servicios",
      "admin-talleres", "aprobaciones", "liquidaciones",
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
      "inv-clientes", "rep-clientes", "inv-productos", "rep-productos", "rep-servicios",
      "usuarios",
      "admin-talleres", "aprobaciones", "liquidaciones",
      "mi-perfil",
    ],
    CLIENTE: ["catalogo", ...PAGINAS_CLIENTE],
    TALLER: ["taller-panel"],
    TIENDA: ["productos", "productoNuevo", "productoEditar", "movimientos", "pedidos-tienda"],
  };
  const esTienda = user?.rol === "TIENDA";

  // Tras un pago aprobado se vacían del carrito solo los productos que se pagaron; el otro grupo se queda
  const vaciarCarritoPagado = () => {
    const pendiente = consumirPagoPendiente();
    if (pendiente) carrito.quitarVarios(pendiente.ids);
  };

  const cabeceraMarketplace = (activo) => (
    <MarketHeader
      activo={activo}
      cantidadCarrito={carrito.cantidad}
      onIrInicio={() => setPagina("catalogo")}
      onIrCatalogo={() => setPagina("marketplace")}
      onIrCarrito={() => setPagina("carrito-marketplace")}
      onIrMisCompras={() => setPagina("mis-compras")}
      onIrLogin={() => setPagina("login")}
      onLogout={handleLogout}
      onSolicitarDiagnostico={() => irA("solicitar-diagnostico")}
      theme={theme}
      onToggleTheme={toggleTheme}
    />
  );

  const renderPagina = () => {
    // Catálogo marketplace: público
    if (pagina === "marketplace") return (
      <CatalogoMarketplace
        header={cabeceraMarketplace("catalogo")}
        onVerProducto={(p) => irA("producto-marketplace", p.id)}
        onAgregar={(p) => carrito.agregar(p, 1)}
      />
    );
    if (pagina === "producto-marketplace") return (
      <DetalleProductoMarketplace
        key={idSeleccionado}
        header={cabeceraMarketplace("catalogo")}
        productoId={idSeleccionado}
        onVolver={() => setPagina("marketplace")}
        onVerProducto={(p) => irA("producto-marketplace", p.id)}
        onAgregar={(p, cantidad) => carrito.agregar(p, cantidad)}
        onIrCarrito={() => setPagina("carrito-marketplace")}
      />
    );
    if (pagina === "carrito-marketplace") return (
      <CarritoMarketplace
        header={cabeceraMarketplace("carrito")}
        carrito={carrito}
        onVolver={() => setPagina("marketplace")}
        onVerProducto={(p) => irA("producto-marketplace", p.id)}
        onIrLogin={() => { guardarDestinoPostLogin("carrito-marketplace"); setPagina("login"); }}
        onContinuarCatalogo={() => setPagina("checkout-marketplace")}
        // Los productos de DriveMaster se pagan por la compra normal, sin perder el resto del carrito
        onPagarPlataforma={(items) => {
          items.forEach((x) => fijarProducto(x.producto, x.cantidad));
          setPagina("checkout");
        }}
      />
    );

    // Pago y compras del catálogo: solo clientes (sin sesión se pide iniciarla y se vuelve aquí)
    if (pagina === "checkout-marketplace" || pagina === "mis-compras") {
      if (!user) {
        return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")}
          onVolver={() => { limpiarDestinoPostLogin(); setPagina("carrito-marketplace"); }} />;
      }
      if (user.rol !== "CLIENTE") return <div style={{ padding: "40px" }}>Esta sección es para clientes.</div>;
      if (pagina === "mis-compras") return (
        <MisCompras
          header={cabeceraMarketplace("compras")}
          onVolver={() => setPagina("catalogo")}
          onIrCatalogo={() => setPagina("marketplace")}
        />
      );
      return (
        <CheckoutMarketplace
          header={cabeceraMarketplace("carrito")}
          carrito={carrito}
          onVolverCarrito={() => setPagina("carrito-marketplace")}
          onPagada={(pagada) => { if (pagada) vaciarCarritoPagado(); setPagina("mis-compras"); }}
        />
      );
    }

    if (pagina === "catalogo") return (
      <CatalogoPage
        onIrLogin={() => setPagina("login")}
        onIrAdmin={() => setPagina(user?.rol === "TALLER" || user?.rol === "TIENDA" ? INICIO_POR_ROL[user.rol] : "dashboard-main")}
        onIrSolicitud={(p) => setPagina(p || "checkout")}
        onIrCheckout={() => setPagina("checkout")}
        onSolicitarDiagnostico={() => irA("solicitar-diagnostico")}
        onRegistroNegocio={() => setPagina("registro-negocio")}
        onIrMarketplace={() => setPagina("marketplace")}
        onIrMisCompras={() => { if (!user) guardarDestinoPostLogin("checkout-marketplace"); setPagina("mis-compras"); }}
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
          <Sidebar paginaActual={pagina} irA={irA} userRole={user.rol}
            abierto={sidebarAbierto} onCerrar={() => setSidebarAbierto(false)} />
          <main className="app-content">
            <TopBar titulo="Enviar solicitud" user={user} onLogout={handleLogout}
              colorTema={colorTema} theme={theme} onToggleTheme={toggleTheme} onColorChange={handleColorChange}
              onMenuClick={() => setSidebarAbierto(true)} />
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
        onIrServicios={() => setPagina("mis-servicios")}
        onCompraCatalogoPagada={vaciarCarritoPagado}
        onIrMisCompras={() => setPagina("mis-compras")}
      />
    );

    // Pública: responder la cotización desde el correo sin iniciar sesión
    if (pagina === "cotizacion-publica") return (
      <CotizacionPublica
        onIrServicios={() => {
          window.history.replaceState(null, "", "/");
          // Sin sesión, "Mis servicios" pide iniciar sesión: al terminar se vuelve aquí
          if (!user) guardarDestinoPostLogin("mis-servicios");
          setPagina("mis-servicios");
        }}
        onIrInicio={() => { window.history.replaceState(null, "", "/"); setPagina("catalogo"); }}
      />
    );

    if (pagina === "mis-pedidos") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return <MisPedidos onVolver={() => setPagina("catalogo")} />;
    }

    if (pagina === "mis-vehiculos") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return (
        <MisVehiculos
          onVolver={() => setPagina("catalogo")}
          onVerServicio={(id) => irA("servicio-vehiculo", id)}
        />
      );
    }

    if (pagina === "servicio-vehiculo") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return (
        <VehiculoServicio
          vehiculoId={idSeleccionado}
          onVolver={() => setPagina("mis-vehiculos")}
          onSolicitarDiagnostico={(id) => irA("solicitar-diagnostico", id)}
        />
      );
    }

    if (pagina === "solicitar-diagnostico" || pagina === "mis-servicios") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")}
        onVolver={() => { limpiarDestinoPostLogin(); setPagina("catalogo"); }} />;
      if (user.rol !== "CLIENTE") return (
        <div style={{ padding: "40px" }}>Esta sección es para clientes. Ingresa con una cuenta de cliente para solicitar un diagnóstico.</div>
      );
      if (pagina === "mis-servicios") return (
        <MisServicios
          onVolver={() => { setVieneDeCotizacion(false); setPagina("catalogo"); }}
          onSolicitar={() => irA("solicitar-diagnostico")}
          avisoCotizacion={vieneDeCotizacion ? user.correo : null}
          onCerrarAviso={() => setVieneDeCotizacion(false)}
        />
      );
      return (
        <SolicitarDiagnostico
          key={idSeleccionado || "nueva"}
          vehiculoIdInicial={idSeleccionado}
          onVolver={() => setPagina("catalogo")}
          onVerServicios={() => irA("mis-servicios")}
          onIrVehiculos={() => irA("mis-vehiculos")}
        />
      );
    }

    if (pagina === "admin-login") {
      return <AdminLogin onLogin={handleLogin} onVolver={() => setPagina("catalogo")} />;
    }

    if (pagina === "registro-negocio") {
      return <RegistroNegocio onIrLogin={() => setPagina("login")} onVolver={() => setPagina("catalogo")} />;
    }

    if (sessionExpired) return (
      <div style={{ padding: "40px", textAlign: "center" }}>
        <h2>Sesión expirada</h2>
        <p>Tu sesión ha expirado. Inicia sesión nuevamente.</p>
        <button onClick={() => { clearSession(); setSessionExpired(false); setPagina("login"); }}>Volver al login</button>
      </div>
    );

    if (!user) {
      if (pagina === "register") return <Register onRegister={handleLogin} onBack={() => setPagina("login")} />;
      return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onGoRegistroNegocio={() => setPagina("registro-negocio")} onVolver={() => { limpiarDestinoPostLogin(); setPagina("catalogo"); }} onGoAdminLogin={() => setPagina("admin-login")} />;
    }

    if (!allowedPages[user.rol]?.includes(pagina)) return (
      <div style={{ padding: "40px" }}>Acceso no autorizado a esta sección.</div>
    );

    if (pagina === "dashboard-main")   return <DashboardMain        token={token} />;

    if (pagina === "productos")        return <Productos            onNuevo={() => irA("productoNuevo")} onEditar={id => irA("productoEditar", id)} token={token} esTienda={esTienda} />;
    if (pagina === "productoNuevo")    return <ProductoForm         onVolver={() => irA("productos")} token={token} esTienda={esTienda} />;
    if (pagina === "productoEditar")   return <ProductoForm         id={idSeleccionado} onVolver={() => irA("productos")} token={token} esTienda={esTienda} />;
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

    if (pagina === "movimientos")      return <Movimientos          token={token} esTienda={esTienda} />;
    if (pagina === "usuarios")         return <Usuarios             token={token} />;
    if (pagina === "mi-perfil")        return <PerfilAdmin          />;
    if (pagina === "taller-panel")     return <PanelTaller          />;
    if (pagina === "admin-talleres")   return <AdminTalleres        />;
    if (pagina === "aprobaciones")     return <Aprobaciones         token={token} />;
    if (pagina === "liquidaciones")    return <Liquidaciones        />;
    if (pagina === "pedidos-tienda")   return <PedidosTienda        />;

    if (pagina === "inv-clientes")     return <InventarioClientes   token={token} />;
    if (pagina === "rep-clientes")     return <ReportesClientes     token={token} />;
    if (pagina === "inv-productos")    return <InventarioProductos  token={token} />;
    if (pagina === "rep-productos")    return <ReportesProductos    token={token} />;
  if (pagina === "rep-servicios")     return <ReportesServicios    token={token} />;

    return <div style={{ padding: "40px" }}>Seleccione una sección válida.</div>;
  };

  const esCliente = user?.rol === "CLIENTE";
  // El registro de negocios es público: nunca va dentro del panel, aunque haya sesión.
  const usarPanel = pagina === "catalogo" || pagina === "registro-negocio" || PAGINAS_MARKETPLACE.includes(pagina) || (esCliente && PAGINAS_CLIENTE.includes(pagina)) ? false
    : !!user && pagina !== "catalogo";

  return (
    <div className={usarPanel ? "app-layout" : ""}>
      {usarPanel && (
        <Sidebar
          paginaActual={pagina}
          irA={irA}
          userRole={user?.rol}
          abierto={sidebarAbierto}
          onCerrar={() => setSidebarAbierto(false)}
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
            onMenuClick={() => setSidebarAbierto(true)}
          />
        )}

        {renderPagina()}
      </main>

      <ChatWidget />
    </div>
  );
}

export default App;