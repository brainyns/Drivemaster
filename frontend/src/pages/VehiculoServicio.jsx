import { useState, useEffect } from "react";
import { getToken } from "../services/authService";
import { obtenerVehiculo, repuestosCompatibles, historialVehiculo } from "../services/vehiculoService";
import { talleresActivos } from "../services/tallerService";
import { cotizarOrden, pagarOrden, cancelarOrden } from "../services/ordenServicioService";
import "../css/servicios.css";

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

export default function VehiculoServicio({ vehiculoId, onVolver }) {
  const token = getToken();

  const [vehiculo, setVehiculo] = useState(null);
  const [repuestos, setRepuestos] = useState([]);
  const [historial, setHistorial] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  const [productoSeleccionado, setProductoSeleccionado] = useState(null);
  const [ciudad, setCiudad] = useState("Bogotá D.C.");
  const [talleres, setTalleres] = useState([]);
  const [buscandoTalleres, setBuscandoTalleres] = useState(false);

const [cotizacion, setCotizacion] = useState(null);
const [procesando, setProcesando] = useState(false);
const [metodoPago, setMetodoPago] = useState("EFECTIVO");
const [referenciaPago, setReferenciaPago] = useState("");

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

  const buscarTalleres = async (ciudadBuscar) => {
    setBuscandoTalleres(true);
    try {
      setTalleres(await talleresActivos(token, { ciudad: ciudadBuscar }));
    } catch (e) {
      setError(e.message);
    } finally {
      setBuscandoTalleres(false);
    }
  };

  const handleComprarInstalar = (producto) => {
    setProductoSeleccionado(producto);
    setCotizacion(null);
    setError("");
    buscarTalleres(ciudad);
  };

  const handleCotizar = async (tallerId) => {
    setProcesando(true);
    setError("");
    try {
      const orden = await cotizarOrden(token, {
        vehiculoId, productoId: productoSeleccionado.productoId, tallerId,
      });
      setCotizacion(orden);
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const handlePagar = async () => {
    setProcesando(true);
    setError("");
    try {
      const actualizada = await pagarOrden(
        token,
        cotizacion.id,
        metodoPago,
        referenciaPago.trim() || null,
      );
      setCotizacion(actualizada);
      setReferenciaPago("");
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const handleCancelar = async () => {
    setProcesando(true);
    try {
      await cancelarOrden(token, cotizacion.id);
      setCotizacion(null);
      setProductoSeleccionado(null);
      cargarTodo();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  if (cargando) return <div className="sv-root"><p className="sv-empty">Cargando...</p></div>;

  return (
    <div className="sv-root">
      <div className="sv-header-top">
        <div>
          <h1 className="sv-title">{vehiculo ? `${vehiculo.marca} ${vehiculo.modelo} ${vehiculo.anio}` : "Vehículo"}</h1>
          <p className="sv-subtitle">Placa {vehiculo?.placa} · Repuestos compatibles e instalación</p>
        </div>
        {onVolver && <button className="sv-volver" onClick={onVolver}>← Mis vehículos</button>}
      </div>

      {error && <p style={{ color: "var(--error)" }}>{error}</p>}

      {/* ── Repuestos compatibles ─────────────────────────── */}
      {!productoSeleccionado && (
        <>
          <h2 className="sv-section-title">Repuestos compatibles</h2>
          {repuestos.length === 0 ? (
            <p className="sv-empty">No encontramos repuestos compatibles con stock disponible para este vehículo.</p>
          ) : (
            <div className="sv-grid">
              {repuestos.map(r => (
                <div className="sv-card" key={r.productoId}>
                  <h3>{r.nombre}</h3>
                  <span className="sv-card-meta">{r.marca} · {r.categoria}</span>
                  <span className="sv-card-meta">Stock: {r.stockActual}</span>
                  <strong>{formatPrecio(r.precioVenta)}</strong>
                  <button className="sv-btn" onClick={() => handleComprarInstalar(r)}>
                    Comprar + instalar
                  </button>
                  <span className="sv-card-meta">¿Solo comprar el repuesto? Búscalo en el catálogo.</span>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {/* ── Selección de taller ──────────────────────────── */}
      {productoSeleccionado && !cotizacion && (
        <>
          <h2 className="sv-section-title">Elige un taller para instalar «{productoSeleccionado.nombre}»</h2>
          <div className="sv-form" style={{ gridTemplateColumns: "2fr 1fr", alignItems: "end" }}>
            <div className="sv-field">
              <label>Ciudad</label>
              <input value={ciudad} onChange={e => setCiudad(e.target.value)} />
            </div>
            <button className="sv-btn-outline" onClick={() => buscarTalleres(ciudad)} disabled={buscandoTalleres}>
              {buscandoTalleres ? "Buscando..." : "Buscar talleres"}
            </button>
          </div>

          {talleres.length === 0 ? (
            <p className="sv-empty">No hay talleres activos en esa ciudad.</p>
          ) : (
            <div className="sv-list">
              {talleres
                .slice()
                .sort((a, b) => (a.distanciaKm ?? 999) - (b.distanciaKm ?? 999))
                .map(t => (
                  <div className="sv-row" key={t.id}>
                    <div>
                      <h3 style={{ margin: 0 }}>{t.nombre}</h3>
                      <span className="sv-card-meta">
                        {t.distanciaKm != null ? `${t.distanciaKm} km · ` : ""}⭐ {t.rating ?? "—"} · Mano de obra {formatPrecio(t.precioManoObra)}
                      </span>
                      <div className="sv-card-meta">{t.direccion}</div>
                    </div>
                    <button className="sv-btn" onClick={() => handleCotizar(t.id)} disabled={procesando}>
                      Cotizar con este taller
                    </button>
                  </div>
                ))}
            </div>
          )}

          <button className="sv-btn-outline" style={{ marginTop: "var(--sp-md)" }}
            onClick={() => { setProductoSeleccionado(null); setTalleres([]); }}>
            ← Elegir otro repuesto
          </button>
        </>
      )}

      {/* ── Cotización / pago ────────────────────────────── */}
      {cotizacion && (
        <div className="sv-quote-box">
          <h2 style={{ marginTop: 0 }}>Cotización — {cotizacion.estado}</h2>
          <div className="sv-quote-line"><span>Repuesto ({cotizacion.productoNombre})</span><span>{formatPrecio(cotizacion.precioRepuesto)}</span></div>
          <div className="sv-quote-line"><span>Mano de obra ({cotizacion.tallerNombre})</span><span>{formatPrecio(cotizacion.precioManoObra)}</span></div>
          <div className="sv-quote-line sv-quote-total"><span>Total</span><span>{formatPrecio(cotizacion.total)}</span></div>

          {cotizacion.estado === "COTIZADA" && (
            <>
              <div className="sv-pay-methods">
                {["EFECTIVO", "TRANSFERENCIA"].map((m) => (
                  <label key={m} className="sv-pay-method">
                    <input
                      type="radio"
                      name="metodoPago"
                      value={m}
                      checked={metodoPago === m}
                      onChange={(e) => setMetodoPago(e.target.value)}
                    />
                    {m === "EFECTIVO" ? "Efectivo" : "Transferencia"}
                  </label>
                ))}
              </div>

              {metodoPago === "TRANSFERENCIA" && (
                <input
                  className="sv-pay-ref"
                  type="text"
                  placeholder="Numero de comprobante de la transferencia"
                  value={referenciaPago}
                  onChange={(e) => setReferenciaPago(e.target.value)}
                  maxLength={40}
                />
              )}

              <div style={{ display: "flex", gap: "var(--sp-md)", marginTop: "var(--sp-md)" }}>
                <button className="sv-btn" onClick={handlePagar} disabled={procesando}>
                  {procesando ? "Procesando..." : `Pagar ${formatPrecio(cotizacion.total)}`}
                </button>
                <button className="sv-btn-outline" onClick={handleCancelar} disabled={procesando}>
                  Cancelar
                </button>
              </div>
            </>
          )}

          {cotizacion.estado === "PAGADA" && (
            <p style={{ marginTop: "var(--sp-md)" }}>
              ¡Pago confirmado! Te enviamos la factura por correo. El taller <strong>{cotizacion.tallerNombre}</strong> verá esta orden en su panel
              y comenzará el servicio. Podrás ver el resultado en el historial de tu vehículo.
            </p>
          )}

          {cotizacion.estado !== "PAGADA" && cotizacion.estado === "COTIZADA" ? null : (
            <button className="sv-btn-outline" style={{ marginTop: "var(--sp-md)" }}
              onClick={() => { setCotizacion(null); setProductoSeleccionado(null); cargarTodo(); }}>
              Volver a repuestos compatibles
            </button>
          )}
        </div>
      )}

      {/* ── Historial ────────────────────────────────────── */}
      {!productoSeleccionado && (
        <>
          <h2 className="sv-section-title">Historial de servicios</h2>
          {historial.length === 0 ? (
            <p className="sv-empty">Este vehículo aún no tiene servicios finalizados.</p>
          ) : (
            <div className="sv-list">
              {historial.map(h => (
                <div className="sv-row" key={h.id}>
                  <div>
                    <h3 style={{ margin: 0 }}>{h.productoNombre}</h3>
                    <span className="sv-card-meta">
                      Taller {h.tallerNombre} · {h.fechaFinalizacion ? new Date(h.fechaFinalizacion).toLocaleDateString("es-CO") : ""}
                    </span>
                    {h.observaciones && <div className="sv-card-meta">"{h.observaciones}"</div>}
                  </div>
                  <strong>{formatPrecio(h.total)}</strong>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}