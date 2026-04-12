import { useState } from "react";
import Sidebar from "./components/Sidebar";
import Productos from "./pages/Productos";
import ProductoForm from "./pages/ProductoForm";
import AjusteInventarioForm from "./pages/AjusteInventarioForm";
import Clientes from "./pages/Clientes";
import ClienteForm from "./pages/ClienteForm";
import Ventas from "./pages/Ventas";
import VentaForm from "./pages/VentaForm";
import VentaDetalle from "./pages/VentaDetalle";
import Compras from "./pages/Compras";
import CompraForm from "./pages/CompraForm";
import CompraDetalle from "./pages/CompraDetalle";
import Proveedores from "./pages/Proveedores";
import ProveedorForm from "./pages/ProveedorForm";
import Movimientos from "./pages/Movimientos";

function App() {
  const [pagina, setPagina] = useState("productos");
  const [idSeleccionado, setIdSeleccionado] = useState(null);

  const irA = (p, id = null) => { setIdSeleccionado(id); setPagina(p); };

  const renderPagina = () => {
    if (pagina === "productos")       return <Productos onNuevo={() => irA("productoNuevo")} onEditar={(id) => irA("productoEditar", id)} />;
    if (pagina === "productoNuevo")   return <ProductoForm onVolver={() => irA("productos")} />;
    if (pagina === "productoEditar")  return <ProductoForm id={idSeleccionado} onVolver={() => irA("productos")} />;

        // AJUSTE INVENTARIO
    if (pagina === "inventarioAjuste")
      return (
        <AjusteInventarioForm
          productoId={idSeleccionado}
          onVolver={() => irA("productos")}
        />
      );

    if (pagina === "clientes")        return <Clientes onNuevo={() => irA("clienteNuevo")} onEditar={(id) => irA("clienteEditar", id)} />;
    if (pagina === "clienteNuevo")    return <ClienteForm onVolver={() => irA("clientes")} />;
    if (pagina === "clienteEditar")   return <ClienteForm id={idSeleccionado} onVolver={() => irA("clientes")} />;

    if (pagina === "ventas")          return <Ventas onNueva={() => irA("ventaNueva")} onDetalle={(id) => irA("ventaDetalle", id)} />;
    if (pagina === "ventaNueva")      return <VentaForm onVolver={() => irA("ventas")} />;
    if (pagina === "ventaDetalle")    return <VentaDetalle id={idSeleccionado} onVolver={() => irA("ventas")} />;

    if (pagina === "compras")         return <Compras onNueva={() => irA("compraNueva")} onDetalle={(id) => irA("compraDetalle", id)} />;
    if (pagina === "compraNueva")     return <CompraForm onVolver={() => irA("compras")} />;
    if (pagina === "compraDetalle")   return <CompraDetalle id={idSeleccionado} onVolver={() => irA("compras")} />;

    if (pagina === "proveedores")     return <Proveedores onNuevo={() => irA("proveedorNuevo")} onEditar={(id) => irA("proveedorEditar", id)} />;
    if (pagina === "proveedorNuevo")  return <ProveedorForm onVolver={() => irA("proveedores")} />;
    if (pagina === "proveedorEditar") return <ProveedorForm id={idSeleccionado} onVolver={() => irA("proveedores")} />;

    if (pagina === "movimientos")     return <Movimientos />;
  };

  return (
    <div className="app-layout">
      <Sidebar paginaActual={pagina} irA={irA} />
      <main className="app-content">
        {renderPagina()}
      </main>
    </div>
  );
}

export default App;