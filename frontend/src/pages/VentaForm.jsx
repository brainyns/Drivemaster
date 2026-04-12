import { useEffect, useState } from "react";
import { crearVenta } from "../services/ventaService";
import { listarClientes } from "../services/clienteService";
import { listarProductos } from "../services/productoService";
import "../css/productos.css";
import "../css/venta.css";

function VentaForm({ onVolver }) {
  const [clientes, setClientes] = useState([]);
  const [productosDisponibles, setProductosDisponibles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Modal efectivo
  const [mostrarModal, setMostrarModal] = useState(false);
  const [dineroCliente, setDineroCliente] = useState("");
  const [vuelto, setVuelto] = useState(null);

  const [form, setForm] = useState({
    clienteId: "",
    metodoPago: "EFECTIVO",
    productos: [{ productoId: "", cantidad: 1 }],
  });

  useEffect(() => {
    listarClientes().then(setClientes).catch(() => {});
    listarProductos().then(setProductosDisponibles).catch(() => {});
  }, []);

  const calcularTotal = () => {
    return form.productos.reduce((acc, det) => {
      const prod = productosDisponibles.find((p) => p.id === det.productoId);
      return acc + (prod ? prod.precioVenta * det.cantidad : 0);
    }, 0);
  };

  const handleProductoChange = (index, field, value) => {
    const nuevos = [...form.productos];
    nuevos[index][field] = field === "cantidad" ? Number(value) : value;
    setForm({ ...form, productos: nuevos });
  };

  const agregarProducto = () => {
    setForm({ ...form, productos: [...form.productos, { productoId: "", cantidad: 1 }] });
  };

  const quitarProducto = (index) => {
    setForm({ ...form, productos: form.productos.filter((_, i) => i !== index) });
  };

  const registrar = async () => {
    const total = calcularTotal();
    const ventaData = {
      clienteId: form.clienteId,
      productos: form.productos,
      pagos: [{ metodo: form.metodoPago, monto: total }],
    };
    setLoading(true);
    setError(null);
    try {
      await crearVenta(ventaData);
      onVolver();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (form.metodoPago === "EFECTIVO") {
      setDineroCliente("");
      setVuelto(null);
      setMostrarModal(true);
    } else {
      registrar();
    }
  };

  const handleConfirmarEfectivo = async () => {
    const total = calcularTotal();
    const dado = Number(dineroCliente);
    if (dado < total) {
      setVuelto(null);
      alert("El dinero ingresado es menor al total de la venta");
      return;
    }
    setVuelto(dado - total);
    await registrar();
    setMostrarModal(false);
  };

  const total = calcularTotal();

  return (
    <div className="form-container">
      <h1>Nueva Venta</h1>
      {error && <p className="estado error">{error}</p>}

      <form onSubmit={handleSubmit} className="producto-form">

        {/* Cliente */}
        <div className="form-group" style={{ marginBottom: "1.5rem" }}>
          <label>Cliente</label>
          <select
            value={form.clienteId}
            onChange={(e) => setForm({ ...form, clienteId: e.target.value })}
            required
            className="select-input"
          >
            <option value="">Seleccionar cliente...</option>
            {clientes.map((c) => (
              <option key={c.id} value={c.id}>{c.nombre} - {c.identificacion}</option>
            ))}
          </select>
        </div>

        {/* Productos */}
        <div className="compatibilidad-section">
          <h3>Productos</h3>
          {form.productos.map((det, index) => (
            <div key={index} className="compatibilidad-row">
              <select
                value={det.productoId}
                onChange={(e) => handleProductoChange(index, "productoId", e.target.value)}
                required
                className="select-input"
                style={{ flex: 2 }}
              >
                <option value="">Seleccionar producto...</option>
                {productosDisponibles.map((p) => (
                  <option key={p.id} value={p.id}>{p.nombre} - ${p.precioVenta}</option>
                ))}
              </select>
              <input
                type="number"
                min="1"
                value={det.cantidad}
                onChange={(e) => handleProductoChange(index, "cantidad", e.target.value)}
                placeholder="Cantidad"
                required
              />
              <button type="button" onClick={() => quitarProducto(index)} className="btn-eliminar">✕</button>
            </div>
          ))}
          <button type="button" onClick={agregarProducto} className="btn-agregar">+ Agregar producto</button>
        </div>

        {/* Total */}
        <div className="total-box">
          <span>Total</span>
          <strong>${total.toLocaleString()}</strong>
        </div>

        {/* Método de pago */}
        <div className="form-group" style={{ marginBottom: "1.5rem" }}>
          <label>Método de pago</label>
          <div className="metodo-pago-options">
            {["EFECTIVO", "TARJETA", "TRANSFERENCIA"].map((m) => (
              <button
                key={m}
                type="button"
                className={`metodo-btn ${form.metodoPago === m ? "activo" : ""}`}
                onClick={() => setForm({ ...form, metodoPago: m })}
              >
                {m === "EFECTIVO" ? "💵 Efectivo" : m === "TARJETA" ? "💳 Tarjeta" : "🏦 Transferencia"}
              </button>
            ))}
          </div>
        </div>

        <div className="form-actions">
          <button type="button" onClick={onVolver} className="btn-cancelar">Cancelar</button>
          <button type="submit" className="btn-guardar" disabled={loading}>
            {loading ? "Guardando..." : "Registrar Venta"}
          </button>
        </div>
      </form>

      {/* Modal Efectivo */}
      {mostrarModal && (
        <div className="modal-overlay">
          <div className="modal-box">
            <h2>Pago en Efectivo</h2>
            <p className="modal-total">Total a cobrar: <strong>${total.toLocaleString()}</strong></p>
            <div className="form-group">
              <label>Dinero del cliente</label>
              <input
                type="number"
                min={total}
                value={dineroCliente}
                onChange={(e) => {
                  setDineroCliente(e.target.value);
                  setVuelto(Number(e.target.value) - total);
                }}
                placeholder={`Mínimo $${total}`}
                autoFocus
              />
            </div>
            {dineroCliente && Number(dineroCliente) >= total && (
              <div className="vuelto-box">
                Vuelto: <strong>${(Number(dineroCliente) - total).toLocaleString()}</strong>
              </div>
            )}
            <div className="modal-actions">
              <button onClick={() => setMostrarModal(false)} className="btn-cancelar">Cancelar</button>
              <button onClick={handleConfirmarEfectivo} className="btn-guardar" disabled={loading || !dineroCliente || Number(dineroCliente) < total}>
                {loading ? "Procesando..." : "Confirmar"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default VentaForm;