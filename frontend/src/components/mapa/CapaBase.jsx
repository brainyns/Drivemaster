import { useSyncExternalStore } from "react";
import { TileLayer } from "react-leaflet";
import { TILES_ATRIBUCION, TILES_CLARO, TILES_OSCURO } from "../../utils/mapa";

// El tema vive en <html data-theme="light|dark"> (lo pone App); sin atributo la app arranca en oscuro
function suscribirTema(avisar) {
  const observador = new MutationObserver(avisar);
  observador.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observador.disconnect();
}
const temaOscuro = () => document.documentElement.getAttribute("data-theme") !== "light";

/** true mientras la app esté en tema oscuro; se actualiza al cambiar el tema. */
function useTemaOscuro() {
  return useSyncExternalStore(suscribirTema, temaOscuro, () => true);
}

/** Teselas CARTO que siguen el tema (Positron en claro, Dark Matter en oscuro), con su atribución. */
export default function CapaBase() {
  const url = useTemaOscuro() ? TILES_OSCURO : TILES_CLARO;
  // La key fuerza a Leaflet a cambiar de capa cuando cambia el tema
  return <TileLayer key={url} url={url} attribution={TILES_ATRIBUCION} subdomains="abcd" maxZoom={20} />;
}
