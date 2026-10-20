import { useState } from "react";
import Icon from "./Icon";
import "../../css/banner.css";

const HERO_IMAGE = "/images/drivemaster-hero.jpg";

/**
 * Hero del sitio público. La imagen es opcional: si el archivo aún no está en
 * public/images, el bloque cae al degradado oscuro (que es el 90% del diseño)
 * en lugar de mostrar un ícono de imagen rota.
 */
export default function Hero({ onVerCatalogo, onSolicitarDiagnostico }) {
  const [imagenOk, setImagenOk] = useState(true);

  return (
    <section className="dm-hero">
      {imagenOk && (
        <img
          className="dm-hero__image"
          src={HERO_IMAGE}
          alt="Vehículo en un taller"
          fetchPriority="high"
          decoding="async"
          loading="eager"
          onError={() => setImagenOk(false)}
        />
      )}
      <div className="dm-hero__shade" />

      <div className="dm-container dm-hero__content">
        <div className="dm-hero__copy">
          <div className="dm-eyebrow dm-hero__eyebrow">
            <span className="dm-dot" /> TALLERES Y AUTOPARTES, CONECTADOS
          </div>

          <h1>
            Tu vehículo en manos expertas,
            <br className="dm-hero__title-break" />{" "}
            <span>de principio a fin.</span>
          </h1>

          <p>
            Pide un diagnóstico, te asignamos el taller más cercano y recibes
            una cotización clara con repuestos de tiendas verificadas.
          </p>

          <div className="dm-hero__buttons">
            <button className="dm-btn dm-btn--primary" onClick={onSolicitarDiagnostico}>
              Solicitar diagnóstico <Icon name="arrow" size={18} />
            </button>
            <button className="dm-btn dm-btn--secondary" onClick={onVerCatalogo}>
              Explorar catálogo <Icon name="arrow" size={18} />
            </button>
          </div>

          <div className="dm-hero__assurance">
            <Icon name="shield" size={17} />
            <span>Tarifa fija de diagnóstico, descontable si aceptas la reparación.</span>
          </div>
        </div>

        <div className="dm-hero__coordinate">
          DEL PRIMER SÍNTOMA A LA ENTREGA <span>↗</span>
        </div>
      </div>
    </section>
  );
}