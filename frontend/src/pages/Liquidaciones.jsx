import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { listarRepartos, liquidarReparto, listarReembolsos, marcarReembolsado, obtenerModo } from "../services/liquidacionService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";

const VISTAS = [
  { key: "repartos", label: "Liquidaciones a talleres y tiendas" },
  { key: "reembolsos", label: "Reembolsos a clientes" },
];

const ESTADOS = {
  repartos: [
    ["POR_LIQUIDAR", "Por liquidar"], ["FALLIDO", "Fallidos"], ["PENDIENTE", "Esperando recepción o entrega"],
    ["LIQUIDADO", "Liquidados"], ["ANULADO", "Anulados"], ["", "Todos"],
  ],
  reembolsos: [["PENDIENTE", "Pendientes"], ["REALIZADO", "Realizados"], ["", "Todos"]],
};

const ETIQUETA_ESTADO = {
  PENDIENTE: "Esperando recepción o entrega",
  POR_LIQUIDAR: "Por liquidar",
  PROCESANDO: "Procesando",
  LIQUIDADO: "Liquidado",
  FALLIDO: "Fallido",
  ANULADO: "Anulado",
};

/** Una línea nueva (regla por evento) en PENDIENTE espera su evento; las anteriores a la Fase 4-B se pagan desde PENDIENTE. */
const sePuedePagar = (f) =>
  f.estado === "POR_LIQUIDAR" || f.estado === "FALLIDO" || (f.estado === "PENDIENTE" && !f.liquidacionPorEvento);

/**
 * Lista del ADMIN. En modo SIMULADO el evento (recepción confirmada / orden entregada)
 * liquida solo y no mueve dinero; en modo MANUAL el ADMIN transfiere por el banco y anota
 * el número de transferencia (obligatorio). Los reembolsos siempre se hacen fuera (panel de Wompi).
 */
