import { useState, useEffect, useMemo, useRef, useCallback } from "react";
import { obtenerProductos } from "../services/productoService";
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
  onIrLogin, onIrAdmin, onIrSolicitud, onIrCheckout, onLogout,
  onSolicitarDiagnostico, onRegistroNegocio, onIrMarketplace, onIrMisCompras,
  solicitudItems, solicitudCount, solicitudSubtotal, solicitudIva, solicitudTotal,
  solicitudAbierta, onToggleSolicitud,
  onAddProduct, onUpdateQuantity, onRemoveProduct,
  theme, onToggleTheme,
}) {
  const token = getToken();

  const [todos, setTodos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const [busqueda, setBusqueda] = useState("");
  const [categoriaActiva, setCategoriaActiva] = useState(TODAS);
  const [marcaActiva, setMarcaActiva] = useState("");
  const [soloMasVendidos, setSoloMasVendidos] = useState(false);
  const [soloCompatibles, setSoloCompatibles] = useState(false);
  const [precioMax, setPrecioMax] = useState(0);
  const [sort, setSort] = useState("recommended");
  const [filtrosAbiertos, setFiltrosAbiertos] = useState(false);

  const [vehiculo, setVehiculo] = useState(null);
  const [idsCompatibles, setIdsCompatibles] = useState(null);

  const [productoSeleccionado, setProductoSeleccionado] = useState(null);
  const [vista, setVista] = useState("inicio");

  const catalogoRef = useRef(null);

  /* ── Carga del catálogo ─────────────────────────────────────────────── */
  useEffect(() => {
    let vivo = true;

    obtenerProductos()
      .then((data) => {
        if (!vivo) return;
        const lista = Array.isArray(data) ? data : [];
        setTodos(lista);
        const techo = lista.reduce((max, p) => Math.max(max, p.precioVenta ?? 0), 0);
        setPrecioMax(Math.ceil((techo || 300000) / 10000) * 10000);
      })
      .catch((e) => {
        if (vivo) setError(e.message);
      })
      .finally(() => {
        if (vivo) setCargando(false);
      });

    return () => {
      vivo = false;
    };
  }, []);

  /* ── Metadatos derivados (categorías, marcas) ───────────────────────── */
  const categorias = useMemo(() => {
    const mapa = new Map();
    todos.forEach((p) => {
      if (!p.categoria) return;
      mapa.set(p.categoria, (mapa.get(p.categoria) || 0) + 1);
    });
    return [...mapa.entries()]
      .map(([nombre, count]) => ({ nombre, count }))
      .sort((a, b) => b.count - a.count);
  }, [todos]);

  const marcas = useMemo(
    () => [...new Set(todos.map((p) => p.marca).filter(Boolean))].sort(),
    [todos]
  );

  const precioTecho = useMemo(
    () => Math.ceil((todos.reduce((max, p) => Math.max(max, p.precioVenta ?? 0), 0) || 300000) / 10000) * 10000,
    [todos]
  );

  /* ── Filtrado ───────────────────────────────────────────────────────── */
  const filtrados = useMemo(() => {
    const q = busqueda.trim().toLowerCase();

    const resultado = todos.filter((p) => {
      if (categoriaActiva !== TODAS && p.categoria !== categoriaActiva) return false;
      if (marcaActiva && p.marca !== marcaActiva) return false;
      if (soloMasVendidos && !p.masVendido) return false;
      if (soloCompatibles && idsCompatibles && !idsCompatibles.has(p.id)) return false;
      if (p.precioVenta != null && p.precioVenta > precioMax) return false;

      if (q) {
        const heno = `${p.nombre || ""} ${p.marca || ""} ${p.codigo || ""} ${p.categoria || ""}`.toLowerCase();
        if (!heno.includes(q)) return false;
      }
      return true;
    });

    const ordenado = [...resultado];
    if (sort === "low") ordenado.sort((a, b) => (a.precioVenta ?? 0) - (b.precioVenta ?? 0));
    else if (sort === "high") ordenado.sort((a, b) => (b.precioVenta ?? 0) - (a.precioVenta ?? 0));
    else ordenado.sort((a, b) => Number(!!b.masVendido) - Number(!!a.masVendido));

    return ordenado;
  }, [todos, busqueda, categoriaActiva, marcaActiva, soloMasVendidos, soloCompatibles, idsCompatibles, precioMax, sort]);

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

  const seleccionarVehiculo = useCallback((v, ids) => {
    setVehiculo(v);
    setIdsCompatibles(ids);
    if (v && ids) {
      setSoloCompatibles(true);
      setCategoriaActiva(TODAS);
      setBusqueda("");
      setMarcaActiva("");
      setSoloMasVendidos(false);
    } else {
      setSoloCompatibles(false);
    }
  }, []);

  const limpiarFiltros = useCallback(() => {
    setBusqueda("");
    setCategoriaActiva(TODAS);
    setMarcaActiva("");
    setSoloMasVendidos(false);
    setSoloCompatibles(false);
    setPrecioMax(precioTecho);
  }, [precioTecho]);

  const filtrosActivos =
    (categoriaActiva !== TODAS ? 1 : 0) +
    (marcaActiva ? 1 : 0) +
    (soloMasVendidos ? 1 : 0) +
    (soloCompatibles && vehiculo ? 1 : 0) +
    (precioMax < precioTecho ? 1 : 0);

  const abrirProducto = useCallback((p) => {
    setProductoSeleccionado(p);
  }, []);

  const alternativas = productoSeleccionado
    ? todos
        .filter(
          (p) =>
            p.id !== productoSeleccionado.id &&
            p.categoria === productoSeleccionado.categoria &&
            (!idsCompatibles || idsCompatibles.has(p.id))
        )
        .slice(0, 4)
    : [];

  const hayFiltrosDeVehiculo = !!(vehiculo && idsCompatibles);

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
        onIrMarketplace={onIrMarketplace}
        onIrMisCompras={onIrMisCompras}
        solicitudItems={solicitudItems}
        solicitudCount={solicitudCount}
        solicitudSubtotal={solicitudSubtotal}
        solicitudIva={solicitudIva}
        solicitudTotal={solicitudTotal}
        solicitudAbierta={solicitudAbierta}
        onToggleSolicitud={onToggleSolicitud}
        onUpdateQuantity={onUpdateQuantity}
        onRemoveProduct={onRemoveProduct}
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
              <p>Identifica tu vehículo y te mostramos los repuestos compatibles del catálogo DriveMaster.</p>
            </div>

            <VehiculoSelector
              token={token}
              vehiculo={vehiculo}
              idsCompatibles={idsCompatibles}
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
                      ? "Repuestos compatibles con tu vehículo. Compara y elige con confianza."
                      : "Explora, compara y encuentra la solución correcta."}
                  </p>
                </div>
                <span className="dm-tag">{cargando ? "CARGANDO" : `${todos.length} PRODUCTOS`}</span>
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
                    <small>Regístralo arriba y te mostramos solo lo que le sirve.</small>
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
                  categorias={categorias}
                  marcas={marcas}
                  categoriaActiva={categoriaActiva}
                  onCategoriaChange={setCategoriaActiva}
                  marcaActiva={marcaActiva}
                  onMarcaChange={setMarcaActiva}
                  precioMax={precioMax}
                  onPrecioMaxChange={setPrecioMax}
                  precioTecho={precioTecho}
                  soloMasVendidos={soloMasVendidos}
                  onMasVendidosChange={setSoloMasVendidos}
                  soloCompatibles={soloCompatibles}
                  onSoloCompatiblesChange={setSoloCompatibles}
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
                      <ProductList
                        productos={filtrados}
                        titulo="No encontramos repuestos con estos filtros."
                        subtitulo={
                          precioMax < precioTecho
                            ? `Hasta ${formatPrecio(precioMax)} COP`
                            : "Precios en COP"
                        }
                        vehiculo={vehiculo}
                        idsCompatibles={idsCompatibles}
                        soloCompatibles={soloCompatibles}
                        onProductoClick={abrirProducto}
                        onRestablecer={limpiarFiltros}
                      />
                      <p className="dm-disclaimer">
                        Productos, precios y existencias son datos de demostración.
                        Confirma la referencia antes de comprar.
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
          producto={productoSeleccionado}
          vehiculo={vehiculo}
          idsCompatibles={idsCompatibles}
          onClose={() => setProductoSeleccionado(null)}
          onAddProduct={onAddProduct}
          onIrSolicitud={onIrCheckout || (() => onIrSolicitud?.("checkout"))}
          onIrVehiculo={irAlVehiculo}
          alternativas={alternativas}
          onAbrirAlternativa={abrirProducto}
        />
      )}
    </div>
  );
}
