import { useState, useEffect, useCallback } from "react";
import Sidebar               from "./components/Sidebar";
import TopBar                from "./components/TopBar";
import Productos             from "./pages/Productos";
import ProductoForm          from "./pages/ProductoForm";
import Compras               from "./pages/Compras";
import CompraForm            from "./pages/CompraForm";
import CompraDetalle         from "./pages/CompraDetalle";
import Proveedores           from "./pages/Proveedores";
import ProveedorForm         from "./pages/ProveedorForm";
import Movimientos           from "./pages/Movimientos";
import Login                 from "./pages/Login";
import AdminLogin            from "./pages/AdminLogin";
import Register              from "./pages/Register";
import EncabezadoPagina      from "./components/ui/EncabezadoPagina";
import Usuarios              from "./pages/Usuarios";
import CatalogoPage          from "./pages/CatalogoPage";
import ReportesServicios     from "./pages/ReportesServicios";
import MisPedidos            from "./pages/MisPedidos";
import MisSolicitudes        from "./pages/MisSolicitudes";
import PagoResultado         from "./pages/PagoResultado";
import CheckoutPage          from "./pages/CheckoutPage";
import DashboardPlataforma   from "./pages/DashboardPlataforma";
import DashboardOperacion    from "./pages/DashboardOperacion";
import ProductosPlataforma   from "./pages/ProductosPlataforma";
import ClientesPlataforma    from "./pages/ClientesPlataforma";
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
import ReportesPlataforma     from "./pages/ReportesPlataforma";
import InformesTienda        from "./pages/InformesTienda";
import AsistenteTienda       from "./pages/AsistenteTienda";
import NegociosPlataforma     from "./pages/NegociosPlataforma";
import AprobacionesAdmin     from "./pages/AprobacionesAdmin";
import PedidosEnvios         from "./pages/PedidosEnvios";
import PedidosTienda         from "./pages/PedidosTienda";
import Liquidaciones         from "./pages/Liquidaciones";
import ChatWidget            from "./components/chat/ChatWidget";
import { reiniciarChat }     from "./components/chat/chatSesion";
import { getUser, getToken, clearSession, refreshToken, logout, getTokenExpiracion } from "./services/authService";
import { useCarrito } from "./hooks/useCarrito";
import { connectNotifications, disconnectNotifications } from "./services/websocketService";
import { tieneCampanita } from "./services/notificacionesUtil";
import MarketHeader from "./components/marketplace/MarketHeader";
import DetalleProductoMarketplace from "./pages/DetalleProductoMarketplace";
import CarritoMarketplace from "./pages/CarritoMarketplace";
import CheckoutMarketplace from "./pages/CheckoutMarketplace";
import MisCompras from "./pages/MisCompras";
import PerfilCliente from "./pages/PerfilCliente";
import ProblemasEnvios from "./pages/ProblemasEnvios";
import DatosMiTienda from "./pages/DatosMiTienda";
import Alerta from "./components/ui/Alerta";
import Avisos from "./components/ui/Avisos";
import { miTienda } from "./services/tiendaService";
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
  "dashboard-main":  "Inicio",
  "productos":       "Productos",
  "productoNuevo":   "Nuevo producto",
  "productoEditar":  "Editar producto",
  "clientes":        "Clientes",
  "compras":         "Compras",
  "compraNueva":     "Nueva compra",
  "compraDetalle":   "Detalle de compra",
  "proveedores":     "Proveedores",
  "proveedorNuevo":  "Nuevo proveedor",
  "proveedorEditar": "Editar proveedor",
  "movimientos":     "Inventario",
  "usuarios":        "Usuarios",
  "rep-servicios":   "Reporte de Servicios",
  "reportes-plataforma": "Reportes de la plataforma",
  "mis-solicitudes": "Mis solicitudes",
  "pago-resultado":  "Resultado del pago",
  "mis-pedidos":     "Historial de compras",
  "checkout":        "Enviar solicitud",
  "mi-perfil":       "Mi perfil",
  "solicitar-diagnostico": "Solicitar diagnóstico",
  "mis-servicios":   "Mis servicios",
  "mis-compras":     "Mis compras",
  "perfil-cliente":  "Mi perfil",
  "checkout-marketplace": "Entrega y pago",
  "taller-panel":    "Inicio del taller",
  "taller-inicio":   "Inicio del taller",
  "taller-solicitudes": "Solicitudes",
  "taller-agenda":   "Agenda",
  "taller-trabajos": "Trabajos",
  "taller-cotizaciones": "Cotizaciones",
  "taller-mitaller": "Mi taller",
  "taller-informes": "Informes",
  "taller-anteriores": "Órdenes anteriores",
  "admin-talleres":  "Servicios",
  "aprobaciones":    "Aprobaciones",
  "negocios":        "Tiendas y talleres",
  "informes-tienda": "Informes",
  "asistente-tienda": "Asistente",
  "pedidos-tienda":  "Pedidos",
  "liquidaciones":   "Liquidaciones y reembolsos",
  "problemas-envios": "Problemas de envío",
  "pedidos-envios":  "Pedidos y envíos",
  "mi-tienda":       "Mi tienda",
};

