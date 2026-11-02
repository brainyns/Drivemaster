// "Mi inventario" del taller y las piezas del taller en la cotización: cifras, origen de cada repuesto y privacidad.
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { cifrasPieza, estadoEnCamino, pct, precioConRecargo } from "../src/pages/taller/reglasInventario.js";
import { claveOferta, etiquetaOrigen, vendedor } from "../src/components/servicio/reglasRepuestos.js";
import { lineasIva } from "../src/components/servicio/desgloseIva.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

test("recargo, margen y ganancia después de comisión son tres cifras distintas, como en el backend", () => {
  assert.deepEqual(cifrasPieza(135_000, 100_000, 10), { recargoPct: 35, margenPct: 25.9, comision: 13_500, ganancia: 21_500, enPerdida: false });
  const perdida = cifrasPieza(105_000, 100_000, 10);
  assert.equal(perdida.ganancia, -5_500);
  assert.equal(perdida.enPerdida, true);
  assert.equal(cifrasPieza(0, 100_000, 10), null, "sin precio no hay cifras");
  assert.equal(cifrasPieza(135_000, null, 10), null, "sin costo tampoco");
});

test("el recargo sugerido lleva el costo al precio y se escribe en español", () => {
  assert.equal(precioConRecargo(100_000, 35), 135_000);
  assert.equal(precioConRecargo(33_333, 35), 45_000);
  assert.equal(pct(25.9), "25,9 %");
  assert.equal(pct(-5), "−5 %");
  assert.equal(pct(null), "—");
});

test("lo que está en camino dice qué falta y nunca se ofrece como stock", () => {
  assert.equal(estadoEnCamino({ estado: "PENDIENTE", entrega: "ENVIO" }).texto, "Esperando a la tienda");
  assert.equal(estadoEnCamino({ estado: "DESPACHADO", entrega: "ENVIO", puedeConfirmar: true }).texto, "En camino");
  assert.equal(estadoEnCamino({ estado: "LISTO_PARA_RETIRAR", entrega: "RETIRO" }).texto, "Listo para retirar");
  assert.equal(estadoEnCamino({ estado: "RETIRADO", entrega: "RETIRO", puedeConfirmar: true }).texto, "Retirado");
  assert.equal(estadoEnCamino({ estado: "DESPACHADO", entrega: "ENVIO", problemaReportado: true }).texto, "Problema reportado");
  assert.match(leer("../src/pages/taller/TallerInventario.jsx"), /no se puede usar en una cotización/);
});

test("la misma pieza del inventario y de una tienda son dos líneas distintas", () => {
  const propia = { origen: "TALLER", piezaTallerId: "t1|REF:BP1" };
  const deTienda = { productoId: "p1", tiendaNombre: "Repuestos Sur" };
  assert.notEqual(claveOferta(propia), claveOferta(deTienda));
  assert.equal(etiquetaOrigen(propia), "Disponible en el taller");
  assert.equal(etiquetaOrigen(deTienda), "Se pide a tienda");
  assert.equal(vendedor(propia), "Tu inventario");
  assert.equal(vendedor(deTienda), "Repuestos Sur");
});

test("con piezas del taller el IVA dice de quién es cada parte, igual que el PDF", () => {
  const c = { ivaRepuestosPct: 19, ivaManoObraPct: 0, subtotalRepuestos: 50_000, ivaRepuestos: 9_500,
    subtotalRepuestosTaller: 100_000, ivaRepuestosTaller: 0, subtotalManoObra: 10_000, ivaManoObra: 0 };
  assert.deepEqual(lineasIva(c), [{ texto: "IVA repuestos de tiendas (19 %)", monto: 9_500 }, { texto: "IVA del taller (0 %)", monto: 0 }]);
  assert.deepEqual(lineasIva({ ...c, subtotalRepuestosTaller: 0 }),
    [{ texto: "IVA repuestos (19 %)", monto: 9_500 }, { texto: "IVA mano de obra (0 %)", monto: 0 }], "sin piezas, como siempre");
});

test("el detalle de la cotización separa repuestos del taller, de tiendas y mano de obra, con su etiqueta", () => {
  const detalle = leer("../src/components/servicio/CotizacionDetalle.jsx");
  for (const texto of ['"Repuestos del taller"', '"Repuestos de tiendas"', '>Mano de obra<', "etiquetaOrigen(i)"]) assert.ok(detalle.includes(texto), texto);
});

test("costo y margen solo se piden en Mi inventario, y solo a la ruta del taller", () => {
  assert.match(leer("../src/services/inventarioTallerService.js"), /\/api\/inventario-taller/);
  for (const ruta of ["../src/components/servicio/CotizacionDetalle.jsx", "../src/pages/MisServicios.jsx", "../src/pages/CotizacionPublica.jsx"]) {
    const fuente = leer(ruta);
    assert.ok(!/costoUnitario|margenPct|gananciaDespuesComision|inventarioTallerService/.test(fuente), `${ruta} no toca datos del taller`);
  }
});

test("el taller compra en el mismo catálogo y vuelve a Mi inventario, sin el vehículo del cliente", () => {
  const app = leer("../src/App.jsx");
  assert.match(app, /"taller-inventario": +"inventario"/);
  assert.match(app, /irA\(user\.rol === "TALLER" \? "taller-inventario" : "mis-compras"\)/);
  assert.match(leer("../src/pages/CheckoutMarketplace.jsx"), /vehiculoId = esTaller \? "" :/);
  assert.match(leer("../src/pages/CarritoMarketplace.jsx"), /user\?\.rol === "TALLER"/);
});

test("cancelar una orden pagada pide motivo y avisa qué pasa antes de hacerlo", () => {
  const detalle = leer("../src/components/admin/DetallePlataforma.jsx");
  assert.match(detalle, /o\.sePuedeCancelar && <CancelarOrden/);
  assert.match(detalle, /deshabilitado=\{motivo\.trim\(\)\.length < MINIMO_MOTIVO\}/);
  assert.match(detalle, /vuelven a su lote/);
});
