import { useState } from "react";
import { loginInterno } from "../services/authService";
import "../css/Login.css";

export default function AdminLogin({ onLogin, onVolver }) {
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const response = await loginInterno(correo, password);
      onLogin(response);
    } catch (err) {
      setError(err.message || "Credenciales inválidas");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="auth-brand-row">
            <div className="auth-brand-icon">
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <rect x="3" y="11" width="18" height="11" rx="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
            </div>
            <span className="auth-brand-name">ACCESO INTERNO</span>
          </div>
          <span className="auth-brand-sub">Personal autorizado</span>
        </div>

        <h2>Iniciar sesión</h2>
        <p className="auth-subtitle">
          Ingresa con tus credenciales de empleado.
        </p>

        <form onSubmit={handleSubmit}>
          <label>
            Correo electrónico
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2"/><polyline points="2,4 12,13 22,4"/></svg>
              </span>
              <input type="email" placeholder="correo@drivemaster.co" value={correo} onChange={(e) => setCorreo(e.target.value)} required />
            </div>
          </label>

          <label>
            Contraseña
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              </span>
              <input type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </div>
          </label>

          {error && <p className="auth-error">{error}</p>}

          <button type="submit" disabled={loading}>
            {loading ? "Ingresando..." : "Entrar"}
          </button>
        </form>

        <p className="auth-footer">
          <button type="button" className="link-button" onClick={onVolver}>
            ← Volver al inicio de sesión de clientes
          </button>
        </p>
      </div>
    </div>
  );
}
