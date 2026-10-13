// El sistema pide menos movimiento (accesibilidad): las animaciones de CSS ya se apagan con
// @media (prefers-reduced-motion); esto apaga las que hace JavaScript (escritura, textos que rotan).
export function sinMovimiento() {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}
