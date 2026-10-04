/**
 * Logotipo de DriveMaster del mockup: marca en curva + wordmark.
 * `light` invierte el color para fondos oscuros.
 */
export default function Logo({ light = false, onClick, as = "button" }) {
  const clases = `dm-logo ${light ? "dm-logo--light" : ""}`;

  const contenido = (
    <>
      <svg viewBox="0 0 34 28" width="34" height="28" fill="none" aria-hidden="true">
        <path
          d="M5 3h12c10 0 16 9 12 17-2 4-6 5-13 5H1l5-7h11c4 0 6-2 6-5s-2-4-6-4H1z"
          fill="currentColor"
        />
        <path d="m0 12 12 0-4 5H0z" fill="#F28C28" />
      </svg>
      <span>
        Drive<span className="dm-logo__master">Master</span>
        <span className="dm-logo__dot">.</span>
      </span>
    </>
  );

  if (onClick) {
    return (
      <button className={clases} onClick={onClick} aria-label="DriveMaster, inicio">
        {contenido}
      </button>
    );
  }

  const Tag = as;
  return <Tag className={clases}>{contenido}</Tag>;
}