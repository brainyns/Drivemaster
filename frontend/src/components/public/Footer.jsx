import Logo from "./Logo";
import Icon from "./Icon";
import "../../css/footer.css";

export default function Footer({ onIrCatalogo, onIrInicio, onIrSolicitud, onIrVehiculo, onIrServicios, onIrLogin, onIrAdmin }) {
  const anio = new Date().getFullYear();

  return (
    <footer className="dm-footer">
      <div className="dm-container dm-footer__top">
        <div>
          <Logo light as="div" />
          <p>
            Precisión para tu vehículo.
            <br />
            Confianza para tu camino.
          </p>
        </div>

        <div>
          <span>EXPLORA</span>
          <button onClick={onIrCatalogo}>Catálogo de repuestos</button>
          <button onClick={onIrVehiculo}>Identifica tu vehículo</button>
          <button onClick={onIrServicios}>Mantenimiento</button>
        </div>

        <div>
          <span>DRIVEMASTER</span>
          <button onClick={() => onIrInicio?.("dm-pasos")}>Cómo funciona</button>
          <button onClick={() => onIrInicio?.("dm-nosotros")}>Nuestra visión</button>
          <button onClick={onIrLogin || onIrAdmin}>Mi cuenta</button>
        </div>

        <div className="dm-footer__signoff">
          <span>PRECISIÓN. CONFIANZA. RENDIMIENTO.</span>
          <strong>
            Siempre hacia
            <br />
            adelante<span>↗</span>
          </strong>
          <button className="dm-footer__wsp" onClick={onIrSolicitud}>
            <Icon name="message" size={16} /> Soporte y cotizaciones
          </button>
        </div>
      </div>

        <div className="dm-container dm-footer__bottom">
          <span>
            © {anio} DriveMaster. Todos los derechos reservados.
          </span>
          <span>
            Diseñado alrededor de tu vehículo.
            <span className="dm-dot" />
          </span>
          <span>
            Soporte:{" "}
            <a href="mailto:soporte@drivemaster.co" className="dm-footer__link">
              soporte@drivemaster.co
            </a>
          </span>
          <span>
            Contacto:{" "}
            <a href="mailto:contacto@drivemaster.co" className="dm-footer__link">
              contacto@drivemaster.co
            </a>
          </span>
        </div>
    </footer>
  );
}