// Páginas del cliente que se muestran sin el panel lateral.
const PAGINAS_CLIENTE = [
  "checkout", "mis-pedidos", "mis-solicitudes", "mis-vehiculos", "servicio-vehiculo",
  "solicitar-diagnostico", "mis-servicios", "checkout-marketplace", "mis-compras", "perfil-cliente",
];

// Catálogo marketplace: se consulta sin sesión y no usa el panel lateral aunque haya una.
const PAGINAS_MARKETPLACE = ["producto-marketplace", "carrito-marketplace"];

// Vuelta de Wompi: siempre con la cabecera pública, haya o no sesión y sea cual sea el rol (antes, con sesión,
// caía dentro del panel: menú lateral y doble cabecera).
const PAGINAS_SIEMPRE_PUBLICAS = ["pago-resultado"];

// Secciones del panel del taller (cada una es una entrada del menú lateral).
const SECCION_TALLER = {
  "taller-inicio":      "inicio",
  "taller-solicitudes": "solicitudes",
  "taller-trabajos":    "trabajos",
  "taller-cotizaciones": "cotizaciones",
  "taller-mitaller":    "mitaller",
  "taller-informes":    "informes",
  "taller-anteriores":  "anteriores",
  "taller-agenda":      "agenda",
};

// Página inicial de cada rol después de iniciar sesión.
const INICIO_POR_ROL = {
  CLIENTE: "catalogo",
  TALLER:  "taller-inicio",
  TIENDA:  "productos",
  ADMIN:   "dashboard-main",
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
  // Lo que el panel del taller le avisa al menú (solicitudes por responder, órdenes anteriores)
  const [resumenTaller, setResumenTaller] = useState({ anteriores: 0, porResponder: 0 });
  const [sessionExpired, setSessionExpired] = useState(false);
  const [solicitudItems, setSolicitudItems] = useState(loadSolicitud);
  const [sidebarAbierto, setSidebarAbierto] = useState(false);
  // true cuando el cliente llegó a "Mis servicios" iniciando sesión desde el enlace de la cotización
  const [vieneDeCotizacion, setVieneDeCotizacion] = useState(false);
  const carrito = useCarrito();
  // Vista con la que abre la landing: "inicio" o "catalogo" (el catálogo global)
  const [vistaCatalogo, setVistaCatalogo] = useState("inicio");
  // false: la tienda propia de DriveMaster tiene ciudad, dirección y horario (si no, sus productos no se muestran)
  // true si la tienda (rol TIENDA) aún no completó ciudad, dirección y horario: no se muestra en el catálogo
  const [tiendaIncompleta, setTiendaIncompleta] = useState(false);

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

  useEffect(() => {
    localStorage.setItem("solicitudItems", JSON.stringify(solicitudItems));
  }, [solicitudItems]);

  // El registro de negocios es solo para visitantes: con sesión se va al inicio del rol, como tras el login.
  // Se ajusta durante el render (patrón de React para corregir estado) para que no alcance a pintarse.
  const inicioDelRol = user ? INICIO_POR_ROL[user.rol] || "dashboard-main" : "catalogo";
  if (user && pagina === "registro-negocio") setPagina(inicioDelRol);
  const irARegistroNegocio = () => setPagina(user ? inicioDelRol : "registro-negocio");

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
    // Cambio de correo: el backend emitió una sesión nueva (el token lleva el correo)
    const onCuenta = () => { setUser(getUser()); setToken(getToken()); };
    window.addEventListener("auth:renovada", onRenovada);
    window.addEventListener("auth:expirada", expirarSesion);
    window.addEventListener("auth:cuenta", onCuenta);
    return () => {
      window.removeEventListener("auth:renovada", onRenovada);
      window.removeEventListener("auth:cuenta", onCuenta);
      window.removeEventListener("auth:expirada", expirarSesion);
    };
  }, [expirarSesion]);

  // Avisos en vivo: una sola conexión por sesión (no se reabre al cambiar de página ni de cabecera)
  const conAvisos = !!user && tieneCampanita(user.rol);
  useEffect(() => {
    if (!conAvisos) return undefined;
    connectNotifications();
    return () => disconnectNotifications();
  }, [conAvisos, user?.id]);

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
    // El chat guarda la conversación en el navegador: quien entre después no debe heredarla.
    reiniciarChat();
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

  const removeProductList = useCallback((productIds) => {
    setSolicitudItems(prev => prev.filter(i => !productIds.includes(i.productoId)));
  }, []);

  const solicitudSubtotal = calcularSubtotal(solicitudItems);
  const solicitudIva = solicitudSubtotal * ivaRate;
  const solicitudTotal = solicitudSubtotal + solicitudIva;

  const allowedPages = {
    // VENDEDOR es un rol retirado: solo ve un aviso en su inicio (los clientes son del SUPERADMIN)
    VENDEDOR: [
      "dashboard-main",
      "mi-perfil",
    ],
    // ADMIN: solo la operación diaria (el panel de plataforma completo es del SUPERADMIN)
    ADMIN: [
      "dashboard-main", "pedidos-envios",
      "aprobaciones", "admin-talleres", "problemas-envios",
      "mi-perfil",
    ],
    SUPERADMIN: [
      "dashboard-main",
      "productos", "productoNuevo", "productoEditar",
      "clientes",
      "rep-servicios", "reportes-plataforma",
      "usuarios",
      "pedidos-envios",
      "admin-talleres", "aprobaciones", "negocios", "liquidaciones", "problemas-envios",
      "mi-perfil",
    ],
    CLIENTE: ["catalogo", ...PAGINAS_CLIENTE],
    // "taller-panel" era la única página del taller: se conserva como alias de Inicio
    TALLER: ["taller-panel", ...Object.keys(SECCION_TALLER)],
    // Compras, proveedores e inventario son de cada tienda (el backend filtra por dueño)
    TIENDA: ["productos", "productoNuevo", "productoEditar", "movimientos", "pedidos-tienda", "mi-tienda", "informes-tienda", "asistente-tienda",
      "compras", "compraNueva", "compraDetalle", "proveedores", "proveedorNuevo", "proveedorEditar"],
  };
  const esTienda = user?.rol === "TIENDA";

  useEffect(() => {
    if (user?.rol !== "TIENDA") return undefined;
    let vivo = true;
    miTienda(getToken())
      .then((t) => { if (vivo) setTiendaIncompleta(t.datosCompletos === false); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [user?.rol]);

  // Tras un pago aprobado se vacían del carrito solo los productos que se pagaron; el otro grupo se queda
  const vaciarCarritoPagado = () => {
    const pendiente = consumirPagoPendiente();
    if (pendiente) carrito.quitarVarios(pendiente.ids);
  };

  // El catálogo global vive en la landing: se abre directo en su vista de catálogo
  const irAlCatalogoGlobal = () => { setVistaCatalogo("catalogo"); setPagina("catalogo"); };
  const irAlInicio = () => { setVistaCatalogo("inicio"); setPagina("catalogo"); };

  const cabeceraMarketplace = (activo) => (
    <MarketHeader
      activo={activo}
      cantidadCarrito={carrito.cantidad}
      onIrInicio={irAlInicio}
      onIrCatalogo={irAlCatalogoGlobal}
      onIrCarrito={() => setPagina("carrito-marketplace")}
      onIrMisCompras={() => irA("mis-compras")}
      onIrLogin={() => setPagina("login")}
      onLogout={handleLogout}
      onSolicitarDiagnostico={() => irA("solicitar-diagnostico")}
      onIr={(p) => irA(p)}
      onIrAdmin={() => setPagina(INICIO_POR_ROL[user?.rol] && user.rol !== "CLIENTE" ? INICIO_POR_ROL[user.rol] : "dashboard-main")}
      onAbrirAviso={irA}
      theme={theme}
      onToggleTheme={toggleTheme}
    />
  );

  // Vistas de cliente sin cabecera propia: llevan la del marketplace (con el logo). El header
  // necesita el scope .dm-page para sus estilos; se aplica solo a él para no cambiar la vista.
  const conCabecera = (vista, activo = null) => (
    <>
      <div className="dm-page dm-page--solo-cabecera">{cabeceraMarketplace(activo)}</div>
      {vista}
    </>
  );

  const renderPagina = () => {
    // Ficha completa y carrito del catálogo: públicos
    if (pagina === "producto-marketplace") return (
      <DetalleProductoMarketplace
        key={idSeleccionado}
        header={cabeceraMarketplace("catalogo")}
        productoId={idSeleccionado}
        onVolver={() => irAlCatalogoGlobal()}
        onVerProducto={(p) => irA("producto-marketplace", p.id)}
        onAgregar={(p, cantidad) => carrito.agregar(p, cantidad)}
        onIrCarrito={() => setPagina("carrito-marketplace")}
      />
    );
    if (pagina === "carrito-marketplace") return (
      <CarritoMarketplace
        header={cabeceraMarketplace("carrito")}
        carrito={carrito}
        onVolver={() => irAlCatalogoGlobal()}
        onVerProducto={(p) => irA("producto-marketplace", p.id)}
        onIrLogin={() => { guardarDestinoPostLogin("carrito-marketplace"); setPagina("login"); }}
        onContinuarCatalogo={() => setPagina("checkout-marketplace")}
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
          key={idSeleccionado || "mis-compras"}
          compraInicial={idSeleccionado}
          header={cabeceraMarketplace("compras")}
          onVolver={() => setPagina("catalogo")}
          onIrCatalogo={() => irAlCatalogoGlobal()}
          onIrSolicitudes={() => setPagina("mis-solicitudes")}
          onIrHistorial={() => setPagina("mis-pedidos")}
        />
      );
      return (
        <CheckoutMarketplace
          header={cabeceraMarketplace("carrito")}
          carrito={carrito}
          onVolverCarrito={() => setPagina("carrito-marketplace")}
          onPagada={(pagada) => { if (pagada) vaciarCarritoPagado(); irA("mis-compras"); }}
        />
      );
    }

    // Mi perfil del cliente (distinto de "mi-perfil", el perfil de los roles internos)
    if (pagina === "perfil-cliente") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      if (user.rol !== "CLIENTE") return <div style={{ padding: "40px" }}>Esta sección es para clientes.</div>;
      return (
        <PerfilCliente
          header={cabeceraMarketplace("perfil")}
          onIr={(p) => irA(p)}
          onIrCatalogo={() => irAlCatalogoGlobal()}
          onSolicitarDiagnostico={() => irA("solicitar-diagnostico")}
          theme={theme}
          onToggleTheme={toggleTheme}
        />
      );
    }

    if (pagina === "catalogo") return (
      <CatalogoPage
        vistaInicial={vistaCatalogo}
        onVistaConsumida={() => setVistaCatalogo("inicio")}
        onIrLogin={() => setPagina("login")}
        onIrAdmin={() => setPagina(INICIO_POR_ROL[user?.rol] && user.rol !== "CLIENTE" ? INICIO_POR_ROL[user.rol] : "dashboard-main")}
        onIrSolicitud={(p) => setPagina(p || "checkout")}
        onSolicitarDiagnostico={() => irA("solicitar-diagnostico")}
        onRegistroNegocio={irARegistroNegocio}
        onIrMisCompras={() => { if (!user) guardarDestinoPostLogin("checkout-marketplace"); irA("mis-compras"); }}
        onLogout={handleLogout}
        carrito={carrito}
        onIrCarrito={() => setPagina("carrito-marketplace")}
        onAbrirAviso={irA}
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
              theme={theme} onToggleTheme={toggleTheme}
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

    if (pagina === "mis-solicitudes") return conCabecera(<MisSolicitudes onVolver={() => setPagina("catalogo")} />);

    if (pagina === "pago-resultado") return conCabecera(
      <PagoResultado
        onPagoExitoso={() => setSolicitudItems([])}
        onIrSolicitudes={() => setPagina("mis-solicitudes")}
        onIrServicios={() => irA("mis-servicios")}
        onCompraCatalogoPagada={vaciarCarritoPagado}
        onIrMisCompras={() => irA("mis-compras")}
      />
    );

    // Pública: responder la cotización desde el correo sin iniciar sesión
    if (pagina === "cotizacion-publica") return (
      <CotizacionPublica
        onIrServicios={() => {
          window.history.replaceState(null, "", "/");
          // Sin sesión, "Mis servicios" pide iniciar sesión: al terminar se vuelve aquí
          if (!user) guardarDestinoPostLogin("mis-servicios");
          irA("mis-servicios");
        }}
        onIrInicio={() => { window.history.replaceState(null, "", "/"); setPagina("catalogo"); }}
      />
    );

    if (pagina === "mis-pedidos") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return conCabecera(<MisPedidos onVolver={() => setPagina("catalogo")} />);
    }

    if (pagina === "mis-vehiculos") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return conCabecera(
        <MisVehiculos
          onVolver={() => setPagina("catalogo")}
          onVerServicio={(id) => irA("servicio-vehiculo", id)}
        />,
        "vehiculos"
      );
    }

    if (pagina === "servicio-vehiculo") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")} onVolver={() => setPagina("catalogo")} />;
      return conCabecera(
        <VehiculoServicio
          vehiculoId={idSeleccionado}
          onVolver={() => setPagina("mis-vehiculos")}
          onSolicitarDiagnostico={(id) => irA("solicitar-diagnostico", id)}
        />,
        "vehiculos"
      );
    }

    if (pagina === "solicitar-diagnostico" || pagina === "mis-servicios") {
      if (!user) return <Login onLogin={handleLogin} onGoRegister={() => setPagina("register")}
        onVolver={() => { limpiarDestinoPostLogin(); setPagina("catalogo"); }} />;
      if (user.rol !== "CLIENTE") return (
        <div style={{ padding: "40px" }}>Esta sección es para clientes. Ingresa con una cuenta de cliente para solicitar un diagnóstico.</div>
      );
      if (pagina === "mis-servicios") return conCabecera(
        <MisServicios
          key={idSeleccionado || "mis-servicios"}
          solicitudInicial={idSeleccionado}
          onVolver={() => { setVieneDeCotizacion(false); setPagina("catalogo"); }}
          onSolicitar={() => irA("solicitar-diagnostico")}
          avisoCotizacion={vieneDeCotizacion ? user.correo : null}
          onCerrarAviso={() => setVieneDeCotizacion(false)}
          onIrHistorial={() => setPagina("mis-pedidos")}
        />,
        "servicios"
      );
      return conCabecera(
        <SolicitarDiagnostico
          key={idSeleccionado || "nueva"}
          vehiculoIdInicial={idSeleccionado}
          onVolver={() => setPagina("catalogo")}
          onVerServicios={() => irA("mis-servicios")}
          onIrVehiculos={() => irA("mis-vehiculos")}
        />,
        "diagnostico"
      );
    }

    if (pagina === "admin-login") {
      return <AdminLogin onLogin={handleLogin} onVolver={() => setPagina("catalogo")} />;
    }

    if (pagina === "registro-negocio") {
      // Con sesión no se pinta: arriba ya se redirigió al inicio del rol
      if (user) return null;
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

    // SUPERADMIN: panel de la plataforma. ADMIN: su resumen operativo. VENDEDOR: rol retirado, solo un aviso.
    if (pagina === "dashboard-main") {
      if (user?.rol === "SUPERADMIN") return <DashboardPlataforma token={token} onIr={(p, id) => irA(p, id)} />;
      if (user?.rol === "ADMIN") return <DashboardOperacion token={token} onIr={(p, id) => irA(p, id)} />;
      if (user?.rol === "VENDEDOR") {
        return (
          <div className="ss-panel--plataforma">
            <EncabezadoPagina titulo="El rol de vendedor se retiró"
              descripcion="Pide a un superadmin que reasigne tu cuenta a administrador o que la desactive." />
          </div>
        );
      }
    }

    // SUPERADMIN: catálogo global de solo lectura; la TIENDA y el VENDEDOR siguen con su gestión de productos
    if (pagina === "productos" && user?.rol === "SUPERADMIN") return <ProductosPlataforma token={token} />;
    if (pagina === "productos")        return <Productos            onNuevo={() => irA("productoNuevo")} onEditar={id => irA("productoEditar", id)} token={token} esTienda={esTienda} />;
    if (pagina === "productoNuevo")    return <ProductoForm         onVolver={() => irA("productos")} token={token} esTienda={esTienda} ivaRate={ivaRate} />;
    if (pagina === "productoEditar")   return <ProductoForm         id={idSeleccionado} onVolver={() => irA("productos")} token={token} esTienda={esTienda} ivaRate={ivaRate} />;

    if (pagina === "clientes" && user?.rol === "SUPERADMIN") return <ClientesPlataforma token={token} />;
    if (pagina === "compras")          return <Compras              onNueva={() => irA("compraNueva")} onDetalle={id => irA("compraDetalle", id)} token={token} />;
    if (pagina === "compraNueva")      return <CompraForm           onVolver={() => irA("compras")} token={token} />;
    if (pagina === "compraDetalle")    return <CompraDetalle        id={idSeleccionado} onVolver={() => irA("compras")} token={token} />;

    if (pagina === "proveedores")      return <Proveedores          onNuevo={() => irA("proveedorNuevo")} onEditar={id => irA("proveedorEditar", id)} token={token} />;
    if (pagina === "proveedorNuevo")   return <ProveedorForm        onVolver={() => irA("proveedores")} token={token} />;
    if (pagina === "proveedorEditar")  return <ProveedorForm        id={idSeleccionado} onVolver={() => irA("proveedores")} token={token} />;

    if (pagina === "movimientos")      return <Movimientos          token={token} esTienda={esTienda} />;
    if (pagina === "usuarios")         return <Usuarios             token={token} />;
    if (pagina === "mi-perfil")        return <PerfilAdmin          />;
    if (pagina === "taller-panel" || SECCION_TALLER[pagina]) {
      return <PanelTaller seccion={SECCION_TALLER[pagina] || "inicio"} param={idSeleccionado} irA={irA} onResumen={setResumenTaller} />;
    }
    if (pagina === "admin-talleres")   return <AdminTalleres        />;
    // ADMIN: solo aprueba o rechaza; SUPERADMIN: gestión completa (y la lista unificada "Tiendas y talleres")
    if (pagina === "aprobaciones" && user?.rol === "ADMIN") return <AprobacionesAdmin token={token} />;
    if (pagina === "aprobaciones")     return <Aprobaciones         token={token} />;
    if (pagina === "negocios")         return <NegociosPlataforma key={idSeleccionado || "todos"} token={token} filtroInicial={idSeleccionado} />;
    if (pagina === "informes-tienda")  return <InformesTienda       token={token} onIr={(p) => irA(p)} />;
    if (pagina === "asistente-tienda") return <AsistenteTienda      token={token} />;
    if (pagina === "liquidaciones")    return <Liquidaciones        />;
    // Un aviso de la campanita abre un pedido con irA("pedidos-tienda", { abrir }); otro aviso la vuelve a montar
    if (pagina === "pedidos-tienda")   return <PedidosTienda key={idSeleccionado?.abrir || "pedidos"} param={idSeleccionado} irA={irA} />;
    if (pagina === "problemas-envios") return <ProblemasEnvios      />;
    if (pagina === "pedidos-envios")   return <PedidosEnvios key={idSeleccionado || "todos"} token={token} filtroInicial={idSeleccionado} />;
    if (pagina === "mi-tienda")        return <DatosMiTienda        token={token} onGuardado={(t) => setTiendaIncompleta(!t.datosCompletos)} />;

    if (pagina === "reportes-plataforma") return <ReportesPlataforma key={idSeleccionado || "ventas"} token={token} filtroInicial={idSeleccionado} />;
    if (pagina === "rep-servicios")    return <ReportesServicios    token={token} />;

    return <div style={{ padding: "40px" }}>Seleccione una sección válida.</div>;
  };

  const esCliente = user?.rol === "CLIENTE";
  // El registro de negocios es público: nunca va dentro del panel, aunque haya sesión.
  const usarPanel = pagina === "catalogo" || pagina === "registro-negocio" || PAGINAS_MARKETPLACE.includes(pagina)
    || PAGINAS_SIEMPRE_PUBLICAS.includes(pagina) || (esCliente && PAGINAS_CLIENTE.includes(pagina)) ? false
    : !!user && pagina !== "catalogo";

  return (
    <div className={usarPanel ? "app-layout" : ""}>
      {usarPanel && (
        <Sidebar
          paginaActual={pagina}
          irA={irA}
          userRole={user?.rol}
          resumenTaller={resumenTaller}
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
            theme={theme}
            onToggleTheme={toggleTheme}
            onMenuClick={() => setSidebarAbierto(true)}
          />
        )}

        {esTienda && tiendaIncompleta && pagina !== "mi-tienda" && (
          <Alerta tono="aviso" className="app-aviso-tienda">
            Tus productos aún no se muestran en el catálogo: faltan la ciudad, la dirección o el horario de tu tienda.{" "}
            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => irA("mi-tienda")}>Completar Mi tienda</button>
          </Alerta>
        )}

        {renderPagina()}
      </main>

      <ChatWidget onVerProducto={(id) => irA("producto-marketplace", id)} />
      <Avisos />
    </div>
  );
}

export default App;