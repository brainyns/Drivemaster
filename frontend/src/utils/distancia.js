const RADIO_TIERRA_KM = 6371.0088;

const rad = (g) => (g * Math.PI) / 180;

/** Distancia en km entre dos puntos { latitud, longitud } (fórmula de Haversine). */
export function distanciaKm(a, b) {
  const dLat = rad(b.latitud - a.latitud);
  const dLng = rad(b.longitud - a.longitud);
  const h = Math.sin(dLat / 2) ** 2
    + Math.cos(rad(a.latitud)) * Math.cos(rad(b.latitud)) * Math.sin(dLng / 2) ** 2;
  return RADIO_TIERRA_KM * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

/** "a 850 m" o "a 3.4 km". */
export function formatoDistancia(km) {
  if (km == null) return "";
  return km < 1 ? `a ${Math.round(km * 1000)} m` : `a ${km.toFixed(1)} km`;
}
