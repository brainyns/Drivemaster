// Leaflet calcula la ruta de sus íconos a partir del CSS, y con Vite esa ruta se pierde:
// el marcador sale roto. Se importan las imágenes para que Vite les dé su URL final.
// (Los mapas de la app usan los marcadores de marca de components/mapa/iconos.js; esto cubre el ícono por defecto.)
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
