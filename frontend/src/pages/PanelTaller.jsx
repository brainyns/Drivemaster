import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { miTaller } from "../services/tallerService";
import { bandejaSolicitudesTaller } from "../services/solicitudServicioService";
import { ordenesDelTaller } from "../services/ordenServicioService";
import EstadoNegocio from "../components/EstadoNegocio";
import TallerSolicitudes from "./TallerSolicitudes";
import TallerInicio from "./taller/TallerInicio";
import TallerMiTaller from "./taller/TallerMiTaller";
import TallerCotizaciones from "./taller/TallerCotizaciones";
import TallerInformes from "./taller/TallerInformes";
import OrdenesAnteriores from "./taller/OrdenesAnteriores";
import { ordenesPorAtender } from "./taller/resumenTaller";
import "../css/servicios.css";
import "../css/panel-taller.css";
import "../css/taller-panel.css";

/**
 * Contenedor del panel del taller: carga lo común (datos del taller, solicitudes y órdenes del
 * flujo anterior) y muestra la sección que pide el menú lateral. También avisa al menú cuántas
 * solicitudes esperan respuesta y si quedan órdenes anteriores por atender.
 */
export default function PanelTaller({ seccion = "inicio", param = null, irA, onResumen }) {
  const token = getToken();
  const [taller, setTaller] = useState(null);
  const [solicitudes, setSolicitudes] = useState([]);
  const [ordenes, setOrdenes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  const cargar = useCallback(async () => {
    setError(null);
    // Cada fuente falla por separado: una caída no deja el panel en blanco.
    const [t, s, o] = await Promise.allSettled([
      miTaller(token), bandejaSolicitudesTaller(token), ordenesDelTaller(token),
    ]);
    if (t.status === "fulfilled") setTaller(t.value);
    if (s.status === "fulfilled") setSolicitudes(s.value || []);
    else setError(s.reason?.message || "No se pudieron cargar las solicitudes.");
    if (o.status === "fulfilled") setOrdenes(o.value || []);
    setCargando(false);
  }, [token]);

  // Se recarga al cambiar de sección para que contadores y avisos no queden viejos.
  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar, seccion]);

  const anteriores = useMemo(() => ordenesPorAtender(ordenes), [ordenes]);
  const porResponder = useMemo(
    () => solicitudes.filter((s) => s.estado === "ASIGNADA_A_TALLER" && !s.aceptadaPorTaller).length,
    [solicitudes]
  );

  useEffect(() => {
    if (onResumen) onResumen({ anteriores: anteriores.length, porResponder });
  }, [onResumen, anteriores.length, porResponder]);

  // La sección temporal desaparece sola cuando ya no queda nada por atender
  useEffect(() => {
    if (seccion === "anteriores" && !cargando && anteriores.length === 0) irA("taller-inicio");
  }, [seccion, cargando, anteriores.length, irA]);

  return (
    <EstadoNegocio negocio={taller} tipo="taller">
      <div className="pt-root tp-root">
        {seccion === "inicio" && (
          <TallerInicio
            taller={taller} solicitudes={solicitudes} anteriores={anteriores}
            cargando={cargando} error={error} irA={irA}
          />
        )}
        {seccion === "solicitudes" && (
          <TallerSolicitudes key={param || "responder"} filtroInicial={param || undefined} />
        )}
        {seccion === "trabajos" && (
          <TallerSolicitudes key={param || "repuestos"} modo="trabajos" filtroInicial={param || undefined} />
        )}
        {seccion === "cotizaciones" && <TallerCotizaciones />}
        {seccion === "informes" && <TallerInformes />}
        {seccion === "mitaller" && (
          <TallerMiTaller taller={taller} ordenes={ordenes} token={token} onActualizado={setTaller} />
        )}
        {seccion === "anteriores" && (
          <OrdenesAnteriores ordenes={ordenes} cargando={cargando} error={error} onRecargar={cargar} />
        )}
      </div>
    </EstadoNegocio>
  );
}
