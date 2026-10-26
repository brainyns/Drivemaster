import { useCallback, useEffect, useMemo, useState } from "react";
import { resolverRetiroSinRecoger, retirosSinRecoger } from "../../services/pedidoTiendaService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../../components/servicio/estadosServicio";
import ConfirmacionAdmin from "../../components/admin/ConfirmacionAdmin";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import EstadoVacio from "../../components/ui/EstadoVacio";
import Esqueleto from "../../components/ui/Esqueleto";
import Campo from "../../components/ui/Campo";
import Paginacion from "../../components/ui/Paginacion";
import { paginar, usePaginacion } from "../../components/ui/reglasPaginacion";
import { avisar } from "../../components/ui/avisar";
import { capitalizar, unir } from "../../utils/formato";

const abierto = (r) => r.estado === "LISTO_PARA_RETIRAR" && !r.resolucionRetiro;
const DECISION = {
  SEGUIR_ESPERANDO: { label: "Se sigue esperando", tono: "info" },
  REEMBOLSO: { label: "Cancelado con reembolso", tono: "error" },
};

/**
 * Retiros en la tienda que llevan 15 días o más sin recogerse (después de dos recordatorios al cliente). El ADMIN
 * decide, con motivo escrito: seguir esperando o cancelar con reembolso. Nada se reembolsa solo. Después de "seguir
 * esperando" todavía se puede cancelar; un pedido cancelado o ya retirado no se vuelve a decidir.
 */
