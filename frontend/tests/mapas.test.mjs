// Pruebas de los mapas: lectura de Photon, Nominatim y OSRM, enlaces de navegación y reglas de uso de los servicios
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  direccionDeNominatim, enlacesComoLlegar, formatoDuracion, normalizarConsulta, resultadoDePhoton,
  resultadosDePhoton, rutaDeOsrm,
} from "../src/utils/geo.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

const feature = (props, coords = [-75.5460, 10.4220]) => ({ type: "Feature", properties: props, geometry: { type: "Point", coordinates: coords } });

test("la búsqueda quita el # y el No. de las direcciones colombianas", () => {
  assert.equal(normalizarConsulta("  Calle 30 # 17-25 "), "Calle 30 17-25");
  assert.equal(normalizarConsulta("Cra 13 No. 45-20, Bogotá"), "Cra 13 45-20, Bogotá");
});

test("un resultado de Photon con calle y número se escribe a la colombiana", () => {
  const r = resultadoDePhoton(feature({
    osm_type: "N", osm_id: 1, street: "Calle 30", housenumber: "17-25",
    district: "Getsemaní", city: "Cartagena", state: "Bolívar", countrycode: "CO", country: "Colombia",
  }));
  assert.equal(r.titulo, "Calle 30 # 17-25");
  assert.equal(r.detalle, "Getsemaní, Cartagena, Bolívar");
  assert.equal(r.latitud, 10.4220);
  assert.equal(r.longitud, -75.5460);
  assert.equal(r.direccion, "Calle 30 # 17-25");
  assert.equal(r.barrio, "Getsemaní");
  assert.equal(r.ciudad, "Cartagena");
});

test("un lugar con nombre va como título y su calle en el detalle", () => {
  const r = resultadoDePhoton(feature({ name: "Centro Comercial Caribe Plaza", street: "Calle 29D", city: "Cartagena", countrycode: "CO" }));
  assert.equal(r.titulo, "Centro Comercial Caribe Plaza");
  assert.equal(r.detalle, "Calle 29D, Cartagena");
});

test("los resultados de Colombia van primero y sin repetidos; los de otro país muestran el país", () => {
  const lista = resultadosDePhoton({
    features: [
      feature({ osm_id: 1, name: "Cartagena", state: "Murcia", country: "España", countrycode: "ES" }),
      feature({ osm_id: 2, name: "Cartagena", state: "Bolívar", countrycode: "CO" }),
      feature({ osm_id: 3, name: "Cartagena", state: "Bolívar", countrycode: "CO" }),
      { type: "Feature", properties: { name: "Sin geometría" } },
    ],
  });
  assert.equal(lista.length, 2);
  assert.equal(lista[0].pais, "CO");
  assert.match(lista[1].detalle, /España/);
});

test("la dirección de Nominatim toma calle, barrio y ciudad", () => {
  const d = direccionDeNominatim({
    display_name: "17-25, Calle 30, Getsemaní, Cartagena, Bolívar, Colombia",
    address: { road: "Calle 30", house_number: "17-25", neighbourhood: "Getsemaní", city: "Cartagena" },
  });
  assert.deepEqual(d, { direccion: "Calle 30 # 17-25", barrio: "Getsemaní", ciudad: "Cartagena", etiqueta: "Calle 30 # 17-25, Getsemaní, Cartagena" });
  assert.equal(direccionDeNominatim({ error: "Unable to geocode" }), null);
  assert.equal(direccionDeNominatim({ display_name: "Zona rural", address: {} }).etiqueta, "Zona rural");
});

test("la ruta de OSRM llega en km, minutos y puntos [lat, lng]", () => {
  const r = rutaDeOsrm({
    code: "Ok",
    routes: [{ distance: 5300, duration: 720, geometry: { coordinates: [[-75.5, 10.4], [-75.51, 10.41]] } }],
  });
  assert.equal(r.distanciaKm, 5.3);
  assert.equal(r.duracionMin, 12);
  assert.deepEqual(r.puntos, [[10.4, -75.5], [10.41, -75.51]]);
  assert.equal(rutaDeOsrm({ code: "NoRoute", routes: [] }), null);
});

