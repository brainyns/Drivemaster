import { useEffect, useState } from "react";
import { obtenerCompra } from "../services/compraService";
import "../css/productos.css";

function CompraDetalle({ id, onVolver }) {
  const [compra, setCompra] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    obtenerCompra(id).then(setCompra).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <p className="estado">Cargando...</p>;
  if (!compra) return <p className="estado error">Compra no encontrada</p>;

  return (
    <div className="form-container">
      <div className="productos-header">
        <h1>Detalle de Compra</h1>
        <button onClick={onVolver} className="btn-cancelar">← Volver</button>
      </div>

      <div className="producto-form">
        <p><strong>Fecha:</strong> {new Date(compra.fecha).toLocaleString()}</p>
        <p><strong>Total:</strong> ${compra.total?.toLocaleString()}</p>

        <h3 style={{ marginTop: "1.5rem" }}>Productos</h3>
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Cantidad</th>
              <th>Costo Unitario</th>
              <th>Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {compra.productos.map((p, i) => (
              <tr key={i}>
                <td>{p.nombre}</td>
                <td>{p.cantidad}</td>
                <td>${p.costo}</td>
                <td>${p.subtotal}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default CompraDetalle;