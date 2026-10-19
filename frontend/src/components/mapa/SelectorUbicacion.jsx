import { useEffect, useState } from "react";
import { MapContainer, useMap } from "react-leaflet";
import CapaBase from "./CapaBase";
import BarraUbicacion from "./BarraUbicacion";
import EstadoUbicacion from "./EstadoUbicacion";
import PinArrastrable, { ClicEnMapa } from "./PinArrastrable";
import useUbicacionPin from "./useUbicacionPin";
import { CENTRO_COLOMBIA, ZOOM_CIUDAD, ZOOM_COLOMBIA, centroDeCiudad } from "../../utils/mapa";
import "../../css/mapa.css";

/** Lleva la vista al punto pedido cada vez que cambia. */
function Volar({ destino }) {
  const map = useMap();
  useEffect(() => {
    if (destino) map.flyTo([destino.latitud, destino.longitud], destino.zoom ?? map.getZoom(), { duration: 0.6 });
  }, [destino, map]);
  return null;
}

/**
 * Mapa para marcar una ubicación exacta (registro y datos de talleres y tiendas, panel de administración): se
 * busca la dirección (autocompletado), se usa la ubicación del dispositivo o se toca el mapa, y el pin se ajusta
 * arrastrándolo. Devuelve las coordenadas con onChange({ latitud, longitud }) y, cuando se conoce, la dirección
 * legible del punto con onDireccion({ direccion, barrio, ciudad, etiqueta }).
 */
export default function SelectorUbicacion({
  value, onChange, onDireccion, ciudad, error, id = "selector-ubicacion",
  ayuda = "Busca la dirección, usa tu ubicación o toca el mapa para marcar el punto.",
  direccionInicial = null,
}) {
  const tieneValor = value?.latitud != null && value?.longitud != null;
  // Sin pin: la ciudad escrita o, si no se conoce, todo el país
  const [vistaInicial] = useState(() => {
    if (tieneValor) return { centro: value, zoom: 17 };
    const c = centroDeCiudad(ciudad);
    return c ? { centro: c, zoom: ZOOM_CIUDAD + 1 } : { centro: CENTRO_COLOMBIA, zoom: ZOOM_COLOMBIA };
  });
  const [destinoCiudad, setDestinoCiudad] = useState(null);
  const pin = useUbicacionPin({ value, onChange, onDireccion, direccionInicial });

  // Si cambia la ciudad y aún no hay pin, el mapa se mueve a esa ciudad (si es una conocida)
  useEffect(() => {
    const c = centroDeCiudad(ciudad);
    if (tieneValor || !c) return undefined;
    const t = setTimeout(() => setDestinoCiudad({ ...c, zoom: ZOOM_CIUDAD + 1 }), 600);
    return () => clearTimeout(t);
  }, [ciudad, tieneValor]);

  return (
    <div className={`mapa-selector${error ? " mapa-selector--error" : ""}`}>
      <BarraUbicacion
        id={id}
        cerca={tieneValor ? value : centroDeCiudad(ciudad)}
        onElegirSugerencia={pin.elegirSugerencia}
        onUsarMiUbicacion={pin.usarMiUbicacion}
        ubicando={pin.ubicando}
      />

      <div className="mapa-selector__mapa">
        <MapContainer
          center={[vistaInicial.centro.latitud, vistaInicial.centro.longitud]}
          zoom={vistaInicial.zoom}
          scrollWheelZoom
          className="mapa-lienzo"
        >
          <CapaBase />
          <ClicEnMapa onElegir={pin.moverPin} />
          <Volar destino={destinoCiudad} />
          <Volar destino={pin.encuadre} />
          {tieneValor && <PinArrastrable posicion={value} onMover={pin.moverPin} />}
        </MapContainer>
      </div>

      <EstadoUbicacion value={value} direccion={pin.direccion} ayuda={ayuda} aviso={pin.aviso}
        onReintentar={pin.reintentarDireccion} />
      {error && <span className="mapa-selector__error">{error}</span>}
    </div>
  );
}
