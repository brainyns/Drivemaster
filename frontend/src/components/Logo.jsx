import "../css/logo.css";

// Ambas variantes tienen exactamente las mismas dimensiones (444×268 px) y caja de contenido.
const LOGO_LIGHT_SRC = "/images/Drivemaster_logo_light.png";
const LOGO_DARK_SRC = "/images/Drivemaster_logo_dark.png";

/**
 * Logo de DriveMaster: la "D" más el nombre.
 * - size: "sm" | "md" | "lg"
 * - conNombre: muestra "DriveMaster." junto a la imagen.
 * - light: fuerza la versión blanca para zonas siempre oscuras (como el footer).
 * - onClick: navegación interna al inicio.
 */
export default function Logo({ size = "md", conNombre = true, light = false, onClick, className = "" }) {
  const clases = ["dm-logo", `dm-logo--${size}`, light && "dm-logo--light", className].filter(Boolean).join(" ");

  const irAlInicio = (e) => {
    if (!onClick) return;
    e.preventDefault();
    onClick();
  };

  return (
    <a href="/" className={clases} onClick={irAlInicio} aria-label="DriveMaster, ir al inicio">
      <span className="dm-logo__placa">
        <span className="dm-logo__marca">
          <img className="dm-logo__img dm-logo__img--light" src={LOGO_LIGHT_SRC} alt="DriveMaster" width="444" height="268" decoding="async" />
          <img className="dm-logo__img dm-logo__img--dark" src={LOGO_DARK_SRC} alt="" aria-hidden="true" width="444" height="268" decoding="async" />
        </span>
      </span>
      {conNombre && (
        <span className="dm-logo__nombre" aria-hidden="true">
          Drive<span className="dm-logo__master">Master</span>
          <span className="dm-logo__dot">.</span>
        </span>
      )}
    </a>
  );
}
