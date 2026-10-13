import "../css/logo.css";

const LOGO_SRC = "/images/Drivemaster_logo.jpg";

/**
 * Logo de DriveMaster: la imagen de la marca (recortada a la "D" sobre una placa blanca, porque
 * el archivo trae fondo claro y mucho margen) más el nombre. Siempre lleva al inicio.
 *
 * - size: "sm" | "md" | "lg" (más pequeño en móvil).
 * - conNombre: muestra "DriveMaster" junto a la imagen.
 * - light: nombre en blanco, para fondos oscuros.
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
        <img className="dm-logo__img" src={LOGO_SRC} alt="DriveMaster" width="1024" height="559" decoding="async" />
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
