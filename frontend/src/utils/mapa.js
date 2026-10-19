// Configuración compartida de los mapas (Leaflet + datos de OpenStreetMap, sin API keys).
// Cada servicio se puede cambiar con una variable VITE_ (p. ej. por un servidor propio en el HomeLab).

const env = import.meta.env ?? {};

// Teselas estándar de OpenStreetMap (sin API key ni registro). Su política de uso: un solo host, sin subdominios
// a/b/c, sin precarga masiva y con el Referer normal del navegador. En tema oscuro se oscurecen con un filtro CSS
// (mapa.css), porque OSM no tiene versión oscura.
export const TILES_URL = env.VITE_MAP_TILES_URL || "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
export const TILES_ATRIBUCION = env.VITE_MAP_TILES_ATTRIBUTION
  || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
export const TILES_MAX_ZOOM = 19;

// Autocompletado (Photon), dirección de un punto (Nominatim) y rutas en carro (OSRM)
export const PHOTON_URL = (env.VITE_PHOTON_URL || "https://photon.komoot.io").replace(/\/+$/, "");
export const NOMINATIM_URL = (env.VITE_NOMINATIM_URL || "https://nominatim.openstreetmap.org").replace(/\/+$/, "");
// Correo de contacto opcional para Nominatim (su política lo pide si se hacen muchas consultas)
export const NOMINATIM_EMAIL = env.VITE_NOMINATIM_EMAIL || "";
export const OSRM_URL = (env.VITE_OSRM_URL || "https://router.project-osrm.org").replace(/\/+$/, "");

// Centro de Colombia: vista del país (con ZOOM_COLOMBIA) y sesgo del autocompletado cuando no se conoce la ciudad
// ni la ubicación
export const CENTRO_COLOMBIA = { latitud: 4.5709, longitud: -74.2973 };
export const ZOOM_COLOMBIA = 5;
// Zoom con el que se abre el mapa en una ciudad
export const ZOOM_CIUDAD = 12;
// Rectángulo de Colombia (con San Andrés) que acota el autocompletado: oeste, sur, este, norte
export const BBOX_COLOMBIA = "-82,-4.3,-66.8,13.6";

// Centros aproximados para abrir el mapa en la ciudad del negocio o del cliente
const CIUDADES = {
  cartagena: [10.391, -75.479],
  bogota: [4.711, -74.072],
  medellin: [6.244, -75.581],
  cali: [3.452, -76.532],
  barranquilla: [10.964, -74.796],
  bucaramanga: [7.119, -73.122],
  santamarta: [11.240, -74.199],
  cucuta: [7.893, -72.508],
  pereira: [4.813, -75.696],
  manizales: [5.070, -75.513],
  ibague: [4.438, -75.232],
  villavicencio: [4.142, -73.626],
  pasto: [1.213, -77.281],
  monteria: [8.748, -75.881],
  sincelejo: [9.304, -75.397],
  valledupar: [10.463, -73.253],
  neiva: [2.927, -75.281],
  armenia: [4.533, -75.681],
  tunja: [5.535, -73.367],
  popayan: [2.444, -76.614],
};

// Nombres oficiales que devuelven los geocodificadores ("Cartagena de Indias") y el nombre corto de uso diario.
// La misma lista está en AsignacionTallerService del backend.
const ALIAS = {
  cartagenadeindias: "cartagena",
  santafedebogota: "bogota",
  santiagodecali: "cali",
  sanjosedecucuta: "cucuta",
};

function normalizar(ciudad) {
  const limpio = (ciudad || "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[^a-z]/g, "");
  const sinDc = ALIAS[limpio] || (limpio.endsWith("dc") ? limpio.slice(0, -2) : limpio);
  return ALIAS[sinDc] || sinDc;
}

/** Centro aproximado de una ciudad colombiana conocida, o null si no está en la lista (nunca un punto inventado). */
export function centroDeCiudad(ciudad) {
  const c = CIUDADES[normalizar(ciudad)];
  return c ? { latitud: c[0], longitud: c[1] } : null;
}

/** Coordenadas con 6 decimales (~10 cm), suficiente para ubicar un local. */
export const redondear = (n) => Math.round(n * 1e6) / 1e6;
