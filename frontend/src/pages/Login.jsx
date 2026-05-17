import { useState, useEffect, useRef } from "react";
import { login, googleLogin } from "../services/authService";
import "../css/Login.css";

export default function Login({ onLogin, onGoRegister, onVolver, onGoAdminLogin }) {
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const googleBtnRef = useRef(null);

  useEffect(() => {
    if (window?.google?.accounts?.id) {
      window.google.accounts.id.initialize({
        client_id: "571035780087-mh3obhajnmvpj98cre938rmuhmqb0cf5.apps.googleusercontent.com",
        callback: handleGoogleResponse,
      });
      window.google.accounts.id.renderButton(googleBtnRef.current, {
        theme: "outline", size: "large", width: 280, text: "continue_with",
      });
      return;
    }
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = () => {
      if (window.google && googleBtnRef.current) {
        window.google.accounts.id.initialize({
          client_id: "571035780087-mh3obhajnmvpj98cre938rmuhmqb0cf5.apps.googleusercontent.com",
          callback: handleGoogleResponse,
        });
        window.google.accounts.id.renderButton(googleBtnRef.current, {
          theme: "outline", size: "large", width: 280, text: "continue_with",
        });
      }
    };
    document.body.appendChild(script);
    return () => {
      try { google.accounts.id.cancel(); } catch {}
      if (script.parentNode) script.parentNode.removeChild(script);
    };
  }, []);

  const handleGoogleResponse = async (response) => {
    try {
      setLoading(true);
      const data = await googleLogin(response.credential);
      if (data && data.token) onLogin(data);
    } catch (err) {
      setError(err.message || "Error al iniciar sesión con Google");
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const response = await login(correo, password);
      onLogin(response);
    } catch (err) {
      setError(err.message || "Credenciales inválidas");
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
                <circle cx="12" cy="12" r="10" fill="none" stroke="#fff" strokeWidth="2"/>
                <circle cx="12" cy="12" r="3" fill="#fff"/>
                <line x1="12" y1="2" x2="12" y2="9" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
                <line x1="4.2" y1="16.5" x2="10.3" y2="13.2" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
                <line x1="19.8" y1="16.5" x2="13.7" y2="13.2" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <span className="auth-brand-name">DRIVEMASTER</span>
          </div>
          <span className="auth-brand-sub">Automotive Engine</span>
        </div>

        <h2>Iniciar Sesión</h2>
        <p className="auth-subtitle">
          Ingresa con tu correo o Google para continuar.
        </p>

        <form onSubmit={handleSubmit}>
          <label>
            Correo electrónico
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
            {loading ? "Ingresando..." : "Iniciar sesión"}
          </button>
        </form>

        <div className="auth-divider">o continúa con</div>

        <div ref={googleBtnRef} className="auth-google-btn"></div>

        <p className="auth-footer">
          ¿No tienes cuenta?{" "}
          <button type="button" className="link-button" onClick={onGoRegister}>
            Regístrate
          </button>
        </p>

        <button
          type="button"
          className="auth-interno-btn"
          onClick={onGoAdminLogin}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="11" width="18" height="11" rx="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
          Acceso interno
        </button>

        <p className="auth-version">
          © 2026 DriveMaster
        </p>
      </div>
    </div>
  );
}
