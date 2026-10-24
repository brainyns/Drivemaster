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

## Índices de MongoDB e índice único de `usuarios.correo`

Los crea `config/IndicesMongo.java` en cada arranque (`ensureIndex` no hace nada si ya existen). La creación
automática de Spring Data (`spring.data.mongodb.auto-index-creation`) sigue apagada a propósito: con ella, un índice
que no se puede crear (por ejemplo, el único con correos repetidos) detiene el arranque; aquí solo deja un aviso.

- `ordenes_servicio`: `taller_flujo_fecha`, `usuario_flujo_fecha`, `taller_cita`, `vehiculo_estado`, `flujo_estado`.
- `horarios_taller` y `cambios_taller`: `taller`.
- `usuarios`: `correo_unico` (único y parcial: solo los documentos con `correo` de tipo texto).

**Si el log dice "No se creó el índice único de usuarios.correo: hay N correos repetidos"**, el índice no existe hasta
resolverlos. Los correos salen enmascarados en el log; para verlos completos:

```js
db.usuarios.aggregate([
  { $match: { correo: { $type: "string" } } },
  { $group: { _id: "$correo", n: { $sum: 1 }, ids: { $push: "$_id" }, roles: { $push: "$rol" } } },
  { $match: { n: { $gt: 1 } } }
])
```

Por cada correo, decide cuál cuenta se queda (la que tiene órdenes, vehículos o negocio asociado) y cambia el correo
de las otras o bórralas (con respaldo previo, como en la sección de compras). Al reiniciar, el índice se crea solo.

El índice distingue mayúsculas. Si el log avisa de correos que "solo se diferencian en mayúsculas", son cuentas
distintas para el sistema: hay que unificarlas a mano antes de la migración de la sección siguiente.

## Correos en minúsculas (`usuarios.correo`, aplicada el 24 de octubre de 2026)

Desde el cambio de la tarjeta Contacto, el registro (cliente, taller, tienda, alta por el SUPERADMIN y Google), el
inicio de sesión y el cambio de correo guardan y buscan el correo **sin espacios y en minúsculas**
(`util/Correos.java`). Los correos que ya estaban guardados **no se modificaron**. El inicio de sesión los sigue
encontrando: `UsuarioServiceImpl.buscarPorCorreo` busca primero el valor exacto y, si no lo encuentra, compara sin
mayúsculas. Si hay dos cuentas que solo se diferencian en mayúsculas, no elige ninguna.

**Aplicada el 24 de octubre de 2026** con los pasos de abajo: había 1 de 43 usuarios con mayúsculas (rol TALLER), sin
choques; `updateMany` modificó 1 usuario y 0 tiendas, y la verificación quedó en 0 en `usuarios.correo` y en
`tiendas.correoContacto`. El respaldo previo (`usuarios` y `tiendas`) quedó fuera del repositorio, en
`Downloads/respaldo-correos-2026-10-24`. Si vuelve a aparecer alguno, al arrancar `IndicesMongo` escribe en el log
cuántos hay ("Hay N usuarios con el correo guardado con mayúsculas o espacios") y estos pasos sirven igual.

### Cómo migrarlos

1. **Respaldo** de `usuarios` y `tiendas`, como en las secciones anteriores
   (`mongodump --uri="$URI" --collection=usuarios --out=respaldo-correos`, y lo mismo para `tiendas`).
2. **Revisar choques** (debe devolver vacío; si no, unificar esas cuentas primero):
   ```js
   db.usuarios.aggregate([
     { $match: { correo: { $type: "string" } } },
     { $group: { _id: { $toLower: { $trim: { input: "$correo" } } }, n: { $sum: 1 }, ids: { $push: "$_id" } } },
     { $match: { n: { $gt: 1 } } }
   ])
   ```
3. **Ver cuáles cambian** (solo lectura):
   ```js
   db.usuarios.find({ correo: { $regex: /[A-Z]|^\s|\s$/ } }, { correo: 1, rol: 1 })
   ```
4. **Migrar** (usuarios y el correo de contacto de las tiendas):
   ```js
   db.usuarios.updateMany({ correo: { $regex: /[A-Z]|^\s|\s$/ } },
     [{ $set: { correo: { $toLower: { $trim: { input: "$correo" } } } } }])
   db.tiendas.updateMany({ correoContacto: { $regex: /[A-Z]|^\s|\s$/ } },
     [{ $set: { correoContacto: { $toLower: { $trim: { input: "$correoContacto" } } } } }])
   ```
5. **Después:** quien tenga la sesión abierta con esas cuentas tendrá que volver a iniciar sesión, porque el token
   lleva el correo anterior. Para iniciar sesión puede escribir el correo con o sin mayúsculas.

Cuando el log ya no avise de correos con mayúsculas, se puede quitar la búsqueda sin mayúsculas de
`buscarPorCorreo`, que es una expresión regular y no aprovecha el índice.

## Cambios de correo pendientes (`cambios_correo`)

La colección se crea sola. Guarda solo el hash del código (BCrypt), y el índice TTL `vence_ttl` borra los cambios que
vencieron sin confirmarse. No hay nada que migrar.
