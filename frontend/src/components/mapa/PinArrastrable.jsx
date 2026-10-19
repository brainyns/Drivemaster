import { useEffect, useRef } from "react";
import { Marker, useMap, useMapEvents } from "react-leaflet";
import { iconoPunto } from "./iconos";

/** Coloca o mueve el pin con un clic en el mapa. */
export function ClicEnMapa({ onElegir }) {
  useMapEvents({ click: (e) => onElegir(e.latlng.lat, e.latlng.lng) });
  return null;
}

/**
 * Pin que se arrastra con el mouse o el dedo y, con el foco en él, se mueve con las flechas
 * (Mayúscula + flecha da saltos más largos). children: p. ej. un Tooltip.
 */
export default function PinArrastrable({ posicion, onMover, icono = iconoPunto, titulo = "Ubicación marcada", children }) {
  const map = useMap();
  const marcador = useRef(null);
  const onMoverRef = useRef(onMover);
  useEffect(() => { onMoverRef.current = onMover; }, [onMover]);

  useEffect(() => {
    const el = marcador.current?.getElement();
    if (!el) return undefined;
    el.setAttribute("aria-label", `${titulo}. Arrástralo o usa las flechas del teclado para ajustarlo.`);
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
  }, [map, titulo]);

  return (
    <Marker
      ref={marcador}
      position={[posicion.latitud, posicion.longitud]}
      icon={icono}
      draggable
      autoPan
      title={titulo}
      zIndexOffset={2000}
      eventHandlers={{
        dragend: (e) => {
          const p = e.target.getLatLng();
          onMoverRef.current(p.lat, p.lng);
        },
      }}
    >
      {children}
    </Marker>
  );
}
