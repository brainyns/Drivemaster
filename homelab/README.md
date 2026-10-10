# DriveMaster en el HomeLab

Guía para correr DriveMaster en una **VM Linux dentro de Proxmox**, con **MongoDB y PostgreSQL locales** y
publicado en Internet con **Cloudflare Tunnel** (HTTPS, sin abrir puertos del router).

En tu Plan Maestro esto corresponde a las fases **5 (Docker)**, **6 (Páginas web)** y **7 (Bases de datos)**.
Necesitas tener la **Fase 2 (Proxmox)** funcionando.

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

| Contenedor | Qué hace | ¿Accesible desde fuera de la VM? |
| --- | --- | --- |
| `frontend` | nginx: entrega el React compilado y reenvía `/api` y `/ws` al backend | Sí, puerto `8088` (solo LAN) |
| `backend` | La API Spring Boot (el mismo `drivemaster/Dockerfile` de siempre) | No |
| `mongo` | MongoDB 7 con usuario y contraseña | No |
| `postgres` | PostgreSQL 16 | No |
| `cloudflared` | Abre una conexión **de salida** hacia Cloudflare y por ahí recibe las visitas | No (no escucha puertos) |

**Por qué así:**

- **Un solo dominio** para el frontend y la API: el navegador no hace peticiones entre dominios, así que no
  hay problemas de CORS ni de cookies.
- **Las bases de datos no publican puertos.** Solo el backend, dentro de la red privada de Docker, puede
  hablar con ellas. Si alguien entra a tu LAN, no ve un MongoDB abierto.
- **Cloudflare Tunnel** sale desde tu casa hacia Cloudflare (como cuando abres una página web). Por eso no
  necesitas abrir puertos en el router ni tener IP pública fija, y tu IP real queda oculta.

**Servicios que siguen en la nube** (no se pueden “instalar” en casa porque son de terceros): Wompi
(pagos), Brevo (correo), Gemini (chat IA), Cloudinary (imágenes) y Google (inicio de sesión).

---

## 2. Crear la VM en Proxmox

Recomendado para empezar:

| Recurso | Valor | Por qué |
| --- | --- | --- |
| Sistema | Debian 12 o 13 (sin entorno gráfico) | Estable y liviano; no gasta RAM en escritorio |
| vCPU | 2 (mejor 4) | Compilar el backend con Maven es lo más pesado |
| RAM | 4 GB (mejor 6 GB) | Backend ≈ 1 GB, Mongo ≈ 0,7 GB, Postgres ≈ 0,2 GB, más el sistema |
| Disco | 32 GB | Imágenes de Docker (~3 GB) + bases de datos + backups |
| **Tipo de CPU** | **`host`** | **Obligatorio, ver advertencia** |

> ⚠️ **Tipo de CPU = `host`.** MongoDB 5 o superior necesita la instrucción **AVX** del procesador.
> El tipo de CPU por defecto de Proxmox (`x86-64-v2-AES`) **no** se la muestra a la VM, y Mongo se cierra
> al arrancar con un error tipo *“Illegal instruction”*. En Proxmox: VM → **Hardware** → **Processors** →
> **Type: host**. (`host` = la VM ve exactamente el mismo procesador que tiene el servidor físico.)

Dale a la VM una **IP fija** (reservándola en el DHCP del router o configurándola en Debian). Así siempre
sabrás dónde está. En esta guía la llamamos `IP-VM`.

---

## 3. Instalar Docker en la VM

Entra a la VM por SSH desde tu PC:

```bash
ssh tu_usuario@IP-VM
```

- `ssh` = programa para abrir una terminal remota cifrada.
- `tu_usuario@IP-VM` = con qué usuario entras y a qué equipo.

Instala Docker con el script oficial:

```bash
sudo apt update && sudo apt install -y curl git
curl -fsSL https://get.docker.com -o instalar-docker.sh
sudo sh instalar-docker.sh
sudo usermod -aG docker $USER
```

- `sudo` = ejecuta el comando como administrador (root).
- `apt update` = actualiza la lista de paquetes disponibles; `apt install -y curl git` instala esas dos
  herramientas (`-y` responde “sí” automáticamente).
- `curl -fsSL URL -o archivo` = descarga el instalador oficial de Docker y lo guarda en un archivo.
  (Si quieres, ábrelo con `less instalar-docker.sh` antes de ejecutarlo: es buena práctica).
- `sudo sh instalar-docker.sh` = lo ejecuta: agrega el repositorio de Docker e instala Docker Engine y
  el plugin `docker compose`.
- `usermod -aG docker $USER` = agrega tu usuario al grupo `docker` para usar Docker sin `sudo`
  (`-a` agregar, `-G` grupo). **Cierra la sesión SSH y vuelve a entrar** para que tome efecto.

> ⚠️ Estar en el grupo `docker` equivale en la práctica a ser root en esa VM. Está bien en una VM
> dedicada a esto; no lo hagas con usuarios en los que no confíes.

Comprueba:

