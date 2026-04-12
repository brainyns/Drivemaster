import { useEffect, useState } from "react";
import { listarMovimientos, registrarMovimiento } from "../services/movimientoService";
import { listarProductos } from "../services/productoService";
import "../css/movimientos.css";

function Movimientos() {
  const [movimientos, setMovimientos] = useState([]);
  const [productos, setProductos]     = useState([]);
  const [loading, setLoading]         = useState(true);
  const [pagina, setPagina]           = useState(1);
  const POR_PAGINA = 10;

  const [filtros, setFiltros] = useState({
    busqueda: "", tipo: "", fechaInicio: "", fechaFin: ""
  });

  const [mostrarModal, setMostrarModal] = useState(false);
  const [ajuste, setAjuste] = useState({
    productoId: "", tipo: "AJUSTE", cantidad: "", motivo: ""
  });
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
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  const aplicarFiltros = e => {
    e.preventDefault();
    setPagina(1);
    cargar(filtros);
  };

  const getNombre = id => {
    const p = productos.find(p => p.id === id);
    return p ? p.nombre : id;
  };

  const getCodigo = id => {
    const p = productos.find(p => p.id === id);
    return p ? p.codigo : "—";
  };

  const getInicial = id => {
    const nombre = getNombre(id);
    return nombre?.[0]?.toUpperCase() || "?";
  };

  const formatFecha = (fecha) => {
    if (!fecha) return "—";
    const d = new Date(fecha);
    return {
      fecha: d.toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }),
      hora: d.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" })
    };
  };

  const totalEntradas = movimientos.filter(m => m.tipo === "ENTRADA").reduce((a, m) => a + m.cantidad, 0);
  const totalSalidas  = movimientos.filter(m => m.tipo === "SALIDA").reduce((a, m) => a + m.cantidad, 0);
  const totalAjustes  = movimientos.filter(m => m.tipo === "AJUSTE").length;

  const filtradosBusqueda = movimientos.filter(m => {
    const nombre = getNombre(m.productoId).toLowerCase();
    const codigo = getCodigo(m.productoId).toLowerCase();
    const q = filtros.busqueda.toLowerCase();
    return nombre.includes(q) || codigo.includes(q);
  });

  const totalPags = Math.ceil(filtradosBusqueda.length / POR_PAGINA);
  const paginados = filtradosBusqueda.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA);

  const handleAjuste = async () => {
    if (!ajuste.productoId || !ajuste.motivo || !ajuste.cantidad) return;
    setLoadingAjuste(true);
    try {
      await registrarMovimiento({ ...ajuste, cantidad: Number(ajuste.cantidad) });
      setMostrarModal(false);
      setAjuste({ productoId: "", tipo: "AJUSTE", cantidad: "", motivo: "" });
      cargar();
    } catch(e) { alert(e.message); }
    finally { setLoadingAjuste(false); }
  };

  const COLORES_AVATAR = ["#343d96","#5e1b7a","#ff5c34","#1a6b3c","#7c3aed","#0369a1"];
  const getColor = id => COLORES_AVATAR[(id?.charCodeAt(0) || 0) % COLORES_AVATAR.length];

  return (
    <div className="mv-page">

      {/* Topbar */}
      <header className="mv-topbar">
        <div className="mv-topbar-left">
          <span className="mv-topbar-brand">Movimiento de Inventario</span>
          <span className="mv-topbar-div">|</span>
          <span className="mv-topbar-sub">DriveMaster Terminal</span>
        </div>
        <div className="mv-topbar-right">
          <button className="mv-icon-btn">🔔</button>
          <button className="mv-icon-btn">⚙</button>
          <div className="mv-user-pill">
            <div className="mv-user-avatar">A</div>
            <div>
              <p className="mv-user-name">Admin</p>
              <p className="mv-user-role">Administrador</p>
            </div>
          </div>
        </div>
      </header>

      <div className="mv-content">

        {/* Stats */}
        <div className="mv-stats">
          <div className="mv-stat">
            <div className="mv-stat-top">
              <div className="mv-stat-icon mv-stat-icon--blue">≡</div>
              <span className="mv-stat-pct mv-stat-pct--pos">+12%</span>
            </div>
            <p className="mv-stat-label">Total Movimientos</p>
            <p className="mv-stat-val">{movimientos.length.toLocaleString()}</p>
          </div>
          <div className="mv-stat">
            <div className="mv-stat-top">
              <div className="mv-stat-icon mv-stat-icon--green">↓</div>
              <span className="mv-stat-pct mv-stat-pct--pos">+8.4%</span>
            </div>
            <p className="mv-stat-label">Unidades Entrada</p>
            <p className="mv-stat-val">{totalEntradas.toLocaleString()}</p>
          </div>
          <div className="mv-stat">
            <div className="mv-stat-top">
              <div className="mv-stat-icon mv-stat-icon--red">↑</div>
              <span className="mv-stat-pct mv-stat-pct--neg">-3.1%</span>
            </div>
            <p className="mv-stat-label">Unidades Salida</p>
            <p className="mv-stat-val">{totalSalidas.toLocaleString()}</p>
          </div>
          <div className="mv-stat">
            <div className="mv-stat-top">
              <div className="mv-stat-icon mv-stat-icon--orange">⚙</div>
              <span className="mv-stat-pct mv-stat-pct--adj">Ajustado</span>
            </div>
            <p className="mv-stat-label">Ajustes Realizados</p>
            <p className="mv-stat-val">{totalAjustes}</p>
          </div>
        </div>

        {/* Filtros */}
        <form onSubmit={aplicarFiltros} className="mv-filters">
          <div className="mv-filter-group">
            <label>Buscar Producto</label>
            <div className="mv-search-wrap">
              <span className="mv-search-icon">🔍</span>
              <input
                className="mv-search"
                placeholder="SKU, Nombre o Categoría..."
                value={filtros.busqueda}
                onChange={e => setFiltros({ ...filtros, busqueda: e.target.value })}
              />
            </div>
          </div>
          <div className="mv-filter-group">
            <label>Tipo de Mov.</label>
            <select
              className="mv-select"
              value={filtros.tipo}
              onChange={e => setFiltros({ ...filtros, tipo: e.target.value })}
            >
              <option value="">Todos</option>
              <option value="ENTRADA">Entrada</option>
              <option value="SALIDA">Salida</option>
              <option value="AJUSTE">Ajuste</option>
            </select>
          </div>
          <div className="mv-filter-group">
            <label>Desde</label>
            <input
              type="date" className="mv-date"
              value={filtros.fechaInicio}
              onChange={e => setFiltros({ ...filtros, fechaInicio: e.target.value })}
            />
          </div>
          <div className="mv-filter-group">
            <label>Hasta</label>
            <input
              type="date" className="mv-date"
              value={filtros.fechaFin}
              onChange={e => setFiltros({ ...filtros, fechaFin: e.target.value })}
            />
          </div>
          <div className="mv-filter-group mv-filter-group--btn">
            <label>&nbsp;</label>
            <button type="submit" className="mv-btn-filter">Filtrar</button>
          </div>
          <div className="mv-filter-group mv-filter-group--btn">
            <label>&nbsp;</label>
            <button type="button" className="mv-btn-ajuste" onClick={() => setMostrarModal(true)}>
              + Ajuste Manual
            </button>
          </div>
        </form>

        {/* Tabla */}
        <div className="mv-table-card">
          <table className="mv-table">
            <thead>
              <tr>
                <th>Fecha / Hora</th>
                <th>Producto</th>
                <th>Tipo</th>
                <th>Cant.</th>
                <th>Stock Ant.</th>
                <th>Stock Nuevo</th>
                <th>Motivo</th>
                <th>Ref.</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan="8" className="mv-empty">Cargando movimientos...</td></tr>
              ) : paginados.length === 0 ? (
                <tr><td colSpan="8" className="mv-empty">No hay movimientos registrados</td></tr>
              ) : paginados.map(m => {
                const { fecha, hora } = formatFecha(m.fecha);
                const esEntrada = m.tipo === "ENTRADA";
                const esSalida  = m.tipo === "SALIDA";
                return (
                  <tr key={m.id} className="mv-row">
                    <td>
                      <p className="mv-fecha">{fecha}</p>
                      <p className="mv-hora">{hora}</p>
                    </td>
                    <td>
                      <div className="mv-product-cell">
                        <div className="mv-product-avatar" style={{ background: getColor(m.productoId) }}>
                          {getInicial(m.productoId)}
                        </div>
                        <div>
                          <p className="mv-product-name">{getNombre(m.productoId)}</p>
                          <p className="mv-product-sku">SKU: {getCodigo(m.productoId)}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      {m.tipo === "ENTRADA" && <span className="mv-badge mv-badge--entrada">↓ ENTRADA</span>}
                      {m.tipo === "SALIDA"  && <span className="mv-badge mv-badge--salida">↑ SALIDA</span>}
                      {m.tipo === "AJUSTE"  && <span className="mv-badge mv-badge--ajuste">⚙ AJUSTE</span>}
                    </td>
                    <td>
                      <span className={`mv-cantidad ${esEntrada ? "mv-cantidad--pos" : esSalida ? "mv-cantidad--neg" : "mv-cantidad--adj"}`}>
                        {esEntrada ? "+" : esSalida ? "-" : ""}{m.cantidad}
                      </span>
                    </td>
                    <td className="mv-muted">{m.stockAnterior}</td>
                    <td><strong className="mv-stock-nuevo">{m.stockNuevo}</strong></td>
                    <td>
                      {m.motivo && <span className="mv-motivo">{m.motivo}</span>}
                    </td>
                    <td>
                      {m.referencia && (
                        <span className="mv-ref">#{m.referencia?.slice(-8)}</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          {/* Footer paginación */}
          <div className="mv-table-footer">
            <span className="mv-count">
              Mostrando <strong>{Math.min((pagina-1)*POR_PAGINA+1, filtradosBusqueda.length)}-{Math.min(pagina*POR_PAGINA, filtradosBusqueda.length)}</strong> de <strong>{filtradosBusqueda.length}</strong> movimientos
            </span>
            {totalPags > 1 && (
              <div className="mv-pag">
                <button className="mv-pag-arrow" onClick={() => setPagina(p => Math.max(1,p-1))} disabled={pagina===1}>‹</button>
                {Array.from({ length: Math.min(totalPags, 5) }, (_, i) => i + 1).map(n => (
                  <button
                    key={n}
                    className={`mv-pag-num ${pagina === n ? "activo" : ""}`}
                    onClick={() => setPagina(n)}
                  >{n}</button>
                ))}
                {totalPags > 5 && <span className="mv-muted">...</span>}
                {totalPags > 5 && (
                  <button className="mv-pag-num" onClick={() => setPagina(totalPags)}>{totalPags}</button>
                )}
                <button className="mv-pag-arrow" onClick={() => setPagina(p => Math.min(totalPags,p+1))} disabled={pagina===totalPags}>›</button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Modal ajuste manual */}
      {mostrarModal && (
        <div className="mv-modal-overlay">
          <div className="mv-modal">
            <h2>Ajuste Manual de Stock</h2>
            <p className="mv-modal-sub">Registra una corrección manual al inventario</p>

            <div className="mv-modal-field">
              <label>Producto</label>
              <select value={ajuste.productoId} onChange={e => setAjuste({ ...ajuste, productoId: e.target.value })}>
                <option value="">Seleccionar producto...</option>
                {productos.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            </div>
            <div className="mv-modal-field">
              <label>Tipo</label>
              <select value={ajuste.tipo} onChange={e => setAjuste({ ...ajuste, tipo: e.target.value })}>
                <option value="ENTRADA">Entrada</option>
                <option value="SALIDA">Salida</option>
                <option value="AJUSTE">Ajuste</option>
              </select>
            </div>
            <div className="mv-modal-field">
              <label>Cantidad</label>
              <input type="text" inputMode="numeric" placeholder="0"
                value={ajuste.cantidad} onChange={e => setAjuste({ ...ajuste, cantidad: e.target.value })} />
            </div>
            <div className="mv-modal-field">
              <label>Motivo</label>
              <input type="text" placeholder="Ej. Corrección de inventario"
                value={ajuste.motivo} onChange={e => setAjuste({ ...ajuste, motivo: e.target.value })} />
            </div>

            <div className="mv-modal-actions">
              <button onClick={() => setMostrarModal(false)} className="mv-modal-cancel">Cancelar</button>
              <button onClick={handleAjuste} className="mv-modal-save"
                disabled={loadingAjuste || !ajuste.productoId || !ajuste.motivo || !ajuste.cantidad}>
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