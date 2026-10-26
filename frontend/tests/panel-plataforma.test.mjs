// Panel de la plataforma (SUPERADMIN): lógica pura de cada pantalla y que use el sistema compartido (components/ui).
import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";
import {
  ESTADOS_NEGOCIO, ESTADOS_PEDIDO, SIN_PLAN, actividadNegocio, barras, chipRevision, estadosConPedidos, filtrosInicialesPedidos, refPedido, conteoRevision, estadoNegocio, filtrarRevision, filtrosInicialesNegocios,
  perfilNegocio, urgentesDashboard,
} from "../src/pages/admin/plataformaAdmin.js";

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

test("Tiendas y talleres: perfil completo según los datos que pide el backend a cada tipo", () => {
  assert.deepEqual(perfilNegocio({ tipo: "TIENDA", datosPorCompletar: [] }), { completos: 3, total: 3, pct: 100, completo: true, faltan: [] });
  const taller = perfilNegocio({ tipo: "TALLER", datosPorCompletar: ["teléfono"] });
  assert.equal(taller.completos, 3);
  assert.equal(taller.total, 4);
  assert.equal(taller.pct, 75);
  assert.equal(taller.completo, false);
  assert.equal(perfilNegocio({ tipo: "TIENDA" }).completo, true, "sin la lista no inventa faltantes");
});

test("Tiendas y talleres: estado con texto, la pausa de un aprobado, actividad y filtros desde el dashboard", () => {
  assert.deepEqual(estadoNegocio({ estado: "PENDIENTE", activo: true }), { label: "Pendiente", plural: "Pendientes", tono: "aviso" });
  assert.deepEqual(estadoNegocio({ estado: "APROBADO", activo: false }), { label: "En pausa", tono: "apagado" });
  assert.equal(estadoNegocio({ estado: "RECHAZADO", activo: false }).label, "Rechazado");
  assert.deepEqual(actividadNegocio({ tipo: "TIENDA", productos: 1, productosActivos: 1 }), { texto: "1 producto", detalle: "1 activo" });
  assert.equal(actividadNegocio({ tipo: "TALLER", ordenes: 12, ordenesEntregadas: 8 }).texto, "12 órdenes");
  assert.equal(actividadNegocio({ tipo: "TALLER" }).texto, "0 órdenes", "un taller sin órdenes no rompe la fila");
  assert.deepEqual(filtrosInicialesNegocios("TALLER"), { tipo: "TALLER", estado: "", plan: "" });
  assert.equal(filtrosInicialesNegocios({ abrir: "x" }).tipo, "");
  assert.equal(SIN_PLAN, "SIN_PLAN", "el mismo valor que entiende el backend");
});

test("Tiendas y talleres: sistema compartido, filtros por tipo y estado (sin planes), y carga, vacío y error con Reintentar", () => {
  const p = usaSistemaCompartido("NegociosPlataforma.jsx", ["EncabezadoPagina", "<LogoNegocio", "url={n.logoUrl}", "<Calificacion",
    "ChipEstado", "BarraProgreso", "<Alerta", "onReintentar", "<EstadoVacio", "<Esqueleto", "<Campo", "ui-buscador",
    "ui-table--tarjetas", 'aria-label="Tipo de negocio"', 'aria-label="Estado de aprobación"', "Limpiar filtros"]);
  // Las mismas llamadas de siempre
  for (const llamada of ["negociosPlataforma(", "resolverAprobacionTaller", "resolverAprobacionTienda", "activarDesactivarTaller",
    "activarDesactivarTienda"]) {
    assert.ok(p.includes(llamada), `falta ${llamada}`);
  }
  // Las suscripciones están apagadas (suscripciones.habilitadas=false): ni planes ni filtro por plan
  for (const plan of ["activarPlan(", "listarPlanes(", "SIN_PLAN"]) assert.ok(!p.includes(plan), `sobra ${plan}`);
  assert.equal(ESTADOS_NEGOCIO.ARCHIVADO.label, "Archivado");
  assert.ok(!p.includes("toFixed("), "la calificación la muestra Calificacion (con «Nuevo»)");
  assert.ok(leer("../src/App.jsx").includes("filtroInicial={idSeleccionado} />") && leer("../src/pages/DashboardPlataforma.jsx").includes('ir("negocios", "TALLER")'));
});

