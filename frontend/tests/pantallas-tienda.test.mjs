// Pantallas del rol TIENDA: lógica pura de cada una y que usen el sistema compartido (components/ui).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  FILTROS, VACIOS, accionPrincipal, coincide, contar, destinatario, historialPedido, ordenarPorUrgencia, plazoVencido, subtotal,
} from "../src/pages/tienda/pedidosTienda.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");
const AHORA = new Date(2026, 9, 19, 9, 0).getTime();
const en = (h) => new Date(AHORA + h * 3600000).toISOString();
const pedido = (id, estado, extra = {}) => ({ id: `0000000${id}`, estado, items: [], fechaCreacion: en(-10), ...extra });

test("Pedidos: cada pestaña tiene su vacío y los contadores cuentan por estado", () => {
  assert.deepEqual(FILTROS.map((f) => f.label),
    ["Por atender", "Nuevos", "En preparación", "Listos para retirar", "En camino", "Entregados", "Cancelados", "Todos"]);
  for (const f of FILTROS) assert.ok(VACIOS[f.key], f.key);
  const c = contar([pedido(1, "PENDIENTE"), pedido(2, "EN_PREPARACION"), pedido(3, "LISTO_PARA_RETIRAR"),
    pedido(4, "DESPACHADO"), pedido(5, "RETIRADO"), pedido(6, "NO_DESPACHADO")]);
  assert.deepEqual(c, { ATENDER: 3, NUEVOS: 1, PREPARACION: 1, LISTOS: 1, EN_CAMINO: 1, ENTREGADOS: 1, CANCELADOS: 1, TODOS: 6 });
});

test("Pedidos: lo urgente primero (vencidos, plazo más cercano, listos para retirar, luego el resto)", () => {
  const lista = [
    pedido(1, "ENTREGADO", { fechaCreacion: en(-1) }),
    pedido(2, "LISTO_PARA_RETIRAR", { fechaListoRetiro: en(-3) }),
    pedido(3, "PENDIENTE", { plazoDespachoHasta: en(30) }),
    pedido(4, "PENDIENTE", { plazoDespachoHasta: en(-2) }),
    pedido(5, "EN_PREPARACION", { plazoDespachoHasta: en(5) }),
  ];
  assert.deepEqual(ordenarPorUrgencia(lista, AHORA).map((p) => p.id.slice(-1)), ["4", "5", "3", "2", "1"]);
  assert.ok(plazoVencido(lista[3], AHORA));
  assert.ok(!plazoVencido(pedido(9, "DESPACHADO", { plazoDespachoHasta: en(-2) }), AHORA), "un despachado ya no vence");
});

test("Pedidos: búsqueda por número o quién recibe, sin datos del cliente en un retiro", () => {
  const envio = pedido(1, "PENDIENTE", { origen: "CATALOGO", entrega: "ENVIO", envioReceptor: "Laura Gómez", id: "abcdef123456" });
  assert.ok(coincide(envio, "#123456"));
  assert.ok(coincide(envio, "laura"));
  assert.ok(!coincide(envio, "pedro"));
  assert.equal(destinatario(envio), "Laura Gómez");
  assert.equal(destinatario(pedido(2, "PENDIENTE", { origen: "CATALOGO", entrega: "RETIRO" })), "Cliente que retira en tu tienda");
  assert.equal(destinatario(pedido(3, "PENDIENTE", { tallerNombre: "AutoMax" })), "AutoMax");
});

test("Pedidos: la acción del estado y el historial salen de los datos del pedido", () => {
  const retiro = { origen: "CATALOGO", entrega: "RETIRO" };
  assert.equal(accionPrincipal(pedido(1, "PENDIENTE", retiro)).tipo, "LISTO");
  assert.equal(accionPrincipal(pedido(2, "PENDIENTE", { origen: "CATALOGO", entrega: "ENVIO" })).tipo, "DESPACHAR");
  assert.equal(accionPrincipal(pedido(3, "LISTO_PARA_RETIRAR", retiro)).tipo, "VALIDAR");
  assert.equal(accionPrincipal(pedido(4, "ENTREGADO")), null);

  const h = historialPedido(pedido(5, "RETIRADO", { ...retiro, fechaCreacion: en(-30), fechaListoRetiro: en(-5), fechaRetirado: en(-1) }));
  assert.deepEqual(h.map((x) => x.titulo), ["Retirado por el cliente", "Listo para retirar", "Pedido pagado"]);
  assert.equal(subtotal({ items: [{ subtotal: 1000 }, { subtotal: 2500 }] }), 3500);
  assert.equal(subtotal({ subtotal: 9, items: [{ subtotal: 1 }] }), 9);
});

