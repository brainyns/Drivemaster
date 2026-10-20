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
