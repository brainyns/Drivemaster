import { useCallback, useEffect, useState } from "react";
import { operacionPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";

const REFRESCO_MS = 60000;

/**
 * Inicio del ADMIN: solo lo que le toca atender. Sin dinero ni datos de la plataforma (eso es del SUPERADMIN).
 */
export default function DashboardOperacion({ token, onIr }) {
  const [estado, setEstado] = useState({ datos: null, error: null });
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    operacionPlataforma(token)
      .then((datos) => setEstado({ datos, error: null }))
      .catch((e) => setEstado((previo) => ({ datos: previo.datos, error: e.message })))
      .finally(() => setCargando(false));
  }, [token]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const intervalo = setInterval(cargar, REFRESCO_MS);
    return () => { clearTimeout(inicial); clearInterval(intervalo); };
  }, [cargar]);

  const { datos: d, error } = estado;

  if (!d) {
    return (
      <div className="db-root">
        {error ? <div className="db-error" role="alert">{error}</div>
          : <div className="db-loading"><div className="db-spinner" /> Cargando tu resumen…</div>}
      </div>
    );
  }

  const porAprobar = d.tiendasPorAprobar + d.talleresPorAprobar;
  const tareas = [
    {
      n: porAprobar, titulo: "Negocios por aprobar",
      detalle: `${d.tiendasPorAprobar} tienda(s) y ${d.talleresPorAprobar} taller(es) esperan tu revisión.`,
      ir: "aprobaciones", accion: "Revisar aprobaciones",
    },
    {
      n: d.problemasEnvioAbiertos, titulo: "Problemas de envío abiertos",
      detalle: "Clientes que reportaron que un pedido no llegó o llegó mal. Decide si se reembolsa o se confirma la entrega.",
      ir: "problemas-envios", accion: "Resolver problemas", critica: true,
    },
    {
      n: d.despachosAtrasados, titulo: "Pedidos atrasados",
      detalle: "Pedidos pagados que la tienda no despachó dentro del plazo. Contacta a la tienda.",
      critica: true,
    },
  ];

  return (
    <div className="db-root dp-root">
      <header className="db-header">
        <div>
          <h1 className="db-h1">Tu resumen</h1>
          <p className="db-sub">Lo que hay que atender hoy en el marketplace.</p>
        </div>
        <div className="db-header-right dp-acciones">
          <span className="db-date">
            Actualizado {new Date(d.generadoEn).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" })}
          </span>
          <button type="button" className="dp-btn" onClick={cargar} disabled={cargando}>
            {cargando ? "Actualizando…" : "Actualizar"}
          </button>
        </div>
      </header>

      {error && <div className="db-error" role="alert">{error} (se muestran los últimos datos)</div>}

      {tareas.every((t) => t.n === 0) && (
        <p className="dp-alertas__ok">Todo al día: no hay negocios por aprobar, problemas de envío ni pedidos atrasados.</p>
      )}

      <div className="dp-tareas">
        {tareas.map((t) => (
          <article key={t.titulo} className={`dp-tarea${t.n > 0 && t.critica ? " dp-tarea--critica" : ""}`}>
            <p className="dp-tarea__n">{t.n}</p>
            <h2 className="dp-tarea__titulo">{t.titulo}</h2>
            <p className="dp-tarea__detalle">{t.detalle}</p>
            {t.ir && t.n > 0 && (
              <button type="button" className="dp-btn" onClick={() => onIr?.(t.ir)}>{t.accion}</button>
            )}
          </article>
        ))}
      </div>
    </div>
  );
}
