import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { repartosTabla, liquidarReparto, listarReembolsos, marcarReembolsado, obtenerModo } from "../services/liquidacionService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import { errorRango, propsPaginacion, textoOrden } from "../components/ui/reglasTabla";
import EncabezadoOrden from "../components/ui/EncabezadoOrden";
import FiltroFechas from "../components/ui/FiltroFechas";
import SelectorOrden from "../components/ui/SelectorOrden";
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

/** Columnas de las liquidaciones que se pueden ordenar (lista blanca de LiquidacionService.ORDEN_REPARTOS). */
const ORDENES_REPARTOS = [
  { campo: "fecha", label: "Fecha", asc: "(más antiguas primero)", desc: "(más recientes primero)" },
  { campo: "monto", label: "Monto" },
  { campo: "beneficiario", label: "Beneficiario", asc: "(A–Z)", desc: "(Z–A)" },
  { campo: "estado", label: "Estado", asc: "(A–Z)", desc: "(Z–A)" },
];

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
  // Liquidaciones: paginadas en el servidor (/repartos/tabla) con fechas y orden; los reembolsos son pocos y siguen
  // llegando completos
  const [tabla, setTabla] = useState(null); // { repartos: Pagina, pendientes, montoPendiente }
  const [pagina, setPagina] = useState(0);
  const [orden, setOrden] = useState(null);
  const [fechas, setFechas] = useState({ desde: "", hasta: "" });
  const errorFechas = errorRango(fechas);

  const cargar = useCallback(async () => {
    if (vista === "repartos" && errorFechas) return; // el selector de fechas ya lo avisa
    setCargando(true);
    try {
      if (vista === "repartos") {
        const r = await repartosTabla(token, { estado, ...fechas, page: pagina, size: 20, sort: textoOrden(orden) });
        setTabla(r);
        setFilas(r?.repartos?.items || []);
      } else {
        setFilas((await listarReembolsos(token, estado)) || []);
      }
      setErrorCarga(null);
    } catch (e) {
      setErrorCarga(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, vista, estado, fechas, pagina, orden, errorFechas]);

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
    setPagina(0);
  };
  // Cambiar estado, fechas u orden vuelve a la primera página
  const elegirEstado = (e) => { setEstado(e); setPagina(0); };
  const ponerFechas = (r) => { setFechas(r); setPagina(0); };
  const ordenar = (o) => { setOrden(o); setPagina(0); };

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

  // En liquidaciones el servidor cuenta toda la lista filtrada (no solo la página); en reembolsos, la lista completa
  const pendientesReembolso = filas.filter((f) => f.estado === "PENDIENTE");
  const numPendientes = esRepartos ? (tabla?.pendientes || 0) : pendientesReembolso.length;
  const totalRegistros = esRepartos ? (tabla?.repartos?.total || 0) : filas.length;
  const totalPendiente = esRepartos ? (tabla?.montoPendiente || 0) : pendientesReembolso.reduce((s, f) => s + (f.monto || 0), 0);
  const marcando = filas.find((f) => f.id === marcandoId) || null;

  const textoAccion = (f) => (!esRepartos ? "Marcar reembolsado"
    : f.estado === "FALLIDO" ? "Reintentar"
    : esManual ? "Registrar pago" : "Liquidar (simulado)");

  const abrirMarcar = (f) => { setMarcandoId(f.id); setNota(""); setNumero(""); setError(null); };
  const listaVacia = !cargando && !errorCarga && filas.length === 0;
  // Reembolsos: 20 por página en el navegador (vuelve a la primera al cambiar de estado)
  const pag = usePaginacion(filas.length, vista + "|" + estado);
  const enPagina = esRepartos ? filas : paginar(filas, pag.pagina);

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
          <button key={valor || "todos"} type="button" className="ui-chip" aria-pressed={estado === valor} onClick={() => elegirEstado(valor)}>
            {label}
          </button>
        ))}
      </div>

      {esRepartos && (
        <div className="liq-herramientas pp-bloque-sep">
          <FiltroFechas id="liq-fechas" etiqueta="Fecha de la liquidación" valor={fechas} onCambiar={ponerFechas} />
          <SelectorOrden id="liq-orden" opciones={ORDENES_REPARTOS} orden={orden} onOrdenar={ordenar} porDefecto="Más recientes primero" />
        </div>
      )}

      {!cargando && !errorCarga && filas.length > 0 && (
        <div className="ui-indicadores pp-seccion liq-resumen">
          <Indicador titulo="Pendiente en esta lista" valor={formatPrecio(totalPendiente)} tono={totalPendiente > 0 ? "accion" : undefined}
            pie={`${numPendientes} de ${totalRegistros} ${totalRegistros === 1 ? "registro" : "registros"} por ${esRepartos ? "pagar" : "reembolsar"}`} />
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
                  {esRepartos
                    ? <EncabezadoOrden campo="beneficiario" orden={orden} onOrdenar={ordenar}>Beneficiario</EncabezadoOrden>
                    : <th scope="col">Pago</th>}
                  {esRepartos
                    ? <EncabezadoOrden campo="fecha" orden={orden} onOrdenar={ordenar}>Fecha</EncabezadoOrden>
                    : <th scope="col">Fecha</th>}
                  <th scope="col">Solicitud</th>
                  <th scope="col">{esRepartos ? "Concepto" : "Motivo"}</th>
                  {esRepartos
                    ? <EncabezadoOrden campo="monto" orden={orden} onOrdenar={ordenar} className="pp-num">Monto</EncabezadoOrden>
                    : <th scope="col" className="pp-num">Monto</th>}
                  {esRepartos
                    ? <EncabezadoOrden campo="estado" orden={orden} onOrdenar={ordenar}>Estado</EncabezadoOrden>
                    : <th scope="col">Estado</th>}
                  <th scope="col"><span className="ui-sr">Acción</span></th>
                </tr>
              </thead>
              <tbody>
                {enPagina.map((f) => {
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
      {filas.length > 0 && (
        esRepartos
          ? <Paginacion {...propsPaginacion(tabla?.repartos)} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de liquidaciones" />
          : <Paginacion pagina={pag.pagina} total={filas.length} onCambiar={pag.cambiar} etiqueta="Páginas de reembolsos" />
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
