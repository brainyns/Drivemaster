// Auditoría de usabilidad antes de la feria: errores claros, motivos, confirmaciones, privacidad y celular.
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import assert from "node:assert/strict";
import { AVISO_COBRO, MENSAJE_GENERICO, textoError, textoErrorPago } from "../src/utils/errores.js";

const SRC = fileURLToPath(new URL("../src/", import.meta.url));
const leer = (ruta) => readFileSync(new URL(ruta, import.meta.url), "utf8");

/** Todos los .js/.jsx de src menos los servicios (que lanzan los errores, no los muestran) y el arnés local. */
function pantallas(dir = SRC, lista = []) {
  for (const nombre of readdirSync(dir)) {
    const ruta = join(dir, nombre);
    if (statSync(ruta).isDirectory()) { if (nombre !== "services") pantallas(ruta, lista); }
    else if (/\.jsx?$/.test(nombre) && !nombre.startsWith("__preview")) lista.push(ruta);
  }
  return lista;
}

test("textoError: deja pasar las frases para la persona y cambia los textos técnicos por uno claro", () => {
  assert.equal(textoError(new Error("Stock insuficiente: de “Filtro” queda 1 unidad.")), "Stock insuficiente: de “Filtro” queda 1 unidad.");
  for (const crudo of ["Failed to fetch", "Unexpected token < in JSON at position 0", "HTTP 500", "500 Internal Server Error",
    "Cannot read properties of undefined (reading 'id')", "TypeError: x is not a function", "Load failed",
    "org.springframework.dao.DuplicateKeyException", "Request failed with status code 404", "", "x".repeat(300)]) {
    assert.equal(textoError(new Error(crudo)), MENSAJE_GENERICO, crudo);
  }
  assert.equal(textoError(null, "No pudimos guardar."), "No pudimos guardar.");
  assert.equal(textoError({}), MENSAJE_GENERICO);
  assert.equal(textoError("Ya existe un producto con ese nombre"), "Ya existe un producto con ese nombre");
});

test("pagos: el error siempre aclara que no se hizo ningún cargo", () => {
  assert.equal(textoErrorPago(new Error("Failed to fetch")), `No pudimos iniciar el pago. ${AVISO_COBRO}`);
  assert.equal(textoErrorPago(new Error("La cotización ya fue pagada")), `La cotización ya fue pagada. ${AVISO_COBRO}`);
  assert.match(AVISO_COBRO, /Si el cobro no se aplica, no se hará ningún cargo/);
  for (const ruta of ["../src/pages/CheckoutMarketplace.jsx", "../src/pages/MisServicios.jsx", "../src/pages/MisSolicitudes.jsx",
    "../src/components/servicio/PagoLocal.jsx"]) {
    assert.ok(leer(ruta).includes("textoErrorPago("), ruta);
  }
});

test("ninguna pantalla muestra e.message crudo ni usa alert()", () => {
  const permitidos = /\btest\(|campoDelDuplicado\(|erroresDelServidor\(|mensaje: \w+\.message|console\./;
  for (const ruta of pantallas()) {
    if (ruta.endsWith(join("utils", "errores.js"))) continue;
    const src = readFileSync(ruta, "utf8");
    src.split(/\r?\n/).forEach((linea, i) => {
      if (permitidos.test(linea)) return;
      assert.doesNotMatch(linea, /\b(e|err|ex|er|fallo|reason)\??\.message\b/, `${ruta}:${i + 1}`);
    });
    assert.doesNotMatch(src, /(^|[^.\w])(window\.)?alert\(/m, `${ruta} usa alert()`);
  }
});

test("motivos: 10 caracteres en todas las cancelaciones, sin contar los espacios", async () => {
  const { MINIMO_MOTIVO, errorMotivo, faltanMotivo, ayudaMotivo } = await import("../src/utils/motivo.js");
  assert.equal(MINIMO_MOTIVO, 10);
  assert.match(errorMotivo("   "), /Escribe el motivo/);
  assert.match(errorMotivo("  no vino "), /mínimo 10 caracteres \(te faltan 3\)/);
  assert.equal(errorMotivo("El cliente no vino"), null);
  assert.equal(faltanMotivo("123456789"), 1);
  assert.match(ayudaMotivo(""), /faltan 10/);
  assert.match(ayudaMotivo("1234567890"), /listo/);
  // El backend usa el mismo número
  assert.match(leer("../../drivemaster/src/main/java/com/drivemaster/drivemaster/util/Motivos.java"), /MINIMO = 10;/);
});

test("motivos: cada cancelación pide el mínimo y muestra el motivo en el diálogo antes de confirmar", async () => {
  const { textoMotivoNoDespacho } = await import("../src/pages/tienda/pedidosTienda.js");
  assert.equal(textoMotivoNoDespacho("SIN_REPUESTO", ""), "No tengo el repuesto");
  assert.equal(textoMotivoNoDespacho("OTRO", "  Se dañó el repuesto "), "Se dañó el repuesto");

  const servicios = leer("../src/pages/MisServicios.jsx");
  assert.match(servicios, /pedirMotivo etiquetaMotivo="Por qué la cancelas" minimoMotivo=\{MINIMO_MOTIVO\}/, "cliente");
  assert.ok(!servicios.includes("(opcional)"), "el motivo del cliente ya no es opcional");
  assert.match(leer("../src/pages/CompraDetalle.jsx"), /minimoMotivo=\{MINIMO_MOTIVO\}/, "compra a proveedor");
  const pedidos = leer("../src/pages/PedidosTienda.jsx");
  assert.ok(pedidos.includes("errorMotivo(nota)") && pedidos.includes("<blockquote>{textoMotivoNoDespacho(tipoMotivo, nota)}</blockquote>"),
    "tienda: no puedo despachar");
  assert.match(leer("../src/pages/admin/RetirosSinRecoger.jsx"), /deshabilitado=\{motivo\.trim\(\)\.length < MINIMO_MOTIVO\}/, "retiros sin recoger");
  assert.match(leer("../src/components/admin/DetallePlataforma.jsx"), /import \{ MINIMO_MOTIVO \} from "..\/..\/utils\/motivo"/, "admin");
  const dialogo = leer("../src/components/marketplace/ConfirmarAccion.jsx");
  assert.ok(dialogo.includes("errorMotivo(motivo, minimoMotivo)") && dialogo.includes("ayudaMotivo(motivo, minimoMotivo)"));
});
