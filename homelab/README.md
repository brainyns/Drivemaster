# DriveMaster en el HomeLab (con Portainer)

Guía para correr DriveMaster en una **VM Linux dentro de Proxmox**, administrada con **Portainer**, usando
las bases de datos en la nube (**MongoDB Atlas** y **Supabase**) y publicada en Internet con HTTPS y sin
abrir puertos del router: hoy con **Tailscale Funnel** y, cuando haya dominio propio, con
**Cloudflare Tunnel**.

El HomeLab **reemplaza al backend de Render**: usa las mismas bases de datos, así que Render debe quedar
suspendido (ver paso 6).

---

## 1. Qué se va a montar

```
Navegador ──HTTPS──► Tailscale Funnel ─────────────┐   (hoy: https://homelab.tailaeb1ae.ts.net)
          ──HTTPS──► Cloudflare ──► cloudflared ───┤   (más adelante, con dominio propio)
                                                   ▼
           Tu PC en la LAN ──http://IP-VM:8088──► frontend (nginx)           ┐
                                                   ├── /        → React       │ dentro de la VM,
                                                   ├── /api/... → backend     │ en Docker
                                                   └── /ws/...  → backend     ┘
                                                                     │
                                                   ┌─────────────────┴─────────────────┐
                                             MongoDB Atlas                      Supabase (PostgreSQL)
                                          (dominio principal)                  (pagos y parámetros)
```

Todo es **un solo stack de Portainer** llamado `drivemaster`, creado desde la rama `homelab` de GitHub.

| Contenedor | Qué hace | ¿Accesible desde fuera de la VM? |
| --- | --- | --- |
| `frontend` | nginx: entrega el React compilado y reenvía `/api` y `/ws` al backend | Puerto `8088` (LAN y Funnel) |
| `backend` | La API Spring Boot (se compila desde `drivemaster/Dockerfile`). El chat IA solo lo reenvía a n8n | No |
| `n8n` | Agente del chat IA (modelo vía OpenRouter, memoria y herramientas). Editor en `localhost:5678` de la VM (túnel SSH) | No (no publica puertos fuera de localhost) |
| `chat-memory` | PostgreSQL local con la memoria del chat (solo lo usa n8n). Así no gasta conexiones de Supabase | No (sin puertos) |
| `cloudflared` | Túnel de Cloudflare. Detenido hasta tener dominio | No (no escucha puertos) |

**Servicios en la nube**: MongoDB Atlas, Supabase, Wompi (pagos), Brevo (correo), Cloudinary (imágenes) y
Google (inicio de sesión). El chat IA usa OpenRouter con la API key guardada como **credencial dentro
de n8n** (no en las variables del stack).

### Qué cambia por usar Portainer

| Tema | Con `docker compose` a mano | Con Portainer (esta guía) |
| --- | --- | --- |
| Código en la VM | `git clone` en `/opt/drivemaster` | Portainer clona el repo por su cuenta (stack de tipo *Repository*) |
| Variables y secretos | Archivo `.env` en la VM | Editor de variables del stack (se guardan en Portainer, no en Git) |
| `nginx.conf` | Se montaba como archivo | Va **dentro de la imagen** del frontend (`homelab/frontend.Dockerfile`), porque Portainer CE no monta archivos del repo con rutas relativas |
| Recompilar al actualizar | `--build` | `pull_policy: build` en el compose |

---

## 2. La VM

| Recurso | Valor | Por qué |
| --- | --- | --- |
| Sistema | Ubuntu 24.04 / Debian 12+ (sin entorno gráfico) | Estable y liviano |
| vCPU | 2 (mejor 4) | Compilar el backend con Maven es lo más pesado |
| RAM | 4 GB | Backend ≈ 1 GB (limitado en el compose), frontend y Portainer ≈ 0,1 GB cada uno, más el sistema y la compilación |
| Disco | 32–40 GB | Imágenes de Docker + capas de compilación + backups |
| Tipo de CPU | `host` | Recomendado: cualquier MongoDB 5+ que corras en la VM necesita AVX |

> Ubuntu suele instalarse usando solo la mitad del disco. Si `df -h /` muestra mucho menos que el disco
> virtual: `sudo lvextend -r -l +100%FREE /dev/ubuntu-vg/ubuntu-lv`.

