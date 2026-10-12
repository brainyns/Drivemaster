# Chat IA de DriveMaster en n8n

Este flujo reemplaza **toda** la lógica de IA que antes vivía en Spring Boot
(`GeminiServiceImpl`: regex de intenciones, caché en memoria, rate limiter y llamadas directas a
Gemini). Ahora el backend solo reenvía el mensaje a este webhook y n8n hace el resto:
agente, modelo, herramientas y memoria.

```
Frontend (widget React) ─► Spring Boot (/api/chat, proxy + rate limit) ─► n8n (este workflow)
                                                                          ├─ Modelo Gemini
                                                                          ├─ Memoria Postgres (por sessionId)
                                                                          └─ Herramienta: GET /api/productos/buscar
```

Análisis completo, problemas encontrados y cómo se resolvieron: [`ANALISIS_CHATBOT.md`](ANALISIS_CHATBOT.md).

## 1. Importar el workflow

1. Levanta n8n (`docker compose up -d n8n` en local, o el stack `drivemaster` en Portainer) y abre el
   editor en http://localhost:5678. En el HomeLab, usa un túnel SSH a la VM: el puerto 5678 **nunca**
   debe quedar público.
2. Crea la cuenta/workspace inicial.
3. Si ya habías importado una versión anterior de este workflow, **desactívala y bórrala**: dos
   workflows activos con el mismo path de webhook chocan.
4. **Workflows → Import from File** → `n8n/workflows/chat-drivemaster.json`.

## 2. Credenciales (en n8n, no en el backend)

Al importar, dos nodos quedan sin credencial. Créalas en **Credentials → Add credential** y asígnalas:

| Nodo | Credencial | Dato que pide |
| --- | --- | --- |
| `Modelo Gemini` | **Google Gemini(PaLM) API** | La API key de Gemini |
| `Memoria (Postgres)` | **Postgres** | Host/base/usuario/clave de Supabase (el *Session pooler*, con SSL). La tabla `drivemaster_chat_memory` se crea sola. |

El webhook **no** necesita credencial: el nodo `Validar secreto` compara el header `X-Chat-Secret`
con la variable de entorno `N8N_CHAT_SECRET` y responde `403` si no coincide.

> ⚠️ La API key de Gemini **no va en las variables del backend** ni en el `.env` de Spring: vive solo
> como credencial dentro de n8n.

## 3. Variables de entorno del contenedor n8n

Las definen `docker-compose.yml` (raíz) y `homelab/docker-compose.yml`:

| Variable | Uso |
| --- | --- |
| `N8N_CHAT_SECRET` | Secreto del header `X-Chat-Secret` con el que el backend llama al webhook. **Obligatorio**: sin él, el workflow rechaza todo. |
| `N8N_TOOLS_SECRET` | Token `X-Service-Token` con el que la herramienta consume `/api/productos/buscar`. **Obligatorio**: sin él, el backend responde `403`. |
| `N8N_TOOLS_BASE_URL` | URL base del backend de Spring (dentro de Docker: `http://backend:8080`). |
| `GEMINI_MODEL` | Modelo de Gemini **sin** el prefijo `models/` (por defecto `gemini-2.5-flash`). |
| `N8N_BLOCK_ENV_ACCESS_IN_NODE=false` | Permite que el workflow lea las variables anteriores con `$env` (desde n8n 2.0 está bloqueado por defecto). |
| `N8N_VERSION` | Versión de la imagen de n8n. Fíjala en la que ya corre (vacía = `latest`). |

`N8N_CHAT_SECRET` y `N8N_TOOLS_SECRET` deben ser los mismos valores que recibe el backend.

## 4. Activar y probar

1. Activa el workflow (toggle **Active** / **Publish**, según la versión de n8n).
2. Prueba directa contra n8n (desde la VM o con el túnel SSH abierto):

```bash
curl -X POST http://localhost:5678/webhook/chat-drivemaster \
  -H "Content-Type: application/json" \
  -H "X-Chat-Secret: TU_N8N_CHAT_SECRET" \
  -d '{"mensaje":"Hola, me llamo Ana","sessionId":"prueba-1"}'
```

3. Sin el secreto debe responder `403`:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:5678/webhook/chat-drivemaster \
  -H "Content-Type: application/json" -d '{"mensaje":"hola","sessionId":"x"}'
```

4. Por el camino real del widget. En local, el backend está en `:8080`; en el HomeLab el backend no
   publica puertos y se entra por nginx en `:8088`:

```bash
curl -X POST http://localhost:8088/api/chat \
  -H "Content-Type: application/json" \
  -d '{"mensaje":"¿Cómo me llamo?","sessionId":"prueba-1"}'
```

Respuesta (contrato del frontend): `{ "respuesta": "...", "productos": [] }`. Con la misma
`sessionId`, el segundo mensaje debe responder "Ana": eso confirma que el agente recibe la pregunta y
que la memoria funciona.

## 5. Añadir más herramientas

Cada herramienta es un nodo que se conecta al puerto `ai_tool` del nodo `Agente DriveMaster`
(se pueden conectar varias). Plantilla para una nueva:

1. Agrega un nodo **HTTP Request** como herramienta (*HTTP Request Tool*) con *Tool Description* manual
   que explique al modelo **cuándo** usarla.
2. Usa `$fromAI('parametro', 'descripción para el modelo', 'string')` para los argumentos.
3. Llama a un endpoint del backend protegido con `X-Service-Token` y conéctalo a `ai_tool` del agente.

Ideas ya previstas: estado de una orden de servicio (`/api/ordenes-servicio/...`), agendar cita,
listar talleres activos (`/api/talleres/activos`).

## 6. Notas y límites

- **Prompt**: las reglas del asistente están en *Options → System Message* del agente. El *Prompt* es
  `{{ $json.chatInput }}`, el mensaje real del usuario. No pongas las reglas en el *Prompt*: el modelo
  dejaría de recibir la pregunta.
- **Búsqueda**: `/api/productos/buscar` solo devuelve productos activos de tiendas visibles (como el
  catálogo público). Busca en nombre, categoría, marca y compatibilidad (marca y modelo del vehículo),
  ordena por número de términos que coinciden y devuelve como máximo 10. Incluye `tipo`
  (STOCK/ENCARGO), `diasEntregaEncargo`, `modelosCompatibles`, `compatibilidadUniversal` y `tienda`.
- **`productos` llega vacío por ahora**: el widget React solo renderiza `respuesta`.
- **Memoria**: si no configuras la credencial Postgres, falla el nodo `Memoria (Postgres)` y con él el
  flujo. Alternativa sin configuración: borra ese nodo (el chat queda sin memoria entre mensajes).
- **Latencia**: el agente con herramientas tarda algunos segundos; el proxy del backend espera hasta
  90 s y el widget hasta 95 s.
- **Cambios en el editor**: tras editar el workflow, expórtalo (**Download**) y reemplaza
  `n8n/workflows/chat-drivemaster.json` para que el repositorio siga siendo la fuente de verdad.
- **Seguridad**: mantén el puerto 5678 sin publicar y respalda el volumen `n8n_data`
  (`homelab/backup.sh` ya lo incluye; contiene también la clave que cifra las credenciales).
