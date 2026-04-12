import { useEffect, useState } from "react";
import { listarCompras } from "../services/compraService";
import "../css/productos.css";
import "../css/venta.css";

function Compras({ onNueva, onDetalle }) {
  const [compras, setCompras] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    listarCompras()
      .then(setCompras)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="estado">Cargando compras...</p>;
  if (error) return <p className="estado error">Error: {error}</p>;

  return (
    <div className="productos-container">
      <div className="productos-header">
        <h1>Compras</h1>
        <button onClick={onNueva} className="btn-nuevo">+ Nueva Compra</button>
      </div>

      {compras.length === 0 ? (
        <p className="estado">No hay compras registradas.</p>
      ) : (
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Proveedor ID</th>
              <th>Productos</th>
              <th>Total</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {compras.map((c) => (
              <tr key={c.id}>
                <td>{new Date(c.fecha).toLocaleString()}</td>
                <td>{c.proveedorId}</td>
                <td>{c.productos?.length || 0} ítem(s)</td>
                <td>${c.total?.toLocaleString()}</td>
                <td className="acciones">
                  <button onClick={() => onDetalle(c.id)} className="btn-editar">Ver detalle</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default Compras;