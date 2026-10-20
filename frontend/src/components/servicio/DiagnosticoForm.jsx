import { useState } from "react";
import { registrarDiagnostico } from "../../services/solicitudServicioService";
import SubidaFotos from "./SubidaFotos";
import BuscadorRepuestos from "./BuscadorRepuestos";
import { ESTADO_OFERTA, TEXTO_CONFIRMACION, URGENCIAS, formatPrecio } from "./estadosServicio";
import Icon from "../public/Icon";

const MIN_HALLAZGOS = 10;

/** Repuestos guardados en la orden → formato de trabajo del formulario. */
function desdeOrden(repuestos = []) {
  return repuestos.map((r) => ({
    productoId: r.productoId,
    descripcion: r.descripcion,
    cantidad: r.cantidad,
    nombre: r.productoNombre,
    marca: r.marca,
    referencia: r.referencia,
    tiendaNombre: r.tiendaNombre,
    precio: r.precioReferencia,
    disponibilidad: r.disponibilidad,
    diasEntrega: r.diasEntrega,
    requiereConfirmacion: r.compatibilidad === "POR_CONFIRMAR",
    confirmado: !!r.compatibilidadConfirmada,
  }));
}

/**
 * El mecánico registra (o corrige) el diagnóstico y los repuestos necesarios.
 * Los repuestos se eligen en la ventana del buscador y se guardan junto con el
 * diagnóstico. Lo "por confirmar" exige marcar la confirmación de compatibilidad.
 */
