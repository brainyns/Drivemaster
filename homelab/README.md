# DriveMaster en el HomeLab (con Portainer)

Guía para correr DriveMaster en una **VM Linux dentro de Proxmox**, administrada con **Portainer**, con
**MongoDB y PostgreSQL locales**, y publicada en Internet con **Cloudflare Tunnel** (HTTPS, sin abrir
puertos del router).

En tu Plan Maestro esto corresponde a las fases **5 (Docker + Portainer)**, **6 (Páginas web)** y
**7 (Bases de datos)**. Necesitas tener la **Fase 2 (Proxmox)** funcionando.

---

## 1. Qué se va a montar

```
Navegador ──HTTPS──► Cloudflare ──túnel cifrado──► cloudflared ─┐
                                                                │   (todo dentro de la VM, en Docker)
                      Tu PC en la LAN ──http://IP-VM:8088──►  frontend (nginx)
                                                                ├── /        → páginas de React
                                                                ├── /api/... → backend (Spring Boot :8080)
                                                                └── /ws/...  → backend (WebSocket)
                                                                          │
                                                          ┌───────────────┴───────────────┐
                                                        mongo :27017                 postgres :5432
                                                   (dominio principal)          (pagos y parámetros)
```

Todo es **un solo stack de Portainer** llamado `drivemaster`, creado desde el repositorio de GitHub.

| Contenedor | Qué hace | ¿Accesible desde fuera de la VM? |
| --- | --- | --- |
| `frontend` | nginx: entrega el React compilado y reenvía `/api` y `/ws` al backend | Sí, puerto `8088` (solo LAN) |
| `backend` | La API Spring Boot (se compila desde `drivemaster/Dockerfile`) | No |
| `mongo` | MongoDB 7 con usuario y contraseña | No |
| `postgres` | PostgreSQL 16 | No |
| `cloudflared` | Abre una conexión **de salida** hacia Cloudflare y por ahí recibe las visitas | No (no escucha puertos) |

**Por qué así:**

- **Un solo dominio** para el frontend y la API: el navegador no hace peticiones entre dominios, así que no
  hay problemas de CORS ni de cookies.
- **Las bases de datos no publican puertos.** Solo el backend, dentro de la red privada de Docker, puede
  hablar con ellas.
- **Cloudflare Tunnel** sale desde tu casa hacia Cloudflare. No necesitas abrir puertos en el router ni IP
  pública fija (funciona con CGNAT), y tu IP real queda oculta.

**Servicios que siguen en la nube** (son de terceros): Wompi (pagos), Brevo (correo), Gemini (chat IA),
Cloudinary (imágenes) y Google (inicio de sesión).

### Qué cambia por usar Portainer

| Tema | Con `docker compose` a mano | Con Portainer (esta guía) |
| --- | --- | --- |
| Código en la VM | `git clone` en `/opt/drivemaster` | Portainer clona el repo por su cuenta (stack de tipo *Repository*) |
| Variables y secretos | Archivo `.env` en la VM | Editor de variables del stack (se guardan en Portainer, no en Git) |
| `nginx.conf` | Se montaba como archivo | Va **dentro de la imagen** del frontend (`homelab/frontend.Dockerfile`), porque Portainer CE no monta archivos del repo con rutas relativas |
| Túnel | Perfil `tunnel` aparte | `cloudflared` es un servicio más del stack |
| Backups | `backup.sh` junto al compose | `backup.sh` copiado a la VM; encuentra los contenedores solo |

---

## 2. Crear la VM en Proxmox

| Recurso | Valor | Por qué |
| --- | --- | --- |
| Sistema | Debian 12 o 13 (sin entorno gráfico) | Estable y liviano |
| vCPU | 2 (mejor 4) | Compilar el backend con Maven es lo más pesado |
| RAM | 4 GB (mejor 6 GB) | Backend ≈ 1 GB, Mongo ≈ 0,7 GB, Postgres ≈ 0,2 GB, Portainer ≈ 0,1 GB, más el sistema |
| Disco | 32–40 GB | Imágenes de Docker (~3 GB) + capas de compilación + bases de datos + backups |
| **Tipo de CPU** | **`host`** | **Obligatorio, ver advertencia** |

