import { useEffect, useState } from "react";
import { pedidosDeOrden, confirmarRepuestosRecibidos } from "../../services/pedidoTiendaService";
import { ESTADOS_PEDIDO } from "./estadosServicio";
import ConfirmarAccion from "../marketplace/ConfirmarAccion";
import ChipEstado from "../ui/ChipEstado";

const SIN_DESPACHO = ["PENDIENTE", "EN_PREPARACION"];
const TEXTO_FALTANTES = "Confirmo que recibí lo demás y sigo sin esas piezas";

/**
 * Confirmación de "repuestos recibidos": lista cada pedido de la orden con su tienda y estado, avisa de las
 * tiendas que no marcaron el despacho y, si alguna no pudo despachar, muestra el motivo y exige confirmar que
 * el taller sigue sin esas piezas (el backend rechaza la petición sin esa confirmación).
 * Los pedidos se cargan al abrir, para decidir con el estado del momento.
 */
export default function ConfirmarRecibidos({ token, solicitud, onConfirmado, onCancelar }) {
  const [pedidos, setPedidos] = useState(null);
  const [error, setError] = useState(null);
  const [aceptaFaltantes, setAceptaFaltantes] = useState(false);

  useEffect(() => {
    let cancelado = false;
    pedidosDeOrden(token, solicitud.id)
      .then((p) => { if (!cancelado) setPedidos(p || []); })
      .catch((e) => { if (!cancelado) setError(e.message); });
    return () => { cancelado = true; };
  }, [token, solicitud.id]);

  const sinDespacho = (pedidos || []).filter((p) => SIN_DESPACHO.includes(p.estado));
  const noDespachados = (pedidos || []).filter((p) => p.estado === "NO_DESPACHADO");
  const hayFaltantes = noDespachados.length > 0;
  // Sin ningún repuesto no hay trabajo que hacer: el backend lo rechaza y DriveMaster ya recibió la alerta
  const ningunoLlego = pedidos?.length > 0 && noDespachados.length === pedidos.length;

  const confirmar = async () => {
    await confirmarRepuestosRecibidos(token, solicitud.id, hayFaltantes && aceptaFaltantes);
    await onConfirmado();
  };

  return (
    <ConfirmarAccion titulo="¿Confirmar repuestos recibidos?"
      mensaje="La solicitud pasa a repuestos recibidos y se habilita el pago de cada tienda que entregó. No se puede deshacer."
      textoConfirmar="Sí, confirmar recepción"
      puedeConfirmar={pedidos !== null && !ningunoLlego && (!hayFaltantes || aceptaFaltantes)}
      onCancelar={onCancelar} onConfirmar={confirmar}>
      {error && <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>}
      {pedidos === null && !error && <p className="ss-meta">Cargando pedidos…</p>}
      {pedidos !== null && (
        <ul className="ct-items">
          {pedidos.map((p) => (
            <li key={p.id} className="ct-item">
              <span className="ct-item__desc">
                <strong>{p.tiendaNombre}</strong>
                <span className="ss-meta">{(p.items || []).map((i) => `${i.cantidad} × ${i.productoNombre}`).join(", ")}</span>
                {p.estado === "NO_DESPACHADO" && (
                  <span className="ss-meta">Motivo: {p.motivoNoDespacho || "sin motivo registrado"}</span>
                )}
              </span>
              <ChipEstado estado={p.estado} catalogo={ESTADOS_PEDIDO} />
            </li>
          ))}
        </ul>
      )}
      {sinDespacho.map((p) => (
        <p key={p.id} className="mk-aviso mk-aviso--aviso">La tienda {p.tiendaNombre} no ha marcado el despacho.</p>
      ))}
      {ningunoLlego && (
        <p className="mk-aviso mk-aviso--error">
          Ninguna tienda pudo despachar los repuestos: no se puede continuar con esta solicitud. DriveMaster la revisará.
        </p>
      )}
      {hayFaltantes && !ningunoLlego && (
        <div className="ss-confirmar">
          <p className="mk-aviso mk-aviso--error">
            {noDespachados.length === 1
              ? `${noDespachados[0].tiendaNombre} no pudo despachar sus repuestos.`
              : `${noDespachados.map((p) => p.tiendaNombre).join(", ")} no pudieron despachar sus repuestos.`}
          </p>
          <label className="ss-confirmar__check">
            <input type="checkbox" checked={aceptaFaltantes} onChange={(e) => setAceptaFaltantes(e.target.checked)} />
            {TEXTO_FALTANTES}
          </label>
        </div>
      )}
    </ConfirmarAccion>
  );
}