export default function DiagnosticoForm({ token, solicitud, mecanicos, onGuardado, onCancelar }) {
  const previo = solicitud.diagnostico;
  const [hallazgos, setHallazgos] = useState(previo?.hallazgos || "");
  const [fallas, setFallas] = useState((previo?.fallas || []).join("\n"));
  const [urgencia, setUrgencia] = useState(previo?.urgencia || "");
  const [observaciones, setObservaciones] = useState(previo?.observaciones || "");
  const [fotos, setFotos] = useState(previo?.fotos || []);
  const [mecanicoId, setMecanicoId] = useState(previo?.mecanicoId || solicitud.mecanicoId || "");
  const [repuestos, setRepuestos] = useState(() => desdeOrden(solicitud.repuestos));
  const [buscadorAbierto, setBuscadorAbierto] = useState(false);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const activos = mecanicos.filter((m) => m.activo !== false || m.id === mecanicoId);
  const elegidos = new Set(repuestos.map((r) => r.productoId));
  const total = repuestos.reduce((s, r) => s + (r.precio || 0) * (r.cantidad || 0), 0);
  const tiendas = new Set(repuestos.map((r) => r.tiendaNombre)).size;

  const elegir = (grupo, oferta, cantidad) => {
    if (elegidos.has(oferta.productoId)) return;
    setRepuestos((prev) => [...prev, {
      productoId: oferta.productoId,
      descripcion: grupo.nombre,
      cantidad,
      nombre: oferta.nombre,
      marca: oferta.marca,
      referencia: oferta.referencia,
      tiendaNombre: oferta.tiendaNombre,
      precio: oferta.precio,
      disponibilidad: oferta.disponibilidad,
      diasEntrega: oferta.diasEntrega,
      requiereConfirmacion: oferta.requiereConfirmacion,
      confirmado: false,
    }]);
  };

  const actualizar = (productoId, cambios) =>
    setRepuestos((prev) => prev.map((r) => (r.productoId === productoId ? { ...r, ...cambios } : r)));

  const quitar = (productoId) => setRepuestos((prev) => prev.filter((r) => r.productoId !== productoId));

  const guardar = async (e) => {
    e.preventDefault();
    if (hallazgos.trim().length < MIN_HALLAZGOS) return setError(`Describe los hallazgos (mínimo ${MIN_HALLAZGOS} caracteres).`);
    if (!urgencia) return setError("Indica la urgencia.");
    if (!mecanicoId) return setError("Elige el mecánico que hizo la revisión.");
    const sinConfirmar = repuestos.find((r) => r.requiereConfirmacion && !r.confirmado);
    if (sinConfirmar) return setError(`Confirma la compatibilidad de "${sinConfirmar.nombre}" o quítalo de la lista.`);

    setGuardando(true);
    setError(null);
    try {
      const actualizada = await registrarDiagnostico(token, solicitud.id, {
        hallazgos: hallazgos.trim(),
        fallas: fallas.split("\n").map((f) => f.trim()).filter(Boolean),
        urgencia,
        observaciones: observaciones.trim() || null,
        fotos,
        mecanicoId,
        repuestos: repuestos.map((r) => ({
          productoId: r.productoId,
          descripcion: r.descripcion?.trim() || r.nombre,
          cantidad: Number(r.cantidad),
          confirmoCompatibilidad: r.requiereConfirmacion ? r.confirmado : null,
        })),
      });
      onGuardado(actualizada);
    } catch (err) {
      setError(err.message);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <>
    <form className="ss-bloque ss-form" onSubmit={guardar} noValidate>
      <h2 className="ss-bloque__titulo">{previo ? "Editar diagnóstico y repuestos" : "Registrar diagnóstico"}</h2>

      <div className="sv-field">
        <label htmlFor="ss-diag-mecanico">Mecánico</label>
        <select id="ss-diag-mecanico" value={mecanicoId} onChange={(e) => setMecanicoId(e.target.value)}>
          <option value="">Elige el mecánico</option>
          {activos.map((m) => <option key={m.id} value={m.id}>{m.nombre}</option>)}
        </select>
        {activos.length === 0 && <span className="ss-ayuda">Registra mecánicos en la pestaña “Mi taller”.</span>}
      </div>

      <div className="sv-field">
        <label htmlFor="ss-diag-hallazgos">Hallazgos</label>
        <textarea id="ss-diag-hallazgos" rows={5} maxLength={3000} value={hallazgos}
          onChange={(e) => setHallazgos(e.target.value)}
          placeholder="Qué se revisó y qué se encontró." />
      </div>

      <div className="sv-field">
        <label htmlFor="ss-diag-fallas">Fallas encontradas (una por línea)</label>
        <textarea id="ss-diag-fallas" rows={4} value={fallas} onChange={(e) => setFallas(e.target.value)}
          placeholder={"Pastillas delanteras al 10 %\nDisco delantero izquierdo rayado"} />
      </div>

      <fieldset className="ss-urgencias">
        <legend>Urgencia</legend>
        {URGENCIAS.map((u) => (
          <label key={u.valor} className={`ss-urgencia-opcion ss-urgencia-opcion--${u.valor.toLowerCase()}${urgencia === u.valor ? " is-activa" : ""}`}>
            <input type="radio" name="urgencia" value={u.valor} checked={urgencia === u.valor}
              onChange={() => setUrgencia(u.valor)} />
            <strong>{u.label}</strong>
            <span>{u.detalle}</span>
          </label>
        ))}
      </fieldset>

      <div className="sv-field">
        <label htmlFor="ss-diag-obs">Observaciones para el cliente (opcional)</label>
        <textarea id="ss-diag-obs" rows={3} maxLength={1000} value={observaciones}
          onChange={(e) => setObservaciones(e.target.value)} />
      </div>

      <div className="sv-field">
        <label>Fotos de la revisión (opcional)</label>
        <SubidaFotos token={token} fotos={fotos} onChange={setFotos} max={8} />
      </div>

      <section className="ss-repuestos" aria-labelledby="ss-repuestos-titulo">
        <div className="ss-repuestos__cabecera">
          <div>
            <h3 id="ss-repuestos-titulo" className="ss-bloque__titulo">Repuestos necesarios</h3>
            <span className="ss-meta">
              {repuestos.length === 0
                ? "Sin repuestos: la reparación sería solo mano de obra."
                : `Total de referencia ${formatPrecio(total)} · ${tiendas === 1 ? "todo en una sola tienda" : `${tiendas} tiendas: un pedido por tienda`}`}
            </span>
          </div>
          <button type="button" className="sv-btn-outline" onClick={() => setBuscadorAbierto(true)}>Buscar repuestos</button>
        </div>

        {repuestos.length > 0 && (
          <ul className="ss-repuestos__lista">
            {repuestos.map((r) => (
              <li key={r.productoId} className={`ss-repuesto${r.requiereConfirmacion && !r.confirmado ? " ss-repuesto--pendiente" : ""}`}>
                <div className="ss-repuesto__fila">
                  <div className="ss-repuesto__producto">
                    <input className="ss-input" value={r.descripcion || ""} maxLength={120}
                      aria-label={`Qué necesita el vehículo (${r.nombre})`}
                      onChange={(e) => actualizar(r.productoId, { descripcion: e.target.value })} />
                    <span className="ss-meta">
                      {r.nombre}{r.referencia ? ` · Ref. ${r.referencia}` : ""} · {r.tiendaNombre}
                      {r.disponibilidad === "ENCARGO" && ` · Encargo${r.diasEntrega ? ` (${r.diasEntrega} días)` : ""}`}
                    </span>
                  </div>
                  <input className="ss-input ss-input--cantidad" type="number" min={1} max={99}
                    aria-label={`Cantidad de ${r.nombre}`} value={r.cantidad}
                    onChange={(e) => actualizar(r.productoId, { cantidad: Math.max(1, Math.min(99, Number(e.target.value) || 1)) })} />
                  <span className="ss-repuesto__precio">{formatPrecio((r.precio || 0) * (r.cantidad || 0))}</span>
                  <button type="button" className="ss-btn-icono" onClick={() => quitar(r.productoId)}
                    aria-label={`Quitar ${r.nombre}`}><Icon name="close" size={16} /></button>
                </div>
                {r.requiereConfirmacion && (
                  <div className="ss-confirmar">
                    <span className={`ss-estado ss-estado--${ESTADO_OFERTA.POR_CONFIRMAR.tono}`}>
                      <span className="ss-estado__punto" />{ESTADO_OFERTA.POR_CONFIRMAR.label}
                    </span>
                    <span className="ss-meta">Este producto no tiene datos de compatibilidad. Verifica que le sirve al vehículo.</span>
                    <label className="ss-confirmar__check">
                      <input type="checkbox" checked={r.confirmado}
                        onChange={(e) => actualizar(r.productoId, { confirmado: e.target.checked })} />
                      {TEXTO_CONFIRMACION}
                    </label>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {error && <p className="ss-alerta" role="alert">{error}</p>}

      <div className="ss-acciones">
        <button type="submit" className="sv-btn" disabled={guardando}>
          {guardando ? "Guardando..." : previo ? "Guardar diagnóstico y repuestos" : "Registrar diagnóstico"}
        </button>
        {onCancelar && <button type="button" className="sv-btn-outline" onClick={onCancelar} disabled={guardando}>Cancelar</button>}
      </div>
    </form>

    {/* Fuera del <form>: la ventana tiene su propio formulario de búsqueda. */}
    <BuscadorRepuestos
      abierto={buscadorAbierto}
      token={token}
      solicitud={solicitud}
      elegidos={elegidos}
      onElegir={elegir}
      onCerrar={() => setBuscadorAbierto(false)}
    />
    </>
  );
}
