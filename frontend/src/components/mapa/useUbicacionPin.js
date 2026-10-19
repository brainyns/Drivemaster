import { useEffect, useRef, useState } from "react";
import { redondear } from "../../utils/mapa";
import { direccionDePunto, esCancelacion, obtenerMiUbicacion } from "../../services/geoService";

// Espera tras el último movimiento del pin antes de pedir su dirección (el teclado mueve el pin a saltos)
const ESPERA_DIRECCION_MS = 700;
// Con más margen de error que esto se avisa que la ubicación es aproximada
const PRECISION_ACEPTABLE_M = 80;

/**
 * Estado del pin que marca el usuario, compartido por SelectorUbicacion y el mapa de Solicitar diagnóstico.
 * value / onChange({ latitud, longitud }) son del componente que lo usa (el estado vive arriba, en la página).
 * Devuelve además la dirección legible del punto (Nominatim, una consulta cuando el pin se queda quieto), el
 * aviso para el usuario y `encuadre`: el punto al que llevar la vista tras una búsqueda o el GPS (al arrastrar
 * o tocar el mapa la vista no se mueve, porque el usuario está ajustando).
 */
export default function useUbicacionPin({ value, onChange, onDireccion, direccionInicial = null }) {
  const [ubicando, setUbicando] = useState(false);
  const [aviso, setAviso] = useState(null);
  // Punto cuya dirección hay que buscar (después de tocar el mapa, arrastrar el pin o usar el GPS)
  const [pendiente, setPendiente] = useState(null);
  const [direccion, setDireccion] = useState(() => (direccionInicial ? { estado: "listo", etiqueta: direccionInicial } : null));
  const [encuadre, setEncuadre] = useState(null);

  const onDireccionRef = useRef(onDireccion);
  useEffect(() => { onDireccionRef.current = onDireccion; }, [onDireccion]);

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
    const punto = marcar(r.latitud, r.longitud);
    setEncuadre({ ...punto, zoom: r.direccion && /\d/.test(r.direccion) ? 18 : 16 });
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
      setEncuadre({ latitud: p.latitud, longitud: p.longitud, zoom: 17 });
      if (p.precision > PRECISION_ACEPTABLE_M) {
        setAviso({ tipo: "info", texto: `Tu ubicación es aproximada (unos ${Math.round(p.precision)} m). Arrastra el pin al sitio exacto.` });
      }
    } catch (e) {
      setAviso({ tipo: "error", texto: e.message });
    } finally {
      setUbicando(false);
    }
  };

  const reintentarDireccion = () => {
    if (value?.latitud != null) setPendiente({ latitud: value.latitud, longitud: value.longitud });
  };

  return { ubicando, aviso, direccion, encuadre, moverPin, elegirSugerencia, usarMiUbicacion, reintentarDireccion };
}
