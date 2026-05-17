import { useState } from "react";
import { register } from "../services/authService";
import "../css/Login.css";

export default function Register({ onRegister, onBack }) {
  const [nombre, setNombre] = useState("");
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const response = await register({ nombre, correo, password });
      onRegister(response);
    } catch (e) {
      setError(e.message || "Error al registrar");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page auth-page--client">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="auth-brand-row">
            <div className="auth-brand-icon">
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <span className="auth-brand-name">CREAR CUENTA</span>
          </div>
          <span className="auth-brand-sub">Registro de cliente</span>
        </div>

        <h2>Registro</h2>
        <p className="auth-subtitle">Crea una cuenta para realizar tus compras.</p>

        <form onSubmit={handleSubmit}>
          <label>
            Nombre
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              </span>
              <input type="text" value={nombre} onChange={(e) => setNombre(e.target.value)} placeholder="Tu nombre" required />
            </div>
          </label>

          <label>
            Correo
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2"/><polyline points="2,4 12,13 22,4"/></svg>
              </span>
              <input type="email" value={correo} onChange={(e) => setCorreo(e.target.value)} placeholder="correo@ejemplo.com" required />
            </div>
          </label>

          <label>
            Contraseña
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              </span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required />
            </div>
          </label>

          {error && <p className="auth-error">{error}</p>}

          <button type="submit" disabled={loading}>
            {loading ? "Registrando..." : "Crear cuenta"}
          </button>
        </form>

        <p className="auth-footer">
          ¿Ya tienes cuenta?{" "}
          <button type="button" className="link-button" onClick={onBack}>
            Iniciar sesión
          </button>
        </p>
      </div>
    </div>
  );
}
