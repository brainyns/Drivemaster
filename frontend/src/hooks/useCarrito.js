import { useCallback, useEffect, useMemo, useState } from "react";

const CLAVE = "carritoMarketplace";
export const MAX_CANTIDAD = 99;

function cargar() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE));
    return Array.isArray(guardado) ? guardado : [];
  } catch {
    return [];
  }
}

/** Entre 1 y 99; si el stock visible es menor al tope (menos de 20), ese es el límite. */
export function limitarCantidad(cantidad, producto) {
  const n = Math.max(1, Math.min(MAX_CANTIDAD, Math.floor(Number(cantidad) || 1)));
  const stock = producto?.tipo === "STOCK" && producto?.stock != null && producto.stock < 20 ? producto.stock : MAX_CANTIDAD;
  return Math.max(1, Math.min(n, stock));
}

/**
 * Carrito del catálogo marketplace. Se guarda en localStorage (solo qué y cuánto, más lo necesario
 * para mostrarlo): el precio, el IVA y el envío los calcula siempre el backend al pagar.
 *
 * Dos grupos que se pagan por separado:
 *  - "catalogo": productos de las tiendas (un solo pago de Wompi, un pedido por tienda).
 *  - "plataforma": productos "Vendido por DriveMaster" (esPlataforma), que se pagan por la compra normal.
 * Agregar de un grupo nunca quita ni bloquea lo del otro.
 */
export function useCarrito() {
  const [items, setItems] = useState(cargar);

  useEffect(() => {
    try { localStorage.setItem(CLAVE, JSON.stringify(items)); } catch { /* sin almacenamiento: el carrito vive en memoria */ }
  }, [items]);

  const agregar = useCallback((producto, cantidad = 1) => {
    setItems((prev) => {
      const i = prev.findIndex((x) => x.producto.id === producto.id);
      if (i >= 0) {
        const next = [...prev];
        next[i] = { producto, cantidad: limitarCantidad(next[i].cantidad + cantidad, producto) };
        return next;
      }
      return [...prev, { producto, cantidad: limitarCantidad(cantidad, producto) }];
    });
  }, []);

  const cambiarCantidad = useCallback((productoId, cantidad) => {
    setItems((prev) => prev.map((x) => (x.producto.id === productoId
      ? { ...x, cantidad: limitarCantidad(cantidad, x.producto) } : x)));
  }, []);

  const quitar = useCallback((productoId) => {
    setItems((prev) => prev.filter((x) => x.producto.id !== productoId));
  }, []);

  const quitarVarios = useCallback((ids) => {
    const set = new Set(ids);
    setItems((prev) => prev.filter((x) => !set.has(x.producto.id)));
  }, []);

  const vaciar = useCallback(() => setItems([]), []);

  const derivados = useMemo(() => {
    const catalogo = items.filter((x) => !x.producto.esPlataforma);
    const plataforma = items.filter((x) => x.producto.esPlataforma);
    const total = (lista) => lista.reduce((s, x) => s + (x.producto.precio || 0) * x.cantidad, 0);
    const unidades = (lista) => lista.reduce((s, x) => s + x.cantidad, 0);
    return {
      catalogo, plataforma,
      subtotalCatalogo: total(catalogo), subtotalPlataforma: total(plataforma),
      cantidad: unidades(items),
      mezclados: catalogo.length > 0 && plataforma.length > 0,
    };
  }, [items]);

  return { items, agregar, cambiarCantidad, quitar, quitarVarios, vaciar, ...derivados };
}
