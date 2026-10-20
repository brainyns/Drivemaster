import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { problemasDeEnvio, resolverProblemaEnvio } from "../services/pedidoTiendaService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import { capitalizar, unir } from "../utils/formato";
// El diálogo de confirmación (ConfirmacionAdmin) trae su estilo de admin-base.css
import "../css/admin-base.css";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/problemas-envio.css";

const abierto = (p) => p.estado === "DESPACHADO" && !p.resolucionProblema;
const RESOLUCION = {
  ENTREGA_CONFIRMADA_POR_ADMIN: { label: "Entrega confirmada", tono: "ok" },
  REEMBOLSO: { label: "Reembolsado al cliente", tono: "error" },
};

/**
 * Problemas de envío reportados por clientes (solo ADMIN/SUPERADMIN). Cada decisión exige un motivo
 * escrito, muestra el monto en juego y pide confirmación; una vez resuelto el problema ya no se puede
 * volver a decidir (el backend lo rechaza aunque otra persona lo intente a la vez).
 */
export default function ProblemasEnvios() {
  const token = getToken();
  const [problemas, setProblemas] = useState(null);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const [ver, setVer] = useState("ABIERTOS");
  const [decision, setDecision] = useState(null); // { pedido, resolucion }
  const [motivo, setMotivo] = useState("");
  const [resolviendo, setResolviendo] = useState(false);
  const [errorDecision, setErrorDecision] = useState(null);

  const cargar = useCallback(async () => {
    try {
      setProblemas(await problemasDeEnvio(token));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [token]);

  useEffect(() => {
    let vivo = true;
    problemasDeEnvio(token)
      .then((lista) => { if (vivo) setProblemas(lista); })
      .catch((e) => { if (vivo) setError(e.message); });
    return () => { vivo = false; };
  }, [token]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const abiertos = useMemo(() => (problemas || []).filter(abierto), [problemas]);
  const resueltos = useMemo(() => (problemas || []).filter((p) => !abierto(p)), [problemas]);
  const visibles = ver === "ABIERTOS" ? abiertos : resueltos;

  const pedir = (pedido, resolucion) => {
    setMotivo("");
    setErrorDecision(null);
    setDecision({ pedido, resolucion });
  };

  const resolver = async () => {
    const { pedido, resolucion } = decision;
    setResolviendo(true);
    setErrorDecision(null);
    try {
      await resolverProblemaEnvio(token, pedido.id, resolucion, motivo.trim());
    } catch (e) {
      setErrorDecision(e.message);
      setResolviendo(false);
      await cargar(); // si otra persona ya lo resolvió, la lista lo refleja
      return;
    }
    setResolviendo(false);
    setDecision(null);
    setAviso(resolucion === "REEMBOLSAR"
      ? `Registramos el reembolso de ${formatPrecio(pedido.total)}. Recuerda hacerlo en el panel de Wompi y marcarlo en Liquidaciones y reembolsos.`
      : `Entrega confirmada: el pago de ${formatPrecio(pedido.total)} a ${pedido.tiendaNombre} quedó habilitado.`);
    await cargar();
  };

  const textos = decision && {
    CONFIRMAR_ENTREGA: {
      titulo: "Confirmar que el pedido sí se entregó",
      mensaje: `El pedido de ${decision.pedido.tiendaNombre} quedará como entregado y se habilitará el pago de ${formatPrecio(decision.pedido.total)} a la tienda. Avisaremos al cliente. Esta decisión no se puede deshacer.`,
      textoConfirmar: "Confirmar entrega",
      variante: "primario",
    },
    REEMBOLSAR: {
      titulo: "Reembolsar al cliente",
      mensaje: `Se anulará el pago a ${decision.pedido.tiendaNombre} y quedará pendiente un reembolso de ${formatPrecio(decision.pedido.total)} para el cliente (lo haces tú en el panel de Wompi). Esta decisión no se puede deshacer.`,
      textoConfirmar: "Reembolsar",
      variante: "peligro",
    },
  }[decision.resolucion];

  return (
    <div className="ss-panel--plataforma pp-root pre-root">
      <EncabezadoPagina titulo="Problemas de envío"
        descripcion="Envíos del catálogo en los que el cliente dijo que algo salió mal. Mientras no los resuelvas, la tienda no cobra ese pedido." />

      {aviso && <Alerta tono="ok" className="pp-bloque-sep" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}
      {error && (
        <Alerta className="pp-bloque-sep" onReintentar={reintentar} reintentando={reintentando}>
          {problemas ? `No pudimos actualizar la lista: ${error}` : error}
        </Alerta>
      )}

      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Problemas por estado">
        <button type="button" className="ui-chip" aria-pressed={ver === "ABIERTOS"} onClick={() => setVer("ABIERTOS")}>
          Abiertos {problemas && <span className="ui-chip__contador">{abiertos.length}</span>}
        </button>
        <button type="button" className="ui-chip" aria-pressed={ver === "RESUELTOS"} onClick={() => setVer("RESUELTOS")}>
          Resueltos {problemas && <span className="ui-chip__contador">{resueltos.length}</span>}
        </button>
      </div>

      {!problemas ? (
        !error && <Esqueleto variante="lista" cantidad={4} texto="Cargando problemas de envío…" />
      ) : visibles.length === 0 ? (
        ver === "ABIERTOS" ? (
          <EstadoVacio icono="check" titulo="No hay problemas de envío abiertos"
            texto="Cuando un cliente reporte que su envío no llegó o llegó mal, aparece aquí para que decidas." />
        ) : (
          <EstadoVacio icono="file" titulo="Aún no has resuelto ningún problema"
            texto="Las decisiones que tomes quedan aquí, con el motivo, quién las tomó y cuándo." />
        )
      ) : (
        <div className="ui-card ui-table-card">
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas pre-tabla">
              <caption className="ui-sr">Problemas de envío {ver === "ABIERTOS" ? "abiertos" : "resueltos"}</caption>
              <thead>
                <tr>
                  <th scope="col">Pedido</th>
                  <th scope="col">Destino</th>
                  <th scope="col" className="pp-num">Monto</th>
                  <th scope="col">Despacho</th>
                  <th scope="col">Reclamo del cliente</th>
                  <th scope="col">{ver === "ABIERTOS" ? "Reportado" : "Resolución"}</th>
                  {ver === "ABIERTOS" && <th scope="col"><span className="ui-sr">Acciones</span></th>}
                </tr>
              </thead>
              <tbody>
                {visibles.map((p) => {
                  const res = RESOLUCION[p.resolucionProblema];
                  return (
                    <tr key={p.id}>
                      <td className="ui-table__titulo">
                        <strong className="pre-ref">#{refSolicitud(p.id)}</strong>
                        <span className="pp-sub">{p.tiendaNombre}</span>
                      </td>
                      <td data-label="Destino">{capitalizar(p.envioCiudad) || <span className="pp-tenue">Sin ciudad</span>}</td>
                      <td data-label="Monto" className="pp-num">{formatPrecio(p.total)}</td>
                      <td data-label="Despacho" className="pre-despacho">
                        {formatFechaHora(p.fechaDespacho)}
                        {p.notaDespacho && <span className="pp-sub">{p.notaDespacho}</span>}
                      </td>
                      <td data-label="Reclamo del cliente" className="pre-reclamo"><q>{p.problemaDetalle}</q></td>
                      {abierto(p) ? (
                        <>
                          <td data-label="Reportado" className="pre-fecha">{formatFechaHora(p.fechaProblema)}</td>
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => pedir(p, "CONFIRMAR_ENTREGA")}
                              aria-label={`Sí se entregó el pedido #${refSolicitud(p.id)}`}>
                              Sí se entregó
                            </button>
                            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico pre-peligro" onClick={() => pedir(p, "REEMBOLSAR")}
                              aria-label={`Reembolsar el pedido #${refSolicitud(p.id)}`}>
                              Reembolsar
                            </button>
                          </td>
                        </>
                      ) : (
                        <td data-label="Resolución" className="pre-resolucion">
                          <ChipEstado tono={res?.tono || "info"}>{res?.label || p.resolucionProblema || "Resuelto"}</ChipEstado>
                          <span className="pp-sub">
                            {unir([p.fechaResolucionProblema && formatFechaHora(p.fechaResolucionProblema),
                              p.resueltoPor && `por ${p.resueltoPor}`, p.motivoResolucion && `Motivo: ${p.motivoResolucion}`])}
                          </span>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {decision && (
        <ConfirmacionAdmin titulo={textos.titulo} texto={textos.mensaje} confirmar={textos.textoConfirmar} variante={textos.variante}
          cargando={resolviendo} deshabilitado={!motivo.trim()} onConfirmar={resolver} onCancelar={() => setDecision(null)}>
          <Campo id="pre-motivo" label="Motivo de tu decisión" ayuda="Queda registrado con tu nombre y fecha.">
            <textarea className="ui-field__input pre-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} required />
          </Campo>
          {errorDecision && <Alerta>{errorDecision}</Alerta>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