test("Aprobaciones: los anteriores al marketplace cuentan como aprobados, búsqueda sin tildes y los recientes primero", () => {
  const negocios = [
    { tipo: "TALLER", id: "w1", nombre: "Mecánica Bogotá", estadoAprobacion: null, activo: true, fechaRegistro: "2026-01-01T00:00:00" },
    { tipo: "TIENDA", id: "t1", nombre: "Frenos Sur", nit: "900123", estadoAprobacion: "PENDIENTE", fechaRegistro: "2026-10-01T00:00:00" },
    { tipo: "TIENDA", id: "t2", nombre: "Baterías Norte", correoContacto: "ventas@norte.co", estadoAprobacion: "PENDIENTE", fechaRegistro: "2026-10-05T00:00:00" },
    { tipo: "TALLER", id: "w2", nombre: "Taller Cerrado", estadoAprobacion: "APROBADO", activo: false },
  ];
  assert.deepEqual(conteoRevision(negocios), { PENDIENTE: 2, APROBADO: 2, RECHAZADO: 0 });
  assert.deepEqual(conteoRevision(negocios, "TIENDA"), { PENDIENTE: 2, APROBADO: 0, RECHAZADO: 0 });
  assert.deepEqual(filtrarRevision(negocios, { estado: "PENDIENTE" }).map((n) => n.id), ["t2", "t1"]);
  assert.deepEqual(filtrarRevision(negocios, { estado: "APROBADO", busqueda: "bogota" }).map((n) => n.id), ["w1"]);
  assert.deepEqual(filtrarRevision(negocios, { estado: "PENDIENTE", busqueda: "900123" }).map((n) => n.id), ["t1"]);
  assert.deepEqual(filtrarRevision(negocios, { estado: "PENDIENTE", busqueda: "norte.co" }).map((n) => n.id), ["t2"]);
  assert.deepEqual(chipRevision(negocios[0]), { label: "Activo", tono: "ok" });
  assert.deepEqual(chipRevision(negocios[3]), { label: "Inactivo", tono: "apagado" });
  assert.equal(chipRevision(negocios[1]).tono, "aviso");
});

test("Aprobaciones: sistema compartido, detalle con Dato y confirmación con Campo, y las mismas llamadas", () => {
  const p = usaSistemaCompartido("Aprobaciones.jsx", ["EncabezadoPagina", "ChipEstado", "<LogoNegocio", "<Alerta", "onReintentar",
    "<EstadoVacio", "<Esqueleto", "<Campo", "<Dato", "ui-buscador", "ui-table--tarjetas", 'aria-label="Estado de revisión"',
    'aria-label="Tipo de negocio"', "DrawerAdmin", "ConfirmacionAdmin"], ["adm-", "db-kpi"]);
  for (const llamada of ["listarTalleres(token)", "listarTiendas(token)", "resolverAprobacionTaller",
    "resolverAprobacionTienda", "activarDesactivarTaller", "activarDesactivarTienda"]) {
    assert.ok(p.includes(llamada), `falta ${llamada}`);
  }
  // Se aprueba sin plan: DriveMaster cobra solo la comisión
  assert.ok(p.includes("{ aprobado: true }") && !p.includes("listarPlanes(") && !p.includes("planCodigo"));
});

test("Pedidos y envíos: estados en el orden del pedido con tono, referencia corta y atrasados desde el dashboard", () => {
  assert.deepEqual(estadosConPedidos({ ENTREGADO: 4, PENDIENTE: 2, RARO: 1, DESPACHADO: 0 }), ["PENDIENTE", "ENTREGADO", "RARO"]);
  assert.equal(ESTADOS_PEDIDO.PENDIENTE.label, "Por despachar");
  assert.equal(ESTADOS_PEDIDO.NO_ENTREGADO.tono, "error");
  assert.equal(refPedido("66f0a1b2c3d4e5f6a7b8c9d0"), "#B8C9D0");
  assert.equal(filtrosInicialesPedidos("atrasados").atrasados, true);
  assert.equal(filtrosInicialesPedidos({ abrir: "x" }).atrasados, false);
});

