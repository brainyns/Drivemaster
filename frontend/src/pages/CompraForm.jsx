import { useEffect, useState } from "react";
import { crearCompra } from "../services/compraService";
import { listarProveedores } from "../services/proveedorService";
import { listarProductos } from "../services/productoService";
import "../css/productos.css";
import "../css/venta.css";

function CompraForm({ onVolver }) {
  const [proveedores, setProveedores] = useState([]);
  const [productosDisponibles, setProductosDisponibles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false);

  const [form, setForm] = useState({
    proveedorId: "",
    productos: [{ productoId: "", cantidad: 1, costo: 0 }],
  });

  useEffect(() => {
    listarProveedores().then(setProveedores).catch(() => {});
    listarProductos().then(setProductosDisponibles).catch(() => {});
  }, []);

  const calcularTotal = () =>
    form.productos.reduce((acc, d) => acc + (Number(d.costo) * Number(d.cantidad)), 0);

  const handleProductoChange = (index, field, value) => {
    const nuevos = [...form.productos];
    if (field === "productoId") {
      const prod = productosDisponibles.find((p) => p.id === value);
      nuevos[index].productoId = value;
      nuevos[index].costo = prod ? prod.precioCompra : 0;
    } else {
      nuevos[index][field] = Number(value);
    }
    setForm({ ...form, productos: nuevos });
  };

  const agregarProducto = () => {
    setForm({ ...form, productos: [...form.productos, { productoId: "", cantidad: 1, costo: 0 }] });
  };

  const quitarProducto = (index) => {
    setForm({ ...form, productos: form.productos.filter((_, i) => i !== index) });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setMostrarConfirmacion(true);
  };

  const confirmarCompra = async () => {
    setLoading(true);
    setError(null);
    try {
      await crearCompra(form);
      onVolver();
    } catch (err) {
      setError(err.message);
      setMostrarConfirmacion(false);
    } finally {
      setLoading(false);
    }
  };

  const total = calcularTotal();

  return (
    <div className="form-container">
      <h1>Nueva Compra</h1>
      {error && <p className="estado error">{error}</p>}

      <form onSubmit={handleSubmit} className="producto-form">

        {/* Proveedor */}
        <div className="form-group" style={{ marginBottom: "1.5rem" }}>
          <label>Proveedor</label>
          <select
            value={form.proveedorId}
            onChange={(e) => setForm({ ...form, proveedorId: e.target.value })}
            required
            className="select-input"
          >
            <option value="">Seleccionar proveedor...</option>
            {proveedores.map((p) => (
              <option key={p.id} value={p.id}>{p.nombre} - {p.contacto}</option>
            ))}
          </select>
        </div>

        {/* Productos */}
        <div className="compatibilidad-section">
          <h3>Productos</h3>
          <div className="compra-header-cols">
            <span style={{ flex: 2 }}>Producto</span>
            <span>Cantidad</span>
            <span>Costo unit.</span>
            <span style={{ width: "32px" }}></span>
          </div>

          {form.productos.map((det, index) => (
            <div key={index} className="compatibilidad-row">
              <select
                value={det.productoId}
                onChange={(e) => handleProductoChange(index, "productoId", e.target.value)}
                required
                className="select-input"
                style={{ flex: 2 }}
              >
                <option value="">Seleccionar...</option>
                {productosDisponibles.map((p) => (
                  <option key={p.id} value={p.id}>{p.nombre}</option>
                ))}
              </select>

              <input
                type="number"
                min="1"
                value={det.cantidad}
                onChange={(e) => handleProductoChange(index, "cantidad", e.target.value)}
                required
              />

              <input
                type="number"
                min="0"
                value={det.costo}
                onChange={(e) => handleProductoChange(index, "costo", e.target.value)}
                required
              />

              <button type="button" onClick={() => quitarProducto(index)} className="btn-eliminar">✕</button>
            </div>
          ))}
          <button type="button" onClick={agregarProducto} className="btn-agregar">+ Agregar producto</button>
        </div>

        {/* Total en tiempo real */}
        <div className="total-box">
          <span>Total</span>
          <strong>${total.toLocaleString()}</strong>
        </div>

        <div className="form-actions">
          <button type="button" onClick={onVolver} className="btn-cancelar">Cancelar</button>
          <button type="submit" className="btn-guardar">Registrar Compra</button>
        </div>
      </form>

      {/* Modal confirmación */}
      {mostrarConfirmacion && (
        <div className="modal-overlay">
          <div className="modal-box">
            <h2>Confirmar Compra</h2>
            <p style={{ color: "#a0aec0" }}>¿Estás seguro que deseas registrar esta compra?</p>

            <table className="productos-tabla" style={{ margin: "1rem 0" }}>
              <thead>
                <tr>
                  <th>Producto</th>
                  <th>Cant.</th>
                  <th>Costo</th>
                  <th>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {form.productos.map((det, i) => {
                  const prod = productosDisponibles.find((p) => p.id === det.productoId);
                  return (
                    <tr key={i}>
                      <td>{prod?.nombre || "-"}</td>
                      <td>{det.cantidad}</td>
                      <td>${det.costo}</td>
                      <td>${(Number(det.costo) * Number(det.cantidad)).toLocaleString()}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            <div className="total-box" style={{ marginBottom: "1rem" }}>
              <span>Total</span>
              <strong>${total.toLocaleString()}</strong>
            </div>

            <div className="modal-actions">
              <button onClick={() => setMostrarConfirmacion(false)} className="btn-cancelar">Cancelar</button>
              <button onClick={confirmarCompra} className="btn-guardar" disabled={loading}>
                {loading ? "Guardando..." : "Confirmar"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default CompraForm;