> ⚠️ **Tipo de CPU = `host`.** MongoDB 5 o superior necesita la instrucción **AVX** del procesador.
> El tipo por defecto de Proxmox (`x86-64-v2-AES`) **no** se la muestra a la VM, y Mongo se cierra al
> arrancar con *“Illegal instruction”*. En Proxmox: VM → **Hardware** → **Processors** → **Type: host**.
> Si ya tienes una VM con Docker y Portainer, revisa esto en esa VM antes de seguir.

Dale a la VM una **IP fija** (reserva DHCP en el router). En esta guía la llamamos `IP-VM`.

---

## 3. Instalar Docker y Portainer en la VM

> Si ya tienes Portainer corriendo en esta VM, salta al paso 4.
> Si tu Portainer está en **otro** equipo, agrega esta VM como *environment* (Portainer →
> **Environments** → **Add environment** → **Docker Standalone** → **Agent**) y sigue con el paso 4
> eligiendo ese environment.

Entra por SSH:

```bash
ssh tu_usuario@IP-VM
```

Instala Docker con el script oficial:

```bash
sudo apt update && sudo apt install -y curl
curl -fsSL https://get.docker.com -o instalar-docker.sh
sudo sh instalar-docker.sh
sudo usermod -aG docker $USER
exit
```

- `curl -fsSL URL -o archivo` descarga el instalador oficial (puedes leerlo con `less` antes).
- `sudo sh instalar-docker.sh` instala Docker Engine y el plugin `docker compose`.
- `usermod -aG docker $USER` te agrega al grupo `docker` para usarlo sin `sudo`. Por eso el `exit`:
  vuelve a entrar para que tome efecto.

> ⚠️ Estar en el grupo `docker` equivale a ser root en esa VM. Lo mismo vale para Portainer: quien
> entre a Portainer controla la VM. Usa una contraseña fuerte para su usuario admin.

Instala Portainer CE:

```bash
docker volume create portainer_data
docker run -d --name portainer --restart=always \
  -p 9443:9443 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v portainer_data:/data \
  portainer/portainer-ce:lts
```

- `-p 9443:9443` publica el panel web de Portainer (HTTPS) en la VM.
- `-v /var/run/docker.sock:...` le da a Portainer el control de Docker en esta VM.
- `-v portainer_data:/data` guarda la configuración de Portainer (usuarios, stacks y **tus variables**)
  en un volumen.
- `:lts` es la versión de soporte largo: cambia menos y es más estable.

Abre `https://IP-VM:9443` (el navegador avisará del certificado autofirmado; acéptalo) y crea el usuario
administrador **en los primeros 5 minutos**: si tardas más, Portainer se bloquea por seguridad y tienes
que reiniciarlo con `docker restart portainer`.

> 🔒 No publiques el puerto 9443 en el router ni en el túnel de Cloudflare. Portainer solo debe verse
> desde tu LAN (o tu VPN, Fase 10).

---

## 4. Token de GitHub para Portainer

El repositorio es privado: Portainer necesita un token para clonarlo.

1. GitHub → tu foto → **Settings** → **Developer settings** → **Personal access tokens** →
   **Fine-grained tokens** → **Generate new token**.
2. Nombre: `portainer-drivemaster`. Resource owner: `brainyns`. Repository access: **Only select
   repositories** → `Drivemaster`.
3. Permissions → Repository permissions → **Contents: Read-only**. Nada más.
4. Genera el token y cópialo (GitHub lo muestra una sola vez).

Solo lectura: si alguien comprometiera la VM, no podría modificar tu código en GitHub.

---

## 5. Crear el stack en Portainer

En Portainer: tu environment (por ejemplo **local**) → **Stacks** → **Add stack**.

| Campo | Valor |
| --- | --- |
| Name | `drivemaster` (en minúsculas; `backup.sh` busca este nombre) |
| Build method | **Repository** |
| Repository URL | `https://github.com/brainyns/Drivemaster` |
| Repository reference | `refs/heads/NOMBRE-DE-LA-RAMA` (la rama donde subiste la carpeta `homelab/`) |
| Compose path | `homelab/docker-compose.yml` |
| Authentication | Activado · Username: tu usuario de GitHub · Personal Access Token: el del paso 4 |
| GitOps updates | **Desactivado** por ahora (ver paso 10) |

### Variables de entorno

