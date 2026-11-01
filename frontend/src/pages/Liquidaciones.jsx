import { useCallback, useEffect, useRef, useState } from "react";
import { getToken } from "../services/authService";
import { repartosTabla, liquidarReparto, listarReembolsos, marcarReembolsado, obtenerModo, resolverReparto } from "../services/liquidacionService";
import { formatFechaHora, formatPrecio } from "../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import DetallePlataforma from "../components/admin/DetallePlataforma";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import { errorRango, propsPaginacion, rangoRapido, textoOrden } from "../components/ui/reglasTabla";
import EncabezadoOrden from "../components/ui/EncabezadoOrden";
import SelectorFechas from "../components/ui/SelectorFechas";
import SelectorOrden from "../components/ui/SelectorOrden";
import ChipEstado from "../components/ui/ChipEstado";
import Indicador from "../components/ui/Indicador";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import Dato from "../components/ui/Dato";
import { unir } from "../utils/formato";
import {
  ESTADOS_LIQUIDACION, MAS_ESTADOS_LIQUIDACION, avisoLiquidacion, avisoModo, desgloseFila, faltaParaResolver, fechaLinea, fueraDelRango, porRevisar, referenciaLinea,
  sePuedePagar, tarjetasLiquidaciones, textoConcepto, textoCuenta, unaALaVez,
} from "./admin/plataformaAdmin";
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
  { key: "repartos", label: "Liquidaciones" },
  { key: "reembolsos", label: "Reembolsos" },
];

const ESTADOS_REEMBOLSO = [["PENDIENTE", "Pendientes"], ["REALIZADO", "Realizados"], ["", "Todos"]];

// "Hoy" no sirve aquí: se paga por periodos. Todo va después, porque se empieza en Este mes
const RANGOS = [{ clave: "mes", label: "Este mes" }, { clave: "7d", label: "Últimos 7 días" }];

// Estado de cada línea con el formato de catálogo de ChipEstado
const ESTADO_REPARTO = {
  PENDIENTE: { label: "En espera", tono: "apagado" },
  POR_LIQUIDAR: { label: "Por pagar", tono: "aviso" },
  PROCESANDO: { label: "Procesando", tono: "proceso" },
  SIN_CONFIRMAR: { label: "Sin confirmar", tono: "aviso" },
  LIQUIDADO: { label: "Pagado", tono: "ok" },
  FALLIDO: { label: "Fallido", tono: "error" },
  ANULADO: { label: "Anulado", tono: "apagado" },
};
const ESTADO_REEMBOLSO = {
  PENDIENTE: { label: "Pendiente", tono: "aviso" },
  REALIZADO: { label: "Realizado", tono: "ok" },
};

const VALOR_MAS = "MAS";
const nombreBeneficiario = (f) => f.beneficiario || f.beneficiarioNombre || "Beneficiario sin nombre";
const tipoBeneficiario = (f) => (f.beneficiarioTipo === "TALLER" ? "Taller" : "Tienda");

/**
 * Lista del ADMIN. Se paga solo lo que está listo: en modos MANUAL y WOMPI cada pago lo aprueba el ADMIN, con su
 * confirmación; en SIMULADO el evento liquida solo y no mueve dinero. Los reembolsos siempre se hacen fuera (panel de
 * Wompi). Una línea sin confirmar por Wompi se resuelve a mano, con motivo y registro.
 */
