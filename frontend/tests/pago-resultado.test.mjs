// Resultado del pago de Wompi: estados, reintentos y límite de tiempo (sin dependencias: node --test tests/)
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  clasificar, debeReintentar, conLimiteDeTiempo, TiempoAgotado,
  MAX_INTENTOS, INTERVALO_REINTENTO_MS, LIMITE_VERIFICACION_MS,
} from "../src/pages/estadoPago.js";

const pagina = readFileSync(new URL("../src/pages/PagoResultado.jsx", import.meta.url), "utf8");

test("cada estado de Wompi se muestra como corresponde", () => {
  assert.equal(clasificar("APPROVED"), "APROBADO");
  assert.equal(clasificar("PENDING"), "PENDIENTE");
  for (const s of ["DECLINED", "VOIDED", "ERROR"]) assert.equal(clasificar(s), "RECHAZADO", s);
  // NO_ENCONTRADO del backend, o una respuesta sin estado: no se sabe, no es un rechazo
  for (const s of ["NO_ENCONTRADO", undefined, ""]) assert.equal(clasificar(s), "NO_VERIFICADO", String(s));
});

test("un pago pendiente se vuelve a consultar durante unos minutos", () => {
  assert.ok(debeReintentar("PENDIENTE", 1));
  assert.ok(debeReintentar("PENDIENTE", MAX_INTENTOS.PENDIENTE - 1));
  assert.ok(!debeReintentar("PENDIENTE", MAX_INTENTOS.PENDIENTE));
  const minutos = (MAX_INTENTOS.PENDIENTE * INTERVALO_REINTENTO_MS) / 60000;
  assert.ok(minutos >= 2 && minutos <= 5, `${minutos} min`);
});

test("aprobado y rechazado son definitivos; sin respuesta se reintenta una vez", () => {
  assert.ok(!debeReintentar("APROBADO", 1));
  assert.ok(!debeReintentar("RECHAZADO", 1));
  assert.ok(debeReintentar("SIN_RESPUESTA", 1));
  assert.ok(!debeReintentar("SIN_RESPUESTA", 2));
  assert.ok(debeReintentar("NO_VERIFICADO", 1));
});

test("dos consultas sin respuesta cubren el despertar de Render (~1 min)", () => {
  assert.ok(MAX_INTENTOS.SIN_RESPUESTA * LIMITE_VERIFICACION_MS >= 60000);
});

test("conLimiteDeTiempo devuelve el resultado si llega a tiempo", async () => {
  assert.equal(await conLimiteDeTiempo(async () => "ok", 1000), "ok");
});

test("conLimiteDeTiempo corta la consulta colgada y cancela la petición", async () => {
  let senal;
  const colgada = (signal) => { senal = signal; return new Promise(() => {}); };
  await assert.rejects(conLimiteDeTiempo(colgada, 20), (e) => e instanceof TiempoAgotado);
  assert.ok(senal.aborted, "la petición se cancela con el signal");
});

test("conLimiteDeTiempo deja pasar los demás errores", async () => {
  await assert.rejects(conLimiteDeTiempo(async () => { throw new Error("500"); }, 1000),
    (e) => !(e instanceof TiempoAgotado) && e.message === "500");
});

test("'No se hizo ningún cobro' solo aparece cuando el pago fue rechazado", () => {
  const ocurrencias = pagina.split("No se hizo ningún cobro").length - 1;
  assert.equal(ocurrencias, 1);
  const pos = pagina.indexOf("No se hizo ningún cobro");
  const inicioRechazado = pagina.indexOf('fase === "RECHAZADO"');
  const siguienteCaso = pagina.indexOf('fase === "SIN_RESPUESTA"');
  assert.ok(inicioRechazado > 0 && inicioRechazado < pos && pos < siguienteCaso);
});

test("el pago pendiente tiene su propio mensaje y el servidor dormido se explica", () => {
  assert.match(pagina, /Tu pago está en proceso/);
  assert.match(pagina, /No vuelvas a pagar/);
  assert.match(pagina, /El servidor no responde/);
});