En la sección **Environment variables** pulsa **Advanced mode**, pega el contenido de
[`.env.example`](.env.example) y completa los valores ahí mismo.

Para cada valor que diga `CAMBIAR_...` genera uno aleatorio (en la VM o en tu PC con Git Bash):

```bash
openssl rand -hex 32
```

Lo que **sí o sí** debes llenar:

| Variable | Qué poner |
| --- | --- |
| `PUBLIC_URL` y `FRONTEND_URL` | `https://drivemaster.tudominio.com` (sin `/` al final) |
| `MONGO_PASSWORD`, `POSTGRES_PASSWORD`, `SECURITY_JWT_SECRET`, `RETIRO_CODIGO_SECRET` | Un valor distinto de `openssl rand -hex 32` en cada uno |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | **Obligatorios**: si están vacíos el backend no arranca |
| `GEMINI_API_KEY`, `CLOUDINARY_*`, `BREVO_*`, `WOMPI_*` | Tus llaves reales (las mismas que usas en Render) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Puede quedar vacío hasta el paso 7 |

> ⚠️ `PUBLIC_URL` se **incrusta en el JavaScript al compilar** el frontend. Si la cambias, hay que
> reconstruir la imagen del frontend (paso 10).

> ⚠️ Las contraseñas de Mongo y Postgres **solo se aplican la primera vez**, cuando se crean los
> volúmenes. Si las cambias después en Portainer, las bases siguen con las viejas y el backend no conecta.

> 💾 Guarda una copia de estas variables en tu gestor de contraseñas. Viven en el volumen de Portainer:
> si lo pierdes, las pierdes.

Pulsa **Deploy the stack**.

La primera vez Portainer clona el repo y **compila** el backend y el frontend (Maven + npm): en una VM de
2 vCPU pueden ser 10–15 minutos. Si el navegador muestra un error de tiempo agotado, no lo repitas: la
compilación sigue en segundo plano. Revisa **Images** (aparecerán `drivemaster-backend` y
`drivemaster-frontend`) y luego **Containers**.

---

## 6. Primer arranque (solo red local)

En **Stacks → drivemaster** verás los contenedores:

- `mongo` y `postgres` deben quedar **healthy**.
- `backend`: ábrelo → **Logs** y espera la línea `Started DrivemasterApplication`.
- `frontend`: **running**.
- `cloudflared`: sin token se reinicia en bucle. Es normal hasta el paso 7; no afecta lo demás. Si te
  molesta, selecciónalo y pulsa **Stop**.

Desde tu PC:

```bash
curl http://IP-VM:8088/api/productos
```

Debe devolver `[]` o productos en JSON. Abre también `http://IP-VM:8088`: la página carga, **pero** el
JavaScript llama a `PUBLIC_URL`, que todavía no existe, así que el login no funcionará hasta el paso 7.

---

## 7. Publicar con Cloudflare Tunnel

**Requisito:** un dominio propio administrado por Cloudflare (plan gratuito sirve).

1. Panel de Cloudflare → **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel**.
2. Tipo **Cloudflared**, nombre `homelab`.
3. En “Install connector” elige **Docker**. Copia **solo el token** (lo que va después de `--token`).
   No ejecutes ese comando: el stack ya trae `cloudflared`.
4. En **Public hostname**: Subdominio `drivemaster` · Dominio `tudominio.com` · Service **HTTP** ·
   URL **`frontend:80`**.
   (`frontend` es el nombre del servicio: `cloudflared` está en la misma red del stack y lo encuentra así.)
5. En Portainer: **Stacks → drivemaster → Editor**, pon el token en `CLOUDFLARE_TUNNEL_TOKEN` y pulsa
   **Update the stack** (o **Pull and redeploy**, según tu versión).
6. El túnel debe verse **Healthy** en Cloudflare y el log de `cloudflared` mostrar
   `Registered tunnel connection`. Abre `https://drivemaster.tudominio.com`.

> 🔒 Quien tenga el token puede conectar su propio servidor a tu túnel. No lo pongas en Git ni en capturas.

---

## 8. Ajustar los servicios externos al nuevo dominio

