import { useState, useEffect, useRef } from "react";
import { login, googleLogin, getUser } from "../services/authService";
import Logo from "../components/Logo";
import Campo from "../components/ui/Campo";
import CampoClave from "../components/ui/CampoClave";
import Alerta from "../components/ui/Alerta";
import { MENSAJE_SIN_CONEXION } from "../services/conexion";
import "../css/Login.css";

const CORREO_VALIDO = /^\S+@\S+\.\S+$/;

/** Errores del formulario, en el orden en que se recorren con el foco. */
function validarLogin(correo, password) {
  const e = {};
  if (!correo.trim()) e.correo = "Escribe tu correo.";
  else if (!CORREO_VALIDO.test(correo.trim())) e.correo = "Revisa el correo: debe tener la forma nombre@dominio.com.";
  if (!password) e.password = "Escribe tu contraseña.";
  return e;
}

// Debe ser el mismo GOOGLE_CLIENT_ID del backend, y el dominio del frontend debe estar autorizado en Google Cloud.
const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || "571035780087-mh3obhajnmvpj98cre938rmuhmqb0cf5.apps.googleusercontent.com";

function Marca({ onInicio }) {
  return (
    <div className="auth-brand">
      <Logo size="lg" onClick={onInicio} />
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
  // Los errores en línea aparecen tras el primer intento y se actualizan al corregir
  const [intentado, setIntentado] = useState(false);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [fotoOk, setFotoOk] = useState(true);
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

  const errores = intentado ? validarLogin(correo, password) : {};

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setIntentado(true);
    const faltan = validarLogin(correo, password);
    if (Object.keys(faltan).length) {
      document.getElementById(faltan.correo ? "auth-correo" : "auth-password")?.focus();
      return;
    }
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
        {/* La foto carga primero (es lo primero que se ve); si falla, queda el color de fondo del panel */}
        <picture className="auth-aside__photo" aria-hidden="true">
          {fotoOk && (
            <>
              <source type="image/webp" sizes="(max-width: 980px) 100vw, 50vw"
                srcSet="/images/drivemaster-hero-800.webp 800w, /images/drivemaster-hero-1440.webp 1440w" />
              <img src="/images/drivemaster-hero.jpg" alt="" width="1800" height="1200"
                fetchPriority="high" loading="eager" decoding="async" onError={() => setFotoOk(false)} />
            </>
          )}
        </picture>

        <div className="auth-aside__body">
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
            <Marca onInicio={onVolver} />
          </div>

          <h1>Iniciar sesión</h1>
          <p className="auth-subtitle">
            Ingresa con tu correo o con Google para continuar.
          </p>

          <form onSubmit={handleSubmit} noValidate>
            <Campo id="auth-correo" label="Correo electrónico" error={errores.correo}>
              <input
                className="ui-field__input"
                type="email"
                inputMode="email"
                value={correo}
                onChange={(e) => setCorreo(e.target.value)}
                placeholder="correo@ejemplo.com"
                autoComplete="email"
                autoCapitalize="none"
                spellCheck={false}
              />
            </Campo>

            <Campo id="auth-password" label="Contraseña" error={errores.password}>
              <CampoClave
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </Campo>

            {/* Sin conexión (servidor dormido o sin red): se puede reintentar sin volver a escribir */}
            {error && <Alerta onReintentar={error === MENSAJE_SIN_CONEXION ? () => handleSubmit({ preventDefault() {} }) : undefined} reintentando={loading}>{error}</Alerta>}

            <button type="submit" className="ui-btn ui-btn--primario ui-btn--grande ui-btn--bloque auth-enviar"
              disabled={loading} aria-busy={loading}>
              {loading ? "Ingresando…" : "Iniciar sesión"}
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

          {/* "Registra tu negocio" solo sin sesión, como en el navbar y el pie */}
          {onGoRegistroNegocio && !getUser() && (
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