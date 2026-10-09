// Accesos de la cuenta del CLIENTE: una sola lista para el menú de usuario (Navbar y
// MarketHeader) y el menú móvil. "destino" es el nombre de la página en App.jsx.
// Archivo sin componentes para no romper Fast Refresh.

export const OPCIONES_CLIENTE = [
  { destino: "mis-compras", label: "Mis compras", icono: "box" },
  { destino: "mis-servicios", label: "Mis servicios", icono: "tool" },
  { destino: "mis-vehiculos", label: "Mis vehículos", icono: "car" },
  // Flujo anterior: salen del menú cuando su contenido quede cubierto en Mis compras y Mis servicios
  { destino: "mis-pedidos", label: "Historial de compras", icono: "layers" },
  { destino: "mis-solicitudes", label: "Mis solicitudes", icono: "message" },
];
