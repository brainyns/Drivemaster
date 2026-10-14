// Pestañas de Cotizaciones del taller (sin dependencias: node --test tests/)
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { FILTROS, VACIO, vencida, contarPorFiltro, filtrar } from "../src/pages/taller/filtrosCotizaciones.js";

const pagina = readFileSync(new URL("../src/pages/taller/TallerCotizaciones.jsx", import.meta.url), "utf8");
const ahora = Date.parse("2026-10-12T12:00:00Z");
const cot = (id, estado, extra = {}) => ({ id, estado, ...extra });

const COTIZACIONES = [
  cot("e1", "EMITIDA", { vigenciaHasta: "2026-10-15T00:00:00Z" }),
  cot("e2", "EMITIDA", { vigenciaHasta: "2026-10-01T00:00:00Z" }), // vigencia pasada: cuenta como vencida
  cot("a1", "APROBADA"),
  cot("r1", "RECHAZADA", { fechaRespuesta: "2026-10-11T09:00:00", motivoRespuesta: "Muy caro" }),
  cot("r2", "RECHAZADA", { fechaRespuesta: "2026-10-10T09:00:00" }),
  cot("v1", "VENCIDA"),
  cot("c1", "CAMBIOS_SOLICITADOS"),
  cot("x1", "REEMPLAZADA"),
];

test("las pestañas van en orden: Emitidas, Aprobadas, Rechazadas, Vencidas", () => {
  assert.deepEqual(FILTROS.map((f) => f.label), ["Emitidas", "Aprobadas", "Rechazadas", "Vencidas"]);
  for (const f of FILTROS) assert.ok(VACIO[f.key], `la pestaña ${f.key} tiene su texto vacío`);
});

test("Rechazadas muestra solo las que el cliente rechazó", () => {
  assert.deepEqual(filtrar(COTIZACIONES, "rechazadas", ahora).map((c) => c.id), ["r1", "r2"]);
});

test("cada pestaña tiene su contador y una rechazada no cuenta en otra pestaña", () => {
  assert.deepEqual(contarPorFiltro(COTIZACIONES, ahora), { emitidas: 1, aprobadas: 1, rechazadas: 2, vencidas: 2 });
  for (const f of FILTROS.filter((x) => x.key !== "rechazadas")) {
    assert.ok(!filtrar(COTIZACIONES, f.key, ahora).some((c) => c.estado === "RECHAZADA"), f.key);
  }
});

test("una emitida con la vigencia pasada cuenta como vencida, no como emitida", () => {
  assert.ok(vencida(COTIZACIONES[1], ahora));
  assert.ok(!vencida(COTIZACIONES[0], ahora));
  assert.ok(!vencida(cot("r", "RECHAZADA", { vigenciaHasta: "2026-10-01T00:00:00Z" }), ahora), "una rechazada nunca es vencida");
});

test("la tarjeta rechazada muestra la fecha de rechazo y el motivo si existe", () => {
  assert.ok(pagina.includes("rechazada {c.fechaRespuesta"), "usa la fecha de la respuesta del cliente");
  assert.ok(pagina.includes("rechazada && c.motivoRespuesta &&"), "el motivo solo aparece si el cliente lo dejó");
  assert.ok(pagina.includes("formatPrecio(c.total)") && pagina.includes("<Etiqueta estado="), "valor y etiqueta de estado");
});
