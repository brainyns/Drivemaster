import { useState } from "react";
import Icon from "./Icon";
import "../../css/banner.css";

const HERO_IMAGE = "/images/drivemaster-hero.jpg";

/**
 * Hero del sitio público. La imagen es opcional: si el archivo aún no está en
 * public/images, el bloque cae al degradado oscuro (que es el 90% del diseño)
 * en lugar de mostrar un ícono de imagen rota.
 */
export default function Hero({ onVerCatalogo, onIrVehiculo }) {
  const [imagenOk, setImagenOk] = useState(true);

  return (
    <section className="dm-hero">
      {imagenOk && (
        <img
          className="dm-hero__image"
          src={HERO_IMAGE}
          alt="Vehículo en un entorno industrial oscuro"
          fetchpriority="high"
          decoding="async"
          loading="eager"
          onError={() => setImagenOk(false)}
        />
      )}
      <div className="dm-hero__shade" />
      <div className="dm-hero__grid" />

      <div className="dm-container dm-hero__content">
        <div className="dm-hero__copy">
          <div className="dm-eyebrow dm-hero__eyebrow">
            <span className="dm-dot" /> TECNOLOGÍA QUE TE MANTIENE EN MOVIMIENTO
          </div>

          <h1>
            El repuesto correcto
            <br className="dm-hero__title-break" /> empieza con{" "}
            <span>tu vehículo.</span>
          </h1>

          <p>
            Encuentra repuestos compatibles, compara opciones y lleva el
            mantenimiento de tu vehículo a otro nivel.
          </p>

          <div className="dm-hero__buttons">
            <button className="dm-btn dm-btn--primary" onClick={onIrVehiculo}>
              Encuentra tu repuesto <Icon name="arrow" size={18} />
            </button>
            <button className="dm-btn dm-btn--secondary" onClick={onVerCatalogo}>
              Explorar catálogo <Icon name="arrow" size={18} />
            </button>
          </div>

          <div className="dm-hero__assurance">
            <Icon name="shield" size={17} />
            <span>Menos dudas. Más precisión. Siempre hacia adelante.</span>
          </div>
        </div>

        <div className="dm-hero__technical">
          <span className="dm-hero__cross">+</span>
          <div>
            PRECISIÓN EN CADA CONEXIÓN
            <small>SISTEMA AUTOMOTRIZ / 01</small>
          </div>
          <span className="dm-hero__rule" />
        </div>

        <div className="dm-hero__coordinate">
          INGENIERÍA PARA SEGUIR AVANZANDO <span>↗</span>
        </div>
      </div>
    </section>
  );
}