export default function RetirosSinRecoger({ token }) {
  const [lista, setLista] = useState(null);
  const [error, setError] = useState(null);
  const [decision, setDecision] = useState(null); // { retiro, tipo }
  const [motivo, setMotivo] = useState("");
  const [resolviendo, setResolviendo] = useState(false);
  const [errorDecision, setErrorDecision] = useState(null);

  const cargar = useCallback(async () => {
    try {
      setLista(await retirosSinRecoger(token));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [token]);

  useEffect(() => {
    const t = setTimeout(cargar, 0);
    return () => clearTimeout(t);
  }, [cargar]);

  const pendientes = useMemo(() => (lista || []).filter(abierto), [lista]);
  const decididos = useMemo(() => (lista || []).filter((r) => !abierto(r)), [lista]);
  // Los que se decidió seguir esperando siguen listos: se pueden cancelar si el cliente nunca llega
  const esperando = useMemo(() => decididos.filter((r) => r.resolucionRetiro === "SEGUIR_ESPERANDO" && r.estado === "LISTO_PARA_RETIRAR"), [decididos]);

  // Una sola lista en orden: por decidir, los que se decidió seguir esperando (aún se pueden cancelar) y los demás
  const filas = useMemo(() => [
    ...pendientes.map((r) => ({ r, conEsperar: true })),
    ...esperando.map((r) => ({ r, conEsperar: false })),
    ...decididos.filter((r) => !esperando.includes(r)).map((r) => ({ r, decidido: true })),
  ], [pendientes, esperando, decididos]);
  const pag = usePaginacion(filas.length);
  const enPagina = paginar(filas, pag.pagina);

  const pedir = (retiro, tipo) => { setMotivo(""); setErrorDecision(null); setDecision({ retiro, tipo }); };

  const resolver = async () => {
    const { retiro, tipo } = decision;
    setResolviendo(true);
    setErrorDecision(null);
    try {
      await resolverRetiroSinRecoger(token, retiro.id, tipo, motivo.trim());
    } catch (e) {
      setErrorDecision(e.message);
      setResolviendo(false);
      await cargar(); // si el cliente lo retiró o alguien ya decidió, la lista lo refleja
      return;
    }
    setResolviendo(false);
    setDecision(null);
    avisar(tipo === "REEMBOLSO"
      ? `Cancelamos el pedido y registramos el reembolso de ${formatPrecio(retiro.total)}. Recuerda hacerlo en Wompi y marcarlo en Liquidaciones y reembolsos.`
      : "Listo: el pedido sigue esperando al cliente en la tienda.");
    await cargar();
  };

  const textos = decision && {
    SEGUIR_ESPERANDO: {
      titulo: "Seguir esperando al cliente",
      mensaje: `El pedido sigue listo en ${decision.retiro.tiendaNombre} y el cliente lo puede retirar con su código. Más adelante todavía podrás cancelarlo.`,
      confirmar: "Seguir esperando", variante: "primario",
    },
    REEMBOLSO: {
      titulo: "Cancelar el pedido y reembolsar",
      mensaje: `Se cancela el pedido, se anula el pago a ${decision.retiro.tiendaNombre}, las unidades en stock vuelven a su inventario y queda pendiente un reembolso de ${formatPrecio(decision.retiro.total)} para el cliente (lo haces tú en Wompi). No se puede deshacer.`,
      confirmar: "Cancelar y reembolsar", variante: "peligro",
    },
  }[decision.tipo];

  const fila = (r, acciones) => (
    <tr key={r.id}>
      <td className="ui-table__titulo">
        <strong className="pre-ref">#{refSolicitud(r.id)}</strong>
        <span className="pp-sub">{r.tiendaNombre}</span>
      </td>
      <td data-label="Ciudad">{capitalizar(r.tiendaCiudad) || <span className="pp-tenue">Sin ciudad</span>}</td>
      <td data-label="Monto" className="pp-num">{formatPrecio(r.total)}</td>
      <td data-label="Listo desde" className="pre-despacho">
        {formatFechaHora(r.fechaListoRetiro)}
        <span className="pp-sub">Hace {r.diasSinRetirar} días · {r.recordatoriosRetiro || 0} recordatorios</span>
      </td>
      {acciones ? (
        <td className="ui-table__acciones">{acciones}</td>
      ) : (
        <td data-label="Decisión" className="pre-resolucion">
          <ChipEstado tono={DECISION[r.resolucionRetiro]?.tono || "info"}>
            {DECISION[r.resolucionRetiro]?.label || (r.estado === "RETIRADO" ? "El cliente lo retiró" : "Resuelto")}
          </ChipEstado>
          <span className="pp-sub">
            {unir([r.fechaResolucionRetiro && formatFechaHora(r.fechaResolucionRetiro),
              r.retiroResueltoPor && `por ${r.retiroResueltoPor}`, r.motivoResolucionRetiro && `Motivo: ${r.motivoResolucionRetiro}`])}
          </span>
        </td>
      )}
    </tr>
  );

  const acciones = (r, conEsperar) => (
    <>
      {conEsperar && (
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => pedir(r, "SEGUIR_ESPERANDO")}
          aria-label={`Seguir esperando el pedido #${refSolicitud(r.id)}`}>
          Seguir esperando
        </button>
      )}
      <button type="button" className="ui-btn ui-btn--texto ui-btn--chico pre-peligro" onClick={() => pedir(r, "REEMBOLSO")}
        aria-label={`Cancelar y reembolsar el pedido #${refSolicitud(r.id)}`}>
        Cancelar y reembolsar
      </button>
    </>
  );

  return (
    <section className="pre-retiros" aria-labelledby="pre-retiros-titulo">
      <div className="pre-retiros__cabecera">
        <h2 id="pre-retiros-titulo" className="pre-retiros__titulo">Retiros sin recoger</h2>
        <p className="pp-sub">
          Pedidos listos en la tienda que el cliente no recoge hace 15 días o más, después de dos recordatorios. Tú decides:
          nada se reembolsa solo.
        </p>
      </div>

      {error && <Alerta className="pp-bloque-sep" onReintentar={cargar}>{lista ? `No pudimos actualizar la lista: ${error}` : error}</Alerta>}

      {!lista ? (
        !error && <Esqueleto variante="lista" cantidad={2} texto="Cargando retiros sin recoger…" />
      ) : pendientes.length + esperando.length === 0 && decididos.length === 0 ? (
        <EstadoVacio icono="check" nivel={3} compacto titulo="Ningún retiro sin recoger"
          texto="Cuando un pedido lleve 15 días listo en la tienda sin que el cliente pase, aparece aquí para que decidas." />
      ) : (
        <div className="ui-card ui-table-card">
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas pre-tabla">
              <caption className="ui-sr">Retiros sin recoger</caption>
              <thead>
                <tr>
                  <th scope="col">Pedido</th>
                  <th scope="col">Ciudad</th>
                  <th scope="col" className="pp-num">Monto</th>
                  <th scope="col">Listo desde</th>
                  <th scope="col">Decisión</th>
                </tr>
              </thead>
              <tbody>
                {enPagina.map(({ r, conEsperar, decidido }) => fila(r, decidido ? null : acciones(r, conEsperar)))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {filas.length > 0 && (
        <Paginacion pagina={pag.pagina} total={filas.length} onCambiar={pag.cambiar} etiqueta="Páginas de retiros sin recoger" />
      )}

      {decision && (
        <ConfirmacionAdmin titulo={textos.titulo} texto={textos.mensaje} confirmar={textos.confirmar} variante={textos.variante}
          cargando={resolviendo} deshabilitado={!motivo.trim()} onConfirmar={resolver} onCancelar={() => setDecision(null)}>
          <Campo id="pre-retiro-motivo" label="Motivo de tu decisión" ayuda="Queda registrado con tu nombre y fecha.">
            <textarea className="ui-field__input pre-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} required />
          </Campo>
          {errorDecision && <Alerta>{errorDecision}</Alerta>}
        </ConfirmacionAdmin>
      )}
    </section>
  );
}