| Servicio | Qué cambiar |
| --- | --- |
| **Google Cloud Console** (OAuth) | En el Client ID: agrega `https://drivemaster.tudominio.com` en *Orígenes de JavaScript autorizados* |
| **Wompi** | URL de eventos: `https://drivemaster.tudominio.com/api/pagos/webhook`. En las variables del stack: `WOMPI_REDIRECT_URL=https://drivemaster.tudominio.com/api/pagos/confirmar-redirect` |
| **Brevo** | Nada, si el remitente ya está verificado |

> ⚠️ Empieza con llaves **sandbox** de Wompi (`pub_test_`). Con llaves reales, el backend exige
> `RETIRO_CODIGO_SECRET` y `DISPERSION_MODO=MANUAL`, o no arranca (protección del propio código).

Después de cambiar variables: **Update the stack**. Solo se recrean los contenedores afectados.

---

## 9. Backups automáticos

Portainer no hace backups de las bases de datos. Para eso está `backup.sh`: hace un `mongodump` y un
`pg_dump`, los comprime con fecha y borra los de más de 14 días. No necesita `.env`: encuentra los
contenedores del stack `drivemaster` y usa las credenciales que ya tienen dentro.

### 9.1 Copiar el script a la VM

Desde tu PC, en la carpeta del proyecto (PowerShell o Git Bash):

```bash
ssh -t tu_usuario@IP-VM 'sudo mkdir -p /opt/drivemaster && sudo chown $USER: /opt/drivemaster'
scp homelab/backup.sh tu_usuario@IP-VM:/opt/drivemaster/backup.sh
```

En la VM:

```bash
chmod +x /opt/drivemaster/backup.sh
/opt/drivemaster/backup.sh
```

Debe crear `mongo_FECHA.archive.gz` y `postgres_FECHA.sql.gz` en `/opt/drivemaster/backups`.

> Si al ejecutarlo ves `bad interpreter` o `$'\r': command not found`, el archivo quedó con saltos de
> línea de Windows. Arréglalo con `sed -i 's/\r$//' /opt/drivemaster/backup.sh`.

### 9.2 Programarlo con cron

```bash
crontab -e
```

y agrega (en una sola línea):

```
0 3 * * * /opt/drivemaster/backup.sh /mnt/nas/drivemaster >> /opt/drivemaster/backup.log 2>&1
```

- Los 5 campos son: minuto, hora, día del mes, mes, día de la semana. `0 3 * * *` = 3:00 todos los días.
- `>> archivo 2>&1` agrega la salida normal y los errores a un log.

> **Almacenamiento ≠ Backup**: guarda los backups **fuera de la VM** (NAS, G52 por NFS, disco externo),
> montado en la VM, por ejemplo en `/mnt/nas`.

Además, en Proxmox programa un backup de la VM completa (Datacenter → **Backup**): así también
recuperas Portainer con todas tus variables.

### 9.3 Restaurar (practícalo una vez al mes)

En la VM:

