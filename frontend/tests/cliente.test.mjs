// Pruebas de la cuenta del CLIENTE: reparto de Mis compras y accesos del menú (sin dependencias: node --test)
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { clasificarCompras, compraEnCurso, estadoVentaAnterior } from "../src/components/marketplace/clasificarCompras.js";

const compra = (id, estado, pedidos, dias = 1) => ({ id, estado, pedidos: pedidos.map((e, i) => ({ id: `${id}-${i}`, estado: e })),
  fechaCreacion: new Date(Date.now() - dias * 86400000).toISOString() });

test("una compra que espera el pago o con algún pedido activo está en curso", () => {
  assert.equal(compraEnCurso(compra("a", "PENDIENTE_PAGO", [])), true);
  for (const activo of ["PENDIENTE", "EN_PREPARACION", "DESPACHADO", "LISTO_PARA_RETIRAR"]) {
    assert.equal(compraEnCurso(compra("b", "PAGADA", ["ENTREGADO", activo])), true, activo);
  }
});

test("reembolsos y compras cerradas van a Anteriores", () => {
  assert.equal(compraEnCurso(compra("c", "NO_APLICADA", [])), false);
  assert.equal(compraEnCurso(compra("d", "PAGADA", ["ENTREGADO", "RETIRADO"])), false);
  assert.equal(compraEnCurso(compra("e", "PAGADA", ["NO_DESPACHADO"])), false);
  assert.equal(compraEnCurso(compra("f", "PAGADA", ["NO_ENTREGADO", "ENTREGADO"])), false);
});

test("las ventas viejas se reparten por su estado, sin las de taller, y todo queda ordenado por fecha", () => {
  const ventas = [
    { id: "v1", tipoVenta: "WEB", estado: "ENTREGADO", fecha: "2025-01-10T10:00:00" },
    { id: "v2", tipoVenta: "WEB", estado: "EN_CAMINO", fecha: "2025-03-10T10:00:00" },
    { id: "v3", tipoVenta: "TALLER", estado: "PAGADA", fecha: "2025-03-11T10:00:00" },
    { id: "v4", tipoVenta: "POS", estado: "ANULADA", fecha: "2025-02-10T10:00:00" },
  ];
  const { enCurso, anteriores } = clasificarCompras([compra("c1", "PAGADA", ["DESPACHADO"]), compra("c2", "PAGADA", ["ENTREGADO"], 3)], ventas);
  assert.deepEqual(enCurso.map((x) => x.id), ["c1", "v2"]);
  assert.deepEqual(anteriores.map((x) => x.id), ["c2", "v4", "v1"]);
  assert.equal(anteriores.find((x) => x.id === "v1").origen, "anterior");
  assert.ok(![...enCurso, ...anteriores].some((x) => x.id === "v3"), "las ventas de taller van en Mis servicios");
  assert.equal(estadoVentaAnterior({ estado: "RARO" }).enCurso, false);
});

test("sin compras ni ventas no hay nada en ninguna pestaña", () => {
  const { enCurso, anteriores } = clasificarCompras([], []);
  assert.equal(enCurso.length + anteriores.length, 0);
});

test("el menú de la cuenta y el menú móvil usan la misma lista", () => {
  const navbar = readFileSync(new URL("../src/components/public/Navbar.jsx", import.meta.url), "utf8");
  const menu = readFileSync(new URL("../src/components/public/MenuUsuario.jsx", import.meta.url), "utf8");
  const market = readFileSync(new URL("../src/components/marketplace/MarketHeader.jsx", import.meta.url), "utf8");
  assert.ok(navbar.includes("OPCIONES_CLIENTE") && menu.includes("OPCIONES_CLIENTE"));
  assert.ok(navbar.includes("<MenuUsuario") && market.includes("<MenuUsuario"), "un solo componente en las dos cabeceras");
  for (const atributo of ['aria-haspopup="menu"', 'role="menu"', 'role="menuitem"', "Escape", "ArrowDown", "ArrowUp"]) {
    assert.ok(menu.includes(atributo), `el menú debería tener ${atributo}`);
  }
});

