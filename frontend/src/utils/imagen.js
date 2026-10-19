// Reglas de una imagen que sube un usuario (logo del taller o de la tienda, foto de un producto): las mismas del
// backend (ImagenSubida): JPG, PNG, WEBP o GIF, sin SVG, hasta 5 MB. Sin componentes: lo prueban los tests.

export const TIPOS_IMAGEN = ["image/jpeg", "image/png", "image/webp", "image/gif"];
export const IMAGEN_MAX_MB = 5;

/** Error del archivo elegido, o null si sirve. que: cómo se nombra en el mensaje ("La foto", "El logo"). */
export function errorImagen(archivo, que = "La foto") {
  if (!archivo) return null;
  if (!TIPOS_IMAGEN.includes((archivo.type || "").toLowerCase())) return `${que} debe ser una imagen JPG, PNG, WEBP o GIF.`;
  if (archivo.size > IMAGEN_MAX_MB * 1024 * 1024) return `${que} debe pesar menos de ${IMAGEN_MAX_MB} MB.`;
  return null;
}
