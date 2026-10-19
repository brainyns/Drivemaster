import { TileLayer } from "react-leaflet";
import { TILES_ATRIBUCION, TILES_MAX_ZOOM, TILES_URL } from "../../utils/mapa";

/**
 * Teselas de OpenStreetMap con su atribución. El tema oscuro no cambia de capa: mapa.css le aplica un filtro
 * solo a .mapa-teselas (no a marcadores, rutas ni controles), así que cambia al instante al alternar el tema.
 * Se dejan las opciones de Leaflet por defecto que respetan la política de OSM: sin subdominios, sin precarga
 * extra (keepBuffer normal) y sin tocar el Referer.
 */
export default function CapaBase() {
  return (
    <TileLayer
      url={TILES_URL}
      attribution={TILES_ATRIBUCION}
      maxZoom={TILES_MAX_ZOOM}
      maxNativeZoom={TILES_MAX_ZOOM}
      className="mapa-teselas"
    />
  );
}
