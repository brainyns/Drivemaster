// Pruebas de estructura del menú y de las rutas por rol (sin dependencias: node --test tests/)
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const sidebar = readFileSync(new URL("../src/components/Sidebar.jsx", import.meta.url), "utf8");
const app = readFileSync(new URL("../src/App.jsx", import.meta.url), "utf8");

const OCULTAS = ["ventas", "ventaNueva", "ventaDetalle", "solicitudes", "inv-clientes", "rep-clientes", "inv-productos", "rep-productos"];

/** El texto del arreglo de cada rol dentro de allowedPages. */
function paginasPermitidas() {
  const inicio = app.indexOf("const allowedPages = {");
  const fin = app.indexOf("const esTienda", inicio);
  const bloque = app.slice(inicio, fin);
  const roles = {};
  for (const m of bloque.matchAll(/^\s{4}(\w+):\s*\[([\s\S]*?)\],?\s*$/gm)) {
    roles[m[1]] = [...m[2].replace(/\/\/.*$/gm, "").matchAll(/"([^"]+)"/g)].map((x) => x[1]);
  }
  return roles;
}

test("ningún rol tiene ruta a Ventas, Solicitudes ni al Historial anterior", () => {
  const roles = paginasPermitidas();
  assert.ok(Object.keys(roles).length >= 6, `se leyeron los roles: ${Object.keys(roles)}`);
  for (const [rol, paginas] of Object.entries(roles)) {
    for (const oculta of OCULTAS) {
      assert.ok(!paginas.includes(oculta), `${rol} no debería poder abrir "${oculta}"`);
    }
  }
});

test("el menú ya no ofrece Ventas, Solicitudes ni Historial anterior", () => {
  for (const clave of ["ventas", "solicitudes", "historial-anterior", "inv-clientes", "rep-clientes", "inv-productos", "rep-productos"]) {
    assert.ok(!sidebar.includes(`key: "${clave}"`), `el menú aún tiene "${clave}"`);
  }
});

test("Reporte de servicios sigue dentro de Reportes, junto a los reportes de plataforma", () => {
  const i = sidebar.indexOf('key: "reportes"');
  assert.ok(i > 0, "existe el grupo Reportes");
  const grupo = sidebar.slice(i, sidebar.indexOf("],", i));
  assert.ok(grupo.includes('key: "rep-servicios"'), "Reporte de servicios está en Reportes");
  assert.ok(grupo.includes('key: "reportes-plataforma"'), "Reportes de plataforma está en Reportes");
});

test("los servicios (solicitudes de diagnóstico) siguen disponibles para SUPERADMIN y ADMIN", () => {
  const roles = paginasPermitidas();
  assert.ok(roles.SUPERADMIN.includes("admin-talleres"));
  assert.ok(roles.ADMIN.includes("admin-talleres"));
  assert.ok(sidebar.includes('key: "admin-talleres", label: "Servicios"'));
});

test("el ADMIN no entra a reportes, usuarios ni clientes", () => {
  const { ADMIN } = paginasPermitidas();
  for (const p of ["usuarios", "clientes", "productos", "reportes-plataforma", "rep-servicios", "liquidaciones", "negocios"]) {
    assert.ok(!ADMIN.includes(p), `ADMIN no debería abrir ${p}`);
  }
});
