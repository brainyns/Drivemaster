import { useEffect, useState } from "react";
import { listarMovimientos, registrarMovimiento } from "../services/movimientoService";
import { listarProductos } from "../services/productoService";
import "../css/productos.css";
import "../css/venta.css";
import "../css/movimientos.css";

function Movimientos() {
  const [movimientos, setMovimientos] = useState([]);
  const [productos, setProductos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Filtros
  const [filtros, setFiltros] = useState({ productoId: "", tipo: "", fechaInicio: "", fechaFin: "" });

  // Modal ajuste manual
  const [mostrarModal, setMostrarModal] = useState(false);
  const [ajuste, setAjuste] = useState({ productoId: "", tipo: "AJUSTE", cantidad: 1, motivo: "" });
  const [loadingAjuste, setLoadingAjuste] = useState(false);

  useEffect(() => {
    listarProductos().then(setProductos).catch(() => {});
    cargar();
  }, []);

  const cargar = async (f = filtros) => {
    setLoading(true);
    try {
      const data = await listarMovimientos(f);
      setMovimientos(data);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  };

  const handleFiltroChange = e => {
    const nuevo = { ...filtros, [e.target.name]: e.target.value };
    setFiltros(nuevo);
  };

  const aplicarFiltros = e => { e.preventDefault(); cargar(filtros); };
  const limpiarFiltros = () => {
    const vacio = { productoId: "", tipo: "", fechaInicio: "", fechaFin: "" };
    setFiltros(vacio);
    cargar(vacio);
  };

  const handleAjuste = async () => {
    setLoadingAjuste(true);
    try {
      await registrarMovimiento(ajuste);
      setMostrarModal(false);
      setAjuste({ productoId: "", tipo: "AJUSTE", cantidad: 1, motivo: "" });
      cargar(filtros);
    } catch (err) { alert(err.message); }
    finally { setLoadingAjuste(false); }
  };

  const getNombreProducto = id => productos.find(p => p.id === id)?.nombre || id;

  const getTipoBadge = tipo => {
    if (tipo === "ENTRADA") return <span className="mov-badge mov-badge--entrada">↑ ENTRADA</span>;
    if (tipo === "SALIDA")  return <span className="mov-badge mov-badge--salida">↓ SALIDA</span>;
    return <span className="mov-badge mov-badge--ajuste">⚙ AJUSTE</span>;
  };

  // Stats
  const totalEntradas = movimientos.filter(m => m.tipo === "ENTRADA").reduce((a, m) => a + m.cantidad, 0);
  const totalSalidas  = movimientos.filter(m => m.tipo === "SALIDA").reduce((a, m) => a + m.cantidad, 0);
  const totalAjustes  = movimientos.filter(m => m.tipo === "AJUSTE").length;

  return (
    <div className="productos-container">

      {/* Header */}
      <div className="productos-header">
        <h1>📊 Movimientos de Inventario</h1>
        <button onClick={() => setMostrarModal(true)} className="btn-nuevo">⚙ Ajuste Manual</button>
      </div>

      {/* Stats */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon">📋</div>
          <div>
            <p className="stat-label">Total Movimientos</p>
            <p className="stat-value">{movimientos.length}</p>
          </div>
        </div>
        <div className="stat-card stat-card--success">
          <div className="stat-icon">📥</div>
          <div>
            <p className="stat-label">Unidades Entrada</p>
            <p className="stat-value">{totalEntradas}</p>
          </div>
        </div>
        <div className="stat-card stat-card--error">
          <div className="stat-icon">📤</div>
          <div>
            <p className="stat-label">Unidades Salida</p>
            <p className="stat-value">{totalSalidas}</p>
          </div>
        </div>
        <div className="stat-card stat-card--warning">
          <div className="stat-icon">⚙️</div>
          <div>
            <p className="stat-label">Ajustes</p>
            <p className="stat-value">{totalAjustes}</p>
          </div>
        </div>
      </div>

      {/* Filtros */}
      <form onSubmit={aplicarFiltros} className="filtros-panel">
        <div className="filtros-grid">
          <div className="form-group">
            <label>Producto</label>
            <select name="productoId" value={filtros.productoId} onChange={handleFiltroChange} className="select-input">
              <option value="">Todos los productos</option>
              {productos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label>Tipo</label>
            <select name="tipo" value={filtros.tipo} onChange={handleFiltroChange} className="select-input">
              <option value="">Todos</option>
              <option value="ENTRADA">Entrada</option>
              <option value="SALIDA">Salida</option>
              <option value="AJUSTE">Ajuste</option>
            </select>
          </div>
          <div className="form-group">
            <label>Fecha Inicio</label>
            <input type="date" name="fechaInicio" value={filtros.fechaInicio} onChange={handleFiltroChange} />
          </div>
          <div className="form-group">
            <label>Fecha Fin</label>
            <input type="date" name="fechaFin" value={filtros.fechaFin} onChange={handleFiltroChange} />
          </div>
        </div>
        <div className="filtros-actions">
          <button type="button" onClick={limpiarFiltros} className="btn-cancelar">Limpiar</button>
          <button type="submit" className="btn-guardar">🔍 Filtrar</button>
        </div>
      </form>

      {/* Tabla */}
      {loading ? <p className="estado">Cargando movimientos...</p> :
       error   ? <p className="estado error">{error}</p> :
       movimientos.length === 0 ? <p className="estado">No hay movimientos.</p> : (
        <table className="productos-tabla">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Producto</th>
              <th>Tipo</th>
              <th>Cantidad</th>
              <th>Stock Anterior</th>
              <th>Stock Nuevo</th>
              <th>Motivo</th>
              <th>Referencia</th>
            </tr>
          </thead>
          <tbody>
            {movimientos.map(m => (
              <tr key={m.id}>
                <td className="text-muted">{new Date(m.fecha).toLocaleString()}</td>
                <td><strong>{getNombreProducto(m.productoId)}</strong></td>
                <td>{getTipoBadge(m.tipo)}</td>
                <td>
                  <span className={`stock-num stock-num--${m.tipo === "ENTRADA" ? "normal" : m.tipo === "SALIDA" ? "bajo" : "medio"}`}>
                    {m.tipo === "SALIDA" ? "-" : "+"}{m.cantidad}
                  </span>
                </td>
                <td className="text-muted">{m.stockAnterior}</td>
                <td><strong>{m.stockNuevo}</strong></td>
                <td className="text-muted">{m.motivo}</td>
                <td className="text-muted"><span className="codigo-badge">{m.referencia || "-"}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* Modal ajuste manual */}
      {mostrarModal && (
        <div className="modal-overlay">
          <div className="modal-box">
            <h2>⚙ Ajuste Manual de Stock</h2>
            <p style={{ color: "var(--text-muted)", fontSize: "0.85rem" }}>
              Registra una entrada, salida o ajuste manual al inventario.
            </p>

            <div className="form-group">
              <label>Producto</label>
              <select value={ajuste.productoId} onChange={e => setAjuste({ ...ajuste, productoId: e.target.value })} className="select-input" required>
                <option value="">Seleccionar producto...</option>
                {productos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            </div>

            <div className="form-group">
              <label>Tipo de Movimiento</label>
              <select value={ajuste.tipo} onChange={e => setAjuste({ ...ajuste, tipo: e.target.value })} className="select-input">
                <option value="ENTRADA">📥 Entrada</option>
                <option value="SALIDA">📤 Salida</option>
                <option value="AJUSTE">⚙ Ajuste</option>
              </select>
            </div>

            <div className="form-group">
              <label>Cantidad</label>
              <input type="number" min="1" value={ajuste.cantidad}
                onChange={e => setAjuste({ ...ajuste, cantidad: Number(e.target.value) })} />
            </div>

            <div className="form-group">
              <label>Motivo</label>
              <input value={ajuste.motivo} placeholder="Ej: Corrección de inventario"
                onChange={e => setAjuste({ ...ajuste, motivo: e.target.value })} />
            </div>

            <div className="modal-actions">
              <button onClick={() => setMostrarModal(false)} className="btn-cancelar">Cancelar</button>
              <button onClick={handleAjuste} className="btn-guardar" disabled={loadingAjuste || !ajuste.productoId || !ajuste.motivo}>
                {loadingAjuste ? "Guardando..." : "Registrar"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default Movimientos;