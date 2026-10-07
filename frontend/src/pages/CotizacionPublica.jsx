import { useEffect, useState } from "react";
import { consultarCotizacionPublica, responderCotizacionPublica } from "../services/cotizacionService";
import CotizacionDetalle from "../components/servicio/CotizacionDetalle";
import RespuestaCotizacion from "../components/servicio/RespuestaCotizacion";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";

const ACCIONES = { aprobar: "APROBAR", rechazar: "RECHAZAR", cambios: "CAMBIOS" };

const RESULTADO = {
  APROBAR:  "¡Cotización aprobada! Para pagar inicia sesión y entra a “Mis servicios”.",
  CAMBIOS:  "Enviamos tu comentario al taller. Te llegará una nueva cotización por correo.",
  RECHAZAR: "Registramos que rechazaste la cotización. La solicitud quedó cerrada.",
};

/**
 * Lee el token del fragmento (#token=…&accion=…). El fragmento nunca llega a un
 * servidor; al cargar se borra de la barra de direcciones. El token solo vive en
 * memoria: no se guarda en localStorage ni se escribe en consola.
 */
function leerFragmento() {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ""));
  return {
    token: params.get("token") || "",
    accion: ACCIONES[(params.get("accion") || "").toLowerCase()] || null,
  };
}

/** Página pública del enlace del correo: ver la cotización y responder sin iniciar sesión. */
export default function CotizacionPublica({ onIrServicios, onIrInicio }) {
  const [{ token, accion }] = useState(leerFragmento);
  const [cotizacion, setCotizacion] = useState(null);
  const [estado, setEstado] = useState(token ? "CARGANDO" : "INVALIDO"); // CARGANDO | LISTA | INVALIDO | RESPONDIDA
  const [respuesta, setRespuesta] = useState(null);

  useEffect(() => {
    // Quita el token de la URL (y del historial) en cuanto se lee
    if (window.location.hash) window.history.replaceState(null, "", window.location.pathname);
  }, []);

  useEffect(() => {
    if (!token) return;
    let cancelado = false;
    consultarCotizacionPublica(token)
      .then((c) => { if (!cancelado) { setCotizacion(c); setEstado("LISTA"); } })
      .catch(() => { if (!cancelado) setEstado("INVALIDO"); });
    return () => { cancelado = true; };
  }, [token]);

  const responder = async (a, motivo) => {
    const c = await responderCotizacionPublica(token, a, motivo);
    setCotizacion(c);
    setRespuesta(a);
    setEstado("RESPONDIDA");
  };

  return (
    <div className="ct-publica">
      <div className="ct-publica__caja">
        <div className="ct-publica__marca">Drive<span>Master</span></div>

        {estado === "CARGANDO" && <p className="sv-empty">Cargando tu cotización...</p>}

        {estado === "INVALIDO" && (
          <section className="ss-bloque">
            <h1 className="ss-bloque__titulo">Enlace no válido o vencido</h1>
            <p className="ss-texto">
              El enlace ya se usó, venció o no es correcto. Si tienes cuenta, puedes ver tus cotizaciones en “Mis servicios”.
            </p>
            <div className="ss-acciones">
              <button className="sv-btn" onClick={onIrServicios}>Ir a mis servicios</button>
              <button className="sv-btn-outline" onClick={onIrInicio}>Ir al inicio</button>
            </div>
          </section>
        )}

        {cotizacion && estado !== "INVALIDO" && (
          <section className="ss-bloque">
            <h1 className="ss-bloque__titulo">Cotización para tu {cotizacion.vehiculoDescripcion}</h1>
            <CotizacionDetalle cotizacion={cotizacion} />

            {estado === "LISTA" && (cotizacion.puedeResponder ? (
              <RespuestaCotizacion onResponder={responder} accionInicial={accion} />
            ) : (
              <p className="ss-aviso">Esta cotización ya no se puede responder desde el correo.</p>
            ))}

            {estado === "RESPONDIDA" && (
              <>
                <p className="ss-exito" role="status">{RESULTADO[respuesta]}</p>
                {respuesta === "APROBAR" && (
                  <div className="ss-acciones">
                    <button className="sv-btn" onClick={onIrServicios}>Iniciar sesión y pagar</button>
                  </div>
                )}
              </>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
