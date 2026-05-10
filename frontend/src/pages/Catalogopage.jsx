import { useState, useEffect, useRef } from "react";
import { buscarProductos } from "../services/productoService";
import { listarMovimientos } from "../services/movimientoService";

import Navbar from "../components/Navbar";
import Banner from "../components/Banner";
import Categorias from "../components/Categorias";
import Filtros from "../components/Filtros";
import ProductList from "../components/ProductList";
import ProductDetail from "../components/ProductDetail";

const CatalogoPage = ({ onIrAdmin, theme, onToggleTheme }) => {
  const [busqueda,             setBusqueda]             = useState("");
  const [categoriaActiva,      setCategoriaActiva]      = useState("Todas");
  const [soloMasVendidos,      setSoloMasVendidos]      = useState(false);
  const [productos,            setProductos]            = useState([]);
  const [cargando,             setCargando]             = useState(true);
  const [productoSeleccionado, setProductoSeleccionado] = useState(null);

  const catalogoRef = useRef(null);

  useEffect(() => {
    const cargar = async () => {
      setCargando(true);
      try {
        // Carga productos y movimientos en paralelo
        const [resultado, movimientos] = await Promise.all([
          buscarProductos({ nombre: busqueda, categoria: categoriaActiva }),
          listarMovimientos({}, null),
        ]);

        // Construye el mapa de ventas
        const mapa = {};
        movimientos
          .filter((m) => m.tipo === "SALIDA")
          .forEach((m) => {
            mapa[m.productoId] = (mapa[m.productoId] || 0) + m.cantidad;
          });

        const filtrados = soloMasVendidos
          ? [...resultado]
              .filter((p) => (mapa[p.id] || 0) > 0)
              .sort((a, b) => (mapa[b.id] || 0) - (mapa[a.id] || 0))
          : resultado;

        setProductos(filtrados);
      } catch (error) {
        console.error("Error al cargar:", error);
      } finally {
        setCargando(false);
      }
    };

    const debounce = setTimeout(cargar, 150);
    return () => clearTimeout(debounce);
  }, [busqueda, categoriaActiva, soloMasVendidos]);

  const handleCategoriaSelect = (categoria) => {
    setCategoriaActiva(categoria === categoriaActiva ? "Todas" : categoria);
    catalogoRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const handleVerCatalogo = () => {
    catalogoRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const tituloLista = () => {
    if (soloMasVendidos)             return "MÁS VENDIDOS";
    if (categoriaActiva !== "Todas") return categoriaActiva.toUpperCase();
    if (busqueda)                    return `RESULTADOS: "${busqueda}"`;
    return "CATÁLOGO";
  };

  return (
    <div>
      <Navbar
        busqueda={busqueda}
        onBusquedaChange={setBusqueda}
        onIrAdmin={onIrAdmin}
        theme={theme}
        onToggleTheme={onToggleTheme}
      />
      <Banner onVerCatalogo={handleVerCatalogo} />
      <Categorias
        categoriaActiva={categoriaActiva}
        onCategoriaSelect={handleCategoriaSelect}
      />
      <div ref={catalogoRef}>
        <Filtros
          categoriaActiva={categoriaActiva}
          onCategoriaChange={setCategoriaActiva}
          soloMasVendidos={soloMasVendidos}
          onMasVendidosChange={setSoloMasVendidos}
          total={productos.length}
        />
        {cargando ? (
          <div style={{ textAlign: "center", padding: "80px 20px", color: "var(--text-muted)" }}>
            Cargando productos...
          </div>
        ) : (
          <ProductList
            productos={productos}
            titulo={tituloLista()}
            onProductoClick={setProductoSeleccionado}
          />
        )}
      </div>
      {productoSeleccionado && (
        <ProductDetail
          producto={productoSeleccionado}
          onClose={() => setProductoSeleccionado(null)}
        />
      )}
    </div>
  );
};

export default CatalogoPage;