# Migraciones pendientes

`spring.jpa.hibernate.ddl-auto=update` agrega tablas y columnas nuevas, pero **no cambia el tipo ni el
tamaño de las columnas que ya existen**. Estos cambios quedan para hacerlos a mano (en Supabase) cuando se
decida:

## Montos en `numeric(14,2)` (pendiente)

Hoy los montos se guardan como `double precision` (`Double` en las entidades). Desde la Fase 4-B los cálculos
del reparto y de la liquidación usan `BigDecimal` en pesos enteros, pero las columnas siguen igual.

```sql
ALTER TABLE repartos_pago ALTER COLUMN monto TYPE numeric(14,2);
ALTER TABLE reembolsos    ALTER COLUMN monto TYPE numeric(14,2);
ALTER TABLE pagos         ALTER COLUMN monto TYPE numeric(14,2);
```

Al hacerlo, las entidades `RepartoPago`, `Reembolso` y `PagoEntity` pasan de `Double` a `BigDecimal`.

## Estados de `repartos_pago` (opcional)

La columna `estado` es `varchar(12)`. Por eso el estado "listo para liquidar" se llama `POR_LIQUIDAR`
(12 caracteres) y no `LISTA_PARA_LIQUIDAR`. Si se prefiere el nombre largo:

```sql
ALTER TABLE repartos_pago ALTER COLUMN estado TYPE varchar(24);
```

y cambiar la constante `RepartoPago.POR_LIQUIDAR` y las consultas de `RepartoPagoRepository`.
