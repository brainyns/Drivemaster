import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { ESTADOS_PAGO_NEGOCIO, filaComision, filaComisionOrden, filaComisionResumen, filasOrden, filasPagosCliente, hayPagosCliente, notaTotalPeriodo, textoConceptoPago, textoPago } from "../src/components/servicio/reglasDesglose.js";
import { textoTotalOrden } from "../src/pages/admin/plataformaAdmin.js";

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

test("lo listo depende de que DriveMaster apruebe la liquidación", () => {
  assert.equal(textoPago({ estado: "LISTO" }), "DriveMaster te lo transfiere cuando apruebe la liquidación.");
  assert.ok(!leer("../src/pages/InformesTienda.jsx").includes("próxima liquidación"));
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
  assert.ok(taller.includes("<ResumenOrdenTaller token={token} ordenId={s.id}"));
  assert.ok(!taller.slice(taller.indexOf("function SeguimientoTaller"), taller.indexOf("<ConfirmarRecibidos")).includes("<ResumenOrdenTaller"),
    "el seguimiento solo existe con cotización: una orden con solo el diagnóstico también tiene pago");
  assert.ok(leer("../src/pages/InformesTienda.jsx").includes("<ResumenDinero resumen={l.desglose} />"));
  assert.ok(leer("../src/pages/taller/TallerInformes.jsx").includes('<ResumenDinero resumen={desglose} etiquetaVenta="Total generado"'));
});

const orden = (cambios = {}) => ({ diagnosticoPagado: 50000, manoDeObra: 119000, anticipo: 50000, cotizacionPagada: 69000, totalGenerado: 119000,
  iva: 19000, comision: 10000, comisionDescontada: 10000, comisionSeDescuenta: true, recibe: 109000, ...cambios });
const etiquetas = (filas) => filas.map((f) => f.etiqueta);

test("el recibo de una orden sigue el orden acordado y dice cuánto es IVA", () => {
  const filas = filasOrden(orden(), "Recibes");
  assert.deepEqual(etiquetas(filas), ["Diagnóstico pagado", "Mano de obra con IVA", "Anticipo del diagnóstico", "Cotización pagada",
    "Total generado por el taller", "Comisión DriveMaster", "Recibes"]);
  assert.ok(pesos(filas[2].valor).startsWith("− "));
  assert.equal(filas[3].tipo, "parte", "cotización pagada es un paso del cálculo, no otro sumando");
  assert.ok(pesos(filas[4].nota).startsWith("Incluye") && filas[4].nota.endsWith("de IVA"));
  assert.ok(pesos(filas[6].valor).includes("109.000"));
});

test("una orden con solo el diagnóstico no muestra lo que no pasó", () => {
  const filas = filasOrden(orden({ manoDeObra: 0, anticipo: 0, cotizacionPagada: 0, totalGenerado: 50000 }), "Recibes");
  assert.ok(!etiquetas(filas).includes("Mano de obra con IVA") && !etiquetas(filas).includes("Cotización pagada"));
});

test("sin saldo de cotización se ve que el anticipo la cubrió", () => {
  const filas = filasOrden(orden({ manoDeObra: 30000, anticipo: 30000, cotizacionPagada: 0, totalGenerado: 50000 }), "Recibes");
  assert.ok(etiquetas(filas).includes("Cotización pagada"));
});

test("con líneas antiguas no se inventa la nota del IVA", () => {
  assert.equal(filasOrden(orden({ iva: null }), "Recibes")[4].nota, undefined);
  assert.equal(filasOrden(orden({ iva: 0 }), "Recibes")[4].nota, undefined, "un taller que no cobra IVA");
});

test("la comisión de la orden dice si se descontó", () => {
  assert.ok(pesos(filaComisionOrden(orden()).valor).startsWith("− "));
  assert.equal(filaComisionOrden(orden({ comisionDescontada: 0, comisionSeDescuenta: false })).nota, "No se descuenta todavía");
  assert.equal(filaComisionOrden(orden({ comisionDescontada: 0 })).nota, "No se descontó en este pago");
});

