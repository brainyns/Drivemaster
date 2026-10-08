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

## Compras y proveedores de las tiendas: asignar los registros sin dueño (parte 7)

Desde la parte 7 cada compra y cada proveedor tiene `tiendaId` y una tienda solo ve los suyos. Los registros
anteriores no tienen dueño, así que **ninguna tienda los ve** hasta que se asignen. La migración no corre sola.

### 1. Respaldo (antes de tocar nada)

Con las herramientas de MongoDB (`mongodump`/`mongorestore`). La URI lleva la clave: tómala del `.env` en una
variable de entorno y no la pegues en chats, tickets ni capturas.

```powershell
# PowerShell
$env:URI = "<SPRING_DATA_MONGODB_URI del .env>"
mongodump --uri="$env:URI" --collection=compras     --out="respaldo-compras-proveedores"
mongodump --uri="$env:URI" --collection=proveedores --out="respaldo-compras-proveedores"
```

```bash
# bash
mongodump --uri="$URI" --collection=compras     --out=respaldo-compras-proveedores
mongodump --uri="$URI" --collection=proveedores --out=respaldo-compras-proveedores
```

Guarda la carpeta `respaldo-compras-proveedores` fuera del repositorio. En Atlas con un clúster de pago también
puedes tomar un snapshot (Backup → Take Snapshot Now). Para deshacer: `mongorestore --uri="$URI" --drop respaldo-compras-proveedores`
(restaura esas dos colecciones tal como estaban).

### 2. Simulación (no escribe nada)

Arranca el backend una vez con estas variables; el log dice cuántos registros se asignarían:

```
MIGRAR_COMPRAS_PROVEEDORES=true
MIGRAR_COMPRAS_PROVEEDORES_TIENDA=<id o nombre exacto de la tienda destino>
MIGRAR_COMPRAS_PROVEEDORES_SIMULAR=true
```

### 3. Ejecución

Igual, quitando `MIGRAR_COMPRAS_PROVEEDORES_SIMULAR` (o poniéndola en `false`). El log reporta cuántas compras y
proveedores asignó y cuántos quedan sin dueño (debe ser 0). Es idempotente: una segunda corrida asigna 0.
Si la tienda no existe o el nombre es ambiguo, el arranque se aborta sin tocar nada.

### 4. Después

Quita las tres variables (la migración no debe quedar activada).
