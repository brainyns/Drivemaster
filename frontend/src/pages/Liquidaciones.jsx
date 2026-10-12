import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { listarRepartos, liquidarReparto, listarReembolsos, marcarReembolsado, obtenerModo } from "../services/liquidacionService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import "../css/admin-base.css";

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

// Variante del badge de cada estado (admin-base.css)
const VARIANTE_REPARTO = {
  PENDIENTE: "neutro",
  POR_LIQUIDAR: "pendiente",
  PROCESANDO: "info",
  LIQUIDADO: "exito",
  FALLIDO: "peligro",
  ANULADO: "neutro",
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
  const marcando = filas.find((f) => f.id === marcandoId) || null;

  const textoAccion = (f) => (vista !== "repartos" ? "Marcar reembolsado"
    : f.estado === "FALLIDO" ? "Reintentar"
    : esManual ? "Registrar pago" : "Liquidar (simulado)");

  const abrirMarcar = (f) => { setMarcandoId(f.id); setNota(""); setNumero(""); setError(null); };

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Liquidaciones y reembolsos</h1>
          <p className="adm-encabezado__sub">
            Pagos pendientes a talleres y tiendas, y reembolsos a clientes. Cada pago queda registrado con su referencia.
          </p>
        </div>
        {!cargando && <span className="adm-encabezado__dato">Pendiente en esta lista: {formatPrecio(totalPendiente)}</span>}
      </header>

      {vista === "repartos" && modo && (
        <p className={`adm-alerta ${modo.modo === "SIMULADO" ? "adm-alerta--aviso" : ""}`} role="status" data-testid="modo-liquidacion">
          <strong>Modo de liquidación: {modo.modo}.</strong>{" "}
          {modo.modo === "SIMULADO"
            ? "Solo desarrollo: las liquidaciones se hacen solas y NO mueven dinero (referencias SIM-…)."
            : "Tú pagas por el banco y anotas aquí el número de transferencia; nada se liquida solo."}
        </p>
      )}

      <div className="adm-filtros" role="search" aria-label="Filtros de liquidaciones">
        <nav className="adm-pestanas" aria-label="Tipo">
          {VISTAS.map((v) => (
            <button key={v.key} type="button" className={`adm-pestana${vista === v.key ? " adm-pestana--activa" : ""}`}
              aria-pressed={vista === v.key} onClick={() => cambiarVista(v.key)}>
              {v.label}
            </button>
          ))}
        </nav>
        <span className="adm-filtros__separador" />
        <label className="adm-campo">
          <span>Estado</span>
          <select value={estado} onChange={(e) => setEstado(e.target.value)}>
            {ESTADOS[vista].map(([valor, label]) => <option key={valor || "todos"} value={valor}>{label}</option>)}
          </select>
        </label>
      </div>

      {error && !marcando && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}

      <div className="adm-tarjeta" aria-busy={cargando}>
        {cargando ? <div className="adm-cargando">Cargando…</div> : filas.length === 0 ? (
          <p className="adm-vacio">No hay registros en esta lista.</p>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Solicitud</th>
                  {vista === "repartos" ? <><th>Beneficiario</th><th>Concepto</th></> : <><th>Pago</th><th>Motivo</th></>}
                  <th className="adm-tabla__der">Monto</th>
                  <th>Estado</th>
                  <th className="adm-tabla__der">Acción</th>
                </tr>
              </thead>
              <tbody>
                {filas.map((f) => {
                  const badge = vista === "repartos"
                    ? (VARIANTE_REPARTO[f.estado] || "neutro")
                    : (f.estado === "REALIZADO" ? "exito" : f.estado === "PENDIENTE" ? "pendiente" : "neutro");
                  return (
                    <tr key={f.id} className={f.estado === "FALLIDO" ? "adm-tabla__fila--alerta" : ""}>
                      <td data-label="Fecha" className="adm-tabla__nowrap">{formatFechaHora(f.fechaCreacion)}</td>
                      <td data-label="Solicitud"><span className="adm-mono">#{refSolicitud(f.ordenServicioId)}</span></td>
                      {vista === "repartos" ? (
                        <>
                          <td data-label="Beneficiario">
                            <span className="adm-tabla__nombre">
                              <strong>{f.beneficiarioNombre || f.beneficiarioId}</strong>
                              <span className="adm-badge adm-badge--chico adm-badge--info">{f.beneficiarioTipo === "TALLER" ? "Taller" : "Tienda"}</span>
                            </span>
                          </td>
                          <td data-label="Concepto">{f.concepto === "DIAGNOSTICO" ? "Diagnóstico" : "Cotización"}</td>
                        </>
                      ) : (
                        <>
                          <td data-label="Pago">{f.referenciaPago}<span className="adm-tabla__sub">{f.transactionId}</span></td>
                          <td data-label="Motivo">{f.motivo}</td>
                        </>
                      )}
                      <td className="adm-tabla__der" data-label="Monto">{formatPrecio(f.monto)}</td>
                      <td data-label="Estado">
                        <span className={`adm-badge adm-badge--${badge}`}>
                          {vista === "repartos" ? (ETIQUETA_ESTADO[f.estado] || f.estado) : (f.estado === "REALIZADO" ? "Realizado" : f.estado === "PENDIENTE" ? "Pendiente" : f.estado)}
                        </span>
                        {vista === "repartos" && f.estado === "LIQUIDADO" && (
                          <span className="adm-tabla__sub">
                            {f.modoLiquidacion === "SIMULADO" ? "SIMULADO (sin dinero real)" : f.modoLiquidacion || ""}
                            {f.referenciaLiquidacion ? ` · Ref. ${f.referenciaLiquidacion}` : ""}
                            {f.liquidadoPor ? ` · ${f.liquidadoPor}` : ""}
                            {f.fechaLiquidacion ? ` · ${formatFechaHora(f.fechaLiquidacion)}` : ""}
                          </span>
                        )}
                        {vista === "repartos" && f.eventoListo && <span className="adm-tabla__sub">Evento: {f.eventoListo}</span>}
                        {vista === "repartos" && f.estado === "FALLIDO" && f.motivoFallo && (
                          <span className="adm-tabla__sub adm-tabla__sub--error">{f.motivoFallo}</span>
                        )}
                        {f.nota && <span className="adm-tabla__sub">{f.nota}</span>}
                      </td>
                      <td className="adm-tabla__der adm-tabla__celda--ancha">
                        <div className="adm-tabla__acciones">
                          {(vista === "repartos" ? sePuedePagar(f) : f.estado === "PENDIENTE") && (
                            <button type="button" className="adm-btn adm-btn--chico adm-btn--primario" onClick={() => abrirMarcar(f)}>
                              {textoAccion(f)}
                            </button>
                          )}
                        </div>
                        {vista === "repartos" && f.estado === "PENDIENTE" && f.liquidacionPorEvento && (
                          <span className="adm-tabla__sub">Se habilita al confirmarse la recepción o la entrega</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {marcando && (
        <ConfirmacionAdmin
          titulo={`${textoAccion(marcando)} · ${formatPrecio(marcando.monto)}`}
          texto={vista === "repartos"
            ? `${marcando.beneficiarioNombre || "Beneficiario"} · solicitud #${refSolicitud(marcando.ordenServicioId)}.`
            : `Reembolso de la solicitud #${refSolicitud(marcando.ordenServicioId)}. Hazlo primero en el panel de Wompi.`}
          confirmar="Confirmar"
          cargando={procesando}
          deshabilitado={vista === "repartos" && esManual && !numero.trim()}
          onConfirmar={() => marcar(marcando.id)}
          onCancelar={() => { setMarcandoId(null); setError(null); }}
        >
          {vista === "repartos" && esManual && (
            <label className="adm-campo adm-campo--bloque">
              <span>N.º de transferencia (obligatorio)</span>
              <input maxLength={80} required value={numero} onChange={(e) => setNumero(e.target.value)} />
            </label>
          )}
          <label className="adm-campo adm-campo--bloque">
            <span>Nota (opcional)</span>
            <input maxLength={300} value={nota} onChange={(e) => setNota(e.target.value)} />
          </label>
          {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
