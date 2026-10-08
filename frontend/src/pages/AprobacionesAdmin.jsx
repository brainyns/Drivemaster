import { useCallback, useEffect, useState } from "react";
import { negociosPendientes } from "../services/plataformaService";
import { resolverAprobacionTaller } from "../services/tallerService";
import { resolverAprobacionTienda } from "../services/tiendaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";

const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

/**
 * Aprobaciones del ADMIN: solo los negocios pendientes y dos decisiones, aprobar o rechazar (con motivo). El plan,
 * la calificación y la actividad de los negocios, y pausarlos o cambiarles el plan, son del SUPERADMIN. Un negocio
 * aprobado aquí arranca con el plan de prueba.
 */
export default function AprobacionesAdmin({ token }) {
  const [estado, setEstado] = useState({ lista: null, error: null });
  const [rechazando, setRechazando] = useState(null);
  const [motivo, setMotivo] = useState("");
  const [procesando, setProcesando] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);

  const cargar = useCallback(() => {
    negociosPendientes(token)
      .then((lista) => setEstado({ lista, error: null }))
      .catch((e) => setEstado((p) => ({ ...p, error: e.message })));
  }, [token]);

  useEffect(() => {
    const t = setTimeout(cargar, 0);
    return () => clearTimeout(t);
  }, [cargar]);

  const clave = (n) => `${n.tipo}-${n.id}`;
  const llamar = (n) => (n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda);

  const ejecutar = async (n, decision, mensaje) => {
    setProcesando(clave(n));
    setErrorAccion(null);
    setAviso(null);
    try {
      await llamar(n)(token, n.id, decision);
      setAviso(mensaje);
      setRechazando(null);
      setMotivo("");
      cargar();
    } catch (e) {
      setErrorAccion(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const { lista, error } = estado;

  return (
    <div className="db-root dp-root">
      <header className="db-header">
        <div>
          <h1 className="db-h1">Aprobaciones</h1>
          <p className="db-sub">Negocios que esperan tu revisión. Aprobarlos los activa con el plan de prueba; al rechazarlos se les envía el motivo.</p>
        </div>
        {lista && <span className="db-date">{lista.length} {lista.length === 1 ? "pendiente" : "pendientes"}</span>}
      </header>

      {aviso && <div className="dp-aviso" role="status">{aviso}</div>}
      {(error || errorAccion) && <div className="db-error" role="alert">{errorAccion || error}</div>}

      {!lista && !error ? (
        <div className="db-loading"><div className="db-spinner" /> Cargando…</div>
      ) : lista && lista.length === 0 ? (
        <p className="dp-alertas__ok">Todo al día: no hay negocios pendientes de revisión.</p>
      ) : lista && (
        <div className="dp-tareas">
          {lista.map((n) => {
            const k = clave(n);
            return (
              <article key={k} className="dp-tarea">
                <p className="dp-sub-celda">{n.tipo === "TIENDA" ? "Tienda" : "Taller"} · registrada {fecha(n.registrado)}</p>
                <h2 className="dp-tarea__titulo">{n.nombre || "Sin nombre"}</h2>
                <p className="dp-tarea__detalle">
                  {[n.ciudad, n.direccion].filter(Boolean).join(" · ") || "Sin ubicación"}
                  {n.nit ? ` · NIT ${n.nit}` : ""}
                </p>
                {n.datosPorCompletar.length > 0 && (
                  <p className="dp-etiqueta dp-etiqueta--alerta">Falta: {n.datosPorCompletar.join(", ")}</p>
                )}
                {rechazando === k ? (
                  <>
                    <label className="dp-campo">
                      <span>Motivo (se le envía al negocio)</span>
                      <input value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={300} />
                    </label>
                    <div className="dp-acciones-fila">
                      <button type="button" className="dp-btn" disabled={procesando === k}
                        onClick={() => (motivo.trim()
                          ? ejecutar(n, { aprobado: false, motivo: motivo.trim() }, `${n.nombre} fue rechazado.`)
                          : setErrorAccion("Escribe el motivo del rechazo: se le enviará al negocio."))}>
                        Confirmar rechazo
                      </button>
                      <button type="button" className="dp-btn dp-btn--sutil" onClick={() => setRechazando(null)}>Cancelar</button>
                    </div>
                  </>
                ) : (
                  <div className="dp-acciones-fila">
                    <button type="button" className="dp-btn" disabled={procesando === k}
                      onClick={() => ejecutar(n, { aprobado: true }, `${n.nombre} quedó aprobado.`)}>Aprobar</button>
                    <button type="button" className="dp-btn dp-btn--sutil" onClick={() => { setRechazando(k); setErrorAccion(null); }}>Rechazar…</button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
