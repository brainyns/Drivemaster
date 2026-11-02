// "Mi inventario" del taller y las piezas del taller en la cotización: cifras, origen de cada repuesto y privacidad.
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { cifrasPieza, estadoEnCamino, pct, precioConRecargo } from "../src/pages/taller/reglasInventario.js";
import { claveOferta, etiquetaOrigen, vendedor } from "../src/components/servicio/reglasRepuestos.js";
import { lineasIva, precioFinalLinea, textoIvaOferta, textoPrecioFinal } from "../src/components/servicio/desgloseIva.js";

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

test("la tienda ve como Cancelado lo que canceló DriveMaster, con su motivo", async () => {
  const { canceladoPorDriveMaster } = await import("../src/pages/tienda/pedidosTienda.js");
  assert.equal(canceladoPorDriveMaster({ estado: "NO_DESPACHADO", tipoMotivoNoDespacho: "ORDEN_CANCELADA" }), true);
  assert.equal(canceladoPorDriveMaster({ estado: "NO_DESPACHADO", tipoMotivoNoDespacho: "PAGO_NO_APLICADO" }), true);
  assert.equal(canceladoPorDriveMaster({ estado: "NO_DESPACHADO", tipoMotivoNoDespacho: "SIN_REPUESTO" }), false, "si la tienda no pudo, es No se despachó");
  const pedidos = leer("../src/pages/PedidosTienda.jsx");
  assert.match(pedidos, /canceladoPorDriveMaster\(p\) \? <ChipEstado tono="error">Cancelado<\/ChipEstado>/);
  assert.match(pedidos, /p\.tipoMotivoNoDespacho === "ORDEN_CANCELADA"/);
});

test("Mi inventario dice pieza o piezas según la cantidad", () => {
  assert.match(leer("../src/pages/taller/TallerInventario.jsx"), /plural\(d\.piezas\.filter\(\(p\) => p\.stock > 0\)\.length, "pieza con stock", "piezas con stock"\)/);
});

test("el taller ve lo que debe por repuestos que se quedó, y el admin sabe quién lo debe", () => {
  const inv = leer("../src/pages/taller/TallerInventario.jsx");
  assert.match(inv, /Lo que debes a DriveMaster/);
  assert.match(inv, /no se descuenta de tus pagos/);
  const detalle = leer("../src/components/admin/DetallePlataforma.jsx");
  assert.match(detalle, /b\.saldoDeudor \? `\. Lo debe \$\{b\.saldoDeudor\}/);
  assert.match(detalle, /resultado\.deudaTaller > 0/);
  assert.match(leer("../src/pages/ReportesPlataforma.jsx"), /t: "Lo debe", f: \(s\) => s\.deudor/);
});

test("el taller ve y paga el precio con descuento; el cliente, el público", async () => {
  const { precioQuePaga, textoDescuento } = await import("../src/components/marketplace/reglasPrecioCatalogo.js");
  assert.equal(precioQuePaga({ precio: 100_000, precioTaller: 80_000 }), 80_000);
  assert.equal(precioQuePaga({ precio: 100_000 }), 100_000, "sin precio de taller, el público");
  assert.equal(textoDescuento(12.5), "−12,5 %");
  const componente = leer("../src/components/marketplace/PrecioCatalogo.jsx");
  assert.match(componente, /<s className="pc-precio__publico">/, "el público va tachado");
  for (const ruta of ["../src/components/public/ProductCard.jsx", "../src/components/public/ProductDetail.jsx", "../src/pages/DetalleProductoMarketplace.jsx", "../src/pages/CarritoMarketplace.jsx"]) {
    assert.match(leer(ruta), /PrecioCatalogo/, `${ruta} muestra el precio del taller`);
  }
  assert.match(leer("../src/hooks/useCarrito.js"), /precioQuePaga\(x\.producto\)/, "el subtotal del carrito usa lo que paga");
});

test("la tienda configura su descuento para talleres de 0 a 50 % en Mi tienda", () => {
  const mi = leer("../src/pages/DatosMiTienda.jsx");
  assert.match(mi, /titulo="Precio para talleres"/);
  assert.match(mi, /n < 0 \|\| n > 50/);
  assert.match(mi, /guardar\(\{ descuentoTalleresPct: n \}\)/);
});

test("en Mi inventario el precio del taller no pasa el público y se ve el tope", () => {
  const inv = leer("../src/pages/taller/TallerInventario.jsx");
  assert.match(inv, /disabled=\{guardando \|\| supera\}/, "no deja guardar sobre el tope");
  assert.match(inv, /Usar el precio público/);
  assert.match(inv, /<dt>Precio público<\/dt>/);
  assert.match(inv, /pieza\.perdidaConTope/);
  assert.ok(!/Usar el sugerido \(\+/.test(inv), "ya no se sugiere costo + recargo");
});

const pesos = (v) => `$${Math.round(v).toLocaleString("es-CO")}`;

test("en el buscador la misma pieza del taller y de la tienda muestra el mismo precio final", () => {
  const taller = { origen: "TALLER", precio: 34_510, ivaPct: 0, ivaMonto: 0, precioFinal: 34_510 };
  const tienda = { origen: "TIENDA", precio: 29_000, ivaPct: 19, ivaMonto: 5_510, precioFinal: 34_510 };
  assert.equal(taller.precioFinal, tienda.precioFinal);
  assert.equal(textoIvaOferta(taller, pesos), "Sin IVA: tu taller no cobra IVA");
  assert.equal(textoIvaOferta(tienda, pesos), "$29.000 + IVA $5.510");
  assert.equal(textoIvaOferta({ precio: 29_000 }, pesos), "", "sin precio final del backend no se inventa la aclaración");
  const tarjeta = leer("../src/components/servicio/BuscadorRepuestos.jsx");
  assert.match(tarjeta, /formatPrecio\(o\.precioFinal \?\? o\.precio\)/, "el número grande es el precio final");
});

test("en la cotización cada línea aclara su precio final con la misma regla que el backend", () => {
  const c = { ivaRepuestosPct: 19, ivaManoObraPct: 0 };
  const taller = precioFinalLinea(c, { tipo: "REPUESTO", origen: "TALLER", precioUnitario: 34_510 });
  const tienda = precioFinalLinea(c, { tipo: "REPUESTO", origen: "TIENDA", precioUnitario: 29_000 });
  assert.equal(taller.total, tienda.total);
  assert.equal(textoPrecioFinal(taller, pesos), "$34.510 c/u, sin IVA");
  assert.equal(textoPrecioFinal(tienda, pesos), "$34.510 c/u con IVA ($29.000 + IVA $5.510)");
  assert.equal(precioFinalLinea({ ivaRepuestosPct: 19, ivaManoObraPct: 19 }, { tipo: "REPUESTO", precioUnitario: 29_000 }), null, "con un solo IVA no hace falta");
  assert.equal(precioFinalLinea(c, { tipo: "MANO_OBRA", precioUnitario: 50_000 }), null);
});
