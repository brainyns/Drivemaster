// ─────────────────────────────────────────────────────────────────────────────
// Datos de prueba del panel de talleres: LISTAR (activo) y CORREGIR/ELIMINAR (comentado)
//
// Los textos de prueba viven en MongoDB (solicitudes, cotizaciones, pedidos, talleres), no en Postgres. Este script es
// para mongosh. Lo que está en Postgres (pagos, repartos y reembolsos de esas solicitudes) va en 2-pagos-ligados.sql,
// con los ids que imprime el paso 1 de aquí.
//
// Cómo usarlo:
//   1. Respaldo (antes de tocar nada). La URI lleva la clave: tómala del .env en una variable de entorno.
//        mongodump --uri="$URI" --collection=ordenes_servicio --out=respaldo-datos-prueba
//        mongodump --uri="$URI" --collection=cotizaciones     --out=respaldo-datos-prueba
//        mongodump --uri="$URI" --collection=pedidos_tienda   --out=respaldo-datos-prueba
//   2. Listado (solo lee):  mongosh "$URI" --file 1-listar-y-corregir.mongodb.js
//   3. Revisa la lista. Descomenta en la sección 2 SOLO las sentencias que quieras, pon los ids y vuelve a correrlo.
//
// "CAMBIOS DE PIEZAs" se busca tal cual (con la "s" minúscula): sin distinguir mayúsculas también saldrían
// cotizaciones reales con "cambios de piezas".
// ─────────────────────────────────────────────────────────────────────────────

const PATRONES = [/Hvffhikbbfsswriknnvcf/i, /\bKaam\b/i, /CAMBIOS DE PIEZAs/];
const MANO_OBRA_PRUEBA = 2000; // mano de obra de $2.000

const enCampos = (campos) => ({ $or: campos.flatMap((c) => PATRONES.map((p) => ({ [c]: p }))) });

// ─── 1. LISTAR (solo lectura) ───────────────────────────────────────────────

print("\n=== Solicitudes (ordenes_servicio) con textos de prueba ===");
const ordenes = db.ordenes_servicio.find(
  enCampos(["sintomas", "observaciones", "vehiculoDescripcion", "motivoCierre", "comentarioCalificacion",
    "diagnostico.hallazgos", "diagnostico.observaciones", "diagnostico.fallas", "tallerNombre", "mecanicoNombre",
    "eventos.nota"]),
  { estado: 1, flujo: 1, tallerId: 1, tallerNombre: 1, usuarioId: 1, vehiculoDescripcion: 1, sintomas: 1,
    "diagnostico.hallazgos": 1, cotizacionId: 1, fechaCreacion: 1 }
).toArray();
ordenes.forEach((o) => printjson(o));

print("\n=== Cotizaciones con textos de prueba o con mano de obra de $2.000 ===");
const cotizaciones = db.cotizaciones.find({
  $or: [
    enCampos(["items.descripcion", "observaciones", "motivoRespuesta", "vehiculoDescripcion", "tallerNombre"]),
    { subtotalManoObra: MANO_OBRA_PRUEBA },
    { items: { $elemMatch: { tipo: "MANO_OBRA", subtotal: MANO_OBRA_PRUEBA } } },
  ],
}, { ordenId: 1, version: 1, estado: 1, tallerId: 1, tallerNombre: 1, "items.tipo": 1, "items.descripcion": 1,
  "items.subtotal": 1, subtotalManoObra: 1, total: 1, fechaEmision: 1 }).toArray();
cotizaciones.forEach((c) => printjson(c));

const ordenIds = [...new Set([...ordenes.map((o) => String(o._id)), ...cotizaciones.map((c) => c.ordenId)])];
const cotizacionIds = [...new Set([...cotizaciones.map((c) => String(c._id)), ...ordenes.map((o) => o.cotizacionId).filter(Boolean)])];

print("\n=== Pedidos a tiendas (pedidos_tienda) de esas solicitudes ===");
db.pedidos_tienda.find({ $or: [{ ordenId: { $in: ordenIds } }, { cotizacionId: { $in: cotizacionIds } }] },
  { ordenId: 1, cotizacionId: 1, tiendaNombre: 1, estado: 1, total: 1 }).forEach((p) => printjson(p));

print("\n=== Talleres, mecánicos y usuarios con textos de prueba ===");
db.talleres.find(enCampos(["nombre", "direccion", "barrio", "mecanicos.nombre"]),
  { nombre: 1, ciudad: 1, estadoAprobacion: 1, "mecanicos.nombre": 1 }).forEach((t) => printjson(t));
db.usuarios.find(enCampos(["nombre"]), { nombre: 1, rol: 1 }).forEach((u) => printjson(u));

print("\n=== Ids para 2-pagos-ligados.sql ===");
print("ordenes:      " + ordenIds.map((id) => `'${id}'`).join(", "));
print("cotizaciones: " + cotizacionIds.map((id) => `'${id}'`).join(", "));
print(`\nTotal: ${ordenes.length} solicitudes, ${cotizaciones.length} cotizaciones. No se modificó nada.`);

// ─── 2. CORREGIR O ELIMINAR (todo comentado: descomenta solo lo que decidas) ─────────────
//
// Recomendación: si la solicitud tiene pagos o repartos (ver 2-pagos-ligados.sql), CORRÍGELA en vez de borrarla, para
// no dejar pagos que apunten a una solicitud que ya no existe. Borra solo lo que no tenga nada ligado.
//
// Reemplaza <ID> por el _id que salió en el listado. Los _id son ObjectId: ObjectId("..."). Cada sentencia toca UN
// documento (updateOne / deleteOne con su _id), nunca un filtro amplio.

// ── 2a. Corregir textos ──
// db.ordenes_servicio.updateOne({ _id: ObjectId("<ID>") }, { $set: { sintomas: "<texto correcto>" } });
// db.ordenes_servicio.updateOne({ _id: ObjectId("<ID>") }, { $set: { "diagnostico.hallazgos": "<texto correcto>" } });
// db.ordenes_servicio.updateOne({ _id: ObjectId("<ID>") }, { $set: { observaciones: null } });
// Línea de una cotización (n = posición del ítem en items, desde 0):
// db.cotizaciones.updateOne({ _id: ObjectId("<ID>") }, { $set: { "items.<n>.descripcion": "Cambio de piezas" } });
// db.cotizaciones.updateOne({ _id: ObjectId("<ID>") }, { $set: { observaciones: null } });
//
// La mano de obra de $2.000 NO se corrige cambiando el precio: los totales, el IVA y el pago ya se calcularon con ese
// valor. Si la cotización sigue EMITIDA, que el taller emita una nueva (la anterior queda REEMPLAZADA); si ya se pagó,
// déjala o elimínala con sus pagos (2b + 2-pagos-ligados.sql).

// ── 2b. Eliminar (solo registros de prueba sin pagos ligados) ──
// Primero los hijos, después la solicitud:
// db.pedidos_tienda.deleteMany({ ordenId: "<ID de la solicitud>" });
// db.cotizaciones.deleteMany({ ordenId: "<ID de la solicitud>" });
// db.ordenes_servicio.deleteOne({ _id: ObjectId("<ID de la solicitud>") });
//
// Una cotización suelta (sin borrar la solicitud). Si era la vigente, la solicitud queda apuntando a ella:
// db.cotizaciones.deleteOne({ _id: ObjectId("<ID>") });
// db.ordenes_servicio.updateOne({ cotizacionId: "<ID>" }, { $unset: { cotizacionId: "" } });
//
// Para deshacer cualquier cambio: mongorestore --uri="$URI" --drop respaldo-datos-prueba
