// Pantallas del rol TALLER: lógica pura de cada una y que usen el sistema compartido (components/ui).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { completitudPerfil, errorHorario, errorTarifa, iniciales } from "../src/pages/taller/perfilTaller.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

const COMPLETO = {
  latitud: 4.6, longitud: -74.1, horario: "Lunes a viernes", tarifaHora: 60000,
  telefono: "300 123 4567", direccion: "Cra 7 # 10-20", especialidades: ["Frenos"],
};

test("Mi taller: el perfil completo cuenta lo que existe en el backend y dice qué falta", () => {
  assert.deepEqual(completitudPerfil(COMPLETO, 1), { porcentaje: 100, faltan: [] });
  const { porcentaje, faltan } = completitudPerfil({ ...COMPLETO, latitud: null, horario: "  " }, 0);
  assert.equal(porcentaje, 57);
  assert.deepEqual(faltan.map((f) => f.clave), ["ubicacion", "horario", "mecanicos"]);
  assert.ok(faltan.every((f) => f.seccion), "lo que el taller puede resolver lleva a su sección");
  const admin = completitudPerfil({ ...COMPLETO, telefono: "" }, 1).faltan[0];
  assert.equal(admin.admin, true, "el teléfono lo registra el administrador");
});

test("Mi taller: la tarifa y el horario se validan en línea con los límites del backend", () => {
  assert.equal(errorTarifa(""), "Escribe la tarifa por hora.");
  assert.equal(errorTarifa("0"), "La tarifa debe ser mayor que 0.");
  assert.match(errorTarifa("1000001"), /no puede pasar/);
  assert.equal(errorTarifa("abc"), "Escribe solo números, sin puntos ni signos.");
  assert.equal(errorTarifa("65000"), null);
  assert.equal(errorHorario("x".repeat(200)), null);
  assert.match(errorHorario("x".repeat(201)), /200 caracteres/);
});

test("Mi taller: iniciales del avatar sin palabras de relleno", () => {
  assert.equal(iniciales("Mecánica Rápida"), "MR");
  assert.equal(iniciales("Taller El Pistón"), "P");
  assert.equal(iniciales(""), "T");
});

test("Mi taller edita por sección y solo con los endpoints que ya existen", () => {
  const pagina = leer("../src/pages/taller/TallerMiTaller.jsx");
  for (const seccion of ["mt-negocio", "mt-servicios", "mt-ubicacion", "mt-horario", "mt-contacto"]) {
    assert.ok(pagina.includes(`id="${seccion}"`), `existe la sección ${seccion}`);
  }
  assert.ok(pagina.includes("actualizarDatosTaller(token, { tarifaHora: Number(tarifa) })"), "la tarifa viaja sola");
  assert.ok(pagina.includes("actualizarDatosTaller(token, { horario })"), "el horario viaja solo");
  assert.ok(pagina.includes("<SelectorUbicacion"), "usa el selector de mapa existente");
  assert.ok(!pagina.includes("taller.rating"), "no muestra la calificación por defecto del registro");
  assert.match(leer("../src/components/servicio/MecanicosTaller.jsx"), /<SeccionEditable/);
});
