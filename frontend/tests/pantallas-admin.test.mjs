// Pantallas del panel administrativo: lógica pura de Usuarios y que use el sistema compartido (components/ui).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  ROLES, avisoDesactivar, cuerpoEdicion, erroresDelServidor, erroresEdicion, erroresNuevoUsuario, etiquetaRol, filaUsuario,
  rolEditable, rolesParaFiltrar,
} from "../src/pages/admin/usuariosAdmin.js";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

test("Usuarios: crear exige nombre, correo válido, contraseña de 6 y un rol del equipo, con mensajes en español", () => {
  const vacio = erroresNuevoUsuario({ nombre: " ", correo: "", password: "", rol: "CLIENTE" });
  assert.deepEqual(Object.keys(vacio).sort(), ["correo", "nombre", "password", "rol"]);
  assert.equal(vacio.nombre, "Escribe el nombre completo.");
  assert.equal(erroresNuevoUsuario({ nombre: "Ana", correo: "ana@", password: "123456", rol: "ADMIN" }).correo,
    "Escribe un correo válido, como nombre@drivemaster.co.");
  assert.equal(erroresNuevoUsuario({ nombre: "Ana", correo: "ana@d.co", password: "123", rol: "ADMIN" }).password,
    "Usa al menos 6 caracteres.");
  assert.deepEqual(erroresNuevoUsuario({ nombre: "Ana", correo: "ana@d.co", password: "123456", rol: "SUPERADMIN" }), {});
  // Las tiendas y los talleres no se crean desde aquí (lo rechaza también el backend)
  assert.ok(erroresNuevoUsuario({ nombre: "Ana", correo: "ana@d.co", password: "123456", rol: "TIENDA" }).rol);
});

test("Usuarios: editar manda el nombre siempre y la contraseña y el rol solo si cambian", () => {
  const admin = { id: "u1", rol: "ADMIN" };
  assert.deepEqual(cuerpoEdicion({ nombre: " Ana ", password: "", rol: "ADMIN" }, admin), { nombre: "Ana" });
  assert.deepEqual(cuerpoEdicion({ nombre: "Ana", password: "secreta", rol: "SUPERADMIN" }, admin),
    { nombre: "Ana", password: "secreta", rol: "SUPERADMIN" });
  // El rol de un cliente, una tienda o un taller no se cambia; el del vendedor retirado sí (para reasignarlo)
  assert.deepEqual(cuerpoEdicion({ nombre: "Tienda", password: "", rol: "ADMIN" }, { rol: "TIENDA" }), { nombre: "Tienda" });
  assert.equal(rolEditable("TALLER"), false);
  assert.equal(rolEditable("VENDEDOR"), true);
  assert.deepEqual(erroresEdicion({ nombre: "Ana", password: "" }), {});
  assert.ok(erroresEdicion({ nombre: "Ana", password: "123" }).password);
});

test("Usuarios: el correo repetido se muestra en su campo y desactivar dice qué pasa con el negocio", () => {
  assert.deepEqual(erroresDelServidor("Correo ya registrado"), { correo: "Ya hay una cuenta con ese correo. Usa otro." });
  assert.equal(erroresDelServidor("Error de red"), null);
  assert.match(avisoDesactivar({ rol: "TIENDA" }), /tienda queda en pausa.*Aprobaciones/);
  assert.match(avisoDesactivar({ rol: "TALLER" }), /taller queda en pausa/);
  assert.doesNotMatch(avisoDesactivar({ rol: "ADMIN" }), /pausa/);
});

test("Usuarios: cada fila usa solo los campos de UsuarioAdminDTO", () => {
  const fila = filaUsuario({ id: "u1", nombre: "Ana", correo: "a***@d.co", rol: "ADMIN", activo: true, bloqueado: true,
    creado: "2026-10-01T10:00:00", ultimoAcceso: null });
  assert.deepEqual(Object.keys(fila), ["id", "nombre", "correo", "rol", "activo", "fechaCreacion", "ultimoLogin"]);
  assert.equal(fila.fechaCreacion, "2026-10-01T10:00:00");
  assert.equal(filaUsuario({ id: "u2", activo: false }).activo, false);
  // Los filtros por rol: los fijos siempre; mecánicos y vendedores solo si hay cuentas
  assert.deepEqual(rolesParaFiltrar({}), ["CLIENTE", "TIENDA", "TALLER", "ADMIN", "SUPERADMIN"]);
  assert.ok(rolesParaFiltrar({ VENDEDOR: 2 }).includes("VENDEDOR"));
  assert.equal(etiquetaRol("VENDEDOR"), ROLES.VENDEDOR.label);
});

test("Usuarios: sistema compartido, tabla que pasa a tarjetas, estados de carga, vacío y error, y sin el sistema viejo", () => {
  const p = leer("../src/pages/Usuarios.jsx");
  for (const pieza of ["EncabezadoPagina", "ChipEstado", "<Alerta", "onReintentar", "<EstadoVacio", "<Esqueleto", "<Campo",
    "CampoClave", "ui-table--tarjetas", "ui-table__titulo", "ui-table__acciones", "ui-buscador", 'aria-label="Rol"',
    'aria-label="Estado de la cuenta"', "ConfirmarAccion"]) {
    assert.ok(p.includes(pieza), `falta ${pieza}`);
  }
  for (const viejo of ["DrawerAdmin", "ConfirmacionAdmin", "adm-", "us-stat", "Usuarios.css", "window.confirm"]) {
    assert.ok(!p.includes(viejo), `sobra ${viejo}`);
  }
  // Los mismos endpoints de antes
  assert.ok(p.includes("usuariosPlataforma(") && p.includes("crearUsuario(") && p.includes("eliminarUsuario("));
  assert.ok(p.includes("`${API_BASE}/usuarios/${id}`") && p.includes('method: "PUT"'));
});
