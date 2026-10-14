import "../css/logo.css";

// Logo original (carretera negra) con fondo transparente. Siempre va sobre blanco: en fondos
// oscuros (tema oscuro, footer) lleva una placa blanca, ver logo.css.
const LOGO_SRC = "/images/Drivemaster_logo.png";

/**
 * Logo de DriveMaster: la "D" (recortada del margen transparente del archivo) más el nombre.
 * Siempre lleva al inicio.
 *
 * - size: "sm" | "md" | "lg" (más pequeño en móvil).
 * - conNombre: muestra "DriveMaster." junto a la imagen.
 * - light: para fondos que son oscuros en cualquier tema (footer): nombre en blanco y el logo
 *   sobre su placa blanca.
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
      <span className="dm-logo__placa">
        <span className="dm-logo__marca">
          <img className="dm-logo__img" src={LOGO_SRC} alt="DriveMaster" width="320" height="175" decoding="async" />
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
