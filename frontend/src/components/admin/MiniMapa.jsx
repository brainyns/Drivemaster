import { MapContainer, Marker } from "react-leaflet";
import CapaBase from "../mapa/CapaBase";
import { iconoPunto } from "../mapa/iconos";
import "../../css/mapa.css";

/** Mapa pequeño, solo para ver dónde está un negocio (se puede acercar, no editar). */
export default function MiniMapa({ latitud, longitud, titulo }) {
  return (
    <div className="adm-minimapa" role="img" aria-label={`Ubicación de ${titulo}`}>
      <MapContainer center={[latitud, longitud]} zoom={15} scrollWheelZoom={false} dragging={false}
        doubleClickZoom={false} attributionControl className="mapa-lienzo">
        <CapaBase />
        <Marker position={[latitud, longitud]} icon={iconoPunto} keyboard={false} />
      </MapContainer>
    </div>
  );
}