test("la duración se muestra en minutos u horas", () => {
  assert.equal(formatoDuracion(0.2), "1 min");
  assert.equal(formatoDuracion(12.4), "12 min");
  assert.equal(formatoDuracion(65), "1 h 5 min");
  assert.equal(formatoDuracion(120), "2 h");
  assert.equal(formatoDuracion(null), "");
});

test("Cómo llegar abre Google Maps y Waze con las coordenadas", () => {
  const e = enlacesComoLlegar({ latitud: 10.422, longitud: -75.546 });
  assert.equal(e.google, "https://www.google.com/maps/dir/?api=1&destination=10.422%2C-75.546");
  assert.equal(e.waze, "https://waze.com/ul?ll=10.422%2C-75.546&navigate=yes");
});

test("el autocompletado usa Photon con espera de 400 ms y mínimo 3 caracteres, nunca el Nominatim público", () => {
  const buscador = leer("../src/components/mapa/BuscadorDireccion.jsx");
  const servicio = leer("../src/services/geoService.js");
  assert.match(buscador, /const ESPERA_MS = 400;/);
  assert.match(servicio, /MIN_CARACTERES_BUSQUEDA = 3;/);
  assert.match(buscador, /new AbortController\(\)/);
  const buscar = servicio.slice(servicio.indexOf("export async function buscarDirecciones"), servicio.indexOf("// Política de uso de Nominatim"));
  assert.match(buscar, /PHOTON_URL/);
  assert.doesNotMatch(buscar, /NOMINATIM/);
});

test("Nominatim se usa a lo sumo una vez por segundo y las URLs de los servicios salen de variables VITE_", () => {
  const servicio = leer("../src/services/geoService.js");
  const config = leer("../src/utils/mapa.js");
  assert.match(servicio, /INTERVALO_NOMINATIM_MS = 1\d{3};/);
  for (const v of ["VITE_MAP_TILES_URL", "VITE_MAP_TILES_ATTRIBUTION", "VITE_PHOTON_URL", "VITE_NOMINATIM_URL", "VITE_OSRM_URL"]) {
    assert.match(config, new RegExp(v));
  }
  assert.match(config, /OpenStreetMap/);
});

test("el mapa base son las teselas estándar de OSM: un solo host, zoom 19 y atribución con enlace", async () => {
  const { TILES_URL, TILES_ATRIBUCION, TILES_MAX_ZOOM } = await import("../src/utils/mapa.js");
  assert.equal(TILES_URL, "https://tile.openstreetmap.org/{z}/{x}/{y}.png");
  assert.equal(TILES_MAX_ZOOM, 19);
  assert.match(TILES_ATRIBUCION, /href="https:\/\/www\.openstreetmap\.org\/copyright"/);
  assert.match(TILES_ATRIBUCION, /OpenStreetMap<\/a> contributors/);
  // Las props de la capa: nada de subdominios, precarga extra ni cambios al Referer
  const capa = leer("../src/components/mapa/CapaBase.jsx");
  const props = capa.slice(capa.indexOf("<TileLayer"), capa.indexOf("/>", capa.indexOf("<TileLayer")));
  assert.doesNotMatch(props, /subdomains|referrerPolicy|keepBuffer|crossOrigin/);
});

