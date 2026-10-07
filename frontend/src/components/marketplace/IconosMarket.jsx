/**
 * Íconos de línea de la sección de compras (SVG propios, sin dependencias). Heredan el color del
 * texto (currentColor) y son decorativos: el significado siempre va en el texto que los acompaña.
 */
function Base({ size = 16, children, ...resto }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...resto}>
      {children}
    </svg>
  );
}

/** Tienda / retiro en el local. */
export function IconoTienda(props) {
  return (
    <Base {...props}>
      <path d="M3 9l1.5-5h15L21 9" />
      <path d="M4 9v11h16V9" />
      <path d="M3 9a3 3 0 006 0 3 3 0 006 0 3 3 0 006 0" />
      <path d="M10 20v-5h4v5" />
    </Base>
  );
}

/** Camión / envío a domicilio. */
export function IconoCamion(props) {
  return (
    <Base {...props}>
      <path d="M2 6h11v10H2z" />
      <path d="M13 9h4l4 4v3h-8z" />
      <circle cx="6.5" cy="17.5" r="1.8" />
      <circle cx="17" cy="17.5" r="1.8" />
    </Base>
  );
}

export function IconoPin(props) {
  return (
    <Base {...props}>
      <path d="M12 21s-7-6.2-7-11.2A7 7 0 0119 9.8C19 14.8 12 21 12 21z" />
      <circle cx="12" cy="10" r="2.5" />
    </Base>
  );
}

/** Candado: pago seguro. */
export function IconoEscudo(props) {
  return (
    <Base {...props}>
      <path d="M12 3l7 3v5c0 4.6-3 8.3-7 10-4-1.7-7-5.4-7-10V6z" />
      <path d="M9 12l2 2 4-4" />
    </Base>
  );
}

/** Engranaje: imagen de reemplazo de un repuesto sin foto. */
export function IconoRepuesto(props) {
  return (
    <Base {...props}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1" />
      <circle cx="12" cy="12" r="6.5" />
    </Base>
  );
}

export function IconoCarrito(props) {
  return (
    <Base {...props}>
      <path d="M3 4h2l2.4 11h10.2L20 7H6.2" />
      <circle cx="9" cy="19.5" r="1.4" />
      <circle cx="17" cy="19.5" r="1.4" />
    </Base>
  );
}

export function IconoDevolucion(props) {
  return (
    <Base {...props}>
      <path d="M4 8h11a5 5 0 010 10H8" />
      <path d="M8 4L4 8l4 4" />
    </Base>
  );
}