```bash
docker run --rm hello-world
docker compose version
```

---

## 4. Descargar el proyecto

```bash
sudo mkdir -p /opt/drivemaster
sudo chown $USER:$USER /opt/drivemaster
git clone https://github.com/brainyns/Drivemaster.git /opt/drivemaster
cd /opt/drivemaster/homelab
```

- `mkdir -p` crea la carpeta (`-p`: no falla si ya existe). `/opt` es la carpeta típica en Linux para
  aplicaciones instaladas a mano.
- `chown usuario:grupo carpeta` cambia el dueño de la carpeta a tu usuario, para no necesitar `sudo`.
- `git clone URL carpeta` descarga el repositorio. Como es privado, Git te pedirá usuario de GitHub y un
  **token personal** (no tu contraseña), o puedes configurar una clave SSH en GitHub.

---

## 5. Configurar las variables (`.env`)

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

- `cp` copia la plantilla.
- `chmod 600 .env` = solo tu usuario puede leerlo y escribirlo (6 = lectura+escritura para el dueño;
  0 = nada para el grupo; 0 = nada para los demás). Ahí van contraseñas y llaves.
- `nano` es un editor de texto en terminal (guardar: `Ctrl+O`, salir: `Ctrl+X`).

Para cada valor que diga `CAMBIAR_...` genera uno aleatorio con:

```bash
openssl rand -hex 32
```

(genera 32 bytes aleatorios y los muestra en hexadecimal = 64 caracteres).

Lo que **sí o sí** debes llenar:

| Variable | Qué poner |
| --- | --- |
| `PUBLIC_URL` y `FRONTEND_URL` | `https://drivemaster.tudominio.com` (sin `/` al final) |
| `MONGO_PASSWORD`, `POSTGRES_PASSWORD`, `SECURITY_JWT_SECRET`, `RETIRO_CODIGO_SECRET` | Valores de `openssl rand -hex 32` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | **Obligatorios**: si están vacíos el backend no arranca (Spring Security falla al crear el cliente OAuth2) |
| `GEMINI_API_KEY`, `CLOUDINARY_*`, `BREVO_*`, `WOMPI_*` | Tus llaves reales (las mismas que usas en Render) |

> ⚠️ `PUBLIC_URL` se **incrusta en el JavaScript al compilar** el frontend. Si la cambias, hay que
> reconstruir: `docker compose up -d --build frontend`.

> ⚠️ Las contraseñas de Mongo y Postgres **solo se aplican la primera vez** que se crean los volúmenes
> de datos. Si las cambias después, no se actualizan solas.

---

## 6. Primer arranque (solo red local)

```bash
docker compose up -d --build
```

- `up` crea y arranca los contenedores; `-d` (*detached*) los deja corriendo en segundo plano;
  `--build` compila las imágenes del backend y del frontend desde el código.
- La primera vez tarda (descarga Maven, dependencias de Java y de Node). En una VM de 2 vCPU pueden ser
  10–15 minutos.

Revisa el estado y espera la línea `Started DrivemasterApplication`:

```bash
docker compose ps
docker compose logs -f backend
```

(`logs -f` sigue el log en vivo; sal con `Ctrl+C`, los contenedores siguen corriendo.)

Desde tu PC abre `http://IP-VM:8088`. La página debe cargar. **Pero** las llamadas a la API van a
`PUBLIC_URL`, que todavía no existe: el login no funcionará hasta el paso 7. Para comprobar solo la API
desde la LAN:

```bash
curl http://IP-VM:8088/api/productos
```

Debe devolver `[]` (lista vacía) o productos en JSON.

---

## 7. Publicar con Cloudflare Tunnel

**Requisito:** un dominio propio administrado por Cloudflare (sus DNS apuntan a Cloudflare). El plan
gratuito de Cloudflare sirve. Si no tienes dominio, este paso no se puede hacer todavía.

1. En el panel de Cloudflare entra a **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel**
   (los nombres del menú pueden cambiar un poco con el tiempo).
2. Tipo **Cloudflared**, nombre `homelab`.
3. En “Install connector” elige **Docker**. Te muestra un comando con `--token eyJ...`.
   **Copia solo el token** (lo que va después de `--token`) y pégalo en `.env` como
   `CLOUDFLARE_TUNNEL_TOKEN=`. No ejecutes ese comando: el compose ya trae `cloudflared`.
4. En **Public hostname** agrega:
   - Subdominio: `drivemaster` · Dominio: `tudominio.com`
   - Service: **HTTP** · URL: **`frontend:80`**

   `frontend` es el nombre del contenedor: `cloudflared` vive en la misma red de Docker y lo encuentra
   por ese nombre.
5. Arranca el túnel:

   ```bash
   docker compose --profile tunnel up -d
   ```

   `--profile tunnel` activa el servicio `cloudflared`, que está apagado por defecto.

6. Abre `https://drivemaster.tudominio.com`. En el panel de Cloudflare el túnel debe verse **Healthy**.

---

