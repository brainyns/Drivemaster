# Dónde se usa la IA en DriveMaster

Hay **cinco usos**: cuatro con modelos de lenguaje o de imagen (vía OpenRouter) y un modelo predictivo propio.
Spring Boot no llama a ningún modelo de texto directamente: valida, limita y reenvía a n8n. La única llamada directa
a OpenRouter desde el backend es la de imágenes.

| # | Función | Quién la usa | Dónde aparece | Motor |
|---|---|---|---|---|
| 1 | Chat de la tienda pública | Cualquier visitante (más datos si inició sesión) | Widget de chat | n8n `chat-drivemaster` → OpenRouter (`claude-haiku-5.5`) |
| 2 | Asistente de la tienda | Rol TIENDA | Página "Asistente de tu tienda" y resumen en Informes | n8n `asistente-tienda` → OpenRouter |
| 3 | Sugerencias del producto | Rol TIENDA | Formulario de producto | n8n `asistente-tienda` (modo `producto`) |
| 4 | Foto del producto con IA | Rol TIENDA | Botón "Generar con IA" del formulario de producto | OpenRouter Image API (`flux-3-image-klein`) |
| 5 | Probabilidad de aprobación de la cotización | Rol TALLER | Alerta al previsualizar una cotización | Regresión logística propia (`ml/`) |
| 6 | Asistente del taller | Rol TALLER | Página "Asistente" del panel del taller | n8n `asistente-tienda` (modo `taller`) → OpenRouter |

---

## 1. Chat de la tienda pública

**Qué hace:** responde preguntas de los clientes, busca repuestos en el catálogo y, si el cliente inició sesión,
le cuenta el estado de sus pedidos.

- Frontend: `frontend/src/components/chat/` (`ChatWindow.jsx`, `useChat.js`).
- Backend: `ChatProxyController` (`POST /api/chat`). Valida el mensaje (máx. 500 caracteres), limita por IP
  (`ChatRateLimiter`: 6/min, 60/día por IP, 400/día global), agrega los pedidos del cliente con
  `PedidosChatService` y reenvía a n8n con el header `X-Chat-Secret`.
- n8n: `n8n/workflows/chat-drivemaster.json`. Es un **agente** con:
  - modelo de OpenRouter (`OPENROUTER_MODEL`, por defecto `anthropic/claude-haiku-5.5`);
  - memoria en Postgres (`chat-memory`) por sesión;
  - herramienta `Buscar productos` → `GET /api/productos/buscar` (con `X-Service-Token`).
- El backend vuelve a comprobar los productos que devuelve n8n (solo tiendas que venden, máx. 3 tarjetas).
- Seguridad: los textos escritos por tiendas que parecen instrucciones al modelo se ocultan del contexto
  (`PedidosChatService.SOSPECHOSO`).
- Si n8n falla, el usuario ve un mensaje amable, no un error.

Detalle de instalación: [`n8n/README.md`](n8n/README.md) y [`n8n/ANALISIS_CHATBOT.md`](n8n/ANALISIS_CHATBOT.md).

## 2. Asistente de la tienda

**Qué hace:** ayuda al dueño de la tienda a entender su negocio. **Solo lee y sugiere, no cambia nada.**

- Frontend: `frontend/src/pages/AsistenteTienda.jsx` (chat) y `InformesTienda.jsx` (resumen del día).
- Backend: `AsistenteTiendaController` (`/api/tiendas/mi-tienda/asistente`), `AsistenteTiendaIA`,
  `ContextoAsistenteTienda`.
- El backend arma el contexto **solo con los datos de la tienda de la sesión** (ventas por mes, más vendidos, qué
  reponer, compras a proveedores, pedidos por despachar, liquidación, productos sin movimiento en 90 días) y lo
  manda a n8n. Ahí el modelo no tiene herramientas: solo ve ese contexto.
- Endpoints:
  - `POST /` — pregunta con historial (máx. 6 turnos). Devuelve también "Detrás de escena": modelo, tokens,
    tiempo y qué datos vio el modelo.
  - `GET /resumen` — resumen del día en 5 líneas; se guarda en caché 6 h por tienda para no pagar el modelo en
    cada visita.
- **Resumen semanal por correo** (`ResumenSemanalTienda`): job de los lunes para las tiendas que lo activaron.
  Las cifras las calcula el backend; la IA **solo redacta el titular**. Si la IA falla, el titular se arma sin
  ella. Endpoints `GET/PUT /resumen-semanal` y `POST /resumen-semanal/prueba` (1 cada 10 min).

## 3. Sugerencias del producto

**Qué hace:** con el nombre, marca y referencia que escribió la tienda, sugiere la categoría, compatibilidades
(marca, modelo, años, motor) y una nota.

