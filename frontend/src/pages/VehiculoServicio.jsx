import { useState, useEffect } from "react";
import { getToken } from "../services/authService";
import { obtenerVehiculo, repuestosCompatibles, historialVehiculo } from "../services/vehiculoService";
import "../css/servicios.css";
import Icon from "../components/public/Icon";
import { etiquetaCategoria } from "../utils/categorias";

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

/**
 * Ficha de servicio de un vehículo. El servicio empieza siempre por un
 * diagnóstico en el taller (el cliente ya no elige el repuesto antes de ir al
 * taller); los repuestos compatibles se muestran como referencia.
 */
export default function VehiculoServicio({ vehiculoId, onVolver, onSolicitarDiagnostico }) {
  const token = getToken();

  const [vehiculo, setVehiculo] = useState(null);
  const [repuestos, setRepuestos] = useState([]);
  const [historial, setHistorial] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  const cargarTodo = async () => {
    setCargando(true);
    setError("");
    try {
      const [v, r, h] = await Promise.all([
        obtenerVehiculo(token, vehiculoId),
        repuestosCompatibles(token, vehiculoId),
        historialVehiculo(token, vehiculoId),
      ]);
      setVehiculo(v);
      setRepuestos(r);
      setHistorial(h);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { cargarTodo(); }, [vehiculoId]);

  if (cargando) return <div className="sv-root"><p className="sv-empty">Cargando...</p></div>;

  return (
    <div className="sv-root">
      <div className="sv-header-top">
        <div>
          <h1 className="sv-title">{vehiculo ? `${vehiculo.marca} ${vehiculo.modelo} ${vehiculo.anio}` : "Vehículo"}</h1>
          <p className="sv-subtitle">Placa {vehiculo?.placa} · Diagnóstico, repuestos e historial</p>
        </div>
        {onVolver && <button className="sv-volver" onClick={onVolver}><Icon name="arrow" size={16} className="ui-volver__icono ui-icono-linea" />Mis vehículos</button>}
      </div>

      {error && <p style={{ color: "var(--error)" }}>{error}</p>}

      {/* ── Diagnóstico ──────────────────────────────────── */}
      <div className="sv-quote-box">
        <h2 style={{ marginTop: 0 }}>¿Algo no anda bien?</h2>
        <p className="sv-card-meta">
          Pide un diagnóstico: lo asignamos al taller más cercano, que revisa el vehículo, elige los
          repuestos con las tiendas y te envía una cotización antes de hacer cualquier trabajo.
        </p>
        <button className="sv-btn" style={{ marginTop: "var(--sp-md)" }}
          onClick={() => onSolicitarDiagnostico?.(vehiculoId)}>
          Solicitar diagnóstico para este vehículo
        </button>
      </div>

      {/* ── Repuestos compatibles (referencia) ───────────── */}
      <h2 className="sv-section-title">Repuestos compatibles</h2>
      {repuestos.length === 0 ? (
        <p className="sv-empty">No encontramos repuestos compatibles con stock disponible para este vehículo.</p>
      ) : (
        <div className="sv-grid">
          {repuestos.map(r => (
            <div className="sv-card" key={r.productoId}>
              <h3>{r.nombre}</h3>
              <span className="sv-card-meta">{r.marca} · {etiquetaCategoria(r.categoria)}</span>
              <span className="sv-card-meta">Stock: {r.stockActual}</span>
              {r.tiendaNombre && (
                <span className="sv-card-meta">Vende: {r.tiendaNombre}{r.tiendaCiudad ? ` · ${r.tiendaCiudad}` : ""}</span>
              )}
              <strong>{formatPrecio(r.precioVenta)}</strong>
              <span className="sv-card-meta">¿Solo comprar el repuesto? Búscalo en el catálogo.</span>
            </div>
          ))}
        </div>
      )}

      {/* ── Historial ────────────────────────────────────── */}
      <h2 className="sv-section-title">Historial de servicios</h2>
      {historial.length === 0 ? (
        <p className="sv-empty">Este vehículo aún no tiene servicios terminados. Aparecen aquí cuando el taller lo entrega.</p>
      ) : (
        <div className="sv-list">
          {historial.map(h => (
            <div className="sv-row" key={h.id}>
              <div>
                <h3 style={{ margin: 0 }}>{h.productoNombre}</h3>
                <span className="sv-card-meta">
                  Taller {h.tallerNombre}{h.fechaFinalizacion ? ` · ${new Date(h.fechaFinalizacion).toLocaleDateString("es-CO")}` : ""}
                </span>
                {/* Marketplace: los repuestos de la cotización; en el flujo anterior el repuesto es el título */}
                {h.repuestos?.length > 0 && (
                  <div className="sv-card-meta">Repuestos: {h.repuestos.join(", ")}</div>
                )}
                {h.flujo === "MARKETPLACE" && !h.repuestos?.length && <div className="sv-card-meta">Solo mano de obra</div>}
                {h.observaciones && <div className="sv-card-meta">"{h.observaciones}"</div>}
              </div>
              <strong>{formatPrecio(h.total)}</strong>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
