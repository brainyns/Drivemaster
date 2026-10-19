// Marcadores con el estilo de DriveMaster (divIcon con SVG): los colores salen de las clases de mapa.css,
// así siguen los tokens de la marca en los dos temas.
import L from "leaflet";

const FORMA_PIN = "M16 1C7.7 1 1 7.6 1 15.8c0 10.6 13.3 24.3 13.9 24.9a1.5 1.5 0 0 0 2.2 0C17.7 40.1 31 26.4 31 15.8 31 7.6 24.3 1 16 1z";
// Llave (taller), dibujada sobre 24 × 24 y llevada al centro del pin
const LLAVE = "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z";

const pin = (modificador, interior) => L.divIcon({
  className: `mapa-marca mapa-marca--${modificador}`,
  html: `<svg viewBox="0 0 32 42" width="32" height="42" aria-hidden="true" focusable="false">
    <path class="mapa-marca__forma" d="${FORMA_PIN}"/>${interior}</svg>`,
  iconSize: [32, 42],
  iconAnchor: [16, 41],
  popupAnchor: [0, -36],
  tooltipAnchor: [0, -30],
});

const llave = '<g transform="translate(8.6 8.2) scale(0.62)"><path class="mapa-marca__glifo" d="' + LLAVE + '"/></g>';

/** Taller en el mapa (naranja) y taller elegido (azul de marca, más grande). */
export const iconoTaller = pin("taller", llave);
export const iconoTallerElegido = pin("elegido", llave);

/** Punto que marca el usuario (su local o su ubicación para el diagnóstico). */
export const iconoPunto = pin("punto", '<circle class="mapa-marca__centro" cx="16" cy="15.6" r="5.2"/>');

/**
 * Pin del cliente en el mapa de Solicitar diagnóstico: naranja como la marca pero más grande que los talleres,
 * con borde azul y centro de "diana" para distinguirlo de un vistazo.
 */
export const iconoMiPunto = L.divIcon({
  className: "mapa-marca mapa-marca--cliente",
  html: `<svg viewBox="0 0 32 42" width="42" height="55" aria-hidden="true" focusable="false">
    <path class="mapa-marca__forma" d="${FORMA_PIN}"/>
    <circle class="mapa-marca__centro" cx="16" cy="15.6" r="6.4"/><circle class="mapa-marca__nucleo" cx="16" cy="15.6" r="2.9"/></svg>`,
  iconSize: [42, 55],
  iconAnchor: [21, 54],
  tooltipAnchor: [0, -50],
});

/** Punto azul con halo: dónde está el cliente. */
export const iconoCliente = L.divIcon({
  className: "mapa-yo",
  html: '<span class="mapa-yo__halo"></span><span class="mapa-yo__punto"></span>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
  tooltipAnchor: [0, -12],
});

const iconosGrupo = new Map();

/** Burbuja con el número de talleres agrupados; tres tamaños según la cantidad. */
export function iconoGrupo(cantidad) {
  const tam = cantidad < 10 ? 36 : cantidad < 50 ? 44 : 52;
  if (!iconosGrupo.has(cantidad)) {
    iconosGrupo.set(cantidad, L.divIcon({
      className: "mapa-grupo",
      html: `<span class="mapa-grupo__burbuja" style="width:${tam}px;height:${tam}px">${cantidad}</span>`,
      iconSize: [tam, tam],
      iconAnchor: [tam / 2, tam / 2],
    }));
  }
  return iconosGrupo.get(cantidad);
}
