// Servicios de mapas gratuitos y sin API key: Photon (autocompletar), Nominatim (dirección de un punto),
// OSRM (ruta en carro) y la geolocalización del navegador. Cada petición acepta un AbortSignal para que
// quien la hace pueda cancelar la anterior.
import { BBOX_COLOMBIA, CENTRO_COLOMBIA, NOMINATIM_EMAIL, NOMINATIM_URL, OSRM_URL, PHOTON_URL } from "../utils/mapa.js";
import { direccionDeNominatim, normalizarConsulta, resultadosDePhoton, rutaDeOsrm } from "../utils/geo.js";

export const MIN_CARACTERES_BUSQUEDA = 3;
const MAX_RESULTADOS = 6;

/** true si el error es por haber cancelado la petición (no se muestra al usuario). */
export const esCancelacion = (e) => e?.name === "AbortError";

const cancelada = () => new DOMException("Petición cancelada", "AbortError");

function esperar(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(cancelada());
    const t = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => { clearTimeout(t); reject(cancelada()); }, { once: true });
  });
}

// Ningún servicio externo puede dejar la pantalla esperando: pasado este tiempo la consulta se corta
export const LIMITE_PETICION_MS = 8000;

/**
 * GET a un servicio de mapas con tiempo límite. Devuelve el JSON o lanza un Error con un mensaje en español según
 * lo que pasó (tardó demasiado, sin conexión, límite de consultas, error del servicio). Si quien llama cancela,
 * relanza la cancelación (AbortError) para que no se muestre.
 */
export async function pedirJson(url, { signal, servicio, limiteMs = LIMITE_PETICION_MS } = {}) {
  if (signal?.aborted) throw cancelada();
  const control = new AbortController();
  const cancelar = () => control.abort();
  signal?.addEventListener("abort", cancelar, { once: true });
  let vencida = false;
  const reloj = setTimeout(() => { vencida = true; control.abort(); }, limiteMs);
  try {
    const res = await fetch(url, { signal: control.signal });
    if (res.status === 403 || res.status === 429) {
      throw new Error(`${servicio} limitó las consultas por un momento. Espera unos segundos y vuelve a intentarlo.`);
    }
    if (!res.ok) throw new Error(`${servicio} no está disponible en este momento (error ${res.status}).`);
    return await res.json();
  } catch (e) {
    if (vencida) throw new Error(`${servicio} tardó demasiado en responder. Vuelve a intentarlo.`);
    if (esCancelacion(e)) throw e;
    // fetch rechaza con TypeError cuando no hay red o el servicio está caído (no hay respuesta que leer)
    if (e instanceof TypeError) throw new Error(`No pudimos conectar con ${servicio.toLowerCase()}. Revisa tu conexión.`);
    if (e instanceof SyntaxError) throw new Error(`${servicio} respondió algo que no pudimos leer.`);
    throw e;
  } finally {
    clearTimeout(reloj);
    signal?.removeEventListener("abort", cancelar);
  }
}

/**
 * Sugerencias de direcciones con Photon, acotadas a Colombia y sesgadas hacia `cerca` (el pin o el centro de
 * la ciudad) o, si no hay, hacia el centro del país. Solo con el sesgo, "Calle 30 … Cartagena" devolvía
 * Cartagena (España).
 */
export async function buscarDirecciones(texto, { cerca, signal } = {}) {
  const q = normalizarConsulta(texto);
  if (q.length < MIN_CARACTERES_BUSQUEDA) return [];
  const centro = cerca || CENTRO_COLOMBIA;
  const params = new URLSearchParams({
    q,
    limit: "10",
    lat: String(centro.latitud),
    lon: String(centro.longitud),
    // Con un punto cercano el sesgo es local; sin él, de país
    zoom: cerca ? "12" : "6",
    bbox: BBOX_COLOMBIA,
  });
  const json = await pedirJson(`${PHOTON_URL}/api/?${params}`, { signal, servicio: "El buscador de direcciones" });
  return resultadosDePhoton(json).slice(0, MAX_RESULTADOS);
}

// Política de uso de Nominatim: como máximo una petición por segundo desde la aplicación. Las peticiones se
// encolan; si se cancelan mientras esperan su turno no llegan a enviarse.
const INTERVALO_NOMINATIM_MS = 1100;
let ultimaNominatim = 0;
let colaNominatim = Promise.resolve();

function turnoNominatim(signal) {
  const turno = colaNominatim.then(async () => {
    if (signal?.aborted) throw cancelada();
    const falta = ultimaNominatim + INTERVALO_NOMINATIM_MS - Date.now();
    if (falta > 0) await esperar(falta, signal);
    ultimaNominatim = Date.now();
  });
  colaNominatim = turno.catch(() => {});
  return turno;
}

/**
 * Dirección legible de un punto con Nominatim: { direccion, barrio, ciudad, etiqueta } o null. El navegador no
 * deja cambiar el User-Agent; Nominatim identifica la aplicación por el Referer, que el navegador envía con su
 * política normal (sin Referer, Nominatim responde 403). Tiene tiempo límite: nunca se queda esperando.
 */
export async function direccionDePunto({ latitud, longitud }, { signal } = {}) {
  await turnoNominatim(signal);
  const params = new URLSearchParams({
    format: "jsonv2",
    lat: String(latitud),
    lon: String(longitud),
    zoom: "18",
    addressdetails: "1",
    "accept-language": "es",
  });
  if (NOMINATIM_EMAIL) params.set("email", NOMINATIM_EMAIL);
  const json = await pedirJson(`${NOMINATIM_URL}/reverse?${params}`, { signal, servicio: "El servicio de direcciones" });
  return direccionDeNominatim(json);
}

// Las rutas ya calculadas se reutilizan: volver a abrir el mismo servicio no repite la consulta
const rutasCalculadas = new Map();
const clavePunto = (p) => `${p.latitud.toFixed(5)},${p.longitud.toFixed(5)}`;

/** Ruta en carro con OSRM: { distanciaKm, duracionMin, puntos }. Solo se llama bajo demanda. */
export async function rutaEnCarro(origen, destino, { signal } = {}) {
  const clave = `${clavePunto(origen)};${clavePunto(destino)}`;
  if (rutasCalculadas.has(clave)) return rutasCalculadas.get(clave);

  const tramo = `${origen.longitud},${origen.latitud};${destino.longitud},${destino.latitud}`;
  const json = await pedirJson(`${OSRM_URL}/route/v1/driving/${tramo}?overview=full&geometries=geojson&alternatives=false&steps=false`,
    { signal, servicio: "El servicio de rutas" });
  const ruta = rutaDeOsrm(json);
  if (!ruta) throw new Error("No encontramos una ruta en carro entre los dos puntos.");
  rutasCalculadas.set(clave, ruta);
  return ruta;
}

/** Ubicación del dispositivo: { latitud, longitud, precision } o un Error con un mensaje en español. */
export function obtenerMiUbicacion() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("Tu navegador no permite obtener la ubicación. Busca la dirección o marca el punto en el mapa."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ latitud: pos.coords.latitude, longitud: pos.coords.longitude, precision: pos.coords.accuracy }),
      (err) => reject(new Error(
        err.code === err.PERMISSION_DENIED
          ? "No diste permiso para usar tu ubicación. Puedes activarlo en el candado de la barra de direcciones, o buscar la dirección."
          : err.code === err.TIMEOUT
            ? "Tu ubicación tardó demasiado en llegar. Intenta de nuevo o busca la dirección."
            : "No pudimos obtener tu ubicación. Busca la dirección o marca el punto en el mapa.",
      )),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  });
}
