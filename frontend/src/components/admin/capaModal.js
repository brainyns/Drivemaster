// Comportamiento común del drawer y del diálogo: foco atrapado, Esc para cerrar
// y devolver el foco al elemento que abrió la capa.

const ENFOCABLES = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/** Mantiene Tab y Shift+Tab dentro del contenedor y cierra con Escape. */
export function manejarTeclas(e, contenedor, onCerrar) {
  // Un diálogo abierto encima de un drawer no debe mover el foco del drawer (los portales burbujean)
  e.stopPropagation();
  if (e.key === "Escape") {
    onCerrar();
    return;
  }
  if (e.key !== "Tab" || !contenedor) return;
  const elementos = [...contenedor.querySelectorAll(ENFOCABLES)];
  if (elementos.length === 0) return;
  const primero = elementos[0];
  const ultimo = elementos[elementos.length - 1];
  if (e.shiftKey && document.activeElement === primero) {
    e.preventDefault();
    ultimo.focus();
  } else if (!e.shiftKey && document.activeElement === ultimo) {
    e.preventDefault();
    primero.focus();
  }
}

/** Al abrir: guarda el foco anterior, enfoca la capa y bloquea el scroll. Devuelve la limpieza. */
export function abrirCapa(contenedor) {
  const anterior = document.activeElement;
  const overflow = document.body.style.overflow;
  document.body.style.overflow = "hidden";
  contenedor?.focus();
  return () => {
    document.body.style.overflow = overflow;
    if (anterior && typeof anterior.focus === "function") anterior.focus();
  };
}