export default function Liquidaciones() {
  const token = getToken();
  const [vista, setVista] = useState("repartos");
  const [estado, setEstado] = useState("POR_LIQUIDAR");
  const [modo, setModo] = useState(null); // { modo, automatica, dineroReal }
  const [numero, setNumero] = useState("");
  const [filas, setFilas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [avisoPago, setAvisoPago] = useState(null); // lo que se sabe de un pago que no volvió con éxito: { tono, texto, estado, accion }
  const [errorCarga, setErrorCarga] = useState(null);
  const [marcandoId, setMarcandoId] = useState(null);
  const [nota, setNota] = useState("");
  const [procesando, setProcesando] = useState(false);
  const [detalle, setDetalle] = useState(null); // { tipo: "pedido" | "orden", id }
  const [resolviendoId, setResolviendoId] = useState(null);
  const [resolucion, setResolucion] = useState({ resultado: "", motivo: "", referencia: "" });
  // Liquidaciones: paginadas en el servidor (/repartos/tabla) con fechas y orden; los reembolsos son pocos y siguen
  // llegando completos
  const [tabla, setTabla] = useState(null); // { repartos: Pagina, resumen }
  const [pagina, setPagina] = useState(0);
  const [orden, setOrden] = useState(null);
  const [fechas, setFechas] = useState(() => rangoRapido("mes"));
  const errorFechas = errorRango(fechas);
  // "Ver fallidos", "Revisar" y "Ver todo" miran todas las fechas por un momento: al volver a un estado sigue el rango elegido
  const fechasAntesDeVerTodo = useRef(null);
  // Solo cuenta la última petición: una respuesta lenta no pisa el filtro que se eligió después
  const ultimaPeticion = useRef(0);
  const enviando = useRef(false);

  const cargar = useCallback(async () => {
    if (vista === "repartos" && errorFechas) return; // el selector de fechas ya lo avisa
    const peticion = ++ultimaPeticion.current;
    setCargando(true);
    try {
      if (vista === "repartos") {
        const r = await repartosTabla(token, { estado, ...fechas, page: pagina, size: 20, sort: textoOrden(orden) });
        if (peticion !== ultimaPeticion.current) return;
        setTabla(r);
        setFilas(r?.repartos?.items || []);
      } else {
        const lista = (await listarReembolsos(token, estado)) || [];
        if (peticion !== ultimaPeticion.current) return;
        setFilas(lista);
      }
      setErrorCarga(null);
    } catch (e) {
      if (peticion === ultimaPeticion.current) setErrorCarga(e.message);
    } finally {
      if (peticion === ultimaPeticion.current) setCargando(false);
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
  const esWompi = modo?.modo === "WOMPI";
  const esRepartos = vista === "repartos";
  const aviso = esRepartos ? avisoModo(modo) : null;

  const cambiarVista = (v) => {
    setVista(v);
    setEstado(v === "repartos" ? "POR_LIQUIDAR" : "PENDIENTE");
    setFilas([]); // son otras columnas: las de la vista anterior no se pueden mostrar mientras carga
    setMarcandoId(null);
    setPagina(0);
  };
  // Cambiar estado, fechas u orden vuelve a la primera página
  const elegirEstado = (e) => {
    setEstado(e);
    if (fechasAntesDeVerTodo.current) { setFechas(fechasAntesDeVerTodo.current); fechasAntesDeVerTodo.current = null; }
    setPagina(0);
  };
  const ponerFechas = (r) => { fechasAntesDeVerTodo.current = null; setFechas(r); setPagina(0); };
  const ordenar = (o) => { setOrden(o); setPagina(0); };
  const verTodoElEstado = (e) => {
    fechasAntesDeVerTodo.current ??= fechas;
    setEstado(e);
    setFechas({ desde: "", hasta: "" });
    setPagina(0);
  };
  // La cifra de Pagado es del mes en curso: lleva a ese mismo mes
  const verPagadoDelMes = () => { fechasAntesDeVerTodo.current = null; setEstado("LIQUIDADO"); setFechas(rangoRapido("mes")); setPagina(0); };

  const marcar = (id) => unaALaVez(enviando, async () => {
    setProcesando(true);
    setError(null);
    setAvisoPago(null);
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
      const avisoDelPago = esRepartos ? avisoLiquidacion(e) : null;
      if (avisoDelPago) {
        setMarcandoId(null);
        setAvisoPago(avisoDelPago);
        // La fila dice su estado real aunque la recarga falle
        if (e.estado) setFilas((lista) => lista.map((f) => (f.id === id ? { ...f, estado: e.estado, referenciaLiquidacion: e.referencia ?? f.referenciaLiquidacion } : f)));
      } else {
        setError(e.message);
      }
      await cargar(); // un fallo también cambia la línea (queda FALLIDO o sin confirmar)
    } finally {
      setProcesando(false);
    }
  });

  const resolver = (id) => unaALaVez(enviando, async () => {
    setProcesando(true);
    setError(null);
    try {
      await resolverReparto(token, id, {
        resultado: resolucion.resultado, motivo: resolucion.motivo.trim(), referencia: resolucion.referencia.trim() || null,
      });
      setResolviendoId(null);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  });

  const pendientesReembolso = filas.filter((f) => f.estado === "PENDIENTE");
  const totalPendienteReembolso = pendientesReembolso.reduce((s, f) => s + (f.monto || 0), 0);
  // Si la línea ya no se puede pagar, el diálogo no se queda abierto ofreciendo pagarla
  const marcando = filas.find((f) => f.id === marcandoId && (!esRepartos || sePuedePagar(f))) || null;
  const desgloseDialogo = marcando && esRepartos ? desgloseFila(marcando, tabla?.comisionSeDescuenta) : null;
  const resolviendo = filas.find((f) => f.id === resolviendoId) || null;

  const resumen = esRepartos ? tabla?.resumen : null;
  const tarjetas = tarjetasLiquidaciones(resumen);
  const fuera = fueraDelRango(estado, resumen, tabla?.repartos?.total || 0, fechas);
  const fallidas = resumen?.fallidas?.cantidad || 0;
  const enProceso = resumen?.enProceso?.cantidad || 0;
  const revisar = porRevisar(resumen, estado);
  const estadoEnMas = esRepartos && MAS_ESTADOS_LIQUIDACION.some(([v]) => v === estado);

  const textoAccion = (f) => (!esRepartos ? "Marcar reembolsado"
    : f.estado === "FALLIDO" ? (esWompi ? "Reintentar con Wompi" : "Reintentar")
    : esManual ? "Registrar pago"
    : esWompi ? "Liquidar con Wompi" : "Liquidar (simulado)");

  // En la fila, corto para que la acción quepa junto al monto; el diálogo dice el nombre completo
  const textoBoton = (f) => (esRepartos && esWompi ? (f.estado === "FALLIDO" ? "Reintentar" : "Liquidar") : textoAccion(f));

  const abrirMarcar = (f) => { setMarcandoId(f.id); setNota(""); setNumero(""); setError(null); };
  const abrirResolver = (f) => { setResolviendoId(f.id); setResolucion({ resultado: "", motivo: "", referencia: "" }); setError(null); };
  const listaVacia = !cargando && !errorCarga && filas.length === 0;
  // Reembolsos: 20 por página en el navegador (vuelve a la primera al cambiar de estado)
  const pag = usePaginacion(filas.length, vista + "|" + estado);
  const enPagina = esRepartos ? filas : paginar(filas, pag.pagina);
  const faltaResolver = faltaParaResolver(resolucion);

  return (
    <div className="ss-panel--plataforma pp-root liq-root">
      <EncabezadoPagina titulo="Liquidaciones y reembolsos"
        descripcion="Pagos a talleres y tiendas, y reembolsos a clientes. Cada pago queda registrado con su referencia." />

      {aviso && !aviso.grande && <p className="liq-modo pp-bloque-sep"><strong>Dinero real</strong>{aviso.texto.replace("Dinero real", "")}</p>}
      {aviso?.grande && <Alerta tono="aviso" className="pp-bloque-sep"><strong>{aviso.texto}</strong></Alerta>}

      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Tipo">
        {VISTAS.map((v) => (
          <button key={v.key} type="button" className="ui-chip" aria-pressed={vista === v.key} onClick={() => cambiarVista(v.key)}>
            {v.label}
          </button>
        ))}
      </div>

      {tarjetas.length > 0 && (
        <div className="ui-indicadores pp-bloque-sep liq-resumen">
          {tarjetas.map((t) => (
            <Indicador key={t.estado} titulo={t.titulo} valor={t.valor} pie={t.pie} tono={t.tono}
              onClick={() => (t.estado === "LIQUIDADO" ? verPagadoDelMes() : elegirEstado(t.estado))} />
          ))}
        </div>
      )}
      {!esRepartos && !cargando && !errorCarga && filas.length > 0 && (
        <div className="ui-indicadores pp-bloque-sep liq-resumen liq-resumen--una">
          <Indicador titulo="Pendiente en esta lista" valor={formatPrecio(totalPendienteReembolso)} tono={totalPendienteReembolso > 0 ? "accion" : undefined}
            pie={`${pendientesReembolso.length} de ${filas.length} ${filas.length === 1 ? "registro" : "registros"} por reembolsar`} />
        </div>
      )}

      <div className="liq-herramientas pp-bloque-sep">
        <div className="ui-filtros" role="group" aria-label="Estado">
          {(esRepartos ? ESTADOS_LIQUIDACION : ESTADOS_REEMBOLSO).map(([valor, label]) => (
            <button key={valor || "todos"} type="button" className="ui-chip" aria-pressed={estado === valor} onClick={() => elegirEstado(valor)}>
              {label}
            </button>
          ))}
          {esRepartos && (
            <span className={`ui-chip liq-mas${estadoEnMas ? " liq-mas--activa" : ""}`}>
              <select aria-label="Más estados" value={estadoEnMas ? estado : VALOR_MAS} onChange={(e) => elegirEstado(e.target.value)}>
                <option value={VALOR_MAS} disabled hidden>Más</option>
                {MAS_ESTADOS_LIQUIDACION.map(([valor, label]) => <option key={valor || "todos"} value={valor}>{label}</option>)}
              </select>
              {fallidas + enProceso > 0 && (
                <span className="ui-chip__contador" aria-label={`${fallidas + enProceso} por revisar`}>{fallidas + enProceso}</span>
              )}
            </span>
          )}
        </div>
        {esRepartos && (
          <div className="liq-selectores">
            <SelectorFechas id="liq-fechas" etiqueta="Fecha" valor={fechas} onCambiar={ponerFechas} rangos={RANGOS} todoAlFinal />
            <div className="liq-orden-movil">
              <SelectorOrden id="liq-orden" opciones={ORDENES_REPARTOS} orden={orden} onOrdenar={ordenar} porDefecto="Más recientes primero" />
            </div>
          </div>
        )}
      </div>

      {revisar.length > 0 && (
        <Alerta tono={revisar[0].estado === "FALLIDO" ? "error" : "aviso"} className="pp-bloque-sep">
          <span className="liq-revisar">
            {revisar.map((r) => (
              <span key={r.estado}>
                {r.texto}.{" "}
                <button type="button" className="dtp-enlace" onClick={() => verTodoElEstado(r.estado)}>{r.accion}</button>
              </span>
            ))}
          </span>
        </Alerta>
      )}
      {fuera > 0 && (
        <Alerta tono="info" className="pp-bloque-sep">
          {fuera === 1 ? "Hay 1 fuera de estas fechas" : `Hay ${fuera} fuera de estas fechas`}.{" "}
          <button type="button" className="dtp-enlace" onClick={() => verTodoElEstado(estado)}>Ver todo</button>
        </Alerta>
      )}

      {avisoPago && (
        <Alerta tono={avisoPago.tono} className="pp-bloque-sep" onCerrar={() => setAvisoPago(null)}>
          {avisoPago.texto}{" "}
          {estado !== avisoPago.estado && <button type="button" className="dtp-enlace" onClick={() => verTodoElEstado(avisoPago.estado)}>{avisoPago.accion}</button>}
        </Alerta>
      )}
      {error && !marcando && !resolviendo && <Alerta className="pp-bloque-sep" onCerrar={() => setError(null)}>{error}</Alerta>}
      {errorCarga && <Alerta className="pp-bloque-sep" onReintentar={cargar} reintentando={cargando}>{errorCarga}</Alerta>}

      {cargando && filas.length === 0 ? (
        <Esqueleto variante="lista" cantidad={4} texto="Cargando…" />
      ) : listaVacia ? (
        <EstadoVacio icono={esRepartos ? "file" : "refresh"} titulo="No hay registros en esta lista"
          texto={esRepartos ? "Prueba con otro estado u otras fechas: aquí aparecen los pagos a talleres y tiendas según avanzan las órdenes."
            : "Prueba con otro estado: aquí aparecen los reembolsos que se decidieron en Problemas de envío."} />
      ) : filas.length > 0 && (
        <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
          <div className="ui-table-wrap">
            <table className={`ui-table ui-table--tarjetas liq-tabla${esRepartos ? " liq-tabla--repartos" : ""}`}>
              <caption className="ui-sr">{esRepartos ? "Liquidaciones a talleres y tiendas" : "Reembolsos a clientes"}</caption>
              <thead>
                <tr>
                  {esRepartos
                    ? <EncabezadoOrden campo="beneficiario" orden={orden} onOrdenar={ordenar}>Beneficiario</EncabezadoOrden>
                    : <th scope="col">Pago</th>}
                  <th scope="col">Referencia</th>
                  {esRepartos
                    ? <EncabezadoOrden campo="fecha" orden={orden} onOrdenar={ordenar}>Fecha</EncabezadoOrden>
                    : <th scope="col">Fecha</th>}
                  {!esRepartos && <th scope="col">Motivo</th>}
                  {esRepartos
                    ? <EncabezadoOrden campo="estado" orden={orden} onOrdenar={ordenar}>Estado</EncabezadoOrden>
                    : <th scope="col">Estado</th>}
                  {esRepartos
                    ? <>
                      <th scope="col" className="pp-num liq-col-desglose">Bruto</th>
                      <th scope="col" className="pp-num liq-col-desglose">Comisión</th>
                      <EncabezadoOrden campo="monto" orden={orden} onOrdenar={ordenar} className="pp-num">Neto</EncabezadoOrden>
                    </>
                    : <th scope="col" className="pp-num">Monto</th>}
                  <th scope="col"><span className="ui-sr">Acción</span></th>
                </tr>
              </thead>
              <tbody>
                {enPagina.map((f) => {
                  const pagable = esRepartos ? sePuedePagar(f) : f.estado === "PENDIENTE";
                  const ref = referenciaLinea(f);
                  const cuenta = esRepartos ? textoCuenta(f.cuenta) : "";
                  const faltaCuenta = esRepartos && esWompi && pagable && !f.cuenta;
                  const idFalta = `liq-falta-${f.id}`;
                  const desglose = esRepartos ? desgloseFila(f, tabla?.comisionSeDescuenta) : null;
                  return (
                    <tr key={f.id} className={f.estado === "FALLIDO" ? "liq-fila--fallida" : undefined}>
                      <td className="ui-table__titulo">
                        {esRepartos ? (
                          <>
                            <span className="liq-beneficiario">
                              <strong>{nombreBeneficiario(f)}</strong>
                              <span className="liq-tipo">{tipoBeneficiario(f)}</span>
                            </span>
                            <span className="pp-sub">{cuenta || "Sin cuenta bancaria"}</span>
                          </>
                        ) : (
                          <>
                            <strong>{f.referenciaPago}</strong>
                            {f.transactionId && <span className="pp-sub liq-ref">{f.transactionId}</span>}
                          </>
                        )}
                      </td>
                      <td data-label="Referencia" className="liq-ref">
                        <span>
                          {ref.tipo ? (
                            <button type="button" className="dtp-enlace" onClick={() => setDetalle({ tipo: ref.tipo, id: ref.id })}
                              aria-label={`Ver detalle de ${ref.tipo === "pedido" ? "el pedido" : "la solicitud"} ${ref.texto}`}>
                              {ref.texto}
                            </button>
                          ) : ref.texto}
                          {esRepartos && <span className="pp-sub">{textoConcepto(f.concepto)}</span>}
                          {esRepartos && <span className="pp-sub liq-fecha-sub">{fechaLinea(f.fechaCreacion)}</span>}
                        </span>
                      </td>
                      <td data-label="Fecha" className="liq-fecha">{fechaLinea(f.fechaCreacion)}</td>
                      {!esRepartos && <td data-label="Motivo" className="liq-texto">{f.motivo}</td>}
                      <td data-label="Estado" className="liq-estado">
                        <ChipEstado catalogo={esRepartos ? ESTADO_REPARTO : ESTADO_REEMBOLSO} estado={f.sePuedeResolver ? "SIN_CONFIRMAR" : f.estado} />
                        {esRepartos && f.estado === "LIQUIDADO" && (
                          <span className="pp-sub">
                            {unir([f.modoLiquidacion === "SIMULADO" ? "SIMULADO (sin dinero real)" : f.modoLiquidacion === "SIN_PAGO" ? "Sin transferencia" : f.modoLiquidacion,
                              f.referenciaLiquidacion && f.modoLiquidacion !== "SIN_PAGO" && `Ref. ${f.referenciaLiquidacion}`, f.liquidadoPor,
                              f.fechaLiquidacion && formatFechaHora(f.fechaLiquidacion)])}
                          </span>
                        )}
                        {esRepartos && (f.estado === "FALLIDO" || f.estado === "PROCESANDO") && f.motivoFallo && <span className="pp-sub liq-fallo">{f.motivoFallo}</span>}
                        {f.nota && <span className="pp-sub">{f.nota}</span>}
                      </td>
                      {esRepartos && <td data-label="Bruto" className="pp-num liq-col-desglose">{formatPrecio(desglose.bruto)}</td>}
                      {esRepartos && (
                        <td data-label="Comisión" className="pp-num liq-col-desglose">
                          <span>{desglose.comision}{desglose.nota && <span className="pp-sub">{desglose.nota}</span>}</span>
                        </td>
                      )}
                      <td data-label={esRepartos ? "Neto" : "Monto"} className="pp-num liq-monto">
                        <span>
                          {formatPrecio(f.monto)}
                          {esRepartos && (
                            <span className="pp-sub liq-desglose-sub">
                              <span>Bruto {formatPrecio(desglose.bruto)}</span>
                              <span>Comisión {desglose.comision}</span>
                            </span>
                          )}
                        </span>
                      </td>
                      <td className="ui-table__acciones liq-acciones">
                        {pagable && (
                          <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" onClick={() => abrirMarcar(f)}
                            disabled={faltaCuenta} aria-describedby={faltaCuenta ? idFalta : undefined}>
                            {textoBoton(f)}
                          </button>
                        )}
                        {faltaCuenta && <span id={idFalta} className="pp-sub liq-fallo">Falta cuenta bancaria</span>}
                        {esRepartos && f.sePuedeResolver && (
                          <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => abrirResolver(f)}>Resolver</button>
                        )}
                        {esRepartos && f.estado === "PENDIENTE" && <span className="pp-sub">Esperando entrega</span>}
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
          titulo={esRepartos ? `${textoAccion(marcando)} · ${formatPrecio(marcando.monto)}` : `Marcar reembolsado · ${formatPrecio(marcando.monto)}`}
          texto={esRepartos ? null : `Reembolso de ${referenciaLinea(marcando).texto}. Hazlo primero en el panel de Wompi.`}
          confirmar={esRepartos ? `Liquidar ${formatPrecio(marcando.monto)}` : "Confirmar"}
          cargando={procesando}
          deshabilitado={esRepartos && esManual && !numero.trim()}
          onConfirmar={() => marcar(marcando.id)}
          onCancelar={() => { setMarcandoId(null); setError(null); }}
        >
          {esRepartos && (
            <>
              <dl className="ui-datos liq-confirmacion">
                <Dato etiqueta="Beneficiario" ancho>{nombreBeneficiario(marcando)} · {tipoBeneficiario(marcando)}</Dato>
                <Dato etiqueta="Banco">{marcando.cuenta?.banco}</Dato>
                <Dato etiqueta="Cuenta">{unir([marcando.cuenta?.tipoCuenta && textoCuenta({ tipoCuenta: marcando.cuenta.tipoCuenta }), marcando.cuenta?.numero])}</Dato>
                <Dato etiqueta="Concepto" ancho>{textoConcepto(marcando.concepto)} {referenciaLinea(marcando).texto}</Dato>
                <Dato etiqueta="Bruto">{formatPrecio(desgloseDialogo.bruto)}</Dato>
                <Dato etiqueta="Comisión DriveMaster">{unir([desgloseDialogo.comision, desgloseDialogo.nota])}</Dato>
                <Dato etiqueta="Se transfiere" ancho><strong>{formatPrecio(marcando.monto)}</strong></Dato>
              </dl>
              {aviso && (
                <p className={`liq-dinero${aviso.grande ? " liq-dinero--prueba" : ""}`}>
                  {aviso.grande ? aviso.texto : <><strong>Dinero real:</strong> este pago sale de verdad y no se puede deshacer desde aquí.</>}
                </p>
              )}
            </>
          )}
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

      {resolviendo && (
        <ConfirmacionAdmin
          titulo={`Resolver pago · ${formatPrecio(resolviendo.monto)}`}
          texto={`${nombreBeneficiario(resolviendo)}. Revisa primero el panel de Wompi: lo que marques aquí no mueve dinero, solo deja la línea como está en Wompi.`}
          confirmar="Guardar resolución"
          cargando={procesando}
          deshabilitado={!!faltaResolver}
          onConfirmar={() => resolver(resolviendo.id)}
          onCancelar={() => { setResolviendoId(null); setError(null); }}
        >
          <dl className="ui-datos liq-confirmacion">
            <Dato etiqueta="Referencia enviada a Wompi" ancho>{resolviendo.referenciaLiquidacion}</Dato>
            {resolviendo.motivoFallo && <Dato etiqueta="Qué pasó" ancho>{resolviendo.motivoFallo}</Dato>}
          </dl>
          <fieldset className="liq-resultado">
            <legend>¿Qué dice el panel de Wompi?</legend>
            {[["LIQUIDADO", "El pago sí salió", "Queda pagado."], ["FALLIDO", "El pago no salió", "Queda fallido y se podrá reintentar."]].map(([valor, titulo, ayuda]) => (
              <label key={valor} className={`ui-opcion${resolucion.resultado === valor ? " ui-opcion--elegida" : ""}`}>
                <input type="radio" name="liq-resultado" value={valor} checked={resolucion.resultado === valor}
                  onChange={() => setResolucion((r) => ({ ...r, resultado: valor }))} />
                <span><strong>{titulo}</strong><span className="pp-sub">{ayuda}</span></span>
              </label>
            ))}
          </fieldset>
          {resolucion.resultado === "LIQUIDADO" && (
            <Campo id="liq-res-ref" label="Referencia del pago en Wompi">
              <input className="ui-field__input" maxLength={80} required value={resolucion.referencia}
                onChange={(e) => setResolucion((r) => ({ ...r, referencia: e.target.value }))} />
            </Campo>
          )}
          <Campo id="liq-res-motivo" label="Motivo" ayuda="Queda registrado con tu usuario y la fecha.">
            <textarea className="ui-field__input" rows={3} maxLength={500} required value={resolucion.motivo}
              onChange={(e) => setResolucion((r) => ({ ...r, motivo: e.target.value }))} />
          </Campo>
          {faltaResolver && <p className="liq-falta">{faltaResolver}</p>}
          {error && <Alerta>{error}</Alerta>}
        </ConfirmacionAdmin>
      )}

      {detalle && <DetallePlataforma key={`${detalle.tipo}-${detalle.id}`} token={token} objetivo={detalle} onCerrar={() => setDetalle(null)} />}
    </div>
  );
}
