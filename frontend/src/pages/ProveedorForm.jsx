import { useEffect, useState } from "react";
import { crearProveedor, actualizarProveedor, obtenerProveedor } from "../services/proveedorService";
import "../css/productos.css";

function ProveedorForm({ id, onVolver, token }) {
  const esEdicion = Boolean(id);

  const [form, setForm] = useState({
    nombre: "",
    contacto: "",
    telefono: "",
    correo: "",
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (esEdicion) {
      obtenerProveedor(id, token).then(setForm).catch((err) => setError(err.message));
    }
  }, [id, token]);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (esEdicion) {
        await actualizarProveedor(id, form, token);
      } else {
        await crearProveedor(form, token);
      }
      onVolver();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="form-container">
      <h1>{esEdicion ? "Editar Proveedor" : "Nuevo Proveedor"}</h1>
      {error && <p className="estado error">{error}</p>}

      <form onSubmit={handleSubmit} className="producto-form">
        <div className="form-grid">
          <div className="form-group">
            <label>Nombre</label>
            <input name="nombre" value={form.nombre} onChange={handleChange} required />
          </div>
          <div className="form-group">
            <label>Contacto</label>
            <input name="contacto" value={form.contacto} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>Teléfono</label>
            <input name="telefono" value={form.telefono} onChange={handleChange} />
          </div>
          <div className="form-group">
            <label>Correo</label>
            <input name="correo" type="email" value={form.correo} onChange={handleChange} />
          </div>
        </div>

        <div className="form-actions">
          <button type="button" onClick={onVolver} className="btn-cancelar">Cancelar</button>
          <button type="submit" className="btn-guardar" disabled={loading}>
            {loading ? "Guardando..." : "Guardar"}
          </button>
        </div>
      </form>
    </div>
  );
}

export default ProveedorForm;