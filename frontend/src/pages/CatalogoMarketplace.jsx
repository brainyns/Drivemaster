import { useCallback, useEffect, useRef, useState } from "react";
import { getToken, getUser } from "../services/authService";
import { buscarProductos, filtrosCatalogo } from "../services/catalogoService";
import { misVehiculos } from "../services/vehiculoService";
import TarjetaProducto from "../components/marketplace/TarjetaProducto";
import "../css/marketplace.css";

const TAMANIO = 24;
const ORDENES = [
  { valor: "", label: "Recomendados" },
  { valor: "precio_asc", label: "Precio: menor a mayor" },
  { valor: "precio_desc", label: "Precio: mayor a menor" },
  { valor: "nombre", label: "Nombre" },
];
const CLAVE_VEHICULO = "marketplaceVehiculoId";

/**
 * Catálogo marketplace: repuestos de TODAS las tiendas aprobadas, con su tienda, ciudad y precio.
 * Búsqueda, filtros (ciudad, tienda, categoría) y, con sesión de cliente, el vehículo para avisar la
 * compatibilidad: solo avisa y ordena a los compatibles primero, nunca oculta productos.
 */
export default function CatalogoMarketplace({ header, onVerProducto, onAgregar }) {
  const user = getUser();
  const esCliente = user?.rol === "CLIENTE";

  const [filtrosDisponibles, setFiltrosDisponibles] = useState({ ciudades: [], tiendas: [], categorias: [] });
  const [vehiculos, setVehiculos] = useState([]);
  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [ciudad, setCiudad] = useState("");
  const [tiendaId, setTiendaId] = useState("");
  const [categoria, setCategoria] = useState("");
  const [orden, setOrden] = useState("");
  const [vehiculoId, setVehiculoId] = useState(() => {
    try { return localStorage.getItem(CLAVE_VEHICULO) || ""; } catch { return ""; }
  });
  const [pagina, setPagina] = useState(0);
  // La respuesta lleva la clave de la consulta que la produjo: "cargando" se deriva (sin setState en el efecto)
  const [respuesta, setRespuesta] = useState({ clave: null, datos: { items: [], total: 0, tamanio: TAMANIO }, error: null });
  const [agregado, setAgregado] = useState(null);
  const temporizador = useRef(null);

  // Opciones de los filtros y vehículos del cliente
  useEffect(() => {
    let vivo = true;
    filtrosCatalogo().then((f) => { if (vivo) setFiltrosDisponibles(f); }).catch(() => {});
    if (esCliente) {
      misVehiculos(getToken()).then((v) => { if (vivo) setVehiculos(Array.isArray(v) ? v : []); }).catch(() => {});
    }
    return () => { vivo = false; };
  }, [esCliente]);

  // La búsqueda por texto espera a que se deje de escribir
  useEffect(() => {
    const t = setTimeout(() => { setQ(busqueda); setPagina(0); }, 350);
    return () => clearTimeout(t);
  }, [busqueda]);

  const vehiculoConsulta = esCliente ? vehiculoId : "";
  const clave = JSON.stringify([q, ciudad, tiendaId, categoria, orden, pagina, vehiculoConsulta]);
  useEffect(() => {
    let vivo = true;
    buscarProductos({ q, ciudad, tiendaId, categoria, orden, pagina, tamanio: TAMANIO, vehiculoId: vehiculoConsulta })
      .then((datos) => { if (vivo) setRespuesta({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setRespuesta((r) => ({ ...r, clave, error: e.message })); });
    return () => { vivo = false; };
  }, [clave, q, ciudad, tiendaId, categoria, orden, pagina, vehiculoConsulta]);

  const cargando = respuesta.clave !== clave;
  const resultado = respuesta.datos;
  const error = respuesta.clave === clave ? respuesta.error : null;

  const cambiarVehiculo = (id) => {
    setVehiculoId(id);
    setPagina(0);
    try { localStorage.setItem(CLAVE_VEHICULO, id); } catch { /* sin almacenamiento */ }
  };

  const filtrar = (setter) => (e) => { setter(e.target.value); setPagina(0); };

  const limpiar = () => {
    setBusqueda(""); setQ(""); setCiudad(""); setTiendaId(""); setCategoria(""); setOrden(""); setPagina(0);
  };

  const agregar = useCallback((producto) => {
    onAgregar(producto);
    setAgregado(producto.nombre);
    clearTimeout(temporizador.current);
    temporizador.current = setTimeout(() => setAgregado(null), 2500);
  }, [onAgregar]);

  useEffect(() => () => clearTimeout(temporizador.current), []);

  const totalPaginas = Math.max(1, Math.ceil(resultado.total / (resultado.tamanio || TAMANIO)));
  const hayFiltros = busqueda || ciudad || tiendaId || categoria || orden;
  const vehiculo = vehiculos.find((v) => v.id === vehiculoId);

  return (
    <div className="mk-pagina">
      {header}
      <main className="dm-container mk-contenido">
        <div className="mk-titulo">
          <span className="dm-eyebrow"><span className="dm-dot" /> REPUESTOS DE LAS TIENDAS</span>
          <h1>Compra directamente a las tiendas</h1>
          <p>Compara precios entre tiendas de tu ciudad. Recibe en casa o retira en la tienda.</p>
        </div>

        <form className="mk-filtros" role="search" onSubmit={(e) => e.preventDefault()} aria-label="Filtros del catálogo">
          <div className="mk-campo mk-campo--ancho">
            <label htmlFor="mk-q">Buscar</label>
            <input id="mk-q" type="search" placeholder="Nombre, marca o referencia" value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)} />
          </div>
          <div className="mk-campo">
            <label htmlFor="mk-ciudad">Ciudad</label>
            <select id="mk-ciudad" value={ciudad} onChange={filtrar(setCiudad)}>
              <option value="">Todas</option>
              {filtrosDisponibles.ciudades.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="mk-campo">
            <label htmlFor="mk-tienda">Tienda</label>
            <select id="mk-tienda" value={tiendaId} onChange={filtrar(setTiendaId)}>
              <option value="">Todas</option>
              {filtrosDisponibles.tiendas.map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
            </select>
          </div>
          <div className="mk-campo">
            <label htmlFor="mk-categoria">Categoría</label>
            <select id="mk-categoria" value={categoria} onChange={filtrar(setCategoria)}>
              <option value="">Todas</option>
              {filtrosDisponibles.categorias.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="mk-campo">
            <label htmlFor="mk-orden">Ordenar</label>
            <select id="mk-orden" value={orden} onChange={filtrar(setOrden)}>
              {ORDENES.map((o) => <option key={o.valor} value={o.valor}>{o.label}</option>)}
            </select>
          </div>
          {esCliente && (
            <div className="mk-campo">
              <label htmlFor="mk-vehiculo">Mi vehículo</label>
              <select id="mk-vehiculo" value={vehiculoId} onChange={(e) => cambiarVehiculo(e.target.value)}>
                <option value="">Sin elegir</option>
                {vehiculos.map((v) => <option key={v.id} value={v.id}>{[v.marca, v.modelo, v.anio].filter(Boolean).join(" ")}</option>)}
              </select>
            </div>
          )}
        </form>

        {vehiculo && (
          <p className="mk-nota">
            Avisamos si un repuesto es compatible con tu <strong>{vehiculo.marca} {vehiculo.modelo}</strong>.
            Los compatibles salen primero; ningún producto se oculta.
          </p>
        )}
        {!esCliente && <p className="mk-nota">Inicia sesión como cliente y elige tu vehículo para ver qué repuestos le sirven.</p>}

        {agregado && <p className="mk-toast" role="status">«{agregado}» agregado al carrito.</p>}
        {error && <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>}

        <p className="mk-conteo" aria-live="polite">
          {cargando ? "Buscando..." : `${resultado.total} ${resultado.total === 1 ? "producto" : "productos"}`}
          {hayFiltros && <button className="mk-enlace" onClick={limpiar}>Limpiar filtros</button>}
        </p>

        {!cargando && resultado.items.length === 0 && !error ? (
          <div className="mk-vacio">
            <p>No encontramos productos con esos filtros.</p>
            <p className="mk-vacio__ayuda">Prueba con otra ciudad o quita algún filtro. Las tiendas que aún no completan sus datos no aparecen.</p>
          </div>
        ) : (
          <div className={`mk-grilla${cargando ? " mk-grilla--cargando" : ""}`}>
            {resultado.items.map((p) => (
              <TarjetaProducto key={p.id} producto={p} onVer={onVerProducto} onAgregar={agregar} />
            ))}
          </div>
        )}

        {totalPaginas > 1 && (
          <nav className="mk-paginas" aria-label="Paginación">
            <button className="dm-btn dm-btn--secondary" disabled={pagina === 0 || cargando} onClick={() => setPagina((p) => p - 1)}>Anterior</button>
            <span>Página {pagina + 1} de {totalPaginas}</span>
            <button className="dm-btn dm-btn--secondary" disabled={pagina + 1 >= totalPaginas || cargando} onClick={() => setPagina((p) => p + 1)}>Siguiente</button>
          </nav>
        )}
      </main>
    </div>
  );
}
