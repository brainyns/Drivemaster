import { useCallback, useEffect, useState } from "react";
import { productosPlataforma } from "../services/plataformaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import { unir } from "../utils/formato";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/listas-plataforma.css";
import { etiquetaCategoria } from "../utils/categorias";

const TAMANO = 20;
const cop = (n) => (n == null ? "—" : new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(n));
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

const FILTROS_INICIALES = { tiendaId: "", nuevosDias: "", estado: "" };
const ESTADOS = [
  { valor: "", label: "Todos" },
  { valor: "activos", label: "Activos" },
  { valor: "inactivos", label: "Inactivos" },
  { valor: "sin-stock", label: "Sin stock" },
];
const INGRESOS = [
  { valor: "", label: "Cualquier fecha" },
  { valor: "7", label: "Nuevos (7 días)" },
  { valor: "30", label: "Nuevos (30 días)" },
];

/** Foto del producto o, sin ella (o si no carga), una caja. Es decorativa: el nombre va al lado. */
function Miniatura({ url }) {
  const [rota, setRota] = useState(false);
  return (
    <span className="lst-miniatura" aria-hidden="true">
      {url && !rota ? <img src={url} alt="" loading="lazy" onError={() => setRota(true)} /> : <Icon name="box" size={18} />}
    </span>
  );
}

/**
 * Catálogo global de solo lectura (SUPERADMIN): todos los productos de todas las tiendas, con su tienda, stock
 * y fecha de ingreso. No se crea ni se edita nada aquí: cada tienda administra el suyo.
 */
export default function ProductosPlataforma({ token }) {
  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [pagina, setPagina] = useState(0);
  const [datos, setDatos] = useState(null); // { productos: { items, total }, tiendas }
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);

  // La búsqueda va al servidor; espera a que se deje de escribir
  useEffect(() => {
    const termino = busqueda.trim();
    if (termino === q) return undefined;
    const id = setTimeout(() => { setQ(termino); setPagina(0); }, 300);
    return () => clearTimeout(id);
  }, [busqueda, q]);

  const cargar = useCallback(async (vivo = () => true) => {
    setCargando(true);
    try {
      const r = await productosPlataforma(token, { ...filtros, q, pagina, tamano: TAMANO });
      if (!vivo()) return;
      setDatos(r);
      setError(null);
    } catch (e) {
      if (vivo()) setError(e.message);
    } finally {
      if (vivo()) setCargando(false);
    }
  }, [token, filtros, q, pagina]);

  useEffect(() => {
    let activo = true;
    const id = setTimeout(() => cargar(() => activo), 0);
    return () => { activo = false; clearTimeout(id); };
  }, [cargar]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
  const poner = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(0); };
  const limpiar = () => { setBusqueda(""); setQ(""); setFiltros(FILTROS_INICIALES); setPagina(0); };
  const hayFiltros = q !== "" || Object.values(filtros).some((v) => v !== "");

  const lista = datos?.productos;
  const items = lista?.items || [];
  const total = lista?.total || 0;

  return (
    <div className="ss-panel--plataforma pp-root lst-root">
      <EncabezadoPagina titulo="Productos"
        descripcion="Catálogo global de las tiendas, solo lectura. Cada tienda administra sus propios productos." />

      <div className="pp-filtros">
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="prd-busqueda" className="ui-sr">Buscar por nombre, código, marca o referencia</label>
          <input id="prd-busqueda" className="ui-field__input" type="search" placeholder="Nombre, código, marca o referencia"
            value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </div>
        <label className="pp-filtros__select">
          Tienda
          <select className="ui-field__input" value={filtros.tiendaId} onChange={(e) => poner({ tiendaId: e.target.value })}>
            <option value="">Todas</option>
            {(datos?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <label className="pp-filtros__select">
          Ingreso
          <select className="ui-field__input" value={filtros.nuevosDias} onChange={(e) => poner({ nuevosDias: e.target.value })}>
            {INGRESOS.map((x) => <option key={x.valor || "todos"} value={x.valor}>{x.label}</option>)}
          </select>
        </label>
      </div>
      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Estado del producto">
        {ESTADOS.map((x) => (
          <button key={x.valor || "todos"} type="button" className="ui-chip" aria-pressed={filtros.estado === x.valor}
            onClick={() => poner({ estado: x.valor })}>{x.label}</button>
        ))}
        {hayFiltros && <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={limpiar}>Limpiar filtros</button>}
      </div>

      {error && <Alerta className="pp-bloque-sep" onReintentar={reintentar} reintentando={reintentando}>{error}</Alerta>}

      {!datos ? (
        !error && <Esqueleto variante="lista" cantidad={6} texto="Cargando productos…" />
      ) : items.length === 0 ? (
        hayFiltros ? (
          <EstadoVacio icono="search" titulo="Ningún producto coincide" texto="Prueba con otro nombre, código o marca, o quita algún filtro.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        ) : (
          <EstadoVacio icono="box" titulo="Todavía no hay productos"
            texto="Los productos aparecen aquí cuando las tiendas aprobadas los publican en su inventario." />
        )
      ) : (
        <>
          <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--tarjetas">
                <caption className="ui-sr">Productos de todas las tiendas</caption>
                <thead>
                  <tr>
                    <th scope="col">Producto</th>
                    <th scope="col">Tienda</th>
                    <th scope="col">Categoría</th>
                    <th scope="col" className="pp-num">Stock</th>
                    <th scope="col" className="pp-num">Precio</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Ingreso</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((p) => {
                    const sinStock = p.tipo !== "ENCARGO" && !(p.stock > 0);
                    return (
                      <tr key={p.id}>
                        <td className="ui-table__titulo">
                          <span className="lst-item">
                            <Miniatura url={p.imagenUrl} />
                            <span className="lst-item__texto">
                              <strong>{p.nombre || "Sin nombre"}</strong>
                              <span className="pp-sub">{unir([p.codigo, p.marca]) || "Sin código ni marca"}</span>
                            </span>
                          </span>
                        </td>
                        <td data-label="Tienda">{p.tienda || <span className="pp-tenue">Sin tienda</span>}</td>
                        <td data-label="Categoría">{etiquetaCategoria(p.categoria) || <span className="pp-tenue">Sin categoría</span>}</td>
                        <td data-label="Stock" className={`pp-num${sinStock ? " lst-agotado" : ""}`}>
                          {p.tipo === "ENCARGO" ? "Bajo pedido" : (p.stock ?? 0)}
                        </td>
                        <td data-label="Precio" className="pp-num">{cop(p.precio)}</td>
                        <td data-label="Estado"><ChipEstado tono={p.activo ? "ok" : "apagado"}>{p.activo ? "Activo" : "Inactivo"}</ChipEstado></td>
                        <td data-label="Ingreso" className="lst-fecha">{fecha(p.fechaIngreso)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
          <Paginacion pagina={pagina + 1} total={total} porPagina={TAMANO} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de productos" />
        </>
      )}
    </div>
  );
}
