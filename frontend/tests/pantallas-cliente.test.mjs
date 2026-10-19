// Pruebas de estructura de las pantallas del cliente: que usen las piezas compartidas (components/ui) y tengan
// sus estados completos (carga, vacío con acción, error con reintento).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

test("Mis vehículos: tarjetas compartidas, campos con etiqueta y los tres estados", () => {
  const p = leer("../src/pages/MisVehiculos.jsx");
  assert.match(p, /<TarjetaVehiculo /);
  assert.match(p, /<Esqueleto variante="tarjetas"/);
  assert.match(p, /<EstadoVacio icono="car"/);
  assert.match(p, /<Alerta onReintentar=\{reintentar\}>/);
  // Todos los campos del alta pasan por Campo (etiqueta asociada); ya no hay <label> sueltos
  assert.doesNotMatch(p, /<label>/);
  assert.match(p, /role="switch"/);
  assert.doesNotMatch(leer("../src/css/servicios.css"), /sv-manto/);
});

test("Mis compras: cabecera compartida, chips de estado con texto y estados completos", () => {
  const p = leer("../src/pages/MisCompras.jsx");
  assert.match(p, /<EncabezadoPagina/);
  assert.equal((p.match(/<ChipEstado tono=/g) || []).length, 3);
  assert.match(p, /<Esqueleto variante="lista"/);
  assert.match(p, /<EstadoVacio icono="box" titulo="Aún no tienes compras"/);
  assert.match(p, /onReintentar=\{reintentar\}/);
  assert.match(p, /<details className="mk-compra__detalle">/);
  assert.doesNotMatch(p, /MIS COMPRAS|dm-eyebrow|mk-etiqueta/);
});

test("Mis servicios: filtros por estado, chips con texto, sin kickers y estados completos", async () => {
  const { contarServicios, filtrarServicios } = await import("../src/components/servicio/filtrosServicios.js");
  const lista = [{ estado: "COTIZADA" }, { estado: "ENTREGADA" }, { estado: "CANCELADA" }, { estado: "EN_INSTALACION" }, { estado: "RECHAZADA" }];
  assert.deepEqual(contarServicios(lista), { todas: 5, enCurso: 2, finalizadas: 3 });
  assert.deepEqual(filtrarServicios(lista, "enCurso").map((s) => s.estado), ["COTIZADA", "EN_INSTALACION"]);

  const p = leer("../src/pages/MisServicios.jsx");
  assert.match(p, /<Esqueleto variante="lista"/);
  assert.match(p, /<EstadoVacio icono="tool" titulo="Aún no has pedido diagnósticos"/);
  assert.match(p, /onReintentar=\{reintentar\}/);
  assert.match(p, /className="ui-chip" aria-pressed=\{filtro === f\.id\}/);
  assert.doesNotMatch(p, /ss-eyebrow|<EstadoServicio|<Etiqueta /);
  assert.doesNotMatch(leer("../src/css/solicitud-servicio.css"), /border-left: 4px/);
});

test("Mi perfil: secciones de datos, seguridad y preferencias, con validación y aviso al guardar", async () => {
  const { validarClave, validarDatos, cuerpoPerfil, datosDesdePerfil } = await import("../src/pages/reglasPerfil.js");
  assert.deepEqual(Object.keys(validarDatos({ nombre: " ", telefono: "abc" })), ["nombre", "telefono"]);
  assert.deepEqual(validarDatos({ nombre: "Ana", telefono: "" }), {});
  assert.deepEqual(Object.keys(validarClave({ actual: "", nueva: "123", confirmar: "12" })), ["actual", "nueva", "confirmar"]);
  assert.equal(validarClave({ actual: "secreta", nueva: "secreta", confirmar: "secreta" }).nueva,
    "La nueva contraseña debe ser distinta de la actual.");
  const d = datosDesdePerfil({ telefono: "300", ciudad: "Cali" }, "Ana");
  assert.equal(d.nombre, "Ana");
  assert.deepEqual(cuerpoPerfil({ ...d, direccion: " Cra 1 " }),
    { identificacion: "", telefono: "300", ciudad: "Cali", direccion: "Cra 1", referencia: "" });

  const p = leer("../src/pages/PerfilCliente.jsx");
  for (const t of ["Datos personales", "Seguridad", "Preferencias"]) assert.match(p, new RegExp(`titulo="${t}"`));
  assert.match(p, /Guardamos tus datos\./);
  assert.match(p, /cambiarPassword\(token, clave\.actual, clave\.nueva\)/);
  assert.equal((p.match(/<CampoClave /g) || []).length, 3);
  assert.doesNotMatch(p, /EditarNombre/);
  assert.doesNotMatch(leer("../src/css/perfil-cliente.css"), /\.pc-pagina \.ui-btn/);
});

test("Tu carrito: confirmar al vaciar, deshacer al quitar, resumen con envío e IVA y barra de pago en celular", () => {
  const p = leer("../src/pages/CarritoMarketplace.jsx");
  assert.doesNotMatch(p, /window\.confirm/);
  assert.match(p, /<ConfirmarAccion\s+titulo="Vaciar el carrito"/);
  assert.match(p, /Deshacer/);
  assert.match(p, /<dt>Envío<\/dt>/);
  assert.match(p, /<dt>IVA<\/dt>/);
  assert.match(p, /<BarraAccionMovil etiqueta="Subtotal"/);
  assert.match(p, /<EstadoVacio icono="box" titulo="Tu carrito está vacío"/);
  // El paso al pago sigue siendo el mismo
  assert.match(p, /onPagar: onContinuarCatalogo/);
});
