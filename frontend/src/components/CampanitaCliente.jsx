import { useState } from "react";
import NotificationDropdown from "./NotificationDropdown";
import useNoLeidas from "../hooks/useNoLeidas";
import { textoContador } from "../services/notificacionesUtil";
import { getUser } from "../services/authService";

/**
 * Campanita del cliente en la cabecera pública. Solo muestra sus propios avisos (el servidor
 * filtra por la sesión) y cada uno abre su solicitud o su compra: onAbrir(pagina, id).
 * `variante`: "publica" (cabecera del sitio) o "panel" (dentro de una página como Mis servicios).
 */
export default function CampanitaCliente({ onAbrir, variante = "publica" }) {
  const esCliente = getUser()?.rol === "CLIENTE";
  const [abierta, setAbierta] = useState(false);
  const { noLeidas, setNoLeidas, latido } = useNoLeidas(esCliente);

  if (!esCliente) return null;

  const claseBoton = variante === "publica"
    ? `dm-header__cart nt-campanita__boton${abierta ? " nt-campanita__boton--activo" : ""}`
    : `tb-ico-btn${abierta ? " tb-ico-btn--active" : ""}`;

  return (
    <div className={`nt-campanita nt-campanita--${variante}`}>
      <button
        type="button"
        className={claseBoton}
        title="Notificaciones"
        aria-label={noLeidas > 0 ? `Notificaciones, ${noLeidas} sin leer` : "Notificaciones"}
        aria-expanded={abierta}
        onClick={() => setAbierta((v) => !v)}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 01-3.46 0"/>
        </svg>
      </button>
      {noLeidas > 0 && (
        <span key={latido} className={`tb-notif-badge${latido > 0 ? " tb-notif-badge--nuevo" : ""}`} aria-hidden="true">
          {textoContador(noLeidas)}
        </span>
      )}
      {abierta && (
        <NotificationDropdown
          rol="CLIENTE"
          variante={variante}
          onClose={() => setAbierta(false)}
          onNavigate={(pagina, id) => onAbrir?.(pagina, id)}
          onNoLeidas={setNoLeidas}
        />
      )}
    </div>
  );
}
