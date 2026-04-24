import "../css/banner.css";

const Banner = ({ onVerCatalogo }) => {
  return (
    <section className="banner">
      <div className="banner__grid" />
      <div className="banner__content">
        <div className="banner__text">
          <div className="banner__badge">
            <span className="banner__badge-dot" />
            Repuestos de calidad garantizada
          </div>

          <h1 className="banner__title">
            TU AUTO<br />
            MERECE LO<br />
            <span>MEJOR</span>
          </h1>

          <p className="banner__subtitle">
            Encuentra repuestos originales y de alto rendimiento para tu vehículo.
            Cotiza directo por WhatsApp, envío rápido a todo el país.
          </p>

          <div className="banner__cta">
            <button className="banner__btn-primary" onClick={onVerCatalogo}>
              Ver Catálogo
            </button>
            <button className="banner__btn-secondary">
              ¿Cómo funciona?
            </button>
          </div>

          <div className="banner__stats">
            <div>
              <div className="banner__stat-value">500+</div>
              <div className="banner__stat-label">Productos</div>
            </div>
            <div>
              <div className="banner__stat-value">4</div>
              <div className="banner__stat-label">Categorías</div>
            </div>
            <div>
              <div className="banner__stat-value">24h</div>
              <div className="banner__stat-label">Respuesta</div>
            </div>
          </div>
        </div>

        <div className="banner__visual">
          <div className="banner__car-graphic">
            <img
              src="https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?w=600&h=400&fit=crop"
              alt="Repuestos automotrices"
            />
            <div className="banner__car-label">🔩 Repuestos originales y alternos</div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Banner;