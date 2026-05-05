import { useEffect, useState } from "react";
import { listarProductos, eliminarProducto } from "../services/productoService";
import "../css/productos.css";

function Productos({ onNuevo, onEditar, token }) {
  const [productos, setProductos] = useState([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState(null);
  const [filtro, setFiltro]       = useState("todos");
  const [busqueda, setBusqueda]   = useState("");

  useEffect(() => { cargarProductos(); }, [token]);

  const cargarProductos = async () => {
    try { const d = await listarProductos(token); setProductos(d); }
    catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const handleEliminar = async (id) => {
    if (!confirm("Eliminar este producto?")) return;
    try { await eliminarProducto(id, token); setProductos(prev => prev.filter(p => p.id !== id)); }
    catch (e) { alert(e.message); }
  };

  const getEstado = p => {
    if (p.stockActual <= p.stockMinimo) return "critico";
    if (p.stockActual <= p.stockMinimo * 2) return "medio";
    return "activo";
  };

  const stockCritico = productos.filter(p => getEstado(p) === "critico").length;
  const categorias   = [...new Set(productos.map(p => p.categoria).filter(Boolean))].length;
  const valorTotal   = productos.reduce((a, p) => a + ((p.precioVenta || 0) * (p.stockActual || 0)), 0);

  const filtrados = productos.filter(p => {
    const q = busqueda.toLowerCase();
    const match = p.nombre?.toLowerCase().includes(q) ||
                  p.codigo?.toLowerCase().includes(q) ||
                  p.categoria?.toLowerCase().includes(q);
    if (filtro === "todos")   return match;
    if (filtro === "activo")  return getEstado(p) === "activo"  && match;
    if (filtro === "medio")   return getEstado(p) === "medio"   && match;
    if (filtro === "critico") return getEstado(p) === "critico" && match;
    return match;
  });

  const getStockPct = p => {
    const max = (p.stockMinimo * 5) || 100;
    return Math.min((p.stockActual / max) * 100, 100);
  };

  if (loading) return <div className="page-loading">Cargando productos...</div>;
  if (error)   return <div className="page-loading error">{error}</div>;

  return (
    <div className="kp-page">

      <div className="kp-page-header">
        <div>
          <h1 className="kp-page-title">Gestion de Productos</h1>
          <p className="kp-page-sub">Inventario centralizado de refacciones y consumibles automotrices.</p>
        </div>
        <div className="kp-page-actions">
          <button className="kp-btn kp-btn--outline" onClick={onNuevo}>Ver Productos</button>
          <button className="kp-btn kp-btn--primary" onClick={onNuevo}>Agregar Producto</button>
        </div>
      </div>

      <div className="kp-stats">
        <div className="kp-stat">
          <p className="kp-stat-label">Total Refacciones</p>
          <p className="kp-stat-value">{productos.length}</p>
        </div>

        <div className={`kp-stat${stockCritico > 0 ? " kp-stat--critico" : ""}`}>
          <p className="kp-stat-label">Stock Critico</p>
          <p className="kp-stat-value">
            {stockCritico}
            {stockCritico > 0 && <span className="kp-revisar">Revisar</span>}
          </p>
        </div>

        <div className="kp-stat">
          <p className="kp-stat-label">Categorias</p>
          <p className="kp-stat-value">{categorias}</p>
        </div>

        <div className="kp-stat">
          <p className="kp-stat-label">Valor Inventario</p>
          <p className="kp-stat-value kp-stat-value--primary">
            ${valorTotal.toLocaleString("es-CO")}
          </p>
        </div>
      </div>

      <div className="kp-card">
        <div className="kp-card-header">
          <div className="kp-card-title">
            <span className="kp-accent-bar" />
            Catalogo de Existencias
          </div>
          <div className="kp-card-tools">
            <div className="kp-filters">
              {[
                { k: "todos",   l: "Todos" },
                { k: "activo",  l: "Normal" },
                { k: "medio",   l: "Medio" },
                { k: "critico", l: "Bajo" },
              ].map(f => (
                <button
                  key={f.k}
                  onClick={() => setFiltro(f.k)}
                  className={`kp-filter-btn kp-filter-btn--${f.k}${filtro === f.k ? " activo" : ""}`}
                >{f.l}</button>
              ))}
            </div>
            <input
              className="kp-search"
              placeholder="Buscar producto..."
              value={busqueda}
              onChange={e => setBusqueda(e.target.value)}
            />
          </div>
        </div>

        <div className="kp-table-wrap">
          <table className="kp-table">
            <thead>
              <tr>
                <th>Codigo</th>
                <th>Producto</th>
                <th>Categoria</th>
                <th>Precio Venta</th>
                <th>Stock</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {filtrados.length === 0 ? (
                <tr><td colSpan="7" className="kp-empty">No se encontraron productos</td></tr>
              ) : filtrados.map((p, i) => {
                const estado = getEstado(p);
                const pct    = getStockPct(p);
                return (
                  <tr key={p.id} className="kp-row">
                    <td className="kp-id">#{p.codigo || String(i + 1).padStart(4, "0")}</td>
                    <td>
                      <div className="kp-product-cell">
                        <div className="kp-product-thumb">{p.nombre?.[0]?.toUpperCase()}</div>
                        <div>
                          <p className="kp-product-name">{p.nombre}</p>
                          <p className="kp-product-sub">{p.marca || "Sin marca"}</p>
                        </div>
                      </div>
                    </td>
                    <td><span className="kp-cat-badge">{p.categoria || "Sin categoria"}</span></td>
                    <td className="kp-price">${p.precioVenta?.toLocaleString("es-CO")}</td>
                    <td>
                      <div className="kp-stock-cell">
                        <div className="kp-stock-bar-bg">
                          <div className={`kp-stock-bar kp-stock-bar--${estado}`} style={{ width: `${pct}%` }} />
                        </div>
                        <span className={`kp-stock-num kp-stock-num--${estado}`}>{p.stockActual}</span>
                      </div>
                    </td>
                    <td>
                      <span className={`kp-estado kp-estado--${estado}`}>
                        {estado === "activo" ? "ACTIVO" : estado === "medio" ? "MEDIO" : "BAJO"}
                      </span>
                    </td>
                    <td>
                      <div className="kp-actions">
                        <button onClick={() => onEditar(p.id)} className="kp-action-btn" title="Editar">&#9998;</button>
                        <button onClick={() => handleEliminar(p.id)} className="kp-action-btn kp-action-btn--del" title="Eliminar">&#128465;</button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="kp-table-footer">
          Mostrando <strong>1-{filtrados.length}</strong> de <strong>{productos.length}</strong> productos
        </div>
      </div>
    </div>
  );
}

export default Productos;