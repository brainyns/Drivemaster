// Panel de la plataforma (SUPERADMIN): lógica pura de cada pantalla y que use el sistema compartido (components/ui).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import { barras, urgentesDashboard } from "../src/pages/admin/plataformaAdmin.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

/** La pantalla usa las piezas compartidas y ya no el sistema viejo (adm-, db-kpi). */
function usaSistemaCompartido(archivo, piezas, viejas = ["adm-", "db-kpi", "admin-base.css"]) {
  const p = leer(`../src/pages/${archivo}`);
  for (const pieza of piezas) assert.ok(p.includes(pieza), `${archivo}: falta ${pieza}`);
  for (const vieja of viejas) assert.ok(!p.includes(vieja), `${archivo}: sobra ${vieja}`);
  return p;
}

test("Dashboard: lo urgente va primero (aprobaciones y pedidos con problema) y solo lo que tiene casos", () => {
  const u = urgentesDashboard({
    negociosPorAprobar: 3, problemasEnvioAbiertos: 1, despachosAtrasados: 2, liquidacionesPendientes: 0,
    liquidacionesFallidas: 1, reembolsosPendientes: 0, montoReembolsosPendientes: 0,
  });
  assert.deepEqual(u.map((x) => x.clave), ["aprobar", "problemas", "atrasados", "fallidas"]);
  assert.equal(u[0].titulo, "3 negocios esperan aprobación");
  assert.equal(u[1].titulo, "1 problema de envío abierto");
  // Cada una lleva a su lista; los atrasados, con el filtro ya puesto
  assert.deepEqual(u.map((x) => x.pagina), ["aprobaciones", "problemas-envios", "pedidos-envios", "liquidaciones"]);
  assert.equal(u[2].preset, "atrasados");
  assert.ok(u.every((x) => x.estado && x.accion && x.tono));
  assert.deepEqual(urgentesDashboard({}), []);
  assert.match(urgentesDashboard({ reembolsosPendientes: 2, montoReembolsosPendientes: 150000 })[0].detalle, /150\.000/);
});

test("Dashboard: las barras siguen el orden de los estados, dejan al final los desconocidos y suman 100%", () => {
  const b = barras({ LISTA: 1, ENVIADA: 3, OTRO: 4, CANCELADA: 0 }, { ENVIADA: "Enviada", LISTA: "Lista" }, ["ENVIADA", "LISTA", "CANCELADA"]);
  assert.deepEqual(b.map((x) => x.clave), ["ENVIADA", "LISTA", "OTRO"]);
  assert.equal(b[0].etiqueta, "Enviada");
  assert.equal(b[2].etiqueta, "OTRO");
  assert.equal(Math.round(b.reduce((s, x) => s + x.pct, 0)), 100);
  assert.deepEqual(barras({}), []);
});

test("Dashboard: cifras con Indicador, lo urgente con acceso directo y estados de carga y error con Reintentar", () => {
  const p = usaSistemaCompartido("DashboardPlataforma.jsx", ["EncabezadoPagina", "<Indicador", "ChipEstado", "<Alerta",
    "onReintentar", "<Esqueleto", "BarraProgreso", "ui-table--tarjetas", "Lo urgente primero", "urgentesDashboard("]);
  assert.ok(p.includes("dashboardPlataforma(token)"), "el mismo endpoint");
  assert.ok(p.includes("ir(u.pagina, u.preset)"));
});
