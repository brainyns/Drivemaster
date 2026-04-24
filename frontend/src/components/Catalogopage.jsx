import { useState, useEffect, useRef } from "react";
import { buscarProductos } from "../services/productoService";

import Navbar from "../components/Navbar";
import Banner from "../components/Banner";
import Categorias from "../components/Categorias";
import Filtros from "../components/Filtros";
import ProductList from "../components/ProductList";
import ProductDetail from "../components/ProductDetail";

// onIrAdmin → lo pasa App.jsx para navegar al login del panel
const CatalogoPage = ({ onIrAdmin }) => {
  const [busqueda,        setBusqueda]        = useState("");
  const [categoriaActiva, setCategoriaActiva] = useState("Todas");
  const [soloMasVendidos, setSoloMasVendidos] = useState(false);
  const [productos,       setProductos]       = useState([]);
  const [cargando,        setCargando]        = useState(true);
  const [productoSeleccionado, setProductoSeleccionado] = useState(null);

  const catalogoRef = useRef(null);

  useEffect(() => {
    const cargar = async () => {
      setCargando(true);
      try {
        const resultado = await buscarProductos({
          nombre: busqueda,
          categoria: categoriaActiva,
          soloMasVendidos,
        });
        setProductos(resultado);
      } catch (error) {
        console.error("Error al cargar productos:", error);
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
    if (soloMasVendidos)          return "MÁS VENDIDOS";
    if (categoriaActiva !== "Todas") return categoriaActiva.toUpperCase();
    if (busqueda)                 return `RESULTADOS: "${busqueda}"`;
    return "CATÁLOGO";
  };

  return (
    <div>
      <Navbar
        busqueda={busqueda}
        onBusquedaChange={setBusqueda}
        onIrAdmin={onIrAdmin}
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