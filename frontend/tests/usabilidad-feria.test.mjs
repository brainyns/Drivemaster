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

test("reembolsos: marcarlo hecho exige el N.º de la transacción de Wompi y el Inicio avisa cuántos faltan", async () => {
  const { resumenReembolsos } = await import("../src/pages/admin/plataformaAdmin.js");
  assert.equal(resumenReembolsos([]), null);
  assert.equal(resumenReembolsos([{ estado: "REALIZADO", monto: 10 }]), null);
  assert.deepEqual(resumenReembolsos([{ estado: "PENDIENTE", monto: 50000 }, { estado: "PENDIENTE", monto: 25500 }, { estado: "REALIZADO", monto: 9 }]),
    { cantidad: 2, total: 75500 });

  const liq = leer("../src/pages/Liquidaciones.jsx");
  assert.ok(liq.includes('id="liq-transaccion"') && liq.includes("marcarReembolsado(token, id, numero.trim(), nota.trim() || null)"));
  assert.ok(liq.includes("deshabilitado={(esRepartos ? esManual : true) && !numero.trim()}"), "sin número no se confirma");
  assert.match(leer("../src/services/liquidacionService.js"), /JSON\.stringify\(\{ transaccion, nota \}\)/);
  const inicio = leer("../src/pages/DashboardPlataforma.jsx");
  assert.ok(inicio.includes('reembolsos.cantidad === 1 ? "reembolso pendiente" : "reembolsos pendientes"'));
  assert.ok(inicio.includes('ir("liquidaciones", "reembolsos")'), "el aviso lleva a la lista");
  assert.match(leer("../src/App.jsx"), /<Liquidaciones key=\{idSeleccionado \|\| "liquidaciones"\} vistaInicial=\{idSeleccionado\} \/>/);
});

