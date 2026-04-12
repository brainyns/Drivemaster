import { useEffect, useState } from "react";
import { listarVentas, anularVenta } from "../services/ventaService";
import "../css/productos.css";

function Ventas({ onNueva, onDetalle }) {
  const [ventas, setVentas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    cargarVentas();
  }, []);

  const cargarVentas = async () => {
    try {
      const data = await listarVentas();
      setVentas(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleAnular = async (id) => {
    if (!confirm("¿Seguro que deseas anular esta venta?")) return;
    try {
      await anularVenta(id);
      cargarVentas();
    } catch (err) {
      alert("Error al anular: " + err.message);
    }
  };

  if (loading) return <p className="estado">Cargando ventas...</p>;
  if (error) return <p className="estado error">Error: {error}</p>;

  return (
    <div className="productos-container">
      <div className="productos-header">
        <h1>Ventas</h1>
        <button onClick={onNueva} className="btn-nuevo">+ Nueva Venta</button>
      </div>

      {ventas.length === 0 ? (
        <p className="estado">No hay ventas registradas.</p>
      ) : (
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Cliente ID</th>
              <th>Total</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {ventas.map((v) => (
              <tr key={v.id}>
                <td>{new Date(v.fecha).toLocaleString()}</td>
                <td>{v.clienteId}</td>
                <td>${v.total}</td>
                <td>
                  <span className={v.estado === "ANULADA" ? "badge-anulada" : "badge-pagada"}>
                    {v.estado}
                  </span>
                </td>
                <td className="acciones">
                  <button onClick={() => onDetalle(v.id)} className="btn-editar">Ver detalle</button>
                  {v.estado !== "ANULADA" && (
                    <button onClick={() => handleAnular(v.id)} className="btn-eliminar">Anular</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default Ventas;