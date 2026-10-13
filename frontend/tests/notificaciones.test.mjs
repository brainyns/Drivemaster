// Pruebas de la campanita (sin dependencias: node --test tests/)
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  tieneCampanita, haceCuanto, tonoDeAviso, agregarAviso, marcarLeidaEnLista, marcarTodasEnLista,
  productosConStockBajo, urlWebSocket, retrasoReconexion, textoContador, MAX_AVISOS,
} from "../src/services/notificacionesUtil.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");
const websocket = leer("../src/services/websocketService.js");
const topbar = leer("../src/components/TopBar.jsx");
const app = leer("../src/App.jsx");
const avisosJava = leer("../../drivemaster/src/main/java/com/drivemaster/drivemaster/service/AvisosSolicitudServicio.java");

/** Páginas permitidas de cada rol (mismo lector que menu.test.mjs). */
function paginasPermitidas() {
  const inicio = app.indexOf("const allowedPages = {");
  const bloque = app.slice(inicio, app.indexOf("const esTienda", inicio));
  const roles = {};
  for (const m of bloque.matchAll(/^\s{4}(\w+):\s*\[([\s\S]*?)\],?\s*$/gm)) {
    roles[m[1]] = [...m[2].replace(/\/\/.*$/gm, "").matchAll(/"([^"]+)"/g)].map((x) => x[1]);
  }
  const taller = app.slice(app.indexOf("const SECCION_TALLER = {"), app.indexOf("};", app.indexOf("const SECCION_TALLER = {")));
  roles.TALLER = [...(roles.TALLER || []), ...[...taller.matchAll(/"([\w-]+)":/g)].map((x) => x[1])];
  return roles;
}

test("taller, tienda, ADMIN y SUPERADMIN tienen campanita; el cliente y el vendedor no (por ahora)", () => {
  for (const rol of ["TALLER", "TIENDA", "ADMIN", "SUPERADMIN"]) assert.ok(tieneCampanita(rol), rol);
  for (const rol of ["CLIENTE", "VENDEDOR", undefined, null]) assert.ok(!tieneCampanita(rol), String(rol));
  assert.ok(topbar.includes("tieneCampanita(user?.rol)"), "la TopBar decide con tieneCampanita");
});

test("el WebSocket se abre con un ticket de un solo uso, nunca con el token de sesión en la URL", () => {
  assert.equal(urlWebSocket("http://localhost:8080", "abc"), "ws://localhost:8080/ws/notificaciones?ticket=abc");
  assert.equal(urlWebSocket("https://api.demo.com/", "a+b/c"), "wss://api.demo.com/ws/notificaciones?ticket=a%2Bb%2Fc");
  assert.ok(!websocket.includes("token="), "websocketService no arma ?token=");
  assert.ok(!websocket.includes("getToken"), "websocketService no lee el token de sesión");
  assert.ok(websocket.includes("pedirTicket()"), "cada conexión pide un ticket nuevo");
});

test("la reconexión espera cada vez más, hasta 30 segundos", () => {
  assert.equal(retrasoReconexion(0), 3000);
  assert.equal(retrasoReconexion(1), 6000);
  assert.equal(retrasoReconexion(50), 30000);
});

test("un aviso en vivo se agrega arriba, sin duplicarse y sin pasar del máximo", () => {
  const lista = Array.from({ length: MAX_AVISOS }, (_, i) => ({ id: `n${i}` }));
  const nueva = agregarAviso(lista, { id: "nuevo" });
  assert.equal(nueva.length, MAX_AVISOS);
  assert.equal(nueva[0].id, "nuevo");
  assert.equal(agregarAviso(nueva, { id: "nuevo" }).filter((a) => a.id === "nuevo").length, 1);
  assert.equal(agregarAviso(lista, null), lista);
});