```bash
MONGO=$(docker ps -q -f label=com.docker.compose.project=drivemaster -f label=com.docker.compose.service=mongo)
POSTGRES=$(docker ps -q -f label=com.docker.compose.project=drivemaster -f label=com.docker.compose.service=postgres)

docker exec -i "$MONGO" sh -c 'mongorestore -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin --archive --gzip --drop' < /opt/drivemaster/backups/mongo_FECHA.archive.gz

gunzip -c /opt/drivemaster/backups/postgres_FECHA.sql.gz \
  | docker exec -i "$POSTGRES" sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

- `docker ps -q -f label=...` busca el id del contenedor por el nombre del stack y del servicio.
- `docker exec -i` ejecuta el comando dentro del contenedor y le pasa el archivo por la entrada (`<`).
- Las comillas simples hacen que `$MONGO_INITDB_ROOT_USERNAME`, etc. se lean **dentro** del contenedor.
- `--drop` borra cada colección antes de restaurarla: úsalo solo cuando de verdad quieras volver atrás.

---

## 10. Actualizar a una versión nueva del código

1. Sube los cambios a la rama en GitHub.
2. En la VM: `/opt/drivemaster/backup.sh` (respaldo antes de tocar nada).
3. En Portainer: **Images** → selecciona `drivemaster-backend:latest` y `drivemaster-frontend:latest` →
   **Remove** (con *Force*). Los contenedores siguen corriendo con la versión vieja mientras tanto.
4. **Stacks → drivemaster → Pull and redeploy**. Portainer descarga el código nuevo y, como las imágenes
   ya no existen, las vuelve a compilar.

¿Por qué el paso 3? Las imágenes se compilan en la VM, y al redesplegar Portainer puede reutilizar las
que ya existen en vez de compilar de nuevo. Borrarlas obliga a compilar con el código nuevo. Si tu versión
de Portainer ya recompila sola, puedes saltarte ese paso.

Los datos están en los volúmenes `drivemaster_mongo-data` y `drivemaster_postgres-data`: sobreviven a
redespliegues. **Nunca** borres esos volúmenes (Portainer → **Volumes**) ni elimines el stack marcando
la opción de borrar volúmenes.

> **GitOps updates** (redesplegar solo al detectar cambios en Git) existe en Portainer, pero por lo
> anterior no siempre recompila, y además se saltaría el backup previo. Déjalo apagado hasta tener esto
> más rodado.

---

## 11. Dónde está cada cosa en Portainer

| Quieres… | En Portainer |
| --- | --- |
| Ver si todo corre y está sano | **Stacks → drivemaster** |
| Ver el log del backend | **Containers → drivemaster-backend-1 → Logs** |
| Reiniciar solo el backend | **Containers → drivemaster-backend-1 → Restart** |
| CPU y RAM por contenedor | **Containers → (contenedor) → Stats** |
| Cambiar una variable | **Stacks → drivemaster → Editor → Update the stack** |
| Abrir una terminal dentro de un contenedor | **Containers → (contenedor) → Console** |
| Liberar disco | **Images** → borrar las que digan *Unused* |

## 12. Problemas comunes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `mongo` se reinicia sin parar, log con *Illegal instruction* | La VM no tiene AVX | Tipo de CPU `host` en Proxmox (paso 2) y apagar/encender la VM |
| Backend: `OAuth2ClientWebMvcSecurityConfiguration ... Constructor threw exception` | `GOOGLE_CLIENT_ID` vacío | Llenarlo en las variables del stack |
| Backend: *Authentication failed* con Mongo o Postgres | Cambiaste las contraseñas después de crear los volúmenes | Vuelve a poner las originales en las variables |
| Al desplegar: *authentication required* / *repository not found* | Token de GitHub mal copiado, vencido o sin acceso al repo | Revisa el token (paso 4) |
| Al desplegar: *stat homelab/docker-compose.yml: no such file* | Rama o *Compose path* incorrectos | Revisa `Repository reference` y `Compose path` (paso 5) |
| La web carga pero el login falla con error de red | `PUBLIC_URL` incorrecta o el túnel no está arriba | Corrige la variable y recompila el frontend (paso 10) |
| Cambié el código pero sigo viendo la versión vieja | Portainer reutilizó la imagen | Borra la imagen y redespliega (paso 10) |
| Error **502** / **1033** en el dominio | El backend aún arranca, se cayó, o `cloudflared` no corre | Logs de `backend` y `cloudflared` |
| Notificaciones en tiempo real no llegan | El WebSocket no pasa | Revisa que el hostname del túnel apunte a `frontend:80` |
| `429 Too Many Requests` al compilar | Límite de descargas de Docker Hub | Espera unos minutos y redespliega |
| La VM se congela al compilar | Falta RAM | Sube la RAM de la VM |

## 13. Seguridad: lista mínima

- [ ] Sin puertos abiertos en el router (el túnel no los necesita).
- [ ] Portainer (9443) solo en la LAN, con contraseña fuerte. Nunca detrás del túnel.
- [ ] Variables solo en Portainer (y en tu gestor de contraseñas), nunca en Git.
- [ ] `DEMO_SEED_ENABLED`, `PAGO_SIMULADO_ENABLED` y `PAGO_VERIFICACION_MANUAL_ENABLED` en `false`.
- [ ] Token de GitHub de solo lectura.
- [ ] SSH a la VM solo con clave (Fase 14 de tu plan).
- [ ] Backups fuera de la VM y una restauración de prueba al mes.
- [ ] Cuando llegues a la Fase 11 (VLANs), esta VM va en la **VLAN 10 – Servidores**.

---

## Anexo: sin Portainer

El mismo compose funciona desde la terminal: clona el repo en la VM, y en `homelab/`:

```bash
cp .env.example .env && chmod 600 .env && nano .env
docker compose --env-file .env up -d --build
```
