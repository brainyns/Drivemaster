// Pruebas de estructura de las pantallas del cliente: que usen las piezas compartidas (components/ui) y tengan
// sus estados completos (carga, vacío con acción, error con reintento).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

test("Mis vehículos: tarjetas compartidas, campos con etiqueta y los tres estados", () => {
  const p = leer("../src/pages/MisVehiculos.jsx");
  assert.match(p, /<TarjetaVehiculo /);
  assert.match(p, /<Esqueleto variante="tarjetas"/);
  assert.match(p, /<EstadoVacio icono="car"/);
  assert.match(p, /<Alerta onReintentar=\{reintentar\}>/);
  // Todos los campos del alta pasan por Campo (etiqueta asociada); ya no hay <label> sueltos
  assert.doesNotMatch(p, /<label>/);
  assert.match(p, /role="switch"/);
  assert.doesNotMatch(leer("../src/css/servicios.css"), /sv-manto/);
});
