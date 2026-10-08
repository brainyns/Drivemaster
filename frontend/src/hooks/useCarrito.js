import { useCallback, useEffect, useMemo, useState } from "react";

const CLAVE = "carritoMarketplace";
export const MAX_CANTIDAD = 99;

function cargar() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE));
    // Un carrito guardado de antes puede traer productos de la antigua tienda propia de DriveMaster
    // (esPlataforma): esa tienda ya no existe y esos productos no se pueden comprar, así que se descartan.
    return Array.isArray(guardado) ? guardado.filter((x) => x?.producto && !x.producto.esPlataforma) : [];
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
 * DriveMaster es la aplicación, no una tienda: todos los productos son de una tienda y se pagan juntos
 * (un solo pago de Wompi, un pedido por tienda).
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

  const derivados = useMemo(() => ({
    catalogo: items,
    subtotalCatalogo: items.reduce((s, x) => s + (x.producto.precio || 0) * x.cantidad, 0),
    cantidad: items.reduce((s, x) => s + x.cantidad, 0),
  }), [items]);

  return { items, agregar, cambiarCantidad, quitar, quitarVarios, vaciar, ...derivados };
}
