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