test("Pedidos usa el sistema compartido y no cambia las llamadas del flujo", () => {
  const pagina = leer("../src/pages/PedidosTienda.jsx");
  for (const pieza of ["<EncabezadoPagina", "<ChipEstado", "<Alerta", "<EstadoVacio", "<Esqueleto", "<Historial", "<BarraAccionMovil",
    "ui-field__input--codigo", 'className="ui-chip" aria-pressed={filtro === f.key}']) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
  for (const llamada of ["validarCodigoRetiro(token, pedido.id, limpio)", "marcarListoParaRetirar(token, p.id)",
    "despacharPedido(token, pedido.id, nota.trim() || null)", "noPuedoDespachar(token, pedido.id, tipoMotivo, nota.trim() || null)",
    "prepararPedido(token, p.id)"]) {
    assert.ok(pagina.includes(llamada), `misma llamada: ${llamada}`);
  }
  assert.doesNotMatch(leer("../src/css/pedidos-tienda.css"), /inset 3px 0 0/, "sin franja de color a la izquierda");
});

test("Mi tienda: perfil completo con lo que pide el catálogo y validación en línea", async () => {
  const { completitudTienda, erroresUbicacion, errorHorarioTienda, errorTelefono, errorCostoEnvio, comoLaVeElCliente } =
    await import("../src/pages/tienda/perfilTienda.js");
  const completa = { ciudad: "Cali", direccion: "Cra 1", horario: "L-V 8-18", latitud: 3.4, longitud: -76.5, telefono: "3001234567" };
  assert.deepEqual(completitudTienda(completa), { porcentaje: 100, faltan: [] });
  const { porcentaje, faltan } = completitudTienda({ ...completa, horario: " ", latitud: null });
  assert.equal(porcentaje, 50);
  assert.deepEqual(faltan.map((f) => f.clave), ["horario", "ubicacion"]);
  assert.ok(faltan[0].catalogo && faltan.every((f) => f.seccion), "dice qué pide el catálogo y lleva a su sección");

  assert.deepEqual(Object.keys(erroresUbicacion({ ciudad: "", direccion: "" })), ["ciudad", "direccion"]);
  assert.deepEqual(erroresUbicacion({ ciudad: "Cali", direccion: "Cra 1" }), {});
  assert.match(errorHorarioTienda(""), /Escribe tu horario/);
  assert.equal(errorTelefono(""), null, "el teléfono es opcional");
  assert.equal(errorTelefono("+57 300 123 4567"), null);
  assert.match(errorTelefono("abc"), /teléfono válido/);
  assert.equal(errorCostoEnvio(false, ""), null, "sin envío no se valida el costo");
  assert.equal(errorCostoEnvio(true, "0"), null);
  assert.match(errorCostoEnvio(true, ""), /puede ser 0/);
  assert.match(errorCostoEnvio(true, "600000"), /entre \$ 0 y \$ 500\.000/);
  assert.deepEqual(comoLaVeElCliente({ ...completa, nombre: "Repuestos Sol", costoEnvio: 8000, nit: "900", correoUsuario: "x@y.com" }),
    { id: undefined, nombre: "Repuestos Sol", ciudad: "Cali", barrio: undefined, direccion: "Cra 1", horario: "L-V 8-18", ofreceEnvio: true, costoEnvio: 8000 });
});

test("Mi tienda usa las piezas de Mi taller y la ficha del catálogo, con los mismos datos de antes", () => {
  const pagina = leer("../src/pages/DatosMiTienda.jsx");
  for (const pieza of ["<CabeceraNegocio", "<SeccionEditable", "<VistaPrevia", "<TiendaVendedor", "<SelectorUbicacion", "<LogoNegocio"]) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
  assert.ok(pagina.includes("actualizarMiTienda(token, datos)"), "el mismo PUT /mi-tienda");
  assert.ok(leer("../src/pages/DetalleProductoMarketplace.jsx").includes("<TiendaVendedor tienda={t} />"), "la misma ficha que ve el cliente");
});