## 8. Ajustar los servicios externos al nuevo dominio

| Servicio | Qué cambiar |
| --- | --- |
| **Google Cloud Console** (OAuth) | En el Client ID: agrega `https://drivemaster.tudominio.com` en *Orígenes de JavaScript autorizados* |
| **Wompi** | URL de eventos (webhook): `https://drivemaster.tudominio.com/api/pagos/webhook`. En `.env`, `WOMPI_REDIRECT_URL=https://drivemaster.tudominio.com/api/pagos/confirmar-redirect` |
| **Brevo** | Nada, si el remitente ya está verificado |

> ⚠️ Empieza con llaves **sandbox** de Wompi (`pub_test_`). Con llaves reales, el backend exige
> `RETIRO_CODIGO_SECRET` y `DISPERSION_MODO=MANUAL`, o no arranca (es una protección del propio código).

Después de cambiar `.env`: `docker compose --profile tunnel up -d` (recrea los contenedores afectados).

---

## 9. Backups automáticos

`backup.sh` hace un `mongodump` y un `pg_dump`, los comprime con fecha y borra los de más de 14 días.
Ya se probó: el respaldo de Mongo se restauró y recuperó los datos.

```bash
./backup.sh                       # guarda en homelab/backups/
./backup.sh /mnt/nas/drivemaster  # o en otra carpeta (p. ej. el NAS o el G52 montado por NFS)
```

Programarlo todos los días a las 3:00 a. m. con `cron`:

```bash
crontab -e
```

y agrega la línea:

```
0 3 * * * /opt/drivemaster/homelab/backup.sh /mnt/nas/drivemaster >> /opt/drivemaster/homelab/backup.log 2>&1
```

- Los 5 campos son: minuto, hora, día del mes, mes, día de la semana. `0 3 * * *` = 3:00 todos los días.
- `>> archivo 2>&1` agrega la salida normal y los errores a un log.

> **Almacenamiento ≠ Backup** (tu propio plan lo dice): guarda los backups **fuera de la VM**. Si los
> dejas en `homelab/backups/`, un fallo del disco de la VM se lleva datos y respaldos a la vez.

Restaurar (ejemplo):

```bash
set -a; source .env; set +a
docker compose exec -T mongo mongorestore -u "$MONGO_USER" -p "$MONGO_PASSWORD" \
  --authenticationDatabase admin --archive --gzip --drop < backups/mongo_FECHA.archive.gz
gunzip -c backups/postgres_FECHA.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```

`--drop` borra cada colección antes de restaurarla: úsalo solo cuando de verdad quieras volver atrás.

---

## 10. Actualizar a una versión nueva del código

```bash
cd /opt/drivemaster
git pull
cd homelab
./backup.sh
docker compose --profile tunnel up -d --build
```

Los datos están en volúmenes de Docker (`mongo-data`, `postgres-data`): sobreviven a reconstrucciones.
**Nunca** uses `docker compose down -v` en producción: la `-v` borra esos volúmenes.

---

## 11. Comandos útiles

| Comando | Para qué |
| --- | --- |
| `docker compose ps` | Ver qué contenedores corren y si están sanos |
| `docker compose logs -f backend` | Ver el log del backend en vivo |
| `docker compose restart backend` | Reiniciar solo el backend |
| `docker stats` | Consumo de CPU y RAM por contenedor |
| `docker compose --profile tunnel down` | Apagar todo (los datos se conservan) |

## 12. Problemas comunes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `mongo` se reinicia sin parar, log con *Illegal instruction* | La VM no tiene AVX | Tipo de CPU `host` en Proxmox (paso 2) |
| Backend: `OAuth2ClientWebMvcSecurityConfiguration ... Constructor threw exception` | `GOOGLE_CLIENT_ID` vacío | Llenarlo en `.env` |
| La web carga pero el login falla con error de red | `PUBLIC_URL` incorrecta o el túnel no está arriba | Revisar `.env`, reconstruir el frontend, ver el estado del túnel |
| Error **502** en el dominio | El backend aún arranca o se cayó | `docker compose logs backend` |
| Notificaciones en tiempo real no llegan | El WebSocket no pasa | Ya está configurado en `nginx.conf`; revisa que el hostname del túnel apunte a `frontend:80` |
| `429 Too Many Requests` al construir | Límite de descargas de Docker Hub | Espera unos minutos y repite |

## 13. Seguridad: lista mínima

- [ ] Sin puertos abiertos en el router (el túnel no los necesita).
- [ ] `.env` con `chmod 600` y nunca en Git.
- [ ] `DEMO_SEED_ENABLED`, `PAGO_SIMULADO_ENABLED` y `PAGO_VERIFICACION_MANUAL_ENABLED` en `false`.
- [ ] SSH a la VM solo con clave (Fase 14 de tu plan).
- [ ] Backups fuera de la VM y una restauración de prueba al mes.
- [ ] Cuando llegues a la Fase 11 (VLANs), esta VM va en la **VLAN 10 – Servidores**.
