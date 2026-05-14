import { useState, useEffect } from "react";
import { obtenerProductos } from "../services/productoService";
import "../css/categorias.css";

/* ── SVG ICONS por categoría ── */
const ICONS = {
  Motor: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Pistón con biela */}
      <rect x="8" y="2" width="8" height="10" rx="1"/>
      <path d="M10 12v3M14 12v3"/>
      <path d="M8 15h8"/>
      <path d="M10 18h4v3H10z"/>
      <path d="M3 9h5M16 9h5"/>
    </svg>
  ),
  "Transmision y Embrague": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Engranajes */}
      <circle cx="8" cy="8" r="3"/>
      <path d="M8 2v1M8 13v1M2 8h1M13 8h1M3.93 3.93l.7.7M11.37 11.37l.7.7M3.93 12.07l.7-.7M11.37 4.63l.7-.7"/>
      <circle cx="16" cy="16" r="3"/>
      <path d="M16 10v1M16 21v1M10 16h1M21 16h1M11.93 11.93l.7.7M19.37 19.37l.7.7M11.93 20.07l.7-.7M19.37 12.63l.7-.7"/>
    </svg>
  ),
  Frenos: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Disco de freno con pastilla */}
      <circle cx="12" cy="12" r="9"/>
      <circle cx="12" cy="12" r="3"/>
      <path d="M12 3v3M12 18v3M3 12h3M18 12h3"/>
      <path d="M5.64 5.64l2.12 2.12M16.24 16.24l2.12 2.12"/>
    </svg>
  ),
  "Suspension y Direccion": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Volante + amortiguador */}
      <circle cx="12" cy="9" r="5"/>
      <circle cx="12" cy="9" r="1.5"/>
      <path d="M7.5 9h-2M18.5 9h-2M12 4.5V3"/>
      <path d="M10 19v-5M14 19v-5"/>
      <path d="M8 19h8"/>
      <path d="M9 22h6"/>
    </svg>
  ),
  "Sistema Electrico": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Rayo / circuito */}
      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
    </svg>
  ),
  "Baterias y Carga": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Batería de auto con terminales + y - */}
      <rect x="2" y="8" width="18" height="12" rx="1.5"/>
      <path d="M20 12h2v4h-2"/>
      <path d="M6 8V6M14 8V6"/>
      <path d="M5 13h4M7 11v4"/>
      <path d="M13 13h4"/>
    </svg>
  ),
  "Enfriamiento y Radiadores": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Radiador con aletas + ventilador */}
      <rect x="2" y="5" width="14" height="14" rx="1"/>
      <path d="M5 5v14M8 5v14M11 5v14"/>
      <path d="M16 9c2 0 6 .5 6 3s-4 3-6 3"/>
    </svg>
  ),
  "Lubricantes y Fluidos": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Aceitera / can de aceite */}
      <path d="M3 20h12a2 2 0 0 0 2-2V9l-4-5H3a1 1 0 0 0-1 1v13a1 1 0 0 0 1 1z"/>
      <path d="M13 4l4 5"/>
      <path d="M17 12h3l1 4h-4"/>
      <path d="M6 13c0 2 3 4 3 4s3-2 3-4a3 3 0 0 0-6 0z"/>
    </svg>
  ),
  Filtros: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Cilindro de filtro */}
      <ellipse cx="12" cy="6" rx="8" ry="2.5"/>
      <path d="M4 6v5c0 1.38 3.58 2.5 8 2.5s8-1.12 8-2.5V6"/>
      <path d="M4 11v5c0 1.38 3.58 2.5 8 2.5s8-1.12 8-2.5v-5"/>
      <path d="M8 6v8M12 6v8M16 6v8"/>
    </svg>
  ),
  "Escape y Emisiones": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Tubo de escape con humo */}
      <path d="M2 17h8a3 3 0 0 0 3-3V8"/>
      <path d="M13 8h6a2 2 0 0 1 0 4h-1"/>
      <path d="M10 3c0 2-2 2-2 4s2 2 2 4"/>
      <path d="M14 3c0 2-2 2-2 4s2 2 2 4"/>
    </svg>
  ),
  "Combustible e Inyeccion": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Bomba de gasolina / inyector */}
      <path d="M3 22V8a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v14"/>
      <path d="M3 22h14"/>
      <path d="M15 8h3l2 2v5h-5"/>
      <path d="M7 11h4M7 15h4"/>
      <path d="M9 6V4"/>
    </svg>
  ),
  "Aire Acondicionado y Climatizacion": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Unidad A/C con aire */}
      <rect x="2" y="6" width="20" height="10" rx="2"/>
      <path d="M6 10h12"/>
      <path d="M8 14c0-2 1-3 1-3s1 1 1 3"/>
      <path d="M12 14c0-2 1-3 1-3s1 1 1 3"/>
      <path d="M16 14c0-2 1-3 1-3s1 1 1 3"/>
      <path d="M2 10h1M21 10h1"/>
    </svg>
  ),
  "Carroceria Exterior": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Silueta de auto de perfil */}
      <path d="M2 14h1l2-4h10l3 4h1a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z"/>
      <path d="M5 10l2-3h6l2 3"/>
      <circle cx="6.5" cy="17.5" r="1.5"/>
      <circle cx="17.5" cy="17.5" r="1.5"/>
    </svg>
  ),
  "Interior y Accesorios": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Asiento de auto */}
      <path d="M6 20V10a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2h2a2 2 0 0 1 2 2v6"/>
      <path d="M6 20h12"/>
      <path d="M6 14h8"/>
      <path d="M6 10V7a1 1 0 0 1 1-1h2"/>
    </svg>
  ),
  Iluminacion: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Faro de auto */}
      <path d="M2 8h8l4 8H2z"/>
      <path d="M14 12h4l3-4"/>
      <path d="M14 12h4l3 4"/>
      <path d="M6 8V6M10 8V5M4 8V7"/>
    </svg>
  ),
  "Neumaticos y Llantas": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Llanta con rin y tuercas */}
      <circle cx="12" cy="12" r="10"/>
      <circle cx="12" cy="12" r="4"/>
      <path d="M12 2v4M12 18v4M2 12h4M18 12h4"/>
      <circle cx="12" cy="6" r="1" fill="currentColor"/>
      <circle cx="12" cy="18" r="1" fill="currentColor"/>
      <circle cx="6" cy="12" r="1" fill="currentColor"/>
      <circle cx="18" cy="12" r="1" fill="currentColor"/>
    </svg>
  ),
  "Audio, Multimedia y Electronica": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Pantalla de consola con speaker */}
      <rect x="2" y="5" width="14" height="10" rx="1.5"/>
      <path d="M16 9l4-2v10l-4-2"/>
      <circle cx="9" cy="10" r="2.5"/>
      <path d="M2 18h14"/>
    </svg>
  ),
  "Herramientas y Equipos": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Llave + destornillador cruzados */}
      <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
      <path d="M2 2l4 4"/>
    </svg>
  ),
  "Consumibles y Taller": (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {/* Caja de taller / bodega */}
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
      <path d="M12 22V12M3.27 6.96 12 12.01l8.73-5.05"/>
      <path d="M7.5 4.21l9 5.16"/>
    </svg>
  ),
  Otro: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="10"/>
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
      <circle cx="12" cy="17" r=".5" fill="currentColor"/>
    </svg>
  ),
};

