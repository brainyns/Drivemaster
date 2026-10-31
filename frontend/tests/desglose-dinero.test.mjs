import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { ESTADOS_PAGO_NEGOCIO, filaComision, filaComisionResumen, textoConceptoPago, textoPago } from "../src/components/servicio/reglasDesglose.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");
const pesos = (t) => t.replace(/ /g, " ");

const linea = (cambios = {}) => ({ estado: "PAGADO", comisionPct: 10, comisionBase: 200000, comision: 20000, estadoComision: "DESCONTADA", ...cambios });

test("la comisión descontada explica su base: sin IVA", () => {
  const f = filaComision(linea());
  assert.equal(f.etiqueta, "Comisión DriveMaster (10 %)");
  assert.ok(pesos(f.valor).startsWith("− "));
  assert.ok(pesos(f.nota).includes("10 % sobre") && f.nota.includes("sin IVA"));
});

test("con la bandera apagada la comisión se ve, pero dice que no se descuenta", () => {
  const f = filaComision(linea({ estadoComision: "TODAVIA_NO" }));
  assert.equal(f.nota, "No se descuenta todavía");
  assert.ok(!f.valor.startsWith("−"));
});

test("con la bandera encendida, una línea anterior dice que no se descontó y no inventa el monto", () => {
  const f = filaComision({ estadoComision: "NO_EN_ESTE_PAGO", comision: null, comisionPct: null });
  assert.equal(f.etiqueta, "Comisión DriveMaster");
  assert.equal(f.valor, "—");
  assert.equal(f.nota, "No se descontó en este pago");
});

test("el porcentaje decimal se escribe con coma", () => {
  assert.equal(filaComision(linea({ comisionPct: 7.5 })).etiqueta, "Comisión DriveMaster (7,5 %)");
});

test("los totales de un informe siguen la misma regla", () => {
  const apagada = filaComisionResumen({ comisionSeDescuenta: false, comision: 20000, comisionDescontada: 0 });
  assert.equal(apagada.nota, "No se descuenta todavía");
  const encendida = filaComisionResumen({ comisionSeDescuenta: true, comision: 20000, comisionDescontada: 15000 });
  assert.ok(pesos(encendida.valor).includes("15.000"), "muestra lo que sí se descontó, no lo calculado");
});

test("cada estado del pago tiene texto y lo simulado no se llama pagado", () => {
  for (const estado of ["EN_ESPERA", "LISTO", "PAGADO", "SIMULADO", "ANULADO"]) {
    assert.ok(ESTADOS_PAGO_NEGOCIO[estado]?.label, estado);
    assert.ok(textoPago({ estado }).length > 0, estado);
  }
  assert.notEqual(ESTADOS_PAGO_NEGOCIO.SIMULADO.label, ESTADOS_PAGO_NEGOCIO.PAGADO.label);
  assert.ok(textoPago({ estado: "SIMULADO" }).includes("no se movió dinero"));
});

test("un pago hecho dice cuándo y con qué referencia", () => {
  const t = textoPago({ estado: "PAGADO", fechaPago: "2026-10-20T10:00:00", referencia: "WOMPI-12-ab34" });
  assert.ok(t.startsWith("Transferido el") && t.includes("Ref. WOMPI-12-ab34"), t);
  assert.equal(textoPago({ estado: "PAGADO" }), "Transferido", "sin datos no deja frases a medias");
});

test("los conceptos se nombran en palabras", () => {
  assert.equal(textoConceptoPago("DIAGNOSTICO"), "Diagnóstico");
  assert.equal(textoConceptoPago("OTRO"), "Pago");
});

test("el desglose está en el pedido de la tienda, en la solicitud del taller y en sus informes", () => {
  const tienda = leer("../src/pages/PedidosTienda.jsx");
  assert.ok(tienda.includes("<DesgloseDinero token={token} pedidoId={p.id}"));
  assert.ok(tienda.includes('p.tipoMotivoNoDespacho === "PAGO_NO_APLICADO"'), "un pedido cancelado por el pago no parece culpa de la tienda");
  const taller = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(taller.includes("<DesgloseDinero token={token} ordenId={s.id}"));
  assert.ok(!taller.slice(taller.indexOf("function SeguimientoTaller"), taller.indexOf("<ConfirmarRecibidos")).includes("<DesgloseDinero"),
    "el seguimiento solo existe con cotización: una orden con solo el diagnóstico también tiene pago");
  assert.ok(leer("../src/pages/InformesTienda.jsx").includes("<ResumenDinero resumen={l.desglose} />"));
  assert.ok(leer("../src/pages/taller/TallerInformes.jsx").includes("<ResumenDinero resumen={desglose} />"));
});

test("la cotización no le promete al taller más de lo que recibe", () => {
  const c = leer("../src/components/servicio/CotizacionDetalle.jsx");
  assert.ok(c.includes("c.recibeTaller"), "la cifra viene del backend");
  assert.ok(!c.includes("comisionInformativa"), "esa cifra incluye los repuestos de las tiendas");
  assert.ok(c.includes("no se descuenta todavía"));
});
