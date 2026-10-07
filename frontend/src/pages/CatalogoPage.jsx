import { useState, useEffect, useMemo, useRef, useCallback } from "react";
import { cargarTodosLosProductos, filtrosCatalogo } from "../services/catalogoService";
import { getToken } from "../services/authService";

import Navbar from "../components/public/Navbar";
import Hero from "../components/public/Hero";
import VehiculoSelector from "../components/public/VehiculoSelector";
import { MarcasStrip, Pasos, Ecosistema, ParaNegocios, Beneficios, CierreBanner } from "../components/public/Landing";
import CatalogToolbar from "../components/public/CatalogToolbar";
import CatalogFilters from "../components/public/CatalogFilters";
import ProductList from "../components/public/ProductList";
import ProductDetail from "../components/public/ProductDetail";
import Footer from "../components/public/Footer";
import Icon from "../components/public/Icon";
import "../css/catalog.css";

const TODAS = "Todas";

const formatPrecio = (n) =>
  new Intl.NumberFormat("es-CO", { maximumFractionDigits: 0 }).format(n ?? 0);

export default function CatalogoPage({
  onIrLogin, onIrAdmin, onIrSolicitud, onLogout,
  onSolicitarDiagnostico, onRegistroNegocio, onIrMisCompras,
  vistaInicial = "inicio",
  onVistaConsumida,
  carrito, onIrCarrito,
  theme, onToggleTheme,
}) {
  const token = getToken();

  // Catálogo global (productos de TODAS las tiendas aprobadas con datos completos, incluida la propia
  // de DriveMaster): "base" es el conjunto sin filtros (inicio, marcas, techo de precio); "respuesta" es
  // la consulta con los filtros del servidor. Ambos llevan su clave: la carga se deriva sin setState en el efecto.
  const [base, setBase] = useState({ items: [], listo: false, error: null });
  const [opciones, setOpciones] = useState({ ciudades: [], tiendas: [], categorias: [] });
  const [respuesta, setRespuesta] = useState({ clave: null, items: [], error: null });

  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [categoriaActiva, setCategoriaActiva] = useState("");
  const [ciudadActiva, setCiudadActiva] = useState("");
  const [tiendaActiva, setTiendaActiva] = useState("");
  const [marcaActiva, setMarcaActiva] = useState("");
  const [precioElegido, setPrecioElegido] = useState(null);
  const [sort, setSort] = useState("recommended");
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false);

  const [vehiculo, setVehiculo] = useState(null);

  const [productoSeleccionado, setProductoSeleccionado] = useState(null);
  const [vista, setVista] = useState(vistaInicial);

  const catalogoRef = useRef(null);

  // La vista con la que se abrió ya se usó: el padre vuelve a "inicio" para la próxima vez
  useEffect(() => { onVistaConsumida?.(); }, [onVistaConsumida]);

  /* ── Carga inicial: conjunto completo y opciones de los filtros ─────── */
  useEffect(() => {
    let vivo = true;
    cargarTodosLosProductos({})
      .then(({ items }) => { if (vivo) setBase({ items, listo: true, error: null }); })
      .catch((e) => { if (vivo) setBase({ items: [], listo: true, error: e.message }); });
    filtrosCatalogo().then((f) => { if (vivo) setOpciones(f); }).catch(() => {});
    return () => { vivo = false; };
  }, []);

  // El texto de búsqueda espera a que se deje de escribir
  useEffect(() => {
    const t = setTimeout(() => setQ(busqueda.trim()), 350);
    return () => clearTimeout(t);
  }, [busqueda]);

  /* ── Consulta con los filtros del servidor (ciudad, tienda, categoría, texto, vehículo, orden) ── */
  const orden = sort === "low" ? "precio_asc" : sort === "high" ? "precio_desc" : "";
  const vehiculoId = vehiculo?.id || "";
  const sinFiltrosDeServidor = !q && !ciudadActiva && !tiendaActiva && !categoriaActiva && !vehiculoId && !orden;
  const clave = JSON.stringify([q, ciudadActiva, tiendaActiva, categoriaActiva, vehiculoId, orden]);

  useEffect(() => {
    if (sinFiltrosDeServidor || vista !== "catalogo") return undefined;
    let vivo = true;
    cargarTodosLosProductos({ q, ciudad: ciudadActiva, tiendaId: tiendaActiva, categoria: categoriaActiva, vehiculoId, orden })
      .then(({ items }) => { if (vivo) setRespuesta({ clave, items, error: null }); })
      .catch((e) => { if (vivo) setRespuesta({ clave, items: [], error: e.message }); });
    return () => { vivo = false; };
  }, [clave, sinFiltrosDeServidor, vista, q, ciudadActiva, tiendaActiva, categoriaActiva, vehiculoId, orden]);

  const todos = base.items;
  const error = sinFiltrosDeServidor ? base.error : (respuesta.clave === clave ? respuesta.error : null);
  const cargando = sinFiltrosDeServidor ? !base.listo : respuesta.clave !== clave;
  const consultados = sinFiltrosDeServidor ? base.items : respuesta.items;

  /* ── Metadatos derivados ────────────────────────────────────────────── */
  const marcas = useMemo(
    () => [...new Set(todos.map((p) => p.marca).filter(Boolean))].sort(),
    [todos]
  );

  const precioTecho = useMemo(
    () => Math.ceil((todos.reduce((max, p) => Math.max(max, p.precio ?? 0), 0) || 300000) / 10000) * 10000,
    [todos]
  );
  const precioMax = precioElegido ?? precioTecho;

  /* ── Filtros del navegador: marca y precio ──────────────────────────── */
  const filtrados = useMemo(
    () => consultados.filter((p) => {
      if (marcaActiva && p.marca !== marcaActiva) return false;
      if (p.precio != null && p.precio > precioMax) return false;
      return true;
    }),
    [consultados, marcaActiva, precioMax]
  );

  /* ── Vistas: el mockup es un SPA de dos páginas (home | catalog) ────── */
  const irAlCatalogo = useCallback(() => {
    setVista("catalogo");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, []);

  const irAInicio = useCallback((id) => {
    setVista("inicio");
    setTimeout(() => {
      if (id) document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
      else window.scrollTo({ top: 0, behavior: "smooth" });
    }, 50);
  }, []);

  const irAlVehiculo = useCallback(() => irAInicio("dm-vehicle"), [irAInicio]);

  const irAServicios = useCallback(() => {
    if (!token) {
      onIrLogin?.();
      return;
    }
    onIrSolicitud?.("mis-vehiculos");
  }, [token, onIrLogin, onIrSolicitud]);

  // Seguimiento de las solicitudes de diagnóstico del cliente.
  const irAMisServicios = useCallback(() => {
    if (!token) {
      onIrLogin?.();
      return;
    }
    onIrSolicitud?.("mis-servicios");
  }, [token, onIrLogin, onIrSolicitud]);

  // La compatibilidad ya no oculta nada: elegir un vehículo solo hace que el servidor avise y ordene.
  const seleccionarVehiculo = useCallback((v) => {
    setVehiculo(v);
    if (v) {
      setCategoriaActiva("");
      setBusqueda("");
      setQ("");
      setMarcaActiva("");
    }
  }, []);

  const limpiarFiltros = useCallback(() => {
    setBusqueda("");
    setQ("");
    setCategoriaActiva("");
    setCiudadActiva("");
    setTiendaActiva("");
    setMarcaActiva("");
    setPrecioElegido(null);
  }, []);

  const filtrosActivos =
    (categoriaActiva ? 1 : 0) +
    (ciudadActiva ? 1 : 0) +
    (tiendaActiva ? 1 : 0) +
    (marcaActiva ? 1 : 0) +
    (precioMax < precioTecho ? 1 : 0);

  const abrirProducto = useCallback((p) => {
    setProductoSeleccionado(p);
  }, []);

  const hayFiltrosDeVehiculo = !!vehiculo;

  return (
    <div className="dm-page">
      <Navbar
        onIrLogin={onIrLogin}
        onIrAdmin={onIrAdmin}
        onIrSolicitud={onIrSolicitud}
        onIrCatalogo={irAlCatalogo}
        onIrInicio={irAInicio}
        onLogout={onLogout}
        onSolicitarDiagnostico={onSolicitarDiagnostico}
        onIrMisServicios={irAMisServicios}
        onIrMisCompras={onIrMisCompras}
        carritoCount={carrito?.cantidad ?? 0}
        onIrCarrito={onIrCarrito}
        theme={theme}
        onToggleTheme={onToggleTheme}
        vehiculoActivo={vehiculo}
      />

      <main className="dm-view-shell">
        {vista === "inicio" ? (
          <div key="inicio" className="dm-view">
            <Hero onVerCatalogo={irAlCatalogo} onSolicitarDiagnostico={onSolicitarDiagnostico} />

            <Pasos
              onSolicitarDiagnostico={onSolicitarDiagnostico}
              onIrServicios={irAMisServicios}
            />

            <Ecosistema onSolicitarDiagnostico={onSolicitarDiagnostico} />

            <ParaNegocios onRegistroNegocio={onRegistroNegocio} />

            {/* Flujo secundario: compra directa en el catálogo de DriveMaster */}
            <div className="dm-container dm-compra-directa">
              <div className="dm-eyebrow">
                <span className="dm-line" /> COMPRA DIRECTA
              </div>
              <h2>¿Prefieres comprar el repuesto tú mismo?</h2>
              <p>Identifica tu vehículo y te avisamos qué repuestos le sirven, de DriveMaster y de las demás tiendas.</p>
            </div>

            <VehiculoSelector
              token={token}
              vehiculo={vehiculo}
              onSelect={seleccionarVehiculo}
              onIrLogin={onIrLogin}
              onIrVehiculos={irAServicios}
              onVerCatalogo={irAlCatalogo}
            />

            <MarcasStrip productos={todos} />

            <Beneficios productos={todos} onIrVehiculo={irAlVehiculo} />

            <CierreBanner onSolicitarDiagnostico={onSolicitarDiagnostico} />
          </div>
        ) : (
          <div key="catalogo" className="dm-view">
          {/* ── Catálogo ─────────────────────────────────────────────────── */}
          <section className="dm-section dm-catalogo" id="dm-catalogo" ref={catalogoRef}>
            <div className="dm-container">
              <div className="dm-breadcrumb">
                <button onClick={() => irAInicio()}>Inicio</button>
                <span>/</span> Catálogo
              </div>

              <div className="dm-section-heading">
                <div>
                  <div className="dm-eyebrow">
                    <span className="dm-line" /> PRECISIÓN EN CADA ELECCIÓN
                  </div>
                  <h1>
                    {hayFiltrosDeVehiculo ? (
                      <>Repuestos para tu vehículo.</>
                    ) : (
                      <>La pieza que <span>necesitas.</span></>
                    )}
                  </h1>
                  <p className="dm-catalogo__lead">
                    {hayFiltrosDeVehiculo
                      ? "Los compatibles salen primero y te avisamos si alguno no le sirve. Compara y elige con confianza."
                      : "Productos de DriveMaster y de las tiendas aliadas. Explora, compara y encuentra la solución correcta."}
                  </p>
                </div>
                <span className="dm-tag">{cargando ? "CARGANDO" : `${todos.length} PRODUCTOS · ${opciones.tiendas.length} TIENDAS`}</span>
              </div>

              <div className={`dm-cat-vehicle ${hayFiltrosDeVehiculo ? "is-identified" : ""}`}>
                <div className="dm-icon-tile">
                  <Icon name="car" size={26} />
                </div>
                <div>
                  <span className="dm-eyebrow">
                    {hayFiltrosDeVehiculo ? "VEHÍCULO IDENTIFICADO" : "EMPIEZA POR TU VEHÍCULO"}
                  </span>
                  <strong>
                    {hayFiltrosDeVehiculo
                      ? `${vehiculo.marca} ${vehiculo.modelo} ${vehiculo.anio || ""}${vehiculo.motor ? ` · ${vehiculo.motor}` : ""}`
                      : "Encuentra repuestos que realmente encajan."}
                  </strong>
                  {!hayFiltrosDeVehiculo && (
                    <small>Regístralo arriba y te avisamos qué repuestos le sirven.</small>
                  )}
                </div>
                {hayFiltrosDeVehiculo && (
                  <span className="dm-cat-vehicle__check">
                    <Icon name="check" size={20} />
                  </span>
                )}
                <button className="dm-btn dm-btn--secondary" onClick={irAlVehiculo}>
                  {hayFiltrosDeVehiculo ? "Cambiar vehículo" : "Identificar vehículo"}
                  <Icon name="arrow" size={16} />
                </button>
              </div>

              <CatalogToolbar
                busqueda={busqueda}
                onBusquedaChange={setBusqueda}
                sort={sort}
                onSortChange={setSort}
                filtrosAbiertos={filtrosAbiertos}
                onToggleFiltros={() => setFiltrosAbiertos((v) => !v)}
                hayFiltrosActivos={filtrosActivos}
              />

              <div className="dm-cat-layout">
                <CatalogFilters
                  abierto={filtrosAbiertos}
                  categorias={opciones.categorias}
                  ciudades={opciones.ciudades}
                  tiendas={opciones.tiendas}
                  marcas={marcas}
                  categoriaActiva={categoriaActiva}
                  onCategoriaChange={setCategoriaActiva}
                  ciudadActiva={ciudadActiva}
                  onCiudadChange={setCiudadActiva}
                  tiendaActiva={tiendaActiva}
                  onTiendaChange={setTiendaActiva}
                  marcaActiva={marcaActiva}
                  onMarcaChange={setMarcaActiva}
                  precioMax={precioMax}
                  onPrecioMaxChange={setPrecioElegido}
                  precioTecho={precioTecho}
                  hayVehiculo={hayFiltrosDeVehiculo}
                  onIrVehiculo={irAlVehiculo}
                  onLimpiar={limpiarFiltros}
                />

                <div className="dm-cat-results">
                  {cargando ? (
                    <div className="dm-empty">
                      <Icon name="box" size={35} />
                      <h3>Cargando catálogo...</h3>
                    </div>
                  ) : error ? (
                    <div className="dm-empty">
                      <Icon name="shield" size={35} />
                      <h3>No pudimos cargar el catálogo</h3>
                      <p>{error}</p>
                    </div>
                  ) : (
                    <>
                      {carrito?.mezclados && (
                        <p className="dm-aviso" role="status">
                          <span>
                            <strong>Estos productos se pagan por separado.</strong> Tu carrito tiene productos de DriveMaster
                            (compra normal) y de otras tiendas (un solo pago): paga cada grupo sin perder el otro.
                          </span>
                          <button onClick={onIrCarrito}>Ver carrito</button>
                        </p>
                      )}
                      <ProductList
                        productos={filtrados}
                        titulo="No encontramos repuestos con estos filtros."
                        subtitulo={
                          precioMax < precioTecho
                            ? `Hasta ${formatPrecio(precioMax)} COP`
                            : "Precios en COP"
                        }
                        onProductoClick={abrirProducto}
                        onRestablecer={limpiarFiltros}
                      />
                      <p className="dm-disclaimer">
                        Los precios y las existencias los define cada tienda. El IVA y el envío se calculan al pagar;
                        confirma la referencia antes de comprar.
                      </p>
                    </>
                  )}
                </div>
              </div>
            </div>
          </section>
          </div>
        )}
      </main>

      <Footer
        onIrCatalogo={irAlCatalogo}
        onIrInicio={irAInicio}
        onIrLogin={onIrLogin}
        onIrAdmin={onIrAdmin}
        onIrVehiculo={irAlVehiculo}
        onIrServicios={irAServicios}
        onIrSolicitud={onIrSolicitud}
        onSolicitarDiagnostico={onSolicitarDiagnostico}
        onRegistroNegocio={onRegistroNegocio}
      />

      {productoSeleccionado && (
        <ProductDetail
          key={productoSeleccionado.id}
          producto={productoSeleccionado}
          vehiculo={vehiculo}
          onClose={() => setProductoSeleccionado(null)}
          onAgregar={(p, cantidad) => carrito?.agregar(p, cantidad)}
          onIrCarrito={() => { setProductoSeleccionado(null); onIrCarrito?.(); }}
          onIrVehiculo={() => { setProductoSeleccionado(null); irAlVehiculo(); }}
          onAbrirProducto={abrirProducto}
          carritoMezclado={!!carrito?.mezclados}
        />
      )}
    </div>
  );
}