- Frontend: `frontend/src/pages/ProductoForm.jsx`.
- Backend: `POST /api/tiendas/mi-tienda/asistente/sugerir-producto` → `AsistenteTiendaIA.sugerirProducto`.
- El modelo solo recibe lo escrito en el formulario y la lista de categorías, **nada de las ventas**.
- La respuesta es JSON y el backend la valida (máx. 8 compatibilidades) antes de mostrarla. La tienda decide si
  la usa.

## 4. Foto del producto con IA

**Qué hace:** genera una foto ilustrativa del repuesto (fondo blanco, estilo catálogo) cuando la tienda no tiene
una a mano.

- Frontend: botón "Generar con IA" en `ProductoForm.jsx` (`productoService.js`).
- Backend: `POST /api/productos/imagen-ia` → `ImagenIaService`. Llama directo a OpenRouter
  (`https://openrouter.ai/api/v1/images`), recibe la imagen en base64 y la sube a Cloudinary.
- Costo ~US$0,01 por imagen → tope global de 50 por día (`OPENROUTER_IMAGE_MAX_DIA`).
- Usa una API key **aparte** de la del chat (`OPENROUTER_API_KEY`, con límite de crédito). Sin key, el botón
  responde 503.

## 5. Probabilidad de aprobación de la cotización (modelo predictivo)

**Qué hace:** cuando el taller previsualiza una cotización, estima la probabilidad de que el cliente la apruebe,
con el nivel de riesgo, los factores a favor y en contra (explicabilidad XAI) y recomendaciones.

- Frontend: `frontend/src/components/servicio/AlertaPrediccionCotizacion.jsx` dentro de `CotizacionForm.jsx`.
- Backend: `CotizacionService.previsualizar` → `PredictorCotizacionService.predecir`.
- Variables: monto de repuestos, mano de obra, total con IVA, cantidad de repuestos, número de tiendas, tipo de
  servicio, marca y año del vehículo, distancia, horas de respuesta y calificación del taller.
- Modelo: regresión logística entrenada en `ml/` (`generar_dataset.py` → `entrenar_modelo.py` →
  `modelo_pesos.json`). Exactitud 91,8 %, ROC-AUC 0,97 (ver `ml/reporte_metricas_aula.txt`).
- Ejecución: primero intenta el microservicio Python `ml/app.py` (`http://127.0.0.1:8000/predict`); si no
  responde, calcula lo mismo en Java con `modelo_pesos.json` del classpath. Si todo falla, la cotización sale sin
  predicción (no se bloquea).
- No usa modelos externos ni manda datos fuera del servidor.

## 6. Asistente del taller

**Qué hace:** lo mismo que el asistente de la tienda, con los datos del taller. **Solo lee y sugiere.**

- Frontend: la misma página `AsistenteTienda.jsx` con `tipo="taller"`, dentro de `PanelTaller.jsx`.
- Backend: `AsistenteTallerController` (`POST /api/talleres/mi-taller/asistente`) y `ContextoAsistenteTaller`.
- Contexto: informes de 6 meses, solicitudes por responder, citas de los próximos 7 días, inventario (máx. 40
  piezas, primero stock bajo y en pérdida), pedidos en camino y saldo pendiente.
- Sin nombre, teléfono, correo, placa ni dirección de clientes. Los textos de terceros (síntomas, comentarios,
  nombres de piezas) se ocultan si parecen instrucciones al modelo (`PedidosChatService.SOSPECHOSO`).
- Usa el mismo webhook de n8n con `modo: "taller"`: **hay que volver a importar `asistente-tienda.json` en n8n**.
- Cupo: el mismo `ChatRateLimiter`, contado por taller.

---

## Configuración

| Variable | Para qué |
|---|---|
| `N8N_WEBHOOK_URL`, `N8N_ASISTENTE_TIENDA_URL` | Webhooks de n8n (chat y asistente) |
| `N8N_CHAT_SECRET` | Header `X-Chat-Secret` backend → n8n |
| `N8N_TOOLS_SECRET` | Header `X-Service-Token` n8n → `/api/productos/buscar` |
| `OPENROUTER_MODEL` | Modelo de texto (en n8n). La key de texto vive como credencial en n8n, no en el backend |
| `N8N_CHAT_RATE_MAX`, `N8N_CHAT_RATE_DIA_IP`, `N8N_CHAT_RATE_DIA_GLOBAL`, `N8N_CHAT_MAX_CARACTERES` | Topes del chat |
| `OPENROUTER_API_KEY`, `OPENROUTER_IMAGE_MODEL`, `OPENROUTER_IMAGE_MAX_DIA` | Imágenes de productos |
