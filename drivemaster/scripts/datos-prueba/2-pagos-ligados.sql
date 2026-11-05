-- ─────────────────────────────────────────────────────────────────────────────
-- Datos de prueba del panel de talleres: pagos, repartos y reembolsos LIGADOS (Postgres)
--
-- Los textos de prueba están en MongoDB: primero corre 1-listar-y-corregir.mongodb.js, que imprime los ids de las
-- solicitudes y cotizaciones de prueba. Pega esos ids en las listas de abajo (reemplaza '<ID>').
--
-- La sección 1 solo lee. La sección 2 está comentada: descomenta solo lo que decidas, dentro de la transacción, revisa
-- los conteos y termina con COMMIT (o ROLLBACK si algo no cuadra).
--
-- Respaldo antes de cambiar nada (la URL lleva la clave: tómala del .env, no la pegues en chats):
--   pg_dump "$DB_URL" -t pagos -t repartos_pago -t reembolsos -f respaldo-pagos-prueba.sql
-- ─────────────────────────────────────────────────────────────────────────────

-- ─── 1. LISTAR (solo lectura) ───────────────────────────────────────────────

-- Pagos de esas solicitudes o cotizaciones
SELECT id, referencia, concepto, estado, monto, orden_servicio_id, cotizacion_id, fecha_creacion, fecha_pago
FROM pagos
WHERE orden_servicio_id IN ('<ID>')
   OR cotizacion_id     IN ('<ID>')
ORDER BY fecha_creacion;

-- Repartos (lo de talleres, tiendas y la comisión informativa) de esos pagos
SELECT r.id, r.pago_id, r.concepto, r.beneficiario_tipo, r.beneficiario_nombre, r.monto, r.estado,
       r.fecha_creacion, r.fecha_liquidacion
FROM repartos_pago r
WHERE r.orden_servicio_id IN ('<ID>')
   OR r.cotizacion_id     IN ('<ID>')
ORDER BY r.pago_id, r.id;

-- Reembolsos de esas solicitudes
SELECT id, pago_id, monto, estado, motivo, fecha_creacion
FROM reembolsos
WHERE orden_servicio_id IN ('<ID>');

-- Ojo: una línea LIQUIDADO ya se pagó de verdad (o se simuló). Borrarla no devuelve el dinero; solo saca la cifra de
-- los informes. Si hay líneas LIQUIDADO o reembolsos REALIZADO, conviene dejarlas y solo corregir textos en Mongo.

-- ─── 2. ELIMINAR (comentado) ────────────────────────────────────────────────
-- Orden: reembolsos y repartos (dependen del pago) y después los pagos.

-- BEGIN;
--
-- DELETE FROM reembolsos
-- WHERE orden_servicio_id IN ('<ID>');
--
-- DELETE FROM repartos_pago
-- WHERE orden_servicio_id IN ('<ID>')
--    OR cotizacion_id     IN ('<ID>');
--
-- DELETE FROM pagos
-- WHERE orden_servicio_id IN ('<ID>')
--    OR cotizacion_id     IN ('<ID>');
--
-- -- Revisa que los conteos de cada DELETE coincidan con el listado de la sección 1 y después:
-- COMMIT;   -- o ROLLBACK;
