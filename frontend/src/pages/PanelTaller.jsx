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
import TallerAgenda from "./taller/TallerAgenda";
import OrdenesAnteriores from "./taller/OrdenesAnteriores";
import { destinoSeccion, ordenesPorAtender } from "./taller/resumenTaller";
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
  const [errorTaller, setErrorTaller] = useState(null);

  const cargar = useCallback(async () => {
    setError(null);
    // Cada fuente falla por separado: una caída no deja el panel en blanco.
    const [t, s, o] = await Promise.allSettled([
      miTaller(token), bandejaSolicitudesTaller(token), ordenesDelTaller(token),
    ]);
    if (t.status === "fulfilled") setTaller(t.value);
    setErrorTaller(t.status === "rejected" ? t.reason?.message || "No se pudieron cargar los datos del taller." : null);
    if (s.status === "fulfilled") setSolicitudes(s.value || []);
    else setError(s.reason?.message || "No se pudieron cargar las solicitudes.");
    if (o.status === "fulfilled") setOrdenes(o.value || []);
    setCargando(false);
  }, [token]);

  // Solo la bandeja: tras una acción en Solicitudes o Trabajos no hace falta pedir de nuevo el taller ni las órdenes
  const recargarSolicitudes = useCallback(async () => {
    try {
      setSolicitudes((await bandejaSolicitudesTaller(token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message || "No se pudieron cargar las solicitudes.");
    }
  }, [token]);

  /** Una solicitud actualizada por una acción reemplaza la suya en la bandeja, sin recargar. */
  const reemplazarSolicitud = useCallback((actualizada) =>
    setSolicitudes((prev) => prev.map((s) => (s.id === actualizada.id ? { ...s, ...actualizada } : s))), []);

  // La bandeja y el taller se cargan aquí una vez y se pasan a cada sección (Solicitudes y Trabajos no los piden de nuevo)
  const bandeja = { solicitudes, taller, cargandoBandeja: cargando, errorBandeja: error,
    onRecargar: recargarSolicitudes, onReemplazar: reemplazarSolicitud };

  // Se recarga al cambiar de sección para que contadores y avisos no queden viejos.
  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar, seccion]);

  const destino = destinoSeccion(param);
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
            cargando={cargando} error={error} onRecargar={cargar} irA={irA}
          />
        )}
        {seccion === "solicitudes" && (
          <TallerSolicitudes key={`${destino.filtro || "responder"}-${destino.abrir || ""}`} filtroInicial={destino.filtro}
            abrirInicial={destino.abrir} onVolver={destino.volverA ? () => irA(destino.volverA) : undefined}
            textoAfuera={destino.volverA === "taller-agenda" ? "Agenda" : "Inicio"} {...bandeja} />
        )}
        {seccion === "trabajos" && (
          <TallerSolicitudes key={`${destino.filtro || "repuestos"}-${destino.abrir || ""}`} modo="trabajos" filtroInicial={destino.filtro}
            abrirInicial={destino.abrir} onVolver={destino.volverA ? () => irA(destino.volverA) : undefined}
            textoAfuera={destino.volverA === "taller-cotizaciones" ? "Cotizaciones" : "Inicio"} {...bandeja} />
        )}
        {seccion === "agenda" && <TallerAgenda taller={taller} irA={irA} />}
        {seccion === "cotizaciones" && <TallerCotizaciones irA={irA} />}
        {seccion === "informes" && <TallerInformes />}
        {seccion === "mitaller" && (
          <TallerMiTaller taller={taller} token={token} cargando={cargando} error={errorTaller} abrirSeccion={destino.filtro}
            onRecargar={cargar} onActualizado={setTaller} />
        )}
        {seccion === "anteriores" && (
          <OrdenesAnteriores ordenes={ordenes} cargando={cargando} error={error} onRecargar={cargar} />
        )}
      </div>
    </EstadoNegocio>
  );
}
