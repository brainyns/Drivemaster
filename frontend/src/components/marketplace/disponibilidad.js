// Texto de disponibilidad de un producto del catálogo. Va en un archivo sin componentes
// para no romper Fast Refresh.
export function textoDisponibilidad(producto) {
  if (producto.tipo === "ENCARGO") {
    return producto.diasEntrega ? `Por encargo · ${producto.diasEntrega} días` : "Por encargo";
  }
  if (!producto.disponible) return "Agotado";
  if (producto.stock != null && producto.stock <= 5) return `Quedan ${producto.stock}`;
  return "Disponible";
}
