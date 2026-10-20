import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { listarRepartos, liquidarReparto, listarReembolsos, marcarReembolsado, obtenerModo } from "../services/liquidacionService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Indicador from "../components/ui/Indicador";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import { unir } from "../utils/formato";
// El diálogo de confirmación (ConfirmacionAdmin) trae su estilo de admin-base.css
import "../css/admin-base.css";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/liquidaciones-plataforma.css";

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

// Estado de cada línea con el formato de catálogo de ChipEstado
const ESTADO_REPARTO = {
  PENDIENTE: { label: "Esperando recepción o entrega", tono: "apagado" },
  POR_LIQUIDAR: { label: "Por liquidar", tono: "aviso" },
  PROCESANDO: { label: "Procesando", tono: "proceso" },
  LIQUIDADO: { label: "Liquidado", tono: "ok" },
  FALLIDO: { label: "Fallido", tono: "error" },
  ANULADO: { label: "Anulado", tono: "apagado" },
};
const ESTADO_REEMBOLSO = {
  PENDIENTE: { label: "Pendiente", tono: "aviso" },
  REALIZADO: { label: "Realizado", tono: "ok" },
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
  const [errorCarga, setErrorCarga] = useState(null);
  const [marcandoId, setMarcandoId] = useState(null);
  const [nota, setNota] = useState("");
  const [procesando, setProcesando] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    try {
      const datos = vista === "repartos" ? await listarRepartos(token, estado) : await listarReembolsos(token, estado);
      setFilas(datos || []);
      setErrorCarga(null);
    } catch (e) {
      setErrorCarga(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, vista, estado]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  useEffect(() => {
    let cancelado = false;
    obtenerModo(token).then((m) => { if (!cancelado) setModo(m); }).catch(() => { if (!cancelado) setModo(null); });
    return () => { cancelado = true; };
  }, [token]);

  const esManual = modo?.modo === "MANUAL";
  const esRepartos = vista === "repartos";

  const cambiarVista = (v) => {
    setVista(v);
    setEstado(v === "repartos" ? "POR_LIQUIDAR" : "PENDIENTE");
    setMarcandoId(null);
  };

  const marcar = async (id) => {
    setProcesando(true);
    setError(null);
    try {
      if (esRepartos) {
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

  const pendientes = filas.filter((f) => (esRepartos
    ? ["PENDIENTE", "POR_LIQUIDAR", "FALLIDO"].includes(f.estado) : f.estado === "PENDIENTE"));
  const totalPendiente = pendientes.reduce((s, f) => s + (f.monto || 0), 0);
  const marcando = filas.find((f) => f.id === marcandoId) || null;

  const textoAccion = (f) => (!esRepartos ? "Marcar reembolsado"
    : f.estado === "FALLIDO" ? "Reintentar"
    : esManual ? "Registrar pago" : "Liquidar (simulado)");

  const abrirMarcar = (f) => { setMarcandoId(f.id); setNota(""); setNumero(""); setError(null); };
  const listaVacia = !cargando && !errorCarga && filas.length === 0;

  return (
    <div className="ss-panel--plataforma pp-root liq-root">
      <EncabezadoPagina titulo="Liquidaciones y reembolsos"
        descripcion="Pagos pendientes a talleres y tiendas, y reembolsos a clientes. Cada pago queda registrado con su referencia." />

      {esRepartos && modo && (
        <Alerta tono={modo.modo === "SIMULADO" ? "aviso" : "info"} className="pp-bloque-sep">
          <strong>Modo de liquidación: {modo.modo}.</strong>{" "}
          {modo.modo === "SIMULADO"
            ? "Solo desarrollo: las liquidaciones se hacen solas y NO mueven dinero (referencias SIM-…)."
            : "Tú pagas por el banco y anotas aquí el número de transferencia; nada se liquida solo."}
        </Alerta>
      )}

      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Tipo">
        {VISTAS.map((v) => (
          <button key={v.key} type="button" className="ui-chip" aria-pressed={vista === v.key} onClick={() => cambiarVista(v.key)}>
            {v.label}
          </button>
        ))}
      </div>
      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Estado">
        {ESTADOS[vista].map(([valor, label]) => (
          <button key={valor || "todos"} type="button" className="ui-chip" aria-pressed={estado === valor} onClick={() => setEstado(valor)}>
            {label}
          </button>
        ))}
      </div>

      {!cargando && !errorCarga && filas.length > 0 && (
        <div className="ui-indicadores pp-seccion liq-resumen">
          <Indicador titulo="Pendiente en esta lista" valor={formatPrecio(totalPendiente)} tono={totalPendiente > 0 ? "accion" : undefined}
            pie={`${pendientes.length} de ${filas.length} ${filas.length === 1 ? "registro" : "registros"} por ${esRepartos ? "pagar" : "reembolsar"}`} />
        </div>
      )}

      {error && !marcando && <Alerta className="pp-bloque-sep" onCerrar={() => setError(null)}>{error}</Alerta>}
      {errorCarga && <Alerta className="pp-bloque-sep" onReintentar={cargar} reintentando={cargando}>{errorCarga}</Alerta>}

      {cargando && filas.length === 0 ? (
        <Esqueleto variante="lista" cantidad={4} texto="Cargando…" />
      ) : listaVacia ? (
        <EstadoVacio icono={esRepartos ? "file" : "refresh"} titulo="No hay registros en esta lista"
          texto={esRepartos ? "Prueba con otro estado: aquí aparecen los pagos a talleres y tiendas según avanzan las órdenes."
            : "Prueba con otro estado: aquí aparecen los reembolsos que se decidieron en Problemas de envío."} />
      ) : filas.length > 0 && (
        <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas liq-tabla">
              <caption className="ui-sr">{esRepartos ? "Liquidaciones a talleres y tiendas" : "Reembolsos a clientes"}</caption>
              <thead>
                <tr>
                  <th scope="col">{esRepartos ? "Beneficiario" : "Pago"}</th>
                  <th scope="col">Fecha</th>
                  <th scope="col">Solicitud</th>
                  <th scope="col">{esRepartos ? "Concepto" : "Motivo"}</th>
                  <th scope="col" className="pp-num">Monto</th>
                  <th scope="col">Estado</th>
                  <th scope="col"><span className="ui-sr">Acción</span></th>
                </tr>
              </thead>
              <tbody>
                {filas.map((f) => {
                  const pagable = esRepartos ? sePuedePagar(f) : f.estado === "PENDIENTE";
                  return (
                    <tr key={f.id} className={f.estado === "FALLIDO" ? "liq-fila--fallida" : undefined}>
                      <td className="ui-table__titulo">
                        {esRepartos ? (
                          <>
                            <strong>{f.beneficiarioNombre || f.beneficiarioId}</strong>
                            <span className="pp-sub">{f.beneficiarioTipo === "TALLER" ? "Taller" : "Tienda"}</span>
                          </>
                        ) : (
                          <>
                            <strong>{f.referenciaPago}</strong>
                            {f.transactionId && <span className="pp-sub liq-ref">{f.transactionId}</span>}
                          </>
                        )}
                      </td>
                      <td data-label="Fecha" className="liq-fecha">{formatFechaHora(f.fechaCreacion)}</td>
                      <td data-label="Solicitud" className="liq-ref">#{refSolicitud(f.ordenServicioId)}</td>
                      <td data-label={esRepartos ? "Concepto" : "Motivo"} className="liq-texto">
                        {esRepartos ? (f.concepto === "DIAGNOSTICO" ? "Diagnóstico" : "Cotización") : f.motivo}
                      </td>
                      <td data-label="Monto" className="pp-num">{formatPrecio(f.monto)}</td>
                      <td data-label="Estado" className="liq-estado">
                        <ChipEstado catalogo={esRepartos ? ESTADO_REPARTO : ESTADO_REEMBOLSO} estado={f.estado} />
                        {esRepartos && f.estado === "LIQUIDADO" && (
                          <span className="pp-sub">
                            {unir([f.modoLiquidacion === "SIMULADO" ? "SIMULADO (sin dinero real)" : f.modoLiquidacion,
                              f.referenciaLiquidacion && `Ref. ${f.referenciaLiquidacion}`, f.liquidadoPor,
                              f.fechaLiquidacion && formatFechaHora(f.fechaLiquidacion)])}
                          </span>
                        )}
                        {esRepartos && f.eventoListo && <span className="pp-sub">Evento: {f.eventoListo}</span>}
                        {esRepartos && f.estado === "FALLIDO" && f.motivoFallo && <span className="pp-sub liq-fallo">{f.motivoFallo}</span>}
                        {f.nota && <span className="pp-sub">{f.nota}</span>}
                      </td>
                      <td className="ui-table__acciones liq-acciones">
                        {pagable && (
                          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" onClick={() => abrirMarcar(f)}>
                            {textoAccion(f)}
                          </button>
                        )}
                        {esRepartos && f.estado === "PENDIENTE" && f.liquidacionPorEvento && (
                          <span className="pp-sub">Se habilita al confirmarse la recepción o la entrega</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {marcando && (
        <ConfirmacionAdmin
          titulo={`${textoAccion(marcando)} · ${formatPrecio(marcando.monto)}`}
          texto={esRepartos
            ? `${marcando.beneficiarioNombre || "Beneficiario"} · solicitud #${refSolicitud(marcando.ordenServicioId)}.`
            : `Reembolso de la solicitud #${refSolicitud(marcando.ordenServicioId)}. Hazlo primero en el panel de Wompi.`}
          confirmar="Confirmar"
          cargando={procesando}
          deshabilitado={esRepartos && esManual && !numero.trim()}
          onConfirmar={() => marcar(marcando.id)}
          onCancelar={() => { setMarcandoId(null); setError(null); }}
        >
          {esRepartos && esManual && (
            <Campo id="liq-numero" label="N.º de transferencia" ayuda="Obligatorio: la referencia del pago en el banco.">
              <input className="ui-field__input" maxLength={80} required value={numero} onChange={(e) => setNumero(e.target.value)} />
            </Campo>
          )}
          <Campo id="liq-nota" label="Nota" opcional>
            <input className="ui-field__input" maxLength={300} value={nota} onChange={(e) => setNota(e.target.value)} />
          </Campo>
          {error && <Alerta>{error}</Alerta>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