test("el tema oscuro filtra solo la capa de teselas, no marcadores ni controles", () => {
  const css = leer("../src/css/mapa.css");
  assert.match(css, /:root:not\(\[data-theme="light"\]\) \.mapa-teselas \{\s*filter: var\(--mapa-filtro-oscuro\);/);
  assert.match(css, /--mapa-filtro-oscuro: invert\(1\) hue-rotate\(180deg\)/);
  assert.equal((css.match(/filter: var\(--mapa-filtro-oscuro\)/g) || []).length, 1);
  assert.match(leer("../src/components/mapa/CapaBase.jsx"), /className="mapa-teselas"/);
});

test("no queda ninguna referencia a CARTO en el frontend", () => {
  for (const archivo of ["../src/utils/mapa.js", "../src/components/mapa/CapaBase.jsx", "../src/css/mapa.css",
    "../src/services/geoService.js", "../Dockerfile"]) {
    assert.doesNotMatch(leer(archivo), /cartocdn|carto\.com|CARTO/);
  }
});

// fetch simulado: responde lo que se le diga o se queda colgado hasta que lo cancelen
function simularFetch(respuesta) {
  const original = globalThis.fetch;
  globalThis.fetch = (url, { signal } = {}) => new Promise((resolve, reject) => {
    signal?.addEventListener("abort", () => reject(new DOMException("abortada", "AbortError")), { once: true });
    if (respuesta === "colgada") return;
    if (respuesta instanceof Error) return reject(respuesta);
    resolve(respuesta);
  });
  return () => { globalThis.fetch = original; };
}

test("una consulta que no responde termina con un mensaje en español en vez de quedarse esperando", async () => {
  const { pedirJson } = await import("../src/services/geoService.js");
  const restaurar = simularFetch("colgada");
  try {
    await assert.rejects(pedirJson("https://x/reverse", { servicio: "El servicio de direcciones", limiteMs: 50 }),
      { message: "El servicio de direcciones tardó demasiado en responder. Vuelve a intentarlo." });
  } finally { restaurar(); }
});

test("límite de consultas, servicio caído y sin conexión dan mensajes distintos; cancelar no muestra error", async () => {
  const { pedirJson, esCancelacion } = await import("../src/services/geoService.js");
  const opciones = { servicio: "El servicio de direcciones", limiteMs: 1000 };

  let restaurar = simularFetch({ ok: false, status: 429 });
  await assert.rejects(pedirJson("https://x", opciones), /limitó las consultas/);
  restaurar();

  restaurar = simularFetch({ ok: false, status: 503 });
  await assert.rejects(pedirJson("https://x", opciones), /no está disponible en este momento \(error 503\)/);
  restaurar();

  restaurar = simularFetch(new TypeError("Failed to fetch"));
  await assert.rejects(pedirJson("https://x", opciones), /No pudimos conectar con el servicio de direcciones/);
  restaurar();

  restaurar = simularFetch({ ok: true, status: 200, json: async () => ({ address: { road: "Calle 30" } }) });
  assert.deepEqual(await pedirJson("https://x", opciones), { address: { road: "Calle 30" } });
  restaurar();

  restaurar = simularFetch("colgada");
  const control = new AbortController();
  const pedido = pedirJson("https://x", { ...opciones, signal: control.signal });
  control.abort();
  await assert.rejects(pedido, (e) => esCancelacion(e));
  restaurar();
});

test("el mapa abre en la ciudad aunque venga con su nombre oficial", async () => {
  const { centroDeCiudad } = await import("../src/utils/mapa.js");
  assert.deepEqual(centroDeCiudad("Cartagena de Indias"), centroDeCiudad("Cartagena"));
  assert.deepEqual(centroDeCiudad("Santiago de Cali"), { latitud: 3.452, longitud: -76.532 });
  assert.deepEqual(centroDeCiudad("Bogotá D.C."), { latitud: 4.711, longitud: -74.072 });
});

test("el autocompletado se acota a Colombia", () => {
  assert.match(leer("../src/services/geoService.js"), /bbox: BBOX_COLOMBIA/);
});

test("ningún mapa usa la API de Google Maps (solo los enlaces gratuitos de Cómo llegar)", () => {
  for (const archivo of ["../src/utils/mapa.js", "../src/components/mapa/CapaBase.jsx", "../src/components/admin/MiniMapa.jsx"]) {
    assert.doesNotMatch(leer(archivo), /maps\.googleapis/);
  }
});
