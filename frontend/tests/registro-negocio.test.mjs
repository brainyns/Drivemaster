// Pruebas del registro de negocios por pasos: pasos según el tipo, validación por paso y datos enviados
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  CAMPOS_PASO, datosRegistro, erroresDePasos, pasosDe, primerPasoConErrores, validarNegocio,
} from "../src/pages/pasosRegistroNegocio.js";

const BASE = {
  nombreNegocio: "Taller El Pistón", nit: "", telefono: "300 123 4567",
  ciudad: "Cartagena", barrio: "", direccion: "Calle 30 # 17-25",
  latitud: 10.42, longitud: -75.54,
  especialidades: "Frenos, Motor ,", precioManoObra: "80000", tarifaHora: "", horario: "",
  nombreContacto: "", correo: "taller@ejemplo.com", password: "secreta1", confirmarPassword: "secreta1",
};

test("el taller tiene el paso Servicio y la tienda no", () => {
  assert.deepEqual(pasosDe("TALLER"), ["negocio", "ubicacion", "servicio", "cuenta", "revisar"]);
  assert.deepEqual(pasosDe("TIENDA"), ["negocio", "ubicacion", "cuenta", "revisar"]);
});

test("cada error pertenece a un paso, así se puede validar paso a paso", () => {
  const todos = Object.keys(validarNegocio("TALLER", { ...BASE, nombreNegocio: "", latitud: "", correo: "x", confirmarPassword: "otra" }));
  const campos = Object.values(CAMPOS_PASO).flat();
  for (const campo of todos) assert.ok(campos.includes(campo), campo);
});

test("un paso solo muestra sus errores", () => {
  const form = { ...BASE, nombreNegocio: "", correo: "" };
  assert.deepEqual(Object.keys(erroresDePasos("TALLER", form, ["negocio"])), ["nombreNegocio"]);
  assert.deepEqual(Object.keys(erroresDePasos("TALLER", form, ["cuenta"])), ["correo"]);
  assert.deepEqual(erroresDePasos("TALLER", BASE, ["negocio", "ubicacion", "servicio", "cuenta"]), {});
});

test("las reglas de siempre: el taller necesita el pin y la tienda el horario", () => {
  assert.equal(erroresDePasos("TALLER", { ...BASE, latitud: "" }, ["ubicacion"]).ubicacion, "Marca la ubicación de tu taller en el mapa.");
  assert.equal(erroresDePasos("TIENDA", { ...BASE, latitud: "" }, ["ubicacion"]).ubicacion, undefined);
  assert.match(erroresDePasos("TIENDA", BASE, ["ubicacion"]).horario, /horario de atención/);
});

test("antes de enviar se vuelve al primer paso con errores", () => {
  assert.equal(primerPasoConErrores("TALLER", BASE), null);
  assert.equal(primerPasoConErrores("TALLER", { ...BASE, precioManoObra: "" }), "servicio");
  assert.equal(primerPasoConErrores("TALLER", { ...BASE, ciudad: "", password: "1" }), "ubicacion");
});

test("se envían los mismos datos que antes", () => {
  const taller = datosRegistro("TALLER", BASE);
  assert.deepEqual(taller.especialidades, ["Frenos", "Motor"]);
  assert.equal(taller.precioManoObra, 80000);
  assert.equal(taller.tarifaHora, null);
  assert.equal(taller.nit, null);
  assert.equal(taller.horario, null);
  const tienda = datosRegistro("TIENDA", { ...BASE, horario: " L-V 8-18 " });
  assert.equal(tienda.precioManoObra, undefined);
  assert.equal(tienda.horario, "L-V 8-18");
});

test("la pantalla usa los pasos, un resumen final y contraseñas que se pueden mostrar", () => {
  const pagina = readFileSync(new URL("../src/pages/RegistroNegocio.jsx", import.meta.url), "utf8");
  assert.match(pagina, /<Pasos /);
  assert.match(pagina, /paso === "revisar"/);
  assert.equal((pagina.match(/<CampoClave /g) || []).length, 2);
  assert.match(pagina, /<SelectorUbicacion/);
  assert.doesNotMatch(pagina, /rn-eyebrow/);
});