export default function Liquidaciones() {
  const token = getToken();
  const [vista, setVista] = useState("repartos");
  const [estado, setEstado] = useState("POR_LIQUIDAR");
  const [modo, setModo] = useState(null); // { modo, automatica }
  const [numero, setNumero] = useState("");
  const [filas, setFilas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [marcandoId, setMarcandoId] = useState(null);
  const [nota, setNota] = useState("");
  const [procesando, setProcesando] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    try {
      const datos = vista === "repartos" ? await listarRepartos(token, estado) : await listarReembolsos(token, estado);
      setFilas(datos || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, vista, estado]);

  useEffect(() => { cargar(); }, [cargar]);

  useEffect(() => {
    let cancelado = false;
    obtenerModo(token).then((m) => { if (!cancelado) setModo(m); }).catch(() => { if (!cancelado) setModo(null); });
    return () => { cancelado = true; };
  }, [token]);

  const esManual = modo?.modo === "MANUAL";

  const cambiarVista = (v) => {
    setVista(v);
    setEstado(v === "repartos" ? "POR_LIQUIDAR" : "PENDIENTE");
    setMarcandoId(null);
  };

  const marcar = async (id) => {
    setProcesando(true);
    setError(null);
    try {
      if (vista === "repartos") {
        await liquidarReparto(token, id, { numeroTransferencia: numero.trim() || null, nota: nota.trim() || null });
      }
      else await marcarReembolsado(token, id, nota.trim() || null);
      setMarcandoId(null);
      setNota("");
      setNumero("");
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const pendientes = filas.filter((f) => (vista === "repartos"
    ? ["PENDIENTE", "POR_LIQUIDAR", "FALLIDO"].includes(f.estado) : f.estado === "PENDIENTE"));
  const totalPendiente = pendientes.reduce((s, f) => s + (f.monto || 0), 0);

  return (
    <div className="ss-panel">
      {vista === "repartos" && modo && (
        <p className={modo.modo === "SIMULADO" ? "ss-aviso" : "ss-meta"} role="status" data-testid="modo-liquidacion">
          <strong>Modo de liquidación: {modo.modo}.</strong>{" "}
          {modo.modo === "SIMULADO"
            ? "Solo desarrollo: las liquidaciones se hacen solas y NO mueven dinero (referencias SIM-…)."
            : "Tú pagas por el banco y anotas aquí el número de transferencia; nada se liquida solo."}
        </p>
      )}

      <nav className="ss-filtros" aria-label="Tipo">
        {VISTAS.map((v) => (
          <button key={v.key} className={`ss-filtro${vista === v.key ? " ss-filtro--activo" : ""}`}
            aria-current={vista === v.key ? "page" : undefined} onClick={() => cambiarVista(v.key)}>
            {v.label}
          </button>
        ))}
      </nav>

      <div className="ct-resumen">
        <div className="ct-resumen__dato">
          <span className="ss-meta">Pendiente en esta lista</span>
          <span className="ct-resumen__valor">{formatPrecio(totalPendiente)}</span>
        </div>
        <div className="sv-field">
          <label htmlFor="lq-estado">Estado</label>
          <select id="lq-estado" value={estado} onChange={(e) => setEstado(e.target.value)}>
            {ESTADOS[vista].map(([valor, label]) => <option key={valor || "todos"} value={valor}>{label}</option>)}
          </select>
        </div>
      </div>

      {error && <p className="ss-alerta" role="alert">{error}</p>}

      {cargando ? <p className="sv-empty">Cargando...</p> : filas.length === 0 ? (
        <p className="sv-empty">No hay registros en esta lista.</p>
      ) : (
        <div className="ct-tabla-wrap">
          <table className="ct-tabla">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Solicitud</th>
                {vista === "repartos" ? <><th>Beneficiario</th><th>Concepto</th></> : <><th>Pago</th><th>Motivo</th></>}
                <th className="ct-tabla__monto">Monto</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.id}>
                  <td>{formatFechaHora(f.fechaCreacion)}</td>
                  <td>#{refSolicitud(f.ordenServicioId)}</td>
                  {vista === "repartos" ? (
                    <>
                      <td>{f.beneficiarioNombre || f.beneficiarioId}<span className="ss-meta">{f.beneficiarioTipo === "TALLER" ? "Taller" : "Tienda"}</span></td>
                      <td>{f.concepto === "DIAGNOSTICO" ? "Diagnóstico" : "Cotización"}</td>
                    </>
                  ) : (
                    <>
                      <td>{f.referenciaPago}<span className="ss-meta">{f.transactionId}</span></td>
                      <td>{f.motivo}</td>
                    </>
                  )}
                  <td className="ct-tabla__monto">{formatPrecio(f.monto)}</td>
                  <td>
                    {vista === "repartos" ? (ETIQUETA_ESTADO[f.estado] || f.estado) : f.estado}
                    {vista === "repartos" && f.estado === "LIQUIDADO" && (
                      <span className="ss-meta">
                        {f.modoLiquidacion === "SIMULADO" ? "SIMULADO (sin dinero real)" : f.modoLiquidacion || ""}
                        {f.referenciaLiquidacion ? ` · Ref. ${f.referenciaLiquidacion}` : ""}
                        {f.liquidadoPor ? ` · ${f.liquidadoPor}` : ""}
                        {f.fechaLiquidacion ? ` · ${formatFechaHora(f.fechaLiquidacion)}` : ""}
                      </span>
                    )}
                    {vista === "repartos" && f.eventoListo && <span className="ss-meta">Evento: {f.eventoListo}</span>}
                    {vista === "repartos" && f.estado === "FALLIDO" && f.motivoFallo && (
                      <span className="ss-error">{f.motivoFallo}</span>
                    )}
                    {f.nota && <span className="ss-meta">{f.nota}</span>}
                  </td>
                  <td>
                    {(vista === "repartos" ? sePuedePagar(f) : f.estado === "PENDIENTE") && (marcandoId === f.id ? (
                      <div className="ct-respuesta">
                        {vista === "repartos" && esManual && (
                          <input className="ss-input" placeholder="N.º de transferencia (obligatorio)" maxLength={80} required
                            aria-label="Número de transferencia" value={numero} onChange={(e) => setNumero(e.target.value)} />
                        )}
                        <input className="ss-input" placeholder="Nota (opcional)" maxLength={300}
                          aria-label="Nota" value={nota} onChange={(e) => setNota(e.target.value)} />
                        <div className="ss-acciones">
                          <button className="sv-btn" onClick={() => marcar(f.id)}
                            disabled={procesando || (vista === "repartos" && esManual && !numero.trim())}>
                            {procesando ? "Guardando..." : "Confirmar"}
                          </button>
                          <button className="sv-btn-outline" onClick={() => setMarcandoId(null)} disabled={procesando}>Cancelar</button>
                        </div>
                      </div>
                    ) : (
                      <button className="sv-btn-outline" onClick={() => { setMarcandoId(f.id); setNota(""); setNumero(""); }}>
                        {vista !== "repartos" ? "Marcar reembolsado"
                          : f.estado === "FALLIDO" ? "Reintentar"
                          : esManual ? "Registrar pago" : "Liquidar (simulado)"}
                      </button>
                    ))}
                    {vista === "repartos" && f.estado === "PENDIENTE" && f.liquidacionPorEvento && (
                      <span className="ss-meta">Se habilita al confirmarse la recepción o la entrega</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
