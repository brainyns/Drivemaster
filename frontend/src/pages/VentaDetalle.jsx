import { useEffect, useState } from "react";
import { obtenerVenta } from "../services/ventaService";
import "../css/productos.css";

function VentaDetalle({ id, onVolver }) {
  const [venta, setVenta] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    obtenerVenta(id).then(setVenta).finally(() => setLoading(false));
  }, [id]);

  if (loading) return <p className="estado">Cargando...</p>;
  if (!venta) return <p className="estado error">Venta no encontrada</p>;

  return (
    <div className="form-container">
      <div className="productos-header">
        <h1>Detalle de Venta</h1>
        <button onClick={onVolver} className="btn-cancelar">← Volver</button>
      </div>

      <div className="producto-form">
        <p><strong>Fecha:</strong> {new Date(venta.fecha).toLocaleString()}</p>
        <p><strong>Estado:</strong> {venta.estado}</p>
        <p><strong>Total:</strong> ${venta.total}</p>

        <h3 style={{ marginTop: "1.5rem" }}>Productos</h3>
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Cantidad</th>
              <th>Precio Unitario</th>
              <th>Subtotal</th>
            </tr>
          </thead>
          <tbody>
            {venta.productos.map((p, i) => (
              <tr key={i}>
                <td>{p.nombre}</td>
                <td>{p.cantidad}</td>
                <td>${p.precioUnitario}</td>
                <td>${p.subtotal}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <h3 style={{ marginTop: "1.5rem" }}>Pagos</h3>
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Método</th>
              <th>Monto</th>
              <th>Referencia</th>
            </tr>
          </thead>
          <tbody>
            {venta.pagos.map((p, i) => (
              <tr key={i}>
                <td>{p.metodo}</td>
                <td>${p.monto}</td>
                <td>{p.referencia}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default VentaDetalle;