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

## Limpieza de la antigua "Tienda DriveMaster" (parte 8)

DriveMaster es la aplicación, no una tienda. Esta migración borra la antigua tienda propia (la marcada con
`plataforma: true`), **sus productos** y **sus movimientos de inventario**. **No toca ventas, solicitudes,
cotizaciones ni compras**: se conservan como historial (cada línea guarda su propia copia del nombre, la imagen
y el precio, así que se siguen viendo aunque el producto ya no exista). No corre sola.

### Orden

1. **Desplegar primero el código de la parte 8-1.** Antes de ese código, cada arranque recreaba la tienda y le
   asignaba los productos sin tienda; con el código nuevo ya no.
2. **Respaldo** (abajo).
3. **Informe previo (simulación, no borra nada):** arranca el backend una vez con
   ```
   MIGRAR_LIMPIEZA_TIENDA_PROPIA=true
   MIGRAR_LIMPIEZA_TIENDA_PROPIA_SIMULAR=true
   ```
   El log trae: productos y movimientos que se borrarían, cuántas ventas, solicitudes y cotizaciones tienen productos
   de esa tienda (y cuántas están sin cerrar), y qué impediría ejecutarla.
4. **Resolver lo que impida ejecutarla**, si aparece: ventas o solicitudes sin cerrar con esos productos (apruébalas,
   recházalas, anúlalas o espera a que se paguen) y cotizaciones de taller sin resolver. Si hay pedidos, compras del
   catálogo, compras a proveedores o proveedores atados a esa tienda, la limpieza se niega a borrarla.
5. **Ejecución:** igual que la simulación pero sin `MIGRAR_LIMPIEZA_TIENDA_PROPIA_SIMULAR`. Si algo no cuadra, aborta
   **sin tocar nada**. Reporta cuántos productos, movimientos y tiendas borró. Es idempotente: una segunda corrida
   no encuentra tienda propia y no hace nada.
6. **Quitar las variables** (la limpieza no debe quedar activada).

### Respaldo antes de ejecutar

La URI de Mongo lleva la clave: tómala del `.env` en una variable de entorno y no la pegues en chats ni capturas.

```powershell
# PowerShell
$env:URI = "<SPRING_DATA_MONGODB_URI del .env>"
foreach ($c in "tiendas","productos","movimientos_inventario","ventas","solicitudes","cotizaciones") {
  mongodump --uri="$env:URI" --collection=$c --out="respaldo-limpieza-tienda-propia"
}
```

```bash
# bash
for c in tiendas productos movimientos_inventario ventas solicitudes cotizaciones; do
  mongodump --uri="$URI" --collection=$c --out=respaldo-limpieza-tienda-propia
done
```

Las tres primeras son las que se modifican; ventas, solicitudes y cotizaciones se respaldan por prudencia (la
limpieza no las toca). Guarda la carpeta fuera del repositorio. En Atlas de pago también puedes tomar un snapshot.

Para deshacer, justo después de ejecutarla y antes de que entren datos nuevos:
`mongorestore --uri="$URI" --nsInclude="<base>.tiendas" --nsInclude="<base>.productos" --nsInclude="<base>.movimientos_inventario" --drop respaldo-limpieza-tienda-propia`
(vuelve a poner esas tres colecciones como estaban).

### Lo que no hace

- No borra las imágenes de los productos en Cloudinary (quedan en la nube; se pueden limpiar a mano).
- No toca los productos que no tienen tienda (el informe dice cuántos son); si existen, no aparecen en ningún catálogo.

## Horario de atención estructurado (`horarios_taller`)

El proyecto no usa Flyway ni Liquibase y los talleres viven en MongoDB: la colección `horarios_taller` se crea sola
con el primer `PUT /api/talleres/{id}/horario`. No hay datos que mover a mano.

- `talleres.horario` (texto libre) **queda deprecado pero no se borra**. Al guardar el horario estructurado se
  reescribe con un resumen generado ("Lun a Vie 8:00 a. m.–6:00 p. m.; …"), porque el mapa y la lista que ve el
  cliente siguen leyéndolo. Los talleres que aún no configuran el horario conservan su texto tal cual.
- El texto libre no se convierte automáticamente en franjas: cada taller lo carga en Mi taller › Horario de atención.
- Mientras un taller no tenga horario estructurado, la cita de revisión no se restringe por horario.
- Pendiente (TODO en `Taller.java`): cuando el cliente lea `horarios_taller` y todos los talleres activos tengan su
  horario, retirar el campo:

```js
db.talleres.updateMany({}, { $unset: { horario: "" } })
```