test("Productos: stock con aviso, publicado u oculto, filtros y lo que necesita atención primero", async () => {
  const { nivelStock, contar, ordenar, resumen, coincide } = await import("../src/pages/tienda/productosTienda.js");
  const p = (nombre, extra) => ({ id: nombre, nombre, precioVenta: 1000, stockActual: 10, stockMinimo: 3, ...extra });
  assert.equal(nivelStock(p("a", { stockActual: 0 })), "agotado");
  assert.equal(nivelStock(p("b", { stockActual: 3 })), "bajo", "en el mínimo ya es poco stock");
  assert.equal(nivelStock(p("c")), "ok");
  assert.equal(nivelStock(p("d", { tipo: "ENCARGO", stockActual: 0 })), "encargo", "los de encargo no dependen del stock");
  const lista = [p("Zeta"), p("Alfa", { stockActual: 2 }), p("Beta", { stockActual: 0, activo: false })];
  assert.deepEqual(contar(lista), { TODOS: 3, POCO: 1, AGOTADOS: 1, PUBLICADOS: 2, OCULTOS: 1 });
  assert.deepEqual(ordenar(lista).map((x) => x.nombre), ["Beta", "Alfa", "Zeta"]);
  assert.deepEqual(resumen(lista), { total: 3, publicados: 2, atencion: 2, valor: 12000 });
  assert.ok(coincide(p("Pastillas", { marca: "Bosch" }), "bosch"));
});

test("Productos usa el sistema compartido, sin confirm() ni alert() del navegador", () => {
  const pagina = leer("../src/pages/Productos.jsx");
  for (const pieza of ["<EncabezadoPagina", "<Indicador", "<ChipEstado", "<EstadoVacio", "<Esqueleto", "<ImagenProducto", "<ConfirmarAccion",
    'className="ui-table ui-table--tarjetas prt-tabla"', "data-label=\"Stock\""]) {
    assert.ok(pagina.includes(pieza), `usa ${pieza}`);
  }
  assert.doesNotMatch(pagina, /\b(confirm|alert)\(/);
  assert.ok(pagina.includes("eliminarProducto(eliminando.id, token)"), "la misma llamada para eliminar");
});

test("Formulario de producto: validación en español, foto con las mismas reglas y margen", async () => {
  const { validarFormulario, errorImagen, margen, COMPAT_VACIA } = await import("../src/pages/tienda/reglasProducto.js");
  const base = { nombre: "Pastillas", categoria: "Frenos", codigo: "BRK-1", marca: "Bosch", tipo: "STOCK", precioCompra: "100",
    precioVenta: "150", stockActual: "4", stockMinimo: "1", compatibilidadUniversal: true, modelosCompatibles: [COMPAT_VACIA] };
  assert.deepEqual(validarFormulario(base, [], false), {});
  const e = validarFormulario({ ...base, nombre: "", codigo: "brk-1", precioVenta: "50", compatibilidadUniversal: false },
    [{ id: "x", codigo: "BRK-1" }], false);
  assert.equal(e.nombre, "Escribe el nombre del producto.");
  assert.equal(e.codigo, "Ya tienes un producto con este código SKU.");
  assert.equal(e.precioVenta, "El precio de venta no puede ser menor que el de compra.");
  assert.equal(e.compatibilidad[0].marca, "Elige la marca del vehículo.");
  assert.equal(validarFormulario({ ...base }, [{ id: "p1", codigo: "BRK-1" }], true, "p1").codigo, undefined, "editar el mismo no es duplicado");
  assert.equal(errorImagen({ type: "image/png", size: 10 }), null);
  assert.match(errorImagen({ type: "image/svg+xml", size: 10 }), /JPG, PNG, WEBP o GIF/);
  assert.match(errorImagen({ type: "image/jpeg", size: 6 * 1024 * 1024 }), /menos de 5 MB/);
  assert.equal(margen("100", "150"), 50);
  assert.equal(margen("", "150"), null);
});

test("Formulario de producto: etiquetas asociadas, errores accesibles y las mismas llamadas", () => {
  const pagina = leer("../src/pages/ProductoForm.jsx");
  assert.ok((pagina.match(/<Campo id=/g) || []).length >= 14, "cada campo con Campo (label y aria)");
  assert.ok(!pagina.includes("Sincronizacion"), "sin la tarjeta que prometía facturación inmediata");
  assert.ok(!/<div[^>]*onClick/.test(pagina), "nada clicable que no sea un botón");
  for (const llamada of ["actualizarProducto(id, payload, token)", "crearProducto(payload, token)",
    "subirImagenProducto(productoId, imagenFile, token)", "sugerirProducto(token, form, CATEGORIAS)"]) {
    assert.ok(pagina.includes(llamada), `misma llamada: ${llamada}`);
  }
});
