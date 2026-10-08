import { useEffect, useMemo, useState } from "react";
import { getToken } from "../../services/authService";
import { cotizacionesDelTaller } from "../../services/cotizacionService";
import CotizacionDetalle from "../../components/servicio/CotizacionDetalle";
import Etiqueta from "../../components/servicio/Etiqueta";
import { ESTADOS_COTIZACION, formatFechaHora, formatPrecio } from "../../components/servicio/estadosServicio";
import "../../css/servicios.css";
import "../../css/solicitud-servicio.css";
import "../../css/cotizacion.css";

// Una cotización emitida cuya vigencia ya pasó cuenta como vencida aunque el sistema aún no la haya cerrado.
const vencida = (c, ahora) =>
  c.estado === "VENCIDA" || (c.estado === "EMITIDA" && c.vigenciaHasta && new Date(c.vigenciaHasta).getTime() < ahora);

const FILTROS = [
  { key: "emitidas", label: "Emitidas", filtro: (c, ahora) => c.estado === "EMITIDA" && !vencida(c, ahora) },
  { key: "aprobadas", label: "Aprobadas", filtro: (c) => c.estado === "APROBADA" },
  { key: "vencidas", label: "Vencidas", filtro: vencida },
];

const VACIO = {
  emitidas: "No tienes cotizaciones esperando respuesta. Las emites desde una solicitud diagnosticada, en Solicitudes.",
  aprobadas: "Aún ninguna cotización aprobada. Cuando el cliente apruebe y pague, el trabajo pasa a Trabajos.",
  vencidas: "No tienes cotizaciones vencidas.",
};

function diasRestantes(fecha, ahora) {
  const d = Math.ceil((new Date(fecha).getTime() - ahora) / 86400000);
  if (d < 0) return `venció hace ${-d} d`;
  return d === 0 ? "vence hoy" : `vence en ${d} d`;
}

/** Cotizaciones del taller en una sola lista: emitidas, aprobadas y vencidas. */
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

  const conteo = useMemo(
    () => Object.fromEntries(FILTROS.map((f) => [f.key, cotizaciones.filter((c) => f.filtro(c, ahora)).length])),
    [cotizaciones, ahora]
  );
  const visibles = cotizaciones.filter((c) => FILTROS.find((f) => f.key === filtro).filtro(c, ahora));
  const pendienteTotal = cotizaciones
    .filter((c) => c.estado === "EMITIDA" && !vencida(c, ahora))
    .reduce((s, c) => s + (c.total || 0), 0);

  return (
    <div className="ss-panel tp-cotizaciones">
      <header className="pt-header tp-cabecera">
        <div>
          <span className="pt-eyebrow">Taller aliado</span>
          <h1 className="pt-title">Cotizaciones</h1>
          <p className="pt-subtitle">
            {conteo.emitidas > 0
              ? `${conteo.emitidas} esperando respuesta del cliente, por ${formatPrecio(pendienteTotal)}.`
              : "Lo que cotizaste, lo que aprobaron y lo que se venció."}
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
            return (
              <li key={c.id} className="tp-cotiz__item">
                <button className="tp-cotiz__fila" aria-expanded={abierto} onClick={() => setAbierta(abierto ? null : c.id)}>
                  <span className="tp-cotiz__vehiculo">
                    <strong>{c.vehiculoDescripcion || "Vehículo"}</strong>
                    <span className="ss-meta">
                      v{c.version} · emitida {formatFechaHora(c.fechaEmision)}
                      {c.vigenciaHasta && c.estado === "EMITIDA" && ` · ${diasRestantes(c.vigenciaHasta, ahora)}`}
                    </span>
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