test("marcar como leídas no cambia las demás ni modifica la lista original", () => {
  const lista = [{ id: "a", leida: false }, { id: "b", leida: false }];
  const una = marcarLeidaEnLista(lista, "a");
  assert.deepEqual(una.map((a) => a.leida), [true, false]);
  assert.equal(lista[0].leida, false);
  assert.ok(marcarTodasEnLista(lista).every((a) => a.leida));
});

test("tiempo relativo de cada aviso", () => {
  const ahora = Date.parse("2026-10-12T12:00:00Z");
  assert.equal(haceCuanto("2026-10-12T11:59:40Z", ahora), "Ahora");
  assert.equal(haceCuanto("2026-10-12T11:45:00Z", ahora), "Hace 15 min");
  assert.equal(haceCuanto("2026-10-12T09:00:00Z", ahora), "Hace 3 h");
  assert.equal(haceCuanto("2026-10-10T12:00:00Z", ahora), "Hace 2 días");
  assert.equal(haceCuanto("2026-10-11T11:00:00Z", ahora), "Hace 1 día");
  assert.equal(haceCuanto(null, ahora), "");
  assert.equal(haceCuanto("no-es-fecha", ahora), "");
});

test("color de cada tipo de aviso", () => {
  assert.equal(tonoDeAviso("PEDIDO_NUEVO"), "ok");
  assert.equal(tonoDeAviso("SOLICITUD_NUEVA"), "marca");
  assert.equal(tonoDeAviso("ALERTA_ADMIN"), "error");
  assert.equal(tonoDeAviso("COTIZACION_EMITIDA"), "info");
  assert.equal(tonoDeAviso("DESCONOCIDO"), "info");
});

test("todos los tipos que manda el backend tienen un color conocido o caen en info", () => {
  const tipos = [...avisosJava.matchAll(/Notificacion\.([A-Z_]+)/g)].map((m) => m[1]);
  assert.ok(tipos.length >= 15, `se leyeron ${tipos.length} tipos`);
  for (const t of tipos) assert.ok(["ok", "marca", "error", "info"].includes(tonoDeAviso(t)), t);
});

test("stock bajo: solo productos activos en el mínimo o por debajo, agotados primero", () => {
  const productos = [
    { id: "1", stockActual: 5, stockMinimo: 2 },
    { id: "2", stockActual: 2, stockMinimo: 2 },
    { id: "3", stockActual: 0, stockMinimo: 1 },
    { id: "4", stockActual: 0, stockMinimo: 1, activo: false },
    { id: "5", stockActual: null, stockMinimo: 1 },
  ];
  assert.deepEqual(productosConStockBajo(productos).map((p) => p.id), ["3", "2"]);
  assert.deepEqual(productosConStockBajo(null), []);
});

test("el contador muestra 99+ cuando hay muchas", () => {
  assert.equal(textoContador(7), "7");
  assert.equal(textoContador(120), "99+");
});

test("cada enlace de los avisos del backend lleva a una página que su destinatario puede abrir", () => {
  const roles = paginasPermitidas();
  const DESTINO = {
    "taller-": ["TALLER"],
    "pedidos-tienda": ["TIENDA"],
    "problemas-envios": ["ADMIN", "SUPERADMIN"],
  };
  const enlaces = new Set([...avisosJava.matchAll(/"((?:taller-|pedidos-tienda|problemas-envios|mis-)[\w-]*)"/g)].map((m) => m[1]));
  assert.ok(enlaces.size >= 5, `se leyeron ${[...enlaces]}`);
  for (const enlace of enlaces) {
    if (enlace.startsWith("mis-")) {
      // Páginas del cliente (su campanita llega en la fase final)
      assert.ok(app.includes(`"${enlace}"`), `${enlace} existe en App.jsx`);
      continue;
    }
    const clave = Object.keys(DESTINO).find((k) => enlace.startsWith(k));
    assert.ok(clave, `enlace sin destinatario conocido: ${enlace}`);
    for (const rol of DESTINO[clave]) {
      assert.ok(roles[rol]?.includes(enlace), `${rol} no puede abrir "${enlace}"`);
    }
  }
});
