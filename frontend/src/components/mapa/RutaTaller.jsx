import { useEffect, useMemo, useState } from "react";
import { MapContainer, Marker, Polyline, Tooltip, useMap } from "react-leaflet";
import CapaBase from "./CapaBase";
import { iconoCliente, iconoTallerElegido } from "./iconos";
import { esCancelacion, rutaEnCarro } from "../../services/geoService";
import { distanciaKm, formatoDistancia } from "../../utils/distancia";
import { enlacesComoLlegar, formatoDuracion } from "../../utils/geo";
import "../../css/mapa.css";

/** Encuadra el mapa para que se vean todos los puntos (la ruta o, sin ella, el cliente y el taller). */
function Encuadrar({ puntos }) {
  const map = useMap();
  useEffect(() => {
    if (puntos.length === 1) map.setView(puntos[0], 16);
    else if (puntos.length > 1) map.fitBounds(puntos, { padding: [32, 32], maxZoom: 16 });
  }, [puntos, map]);
  return null;
}

const valido = (p) => p && Number.isFinite(p.latitud) && Number.isFinite(p.longitud);

/**
 * Taller asignado en el mapa: la ruta en carro desde el punto del cliente (OSRM, una sola consulta al mostrarse),
 * la distancia y el tiempo estimado, y los botones para navegar con Google Maps o Waze.
 * origen: { latitud, longitud } del cliente (opcional). taller: { nombre, direccion, latitud, longitud }.
 */
export default function RutaTaller({ origen, taller }) {
  const conOrigen = valido(origen);
  const conTaller = valido(taller);
  // Las dependencias son los números: origen y taller pueden llegar como objetos nuevos en cada render
  const oLat = conOrigen ? origen.latitud : null;
  const oLng = conOrigen ? origen.longitud : null;
  const tLat = conTaller ? taller.latitud : null;
  const tLng = conTaller ? taller.longitud : null;
  const clave = conOrigen && conTaller ? `${oLat},${oLng};${tLat},${tLng}` : null;
  const [resultado, setResultado] = useState(null); // { clave, ruta } | { clave, error }

  useEffect(() => {
    if (oLat == null || tLat == null) return undefined;
    const control = new AbortController();
    const k = `${oLat},${oLng};${tLat},${tLng}`;
    rutaEnCarro({ latitud: oLat, longitud: oLng }, { latitud: tLat, longitud: tLng }, { signal: control.signal })
      .then((ruta) => setResultado({ clave: k, ruta }))
      .catch((e) => { if (!esCancelacion(e)) setResultado({ clave: k, error: e.message }); });
    return () => control.abort();
  }, [oLat, oLng, tLat, tLng]);

  const actual = resultado?.clave === clave ? resultado : null;
  const cargando = clave != null && !actual;
  const ruta = actual?.ruta;

  // Lo que se encuadra: la ruta o, mientras no la haya, el taller y el cliente
  const puntos = useMemo(() => {
    if (ruta?.puntos?.length) return ruta.puntos;
    if (tLat == null) return [];
    return [[tLat, tLng], ...(oLat != null ? [[oLat, oLng]] : [])];
  }, [ruta, tLat, tLng, oLat, oLng]);

  if (!conTaller) return null;

  const enlaces = enlacesComoLlegar(taller);
  const lineaRecta = conOrigen ? distanciaKm(origen, taller) : null;

  let resumen = null;
  if (ruta) {
    resumen = (
      <>
        <strong>{formatoDuracion(ruta.duracionMin)}</strong> en carro · {formatoDistancia(ruta.distanciaKm).replace(/^a /, "")}
      </>
    );
  } else if (cargando) {
    resumen = "Calculando la ruta en carro…";
  } else if (lineaRecta != null) {
    resumen = <>{formatoDistancia(lineaRecta)} en línea recta</>;
  }

  return (
    <div className="mapa-ruta-taller">
      <div className="mapa-ruta-taller__mapa">
        <MapContainer
          center={[taller.latitud, taller.longitud]}
          zoom={15}
          scrollWheelZoom={false}
          className="mapa-lienzo"
        >
          <CapaBase />
          <Encuadrar puntos={puntos} />
          {ruta && (
            <>
              <Polyline positions={ruta.puntos} pathOptions={{ className: "mapa-ruta-borde", weight: 8, opacity: 1 }} interactive={false} />
              <Polyline positions={ruta.puntos} pathOptions={{ className: "mapa-ruta", weight: 5, opacity: 1 }} interactive={false} />
            </>
          )}
          {conOrigen && (
            <Marker position={[origen.latitud, origen.longitud]} icon={iconoCliente} keyboard={false} interactive={false} />
          )}
          <Marker position={[taller.latitud, taller.longitud]} icon={iconoTallerElegido} title={taller.nombre}>
            <Tooltip direction="top" permanent>{taller.nombre}</Tooltip>
          </Marker>
        </MapContainer>
      </div>

      <div className="mapa-ruta-taller__info">
        {resumen && <p className="mapa-ruta-taller__resumen" aria-live="polite">{resumen}</p>}
        {actual?.error && (
          <p className="mapa-aviso" role="status">
            No pudimos calcular la ruta en carro ahora mismo{lineaRecta != null ? "; te mostramos la distancia en línea recta" : ""}.
          </p>
        )}
        {!conOrigen && (
          <p className="mapa-aviso">No marcaste tu ubicación en la solicitud: los botones te llevan al taller desde donde estés.</p>
        )}
        <div className="mapa-ruta-taller__acciones" role="group" aria-label={`Cómo llegar a ${taller.nombre}`}>
          <a className="mapa-boton mapa-boton--acento-linea" href={enlaces.google} target="_blank" rel="noopener noreferrer">
            Cómo llegar con Google Maps<span className="mapa-sr"> (se abre en otra pestaña)</span>
          </a>
          <a className="mapa-boton" href={enlaces.waze} target="_blank" rel="noopener noreferrer">
            Abrir en Waze<span className="mapa-sr"> (se abre en otra pestaña)</span>
          </a>
        </div>
      </div>
    </div>
  );
}
