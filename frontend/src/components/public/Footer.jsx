import Logo from "../Logo";
import Icon from "./Icon";
import "../../css/footer.css";

export default function Footer({
  onIrCatalogo, onIrInicio, onIrVehiculo, onIrLogin, onIrAdmin,
  onSolicitarDiagnostico, onRegistroNegocio,
}) {
  const anio = new Date().getFullYear();

  return (
    <footer className="dm-footer">
      <div className="dm-container dm-footer__top">
        <div>
          <Logo light onClick={() => onIrInicio?.()} />
          <p>
            Diagnóstico, repuestos e instalación.
            <br />
            Todo conectado.
          </p>
        </div>

        <div>
          <span>EXPLORA</span>
          <button onClick={onSolicitarDiagnostico}>Solicitar diagnóstico</button>
          <button onClick={onIrCatalogo}>Catálogo de repuestos</button>
          <button onClick={onIrVehiculo}>Identifica tu vehículo</button>
        </div>

        <div>
          <span>DRIVEMASTER</span>
          <button onClick={() => onIrInicio?.("dm-pasos")}>Cómo funciona</button>
          <button onClick={onRegistroNegocio}>Registra tu negocio</button>
          <button onClick={onIrLogin || onIrAdmin}>Mi cuenta</button>
        </div>

        <div className="dm-footer__signoff">
          <span>PRECISIÓN. CONFIANZA. RENDIMIENTO.</span>
          <strong>
            Siempre hacia
            <br />
            adelante<span>↗</span>
          </strong>
          {/* Lleva a Solicitar diagnóstico, donde empieza una cotización. Antes pasaba el evento
              del clic como nombre de página y terminaba en "Acceso no autorizado". */}
          <button className="dm-footer__wsp" onClick={() => onSolicitarDiagnostico?.()}>
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