Dale a la VM una **IP fija** (reserva DHCP en el router). En esta guía la llamamos `IP-VM`.

---

## 3. Docker y Portainer

> Si ya tienes Portainer corriendo en esta VM, salta al paso 4.

```bash
sudo apt update && sudo apt install -y curl
curl -fsSL https://get.docker.com -o instalar-docker.sh
sudo sh instalar-docker.sh
sudo usermod -aG docker $USER
exit
```

Vuelve a entrar por SSH e instala Portainer CE:

```bash
docker volume create portainer_data
docker run -d --name portainer --restart=always \
  -p 9443:9443 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v portainer_data:/data \
  portainer/portainer-ce:lts
```

Abre `https://IP-VM:9443` y crea el usuario administrador **en los primeros 5 minutos**.

> 🔒 Estar en el grupo `docker`, o entrar a Portainer, equivale a ser root en la VM. Portainer (9443)
> solo debe verse desde tu LAN o tu VPN: nunca lo publiques con Funnel ni con el túnel.

---

## 4. Token de GitHub para Portainer

El repositorio es privado y pertenece a la cuenta personal `brainyns`. Los tokens *fine-grained* solo
sirven para repos de tu propia cuenta o de organizaciones, así que se usa un token **classic**:

1. https://github.com/settings/tokens/new
2. Note: `portainer-drivemaster` · Expiration: **90 days** · Scopes: solo **`repo`**.
3. Genera el token (`ghp_...`) y guárdalo en tu gestor de contraseñas.

> Ese token puede leer y escribir en todos tus repos: solo va en Portainer. Cuando venza, crea otro y
> cámbialo en Portainer (**Stacks → drivemaster → Git credentials** o en **Settings → Credentials**).
> Si el dueño de `brainyns` crea un token *fine-grained* de solo lectura para `Drivemaster`, es más seguro.

---

## 5. Crear el stack en Portainer

**local** → **Stacks** → **Add stack**:

| Campo | Valor |
| --- | --- |
| Name | `drivemaster` (minúsculas; `backup.sh` busca este nombre) |
| Build method | **Repository** |
| Repository URL | `https://github.com/brainyns/Drivemaster` |
| Repository reference | `refs/heads/homelab` |
| Compose path | `homelab/docker-compose.yml` |
| Authentication | Tu usuario de GitHub + el token del paso 4 |
| GitOps updates / Webhook | Apagados |

### Variables de entorno

Pulsa **Advanced mode** (*Cambie al modo avanzado*), pega [`.env.example`](.env.example) **sin las
líneas de comentario** y complétalo:

| Variable | Qué poner |
| --- | --- |
| `PUBLIC_URL`, `FRONTEND_URL` | La dirección pública, sin `/` al final. Hoy: `https://homelab.tailaeb1ae.ts.net` |
| `WOMPI_REDIRECT_URL` | `PUBLIC_URL` + `/api/pagos/confirmar-redirect` |
| `SPRING_DATA_MONGODB_URI` | La de Render (Atlas) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Las de Render (Supabase) |
| `SECURITY_JWT_SECRET`, `RETIRO_CODIGO_SECRET` | **Las mismas de Render**: así las sesiones y los códigos de retiro ya emitidos siguen siendo válidos |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | **Obligatorios** (vacíos = el backend no arranca) |
| `N8N_CHAT_SECRET` / `N8N_TOOLS_SECRET` | Secretos compartidos backend ↔ n8n (genera dos con `openssl rand -hex 32`). **Obligatorios**: sin ellos el stack no se despliega. La API key de OpenRouter va como credencial dentro de n8n, no aquí |
| `CHAT_MEMORY_DB_PASSWORD` | Contraseña del PostgreSQL de la memoria del chat (genera otra con `openssl rand -hex 32`). **Obligatoria**. La misma va en la credencial Postgres de n8n |
| `N8N_VERSION` | La versión de n8n que ya corre (n8n → Settings → About). Vacía = `latest`. Nunca una anterior a la actual |
| `OPENROUTER_MODEL`, `N8N_CHAT_RATE_MAX`, `N8N_CHAT_RATE_DIA_IP`, `N8N_CHAT_RATE_DIA_GLOBAL` | Opcionales: modelo de OpenRouter (`anthropic/claude-haiku-5.5`) y topes del chat: por minuto por IP (`6`), por día por IP (`60`) y por día en total (`400`, acota el gasto diario) |
| `CLOUDINARY_*`, `BREVO_*`, `WOMPI_*` | Las de Render |
| `CLOUDFLARE_TUNNEL_TOKEN` | Vacío hasta tener dominio |

