import { useState, useEffect, useRef } from "react";
import "../css/reportes.css";
import InventarioClientes  from "./InventarioClientes";
import ReportesClientes    from "./ReportesClientes";
import InventarioProductos from "./InventarioProductos";
import ReportesProductos   from "./ReportesProductos";
import DashboardGeneral    from "./DashboardGeneral";
import { getDashboard }    from "../services/reporteService";

const MENU = [
  {
    key:   "inventario",
    label: "Inventario",
    ico:   "📦",
    sub: [
      { key: "inv-clientes",  label: "Inventario de Clientes",  ico: "👤" },
      { key: "inv-productos", label: "Inventario de Productos",  ico: "🔧" },
      { key: "rep-clientes",  label: "Reportes de Clientes",     ico: "📊" },
      { key: "rep-productos", label: "Reportes de Productos",    ico: "📈" },
    ],
  },
  { key: "dashboard", label: "Dashboard General", ico: "🏠" },
];

function ReportesPage({ token }) {
  const [vista,          setVista]          = useState("dashboard");
  const [openMenu,       setOpenMenu]       = useState(null);   // key del menú abierto
  const [dashData,       setDashData]       = useState(null);
  const [loadingDash,    setLoadingDash]    = useState(true);
  const [errorDash,      setErrorDash]      = useState(null);
  const menuRef = useRef(null);

  // Cerrar dropdown al click afuera
  useEffect(() => {
    const handler = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpenMenu(null);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  useEffect(() => {
    if (vista === "dashboard") {
      setLoadingDash(true);
      getDashboard(token)
        .then(setDashData)
        .catch(e => setErrorDash(e.message))
        .finally(() => setLoadingDash(false));
    }
  }, [vista, token]);

  const renderVista = () => {
    switch (vista) {
      case "inv-clientes":  return <InventarioClientes  token={token} />;
      case "rep-clientes":  return <ReportesClientes    token={token} />;
      case "inv-productos": return <InventarioProductos token={token} />;
      case "rep-productos": return <ReportesProductos   token={token} />;
      default:
        return <DashboardGeneral data={dashData} loading={loadingDash} error={errorDash} />;
    }
  };

  const activeLabel = (() => {
    for (const m of MENU) {
      if (m.key === vista) return m.label;
      if (m.sub) {
        const s = m.sub.find(x => x.key === vista);
        if (s) return s.label;
      }
    }
    return "Reportes";
  })();

  return (
    <div className="rp-root">
      {/* Topbar */}
      <header className="rp-top">
        <span className="rp-brand">DriveMaster</span>
        <span className="rp-div">|</span>
        <span className="rp-breadcrumb">Reportes › {activeLabel}</span>
        <div className="rp-top-right">
          <button className="rp-ico-btn">🔔</button>
          <div className="rp-avatar">A</div>
        </div>
      </header>

      {/* Page header */}
      <div className="rp-page-header">
        <div>
          <h1 className="rp-h1">Reportes & Analítica</h1>
          <p className="rp-sub">Visualiza métricas clave, inventario y rendimiento de tu negocio</p>
        </div>
      </div>

      {/* Nav con dropdowns */}
      <nav className="rp-nav" ref={menuRef}>
        {MENU.map(m => (
          <div key={m.key} className="rp-nav-item">
            {m.sub ? (
              <>
                <button
                  className={`rp-nav-btn${
                    (openMenu === m.key || m.sub.some(s => s.key === vista)) ? " active" : ""
                  }${openMenu === m.key ? " open" : ""}`}
                  onClick={() => setOpenMenu(openMenu === m.key ? null : m.key)}
                >
                  <span>{m.ico}</span>
                  {m.label}
                  <span className="arrow">▾</span>
                </button>

                {openMenu === m.key && (
                  <div className="rp-dropdown">
                    {m.sub.map(s => (
                      <button
                        key={s.key}
                        className={`rp-dropdown-item${vista === s.key ? " active" : ""}`}
                        onClick={() => { setVista(s.key); setOpenMenu(null); }}
                      >
                        <span className="ico">{s.ico}</span>
                        {s.label}
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <button
                className={`rp-nav-btn${vista === m.key ? " active" : ""}`}
                onClick={() => { setVista(m.key); setOpenMenu(null); }}
              >
                <span>{m.ico}</span>
                {m.label}
              </button>
            )}
          </div>
        ))}
      </nav>

      {/* Contenido */}
      <div className="rp-content">
        {renderVista()}
      </div>
    </div>
  );
}

export default ReportesPage;