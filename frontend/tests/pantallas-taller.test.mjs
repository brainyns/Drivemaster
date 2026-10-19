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

test("Inicio: lo urgente va primero y las citas de hoy son solo las de hoy", async () => {
  const { urgentes, citasDeHoy } = await import("../src/pages/taller/resumenTaller.js");
  const ahora = new Date(2026, 9, 19, 9, 0).getTime();
  const en = (h) => new Date(ahora + h * 3600000).toISOString();
  const lista = [
    { id: "lista", estado: "LISTA", fechaCreacion: en(-50) },
    { id: "cotizar", estado: "DIAGNOSTICADA", fechaCreacion: en(-20) },
    { id: "tarde", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: false, fechaLimiteRespuesta: en(10) },
    { id: "ya", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: false, fechaLimiteRespuesta: en(1) },
    { id: "cita", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: true, fechaRevision: en(5) },
    { id: "manana", estado: "ASIGNADA_A_TALLER", aceptadaPorTaller: true, fechaRevision: en(26) },
  ];
  const u = urgentes(lista, ahora);
  assert.deepEqual(u.map((x) => x.s.id), ["ya", "tarde", "cita", "cotizar", "lista"]);
  assert.equal(u[0].tono, "error", "menos de 2 h para responder es crítico");
  assert.equal(u[1].tono, "aviso");
  assert.ok(u.every((x) => x.estado && x.accion && x.pagina), "cada ítem dice su estado con texto y qué hacer");
  assert.deepEqual(citasDeHoy(lista, ahora).map((s) => s.id), ["cita"]);
});

test("Inicio usa el sistema compartido y la barra inferior en celular", () => {
  const pagina = leer("../src/pages/taller/TallerInicio.jsx");
  for (const pieza of ["<EncabezadoPagina", "<Indicador", "<ChipEstado", "<Alerta", "<EstadoVacio", "<Esqueleto", "<BarraAccionMovil"]) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
  assert.ok(!pagina.includes("→"), "sin flechas de texto: el ícono es SVG");
});

test("Solicitudes y Trabajos: filtros con chips, búsqueda con etiqueta y estados compartidos", () => {
  const pagina = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(pagina.includes('className="ui-chip" aria-pressed={filtro === f.key}'), "filtros con ui-chip");
  assert.ok(pagina.includes('<label htmlFor="st-busqueda" className="ui-sr">'), "la búsqueda tiene etiqueta");
  for (const pieza of ["<EstadoVacio", "<Esqueleto", "onReintentar={reintentar}", "<Pasos pasos={PASOS_TRABAJO}"]) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
});
