import "../css/logo.css";

// Logo con fondo transparente. La variante "fondo-oscuro" tiene la carretera y la flecha en blanco
// (en el original son negras y desaparecen sobre fondos oscuros).
const LOGO_FONDO_CLARO = "/images/Drivemaster_logo.png";
const LOGO_FONDO_OSCURO = "/images/Drivemaster_logo-fondo-oscuro.png";

/**
 * Logo de DriveMaster: la "D" (recortada del margen transparente del archivo) más el nombre.
 * Muestra la variante del logo que se lee sobre el fondo del tema actual. Siempre lleva al inicio.
 *
 * - size: "sm" | "md" | "lg" (más pequeño en móvil).
 * - conNombre: muestra "DriveMaster." junto a la imagen.
 * - light: para fondos que son oscuros en cualquier tema (footer, panel del login): nombre en
 *   blanco y la variante del logo para fondo oscuro.
 * - onClick: navegación interna al inicio; sin él, el enlace recarga "/".
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
      <span className="dm-logo__marca">
        <img className="dm-logo__img dm-logo__img--fondo-oscuro" src={LOGO_FONDO_OSCURO} alt="DriveMaster"
          width="320" height="175" decoding="async" />
        <img className="dm-logo__img dm-logo__img--fondo-claro" src={LOGO_FONDO_CLARO} alt="DriveMaster"
          width="320" height="175" decoding="async" />
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
