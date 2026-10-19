// Lectura de las respuestas de Photon, Nominatim y OSRM, y enlaces de navegación. Funciones puras, sin red:
// las peticiones viven en services/geoService.js.

const unicos = (lista) => lista.filter((v, i) => v && lista.indexOf(v) === i);

/** "Calle 30" + "17-25" → "Calle 30 # 17-25" (forma colombiana). */
function calleConNumero(calle, numero) {
  if (!calle) return null;
  const n = (numero || "").replace(/^#\s*/, "").trim();
  return n ? `${calle} # ${n}` : calle;
}

/**
 * Texto de búsqueda para Photon: el "#" de las direcciones colombianas ("Calle 30 # 17-25") no ayuda al
 * buscador y "No." tampoco; se quitan y se normalizan los espacios.
 */
export function normalizarConsulta(texto) {
  return (texto || "").replace(/#/g, " ").replace(/\bno\.\s*/gi, " ").replace(/\s+/g, " ").trim();
}

/** Un resultado de Photon (Feature GeoJSON) en la forma que usa el buscador. */
export function resultadoDePhoton(feature) {
  const p = feature?.properties || {};
  const [longitud, latitud] = feature?.geometry?.coordinates || [];
  if (!Number.isFinite(latitud) || !Number.isFinite(longitud)) return null;

  const calle = calleConNumero(p.street, p.housenumber);
  const barrio = p.district || p.locality || null;
  const ciudad = p.city || p.county || null;
  // Un lugar con nombre (centro comercial, taller) va como título y su calle en el detalle
  const titulo = p.name && p.name !== p.street ? p.name : calle || p.name || ciudad || p.state;
  if (!titulo) return null;

  return {
    id: `${p.osm_type || ""}${p.osm_id ?? `${latitud},${longitud}`}`,
    titulo,
    detalle: unicos([titulo !== calle ? calle : null, barrio, ciudad, p.state, p.countrycode !== "CO" ? p.country : null])
      .filter((v) => v !== titulo).join(", "),
    latitud,
    longitud,
    direccion: calle || p.name || null,
    barrio,
    ciudad,
    pais: p.countrycode || null,
  };
}

/** Resultados de Colombia primero, sin repetir el mismo lugar; dentro de cada grupo se respeta el orden de Photon. */
export function resultadosDePhoton(json, pais = "CO") {
  const lista = (json?.features || []).map(resultadoDePhoton).filter(Boolean);
  const vistos = new Set();
  const sinRepetir = lista.filter((r) => {
    const clave = `${r.titulo}|${r.detalle}`;
    if (vistos.has(clave)) return false;
    vistos.add(clave);
    return true;
  });
  return [...sinRepetir.filter((r) => r.pais === pais), ...sinRepetir.filter((r) => r.pais !== pais)];
}

/** Respuesta de /reverse de Nominatim → { direccion, barrio, ciudad, etiqueta }, o null si no hay dirección. */
export function direccionDeNominatim(json) {
  if (!json || json.error) return null;
  const a = json.address || {};
  const direccion = calleConNumero(a.road || a.pedestrian || a.footway, a.house_number);
  const barrio = a.neighbourhood || a.suburb || a.quarter || a.city_district || null;
  const ciudad = a.city || a.town || a.village || a.municipality || a.county || null;
  const etiqueta = unicos([direccion, barrio, ciudad]).join(", ") || json.display_name || null;
  if (!etiqueta) return null;
  return { direccion, barrio, ciudad, etiqueta };
}

/** Respuesta de /route de OSRM → { distanciaKm, duracionMin, puntos: [[lat, lng]] }, o null si no hay ruta. */
export function rutaDeOsrm(json) {
  const ruta = json?.code === "Ok" ? json.routes?.[0] : null;
  if (!ruta) return null;
  return {
    distanciaKm: ruta.distance / 1000,
    duracionMin: ruta.duration / 60,
    puntos: (ruta.geometry?.coordinates || []).map(([lng, lat]) => [lat, lng]),
  };
}

/** "4 min", "1 h 5 min", "2 h". */
export function formatoDuracion(minutos) {
  if (minutos == null || !Number.isFinite(minutos)) return "";
  const total = Math.max(1, Math.round(minutos));
  if (total < 60) return `${total} min`;
  const h = Math.floor(total / 60);
  const m = total % 60;
  return m ? `${h} h ${m} min` : `${h} h`;
}

/** Enlaces gratuitos para navegar hasta un punto con Google Maps o Waze. */
export function enlacesComoLlegar({ latitud, longitud }) {
  const punto = `${latitud},${longitud}`;
  return {
    google: `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(punto)}`,
    waze: `https://waze.com/ul?ll=${encodeURIComponent(punto)}&navigate=yes`,
  };
}
