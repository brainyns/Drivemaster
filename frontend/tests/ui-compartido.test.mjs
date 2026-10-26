// Pruebas del sistema compartido (components/ui + css/ui.css): que las piezas existan con su accesibilidad
// y que las pantallas del cliente las usen en vez de reimplementarlas.
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { datosVehiculo, formatoKm } from "../src/utils/vehiculo.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

test("los datos del vehículo salen con su etiqueta y en orden", () => {
  assert.deepEqual(datosVehiculo({ motor: "1.6", combustible: "Gasolina", kilometraje: 82000 }),
    ["Motor 1.6 L", "Gasolina", "82.000 km"]);
  assert.deepEqual(datosVehiculo({ motor: "", combustible: null, kilometraje: null }), []);
  assert.equal(formatoKm(""), null);
});

test("Campo asocia la etiqueta y pone aria-invalid y aria-describedby al control", () => {
  const campo = leer("../src/components/ui/Campo.jsx");
  assert.match(campo, /<label htmlFor=\{id\}/);
  assert.match(campo, /"aria-invalid": error \? true : undefined/);
  assert.match(campo, /"aria-describedby": describe/);
});

test("la contraseña se puede mostrar con un botón con nombre y estado", () => {
  const clave = leer("../src/components/ui/CampoClave.jsx");
  assert.match(clave, /aria-label=\{ver \? "Ocultar contraseña" : "Mostrar contraseña"\}/);
  assert.match(clave, /aria-pressed=\{ver\}/);
});

test("el chip de estado siempre lleva texto y el punto de color es decorativo", () => {
  const chip = leer("../src/components/ui/ChipEstado.jsx");
  assert.match(chip, /aria-hidden="true"/);
  assert.match(chip, /\{children\}/);
  assert.match(chip, /proceso: "info"/);
});

test("los errores se anuncian de inmediato y los demás avisos con cortesía", () => {
  assert.match(leer("../src/components/ui/Alerta.jsx"), /role=\{tono === "error" \? "alert" : "status"\}/);
});

