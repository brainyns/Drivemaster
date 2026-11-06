// Límite de tiempo de las peticiones al backend. Sin él, con el servidor dormido (Render) o sin red, la pantalla se
// quedaba cargando sin explicación. Módulo aparte para que lo usen http.js y authService.js sin importarse entre sí.

export const LIMITE_MS = 20000;

// Las consultas (GET) esperan más: el backend de Render tarda hasta un minuto en despertar, y mientras tanto la pantalla
// muestra su esqueleto, no un error. Las acciones que guardan conservan los 20 s.
export const LIMITE_LECTURA_MS = 60000;

const limitePorDefecto = (options) => (String(options?.method || "GET").toUpperCase() === "GET" ? LIMITE_LECTURA_MS : LIMITE_MS);

export const MENSAJE_SIN_CONEXION = "No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo";

/** Error de red o de tiempo: el servidor no respondió. Su message es MENSAJE_SIN_CONEXION. */
export class ErrorConexion extends Error {
  constructor(causa) {
    super(MENSAJE_SIN_CONEXION);
    this.name = "ErrorConexion";
    this.cause = causa;
  }
}

export const esErrorConexion = (e) => e?.name === "ErrorConexion" || e?.message === MENSAJE_SIN_CONEXION;

/**
 * fetch que se rinde a los `limite` ms (por defecto, 60 s una consulta y 20 s lo demás). Si quien llama pasa su propio signal y aborta, se respeta su AbortError (no es
 * un problema de conexión). Un fallo de red (TypeError de fetch) o el tiempo vencido lanzan ErrorConexion.
 */
export async function fetchConLimite(url, options = {}, limite = limitePorDefecto(options)) {
  const control = new AbortController();
  let vencido = false;
  const reloj = setTimeout(() => { vencido = true; control.abort(); }, limite);
  const externo = options.signal;
  const alAbortarExterno = () => control.abort();
  if (externo) {
    if (externo.aborted) control.abort();
    else externo.addEventListener("abort", alAbortarExterno, { once: true });
  }
  try {
    return await fetch(url, { ...options, signal: control.signal });
  } catch (e) {
    if (externo?.aborted && !vencido) throw e;
    if (vencido || e instanceof TypeError) throw new ErrorConexion(e);
    throw e;
  } finally {
    clearTimeout(reloj);
    externo?.removeEventListener("abort", alAbortarExterno);
  }
}
