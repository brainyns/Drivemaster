// Productos de la tienda: nivel de stock, filtros y resumen. Sin componentes: lo prueban los tests con node --test.
// "Poco stock" es lo mismo que avisa la campanita (stockActual en el mínimo o por debajo).

export const publicado = (p) => p.activo !== false;
export const porEncargo = (p) => p.tipo === "ENCARGO";

/** agotado | bajo | ok | encargo (los de encargo no dependen del stock). */
export function nivelStock(p) {
  if (porEncargo(p)) return "encargo";
  const stock = Number(p.stockActual) || 0;
  if (stock <= 0) return "agotado";
  if (p.stockMinimo != null && stock <= p.stockMinimo) return "bajo";
  return "ok";
}

export const NIVEL = {
  agotado: { tono: "error", texto: "Agotado" },
  bajo: { tono: "aviso", texto: "Poco stock" },
  ok: { tono: "ok", texto: "En stock" },
  encargo: { tono: "info", texto: "Por encargo" },
};

export const FILTROS = [
  { key: "TODOS", label: "Todos", filtro: () => true },
  { key: "POCO", label: "Poco stock", filtro: (p) => nivelStock(p) === "bajo" },
  { key: "AGOTADOS", label: "Agotados", filtro: (p) => nivelStock(p) === "agotado" },
  { key: "PUBLICADOS", label: "Publicados", filtro: publicado },
  { key: "OCULTOS", label: "Ocultos", filtro: (p) => !publicado(p) },
];

export const contar = (productos) => Object.fromEntries(FILTROS.map((f) => [f.key, productos.filter(f.filtro).length]));

export function coincide(p, termino) {
  const q = (termino || "").trim().toLowerCase();
  if (!q) return true;
  return [p.nombre, p.codigo, p.categoria, p.marca, p.referencia].some((x) => x && String(x).toLowerCase().includes(q));
}

/** Lo que necesita atención primero (agotados y poco stock), luego por nombre. */
export function ordenar(productos) {
  const peso = { agotado: 0, bajo: 1, ok: 2, encargo: 2 };
  return [...productos].sort((a, b) => peso[nivelStock(a)] - peso[nivelStock(b)]
    || String(a.nombre || "").localeCompare(String(b.nombre || ""), "es"));
}

export function resumen(productos) {
  return {
    total: productos.length,
    publicados: productos.filter(publicado).length,
    atencion: productos.filter((p) => ["agotado", "bajo"].includes(nivelStock(p))).length,
    valor: productos.reduce((s, p) => s + (Number(p.precioVenta) || 0) * Math.max(0, Number(p.stockActual) || 0), 0),
  };
}