test("el estado de la orden tiene texto y lo parcial no se llama pagado", () => {
  assert.equal(ESTADOS_PAGO_NEGOCIO.PARCIAL.label, "Pagado en parte");
  assert.ok(ESTADOS_PAGO_NEGOCIO.SIN_PAGOS.label);
});

test("el cliente ve lo que pagó y, con devoluciones, lo que le costó de verdad", () => {
  const sinDevolucion = filasPagosCliente({ diagnostico: 50000, cotizacion: 307000, total: 357000, devolucionPendiente: 0, devuelto: 0 });
  assert.deepEqual(etiquetas(sinDevolucion), ["Diagnóstico pagado", "Cotización pagada", "Total pagado"]);
  assert.equal(sinDevolucion[2].tipo, "final");
  const conDevolucion = filasPagosCliente({ diagnostico: 50000, cotizacion: 307000, total: 357000, devolucionPendiente: 100000, devuelto: 38000 });
  assert.deepEqual(etiquetas(conDevolucion).slice(2), ["Total pagado", "Te devolveremos", "Te devolvimos", "Costo final del servicio"]);
  assert.ok(pesos(conDevolucion[5].valor).includes("219.000"));
});

test("antes de pagar el cliente no ve el bloque, y nunca la comisión", () => {
  assert.equal(hayPagosCliente({ total: 0, devolucionPendiente: 0, devuelto: 0 }), false);
  assert.equal(hayPagosCliente(null), false);
  assert.ok(!etiquetas(filasPagosCliente({ diagnostico: 1, cotizacion: 1, total: 2, devolucionPendiente: 0, devuelto: 0 })).some((e) => /comisi/i.test(e)));
});

test("el total del periodo dice en cuántas órdenes y cuánto es IVA", () => {
  const n = pesos(notaTotalPeriodo({ ordenes: 3, iva: 19000 }));
  assert.ok(n.startsWith("3 órdenes · incluye") && n.endsWith("de IVA"), n);
  assert.equal(notaTotalPeriodo({ ordenes: 1, iva: null }), "1 orden");
  assert.equal(notaTotalPeriodo({ ordenes: 0, iva: null }), undefined);
});

test("Liquidaciones enlaza las líneas con el total de su orden, y se sigue pagando línea por línea", () => {
  assert.equal(textoTotalOrden(null), null);
  assert.equal(textoTotalOrden({ lineas: 1, totalGenerado: 50000, recibe: 45000 }), null, "con una sola línea no aporta");
  const t = pesos(textoTotalOrden({ lineas: 2, totalGenerado: 119000, recibe: 109000 }));
  assert.ok(t.includes("119.000") && t.includes("2 pagos"), t);
  assert.ok(leer("../src/pages/Liquidaciones.jsx").includes("textoTotalOrden(f.totalOrden)"));
});

test("el resumen está en la solicitud del admin y en el detalle del cliente", () => {
  assert.ok(leer("../src/components/admin/DetallePlataforma.jsx").includes('etiquetaRecibe="Recibe el taller"'));
  assert.ok(leer("../src/pages/MisServicios.jsx").includes("<PagosCliente token={token} ordenId={s.id}"));
});

test("marcar el despacho pide motivo, dice que no mueve dinero y solo sale si el backend lo permite", () => {
  const d = leer("../src/components/admin/DetallePlataforma.jsx");
  assert.ok(d.includes("Marcar como despachado (confirmado con la tienda)"));
  assert.ok(d.includes("No mueve dinero"));
  assert.ok(d.includes("deshabilitado={motivo.trim().length < MINIMO_MOTIVO}"));
  assert.ok(d.includes("{p.sePuedeMarcarDespachado && <MarcarDespachado"));
});

test("la cotización no le promete al taller más de lo que recibe", () => {
  const c = leer("../src/components/servicio/CotizacionDetalle.jsx");
  assert.ok(c.includes("c.recibeTaller"), "la cifra viene del backend");
  assert.ok(!c.includes("comisionInformativa"), "esa cifra incluye los repuestos de las tiendas");
  assert.ok(c.includes("no se descuenta todavía"));
});
