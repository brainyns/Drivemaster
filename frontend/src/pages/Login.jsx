import { useState, useEffect, useRef } from "react";
import { login, googleLogin } from "../services/authService";
import "../css/Login.css";

const CLIENT_ID = "571035780087-mh3obhajnmvpj98cre938rmuhmqb0cf5.apps.googleusercontent.com";

function Marca() {
  return (
    <div className="auth-brand">
      <div className="auth-brand-row">
        <div className="auth-brand-icon">
          <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="10" fill="none" strokeWidth="2" />
            <circle cx="12" cy="12" r="3" strokeWidth="2" />
            <line x1="12" y1="2" x2="12" y2="9" strokeWidth="2" strokeLinecap="round" />
            <line x1="4.2" y1="16.5" x2="10.3" y2="13.2" strokeWidth="2" strokeLinecap="round" />
            <line x1="19.8" y1="16.5" x2="13.7" y2="13.2" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </div>
        <span className="auth-brand-name">DRIVEMASTER</span>
      </div>
      <span className="auth-brand-sub">Automotive Engine</span>
    </div>
  );
}

const Icono = ({ d, size = 15 }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
    <path d={d} />
  </svg>
);

export default function Login({
  onLogin,
  onGoRegister,
  onGoRegistroNegocio,
  onGoAdminLogin,
  onVolver,
}) {
  const [correo, setCorreo] = useState("");
  const [password, setPassword] = useState("");
  const [verClave, setVerClave] = useState(false);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const googleBtnRef = useRef(null);

  const handleGoogleResponse = async (response) => {
    try {
      setLoading(true);
      setError(null);
      const data = await googleLogin(response.credential);
      if (data && data.token) onLogin(data);
    } catch (err) {
      setError(err.message || "Error al iniciar sesión con Google");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const pintarBoton = () => {
      if (!window.google?.accounts?.id || !googleBtnRef.current) return;
      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: handleGoogleResponse,
      });
      window.google.accounts.id.renderButton(googleBtnRef.current, {
        theme: "outline",
        size: "large",
        width: 280,
        text: "continue_with",
        color_scheme:
          document.documentElement.getAttribute("data-theme") === "light"
            ? "light"
            : "dark",
      });
    };

    if (window.google?.accounts?.id) {
      pintarBoton();
      return;
    }

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.onload = pintarBoton;
    document.body.appendChild(script);

    return () => {
      try {
        window.google?.accounts?.id?.cancel();
      } catch {
        /* ignore */
      }
      if (script.parentNode) script.parentNode.removeChild(script);
    };
  }, []);

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
      <aside className="auth-aside">
        <div className="auth-aside__photo" aria-hidden="true" />

        <Marca />

        <div className="auth-aside__body">
          <span className="auth-eyebrow">Tu cuenta</span>
          <h2 className="auth-aside__title">
            Tu garaje
            <br />
            digital.
          </h2>
          <p className="auth-aside__lead">
            Tus vehículos, tus repuestos y tu próximo paso. En un solo lugar.
          </p>

          <ul className="auth-points">
            <li>
              <Icono d="M20 6 9 17l-5-5" />
              Compatibilidad verificada con tu vehículo
            </li>
            <li>
              <Icono d="M20 6 9 17l-5-5" />
              Cotizaciones y solicitudes en un clic
            </li>
            <li>
              <Icono d="M20 6 9 17l-5-5" />
              Originales y alternativas siempre comparados
            </li>
          </ul>
        </div>

        <div className="auth-aside__foot">
          <span className="auth-aside__caption">
            <span className="auth-aside__caption-dot" />
            Catálogo · Vehículo · Solicitudes
          </span>
          <span className="auth-aside__signoff">
            Precisión. Confianza. Rendimiento.
          </span>
        </div>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <div className="auth-card__brand">
            <Marca />
          </div>

          <h2>Iniciar sesión</h2>
          <p className="auth-subtitle">
            Ingresa con tu correo o con Google para continuar.
          </p>

          <form onSubmit={handleSubmit}>
            <label htmlFor="auth-correo">
              Correo electrónico
              <div className="input-wrapper">
                <span className="input-icon">
                  <svg viewBox="0 0 24 24">
                    <rect x="2" y="4" width="20" height="16" rx="2" />
                    <polyline points="2,4 12,13 22,4" />
                  </svg>
                </span>
                <input
                  id="auth-correo"
                  type="email"
                  value={correo}
                  onChange={(e) => setCorreo(e.target.value)}
                  placeholder="correo@ejemplo.com"
                  autoComplete="email"
                  required
                />
              </div>
            </label>

            <label htmlFor="auth-password">
              Contraseña
              <div className="input-wrapper">
                <span className="input-icon">
                  <svg viewBox="0 0 24 24">
                    <rect x="3" y="11" width="18" height="11" rx="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                </span>
                <input
                  id="auth-password"
                  type={verClave ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Mínimo 6 caracteres"
                  autoComplete="current-password"
                  minLength={6}
                  required
                />
                <button
                  type="button"
                  className="input-toggle"
                  onClick={() => setVerClave((v) => !v)}
                  aria-label={verClave ? "Ocultar contraseña" : "Mostrar contraseña"}
                  aria-pressed={verClave}
                >
                  <svg viewBox="0 0 24 24">
                    {verClave ? (
                      <>
                        <path d="M9.9 5.2A9.8 9.8 0 0 1 12 5c5 0 9 4.5 10 7-.4 1-1.6 2.6-3.2 3.9" />
                        <path d="M6.2 6.6C3.9 8.2 2.2 10.4 2 12c1 2.5 5 7 10 7 1.7 0 3.3-.5 4.7-1.3" />
                        <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
                        <line x1="3" y1="3" x2="21" y2="21" />
                      </>
                    ) : (
                      <>
                        <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z" />
                        <circle cx="12" cy="12" r="3" />
                      </>
                    )}
                  </svg>
                </button>
              </div>
            </label>

            {error && (
              <p className="auth-error" role="alert">
                <svg viewBox="0 0 24 24">
                  <circle cx="12" cy="12" r="9" />
                  <line x1="12" y1="7.5" x2="12" y2="13" />
                  <line x1="12" y1="16.5" x2="12" y2="16.6" />
                </svg>
                {error}
              </p>
            )}

            <button type="submit" disabled={loading}>
              {loading ? "Ingresando..." : "Iniciar sesión"}
              {!loading && (
                <svg viewBox="0 0 24 24">
                  <line x1="4" y1="12" x2="19" y2="12" />
                  <polyline points="13 6 19 12 13 18" />
                </svg>
              )}
            </button>
          </form>

          <div className="auth-divider">o continúa con</div>

          <div ref={googleBtnRef} className="auth-google-btn" />

          <p className="auth-footer">
            ¿No tienes cuenta?{" "}
            <button type="button" className="link-button" onClick={onGoRegister}>
              Regístrate
            </button>
          </p>

          {onGoRegistroNegocio && (
            <p className="auth-footer auth-footer--negocio">
              ¿Tienes un taller o una tienda de autopartes?{" "}
              <button type="button" className="link-button" onClick={onGoRegistroNegocio}>
                Registra tu negocio
              </button>
            </p>
          )}

          {onGoAdminLogin && (
            <button
              type="button"
              className="auth-interno-btn"
              onClick={onGoAdminLogin}
            >
              <svg viewBox="0 0 24 24">
                <rect x="3" y="11" width="18" height="11" rx="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              Acceso interno
            </button>
          )}

          {onVolver && (
            <button type="button" className="auth-back" onClick={onVolver}>
              <svg viewBox="0 0 24 24">
                <line x1="20" y1="12" x2="5" y2="12" />
                <polyline points="11 18 5 12 11 6" />
              </svg>
              Volver al catálogo
            </button>
          )}

          <p className="auth-version">© 2026 DriveMaster</p>
        </div>
      </main>
    </div>
  );
}