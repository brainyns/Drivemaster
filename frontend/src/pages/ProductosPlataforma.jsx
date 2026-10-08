import { useEffect, useState } from "react";
import { productosPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";

const TAMANO = 20;
const cop = (n) => (n == null ? "—" : new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n));
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

const FILTROS_INICIALES = { q: "", tiendaId: "", nuevosDias: "", estado: "" };

/**
 * Catálogo global de solo lectura (SUPERADMIN): todos los productos de todas las tiendas, con su tienda, stock
 * y fecha de ingreso. No se crea ni se edita nada aquí: cada tienda administra el suyo.
 */
export default function ProductosPlataforma({ token }) {
  const [borrador, setBorrador] = useState(FILTROS_INICIALES); // lo que se escribe
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);   // lo que se consulta
  const [pagina, setPagina] = useState(0);
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });

  const clave = JSON.stringify([filtros, pagina]);

  useEffect(() => {
    let vivo = true;
    productosPlataforma(token, { ...filtros, pagina, tamano: TAMANO })
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setEstado({ clave, datos: null, error: e.message }); });
    return () => { vivo = false; };
  }, [token, filtros, pagina, clave]);

  const cargando = estado.clave !== clave;
  const respuesta = estado.datos;
  const lista = respuesta?.productos;
  const paginas = lista ? Math.max(1, Math.ceil(lista.total / lista.tamano)) : 1;

  const aplicar = (e) => {
    e.preventDefault();
    setPagina(0);
    setFiltros(borrador);
  };
  const cambiar = (campo) => (e) => {
    const siguiente = { ...borrador, [campo]: e.target.value };
    setBorrador(siguiente);
    if (campo !== "q") { setPagina(0); setFiltros(siguiente); } // los selectores filtran al elegir
  };
  const limpiar = () => { setBorrador(FILTROS_INICIALES); setFiltros(FILTROS_INICIALES); setPagina(0); };
  const hayFiltros = Object.values(filtros).some((v) => v !== "");

  return (
    <div className="db-root dp-root">
      <header className="db-header">
        <div>
          <h1 className="db-h1">Productos</h1>
          <p className="db-sub">Catálogo global de las tiendas, solo lectura. Cada tienda administra sus propios productos.</p>
        </div>
        {lista && <span className="db-date">{lista.total} {lista.total === 1 ? "producto" : "productos"}</span>}
      </header>

      <form className="dp-filtros" onSubmit={aplicar} role="search" aria-label="Filtros de productos">
        <label className="dp-campo dp-campo--ancho">
          <span>Buscar</span>
          <input type="search" value={borrador.q} onChange={cambiar("q")} placeholder="Nombre, código, marca o referencia" />
        </label>
        <label className="dp-campo">
          <span>Tienda</span>
          <select value={borrador.tiendaId} onChange={cambiar("tiendaId")}>
            <option value="">Todas</option>
            {(respuesta?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <label className="dp-campo">
          <span>Ingreso</span>
          <select value={borrador.nuevosDias} onChange={cambiar("nuevosDias")}>
            <option value="">Todos</option>
            <option value="7">Nuevos (7 días)</option>
            <option value="30">Nuevos (30 días)</option>
          </select>
        </label>
        <label className="dp-campo">
          <span>Estado</span>
          <select value={borrador.estado} onChange={cambiar("estado")}>
            <option value="">Todos</option>
            <option value="activos">Activos</option>
            <option value="inactivos">Inactivos</option>
            <option value="sin-stock">Sin stock</option>
          </select>
        </label>
        <div className="dp-filtros__acciones">
          <button type="submit" className="dp-btn">Buscar</button>
          {hayFiltros && <button type="button" className="dp-btn dp-btn--sutil" onClick={limpiar}>Limpiar</button>}
        </div>
      </form>

      {estado.error && <div className="db-error" role="alert">{estado.error}</div>}

      <div className="db-card dp-lista" aria-busy={cargando}>
        {!lista && !estado.error ? (
          <div className="db-loading"><div className="db-spinner" /> Cargando productos…</div>
        ) : lista && lista.items.length === 0 ? (
          <p className="db-empty">{hayFiltros ? "Ningún producto coincide con los filtros." : "Todavía no hay productos."}</p>
        ) : lista && (
          <div className="db-table-wrap">
            <table className="db-table">
              <thead>
                <tr>
                  <th>Producto</th><th>Tienda</th><th>Categoría</th>
                  <th className="dp-der">Stock</th><th className="dp-der">Precio</th><th>Estado</th><th>Ingreso</th>
                </tr>
              </thead>
              <tbody>
                {lista.items.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <strong>{p.nombre || "Sin nombre"}</strong>
                      <span className="dp-sub-celda">{[p.codigo, p.marca].filter(Boolean).join(" · ")}</span>
                    </td>
                    <td>{p.tienda || "—"}</td>
                    <td>{p.categoria || "—"}</td>
                    <td className="dp-der">{p.tipo === "ENCARGO" ? "Bajo pedido" : (p.stock ?? 0)}</td>
                    <td className="dp-der">{cop(p.precio)}</td>
                    <td>
                      <span className={`dp-etiqueta ${p.activo ? "dp-etiqueta--ok" : "dp-etiqueta--no"}`}>
                        {p.activo ? "Activo" : "Inactivo"}
                      </span>
                    </td>
                    <td>{fecha(p.fechaIngreso)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {lista && lista.total > lista.tamano && (
        <nav className="dp-paginacion" aria-label="Paginación">
          <button type="button" className="dp-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {paginas}</span>
          <button type="button" className="dp-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  );
}
