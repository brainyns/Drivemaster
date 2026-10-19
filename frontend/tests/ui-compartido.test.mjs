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
