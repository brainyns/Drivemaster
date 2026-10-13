import { useEffect, useMemo, useState } from "react";
import { getToken } from "../../services/authService";
import { cotizacionesDelTaller } from "../../services/cotizacionService";
import CotizacionDetalle from "../../components/servicio/CotizacionDetalle";
import Etiqueta from "../../components/servicio/Etiqueta";
import { ESTADOS_COTIZACION, formatFechaHora, formatPrecio } from "../../components/servicio/estadosServicio";
import { FILTROS, VACIO, vencida, contarPorFiltro, filtrar } from "./filtrosCotizaciones";
import "../../css/servicios.css";
import "../../css/solicitud-servicio.css";
import "../../css/cotizacion.css";

function diasRestantes(fecha, ahora) {
  const d = Math.ceil((new Date(fecha).getTime() - ahora) / 86400000);
  if (d < 0) return `venció hace ${-d} d`;
  return d === 0 ? "vence hoy" : `vence en ${d} d`;
}

/** Cotizaciones del taller en una sola lista: emitidas, aprobadas, rechazadas y vencidas. */
export default function TallerCotizaciones() {
  const token = getToken();
  const [cotizaciones, setCotizaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [filtro, setFiltro] = useState("emitidas");
  const [abierta, setAbierta] = useState(null);
  const [ahora] = useState(() => Date.now());

  useEffect(() => {
    let cancelado = false;
    cotizacionesDelTaller(token)
      .then((c) => { if (!cancelado) setCotizaciones(c || []); })
      .catch((e) => { if (!cancelado) setError(e.message); })
      .finally(() => { if (!cancelado) setCargando(false); });
    return () => { cancelado = true; };
  }, [token]);

  const conteo = useMemo(() => contarPorFiltro(cotizaciones, ahora), [cotizaciones, ahora]);
  const visibles = filtrar(cotizaciones, filtro, ahora);
  const pendienteTotal = cotizaciones
    .filter((c) => c.estado === "EMITIDA" && !vencida(c, ahora))
    .reduce((s, c) => s + (c.total || 0), 0);

  return (
    <div className="ss-panel tp-cotizaciones">
      <header className="pt-header tp-cabecera">
        <div>
          <h1 className="pt-title">Cotizaciones</h1>
          <p className="pt-subtitle">
            {conteo.emitidas > 0
              ? `${conteo.emitidas} esperando respuesta del cliente, por ${formatPrecio(pendienteTotal)}.`
              : "Lo que cotizaste, lo que aprobaron, lo que rechazaron y lo que se venció."}
          </p>
        </div>
      </header>

      {error && <p className="ss-alerta" role="alert">{error}</p>}

      <nav className="ss-filtros" aria-label="Cotizaciones por estado">
        {FILTROS.map((f) => (
          <button key={f.key} className={`ss-filtro${filtro === f.key ? " ss-filtro--activo" : ""}`}
            aria-current={filtro === f.key ? "page" : undefined} onClick={() => { setFiltro(f.key); setAbierta(null); }}>
            {f.label}
            {conteo[f.key] > 0 && <span className="ss-filtro__conteo">{conteo[f.key]}</span>}
          </button>
        ))}
      </nav>

      {cargando ? (
        <p className="sv-empty">Cargando cotizaciones...</p>
      ) : visibles.length === 0 ? (
        <p className="sv-empty">{VACIO[filtro]}</p>
      ) : (
        <ul className="tp-cotiz">
          {visibles.map((c) => {
            const abierto = abierta === c.id;
            const rechazada = c.estado === "RECHAZADA";
            return (
              <li key={c.id} className="tp-cotiz__item">
                <button className="tp-cotiz__fila" aria-expanded={abierto} onClick={() => setAbierta(abierto ? null : c.id)}>
                  <span className="tp-cotiz__vehiculo">
                    <strong>{c.vehiculoDescripcion || "Vehículo"}</strong>
                    <span className="ss-meta">
                      {rechazada
                        ? <>v{c.version} · rechazada {c.fechaRespuesta ? formatFechaHora(c.fechaRespuesta) : "(sin fecha)"}</>
                        : <>v{c.version} · emitida {formatFechaHora(c.fechaEmision)}</>}
                      {c.vigenciaHasta && c.estado === "EMITIDA" && ` · ${diasRestantes(c.vigenciaHasta, ahora)}`}
                    </span>
                    {rechazada && c.motivoRespuesta && (
                      <span className="tp-cotiz__motivo">Motivo del cliente: “{c.motivoRespuesta}”</span>
                    )}
                  </span>
                  <span className="tp-cotiz__total">{formatPrecio(c.total)}</span>
                  <Etiqueta estado={vencida(c, ahora) ? "VENCIDA" : c.estado} catalogo={ESTADOS_COTIZACION} />
                </button>
                {abierto && <div className="tp-cotiz__detalle"><CotizacionDetalle cotizacion={c} mostrarComision sinEstado /></div>}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
