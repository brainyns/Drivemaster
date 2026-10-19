import { useEffect, useRef, useState } from "react";
import { MapContainer, Marker, useMap, useMapEvents } from "react-leaflet";
import CapaBase from "./CapaBase";
import BuscadorDireccion from "./BuscadorDireccion";
import { iconoPunto } from "./iconos";
import { centroDeCiudad, redondear } from "../../utils/mapa";
import { direccionDePunto, esCancelacion, obtenerMiUbicacion } from "../../services/geoService";
import "../../css/mapa.css";

// Espera tras el último movimiento del pin antes de pedir su dirección (el teclado mueve el pin a saltos)
const ESPERA_DIRECCION_MS = 700;
// Con más margen de error que esto se avisa que la ubicación es aproximada
const PRECISION_ACEPTABLE_M = 80;

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
 * Pin que se arrastra con el mouse o el dedo y, con el foco en él, se mueve con las flechas
 * (Mayúscula + flecha da saltos más largos).
 */
function PinArrastrable({ posicion, onMover }) {
  const map = useMap();
  const marcador = useRef(null);
  const onMoverRef = useRef(onMover);
  useEffect(() => { onMoverRef.current = onMover; }, [onMover]);

  useEffect(() => {
    const el = marcador.current?.getElement();
    if (!el) return undefined;
    el.setAttribute("aria-label", "Pin de la ubicación. Arrástralo o usa las flechas del teclado para ajustarlo.");
    const DIRECCIONES = { ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0] };
    const tecla = (e) => {
      const d = DIRECCIONES[e.key];
      if (!d) return;
      e.preventDefault();
      e.stopPropagation();
      const paso = e.shiftKey ? 40 : 8;
      const actual = map.latLngToContainerPoint(marcador.current.getLatLng());
      const nuevo = map.containerPointToLatLng(actual.add([d[0] * paso, d[1] * paso]));
      marcador.current.setLatLng(nuevo);
      map.panInside(nuevo, { padding: [40, 40] });
      onMoverRef.current(nuevo.lat, nuevo.lng);
    };
    el.addEventListener("keydown", tecla);
    return () => el.removeEventListener("keydown", tecla);
  }, [map]);

  return (
    <Marker
      ref={marcador}
      position={[posicion.latitud, posicion.longitud]}
      icon={iconoPunto}
      draggable
      autoPan
      title="Ubicación marcada"
      zIndexOffset={1000}
      eventHandlers={{
        dragend: (e) => {
          const p = e.target.getLatLng();
          onMoverRef.current(p.lat, p.lng);
        },
      }}
    />
  );
}

/**
 * Mapa para marcar una ubicación exacta: se busca la dirección (autocompletado), se usa la ubicación del
 * dispositivo o se toca el mapa, y el pin se ajusta arrastrándolo. Devuelve las coordenadas con
 * onChange({ latitud, longitud }) y, cuando se conoce, la dirección legible del punto con
 * onDireccion({ direccion, barrio, ciudad, etiqueta }).
 */
