// Tablas paginadas en el servidor: orden por columna, rango de fechas en hora de Colombia, la página del backend y
// que cada tabla use su endpoint con page/size/sort (y desde/hasta donde hay fecha).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  RANGOS_RAPIDOS, ariaOrden, claveDeRango, consultaTabla, errorRango, flechaOrden, hoyColombia, propsPaginacion, rangoRapido,
  siguienteOrden, sumarDias, textoOrden, textoRango,
} from "../src/components/ui/reglasTabla.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

// ─── Orden ──────────────────────────────────────────────────────────────────

test("Orden: un clic ordena ascendente, otro descendente; otra columna vuelve a empezar ascendente", () => {
  let orden = null;
  orden = siguienteOrden(orden, "fecha");
  assert.deepEqual(orden, { campo: "fecha", dir: "asc" });
  orden = siguienteOrden(orden, "fecha");
  assert.deepEqual(orden, { campo: "fecha", dir: "desc" });
  orden = siguienteOrden(orden, "fecha");
  assert.deepEqual(orden, { campo: "fecha", dir: "asc" });
  assert.deepEqual(siguienteOrden({ campo: "fecha", dir: "desc" }, "total"), { campo: "total", dir: "asc" });

  assert.equal(textoOrden({ campo: "precio", dir: "desc" }), "precio,desc");
  assert.equal(textoOrden(null), "", "sin orden se usa el del backend");
});

test("Orden: la flecha y aria-sort solo en la columna ordenada", () => {
  const orden = { campo: "stock", dir: "desc" };
  assert.equal(flechaOrden(orden, "stock"), "↓");
  assert.equal(flechaOrden({ campo: "stock", dir: "asc" }, "stock"), "↑");
  assert.equal(flechaOrden(orden, "nombre"), "↕");
  assert.equal(ariaOrden(orden, "stock"), "descending");
  assert.equal(ariaOrden(orden, "nombre"), "none");
  assert.equal(ariaOrden(null, "nombre"), "none");

  const e = leer("../src/components/ui/EncabezadoOrden.jsx");
  assert.ok(e.includes("aria-sort={ariaOrden(orden, campo)}") && e.includes('<th scope="col"') && e.includes("<button"),
    "el encabezado es un botón dentro del th, con aria-sort");
});

