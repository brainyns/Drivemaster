import { useEffect, useState } from "react";
import { crearCompra } from "../services/compraService";
import { listarProveedores } from "../services/proveedorService";
import { listarProductos } from "../services/productoService";
import "../css/compras.css";

const IcTruck  = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><rect x="1" y="3" width="15" height="13"/><path d="M16 8h4l3 4v4h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>;
const IcBox    = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>;
const IcPlus   = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="14" height="14"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>;
const IcSearch = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>;
const IcX      = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="13" height="13"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>;
const IcCheck  = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" width="14" height="14"><polyline points="20 6 9 17 4 12"/></svg>;

function CompraForm({ onVolver, token }) {
  const [proveedores,  setProveedores]  = useState([]);
  const [productos,    setProductos]    = useState([]);
  const [loading,      setLoading]      = useState(false);
  const [error,        setError]        = useState(null);
  const [showConfirm,  setShowConfirm]  = useState(false);

  // Proveedor seleccionado
  const [proveedorId,  setProveedorId]  = useState("");
  const [provSearch,   setProvSearch]   = useState("");
  const [showProvDrop, setShowProvDrop] = useState(false);

  // Búsqueda de producto para agregar
  const [prodSearch,   setProdSearch]   = useState("");
  const [prodQty,      setProdQty]      = useState(1);
  const [prodCosto,    setProdCosto]    = useState(0);
  const [prodSel,      setProdSel]      = useState(null);
  const [showProdDrop, setShowProdDrop] = useState(false);

  // Carrito
  const [items, setItems] = useState([]);

  useEffect(() => {
    listarProveedores(token).then(setProveedores).catch(()=>{});
    listarProductos(token).then(setProductos).catch(()=>{});
  }, [token]);

  const proveedorActivo = proveedores.find(p => p.id === proveedorId);

  const provFiltrados = proveedores.filter(p =>
    p.nombre?.toLowerCase().includes(provSearch.toLowerCase()) ||
    p.contacto?.toLowerCase().includes(provSearch.toLowerCase())
  );

  const prodFiltrados = productos.filter(p =>
    p.nombre?.toLowerCase().includes(prodSearch.toLowerCase()) ||
    p.codigo?.toLowerCase().includes(prodSearch.toLowerCase())
  ).slice(0, 6);

  const seleccionarProd = (p) => {
    setProdSel(p);
    setProdCosto(p.precioCompra || 0);
    setProdSearch(p.nombre);
    setShowProdDrop(false);
  };

  const agregarItem = () => {
    if (!prodSel) return;
    setItems(prev => {
      const exist = prev.find(i => i.productoId === prodSel.id);
      if (exist) return prev.map(i => i.productoId === prodSel.id
        ? { ...i, cantidad: i.cantidad + Number(prodQty), subtotal: (i.cantidad + Number(prodQty)) * i.costo }
        : i);
      return [...prev, {
        productoId: prodSel.id,
        nombre: prodSel.nombre,
        sku: prodSel.codigo || prodSel.id.slice(-6).toUpperCase(),
        costo: Number(prodCosto),
        cantidad: Number(prodQty),
        subtotal: Number(prodCosto) * Number(prodQty),
      }];
    });
    setProdSel(null); setProdSearch(""); setProdQty(1); setProdCosto(0);
  };

  const quitarItem = id => setItems(prev => prev.filter(i => i.productoId !== id));

  const subtotal = items.reduce((s,i) => s + i.subtotal, 0);
  const total    = subtotal;

  const confirmar = async () => {
    if (!proveedorId || items.length === 0) return;
    setLoading(true); setError(null);
    try {
      await crearCompra({
        proveedorId,
        productos: items.map(i => ({ productoId: i.productoId, cantidad: i.cantidad, costo: i.costo })),
      }, token);
      onVolver();
    } catch(e) { setError(e.message); setShowConfirm(false); }
    finally { setLoading(false); }
  };

  return (
    <div className="cp-root">
      {/* Header */}
      <div className="cp-header">
        <div>
          <p className="cp-breadcrumb">
            <button className="cp-back-link" onClick={onVolver}>← Compras</button>
            {" / "}Nueva Compra
          </p>
          <h1 className="cp-h1">Nueva Orden de Compra</h1>
        </div>
      </div>

      {error && <p className="cp-error">{error}</p>}

      <div className="cp-form-layout">

        {/* ── COLUMNA IZQUIERDA ── */}
        <div className="cp-form-left">

          {/* 1. Proveedor */}
          <div className="cp-panel">
            <div className="cp-panel-head">
              <span className="cp-panel-ico">{IcTruck}</span>
              <div>
                <p className="cp-panel-title">1. Supplier Entity</p>
                <p className="cp-panel-sub">Selecciona el proveedor activo</p>
              </div>
            </div>

            <div style={{ position:"relative" }}>
              <div className="cp-prov-search-wrap" onClick={() => setShowProvDrop(v => !v)}>
                <span className="cp-prov-search-ico">{IcSearch}</span>
                <input
                  className="cp-prov-search-input"
                  placeholder="Buscar proveedor..."
                  value={provSearch}
                  onChange={e => { setProvSearch(e.target.value); setShowProvDrop(true); }}
                  onFocus={() => setShowProvDrop(true)}
                />
                <span className="cp-prov-chevron">▾</span>
              </div>
              {showProvDrop && (
                <div className="cp-dropdown" onMouseLeave={() => setShowProvDrop(false)}>
                  {provFiltrados.length === 0
                    ? <p className="cp-dropdown-empty">Sin resultados</p>
                    : provFiltrados.map(p => (
                        <button key={p.id} className="cp-dropdown-item"
                          onClick={() => { setProveedorId(p.id); setProvSearch(p.nombre); setShowProvDrop(false); }}>
                          <div className="cp-prov-ico" style={{ width:28,height:28,fontSize:".72rem" }}>{p.nombre[0]}</div>
                          <div>
                            <p style={{fontWeight:600,fontSize:".82rem"}}>{p.nombre}</p>
                            <p style={{fontSize:".72rem",color:"var(--muted)"}}>{p.contacto}</p>
                          </div>
                        </button>
                      ))
                  }
                </div>
              )}
            </div>

            {proveedorActivo && (
              <div className="cp-prov-info">
                <div className="cp-prov-info-row">
                  <div>
                    <p className="cp-prov-info-label">Contacto</p>
                    <p className="cp-prov-info-val">{proveedorActivo.contacto || "—"}</p>
                  </div>
                  <div>
                    <p className="cp-prov-info-label">Teléfono</p>
                    <p className="cp-prov-info-val">{proveedorActivo.telefono || "—"}</p>
                  </div>
                  <div>
                    <p className="cp-prov-info-label">Email</p>
                    <p className="cp-prov-info-val">{proveedorActivo.email || "—"}</p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* 2. Agregar productos */}
          <div className="cp-panel">
            <div className="cp-panel-head">
              <span className="cp-panel-ico">{IcBox}</span>
              <div>
                <p className="cp-panel-title">2. Line Item Entry</p>
                <p className="cp-panel-sub">Busca y agrega productos al registro</p>
              </div>
            </div>

            <div className="cp-line-grid">
              {/* Búsqueda producto */}
              <div className="cp-field cp-field--lg" style={{ position:"relative" }}>
                <label>Producto / SKU</label>
                <div className="cp-prod-search-wrap">
                  <span>{IcSearch}</span>
                  <input
                    className="cp-prod-search-input"
                    placeholder="Buscar inventario... SKU-001"
                    value={prodSearch}
                    onChange={e => { setProdSearch(e.target.value); setShowProdDrop(true); setProdSel(null); }}
                    onFocus={() => setShowProdDrop(true)}
                  />
                </div>
                {showProdDrop && prodSearch && (
                  <div className="cp-dropdown" onMouseLeave={() => setShowProdDrop(false)}>
                    {prodFiltrados.length === 0
                      ? <p className="cp-dropdown-empty">Sin resultados</p>
                      : prodFiltrados.map(p => (
                          <button key={p.id} className="cp-dropdown-item" onClick={() => seleccionarProd(p)}>
                            <div className="cp-prod-thumb">{p.nombre[0]}</div>
                            <div>
                              <p style={{fontWeight:600,fontSize:".82rem"}}>{p.nombre}</p>
                              <p style={{fontSize:".71rem",color:"var(--muted)"}}>SKU: {p.codigo||p.id.slice(-6)} · Stock: {p.stockActual}</p>
                            </div>
                            <span style={{marginLeft:"auto",fontWeight:700,color:"var(--primary)",fontSize:".78rem",flexShrink:0}}>
                              ${p.precioCompra?.toLocaleString()||"—"}
                            </span>
                          </button>
                        ))
                    }
                  </div>
                )}
              </div>

              {/* Cantidad */}
              <div className="cp-field">
                <label>Cantidad</label>
                <input type="number" min="1" value={prodQty} onChange={e=>setProdQty(e.target.value)} className="cp-input" placeholder="0.00"/>
              </div>

              {/* Costo */}
              <div className="cp-field">
                <label>Costo unit. ($)</label>
                <input type="number" min="0" value={prodCosto} onChange={e=>setProdCosto(e.target.value)} className="cp-input" placeholder="0.00"/>
              </div>
            </div>

            <button
              className="cp-btn-add-item"
              onClick={agregarItem}
              disabled={!prodSel}
            >
              {IcPlus} Add Item to Registry
            </button>
          </div>
        </div>

        {/* ── CARRITO LATERAL ── */}
        <div className="cp-basket">
          <div className="cp-basket-header">
            <span className="cp-basket-title">Purchase Basket</span>
            {items.length > 0 && <span className="cp-basket-count">{items.length} ITEMS</span>}
          </div>

          <div className="cp-basket-items">
            {items.length === 0 ? (
              <div className="cp-basket-empty">
                <div className="cp-basket-empty-ico">{IcBox}</div>
                <p>Agrega productos al registro</p>
              </div>
            ) : items.map(it => (
              <div key={it.productoId} className="cp-basket-item">
                <div className="cp-basket-item-main">
                  <p className="cp-basket-item-name">{it.nombre}</p>
                  <p className="cp-basket-item-sku">SKU: {it.sku}</p>
                  <div className="cp-basket-item-meta">
                    <span>QTY <strong>{it.cantidad}</strong></span>
                    <span>UNIT <strong>${it.costo.toLocaleString()}</strong></span>
                  </div>
                </div>
                <div className="cp-basket-item-right">
                  <span className="cp-basket-item-total">
                    ${it.subtotal.toLocaleString("es-CO",{minimumFractionDigits:2})}
                  </span>
                  <button className="cp-basket-item-del" onClick={() => quitarItem(it.productoId)}>{IcX}</button>
                </div>
              </div>
            ))}
          </div>

          <div className="cp-basket-totals">
            <div className="cp-basket-row"><span>Subtotal</span><span>${subtotal.toLocaleString("es-CO",{minimumFractionDigits:2})}</span></div>
          </div>

          <div className="cp-basket-total-final">
            <span>TOTAL</span>
            <span>${total.toLocaleString("es-CO",{minimumFractionDigits:2})}</span>
          </div>

          <button
            className="cp-btn-register"
            onClick={() => setShowConfirm(true)}
            disabled={!proveedorId || items.length === 0}
          >
            Register Purchase
          </button>

          <div className="cp-basket-note">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            Las compras registradas actualizarán el stock de inventario automáticamente.
          </div>
        </div>
      </div>

      {/* ── MODAL CONFIRMACIÓN ── */}
      {showConfirm && (
        <div className="cp-modal-overlay" onClick={e=>e.target===e.currentTarget&&setShowConfirm(false)}>
          <div className="cp-modal">
            <div className="cp-modal-header">
              <div>
                <p className="cp-modal-tag">Confirmar Orden</p>
                <h2 className="cp-modal-title">Registro de Compra</h2>
              </div>
              <button className="cp-modal-close" onClick={()=>setShowConfirm(false)}>{IcX}</button>
            </div>

            <div className="cp-modal-body">
              <p className="cp-modal-prov">
                <span>{IcTruck}</span>
                {proveedorActivo?.nombre || "Proveedor"}
              </p>

              <table className="cp-modal-table">
                <thead>
                  <tr><th>Producto</th><th>SKU</th><th style={{textAlign:"center"}}>Cant.</th><th style={{textAlign:"right"}}>Costo</th><th style={{textAlign:"right"}}>Subtotal</th></tr>
                </thead>
                <tbody>
                  {items.map((it,i) => (
                    <tr key={i}>
                      <td style={{fontWeight:600}}>{it.nombre}</td>
                      <td style={{color:"var(--muted)",fontSize:".75rem"}}>{it.sku}</td>
                      <td style={{textAlign:"center"}}>{String(it.cantidad).padStart(2,"0")}</td>
                      <td style={{textAlign:"right"}}>${it.costo.toLocaleString()}</td>
                      <td style={{textAlign:"right",fontWeight:700,color:"var(--primary)"}}>
                        ${it.subtotal.toLocaleString("es-CO",{minimumFractionDigits:2})}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="cp-modal-total">
                <span>TOTAL DUE</span>
                <span>${total.toLocaleString("es-CO",{minimumFractionDigits:2})}</span>
              </div>
            </div>

            <div className="cp-modal-footer">
              <button className="cp-btn-outline" onClick={()=>setShowConfirm(false)}>Cancelar</button>
              <button className="cp-btn-register" onClick={confirmar} disabled={loading} style={{padding:".65rem 1.5rem"}}>
                {loading ? "Procesando..." : <>{IcCheck} Confirmar Compra</>}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default CompraForm;