test("ui.css no repite la franja de color a la izquierda ni usa colores fijos en los estados", () => {
  const css = leer("../src/css/ui.css");
  assert.doesNotMatch(css, /border-left:\s*[2-9]px/);
  assert.doesNotMatch(css, /inset [3-9]px 0 0/);
  assert.match(css, /\.ui-badge--ok\s+\{ color: var\(--ui-ok\)/);
});

test("el chip toma texto y tono de un catálogo de estados", () => {
  const chip = leer("../src/components/ui/ChipEstado.jsx");
  assert.match(chip, /catalogo\[estado\]/);
  assert.match(chip, /\{info\?\.label\}/);
});

test("las piezas nuevas del panel llevan su accesibilidad", () => {
  assert.match(leer("../src/components/ui/Estrellas.jsx"), /role="img" aria-label=\{texto\}/);
  const barra = leer("../src/components/ui/BarraProgreso.jsx");
  assert.match(barra, /role="progressbar"/);
  assert.match(barra, /aria-valuenow=\{pct\}/);
  const seccion = leer("../src/components/ui/SeccionEditable.jsx");
  assert.match(seccion, /aria-labelledby=\{idTitulo\}/);
  assert.match(leer("../src/components/ui/Indicador.jsx"), /type=\{onClick \? "button" : undefined\}/);
});

test("el historial es una lista ordenada con fechas en <time> y la marca es decorativa", () => {
  const historial = leer("../src/components/ui/Historial.jsx");
  assert.match(historial, /<ol className="ui-historial" aria-label=\{etiqueta\}>/);
  assert.match(historial, /<time dateTime=\{p\.fecha\}>/);
  assert.match(historial, /ui-historial__marca" aria-hidden="true"/);
  assert.match(leer("../src/css/ui.css"), /\.ui-field__input\.ui-field__input--codigo \{/);
});

test("la cabecera de negocio y la vista previa son las mismas para Mi taller y Mi tienda", () => {
  const cabecera = leer("../src/components/ui/CabeceraNegocio.jsx");
  assert.match(cabecera, /<h1 className="ui-negocio__nombre">/);
  assert.match(cabecera, /aria-label="Lo que falta"/);
  assert.match(cabecera, /lo registra el administrador/);
  assert.match(leer("../src/components/ui/VistaPrevia.jsx"), /className="ui-vista__marco" aria-hidden="true"/);
  const taller = leer("../src/pages/taller/TallerMiTaller.jsx");
  assert.ok(taller.includes("<CabeceraNegocio") && taller.includes("<VistaPrevia"), "Mi taller usa las piezas compartidas");
  assert.doesNotMatch(leer("../src/css/mi-taller.css"), /\.mt-(identidad|completo|rejilla|vista \{)/, "sin CSS duplicado");
});

test("las tablas pasan a tarjetas en celular con la etiqueta de cada dato", () => {
  const css = leer("../src/css/ui.css");
  assert.match(css, /\.ui-table--tarjetas td\[data-label\]::before \{\s*content: attr\(data-label\);/);
  assert.match(css, /\.ui-table--tarjetas td\.ui-table__titulo/);
});

test("El asistente no se muestra en el acceso y en celular sube por encima de la barra de acción fija", () => {
  const leerArchivo = (r) => readFileSync(new URL(r, import.meta.url), "utf8");
  const chat = leerArchivo("../src/components/chat/chat.css");
  assert.match(chat, /body:has\(\.auth-page\) \.chat-widget-container \{ display: none; \}/);
  assert.match(chat, /body:has\(\.ui-barra-movil\) \.chat-widget-container/);
  assert.match(chat, /bottom: var\(--chat-abajo, 20px\)/);
  assert.ok(leerArchivo("../src/components/ui/BarraAccionMovil.jsx").includes('"--alto-barra-movil"'), "la barra publica su alto");
  // Una animación con fill "both" sobre transform deja al contenedor como bloque de lo fijo: la barra no quedaba abajo
  assert.ok(!/animation: pt-in [^;]*\bboth\b/.test(leerArchivo("../src/css/panel-taller.css")));
});

test("Categorías: con tilde en la interfaz, sin cambiar el valor guardado ni el de los filtros", async () => {
  const { etiquetaCategoria } = await import("../src/utils/categorias.js");
  const { CATEGORIAS } = await import("../src/pages/tienda/reglasProducto.js");
  assert.equal(etiquetaCategoria("Suspension y Direccion"), "Suspensión y Dirección");
  assert.equal(etiquetaCategoria("Frenos"), "Frenos");
  assert.equal(etiquetaCategoria(null), null);
  assert.ok(CATEGORIAS.includes("Suspension y Direccion"), "el valor guardado no cambia");
  const leerArchivo = (r) => readFileSync(new URL(r, import.meta.url), "utf8");
  assert.ok(leerArchivo("../src/pages/ProductoForm.jsx").includes("<option key={c} value={c}>{etiquetaCategoria(c)}</option>"));
  assert.ok(leerArchivo("../src/components/public/CatalogFilters.jsx").includes("onChange={() => onCategoriaChange(c)}"), "el filtro sigue con el valor");
});

test("Conexión: a los 20 s o sin red, un error con mensaje claro; una cancelación propia no cuenta como falta de conexión", async () => {
  const { fetchConLimite, ErrorConexion, MENSAJE_SIN_CONEXION, LIMITE_MS, esErrorConexion } = await import("../src/services/conexion.js");
  assert.equal(LIMITE_MS, 20000);
  const original = globalThis.fetch;
  try {
    // Servidor que nunca responde: se rinde al vencer el límite
    globalThis.fetch = (_u, { signal }) => new Promise((_, rechazar) => signal.addEventListener("abort", () => rechazar(new DOMException("x", "AbortError"))));
    await assert.rejects(fetchConLimite("/api", {}, 20), (e) => e instanceof ErrorConexion && e.message === MENSAJE_SIN_CONEXION);

    // Sin red: fetch lanza TypeError
    globalThis.fetch = async () => { throw new TypeError("Failed to fetch"); };
    await assert.rejects(fetchConLimite("/api"), (e) => esErrorConexion(e));

    // Quien llama cancela (p. ej. el buscador de direcciones): se respeta su AbortError
    globalThis.fetch = (_u, { signal }) => new Promise((_, rechazar) => signal.addEventListener("abort", () => rechazar(new DOMException("x", "AbortError"))));
    const propio = new AbortController();
    const p = fetchConLimite("/api", { signal: propio.signal }, 1000);
    propio.abort();
    await assert.rejects(p, (e) => e.name === "AbortError");

    // Respuesta normal: pasa tal cual
    globalThis.fetch = async () => ({ ok: true, status: 200 });
    assert.equal((await fetchConLimite("/api")).status, 200);
  } finally {
    globalThis.fetch = original;
  }

  const leerSrc = (r) => readFileSync(new URL(`../src/${r}`, import.meta.url), "utf8");
  assert.ok(!/await fetch\(/.test(leerSrc("services/http.js") + leerSrc("services/authService.js")), "todo pasa por fetchConLimite");
  assert.ok(leerSrc("pages/Login.jsx").includes("onReintentar={error === MENSAJE_SIN_CONEXION"));
});

test("Contacto: teléfono colombiano, correo en minúsculas y código de 6 dígitos", async () => {
  const { normalizarTelefono, errorTelefono, formatoTelefono, normalizarCorreo, errorCorreo, errorCodigo, MENSAJE_TELEFONO } =
    await import("../src/pages/contactoNegocio.js");
  assert.equal(normalizarTelefono("+57 300-123-4567"), "3001234567");
  assert.equal(normalizarTelefono("(601) 234 5678"), "6012345678");
  assert.equal(normalizarTelefono("2345678"), null, "los fijos viejos de 7 dígitos ya no sirven");
  assert.equal(normalizarTelefono("609 123 4567"), null);
  assert.equal(errorTelefono("300 123 4567"), null);
  assert.equal(errorTelefono("123"), MENSAJE_TELEFONO);
  assert.match(errorTelefono(""), /Escribe el número/);
  assert.equal(formatoTelefono("3001234567"), "300 123 4567");
  assert.equal(normalizarCorreo("  Ana.Perez@Gmail.COM "), "ana.perez@gmail.com");
  assert.equal(errorCorreo("Ana@Demo.co"), null);
  assert.match(errorCorreo("ana@demo"), /nombre@dominio/);
  assert.equal(errorCodigo("123 456"), null);
  assert.match(errorCodigo("12345"), /6 números/);
});

test("Contacto: Mi taller y Mi tienda usan la misma tarjeta, sin mandar al administrador", () => {
  const src = (r) => readFileSync(new URL(`../src/${r}`, import.meta.url), "utf8");
  const taller = src("pages/taller/TallerMiTaller.jsx");
  const tienda = src("pages/DatosMiTienda.jsx");
  for (const pagina of [taller, tienda]) {
    assert.ok(pagina.includes("<SeccionContacto"), "la tarjeta compartida");
    assert.ok(!/El correo de contacto lo cambia el administrador/.test(pagina));
  }
  assert.ok(!/nota=\{ADMIN\}>\s*<dl className="ui-datos">\s*<Dato etiqueta="Teléfono"/.test(taller), "Contacto ya no remite al administrador");
  assert.ok(taller.includes("guardarTelefonoTaller(token") && tienda.includes("guardarTelefonoTienda(token"));

  const tarjeta = src("components/ui/SeccionContacto.jsx");
  assert.ok(tarjeta.includes('autoComplete="current-password"'), "pide la contraseña actual");
  assert.ok(tarjeta.includes('autoComplete="one-time-code"'), "el código se puede pegar desde el correo");
  assert.ok(tarjeta.includes("cancelarCambioCorreo"), "cancelar anula el código pendiente");

  const auth = src("services/authService.js");
  assert.ok(auth.includes("correo: normalizarCorreo(correo)"), "el login envía el correo en minúsculas");
  assert.ok(auth.includes('new Event("auth:cuenta")') && src("App.jsx").includes('"auth:cuenta", onCuenta'),
    "tras cambiar el correo, App toma la sesión nueva");

  const css = src("css/ui.css");
  assert.match(css, /\.ui-datos--contacto \{ grid-template-columns: minmax\(140px, max-content\) minmax\(0, 1fr\); \}/,
    "el correo usa el resto de la fila");
  assert.match(css, /@media \(max-width: 560px\) \{\s*\.ui-datos--contacto \{ grid-template-columns: minmax\(0, 1fr\); \}/,
    "en celular se apilan");
});

test("Recuperar contraseña: validación de los dos pasos con las reglas del registro", async () => {
  const { erroresSolicitud, erroresConfirmacion } = await import("../src/pages/recuperarClave.js");
  assert.match(erroresSolicitud("").correo, /correo de tu cuenta/);
  assert.match(erroresSolicitud("ana@demo").correo, /nombre@dominio/);
  assert.deepEqual(erroresSolicitud(" Ana@Demo.com "), {});
  assert.deepEqual(erroresConfirmacion({ codigo: "123456", password: "nueva12", confirmar: "nueva12" }), {});
  assert.deepEqual(Object.keys(erroresConfirmacion({ codigo: "12", password: "12345", confirmar: "x" })),
    ["codigo", "password", "confirmar"], "en el orden del formulario");
  assert.match(erroresConfirmacion({ codigo: "123456", password: "x".repeat(129), confirmar: "x".repeat(129) }).password, /Máximo 128/);
});

test("Recuperar contraseña: enlace en el login, sin sesión y sin revelar cuentas", () => {
  const src = (r) => readFileSync(new URL(`../src/${r}`, import.meta.url), "utf8");
  const login = src("pages/Login.jsx");
  assert.ok(login.includes("¿Olvidaste tu contraseña?") && login.includes("<RecuperarClave"));
  const pantalla = src("components/auth/RecuperarClave.jsx");
  assert.ok(pantalla.includes('autoComplete="one-time-code"') && pantalla.includes('autoComplete="new-password"'));
  assert.ok(pantalla.includes("setAviso(mensaje)"), "muestra el mensaje del backend, igual exista o no la cuenta");
  const auth = src("services/authService.js");
  assert.match(auth, /solicitarRecuperacion[\s\S]*?fetchConLimite\(`\$\{API_BASE\}\/auth\/recuperar\/solicitar`/, "no usa apiFetch: no hay sesión");
  assert.match(auth, /confirmarRecuperacion[\s\S]*?\/auth\/recuperar\/confirmar/);
});

test("plural: singular con 1 y plural con el resto, con separador de miles", async () => {
  const { plural } = await import("../src/utils/formato.js");
  assert.equal(plural(1, "unidad", "unidades"), "1 unidad");
  assert.equal(plural(0, "unidad", "unidades"), "0 unidades");
  assert.equal(plural(1200, "unidad", "unidades"), "1.200 unidades");
  assert.equal(plural(1, "producto"), "1 producto");
  assert.equal(plural(3, "día"), "3 días");
});

test("Paginación: 20 por página, el rango que se muestra y los números con huecos", async () => {
  const { POR_PAGINA, paginar, paginasVisibles, rangoMostrado, totalPaginas } = await import("../src/components/ui/reglasPaginacion.js");
  assert.equal(POR_PAGINA, 20);
  const lista = Array.from({ length: 135 }, (_, i) => i + 1);
  assert.equal(totalPaginas(135), 7);
  assert.equal(totalPaginas(0), 1, "una lista vacía tiene una página");
  assert.deepEqual(paginar(lista, 1).slice(0, 2), [1, 2]);
  assert.deepEqual(paginar(lista, 7), lista.slice(120), "la última trae lo que sobra");
  assert.deepEqual(rangoMostrado(1, 135), { desde: 1, hasta: 20 }, "Mostrando 1–20 de 135");
  assert.deepEqual(rangoMostrado(7, 135), { desde: 121, hasta: 135 });
  assert.deepEqual(rangoMostrado(1, 0), { desde: 0, hasta: 0 });
  assert.deepEqual(paginasVisibles(1, 1), [1]);
  assert.deepEqual(paginasVisibles(1, 7), [1, 2, 3, "…", 7]);
  assert.deepEqual(paginasVisibles(6, 12), [1, "…", 4, 5, 6, 7, 8, "…", 12]);
  assert.deepEqual(paginasVisibles(4, 7), [1, 2, 3, 4, 5, 6, 7], "sin huecos de un solo número");
});

test("Paginación: todas las listas del panel usan el mismo componente", () => {
  const paginas = ["Productos", "Compras", "Proveedores", "Movimientos", "PedidosTienda", "ProveedorDetalle", "TallerSolicitudes",
    "taller/TallerCotizaciones", "taller/OrdenesAnteriores", "Aprobaciones", "AdminTalleres", "Liquidaciones", "ProblemasEnvios",
    "admin/RetirosSinRecoger", "Usuarios", "ClientesPlataforma", "NegociosPlataforma", "ProductosPlataforma", "PedidosEnvios"];
  for (const p of paginas) assert.ok(leer(`../src/pages/${p}.jsx`).includes("<Paginacion "), `${p} usa <Paginacion>`);
});
