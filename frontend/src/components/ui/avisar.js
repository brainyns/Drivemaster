// Avisos flotantes (toasts) de todo el panel. Sin componentes, para no romper Fast Refresh: cualquier pantalla llama a
// avisar("Producto guardado.") y <Avisos /> (Avisos.jsx, montado una vez en App) lo muestra unos segundos. Sirve también después
// de cambiar de pantalla, como al volver a la lista tras guardar un formulario.

export const EVENTO_AVISO = "ui:aviso";
export const DURACION_AVISO_MS = 4500;

let secuencia = 0;

/** Muestra un aviso. tono: ok (por defecto) | error | aviso | info. */
export function avisar(texto, tono = "ok") {
  if (!texto || typeof window === "undefined") return;
  secuencia += 1;
  window.dispatchEvent(new CustomEvent(EVENTO_AVISO, { detail: { id: secuencia, texto, tono } }));
}
