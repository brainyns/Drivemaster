import { useEffect, useState } from "react";
import { pedidosDeOrden, confirmarRepuestosRecibidos } from "../../services/pedidoTiendaService";
import { ESTADOS_PEDIDO } from "./estadosServicio";
import { SIN_DESPACHO, TEXTO_SIN_DESPACHO, estadoRecibidos } from "./reglasRecibidos";
import ConfirmarAccion from "../marketplace/ConfirmarAccion";
import ChipEstado from "../ui/ChipEstado";

const TEXTO_FALTANTES = "Confirmo que recibí lo demás y sigo sin esas piezas";

const articulos = (p) => (p.items || []).map((i) => `${i.cantidad} × ${i.productoNombre}`).join(", ");

/**
 * Confirmación de repuestos pedido por pedido: el taller marca los que tiene en la mano y solo puede marcar los que la
 * tienda despachó, que son los que habilitan su pago. Si alguna tienda no pudo despachar, muestra el motivo y exige
 * aceptar que sigue sin esas piezas antes de avanzar. Los pedidos se cargan al abrir, para decidir con el estado del momento.
 */
export default function ConfirmarRecibidos({ token, solicitud, onConfirmado, onCancelar }) {
  const [pedidos, setPedidos] = useState(null);
  const [error, setError] = useState(null);
  const [elegidos, setElegidos] = useState([]);
  const [aceptaFaltantes, setAceptaFaltantes] = useState(false);

  useEffect(() => {
    let cancelado = false;
    pedidosDeOrden(token, solicitud.id)
      .then((p) => { if (!cancelado) setPedidos(p || []); })
      .catch((e) => { if (!cancelado) setError(e.message); });
    return () => { cancelado = true; };
  }, [token, solicitud.id]);

  const { noDespachados, validos, ningunoLlego, avanza, pideFaltantes, puedeConfirmar } = estadoRecibidos(pedidos || [], elegidos, aceptaFaltantes);

  const alternar = (id) => setElegidos((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));

  const confirmar = async () => {
    const actualizados = await confirmarRepuestosRecibidos(token, solicitud.id, validos, pideFaltantes && aceptaFaltantes);
    await onConfirmado(actualizados);
  };

  return (
    <ConfirmarAccion titulo="¿Qué repuestos tienes en el taller?"
      mensaje={avanza
        ? "La solicitud pasa a repuestos recibidos y se habilita el pago de cada tienda confirmada. No se puede deshacer."
        : "Marca solo lo que ya llegó: se habilita el pago de esa tienda y la solicitud sigue esperando lo demás. No se puede deshacer."}
      textoConfirmar={avanza ? "Sí, confirmar recepción" : "Confirmar lo que llegó"}
      puedeConfirmar={pedidos !== null && puedeConfirmar}
      onCancelar={onCancelar} onConfirmar={confirmar}>
      {error && <p className="mk-aviso mk-aviso--error" role="alert">{error}</p>}
      {pedidos === null && !error && <p className="ss-meta">Cargando pedidos…</p>}
      {pedidos !== null && (
        <ul className="ct-items">
          {pedidos.map((p) => (
            <li key={p.id} className="ct-item">
              {p.estado === "DESPACHADO" ? (
                <label className="ss-recibido">
                  <input type="checkbox" checked={elegidos.includes(p.id)} onChange={() => alternar(p.id)} />
                  <span className="ct-item__desc">
                    <strong>{p.tiendaNombre}</strong>
                    <span className="ss-meta">{articulos(p)}</span>
                  </span>
                </label>
              ) : (
                <span className="ct-item__desc">
                  <strong>{p.tiendaNombre}</strong>
                  <span className="ss-meta">{articulos(p)}</span>
                  {SIN_DESPACHO.includes(p.estado) && <span className="ss-recibido__espera">{TEXTO_SIN_DESPACHO}</span>}
                  {p.estado === "NO_DESPACHADO" && (
                    <span className="ss-meta">Motivo: {p.motivoNoDespacho || "sin motivo registrado"}</span>
                  )}
                </span>
              )}
              <ChipEstado estado={p.estado} catalogo={ESTADOS_PEDIDO} />
            </li>
          ))}
        </ul>
      )}
      {ningunoLlego && (
        <p className="mk-aviso mk-aviso--error">
          Ninguna tienda pudo despachar los repuestos: no se puede continuar con esta solicitud. DriveMaster la revisará.
        </p>
      )}
      {pideFaltantes && (
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