> ⚠️ Pega **valores**, no comandos: algo como `$(openssl rand -hex 32)` Portainer lo toma como texto y
> bloquea el botón de desplegar. Genera los valores en la terminal y pega el resultado.

> ⚠️ `PUBLIC_URL` se **incrusta en el JavaScript al compilar** el frontend. Al cambiarla, el frontend se
> recompila solo al pulsar **Update the stack** (por `pull_policy: build`).

> 💾 Guarda una copia de estas variables en tu gestor de contraseñas. Viven en el volumen de Portainer.

Pulsa **Deploy the stack**. La primera compilación tarda 10–20 minutos; si el navegador da *timeout*, no
lo repitas.

### 5.1 Configurar n8n (chat IA)

1. Desde tu PC: `ssh -L 5678:localhost:5678 tu_usuario@IP-VM` y abre `http://localhost:5678`
   (el editor de n8n **no** debe quedar accesible desde fuera de la VM).
2. Crea el usuario del editor. Si ya habías importado una versión anterior del workflow,
   desactívala y bórrala. Luego importa `n8n/workflows/chat-drivemaster.json`
   (**Workflows → Import from File**).
3. Crea las credenciales: **OpenRouter** con la API key de OpenRouter (con límite de crédito) y, para la memoria,
   **Postgres** con el contenedor local de memoria: Host `chat-memory`, Database `chat_memory`,
   User `n8n`, Password = `CHAT_MEMORY_DB_PASSWORD`, Port `5432`, SSL **disable**. Asígnalas a los nodos
   `Modelo OpenRouter` y `Memoria (Postgres)`. El webhook no necesita credencial: el workflow valida
   `X-Chat-Secret` contra `N8N_CHAT_SECRET`.
4. Activa el workflow. Prueba desde la VM. El backend no publica el puerto 8080, así que entra por
   nginx en el 8088:

   ```bash
   curl -X POST http://localhost:8088/api/chat -H "Content-Type: application/json" \
     -d '{"mensaje":"Hola, me llamo Ana","sessionId":"prueba-1"}'
   curl -X POST http://localhost:8088/api/chat -H "Content-Type: application/json" \
     -d '{"mensaje":"¿Cómo me llamo?","sessionId":"prueba-1"}'
   ```

   La segunda respuesta debe decir "Ana".
5. Anota en tu gestor de contraseñas la clave de cifrado de n8n (`docker exec drivemaster-n8n-1 cat
   /home/node/.n8n/config`). Sin ella, un backup del volumen no puede descifrar las credenciales.

Detalles, pruebas y cómo añadir herramientas: [`n8n/README.md`](../n8n/README.md).

---

## 6. Atlas, Supabase y Render

1. **Atlas → Network Access**: la IP pública de tu casa debe estar permitida. Si Render ya funcionaba,
   probablemente ya está `0.0.0.0/0` (cualquier IP). Si no, agrega tu IP pública (`curl ifconfig.me` en
   la VM); ojo: si tu proveedor la cambia, Atlas te bloqueará.
2. **Supabase**: si `DB_URL` apunta a `db.xxxxx.supabase.co`, esa conexión es **solo IPv6** y es
   probable que tu casa no la tenga. Usa la del **Session pooler** (`...pooler.supabase.com`), en
   Supabase → **Connect**.
3. **Render**: cuando el HomeLab funcione, **suspende el backend de Render** (Settings → *Suspend Web
   Service*). Si los dos corren contra las mismas bases, las tareas programadas
   (`ReasignacionScheduler`, `DespachoAtrasadoScheduler`, `RecepcionEnvioScheduler`,
   `MantenimientoScheduler`) se ejecutan dos veces.

