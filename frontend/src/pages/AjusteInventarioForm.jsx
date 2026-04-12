import { useState } from "react";
import { ajustarStock } from "../services/inventarioService";

function AjusteInventarioForm({ productoId, onVolver }) {
  const [nuevoStock, setNuevoStock] = useState("");
  const [motivo, setMotivo] = useState("");
  const [mensaje, setMensaje] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      await ajustarStock(productoId, nuevoStock, motivo);

      setMensaje("Stock ajustado correctamente");

      setTimeout(() => {
        onVolver();
      }, 1000);
    } catch (error) {
      setMensaje("Error ajustando inventario");
    }
  };

  return (
    <div className="form-container">
      <h2>Ajuste Inventario</h2>

      <form onSubmit={handleSubmit}>
        <label>Nuevo stock real</label>

        <input
          type="number"
          value={nuevoStock}
          onChange={(e) => setNuevoStock(e.target.value)}
          required
        />

        <label>Motivo</label>

        <input
          type="text"
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
          required
        />

        <button type="submit">Guardar ajuste</button>
      </form>

      {mensaje && <p>{mensaje}</p>}
    </div>
  );
}

export default AjusteInventarioForm;
