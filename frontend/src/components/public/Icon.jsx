const PATHS = {
  arrow: <path d="M4 12h15M13 6l6 6-6 6" />,
  chevron: <path d="m8 10 4 4 4-4" />,
  car: (
    <>
      <path d="m5 8 2-4h10l2 4M3 9h18v9H3zM3 13h18M6 18v3m12-3v3" />
      <path d="M6 10v2m12-2v2" />
    </>
  ),
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m16 16 5 5" />
    </>
  ),
  check: <path d="m5 12 4 4L19 6" />,
  shield: (
    <>
      <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z" />
      <path d="m8 12 3 3 5-6" />
    </>
  ),
  tool: <path d="M21 7a6 6 0 0 1-8 6L5 21l-3-3 8-8a6 6 0 0 1 7-8l-4 4 3 3 5-2Z" />,
  box: <path d="m12 3 9 5v9l-9 5-9-5V8zM3 8l9 5 9-5M12 13v9M7 5l10 6" />,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21v-3a8 8 0 0 1 16 0v3" />
    </>
  ),
  sliders: (
    <>
      <path d="M4 6h16M4 12h16M4 18h16" />
      <path d="M8 3v6m8 0v6m-6 0v6" />
    </>
  ),
  message: (
    <>
      <path d="M21 11a9 9 0 0 1-13 8l-6 2 2-6A9 9 0 1 1 21 11Z" />
      <path d="M8 8c0 4 4 7 7 7l1-2-3-1-1 1-2-2 1-1-1-3Z" />
    </>
  ),
  bolt: <path d="m13 2-9 12h7l-1 8 10-13h-7z" />,
  target: (
    <>
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="4" />
      <path d="M12 1v4m0 14v4M1 12h4m14 0h4" />
    </>
  ),
  layers: (
    <>
      <path d="m12 3 10 5-10 5L2 8zM2 12l10 5 10-5M2 16l10 5 10-5" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
};

/**
 * Set de iconos de línea del mockup. Hereda `currentColor` y `stroke-width: 1.6`.
 */
export default function Icon({ name, size = 20, className = "" }) {
  const contenido = PATHS[name];
  if (!contenido) return null;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {contenido}
    </svg>
  );
}