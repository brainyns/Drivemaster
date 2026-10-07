# Decisiones pendientes (no son migraciones)

## IVA del costo de envío

Hoy el costo de envío de una tienda es un valor fijo en pesos que se cobra **tal cual, sin IVA**
(`CompraCatalogoService.calcular`). Queda pendiente decidir si el envío debe llevar IVA y con qué
tarifa. Va junto con "IVA según el régimen de cada tienda", que está fuera de la Fase 4.
Al decidirlo, el cambio es solo en `calcular` (el total por tienda ya separa subtotal, IVA y envío).

## Cobro de suscripciones

Queda para el final.

## Reembolsos automáticos por la API de Wompi

Hoy el ADMIN los hace a mano en el panel de Wompi y los marca en Liquidaciones y reembolsos.

## Unificación del catálogo, etapa 2 (después de la 4-C)

La etapa 1 ya está: la tienda propia de DriveMaster aparece en el catálogo nuevo con la etiqueta
"Vendido por DriveMaster" (`esPlataforma`) y se compara con las demás, pero su compra sigue por la
compra normal (Solicitud/Venta + Wompi). Un carrito no mezcla ambos flujos: el carrito del catálogo
rechaza productos de DriveMaster (409) y el frontend los separa para pagarlos por aparte.

La etapa 2 sería un solo pago para todo: la tienda propia pasaría por `CompraCatalogo` como una tienda
más. Antes hay que resolver:
- Su línea de reparto no se liquida (el dinero ya es de DriveMaster): hay que marcarla sin dispersión.
- Sus ventas dejan de ser `Venta` y salen de los reportes y del Dashboard "Tienda DriveMaster" de la
  4-C: hay que migrar esos reportes (y sus facturas/PDF) al nuevo modelo.
- Los datos de la tienda propia (ciudad, dirección, horario) deben estar completos; el ADMIN los
  completa con `PUT /api/tiendas/plataforma/datos`.