Comprueba el arranque: **Containers → drivemaster-backend-1 → Logs** → `Started DrivemasterApplication`.

---

## 7. Publicar en Internet

### Hoy: Tailscale Funnel

```bash
sudo tailscale funnel --bg 8088
```

Publica **solo** el puerto 8088 con HTTPS en `https://<máquina>.<tailnet>.ts.net`. La primera vez pide
activar HTTPS y Funnel en la cuenta de Tailscale. Para quitarlo: `sudo tailscale funnel --https=443 off`.

### Más adelante: dominio propio con Cloudflare Tunnel

1. Cloudflare → **Zero Trust** → **Networks** → **Tunnels** → **Create a tunnel** (tipo Cloudflared).
2. Copia **solo el token** del comando de Docker y ponlo en `CLOUDFLARE_TUNNEL_TOKEN`.
3. **Public hostname**: `drivemaster.tudominio.com` → Service **HTTP** → URL **`frontend:80`**.
4. Cambia `PUBLIC_URL`, `FRONTEND_URL` y `WOMPI_REDIRECT_URL` al dominio nuevo → **Update the stack**.
5. Inicia el contenedor `cloudflared` en Portainer y apaga Funnel.

> 🔒 Quien tenga el token del túnel puede suplantar tu sitio. No lo pongas en Git ni en capturas.

---

## 8. Ajustar los servicios externos a la dirección pública

| Servicio | Qué cambiar |
| --- | --- |
| **Google Cloud Console** (OAuth) | Agrega `PUBLIC_URL` en *Orígenes de JavaScript autorizados* |
| **Wompi** | URL de eventos: `PUBLIC_URL/api/pagos/webhook` |
| **Brevo** | Nada, si el remitente ya está verificado |

> ⚠️ Con llaves reales de Wompi el backend exige `RETIRO_CODIGO_SECRET` y `DISPERSION_MODO=MANUAL`.

---

## 9. Backups

> ⚠️ El plan gratuito de **Atlas (M0) no tiene backups automáticos**, y el de Supabase tampoco ofrece
> restauraciones que puedas descargar. `backup.sh` es tu respaldo.

`backup.sh` lee las conexiones del contenedor del backend y ejecuta `mongodump` y `pg_dump` en
contenedores temporales (no hay que instalar nada). También empaqueta el volumen de n8n
(`drivemaster_n8n_data`: workflows del chat IA, ejecuciones y credenciales). Guarda
`mongo_FECHA.archive.gz`, `postgres_FECHA.sql.gz` y `n8n_FECHA.tar.gz`, y borra los de más de 14 días.

### 9.1 Copiar el script a la VM

Desde tu PC, en la carpeta del repo:

```bash
ssh -t tu_usuario@IP-VM 'sudo mkdir -p /opt/drivemaster && sudo chown $USER: /opt/drivemaster'
scp homelab/backup.sh tu_usuario@IP-VM:/opt/drivemaster/backup.sh
```

En la VM:

```bash
chmod +x /opt/drivemaster/backup.sh
/opt/drivemaster/backup.sh
```

> Si ves `bad interpreter` o `$'\r': command not found`: `sed -i 's/\r$//' /opt/drivemaster/backup.sh`.
> Si `pg_dump` dice *server version mismatch*, cambia `IMAGEN_PG` en el script por la versión de tu
> Supabase (Supabase → Settings → Infrastructure).

### 9.2 Programarlo con cron

```bash
crontab -e
```

```
0 3 * * * /opt/drivemaster/backup.sh /mnt/nas/drivemaster >> /opt/drivemaster/backup.log 2>&1
```

> **Almacenamiento ≠ Backup**: guarda los backups **fuera de la VM** (NAS, G52 por NFS, disco externo).

### 9.3 Restaurar

Restaurar **sobrescribe datos de producción**. Practícalo primero contra una base de pruebas (otro
cluster de Atlas u otro proyecto de Supabase) cambiando las URIs:

```bash
MONGO_URI='mongodb+srv://...base-de-pruebas...' \
  docker run --rm -i -e MONGO_URI mongo:7.0 sh -c 'mongorestore --uri "$MONGO_URI" --archive --gzip --drop' \
  < /opt/drivemaster/backups/mongo_FECHA.archive.gz

gunzip -c /opt/drivemaster/backups/postgres_FECHA.sql.gz \
  | PG_URI='postgresql://usuario:clave@host:5432/postgres?sslmode=require' \
    docker run --rm -i -e PG_URI postgres:17-alpine sh -c 'psql "$PG_URI"'
```