test("Pedidos y envíos: sistema compartido, alertas que filtran y carga, vacío y error con Reintentar", () => {
  const p = usaSistemaCompartido("PedidosEnvios.jsx", ["EncabezadoPagina", "ChipEstado", "catalogo={ESTADOS_PEDIDO}", "<Alerta",
    "onReintentar", "<EstadoVacio", "<Esqueleto", "ui-table--tarjetas", 'aria-label="Estado del pedido"',
    'aria-label="Pedidos que piden atención"', "filtrosInicialesPedidos(filtroInicial)"], ["adm-", "db-kpi", "admin-base.css", "dp-alerta"]);
  assert.ok(p.includes("pedidosPlataforma(token") && p.includes('estado: filtros.estado === "PROBLEMA"'), "el mismo endpoint y filtros");
});

test("Problemas de envío: sistema compartido, decisión con motivo en Campo y las mismas llamadas", () => {
  const p = usaSistemaCompartido("ProblemasEnvios.jsx", ["EncabezadoPagina", "ChipEstado", "<Alerta", "onReintentar", "<EstadoVacio",
    "<Esqueleto", "<Campo", "ui-table--tarjetas", 'aria-label="Problemas por estado"', "ConfirmacionAdmin"], ["adm-", "db-kpi"]);
  assert.ok(p.includes("problemasDeEnvio(token)") && p.includes("resolverProblemaEnvio(token, pedido.id, resolucion, motivo.trim())"));
  assert.ok(p.includes("deshabilitado={!motivo.trim()}"), "sin motivo no se puede decidir");
});

test("Liquidaciones: sistema compartido, estados con texto y el número de transferencia obligatorio en modo manual", () => {
  const p = usaSistemaCompartido("Liquidaciones.jsx", ["EncabezadoPagina", "ChipEstado", "<Indicador", "<Alerta", "onReintentar",
    "<EstadoVacio", "<Esqueleto", "<Campo", "ui-table--tarjetas", 'aria-label="Tipo"', 'aria-label="Estado"', "ConfirmacionAdmin"], ["adm-", "db-kpi"]);
  for (const llamada of ["listarRepartos(token, estado)", "listarReembolsos(token, estado)", "liquidarReparto(token, id,", "marcarReembolsado(token, id,", "obtenerModo(token)"]) {
    assert.ok(p.includes(llamada), `falta ${llamada}`);
  }
  assert.ok(p.includes("deshabilitado={esRepartos && esManual && !numero.trim()}"));
});

test("Servicios: sistema compartido, cifras con Indicador y el formulario con Campo, sin cambiar las llamadas", () => {
  const p = usaSistemaCompartido("AdminTalleres.jsx", ["EncabezadoPagina", "<Indicador", "ChipEstado", "<LogoNegocio", "<Alerta",
    "onReintentar", "<EstadoVacio", "<Esqueleto", "<Campo", "CampoClave", "ui-buscador", "ui-table--tarjetas",
    'aria-label="Filtrar por estado"', "DrawerAdmin", "ConfirmacionAdmin"], ["adm-", "db-kpi", "at-stat", "admin-talleres.css"]);
  for (const llamada of ["listarTalleres(token)", "crearTaller(token, payload)", "actualizarTaller(token, editandoId, payload)",
    "activarDesactivarTaller(token, t.id, !t.activo)", "ordenesPorTallerAdmin(token, t.id)"]) {
    assert.ok(p.includes(llamada), `falta ${llamada}`);
  }
  assert.ok(p.includes('getUser()?.rol === "SUPERADMIN"'), "el ADMIN sigue sin crear, editar ni pausar");
});

test("Productos: catálogo de solo lectura con el sistema compartido, los mismos filtros y el mismo endpoint", () => {
  const p = usaSistemaCompartido("ProductosPlataforma.jsx", ["EncabezadoPagina", "ChipEstado", "<Alerta", "onReintentar", "<EstadoVacio",
    "<Esqueleto", "ui-buscador", "ui-table--tarjetas", 'aria-label="Estado del producto"', '"sin-stock"', "nuevosDias", "tiendaId"]);
  assert.ok(p.includes("productosPlataforma(token, { ...filtros, q, pagina, tamano: TAMANO })"));
  for (const accion of ["Editar", "Eliminar", "Nuevo producto"]) assert.ok(!p.includes(accion), `solo lectura: sobra ${accion}`);
});

