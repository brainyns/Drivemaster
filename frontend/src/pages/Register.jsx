import { useState } from "react";
import { register } from "../services/authService";

export default function Register({ onRegister, onBack }) {
  const [nombre, setNombre] = useState("");
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [rol, setRol] = useState("VENDEDOR");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const response = await register({ nombre, correo, password, rol });
      onRegister(response);
    } catch (e) {
      setError(e.message || "Error al registrar usuario");
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="auth-page">
        <div className="auth-card">
          <h2>Registro</h2>
          <form onSubmit={handleSubmit}>
            <label>
              Nombre
              <input
                  type="text"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                  required
              />
            </label>

            <label>
              Correo
              <input
                  type="email"
                  value={correo}
                  onChange={(e) => setCorreo(e.target.value)}
                  required
              />
            </label>

            <label>
              Contraseña
              <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
              />
            </label>

            <label>
              Rol
              <select value={rol} onChange={(e) => setRol(e.target.value)}>
                <option value="VENDEDOR">Vendedor</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>

            {error && <p className="auth-error">{error}</p>}

            <button type="submit" disabled={loading}>
              {loading ? "Registrando..." : "Crear cuenta"}
            </button>
          </form>
          <p className="auth-footer">
            ¿Ya tienes cuenta?{' '}
            <button type="button" className="link-button" onClick={onBack}>
              Volver al login
            </button>
          </p>
        </div>
      </div>
  );
}