test("Orden: cada pantalla solo ofrece columnas de la lista blanca del backend (un campo no permitido no se puede pedir)", () => {
  // Las listas blancas del backend (PeticionTabla.OrdenPermitido) por tabla
  const permitidos = {
    "../src/pages/Productos.jsx": ["nombre", "stock", "precio", "estado"],
    "../src/pages/Movimientos.jsx": ["fecha", "tipo", "cantidad"],
    "../src/pages/PedidosTienda.jsx": ["fecha", "total", "estado"],
    "../src/pages/PedidosEnvios.jsx": ["fecha", "estado", "tienda"],
    "../src/pages/ProductosPlataforma.jsx": ["fecha", "nombre", "precio", "stock"],
    "../src/pages/ClientesPlataforma.jsx": ["fecha", "nombre"],
    "../src/pages/Usuarios.jsx": ["fecha", "nombre", "rol", "ultimoAcceso"],
    "../src/pages/Liquidaciones.jsx": ["fecha", "monto", "estado", "beneficiario"],
  };
  for (const [archivo, lista] of Object.entries(permitidos)) {
    const p = leer(archivo);
    const usados = [...p.matchAll(/EncabezadoOrden campo="([a-zA-Z]+)"|\{ campo: "([a-zA-Z]+)"/g)].map((m) => m[1] || m[2]);
    assert.ok(usados.length > 0, `${archivo}: no ordena por ninguna columna`);
    for (const c of usados) assert.ok(lista.includes(c), `${archivo}: «${c}» no está en la lista blanca del backend`);
  }
});

// ─── Fechas ─────────────────────────────────────────────────────────────────

test("Fechas: hoy en Colombia aunque en UTC ya sea mañana", () => {
  // 27 oct 2026 a las 23:30 en Bogotá = 28 oct 04:30 UTC
  const nocheEnBogota = new Date("2026-10-28T04:30:00Z");
  assert.equal(hoyColombia(nocheEnBogota), "2026-10-27");
  assert.equal(hoyColombia(new Date("2026-10-28T05:00:00Z")), "2026-10-28");
  assert.equal(sumarDias("2026-03-01", -1), "2026-02-28");
  assert.equal(sumarDias("2026-12-31", 1), "2027-01-01");
});

test("Fechas: hoy, últimos 7 días (con hoy) y este mes", () => {
  const ahora = new Date("2026-10-28T04:30:00Z"); // 27 oct en Colombia
  assert.deepEqual(rangoRapido("hoy", ahora), { desde: "2026-10-27", hasta: "2026-10-27" });
  assert.deepEqual(rangoRapido("7d", ahora), { desde: "2026-10-21", hasta: "2026-10-27" });
  assert.deepEqual(rangoRapido("mes", ahora), { desde: "2026-10-01", hasta: "2026-10-27" });
  assert.deepEqual(RANGOS_RAPIDOS.map((r) => r.label), ["Hoy", "Últimos 7 días", "Este mes"]);

  assert.equal(claveDeRango({ desde: "2026-10-21", hasta: "2026-10-27" }, ahora), "7d");
  assert.equal(claveDeRango({ desde: "2026-10-02", hasta: "2026-10-05" }, ahora), "personalizado");
  assert.equal(claveDeRango({}, ahora), "");
});

test("Fechas: un rango invertido se avisa antes de pedirlo, con el mismo mensaje del backend", () => {
  assert.equal(errorRango({ desde: "2026-10-08", hasta: "2026-10-01" }), "La fecha «desde» no puede ser posterior a «hasta».");
  assert.equal(errorRango({ desde: "2026-10-01", hasta: "2026-10-01" }), null);
  assert.equal(errorRango({ desde: "2026-10-01" }), null);
  assert.match(textoRango({ desde: "2026-10-01", hasta: "2026-10-07" }), /^del 1 .* al 7 /);

  const f = leer("../src/components/ui/FiltroFechas.jsx");
  for (const pieza of ["RANGOS_RAPIDOS", "Personalizado", 'type="date"', "errorRango"]) assert.ok(f.includes(pieza), `falta ${pieza}`);
  // Las pantallas no piden un rango invertido
  for (const archivo of ["Movimientos.jsx", "PedidosTienda.jsx", "PedidosEnvios.jsx", "Liquidaciones.jsx"]) {
    const p = leer(`../src/pages/${archivo}`);
    assert.ok(p.includes("<FiltroFechas") && p.includes("errorRango("), `${archivo}: usa el rango de fechas y lo valida`);
  }
});

// ─── Página del servidor ────────────────────────────────────────────────────

test("Paginación: la página del backend (desde 0) pasa a Paginacion (desde 1) con su total", () => {
  assert.deepEqual(propsPaginacion({ items: [], total: 103, pagina: 5, tamano: 20, totalPaginas: 6 }),
    { pagina: 6, total: 103, porPagina: 20 });
  assert.deepEqual(propsPaginacion(undefined), { pagina: 1, total: 0, porPagina: 20 });
  assert.equal(consultaTabla({ page: 0, size: 20, sort: "", q: "  ", tipo: "SALIDA" }), "?page=0&size=20&tipo=SALIDA",
    "page 0 sí se manda; lo vacío no");
  assert.equal(consultaTabla({}), "");
});

test("Paginación: cada tabla pide su endpoint con page/size/sort y vuelve a la página 1 al cambiar filtros u orden", () => {
  const tablas = {
    "Productos.jsx": "inventarioTabla(token, { filtro, q, page: pagina, size: 20, sort: textoOrden(orden) })",
    "Movimientos.jsx": "movimientosTabla(token, { tipo: filtros.tipo, q, desde: filtros.desde, hasta: filtros.hasta,",
    "PedidosTienda.jsx": "pedidosTiendaTabla(token, { pestana: filtro, q, desde: fechas.desde, hasta: fechas.hasta,",
    "PedidosEnvios.jsx": "pedidosPlataforma(token, { ...filtros, ...fechas, page: pagina, size: TAMANO, sort: textoOrden(orden) })",
    "ProductosPlataforma.jsx": "productosPlataforma(token, { ...filtros, q, page: pagina, size: TAMANO, sort: textoOrden(orden) })",
    "ClientesPlataforma.jsx": "clientesPlataforma(token, { ...filtros, q, page: pagina, size: TAMANO, sort: textoOrden(orden) })",
    "Usuarios.jsx": "usuariosPlataforma(token, { q, rol, estado, page: pagina, size: TAMANO, sort: textoOrden(orden) })",
    "Liquidaciones.jsx": "repartosTabla(token, { estado, ...fechas, page: pagina, size: 20, sort: textoOrden(orden) })",
  };
  for (const [archivo, llamada] of Object.entries(tablas)) {
    const p = leer(`../src/pages/${archivo}`);
    assert.ok(p.includes(llamada), `${archivo}: falta ${llamada}`);
    assert.ok(p.includes("propsPaginacion("), `${archivo}: el pie usa la página del servidor`);
    assert.ok(/setPagina\(0\)/.test(p), `${archivo}: vuelve a la primera página al cambiar algo`);
    assert.ok(p.includes("<SelectorOrden"), `${archivo}: en celular se ordena con el selector`);
  }
  // Los endpoints aparte de la tienda; las listas completas de siempre siguen para selectores y formularios
  assert.ok(leer("../src/services/productoService.js").includes("/mi-tienda/tabla"));
  assert.ok(leer("../src/services/pedidoTiendaService.js").includes("/mios/tabla"));
  assert.ok(leer("../src/services/movimientoService.js").includes("/tabla"));
  assert.ok(leer("../src/services/liquidacionService.js").includes("/repartos/tabla"));
  assert.ok(leer("../src/services/productoService.js").includes("export async function listarProductosMiTienda"));
});

test("Pedidos de la tienda: el pedido abierto desde un aviso se busca por su id aunque no esté en la página", () => {
  const p = leer("../src/pages/PedidosTienda.jsx");
  assert.ok(p.includes('pedidosTiendaTabla(token, { pestana: "TODOS", q: abiertoId, size: 1 })'));
  assert.ok(p.includes("noEncontrado"), "el aviso de «no encontramos el pedido» espera a esa búsqueda");
});

test("En celular (tarjetas) el encabezado se oculta: el selector de orden aparece por debajo de 760 px", () => {
  const css = leer("../src/css/ui.css");
  assert.match(css, /\.ui-orden-movil \{ display: none; \}/);
  assert.match(css, /@media \(max-width: 760px\) \{\s*\.ui-orden-movil \{ display: grid;/);
  assert.match(css, /\.ui-table--tarjetas thead \{ display: none; \}/, "760 px es el mismo corte de las tarjetas");
});
