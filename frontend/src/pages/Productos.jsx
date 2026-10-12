import { useEffect, useState } from "react";
import { listarProductos, listarProductosMiTienda, eliminarProducto } from "../services/productoService";
import { miTienda } from "../services/tiendaService";
import EstadoNegocio from "../components/EstadoNegocio";
import "../css/productos.css";
import "../css/admin-base.css";

// ─── SVG Icons ────────────────────────────────────────────────────────────────
const IconBox = () => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
    <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
    <line x1="12" y1="22.08" x2="12" y2="12"/>
  </svg>
);

const IconAlert = () => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <triangle points="10.29 3.86 1.82 18 22.18 18"/>
    <path d="M10.29 3.86L1.82 18h20.36L10.29 3.86z"/>
    <line x1="12" y1="9" x2="12" y2="13"/>
    <line x1="12" y1="17" x2="12.01" y2="17"/>
  </svg>
);

const IconTag = () => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
    <line x1="7" y1="7" x2="7.01" y2="7"/>
  </svg>
);

const IconDollar = () => (
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="12" y1="1" x2="12" y2="23"/>
    <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
  </svg>
);

// esTienda: el usuario es una tienda del marketplace y solo ve su propio catálogo.
function Productos({ onNuevo, onEditar, token, esTienda = false }) {
  const [productos, setProductos] = useState([]);
  const [tienda, setTienda]       = useState(null);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState(null);
  const [filtro, setFiltro]       = useState("todos");
  const [busqueda, setBusqueda]   = useState("");

  useEffect(() => { cargarProductos(); }, [token, esTienda]);

  const cargarProductos = async () => {
    try {
      if (esTienda) {
        // Si la tienda aún no está aprobada, sus productos responden 403 y se muestra
        // el aviso de estado en lugar del error.
        const [t, p] = await Promise.allSettled([miTienda(token), listarProductosMiTienda(token)]);
        if (t.status === "fulfilled") setTienda(t.value);
        if (p.status === "fulfilled") setProductos(p.value);
        else setError(p.reason.message);
      } else {
        setProductos(await listarProductos(token));
      }
    }
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

  // Etiqueta y variante de cada nivel de stock (admin-base.css)
  const NIVEL = {
    activo:  { label: "Normal", badge: "exito", barra: "" },
    medio:   { label: "Medio",  badge: "pendiente", barra: " adm-nivel__relleno--pendiente" },
    critico: { label: "Bajo",   badge: "peligro", barra: " adm-nivel__relleno--peligro" },
  };
  const FILTROS = [
    { k: "todos",   l: "Todos" },
    { k: "activo",  l: "Normal" },
    { k: "medio",   l: "Medio" },
    { k: "critico", l: "Bajo" },
  ];
  const conteoFiltro = (k) => (k === "todos" ? productos.length : productos.filter(p => getEstado(p) === k).length);

  if (loading) return <div className="adm-pagina"><div className="adm-cargando">Cargando productos…</div></div>;

  return (
    <EstadoNegocio negocio={tienda} tipo="tienda">
    {error ? <div className="adm-pagina"><p className="adm-alerta adm-alerta--error" role="alert">{error}</p></div> : (
    <div className="adm-pagina">

      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">{tienda ? `Productos de ${tienda.nombre}` : "Gestión de productos"}</h1>
          <p className="adm-encabezado__sub">
            {tienda
              ? "Catálogo e inventario de tu tienda. Los talleres ven tu precio, disponibilidad y ubicación."
              : "Inventario centralizado de refacciones y consumibles automotrices."}
          </p>
        </div>
        <div className="adm-encabezado__lado">
          <button type="button" className="adm-btn adm-btn--primario" onClick={onNuevo}>Agregar producto</button>
        </div>
      </header>

      <section className="adm-kpis" aria-label="Resumen del catálogo">
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true"><IconBox /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Total de repuestos</span>
            <p className="adm-kpi__valor">{productos.length}</p>
          </div>
        </div>
        <div className={`adm-kpi${stockCritico > 0 ? " adm-kpi--alerta" : ""}`}>
          <span className={`adm-kpi__icono${stockCritico > 0 ? " adm-kpi__icono--peligro" : ""}`} aria-hidden="true"><IconAlert /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Stock crítico</span>
            <p className="adm-kpi__valor">
              {stockCritico}
              {stockCritico > 0 && <span className="adm-badge adm-badge--peligro">Revisar</span>}
            </p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true"><IconTag /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Categorías</span>
            <p className="adm-kpi__valor">{categorias}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono" aria-hidden="true"><IconDollar /></span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Valor del inventario</span>
            <p className="adm-kpi__valor">${valorTotal.toLocaleString("es-CO")}</p>
          </div>
        </div>
      </section>

      <div className="adm-filtros" role="search" aria-label="Filtros de productos">
        <nav className="adm-pestanas" aria-label="Nivel de stock">
          {FILTROS.map(f => (
            <button key={f.k} type="button" aria-pressed={filtro === f.k}
              className={`adm-pestana${filtro === f.k ? " adm-pestana--activa" : ""}`}
              onClick={() => setFiltro(f.k)}>
              {f.l}<span className="adm-pestana__n">{conteoFiltro(f.k)}</span>
            </button>
          ))}
        </nav>
        <span className="adm-filtros__separador" />
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" placeholder="Nombre, código o categoría" value={busqueda} onChange={e => setBusqueda(e.target.value)} />
        </label>
      </div>

      <div className="adm-tarjeta">
        {filtrados.length === 0 ? (
          <p className="adm-vacio">No se encontraron productos.</p>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th>Código</th>
                  <th>Categoría</th>
                  <th className="adm-tabla__der">Precio de venta</th>
                  <th>Stock</th>
                  <th>Estado</th>
                  <th className="adm-tabla__der">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {filtrados.map((p, i) => {
                  const estado = getEstado(p);
                  const nivel = NIVEL[estado];
                  return (
                    <tr key={p.id}>
                      <td data-label="Producto">
                        <div className="adm-celda-producto">
                          <span className="adm-miniatura" aria-hidden="true">{p.nombre?.[0]?.toUpperCase()}</span>
                          <div>
                            <strong>{p.nombre}</strong>
                            <span className="adm-tabla__sub">{p.marca || "Sin marca"}</span>
                          </div>
                        </div>
                      </td>
                      <td data-label="Código"><span className="adm-mono">{p.codigo || String(i + 1).padStart(4, "0")}</span></td>
                      <td data-label="Categoría"><span className="adm-badge adm-badge--neutro">{p.categoria || "Sin categoría"}</span></td>
                      <td data-label="Precio de venta" className="adm-tabla__der adm-tabla__nowrap">${p.precioVenta?.toLocaleString("es-CO")}</td>
                      <td data-label="Stock">
                        <div className="adm-nivel">
                          <span className="adm-nivel__pista" aria-hidden="true">
                            <span className={`adm-nivel__relleno${nivel.barra}`} style={{ width: `${getStockPct(p)}%` }} />
                          </span>
                          <span className="adm-nivel__num">{p.stockActual}</span>
                        </div>
                      </td>
                      <td data-label="Estado"><span className={`adm-badge adm-badge--${nivel.badge}`}>{nivel.label}</span></td>
                      <td className="adm-tabla__der adm-tabla__celda--ancha">
                        <div className="adm-tabla__acciones">
                          <button type="button" onClick={() => onEditar(p.id)} className="adm-btn adm-btn--chico">Editar</button>
                          <button type="button" onClick={() => handleEliminar(p.id)} className="adm-btn adm-btn--chico adm-btn--peligro">Eliminar</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <p className="adm-paginacion">
        Mostrando {filtrados.length} de {productos.length} productos
      </p>
    </div>
    )}
    </EstadoNegocio>
  );
}

export default Productos;
