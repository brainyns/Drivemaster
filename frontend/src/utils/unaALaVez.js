/**
 * Corta un segundo envío antes de que React deshabilite el botón. candado: { current } de useRef. Una acción a la vez;
 * el candado se suelta con la respuesta, buena o mala.
 */
export async function unaALaVez(candado, accion) {
  if (candado.current) return;
  candado.current = true;
  try {
    await accion();
  } finally {
    candado.current = false;
  }
}
