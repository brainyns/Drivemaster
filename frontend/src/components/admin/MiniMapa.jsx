import { MapContainer, Marker, TileLayer } from "react-leaflet";
import { TILES_ATRIBUCION, TILES_URL } from "../../utils/mapa";

/** Mapa pequeño, solo para ver dónde está un negocio (se puede acercar, no editar). */
export default function MiniMapa({ latitud, longitud, titulo }) {
  return (
    <div className="adm-minimapa" role="img" aria-label={`Ubicación de ${titulo}`}>
      <MapContainer center={[latitud, longitud]} zoom={15} scrollWheelZoom={false} dragging={false}
        doubleClickZoom={false} attributionControl>
        <TileLayer url={TILES_URL} attribution={TILES_ATRIBUCION} />
        <Marker position={[latitud, longitud]} keyboard={false} />
      </MapContainer>
    </div>
  );
}
