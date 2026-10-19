// Pruebas de Solicitar diagnóstico: sugerencias de síntomas, datos del vehículo, qué falta para enviar y estructura
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  MIN_SINTOMAS, PASOS_SERVICIO, SUGERENCIAS_SINTOMAS, alternarSugerencia, faltantes, tieneSugerencia,
} from "../src/components/servicio/solicitudDiagnostico.js";
import { formatoKm, formatoMotor } from "../src/utils/vehiculo.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");
const frenos = SUGERENCIAS_SINTOMAS.find((s) => s.id === "frenos");
const motor = SUGERENCIAS_SINTOMAS.find((s) => s.id === "motor");

test("los pasos del servicio son los cinco de la portada", () => {
  assert.deepEqual(PASOS_SERVICIO, ["Diagnóstico", "Taller", "Cotización", "Repuestos", "Instalación"]);
});

test("una sugerencia se agrega al final del texto y al tocarla otra vez se quita", () => {
  const con = alternarSugerencia("Desde ayer.", frenos);
  assert.equal(con, `Desde ayer. ${frenos.texto}`);
  assert.ok(tieneSugerencia(con, frenos));
  const dos = alternarSugerencia(con, motor);
  assert.equal(dos, `Desde ayer. ${frenos.texto} ${motor.texto}`);
  assert.equal(alternarSugerencia(dos, frenos), `Desde ayer. ${motor.texto}`);
  assert.equal(alternarSugerencia("", frenos), frenos.texto);
  assert.equal(alternarSugerencia(frenos.texto, frenos), "");
});

test("hay sugerencias para los síntomas más comunes y todas cumplen el mínimo por sí solas", () => {
  const etiquetas = SUGERENCIAS_SINTOMAS.map((s) => s.etiqueta);
  for (const e of ["Frenos", "Ruido extraño", "Motor", "Luces del tablero", "Batería", "Aire acondicionado"]) {
    assert.ok(etiquetas.includes(e), e);
  }
  assert.ok(SUGERENCIAS_SINTOMAS.every((s) => s.texto.length >= MIN_SINTOMAS));
});

test("el motor se muestra con su etiqueta y unidad", () => {
  assert.equal(formatoMotor("1.2"), "Motor 1.2 L");
  assert.equal(formatoMotor("1.22"), "Motor 1.22 L");
  assert.equal(formatoMotor("1,6"), "Motor 1,6 L");
  assert.equal(formatoMotor("1600"), "Motor 1600 cc");
  assert.equal(formatoMotor("2.0 turbo"), "Motor 2.0 turbo");
  assert.equal(formatoMotor("Motor diésel"), "Motor diésel");
  assert.equal(formatoMotor(""), null);
  assert.equal(formatoMotor(null), null);
  assert.equal(formatoKm(45000), "45.000 km");
  assert.equal(formatoKm(null), null);
});

test("qué falta para enviar, en el orden del formulario y con mensajes en español", () => {
  const vacio = faltantes({ vehiculoId: "", sintomas: "", ciudad: " " });
  assert.deepEqual(vacio.map((f) => f.campo), ["vehiculo", "sintomas", "ciudad"]);
  assert.equal(vacio[1].error, `Cuéntanos qué está pasando (mínimo ${MIN_SINTOMAS} caracteres).`);
  assert.equal(faltantes({ vehiculoId: "v1", sintomas: "Ruido raro", ciudad: "Cali" }).length, 0);
  const corto = faltantes({ vehiculoId: "v1", sintomas: "Ruido", ciudad: "Cali" });
  assert.equal(corto[0].error, "Cuéntanos un poco más: faltan 5 caracteres.");
  assert.equal(faltantes({ vehiculoId: "v1", sintomas: "Ruido ext", ciudad: "Cali" })[0].error, "Cuéntanos un poco más: falta 1 carácter.");
});

test("la página tiene stepper, resumen fijo con la tarifa, barra en celular y no usa emojis", () => {
  const pagina = leer("../src/pages/SolicitarDiagnostico.jsx");
  assert.match(pagina, /<PasosServicio actual=\{1\} \/>/);
  assert.match(pagina, /className="mk-resumen sd-resumen"/);
  assert.match(pagina, /<BarraAccionMovil/);
  assert.match(pagina, /se descuenta del total si aceptas la reparación/);
  assert.doesNotMatch(pagina, /🚗|ss-eyebrow/);
  // Los dos botones envían el mismo formulario
  assert.match(pagina, /form="sd-form"/);
  assert.equal((pagina.match(/<MapaTalleres/g) || []).length, 1);
  // La lógica de envío no cambia: mismo servicio y mismos campos
  assert.match(pagina, /crearSolicitudServicio\(token, \{\s*vehiculoId,\s*sintomas: sintomas\.trim\(\),\s*fotos,/);
});

test("los vehículos son radios nativos dentro de un fieldset con legend", () => {
  const pagina = leer("../src/pages/SolicitarDiagnostico.jsx");
  assert.match(pagina, /<fieldset className="sd-fieldset"[^>]*>\s*<legend className="sd-seccion__titulo" id="sd-t-vehiculo">/);
  assert.match(pagina, /seleccion=\{\{ name: "vehiculo"/);
  assert.match(leer("../src/components/ui/TarjetaVehiculo.jsx"), /type="radio"\s+name=\{seleccion\.name\}/);
  assert.match(leer("../src/css/ui.css"), /\.ui-vehiculo:has\(input:focus-visible\)/);
});
