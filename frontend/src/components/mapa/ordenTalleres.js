// Orden de la lista de talleres y puntos a encuadrar en el mapa de Solicitar diagnóstico. Funciones puras.
import { distanciaKm } from "../../utils/distancia.js";

/** true si el taller tiene coordenadas para pintarlo en el mapa. */
export const conUbicacion = (t) => Number.isFinite(t?.latitud) && Number.isFinite(t?.longitud);

const porNombre = (a, b) => (a.nombre || "").localeCompare(b.nombre || "", "es");

/**
 * Talleres con su distancia al cliente y, si ya llegó, su tiempo en carro (tiempos: { [id]: { duracionMin,
 * distanciaRutaKm } }). Con ubicación: primero los que tienen tiempo en carro (los más cercanos), del más rápido
 * al más lento; después el resto por distancia en línea recta. Sin ubicación: por nombre. Los talleres sin
 * coordenadas van siempre al final, sin distancia.
 */
export function ordenarTalleres(talleres, ubicacion, tiempos = {}) {
  const hayUbicacion = conUbicacion(ubicacion);
  const lista = (talleres || []).map((t) => {
    const sinUbicacion = !conUbicacion(t);
    return {
      ...t,
      sinUbicacion,
      distancia: hayUbicacion && !sinUbicacion ? distanciaKm(ubicacion, t) : null,
      enCarro: hayUbicacion && !sinUbicacion ? tiempos[t.id] || null : null,
    };
  });
  const ubicados = lista.filter((t) => !t.sinUbicacion);
  const sinUbicar = lista.filter((t) => t.sinUbicacion).sort(porNombre);
  if (!hayUbicacion) return [...ubicados.sort(porNombre), ...sinUbicar];

  const conTiempo = ubicados.filter((t) => t.enCarro).sort((a, b) => a.enCarro.duracionMin - b.enCarro.duracionMin);
  const sinTiempo = ubicados.filter((t) => !t.enCarro).sort((a, b) => a.distancia - b.distancia);
  return [...conTiempo, ...sinTiempo, ...sinUbicar];
}

/** Puntos [lat, lng] para que en la vista quepan el cliente y sus `cuantos` talleres más cercanos. */
export function puntosCercanos(ubicacion, talleres, cuantos = 3) {
  const cercanos = (talleres || []).filter(conUbicacion)
    .map((t) => ({ t, km: distanciaKm(ubicacion, t) }))
    .sort((a, b) => a.km - b.km)
    .slice(0, cuantos)
    .map(({ t }) => [t.latitud, t.longitud]);
  return [[ubicacion.latitud, ubicacion.longitud], ...cercanos];
}