test("confirmaciones: emitir la cotización, confirmar la llegada y los movimientos manuales piden un sí antes", () => {
  const cot = leer("../src/components/servicio/CotizacionForm.jsx");
  assert.ok(cot.includes("onClick={() => setConfirmando(true)}") && cot.includes("¿Enviar la cotización a ${solicitud.clienteNombre"),
    "emitir abre el diálogo con el nombre del cliente");
  assert.ok(cot.includes("onConfirmar={async () => { setConfirmando(false); await emitir(); }}"));

  const inv = leer("../src/pages/taller/TallerInventario.jsx");
  assert.ok(inv.includes("onClick={() => setConfirmaLlegada(true)}"), "Confirmar que llegó abre el diálogo");
  assert.ok(inv.includes("¿Llegaron ${unidades === 1") && inv.includes("queda habilitada para cobrar"));

  for (const ruta of ["../src/pages/taller/TallerMovimientos.jsx", "../src/pages/Movimientos.jsx"]) {
    const src = leer(ruta);
    assert.ok(src.includes("setConfirmando(true);") && src.includes("onConfirmar={guardar}"), `${ruta}: confirma antes de guardar`);
    assert.ok(src.includes("<blockquote>{form.motivo.trim()}</blockquote>"), `${ruta}: muestra el motivo`);
    assert.match(src, /El stock pasa de \$\{/, `${ruta}: dice cómo queda el stock`);
  }
});

test("privacidad: cuenta y documento enmascarados con Mostrar, y la tienda no ve las notas internas del admin", async () => {
  const { enmascarar } = await import("../src/utils/formato.js");
  assert.equal(enmascarar("0123-4567 8901"), "•••• 8901");
  assert.equal(enmascarar("1234"), "••••");
  assert.equal(enmascarar(""), "");
  assert.equal(enmascarar(null), "");
  for (const ruta of ["../src/pages/DatosMiTienda.jsx", "../src/pages/taller/TallerMiTaller.jsx"]) {
    const src = leer(ruta);
    assert.match(src, /<ValorSensible valor=\{\w+\.cuentaBancaria\.numeroCuenta\}/, `${ruta}: cuenta`);
    assert.match(src, /<ValorSensible valor=\{\w+\.cuentaBancaria\.titularDocumentoNumero\}/, `${ruta}: documento`);
  }
  assert.ok(leer("../src/components/ui/ValorSensible.jsx").includes('{visible ? "Ocultar" : "Mostrar"}'));

  const { resolucionPublica, historialPedido } = await import("../src/pages/tienda/pedidosTienda.js");
  assert.match(resolucionPublica("REEMBOLSO"), /Se reembolsó al cliente/);
  assert.equal(resolucionPublica(undefined), null);
  const pasos = historialPedido({ fechaCreacion: "2026-11-01T10:00:00", fechaResolucionProblema: "2026-11-02T10:00:00",
    resolucionProblema: "ENTREGA_CONFIRMADA_POR_ADMIN", motivoResolucion: "nota interna del admin" });
  assert.ok(!JSON.stringify(pasos).includes("nota interna"), "el historial no usa la nota del admin");
  assert.ok(!leer("../src/pages/PedidosTienda.jsx").includes("motivoResolucionRetiro"));
  assert.match(leer("../../drivemaster/src/main/java/com/drivemaster/drivemaster/service/PedidoTiendaService.java"),
    /\.motivoResolucionRetiro\(null\)\.retiroResueltoPor\(null\)/);
});

test("rechazar una solicitud avisa que pasa a otro taller", () => {
  const src = leer("../src/pages/TallerSolicitudes.jsx");
  assert.ok(src.includes("Si la rechazas, la solicitud pasa al siguiente taller cercano"), "antes de confirmar");
  assert.ok(src.includes('avisar("Solicitud rechazada: pasó al siguiente taller cercano.")'), "después de rechazar");
});

test("celular: los lotes pasan a tarjetas, las tablas anchas tienen scroll y los botones táctiles miden 44 px", () => {
  const inv = leer("../src/pages/taller/TallerInventario.jsx");
  assert.ok(inv.includes('className="iv-tabla iv-tabla--tarjetas"'));
  for (const etiqueta of ["Compra", "Tienda", "Quedan", "Pagaste", "Costo"]) assert.ok(inv.includes(`data-label="${etiqueta}"`), etiqueta);
  assert.match(leer("../src/css/inventario-taller.css"), /@media \(max-width: 600px\) \{\s*\.iv-tabla--tarjetas thead \{ display: none; \}/);

  // Toda tabla de las pantallas está dentro de un contenedor con scroll o es de tarjetas
  for (const ruta of pantallas()) {
    const src = readFileSync(ruta, "utf8");
    if (!src.includes("<table") || ruta.endsWith("Grafico.jsx")) continue;
    const conScroll = /ui-table-wrap|mk-tabla-scroll|--tarjetas|it-solo-lectores/.test(src);
    assert.ok(conScroll, `${ruta}: tabla sin scroll ni tarjetas`);
  }

  const ui = leer("../src/css/ui.css");
  const tactil = ui.slice(ui.indexOf("@media (pointer: coarse)"));
  assert.ok(tactil.length > 30, "hay reglas para pantallas táctiles");
  assert.match(tactil, /\.ui-btn, \.ui-btn--chico, \.ui-chip, \.dm-btn, \.sv-btn/);
  assert.match(tactil, /min-height: 44px;/);
});

test("palabras simples: no quedan los términos viejos del dinero y del inventario en ninguna pantalla", async () => {
  // Texto visible: lo que va entre comillas, en plantillas o entre etiquetas. Los nombres del código (liquidado,
  // porLiquidar, rutas /liquidaciones) no son texto y no cuentan.
  const VIEJOS = [/\bLiquidad[oa]s?\b/, /\bliquidad[oa]s?\b/, /\b[Ll]iquidar\b/, /\b[Ll]iquidaci(ón|ones)\b/, /\b[Ee]n espera\b/,
    /\b[Ss]aldo de (la )?cotización\b/, /\b[Aa]nticipo\b/, /\b[Mm]ermas?\b/, /\b[Cc]arga inicial\b/, /\b[Aa]justes? por conteo\b/,
    /\b[Aa]nulad[oa]s?\b/, /[Rr]esponsable de IVA/];
  // En las plantillas solo cuenta el texto: las expresiones ${…} son código
  const textos = (src) => [...src.matchAll(/"([^"\n]*)"|`([^`]*)`|>([^<>{}\n]+)</g)]
    .map((m) => m[1] ?? (m[2] != null ? m[2].replace(/\$\{[^}]*\}/g, " ") : m[3]));
  const codigo = (t) => /^[\w./-]+$/.test(t) || /^\/api|\$\{BASE\}|import |from /.test(t);
  for (const ruta of pantallas()) {
    const src = readFileSync(ruta, "utf8").replace(/^\s*(\/\/|\*|\/\*).*$/gm, "");
    for (const t of textos(src)) {
      if (codigo(t)) continue;
      for (const viejo of VIEJOS) assert.doesNotMatch(t, viejo, `${ruta}: «${t.slice(0, 80)}»`);
    }
  }
  const { GLOSARIO, terminos } = await import("../src/utils/glosario.js");
  for (const t of ["Ya te pagaron", "Por recibir", "Falta que se entregue", "Adelanto del diagnóstico", "Resta de la cotización", "Comisión", "IVA"]) {
    assert.ok(GLOSARIO[t], t);
  }
  assert.deepEqual(terminos("IVA", "no existe").map((x) => x.termino), ["IVA"]);
  for (const ruta of ["../src/pages/InformesTienda.jsx", "../src/pages/taller/TallerInformes.jsx", "../src/pages/ReportesPlataforma.jsx", "../src/pages/Liquidaciones.jsx"]) {
    assert.match(leer(ruta), /<Glosario (className="[^"]+" )?claves=\{\[/, `${ruta}: glosario`);
  }
});

test("informes claros: banda Ventas − IVA − Comisión = Lo tuyo, Este mes con meses=1 y las cifras de cada rol", async () => {
  const { bandaDinero, loTuyoDelMes, gananciaDelMes } = await import("../src/utils/bandaDinero.js");
  const desglose = { venta: 595000, comision: 50000, comisionDescontada: 50000, recibes: 545000, comisionSeDescuenta: true };
  assert.deepEqual(bandaDinero(desglose, 95000), { ventas: 595000, iva: 95000, comision: 50000, comisionSeDescuenta: true, loTuyo: 450000 });
  // Con el cobro apagado no se resta una comisión que no se descontó
  assert.equal(bandaDinero({ ...desglose, comisionDescontada: 0, comisionSeDescuenta: false }, 95000).loTuyo, 500000);
  assert.equal(bandaDinero(desglose, null).loTuyo, null, "sin IVA conocido no se inventa lo tuyo");
  assert.equal(bandaDinero(null, 0), null);
  assert.equal(loTuyoDelMes(desglose, 95000), 450000);
  assert.equal(gananciaDelMes({ meses: [{ mes: "2026-10", cobrada: 10 }, { mes: "2026-11", cobrada: 12345.6 }] }), 12346);
  assert.equal(gananciaDelMes({ meses: [] }), null);

  const taller = leer("../src/pages/taller/TallerInformes.jsx");
  assert.ok(taller.includes("informesTaller(token, 1).then((m) => loTuyoDelMes(m.ingresos?.desglose, m.ingresos?.iva))"), "taller: este mes exacto");
  assert.ok(taller.includes("bandaDinero(d.ingresos.desglose, d.ingresos.iva)"));
  assert.ok(taller.includes('<Indicador titulo="Mano de obra"') && taller.includes('<Indicador titulo="Repuestos"'), "mano de obra y repuestos separados");
  assert.ok(taller.includes("<Grafico"), "gráfica");

  const tienda = leer("../src/pages/InformesTienda.jsx");
  assert.ok(tienda.includes("informesTienda(token, 1).then((m) => loTuyoDelMes(m.dinero?.desglose, m.dinero?.iva))"), "tienda: este mes exacto");
  assert.ok(tienda.includes("bandaDinero(datos.dinero?.desglose, datos.dinero?.iva)"));
  assert.ok(tienda.includes('<Indicador titulo="Ya te pagaron"') && tienda.includes('<Indicador titulo="Por recibir"'));

  const admin = leer("../src/pages/ReportesPlataforma.jsx");
  for (const t of ['titulo="Tu ganancia"', 'titulo="Por cobrar"', 'titulo="Dinero que pasó por la plataforma"', "<Grafico"]) assert.ok(admin.includes(t), t);
  assert.ok(admin.includes("reporteComisionPlataforma(token, { meses: 1 }).then(gananciaDelMes)"), "admin: este mes exacto");

  const esteMes = leer("../src/components/ui/EsteMes.jsx");
  assert.ok(!/<(input|select|button)/.test(esteMes), "Este mes es solo lectura");
});

test("formato es-CO: coma decimal, punto de miles y espacio antes de %, en todo lo visible", async () => {
  const { decimal, porcentaje, horas, kilometros } = await import("../src/utils/formato.js");
  assert.equal(horas(41.3), "41,3 h");
  assert.equal(porcentaje(83.9), "83,9 %");
  assert.equal(kilometros(0.7), "0,7 km");
  assert.equal(decimal(1234.5), "1.234,5");
  assert.equal(decimal(4, { fijo: 1 }), "4,0");
  assert.equal(decimal(19.46), "19,5");
  assert.equal(porcentaje(null), "—");
  const { formatoDistancia } = await import("../src/utils/distancia.js").catch(() => ({}));
  if (formatoDistancia) assert.equal(formatoDistancia(3.4), "a 3,4 km");

  // Ningún número decimal se escribe con toFixed (punto) en una pantalla, salvo las coordenadas del mapa
  for (const ruta of pantallas()) {
    const src = readFileSync(ruta, "utf8");
    src.split(/\r?\n/).forEach((linea, i) => {
      if (!linea.includes(".toFixed(") || /latitud|longitud|clavePunto/.test(linea)) return;
      assert.fail(`${ruta}:${i + 1} usa toFixed: ${linea.trim().slice(0, 90)}`);
    });
    // Ni porcentajes pegados al número ("83%")
    for (const m of src.matchAll(/\{[^}]+\}%|\$\{[^}]+\}%/g)) {
      if (/width|height|style|transform/.test(m[0] + src.slice(Math.max(0, m.index - 40), m.index))) continue;
      assert.fail(`${ruta}: porcentaje sin formato: ${m[0]}`);
    }
  }
});