Para n8n: detén el stack (Portainer → Stacks → drivemaster → Stop) y restaura el volumen:

```bash
docker run --rm -v drivemaster_n8n_data:/data -v /opt/drivemaster/backups:/backup alpine:3.20 \
  sh -c 'rm -rf /data/* && tar xzf /backup/n8n_FECHA.tar.gz -C /data'
```

`--drop` borra cada colección antes de restaurarla: úsalo solo cuando de verdad quieras volver atrás.

---

## 10. Actualizar a una versión nueva del código

1. Sube los cambios a la rama `homelab` en GitHub.
2. En la VM: `/opt/drivemaster/backup.sh`.
3. En Portainer: **Stacks → drivemaster → Pull and redeploy**.

Con `pull_policy: build`, Portainer recompila el backend y el frontend en cada despliegue (también al
cambiar solo una variable). Las capas que no cambiaron se reutilizan, así que tarda pocos minutos.

> **GitOps updates** se saltaría el backup previo: déjalo apagado.

---

## 11. Dónde está cada cosa en Portainer

| Quieres… | En Portainer |
| --- | --- |
| Ver si todo corre | **Stacks → drivemaster** |
| Log del backend | **Containers → drivemaster-backend-1 → Logs** |
| Reiniciar el backend | **Containers → drivemaster-backend-1 → Restart** |
| Abrir el editor de n8n | Túnel SSH (`ssh -L 5678:localhost:5678 ...`) → `http://localhost:5678` |
| CPU y RAM | **Containers → (contenedor) → Stats** |
| Cambiar una variable | **Stacks → drivemaster → Environment variables → Update the stack** |
| Liberar disco | **Images** → borrar las *Unused* |

## 12. Problemas comunes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| *Deploy the stack* gris | Alguna variable mal formada (comandos, `EOF`, líneas sin `=`) | Vacía el cuadro y pega solo `NOMBRE=valor` |
| Backend: `OAuth2ClientWebMvcSecurityConfiguration ... Constructor threw exception` | `GOOGLE_CLIENT_ID` vacío | Llenarlo |
| Backend: `MongoTimeoutException` / *timed out after 30000 ms* | Atlas bloquea tu IP | Atlas → Network Access |
| Backend: `Network is unreachable` / `UnknownHostException` con Supabase | Conexión directa (IPv6) | Usa el Session pooler (paso 6) |
| Al desplegar: *authentication required* | Token vencido o mal copiado | Token nuevo (paso 4) |
| La web carga pero el login falla | `PUBLIC_URL` distinta de la dirección que abres | Corrige y **Update the stack** |
| Notificaciones o tareas duplicadas | Render sigue encendido | Suspende Render (paso 6) |
| `429 Too Many Requests` al compilar | Límite de Docker Hub | Espera y redespliega |
| Al desplegar: *Define N8N_CHAT_SECRET…* | Faltan los secretos del chat | Agrega `N8N_CHAT_SECRET` y `N8N_TOOLS_SECRET` en las variables del stack |
| El chat siempre dice "el asistente no está disponible" | Workflow inactivo, secreto distinto entre backend y n8n, o n8n caído | Log de `drivemaster-backend-1` (`Error llamando a n8n`) y **Executions** en n8n |
| La VM se congela al compilar | Falta RAM | Sube la RAM de la VM |

## 13. Seguridad: lista mínima

- [ ] Sin puertos abiertos en el router.
- [ ] Portainer (9443) solo en la LAN o la VPN.
- [ ] Variables solo en Portainer y en tu gestor de contraseñas, nunca en Git ni en capturas.
- [ ] `DEMO_SEED_ENABLED`, `PAGO_SIMULADO_ENABLED` y `PAGO_VERIFICACION_MANUAL_ENABLED` en `false`.
- [ ] Render suspendido.
- [ ] Backups diarios fuera de la VM.
- [ ] SSH a la VM solo con clave.
