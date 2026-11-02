// Movimientos del inventario del taller: reglas del formulario, signo de cada fila, enlaces de las referencias cortas y
// la pantalla armada con los componentes de la de la tienda.
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  CLASES, CLASES_MANUALES, FILTROS_TIPO, cantidadConSigno, erroresMovimientoTaller, piezasStockBajo, stockResultante,
} from "../src/pages/taller/movimientosTaller.js";
import { destinoReferencia } from "../src/utils/navegacion.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");
const PIEZA = { id: "t|REF:BP1", nombre: "Pastillas", stock: 5 };

test("el motivo es obligatorio en todos los movimientos manuales", () => {
  for (const clase of Object.keys(CLASES_MANUALES)) {
    const e = erroresMovimientoTaller({ clase, cantidad: "1", costo: "1000", motivo: "  " }, PIEZA);
    assert.ok(e.motivo, `${clase} sin motivo`);
  }
});

test("nunca deja sacar más de lo que hay; contar 0 sí, contar lo mismo no", () => {
  assert.match(erroresMovimientoTaller({ clase: "MERMA", cantidad: "6", motivo: "x" }, PIEZA).cantidad, /hay 5/);
  assert.match(erroresMovimientoTaller({ clase: "DEVOLUCION_TIENDA", cantidad: "9", motivo: "x" }, PIEZA).cantidad, /hay 5/);
  assert.equal(erroresMovimientoTaller({ clase: "AJUSTE", cantidad: "0", motivo: "x" }, PIEZA).cantidad, undefined);
  assert.match(erroresMovimientoTaller({ clase: "AJUSTE", cantidad: "5", motivo: "x" }, PIEZA).cantidad, /mismo stock/);
  assert.ok(erroresMovimientoTaller({ clase: "MERMA", cantidad: "-1", motivo: "x" }, PIEZA).cantidad);
  assert.equal(stockResultante("MERMA", 5, "2"), 3);
  assert.equal(stockResultante("DEVOLUCION_CLIENTE", 5, "2"), 7);
  assert.equal(stockResultante("AJUSTE", 5, "2"), 2);
});

test("la carga inicial pide pieza del catálogo y costo", () => {
  const e = erroresMovimientoTaller({ clase: "CARGA_INICIAL", cantidad: "3", costo: "", motivo: "Ya la tenía" }, null);
  assert.match(e.pieza, /catálogo/);
  assert.ok(e.costo);
  assert.deepEqual(erroresMovimientoTaller({ clase: "CARGA_INICIAL", cantidad: "3", costo: "21.000", motivo: "Ya la tenía" }, { id: "p9" }), {});
});

test("cada fila lleva su signo por stock antes → después y cada clase su etiqueta", () => {
  assert.equal(cantidadConSigno({ stockAnterior: 2, stockNuevo: 5, cantidad: 3 }), "+3");
  assert.equal(cantidadConSigno({ stockAnterior: 5, stockNuevo: 1, cantidad: 4 }), "−4");
  for (const c of ["COMPRA", "VENTA", "CANCELACION", "REVERSA", "AJUSTE", "CARGA_INICIAL", "MERMA", "DEVOLUCION_TIENDA", "DEVOLUCION_CLIENTE"]) {
    assert.ok(CLASES[c]?.label, c);
  }
  assert.equal(CLASES.CANCELACION.label, "Devolución por cancelación");
  assert.deepEqual(FILTROS_TIPO.map((f) => f.label), ["Todos", "Entradas", "Salidas", "Ajustes"]);
  assert.deepEqual(piezasStockBajo([{ stockBajo: true }, { stockBajo: false }, {}]).length, 1);
});

test("las referencias cortas llevan al detalle y nunca muestran el id interno", () => {
  assert.deepEqual(destinoReferencia({ texto: "Orden #6AE8CE", destino: "ORDEN", id: "o1" }, "taller-movimientos"),
    { pagina: "taller-trabajos", param: { abrir: "o1", volverA: "taller-movimientos" } });
  assert.deepEqual(destinoReferencia({ texto: "Pedido #A1B2C3", destino: "PEDIDO", id: "p1" }, "movimientos"),
    { pagina: "pedidos-tienda", param: { abrir: "p1", volverA: "movimientos" } });
  assert.deepEqual(destinoReferencia({ texto: "Compra #X", destino: "COMPRA", id: "c1" }), { pagina: "compraDetalle", param: "c1" });
  assert.equal(destinoReferencia({ texto: "Venta #X", destino: null, id: null }), null);
  const tienda = leer("../src/pages/Movimientos.jsx");
  assert.doesNotMatch(tienda, /#\{m\.referencia\}/, "la tienda ya no pinta el id largo");
  assert.match(tienda, /m\.referenciaLegible\.texto/);
});

test("la pantalla del taller reutiliza los componentes de Inventario de la tienda y está en el menú y en Mi inventario", () => {
  const pantalla = leer("../src/pages/taller/TallerMovimientos.jsx");
  for (const c of ["Indicador", "FiltroFechas", "SelectorOrden", "EncabezadoOrden", "Paginacion", "BuscadorProducto", "movimientos.css"]) {
    assert.match(pantalla, new RegExp(c), c);
  }
  for (const t of ["Unidades que entraron", "Unidades que salieron", "Ajustes por conteo", "Excel", "PDF"]) assert.match(pantalla, new RegExp(t));
  assert.match(leer("../src/components/Sidebar.jsx"), /key: "taller-movimientos"/);
  const inventario = leer("../src/pages/taller/TallerInventario.jsx");
  assert.match(inventario, /irA\("taller-movimientos"\)/);
  assert.match(inventario, /EditorMinimo/);
});
