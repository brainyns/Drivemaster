import { useEffect, useState } from "react";
import { listarProveedores, eliminarProveedor } from "../services/proveedorService";
import "../css/productos.css";

function Proveedores({ onNuevo, onEditar }) {
  const [proveedores, setProveedores] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    listarProveedores()
      .then(setProveedores)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const handleEliminar = async (id) => {
    if (!confirm("¿Seguro que deseas eliminar este proveedor?")) return;
    try {
      await eliminarProveedor(id);
      setProveedores(proveedores.filter((p) => p.id !== id));
    } catch (err) {
      alert("Error al eliminar: " + err.message);
    }
  };

  if (loading) return <p className="estado">Cargando proveedores...</p>;
  if (error) return <p className="estado error">Error: {error}</p>;

  return (
    <div className="productos-container">
      <div className="productos-header">
        <h1>Proveedores</h1>
        <button onClick={onNuevo} className="btn-nuevo">+ Nuevo Proveedor</button>
      </div>

      {proveedores.length === 0 ? (
        <p className="estado">No hay proveedores registrados.</p>
      ) : (
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Contacto</th>
              <th>Teléfono</th>
              <th>Correo</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {proveedores.map((p) => (
              <tr key={p.id}>
                <td>{p.nombre}</td>
                <td>{p.contacto}</td>
                <td>{p.telefono}</td>
                <td>{p.correo}</td>
                <td className="acciones">
                  <button onClick={() => onEditar(p.id)} className="btn-editar">Editar</button>
                  <button onClick={() => handleEliminar(p.id)} className="btn-eliminar">Eliminar</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default Proveedores;