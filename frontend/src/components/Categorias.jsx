import { useState, useEffect } from "react";
import { obtenerProductos } from "../services/productoService";
import "../css/categorias.css";

const CATEGORIAS_CONFIG = {
  Aceites:     { icono: "🛢️", desc: "Motor, transmisión y diferencial" },
  Frenos:      { icono: "🔧", desc: "Pastillas, discos y líquidos" },
  Suspensión:  { icono: "🔩", desc: "Amortiguadores y bujes" },
  Refacciones: { icono: "⚙️", desc: "Filtros, bujías y más" },
};

const ICONO_DEFAULT = "📦";

const Categorias = ({ categoriaActiva, onCategoriaSelect }) => {
  const [categorias, setCategorias] = useState([]);

  useEffect(() => {
    obtenerProductos()
      .then((productos) => {
        // Agrupa categorías únicas con conteo desde el backend
        const mapa = {};
        productos.forEach((p) => {
          if (!p.categoria) return;
          mapa[p.categoria] = (mapa[p.categoria] || 0) + 1;
        });
        const lista = Object.entries(mapa).map(([nombre, count]) => ({
          nombre,
          count,
          icono: CATEGORIAS_CONFIG[nombre]?.icono ?? ICONO_DEFAULT,
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
              onCategoriaSelect(
                categoriaActiva === cat.nombre ? "Todas" : cat.nombre
              )
            }
          >
            <span className="categoria-card__icon">{cat.icono}</span>
            <div>
              <div className="categoria-card__name">{cat.nombre}</div>
              <div className="categoria-card__count">{cat.count} productos</div>
            </div>
            <span className="categoria-card__arrow">→</span>
          </div>
        ))}
      </div>
    </section>
  );
};

export default Categorias;