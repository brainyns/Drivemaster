// Leaflet calcula la ruta de sus íconos a partir del CSS, y con Vite esa ruta se pierde:
// el marcador sale roto. Se importan las imágenes para que Vite les dé su URL final.
import L from "leaflet";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";

delete L.Icon.Default.prototype._getIconUrl;

L.Icon.Default.mergeOptions({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
});

// Pin del taller elegido: mismo dibujo que el de Leaflet, con el color de acento
export const iconoElegido = L.divIcon({
  className: "mapa-pin",
  html: '<span class="mapa-pin__forma mapa-pin__forma--elegido"></span>',
  iconSize: [28, 40],
  iconAnchor: [14, 40],
  popupAnchor: [0, -36],
});

// Pin normal explícito: react-leaflet no restaura el ícono si la prop deja de enviarse
export const iconoNormal = new L.Icon.Default();