test("Servicios anteriores: las órdenes en curso primero y solo las citas futuras", async () => {
  const { ordenarOrdenesAnteriores, tieneCitaProxima } = await import("../src/components/servicio/ordenesAnteriores.js");
  const ordenes = [
    { id: "a", estado: "FINALIZADA", fechaCreacion: "2025-05-01T10:00:00" },
    { id: "b", estado: "EN_PROCESO", fechaCreacion: "2025-01-01T10:00:00" },
    { id: "c", estado: "PAGADA", fechaCreacion: "2025-03-01T10:00:00" },
  ];
  assert.deepEqual(ordenarOrdenesAnteriores(ordenes).map((o) => o.id), ["c", "b", "a"]);
  const ahora = Date.parse("2025-06-01T00:00:00");
  assert.equal(tieneCitaProxima({ estado: "PAGADA", fechaProgramada: "2025-06-03T09:00:00" }, ahora), true);
  assert.equal(tieneCitaProxima({ estado: "PAGADA", fechaProgramada: "2025-05-03T09:00:00" }, ahora), false);
  assert.equal(tieneCitaProxima({ estado: "CANCELADA", fechaProgramada: "2025-06-03T09:00:00" }, ahora), false);
});

test("el aviso cuenta solo las solicitudes viejas sin cerrar", async () => {
  const { solicitudesSinCerrar } = await import("../src/components/marketplace/clasificarCompras.js");
  const estados = ["PENDIENTE", "APROBADO", "PAGADO", "EN_CAMINO", "RECHAZADO", "ENTREGADO", "COMPLETADA"];
  assert.equal(solicitudesSinCerrar(estados.map((estado, i) => ({ id: i, estado }))).length, 4);
});

test("Historial de compras y Mis solicitudes salen del menú pero sus pantallas siguen y se llega desde otras", () => {
  const opciones = readFileSync(new URL("../src/components/public/opcionesCliente.js", import.meta.url), "utf8");
  const app = readFileSync(new URL("../src/App.jsx", import.meta.url), "utf8");
  const lista = opciones.slice(opciones.indexOf("["), opciones.indexOf("];"));
  assert.ok(!lista.includes('"mis-pedidos"') && !lista.includes('"mis-solicitudes"'));
  assert.ok(app.includes('pagina === "mis-pedidos"') && app.includes('pagina === "mis-solicitudes"'), "las pantallas siguen");
  assert.ok(app.includes('onIrSolicitudes={() => setPagina("mis-solicitudes")}'), "el aviso de Mis compras lleva a Mis solicitudes");
  assert.ok(app.includes('onIrHistorial={() => setPagina("mis-pedidos")}'), "Mis compras y Mis servicios enlazan al historial");
});

test("menú final: Mi perfil, Mis compras, Mis servicios y Mis vehículos", async () => {
  const { OPCIONES_CLIENTE } = await import("../src/components/public/opcionesCliente.js");
  assert.deepEqual(OPCIONES_CLIENTE.map((o) => o.destino), ["perfil-cliente", "mis-compras", "mis-servicios", "mis-vehiculos"]);
});

test("Mi perfil es una página nueva solo para CLIENTE y no toca el perfil de los roles internos", () => {
  const app = readFileSync(new URL("../src/App.jsx", import.meta.url), "utf8");
  const perfil = readFileSync(new URL("../src/pages/PerfilCliente.jsx", import.meta.url), "utf8");
  const bloque = app.slice(app.indexOf('if (pagina === "perfil-cliente")'), app.indexOf('if (pagina === "catalogo")'));
  assert.ok(bloque.includes("if (!user)") && bloque.includes('user.rol !== "CLIENTE"'), "pide sesión de CLIENTE");
  assert.ok(app.includes('if (pagina === "mi-perfil")        return <PerfilAdmin'), "mi-perfil sigue siendo de los roles internos");
  // El nombre se edita en "Datos personales" con el mismo servicio que usa la ventana de editar nombre
  assert.ok(perfil.includes("actualizarMiNombre("), "reutiliza el servicio de editar nombre");
  assert.ok(!perfil.includes("/api/"), "usa los servicios existentes, sin endpoints nuevos");
});

test("Soporte y cotizaciones del pie lleva a Solicitar diagnóstico sin pasar el evento del clic", () => {
  const footer = readFileSync(new URL("../src/components/public/Footer.jsx", import.meta.url), "utf8");
  const linea = footer.split("\n").find((l) => l.includes("dm-footer__wsp"));
  assert.ok(linea.includes("onClick={() => onSolicitarDiagnostico?.()}"), linea);
  assert.ok(!footer.includes("onClick={onIrSolicitud}"));
});

test("Resultado del pago: siempre con la cabecera pública, sin el menú lateral del panel aunque haya sesión", () => {
  const app = readFileSync(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /const PAGINAS_SIEMPRE_PUBLICAS = \["pago-resultado"\];/);
  const usarPanel = app.slice(app.indexOf("const usarPanel"), app.indexOf("const usarPanel") + 400);
  assert.ok(usarPanel.includes("PAGINAS_SIEMPRE_PUBLICAS.includes(pagina)"), "usarPanel debe excluirla");
});
