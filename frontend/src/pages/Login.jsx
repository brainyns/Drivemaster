import { useState } from "react";
import { login } from "../services/authService";
import "../css/Login.css";

export default function Login({ onLogin, onGoRegister }) {
  const [correo, setCorreo]       = useState("");
  const [password, setPassword]   = useState("");
  const [showPass, setShowPass]   = useState(false);
  const [remember, setRemember]   = useState(false);
  const [error, setError]         = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      const response = await login(correo, password);
      onLogin(response);
    } catch (err) {
      setError(err.message || "Error de inicio de sesión");
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">

        {/* ── Brand ── */}
        <div className="auth-brand">
          <div className="auth-brand-row">
            <div className="auth-brand-icon">
              {/* steering wheel icon */}
              <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <circle cx="12" cy="12" r="10" fill="none" stroke="#fff" strokeWidth="2"/>
                <circle cx="12" cy="12" r="3"  fill="#fff"/>
                <line x1="12" y1="2"  x2="12" y2="9"  stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
                <line x1="4.2"  y1="16.5" x2="10.3" y2="13.2" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
                <line x1="19.8" y1="16.5" x2="13.7" y2="13.2" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            </div>
            <span className="auth-brand-name">Drivemaster</span>
          </div>
          <span className="auth-brand-sub">Automotive Engine</span>
        </div>

        {/* ── Heading ── */}
        <h2>Welcome Back</h2>
        <p className="auth-subtitle">
          Please sign in to access your workshop dashboard.
        </p>

        {/* ── Form ── */}
        <form onSubmit={handleSubmit}>

          {/* Email */}
          <label>
            Email Address
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2"/><polyline points="2,4 12,13 22,4"/></svg>
              </span>
              <input
                type="email"
                placeholder="name@workshop.com"
                value={correo}
                onChange={(e) => setCorreo(e.target.value)}
                required
              />
            </div>
          </label>

          {/* Password */}
          <label>
            <div className="password-header">
              <span>Password</span>
              <button type="button" className="forgot-link">Forgot Password?</button>
            </div>
            <div className="input-wrapper">
              <span className="input-icon">
                <svg viewBox="0 0 24 24"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              </span>
              <input
                type={showPass ? "text" : "password"}
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
              <button
                type="button"
                className="input-toggle"
                onClick={() => setShowPass(!showPass)}
                aria-label="Toggle password"
              >
                {showPass ? (
                  <svg viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                ) : (
                  <svg viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                )}
              </button>
            </div>
          </label>

          {/* Remember */}
          <label className="remember-row">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
            />
            Remember this terminal
          </label>

          {/* Error */}
          {error && <p className="auth-error">{error}</p>}

          {/* Submit */}
          <button type="submit">
            Sign in to Engine <span className="btn-arrow">→</span>
          </button>
        </form>

        {/* ── Security badges ── */}
        <div className="auth-badges">
          <p className="auth-badges-label">Secure terminal access powered by</p>
          <div className="auth-badges-row">
            <span className="auth-badge">
              <svg viewBox="0 0 24 24"><rect x="2" y="11" width="20" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              AES-256
            </span>
            <span className="auth-badge-dot" />
            <span className="auth-badge">
              <svg viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              Encrypted
            </span>
          </div>
        </div>

        {/* ── Footer ── */}
        <p className="auth-footer">
          ¿No tienes cuenta?{" "}
          <button type="button" className="link-button" onClick={onGoRegister}>
            Regístrate
          </button>
        </p>

        <p className="auth-version">
          System Version: <span>v1.0.0-stable</span> | © 2025 Drivemaster
        </p>
      </div>
    </div>
  );
}
