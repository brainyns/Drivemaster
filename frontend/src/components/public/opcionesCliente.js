// Accesos de la cuenta del CLIENTE: una sola lista para el menú de usuario (Navbar y
// MarketHeader) y el menú móvil. "destino" es el nombre de la página en App.jsx.
// Archivo sin componentes para no romper Fast Refresh.

export const OPCIONES_CLIENTE = [
  { destino: "perfil-cliente", label: "Mi perfil", icono: "user" },
  { destino: "mis-compras", label: "Mis compras", icono: "box" },
  { destino: "mis-servicios", label: "Mis servicios", icono: "tool" },
  { destino: "mis-vehiculos", label: "Mis vehículos", icono: "car" },
];
// Fuera del menú, pero las pantallas siguen y se llega desde otras:
// - "Historial de compras" (mis-pedidos): sus ventas están en Mis compras > Anteriores y sus
//   órdenes de taller en Mis servicios > Servicios anteriores; ambas enlazan al historial.
// - "Mis solicitudes" (mis-solicitudes): el aviso de Mis compras > En curso lleva a ella.
