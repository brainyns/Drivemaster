import { useEffect, useState } from "react";
import { productosPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";
import "../css/admin-base.css";

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
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Productos</h1>
          <p className="adm-encabezado__sub">Catálogo global de las tiendas, solo lectura. Cada tienda administra sus propios productos.</p>
        </div>
        {lista && <span className="adm-encabezado__dato">{lista.total} {lista.total === 1 ? "producto" : "productos"}</span>}
      </header>

      <form className="adm-filtros" onSubmit={aplicar} role="search" aria-label="Filtros de productos">
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" value={borrador.q} onChange={cambiar("q")} placeholder="Nombre, código, marca o referencia" />
        </label>
        <label className="adm-campo">
          <span>Tienda</span>
          <select value={borrador.tiendaId} onChange={cambiar("tiendaId")}>
            <option value="">Todas</option>
            {(respuesta?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <label className="adm-campo">
          <span>Ingreso</span>
          <select value={borrador.nuevosDias} onChange={cambiar("nuevosDias")}>
            <option value="">Todos</option>
            <option value="7">Nuevos (7 días)</option>
            <option value="30">Nuevos (30 días)</option>
          </select>
        </label>
        <label className="adm-campo">
          <span>Estado</span>
          <select value={borrador.estado} onChange={cambiar("estado")}>
            <option value="">Todos</option>
            <option value="activos">Activos</option>
            <option value="inactivos">Inactivos</option>
            <option value="sin-stock">Sin stock</option>
          </select>
        </label>
        <div className="adm-filtros__acciones">
          <button type="submit" className="adm-btn">Buscar</button>
          {hayFiltros && <button type="button" className="adm-btn adm-btn--sutil" onClick={limpiar}>Limpiar</button>}
        </div>
      </form>

      {estado.error && <div className="adm-alerta adm-alerta--error" role="alert">{estado.error}</div>}

      <div className="adm-tarjeta" aria-busy={cargando}>
        {!lista && !estado.error ? (
          <div className="adm-cargando">Cargando productos…</div>
        ) : lista && lista.items.length === 0 ? (
          <p className="adm-vacio">{hayFiltros ? "Ningún producto coincide con los filtros." : "Todavía no hay productos."}</p>
        ) : lista && (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Producto</th><th>Tienda</th><th>Categoría</th>
                  <th className="adm-tabla__der">Stock</th><th className="adm-tabla__der">Precio</th><th>Estado</th><th>Ingreso</th>
                </tr>
              </thead>
              <tbody>
                {lista.items.map((p) => (
                  <tr key={p.id}>
                    <td data-label="Producto">
                      <strong>{p.nombre || "Sin nombre"}</strong>
                      <span className="adm-tabla__sub">{[p.codigo, p.marca].filter(Boolean).join(" · ")}</span>
                    </td>
                    <td data-label="Tienda">{p.tienda || "—"}</td>
                    <td data-label="Categoría">{p.categoria || "—"}</td>
                    <td className="adm-tabla__der" data-label="Stock">{p.tipo === "ENCARGO" ? "Bajo pedido" : (p.stock ?? 0)}</td>
                    <td className="adm-tabla__der" data-label="Precio">{cop(p.precio)}</td>
                    <td data-label="Estado">
                      <span className={`adm-badge ${p.activo ? "adm-badge--exito" : "adm-badge--neutro"}`}>
                        {p.activo ? "Activo" : "Inactivo"}
                      </span>
                    </td>
                    <td data-label="Ingreso">{fecha(p.fechaIngreso)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {lista && lista.total > lista.tamano && (
        <nav className="adm-paginacion" aria-label="Paginación">
          <button type="button" className="adm-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {paginas}</span>
          <button type="button" className="adm-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  );
}
