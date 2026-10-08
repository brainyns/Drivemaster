import { useCallback, useEffect, useMemo, useState } from "react";
import { getToken } from "../services/authService";
import { problemasDeEnvio, resolverProblemaEnvio } from "../services/pedidoTiendaService";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import "../css/marketplace.css";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";

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

  const resolver = async (motivo) => {
    const { pedido, resolucion } = decision;
    try {
      await resolverProblemaEnvio(token, pedido.id, resolucion, motivo);
    } catch (e) {
      await cargar(); // si otra persona ya lo resolvió, la lista lo refleja
      throw e;
    }
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
    },
    REEMBOLSAR: {
      titulo: "Reembolsar al cliente",
      mensaje: `Se anulará el pago a ${decision.pedido.tiendaNombre} y quedará pendiente un reembolso de ${formatPrecio(decision.pedido.total)} para el cliente (lo haces tú en el panel de Wompi). Esta decisión no se puede deshacer.`,
      textoConfirmar: "Reembolsar",
      peligro: true,
    },
  }[decision.resolucion];

  return (
    <div className="ss-panel ss-panel--plataforma">
      <p className="ss-ayuda">
        Envíos del catálogo en los que el cliente dijo que algo salió mal. Mientras no los resuelvas, la tienda no cobra ese pedido.
      </p>

      {error && <p className="ss-alerta" role="alert">{error}</p>}
      {aviso && <p className="ss-exito" role="status">{aviso} <button className="sv-btn-outline" onClick={() => setAviso(null)}>Cerrar</button></p>}

      <nav className="ss-filtros" aria-label="Problemas por estado">
        <button className={`ss-filtro${ver === "ABIERTOS" ? " ss-filtro--activo" : ""}`} onClick={() => setVer("ABIERTOS")}
          aria-current={ver === "ABIERTOS" ? "page" : undefined}>
          Abiertos{abiertos.length > 0 && <span className="ss-filtro__conteo">{abiertos.length}</span>}
        </button>
        <button className={`ss-filtro${ver === "RESUELTOS" ? " ss-filtro--activo" : ""}`} onClick={() => setVer("RESUELTOS")}
          aria-current={ver === "RESUELTOS" ? "page" : undefined}>
          Resueltos
        </button>
      </nav>

      {!problemas && !error && <p className="sv-empty">Cargando...</p>}
      {problemas && visibles.length === 0 && (
        <p className="sv-empty">{ver === "ABIERTOS" ? "No hay problemas de envío abiertos." : "Aún no has resuelto ningún problema."}</p>
      )}

      <div className="sv-list">
        {visibles.map((p) => (
          <section key={p.id} className="ss-bloque ss-bloque--destacado" aria-labelledby={`prob-${p.id}`}>
            <div className="ct-detalle__cabecera">
              <span className="ss-eyebrow" id={`prob-${p.id}`}>Pedido #{refSolicitud(p.id)} · {p.tiendaNombre}</span>
              <strong>{formatPrecio(p.total)}</strong>
            </div>

            <dl className="ct-totales">
              <div className="ct-totales__fila"><dt>Monto del pedido</dt><dd>{formatPrecio(p.total)}</dd></div>
              <div className="ct-totales__fila"><dt>Despachado</dt><dd>{formatFechaHora(p.fechaDespacho)}</dd></div>
              <div className="ct-totales__fila"><dt>Problema reportado</dt><dd>{formatFechaHora(p.fechaProblema)}</dd></div>
              {p.notaDespacho && <div className="ct-totales__fila"><dt>Guía / información</dt><dd>{p.notaDespacho}</dd></div>}
            </dl>

            <div className="ct-pedido__entrega">
              Ciudad de destino: <strong>{p.envioCiudad || "—"}</strong>
            </div>
            <p className="ss-alerta" role="note">Lo que dijo el cliente: «{p.problemaDetalle}»</p>

            {abierto(p) ? (
              <div className="ss-acciones">
                <button className="sv-btn-outline" onClick={() => setDecision({ pedido: p, resolucion: "CONFIRMAR_ENTREGA" })}>
                  Sí se entregó
                </button>
                <button className="sv-btn ss-btn-peligro" onClick={() => setDecision({ pedido: p, resolucion: "REEMBOLSAR" })}>
                  Reembolsar al cliente
                </button>
              </div>
            ) : (
              <p className="ss-exito" role="status">
                {RESOLUCION[p.resolucionProblema] || p.resolucionProblema}
                {p.fechaResolucionProblema ? ` · ${formatFechaHora(p.fechaResolucionProblema)}` : ""}
                {p.resueltoPor ? ` · por ${p.resueltoPor}` : ""}
                {p.motivoResolucion ? ` · Motivo: ${p.motivoResolucion}` : ""}
              </p>
            )}
          </section>
        ))}
      </div>

      {decision && (
        <ConfirmarAccion {...textos} pedirMotivo etiquetaMotivo="Motivo de tu decisión"
          ayudaMotivo="Queda registrado con tu nombre y fecha." onConfirmar={resolver} onCancelar={() => setDecision(null)} />
      )}
    </div>
  );
}
