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
