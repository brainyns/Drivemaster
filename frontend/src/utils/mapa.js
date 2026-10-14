// Configuración compartida de los mapas (Leaflet + OpenStreetMap, sin API keys).

export const TILES_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
export const TILES_ATRIBUCION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

// Cartagena: centro por defecto cuando no se conoce la ciudad
export const CENTRO_DEFECTO = { latitud: 10.391, longitud: -75.479 };

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

function normalizar(ciudad) {
  const limpio = (ciudad || "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[^a-z]/g, "");
  return limpio.endsWith("dc") ? limpio.slice(0, -2) : limpio;
}

/** Centro aproximado de una ciudad colombiana conocida, o Cartagena. */
export function centroDeCiudad(ciudad) {
  const c = CIUDADES[normalizar(ciudad)];
  return c ? { latitud: c[0], longitud: c[1] } : CENTRO_DEFECTO;
}

/** Coordenadas con 6 decimales (~10 cm), suficiente para ubicar un local. */
export const redondear = (n) => Math.round(n * 1e6) / 1e6;
