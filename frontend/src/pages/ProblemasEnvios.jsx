import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { problemasDeEnvio, resolverProblemaEnvio } from "../services/pedidoTiendaService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import { capitalizar } from "../utils/formato";
import "../css/admin-base.css";

const abierto = (p) => p.estado === "DESPACHADO" && !p.resolucionProblema;
const RESOLUCION = {
  ENTREGA_CONFIRMADA_POR_ADMIN: "Entrega confirmada",
  REEMBOLSO: "Reembolsado al cliente",
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
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Problemas de envío</h1>
          <p className="adm-encabezado__sub">
            Envíos del catálogo en los que el cliente dijo que algo salió mal. Mientras no los resuelvas, la tienda no cobra ese pedido.
          </p>
        </div>
        {problemas && <span className="adm-encabezado__dato">{abiertos.length} {abiertos.length === 1 ? "abierto" : "abiertos"}</span>}
      </header>

      {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}
      {aviso && (
        <p className="adm-alerta adm-alerta--exito" role="status">
          {aviso}{" "}
          <button type="button" className="adm-btn adm-btn--chico adm-btn--sutil" onClick={() => setAviso(null)}>Cerrar</button>
        </p>
      )}

      <div className="adm-filtros">
        <nav className="adm-pestanas" aria-label="Problemas por estado">
          <button type="button" className={`adm-pestana${ver === "ABIERTOS" ? " adm-pestana--activa" : ""}`}
            aria-pressed={ver === "ABIERTOS"} onClick={() => setVer("ABIERTOS")}>
            Abiertos<span className="adm-pestana__n">{abiertos.length}</span>
          </button>
          <button type="button" className={`adm-pestana${ver === "RESUELTOS" ? " adm-pestana--activa" : ""}`}
            aria-pressed={ver === "RESUELTOS"} onClick={() => setVer("RESUELTOS")}>
            Resueltos<span className="adm-pestana__n">{resueltos.length}</span>
          </button>
        </nav>
      </div>

      <div className="adm-tarjeta" aria-busy={!problemas && !error}>
        {!problemas && !error ? (
          <div className="adm-cargando">Cargando…</div>
        ) : problemas && visibles.length === 0 ? (
          <p className="adm-vacio">{ver === "ABIERTOS" ? "No hay problemas de envío abiertos." : "Aún no has resuelto ningún problema."}</p>
        ) : problemas && (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Pedido</th><th>Destino</th><th className="adm-tabla__der">Monto</th><th>Despacho</th>
                  <th>Reclamo del cliente</th><th>{ver === "ABIERTOS" ? "Reportado" : "Resolución"}</th>
                  {ver === "ABIERTOS" && <th className="adm-tabla__der">Acciones</th>}
                </tr>
              </thead>
              <tbody>
                {visibles.map((p) => (
                  <tr key={p.id} className={abierto(p) ? "adm-tabla__fila--alerta" : ""}>
                    <td data-label="Pedido">
                      <strong>#{refSolicitud(p.id)}</strong>
                      <span className="adm-tabla__sub">{p.tiendaNombre}</span>
                    </td>
                    <td data-label="Destino">{capitalizar(p.envioCiudad) || "—"}</td>
                    <td className="adm-tabla__der" data-label="Monto">{formatPrecio(p.total)}</td>
                    <td data-label="Despacho">
                      {formatFechaHora(p.fechaDespacho)}
                      {p.notaDespacho && <span className="adm-tabla__sub">{p.notaDespacho}</span>}
                    </td>
                    <td data-label="Reclamo del cliente" className="adm-tabla__celda--ancha">«{p.problemaDetalle}»</td>
                    {abierto(p) ? (
                      <>
                        <td data-label="Reportado" className="adm-tabla__nowrap">{formatFechaHora(p.fechaProblema)}</td>
                        <td className="adm-tabla__der adm-tabla__celda--ancha">
                          <div className="adm-tabla__acciones">
                            <button type="button" className="adm-btn adm-btn--chico" onClick={() => pedir(p, "CONFIRMAR_ENTREGA")}>
                              Sí se entregó
                            </button>
                            <button type="button" className="adm-btn adm-btn--chico adm-btn--peligro" onClick={() => pedir(p, "REEMBOLSAR")}>
                              Reembolsar
                            </button>
                          </div>
                        </td>
                      </>
                    ) : (
                      <td data-label="Resolución">
                        <span className={`adm-badge ${p.resolucionProblema === "REEMBOLSO" ? "adm-badge--peligro" : "adm-badge--exito"}`}>
                          {RESOLUCION[p.resolucionProblema] || p.resolucionProblema || "Resuelto"}
                        </span>
                        <span className="adm-tabla__sub">
                          {[p.fechaResolucionProblema && formatFechaHora(p.fechaResolucionProblema),
                            p.resueltoPor && `por ${p.resueltoPor}`,
                            p.motivoResolucion && `Motivo: ${p.motivoResolucion}`].filter(Boolean).join(" · ")}
                        </span>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {decision && (
        <ConfirmacionAdmin
          titulo={textos.titulo}
          texto={textos.mensaje}
          confirmar={textos.textoConfirmar}
          variante={textos.variante}
          cargando={resolviendo}
          deshabilitado={!motivo.trim()}
          onConfirmar={resolver}
          onCancelar={() => setDecision(null)}
        >
          <label className="adm-campo adm-campo--bloque">
            <span>Motivo de tu decisión</span>
            <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} required />
            <span className="adm-campo__ayuda">Queda registrado con tu nombre y fecha.</span>
          </label>
          {errorDecision && <p className="adm-alerta adm-alerta--error" role="alert">{errorDecision}</p>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