const ICON_DEFAULT = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2"/>
    <path d="M3 9h18M9 21V9"/>
  </svg>
);

const ARROW_ICON = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="16" height="16">
    <path d="M5 12h14M12 5l7 7-7 7"/>
  </svg>
);

/* ── COMPONENT ── */
const Categorias = ({ categoriaActiva, onCategoriaSelect }) => {
  const [categorias, setCategorias] = useState([]);

  useEffect(() => {
    obtenerProductos()
      .then((productos) => {
        const mapa = {};
        productos.forEach((p) => {
          if (!p.categoria) return;
          mapa[p.categoria] = (mapa[p.categoria] || 0) + 1;
        });
        const lista = Object.entries(mapa).map(([nombre, count]) => ({
          nombre,
          count,
          icon: ICONS[nombre] ?? ICON_DEFAULT,
        }));
        setCategorias(lista);
      })
      .catch(console.error);
  }, []);

  return (
    <section className="categorias">
      <div className="categorias__header">
        <p className="categorias__title">Explorar por</p>
        <h2 className="categorias__heading">CATEGORÍAS</h2>
      </div>

      <div className="categorias__grid">
        {categorias.map((cat) => (
          <div
            key={cat.nombre}
            className={`categoria-card ${categoriaActiva === cat.nombre ? "active" : ""}`}
            onClick={() =>
              onCategoriaSelect(categoriaActiva === cat.nombre ? "Todas" : cat.nombre)
            }
          >
            <span className="categoria-card__icon" style={{ width: 24, height: 24 }}>{cat.icon}</span>
            <div>
              <div className="categoria-card__name">{cat.nombre}</div>
              <div className="categoria-card__count">{cat.count} productos</div>
            </div>
            <span className="categoria-card__arrow">{ARROW_ICON}</span>
          </div>
        ))}
      </div>
    </section>
  );
};

export default Categorias;