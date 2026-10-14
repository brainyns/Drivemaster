import { useEffect, useRef, useState } from "react";
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { TILES_ATRIBUCION, TILES_URL, centroDeCiudad, redondear } from "../../utils/mapa";
import "../../css/mapa.css";

const NOMINATIM = "https://nominatim.openstreetmap.org/search";
// Política de uso de Nominatim: como máximo una petición por segundo
const INTERVALO_BUSQUEDA_MS = 1000;

const espera = (ms) => new Promise((r) => setTimeout(r, ms));

/** Coloca o mueve el pin con un clic en el mapa. */
function ClicEnMapa({ onElegir }) {
  useMapEvents({ click: (e) => onElegir(e.latlng.lat, e.latlng.lng) });
  return null;
}

/** Lleva la vista al punto pedido cada vez que cambia. */
function Volar({ destino }) {
  const map = useMap();
  useEffect(() => {
    if (destino) map.flyTo([destino.latitud, destino.longitud], destino.zoom ?? map.getZoom(), { duration: 0.6 });
  }, [destino, map]);
  return null;
}

/**
 * Mapa para marcar la ubicación de un negocio. Devuelve las coordenadas con
 * onChange({ latitud, longitud }). Se marca con clic, arrastrando el pin, buscando
 * una dirección (Nominatim) o con la ubicación del dispositivo.
 */
export default function SelectorUbicacion({ value, onChange, ciudad, error, id = "selector-ubicacion" }) {
  const tieneValor = value?.latitud != null && value?.longitud != null;
  const [centroInicial] = useState(() => (tieneValor ? value : centroDeCiudad(ciudad)));
  const [destino, setDestino] = useState(null);
  const [consulta, setConsulta] = useState("");
  const [buscando, setBuscando] = useState(false);
  const [ubicando, setUbicando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const ultimaBusqueda = useRef(0);

  // Si cambia la ciudad y aún no hay pin, el mapa se mueve a esa ciudad
  useEffect(() => {
    if (tieneValor || !ciudad) return;
    const t = setTimeout(() => setDestino({ ...centroDeCiudad(ciudad), zoom: 13 }), 600);
    return () => clearTimeout(t);
  }, [ciudad, tieneValor]);

  const elegir = (lat, lng) => {
    setAviso(null);
    onChange({ latitud: redondear(lat), longitud: redondear(lng) });
  };

  const buscar = async () => {
    const q = consulta.trim();
    if (!q || buscando) return;
    setAviso(null);
    setBuscando(true);
    try {
      const faltan = INTERVALO_BUSQUEDA_MS - (Date.now() - ultimaBusqueda.current);
      if (faltan > 0) await espera(faltan);
      ultimaBusqueda.current = Date.now();

      // La ciudad del formulario ayuda a desambiguar ("Calle 30" existe en todas)
      const texto = ciudad && !q.toLowerCase().includes(ciudad.toLowerCase()) ? `${q}, ${ciudad}` : q;
      const url = `${NOMINATIM}?format=json&countrycodes=co&limit=1&q=${encodeURIComponent(texto)}`;
      const res = await fetch(url, { headers: { "Accept-Language": "es" } });
      if (!res.ok) throw new Error();
      const datos = await res.json();
      if (!datos.length) {
        setAviso({ tipo: "info", texto: "No encontramos esa dirección. Prueba con otra o marca el punto en el mapa." });
        return;
      }
      const lat = Number(datos[0].lat);
      const lng = Number(datos[0].lon);
      elegir(lat, lng);
      setDestino({ latitud: lat, longitud: lng, zoom: 17 });
      setAviso({ tipo: "info", texto: "Revisa que el pin quede sobre tu local; puedes arrastrarlo para ajustarlo." });
    } catch {
      setAviso({ tipo: "error", texto: "No se pudo buscar la dirección. Intenta de nuevo en un momento." });
    } finally {
      setBuscando(false);
    }
  };

  const usarMiUbicacion = () => {
    if (!navigator.geolocation) {
      setAviso({ tipo: "error", texto: "Tu navegador no permite obtener la ubicación." });
      return;
    }
    setAviso(null);
    setUbicando(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        elegir(pos.coords.latitude, pos.coords.longitude);
        setDestino({ latitud: pos.coords.latitude, longitud: pos.coords.longitude, zoom: 17 });
        setUbicando(false);
      },
      (err) => {
        setAviso({
          tipo: "error",
          texto: err.code === err.PERMISSION_DENIED
            ? "No diste permiso para usar tu ubicación. Busca la dirección o marca el punto en el mapa."
            : "No pudimos obtener tu ubicación. Busca la dirección o marca el punto en el mapa.",
        });
        setUbicando(false);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  // Enter en el buscador busca, no envía el formulario que contiene al selector
  const teclaBuscador = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      buscar();
    }
  };

  return (
    <div className={`mapa-selector${error ? " mapa-selector--error" : ""}`}>
      <div className="mapa-selector__barra">
        <label className="mapa-selector__sr" htmlFor={`${id}-buscar`}>Buscar dirección</label>
        <input
          id={`${id}-buscar`}
          className="mapa-selector__input"
          value={consulta}
          onChange={(e) => setConsulta(e.target.value)}
          onKeyDown={teclaBuscador}
          placeholder="Busca la dirección, ej. Calle 30 # 17-25"
        />
        <button type="button" className="mapa-boton mapa-boton--primario" onClick={buscar} disabled={buscando || !consulta.trim()}>
          {buscando ? "Buscando..." : "Buscar"}
        </button>
        <button type="button" className="mapa-boton" onClick={usarMiUbicacion} disabled={ubicando}>
          {ubicando ? "Ubicando..." : "Usar mi ubicación"}
        </button>
      </div>

      <div className="mapa-selector__mapa">
        <MapContainer
          center={[centroInicial.latitud, centroInicial.longitud]}
          zoom={tieneValor ? 16 : 13}
          scrollWheelZoom
          className="mapa-lienzo"
        >
          <TileLayer url={TILES_URL} attribution={TILES_ATRIBUCION} />
          <ClicEnMapa onElegir={elegir} />
          <Volar destino={destino} />
          {tieneValor && (
            <Marker
              position={[value.latitud, value.longitud]}
              draggable
              eventHandlers={{
                dragend: (e) => {
                  const p = e.target.getLatLng();
                  elegir(p.lat, p.lng);
                },
              }}
            />
          )}
        </MapContainer>
      </div>

      <p className="mapa-selector__estado" aria-live="polite">
        {tieneValor
          ? <>Ubicación: <strong>{value.latitud.toFixed(6)}, {value.longitud.toFixed(6)}</strong></>
          : "Haz clic en el mapa para marcar la ubicación de tu local."}
      </p>
      {aviso && (
        <p className={`mapa-aviso${aviso.tipo === "error" ? " mapa-aviso--error" : ""}`} role={aviso.tipo === "error" ? "alert" : "status"}>
          {aviso.texto}
        </p>
      )}
      {error && <span className="mapa-selector__error">{error}</span>}
    </div>
  );
}
