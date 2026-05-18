const BASE_URL = `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/productos`;

const buildHeaders = (token, contentType = "application/json") => {
  const headers = {};
  if (contentType) headers["Content-Type"] = contentType;
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
};

// ─────────────────────────────────────────────────────────────────────────────
// PANEL ADMIN — requieren token
// ─────────────────────────────────────────────────────────────────────────────

export async function listarProductos(token) {
  const res = await fetch(BASE_URL, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Error al obtener productos");
  return res.json();
}

export async function obtenerProducto(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: buildHeaders(token) });
  if (!res.ok) throw new Error("Producto no encontrado");
  return res.json();
}

export async function crearProducto(producto, token) {
  const res = await fetch(`${BASE_URL}/guardar`, {
    method: "POST",
    headers: buildHeaders(token),
    body: JSON.stringify(producto),
  });
  if (!res.ok) throw new Error("Error al crear producto");
  return res.json();
}

export async function actualizarProducto(id, producto, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: buildHeaders(token),
    body: JSON.stringify(producto),
  });
  if (!res.ok) throw new Error("Error al actualizar producto");
  return res.json();
}

export async function subirImagenProducto(id, archivo, token) {
  const formData = new FormData();
  formData.append("archivo", archivo);
  const res = await fetch(`${BASE_URL}/${id}/imagen`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
  if (!res.ok) throw new Error("Error al subir imagen");
  return res.json();
}

export async function eliminarProducto(id, token) {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: buildHeaders(token),
  });
  if (!res.ok) throw new Error("Error al eliminar producto");
}

export async function actualizarStock(id, nuevoStock, token) {
  const res = await fetch(`${BASE_URL}/${id}/stock`, {
    method: "PATCH",
    headers: buildHeaders(token),
    body: JSON.stringify({ stock: nuevoStock }),
  });
  if (!res.ok) throw new Error("Error al actualizar stock");
  return res.json();
}

// ─────────────────────────────────────────────────────────────────────────────
// CATÁLOGO PÚBLICO — sin token, leen del backend directo
// ─────────────────────────────────────────────────────────────────────────────

// Todos los productos (catálogo público)
export async function obtenerProductos() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener productos");
  return res.json();
}

// Un producto por ID (catálogo público)
export async function obtenerProductoCatalogo(id) {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Producto no encontrado");
  return res.json();
}

// Búsqueda + filtros para el catálogo público
// El filtrado se hace en frontend sobre la lista completa
export async function buscarProductos(filtros = {}) {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener productos");
  let resultado = await res.json();

  if (filtros.nombre) {
    const query = filtros.nombre.toLowerCase();
    resultado = resultado.filter(
      (p) =>
        p.nombre.toLowerCase().includes(query) ||
        p.categoria.toLowerCase().includes(query)
    );
  }

  if (filtros.categoria && filtros.categoria !== "Todas") {
    resultado = resultado.filter((p) => p.categoria === filtros.categoria);
  }

  if (filtros.soloMasVendidos) {
    resultado = resultado.filter((p) => p.masVendido === true);
  }

  return resultado;
}

// Categorías únicas extraídas de los productos del backend
export async function obtenerCategorias() {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener categorías");
  const productos = await res.json();
  const unicas = [...new Set(productos.map((p) => p.categoria).filter(Boolean))];
  return unicas;
}