export default function SelectorUbicacion({
  value, onChange, onDireccion, ciudad, error, id = "selector-ubicacion",
  ayuda = "Busca la dirección, usa tu ubicación o toca el mapa para marcar el punto.",
  direccionInicial = null,
}) {
  const tieneValor = value?.latitud != null && value?.longitud != null;
  const [centroInicial] = useState(() => (tieneValor ? value : centroDeCiudad(ciudad)));
  const [destino, setDestino] = useState(null);
  const [ubicando, setUbicando] = useState(false);
  const [aviso, setAviso] = useState(null);
  // Punto cuya dirección hay que buscar (después de tocar el mapa, arrastrar el pin o usar el GPS)
  const [pendiente, setPendiente] = useState(null);
  const [direccion, setDireccion] = useState(() => (direccionInicial ? { estado: "listo", etiqueta: direccionInicial } : null));

  const onDireccionRef = useRef(onDireccion);
  useEffect(() => { onDireccionRef.current = onDireccion; }, [onDireccion]);

  // Si cambia la ciudad y aún no hay pin, el mapa se mueve a esa ciudad
  useEffect(() => {
    if (tieneValor || !ciudad) return undefined;
    const t = setTimeout(() => setDestino({ ...centroDeCiudad(ciudad), zoom: 13 }), 600);
    return () => clearTimeout(t);
  }, [ciudad, tieneValor]);

  // Dirección del punto con Nominatim: una sola consulta cuando el pin se queda quieto; la anterior se cancela
  useEffect(() => {
    if (!pendiente) return undefined;
    const control = new AbortController();
    const espera = setTimeout(async () => {
      setDireccion({ estado: "buscando" });
      try {
        const d = await direccionDePunto(pendiente, { signal: control.signal });
        setDireccion(d ? { estado: "listo", etiqueta: d.etiqueta } : { estado: "sin-datos" });
        if (d) onDireccionRef.current?.(d);
      } catch (e) {
        // pedirJson siempre termina (tiempo límite): el estado nunca se queda en "buscando"
        if (!esCancelacion(e)) setDireccion({ estado: "error", motivo: e.message });
      }
    }, ESPERA_DIRECCION_MS);
    return () => {
      clearTimeout(espera);
      control.abort();
    };
  }, [pendiente]);

  const marcar = (lat, lng) => {
    const punto = { latitud: redondear(lat), longitud: redondear(lng) };
    onChange(punto);
    return punto;
  };

  // Tocar el mapa, arrastrar el pin o el GPS: la dirección se averigua después
  const moverPin = (lat, lng) => {
    setAviso(null);
    setPendiente(marcar(lat, lng));
  };

  // Una sugerencia del buscador ya trae su dirección
  const elegirSugerencia = (r) => {
    setAviso(null);
    setPendiente(null);
    marcar(r.latitud, r.longitud);
    setDestino({ latitud: r.latitud, longitud: r.longitud, zoom: r.direccion && /\d/.test(r.direccion) ? 18 : 16 });
    const etiqueta = r.detalle ? `${r.titulo}, ${r.detalle}` : r.titulo;
    setDireccion({ estado: "listo", etiqueta });
    onDireccionRef.current?.({ direccion: r.direccion, barrio: r.barrio, ciudad: r.ciudad, etiqueta });
    setAviso({ tipo: "info", texto: "Revisa que el pin quede en el sitio exacto; puedes arrastrarlo para ajustarlo." });
  };

  const usarMiUbicacion = async () => {
    setAviso(null);
    setUbicando(true);
    try {
      const p = await obtenerMiUbicacion();
      moverPin(p.latitud, p.longitud);
      setDestino({ latitud: p.latitud, longitud: p.longitud, zoom: 17 });
      if (p.precision > PRECISION_ACEPTABLE_M) {
        setAviso({ tipo: "info", texto: `Tu ubicación es aproximada (unos ${Math.round(p.precision)} m). Arrastra el pin al sitio exacto.` });
      }
    } catch (e) {
      setAviso({ tipo: "error", texto: e.message });
    } finally {
      setUbicando(false);
    }
  };

  let estadoTexto = ayuda;
  if (tieneValor) {
    if (direccion?.estado === "buscando") estadoTexto = "Buscando la dirección de este punto…";
    else if (direccion?.estado === "listo") estadoTexto = direccion.etiqueta;
    else if (direccion?.estado === "error") {
      estadoTexto = `El punto quedó marcado, pero no pudimos obtener su dirección. ${direccion.motivo || ""}`.trim();
    } else if (direccion?.estado === "sin-datos") estadoTexto = "El punto quedó marcado; no encontramos una dirección para este lugar.";
    else estadoTexto = "Punto marcado en el mapa.";
  }

  return (
    <div className={`mapa-selector${error ? " mapa-selector--error" : ""}`}>
      <div className="mapa-selector__barra">
        <BuscadorDireccion id={`${id}-buscar`} cerca={tieneValor ? value : ciudad ? centroDeCiudad(ciudad) : null} onElegir={elegirSugerencia} />
        <button type="button" className="mapa-boton mapa-boton--ubicacion" onClick={usarMiUbicacion} disabled={ubicando} aria-busy={ubicando}>
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">
            <circle cx="12" cy="12" r="3.5" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" /><circle cx="12" cy="12" r="7.5" />
          </svg>
          {ubicando ? "Ubicando…" : "Usar mi ubicación"}
        </button>
      </div>

      <div className="mapa-selector__mapa">
        <MapContainer
          center={[centroInicial.latitud, centroInicial.longitud]}
          zoom={tieneValor ? 17 : 13}
          scrollWheelZoom
          className="mapa-lienzo"
        >
          <CapaBase />
          <ClicEnMapa onElegir={moverPin} />
          <Volar destino={destino} />
          {tieneValor && <PinArrastrable posicion={value} onMover={moverPin} />}
        </MapContainer>
      </div>

      <div className={`mapa-selector__estado${tieneValor ? " mapa-selector__estado--marcado" : ""}`} aria-live="polite">
        {tieneValor && (
          <svg viewBox="0 0 32 42" width="14" height="18" aria-hidden="true" focusable="false" className="mapa-selector__icono">
            <path d="M16 1C7.7 1 1 7.6 1 15.8c0 10.6 13.3 24.3 13.9 24.9a1.5 1.5 0 0 0 2.2 0C17.7 40.1 31 26.4 31 15.8 31 7.6 24.3 1 16 1z" />
          </svg>
        )}
        <span>
          {estadoTexto}
          {tieneValor && (
            <span className="mapa-selector__coords">{value.latitud.toFixed(6)}, {value.longitud.toFixed(6)}</span>
          )}
        </span>
        {tieneValor && direccion?.estado === "error" && (
          <button type="button" className="mapa-boton mapa-boton--chico mapa-selector__reintentar"
            onClick={() => setPendiente({ latitud: value.latitud, longitud: value.longitud })}>
            Reintentar
          </button>
        )}
      </div>
      {aviso && (
        <p className={`mapa-aviso${aviso.tipo === "error" ? " mapa-aviso--error" : ""}`} role={aviso.tipo === "error" ? "alert" : "status"}>
          {aviso.texto}
        </p>
      )}
      {error && <span className="mapa-selector__error">{error}</span>}
    </div>
  );
}
