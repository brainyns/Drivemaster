import { useState } from "react";
import Icon from "./Icon";
import { actualizarMiNombre } from "../../services/authService";

/**
 * Ventana para cambiar el nombre del usuario. Es la misma que vivía dentro del
 * Navbar (mismo servicio y mismos mensajes); ahora la abren el menú de usuario y
 * Mi perfil.
 */
export default function EditarNombre({ nombreInicial, onCerrar }) {
  const [nuevoNombre, setNuevoNombre] = useState(nombreInicial || "");
  const [mensaje, setMensaje] = useState(null);

  const handleGuardarNombre = async () => {
    try {
      await actualizarMiNombre(nuevoNombre);
      onCerrar();
      setMensaje("Nombre actualizado");
      setTimeout(() => setMensaje(null), 2000);
    } catch (e) {
      setMensaje("Error: " + e.message);
      setTimeout(() => setMensaje(null), 3000);
    }
  };

  return (
    <div className="dm-modal-overlay" onClick={onCerrar}>
      <div className="dm-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dm-modal-header">
          <span className="dm-eyebrow">DRIVEMASTER / PERFIL</span>
          <button className="dm-icon-btn" onClick={onCerrar} aria-label="Cerrar">
            <Icon name="close" />
          </button>
        </div>
        <h2>Editar nombre de usuario</h2>
        <label className="dm-form-label">
          Nombre
          <input
            type="text"
            value={nuevoNombre}
            onChange={(e) => setNuevoNombre(e.target.value)}
            autoFocus
          />
        </label>
        {mensaje && <p className="dm-form-error">{mensaje}</p>}
        <button className="dm-btn dm-btn--primary dm-btn--full" onClick={handleGuardarNombre}>
          Guardar
        </button>
      </div>
    </div>
  );
}