test("Clientes: lista de solo lectura con el sistema compartido y sin datos personales de más", () => {
  const p = usaSistemaCompartido("ClientesPlataforma.jsx", ["EncabezadoPagina", "ChipEstado", "<LogoNegocio", "<Alerta", "onReintentar",
    "<EstadoVacio", "<Esqueleto", "ui-buscador", "ui-table--tarjetas", 'aria-label="Estado de la cuenta"']);
  assert.ok(p.includes("clientesPlataforma(token, { ...filtros, q, pagina, tamano: TAMANO })"));
  for (const dato of ["telefono", "direccion", "documento"]) assert.ok(!p.includes(`c.${dato}`), `no muestra ${dato}`);
});

test("Reportes de plataforma: sistema compartido, comisión con Indicador y la comisión del dashboard abre su reporte", () => {
  const p = usaSistemaCompartido("ReportesPlataforma.jsx", ["EncabezadoPagina", "<Indicador", "<Alerta", "onReintentar", "<EstadoVacio",
    "<Esqueleto", "ui-table--tarjetas", 'aria-label="Reporte"', "<tfoot>"]);
  for (const llamada of ["reporteVentasPlataforma(", "reporteProductosPlataforma(", "reporteClientesPlataforma(",
    "reporteServiciosPlataforma(", "reporteComisionPlataforma("]) assert.ok(p.includes(llamada), `falta ${llamada}`);
  assert.ok(leer("../src/pages/DashboardPlataforma.jsx").includes('ir("reportes-plataforma", "comision")'));
  assert.ok(leer("../src/App.jsx").includes("<ReportesPlataforma key={idSeleccionado || \"ventas\"} token={token} filtroInicial={idSeleccionado} />"));
});

test("Reporte de servicios: cifras con Indicador, rankings con BarraProgreso y error con Reintentar sin perder el período", () => {
  const p = usaSistemaCompartido("ReportesServicios.jsx", ["EncabezadoPagina", "<Indicador", "BarraProgreso", "<Alerta", "onReintentar",
    "<EstadoVacio", "<Esqueleto", 'aria-label="Periodo"'], ["adm-", "rp-kpi", "style={{", "admin-base.css"]);
  assert.ok(p.includes("getReportesServicios(token, periodo)"), "el mismo endpoint");
});

test("Mi perfil: sistema compartido, la misma validación de la contraseña con el error en su campo", () => {
  const p = usaSistemaCompartido("PerfilAdmin.jsx", ["EncabezadoPagina", "ChipEstado", "<Indicador", "<Alerta", "onReintentar", "<Esqueleto",
    "<Campo", "CampoClave", "<Dato", "ui-seccion"], ["adm-", "db-kpi", "pf-", "perfil.css\""]);
  assert.ok(p.includes("getPerfil(token)") && p.includes("cambiarPassword(token, passActual, passNueva)"));
  assert.ok(p.includes('"Las contraseñas no coinciden"') && p.includes('"La nueva contraseña debe tener al menos 6 caracteres"'));
  assert.ok(p.includes("error={errores.confirmar}") && p.includes("error={errores.nueva}"));
});

test("ADMIN: Tu resumen y Aprobaciones usan el sistema compartido, con las mismas llamadas", () => {
  const d = usaSistemaCompartido("DashboardOperacion.jsx", ["EncabezadoPagina", "<Indicador", "<Alerta", "onReintentar"], ["adm-", "db-", "dp-"]);
  assert.ok(d.includes("operacionPlataforma(token)") && d.includes("onIr?.(t.ir, t.preset)"));
  const a = usaSistemaCompartido("AprobacionesAdmin.jsx", ["EncabezadoPagina", "ChipEstado", "<LogoNegocio", "<Alerta", "onReintentar",
    "<EstadoVacio", "<Esqueleto", "<Campo", "ui-table--tarjetas", "ConfirmacionAdmin"], ["adm-", "db-kpi"]);
  assert.ok(a.includes("negociosPendientes(token)") && a.includes("!motivo.trim()"));
});
