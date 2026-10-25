import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../../services/authService";
import { cotizacionesDelTaller } from "../../services/cotizacionService";
import CotizacionDetalle from "../../components/servicio/CotizacionDetalle";
import { formatFechaHora, formatPrecio } from "../../components/servicio/estadosServicio";
import { ESTADOS_COTIZACION_TALLER } from "../../components/servicio/estadosTaller";
import EncabezadoPagina from "../../components/ui/EncabezadoPagina";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Esqueleto from "../../components/ui/Esqueleto";
import Icon from "../../components/public/Icon";
import { FILTROS, VACIO, vencida, vencePronto, contarPorFiltro, filtrar } from "./filtrosCotizaciones";
import "../../css/servicios.css";
import "../../css/solicitud-servicio.css";
import "../../css/cotizacion.css";
import "../../css/cotizaciones-taller.css";

const TITULO_VACIO = {
  emitidas: "Nada esperando respuesta",
  aprobadas: "Aún sin cotizaciones aprobadas",
  rechazadas: "Ninguna rechazada",
  vencidas: "Ninguna vencida",
};

function diasRestantes(fecha, ahora) {
  const d = Math.ceil((new Date(fecha).getTime() - ahora) / 86400000);
  if (d < 0) return `venció hace ${-d} d`;
  return d === 0 ? "vence hoy" : `vence en ${d} d`;
}

/** Cotizaciones del taller en una sola lista: emitidas, aprobadas, rechazadas y vencidas. */
export default function TallerCotizaciones({ irA }) {
  const token = getToken();
  const [cotizaciones, setCotizaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [filtro, setFiltro] = useState("emitidas");
  const [abierta, setAbierta] = useState(null);
  const [ahora] = useState(() => Date.now());

  const cargar = useCallback(async () => {
    try {
      setCotizaciones((await cotizacionesDelTaller(token)) || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const reintentar = async () => {
    setReintentando(true);
    await cargar();
    setReintentando(false);
  };

  const conteo = useMemo(() => contarPorFiltro(cotizaciones, ahora), [cotizaciones, ahora]);
  const visibles = filtrar(cotizaciones, filtro, ahora);
  const pendienteTotal = cotizaciones
    .filter((c) => c.estado === "EMITIDA" && !vencida(c, ahora))
    .reduce((s, c) => s + (c.total || 0), 0);

  return (
    <div className="ss-panel tc-root">
      <EncabezadoPagina
        titulo="Cotizaciones"
        descripcion={conteo.emitidas > 0
          ? `${conteo.emitidas} esperando respuesta del cliente, por ${formatPrecio(pendienteTotal)}.`
          : "Lo que cotizaste, lo que aprobaron, lo que rechazaron y lo que se venció."}
        acciones={irA && (
          <button type="button" className="ui-btn ui-btn--secundario" onClick={() => irA("taller-solicitudes", "diagnosticadas")}>
            <Icon name="file" size={16} /> Crear cotización
          </button>
        )}
      />

      {error && (
        <Alerta className="tc-alerta" onReintentar={reintentar} reintentando={reintentando}>
          No pudimos cargar tus cotizaciones. {error}
        </Alerta>
      )}

      <div className="ui-filtros tc-filtros" role="group" aria-label="Cotizaciones por estado">
        {FILTROS.map((f) => (
          <button key={f.key} type="button" className="ui-chip" aria-pressed={filtro === f.key}
            onClick={() => { setFiltro(f.key); setAbierta(null); }}>
            {f.label}
            <span className="ui-chip__contador">{conteo[f.key]}</span>
          </button>
        ))}
      </div>

      {cargando ? (
        <Esqueleto variante="lista" cantidad={4} texto="Cargando cotizaciones…" />
      ) : visibles.length === 0 ? (
        !error && (
          <EstadoVacio icono="file" titulo={TITULO_VACIO[filtro]} texto={VACIO[filtro]}>
            {filtro === "emitidas" && irA && (
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => irA("taller-solicitudes", "diagnosticadas")}>
                Ver solicitudes por cotizar
              </button>
            )}
          </EstadoVacio>
        )
      ) : (
        <ul className="tc-lista">
          {visibles.map((c) => {
            const abierto = abierta === c.id;
            const rechazada = c.estado === "RECHAZADA";
            return (
              <li key={c.id} className={`tc-item${abierto ? " tc-item--abierto" : ""}`}>
                <button type="button" className="tc-fila" aria-expanded={abierto} aria-controls={`tc-detalle-${c.id}`}
                  onClick={() => setAbierta(abierto ? null : c.id)}>
                  <span className="tc-fila__vehiculo">
                    <strong>{c.vehiculoDescripcion || "Vehículo"}</strong>
                    <span className="tc-fila__meta">
                      {rechazada
                        ? <>v{c.version} · rechazada {c.fechaRespuesta ? formatFechaHora(c.fechaRespuesta) : "(sin fecha)"}</>
                        : <>v{c.version} · emitida {formatFechaHora(c.fechaEmision)}</>}
                      {c.vigenciaHasta && c.estado === "EMITIDA" && ` · ${diasRestantes(c.vigenciaHasta, ahora)}`}
                    </span>
                    {rechazada && c.motivoRespuesta && (
                      <span className="tc-fila__motivo">Motivo del cliente: “{c.motivoRespuesta}”</span>
                    )}
                  </span>
                  <span className="tc-fila__total ui-precio">{formatPrecio(c.total)}</span>
                  <span className="tc-fila__chips">
                    <ChipEstado estado={vencida(c, ahora) ? "VENCIDA" : c.estado} catalogo={ESTADOS_COTIZACION_TALLER} />
                    {vencePronto(c, ahora) && <ChipEstado tono="aviso">Vence pronto</ChipEstado>}
                  </span>
                  <Icon name="chevron" size={18} className="tc-fila__chevron" />
                </button>
                {abierto && (
                  <div id={`tc-detalle-${c.id}`} className="tc-detalle">
                    <CotizacionDetalle cotizacion={c} mostrarComision sinEstado />
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
