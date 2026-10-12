import { useState } from "react";
import { previsualizarCotizacion, emitirCotizacion } from "../../services/cotizacionService";
import CotizacionDetalle from "./CotizacionDetalle";
import AlertaPrediccionCotizacion from "./AlertaPrediccionCotizacion";
import { formatPrecio } from "./estadosServicio";
import "../../css/cotizacion.css";

const lineaVacia = () => ({ descripcion: "", modalidad: "ITEM", valor: "", horas: "" });

/**
 * El taller arma la cotización: los repuestos salen de la selección ya guardada
 * (con el precio actual de la tienda) y aquí agrega la mano de obra. Primero se
 * previsualizan los totales calculados por el backend y luego se emite.
 */
export default function CotizacionForm({ token, solicitud, tarifaHora, onEmitida, onCancelar }) {
  const [lineas, setLineas] = useState([lineaVacia()]);
  const [tiempo, setTiempo] = useState("");
  const [observaciones, setObservaciones] = useState("");
  const [vista, setVista] = useState(null);
  const [error, setError] = useState(null);
  const [procesando, setProcesando] = useState(false);

  const cambiar = (n, campo, valor) => {
    setVista(null);
    setLineas((prev) => prev.map((l, i) => (i === n ? { ...l, [campo]: valor } : l)));
  };

  const datos = () => ({
    manoObra: lineas
      .filter((l) => l.descripcion.trim())
      .map((l) => ({
        descripcion: l.descripcion.trim(),
        modalidad: l.modalidad,
        valor: l.modalidad === "ITEM" ? Number(l.valor) : null,
        horas: l.modalidad === "HORAS" ? Number(l.horas) : null,
      })),
    tiempoEstimadoHoras: tiempo ? Number(tiempo) : null,
    observaciones: observaciones.trim() || null,
  });

  const ejecutar = async (accion) => {
    setProcesando(true);
    setError(null);
    try {
      await accion();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const previsualizar = (e) => {
    e.preventDefault();
    ejecutar(async () => setVista(await previsualizarCotizacion(token, solicitud.id, datos())));
  };

  const emitir = () => ejecutar(async () => {
    const cotizacion = await emitirCotizacion(token, solicitud.id, datos());
    onEmitida(cotizacion);
  });

  return (
    <section className="ss-bloque ss-bloque--destacado">
      <h3 className="ss-bloque__titulo">Cotizar</h3>
      <p className="ss-meta">
        {solicitud.repuestos?.length > 0
          ? `Incluye ${solicitud.repuestos.length} repuesto(s) elegidos, al precio actual de cada tienda.`
          : "Sin repuestos: la cotización es solo mano de obra."}
        {" "}El IVA y el descuento del diagnóstico los calcula DriveMaster.
      </p>
      {error && <p className="ss-alerta" role="alert">{error}</p>}

      <form className="ct-form" onSubmit={previsualizar}>
        <h4 className="ct-detalle__grupo">Mano de obra</h4>
        {lineas.map((l, n) => (
          <div key={n} className="ct-linea">
            <input className="ss-input ct-linea__desc" placeholder="Trabajo (ej. Cambio de pastillas)" maxLength={200}
              aria-label="Descripción del trabajo" value={l.descripcion}
              onChange={(e) => cambiar(n, "descripcion", e.target.value)} />
            <select className="ss-input ct-linea__modalidad" aria-label="Modalidad" value={l.modalidad}
              onChange={(e) => cambiar(n, "modalidad", e.target.value)}>
              <option value="ITEM">Valor fijo</option>
              <option value="HORAS">Por horas</option>
            </select>
            {l.modalidad === "ITEM" ? (
              <input className="ss-input ct-linea__num" type="number" min="1" step="1" placeholder="Valor sin IVA"
                aria-label="Valor sin IVA" value={l.valor} onChange={(e) => cambiar(n, "valor", e.target.value)} />
            ) : (
              <input className="ss-input ct-linea__num" type="number" min="0.25" step="0.25" placeholder="Horas"
                aria-label="Horas" value={l.horas} onChange={(e) => cambiar(n, "horas", e.target.value)} />
            )}
            {lineas.length > 1 && (
              <button type="button" className="sv-btn-outline ct-linea__quitar" aria-label="Quitar línea"
                onClick={() => { setVista(null); setLineas((p) => p.filter((_, i) => i !== n)); }}>×</button>
            )}
          </div>
        ))}
        <p className="ss-ayuda">
          {tarifaHora ? `Por horas usa tu tarifa: ${formatPrecio(tarifaHora)} / hora.` : "Para cotizar por horas configura tu tarifa por hora en “Mi taller”."}
        </p>
        <button type="button" className="sv-btn-outline ct-form__agregar" disabled={lineas.length >= 20}
          onClick={() => setLineas((p) => [...p, lineaVacia()])}>+ Agregar trabajo</button>

        <div className="sv-form">
          <div className="sv-field">
            <label htmlFor="ct-tiempo">Tiempo estimado (horas, opcional)</label>
            <input id="ct-tiempo" type="number" min="1" max="2000" value={tiempo}
              onChange={(e) => { setVista(null); setTiempo(e.target.value); }} />
          </div>
          <div className="sv-field sv-form-full">
            <label htmlFor="ct-obs">Observaciones para el cliente (opcional)</label>
            <textarea id="ct-obs" className="ss-textarea" rows={2} maxLength={1000} value={observaciones}
              onChange={(e) => { setVista(null); setObservaciones(e.target.value); }} />
          </div>
        </div>

        <div className="ss-acciones">
          <button type="submit" className="sv-btn-outline" disabled={procesando}>
            {procesando && !vista ? "Calculando..." : "Calcular totales"}
          </button>
          {onCancelar && <button type="button" className="sv-btn-outline" onClick={onCancelar} disabled={procesando}>Cancelar</button>}
        </div>
      </form>

      {vista && (
        <div className="ct-previa">
          {vista.prediccionAprobacion && (
            <AlertaPrediccionCotizacion prediccion={vista.prediccionAprobacion} />
          )}
          <CotizacionDetalle cotizacion={vista} mostrarComision sinEstado />
          <div className="ss-acciones">
            <button className="sv-btn" onClick={emitir} disabled={procesando}>
              {procesando ? "Enviando..." : "Emitir y enviar al cliente"}
            </button>
          </div>
          <p className="ss-ayuda">El cliente recibe un correo para aprobar, pedir cambios o rechazar.</p>
        </div>
      )}
    </section>
  );
}
