import { useEffect, useState, useRef } from "react";
import { crearVenta } from "../services/ventaService";
import { listarClientes } from "../services/clienteService";
import { listarProductos } from "../services/productoService";
import "../css/venta.css";

const METODOS = [
  { key: "EFECTIVO",      ico: "💵", label: "Efectivo" },
  { key: "TARJETA",       ico: "💳", label: "Tarjeta" },
  { key: "TRANSFERENCIA", ico: "🏦", label: "Transferencia" },
];

const getIniciales = (n) => n ? n.split(" ").map(x => x[0]).join("").toUpperCase().slice(0,2) : "?";

function VentaForm({ onVolver, token }) {
  const [clientes, setClientes]             = useState([]);
  const [productos, setProductos]           = useState([]);
  const [loading, setLoading]               = useState(false);
  const [error, setError]                   = useState(null);

  // Cliente por ID
  const [clienteIdInput, setClienteIdInput] = useState("");
  const [clienteEncontrado, setClienteEncontrado] = useState(null);
  const [clienteError, setClienteError]     = useState(false);

  // Items carrito
  const [items, setItems]     = useState([]);
  const [metodoPago, setMetodoPago] = useState("EFECTIVO");

  // Modal catálogo
  const [showCatalog, setShowCatalog]       = useState(false);
  const [catalogSearch, setCatalogSearch]   = useState("");
  const [catalogSelected, setCatalogSelected] = useState(null);
  const [catalogQty, setCatalogQty]         = useState(1);

  // Escaneo por ID
  const [skuId, setSkuId]   = useState("");
  const [skuQty, setSkuQty] = useState(1);

  // Modal efectivo
  const [showModal, setShowModal] = useState(false);
  const [dinero, setDinero]       = useState("");

  const TAX = 0.16;

  useEffect(() => {
    if (token) {
      listarClientes(token).then(setClientes).catch(() => {});
      listarProductos(token).then(setProductos).catch(() => {});
    }
  }, [token]);

  // ── Buscar cliente por ID ──
  const buscarCliente = () => {
    const found = clientes.find(c =>
      String(c.identificacion).trim() === clienteIdInput.trim() ||
      String(c.id).trim() === clienteIdInput.trim()
    );
    if (found) { setClienteEncontrado(found); setClienteError(false); }
    else        { setClienteEncontrado(null);  setClienteError(true); }
  };

  // ── Carrito ──
  const agregarItem = (prod, cantidad) => {
    setItems(prev => {
      const exist = prev.find(i => i.productoId === prod.id);
      if (exist) return prev.map(i => i.productoId === prod.id ? { ...i, cantidad: i.cantidad + Number(cantidad) } : i);
      return [...prev, { productoId: prod.id, nombre: prod.nombre, precio: parseFloat(prod.precioVenta||0), cantidad: Number(cantidad) }];
    });
  };

  const quitarItem = (pid) => setItems(prev => prev.filter(i => i.productoId !== pid));

  // ── Agregar desde catálogo ──
  const confirmarCatalogo = () => {
    if (!catalogSelected || catalogQty < 1) return;
    agregarItem(catalogSelected, catalogQty);
    setShowCatalog(false);
    setCatalogSelected(null);
    setCatalogQty(1);
    setCatalogSearch("");
  };
  

  // ── Agregar por SKU/ID ──
  const agregarPorSku = () => {
    const prod = productos.find(p =>
      String(p.id) === skuId.trim() || p.sku?.toLowerCase() === skuId.trim().toLowerCase()
    );
    if (!prod) { alert("Producto no encontrado con ese ID"); return; }
    agregarItem(prod, skuQty);
    setSkuId(""); setSkuQty(1);
  };

  // ── Totales ──
  const subtotal = items.reduce((a, i) => a + i.precio * i.cantidad, 0);
  const impuesto = subtotal * TAX;
  const total    = subtotal + impuesto;

  // ── Registrar ──
  const registrar = async () => {
    setLoading(true); setError(null);
    try {
      await crearVenta({
        clienteId: clienteEncontrado.id,
        productos: items.map(i => ({ productoId: i.productoId, cantidad: i.cantidad })),
        pagos: [{ metodo: metodoPago, monto: subtotal }]
      }, token);
      onVolver();
    } catch(e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const handleRegistrar = () => {
    if (!clienteEncontrado) { setError("Busca y valida un cliente primero"); return; }
    if (items.length === 0)  { setError("Agrega al menos un producto"); return; }
    setError(null);
    if (metodoPago === "EFECTIVO") { setDinero(""); setShowModal(true); }
    else registrar();
  };

  const catalogoFiltrado = productos.filter(p =>
    p.nombre?.toLowerCase().includes(catalogSearch.toLowerCase()) ||
    String(p.id).includes(catalogSearch)
  );

  return (
    <div className="vt-root">

      {/* Topbar */}
      <header className="vt-top">
        <div className="vt-top-left">
          <span className="vt-brand">DriveMaster</span>
          <span className="vt-div">|</span>
          <span className="vt-breadcrumb">Ventas</span>
        </div>
        <div className="vt-tabs">
          <button className="vt-tab active">Nueva Venta</button>
          <button className="vt-tab" onClick={onVolver}>Historial</button>
          <button className="vt-tab">Cortes de Caja</button>
        </div>
        <div className="vt-top-right">
          <button className="vt-ico-btn">🔔</button>
          <button className="vt-ico-btn">⚙</button>
          <div className="vt-avatar">A</div>
        </div>
      </header>

      {/* Page header */}
      <div className="vt-page-header" style={{ paddingBottom:".75rem" }}>
        <div>
          <h1 className="vt-h1">Nueva Venta</h1>
          <p className="vt-sub">Registra una transacción nueva para un cliente</p>
        </div>
      </div>

      {error && <p className="vt-error">{error}</p>}

      <div className="vf-body">

        {/* ── Columna principal ── */}
        <div style={{ display:"flex", flexDirection:"column", gap:".9rem" }}>

          {/* 1. Info cliente */}
          <div className="vf-panel">
            <div className="vf-section-header">
              <div className="vf-section-ico">👤</div>
              <div>
                <p className="vf-section-title">Información del Cliente</p>
                <p className="vf-section-sub">Busca por cédula o ID del sistema</p>
              </div>
            </div>

            <div style={{ display:"flex", gap:".5rem", alignItems:"flex-end" }}>
              <div className="vf-field" style={{ flex:1 }}>
                <label>Buscar por ID</label>
                <input
                  placeholder="Ingresa cédula o ID..."
                  value={clienteIdInput}
                  onChange={e => { setClienteIdInput(e.target.value); setClienteEncontrado(null); setClienteError(false); }}
                  onKeyDown={e => e.key === "Enter" && buscarCliente()}
                  style={clienteError ? { borderColor:"var(--red)" } : {}}
                />
              </div>
              <button
                onClick={buscarCliente}
                style={{
                  background: "var(--surface2)", border:"1px solid var(--border2)",
                  borderRadius:"10px", color:"var(--text)",
                  fontFamily:"'DM Sans',sans-serif", fontWeight:700, fontSize:".8rem",
                  padding:".65rem 1rem", cursor:"pointer", whiteSpace:"nowrap",
                  transition:"all .15s",
                }}
              >
                Buscar ✓
              </button>
            </div>

            {/* Estado cliente */}
            {clienteEncontrado && (
              <div className="vf-cliente-found">
                <div className="vf-cliente-ava">{getIniciales(clienteEncontrado.nombre)}</div>
                <div style={{ flex:1 }}>
                  <p className="vf-cliente-found-name">{clienteEncontrado.nombre}</p>
                  <p className="vf-cliente-found-sub">{clienteEncontrado.correo || clienteEncontrado.identificacion}</p>
                </div>
                <span className="vf-cliente-found-badge">Cliente Encontrado ✓</span>
              </div>
            )}
            {clienteError && (
              <p style={{ fontSize:".76rem", color:"var(--red)", display:"flex", alignItems:"center", gap:".35rem" }}>
                ✕ No se encontró ningún cliente con ese ID
              </p>
            )}
          </div>

          {/* 2. Productos */}
          <div className="vf-panel">
            <div className="vf-section-header">
              <div className="vf-section-ico">📦</div>
              <div>
                <p className="vf-section-title">Agregar Productos</p>
                <p className="vf-section-sub">Usa el catálogo o ingresa el ID directamente</p>
              </div>
            </div>

            <div className="vf-productos-grid">

              {/* Opción A: Catálogo */}
              <div style={{ display:"flex", flexDirection:"column", gap:".5rem" }}>
                <p style={{ fontSize:".65rem", fontWeight:700, textTransform:"uppercase", letterSpacing:".1em", color:"var(--muted)" }}>
                  Por Catálogo
                </p>
                <button className="vf-open-catalog-btn" onClick={() => { setShowCatalog(true); setCatalogSearch(""); setCatalogSelected(null); setCatalogQty(1); }}>
                  <span>🗂</span> Seleccionar del Catálogo
                </button>
              </div>

              {/* Divisor */}
              <div style={{ display:"flex", flexDirection:"column", gap:".5rem" }}>
                <p style={{ fontSize:".65rem", fontWeight:700, textTransform:"uppercase", letterSpacing:".1em", color:"var(--muted)" }}>
                  Por ID / SKU
                </p>
                <div style={{ display:"flex", gap:".4rem", alignItems:"flex-start" }}>
                  <div className="vf-field" style={{ flex:1 }}>
                    <input
                      placeholder="ID o código SKU..."
                      value={skuId}
                      onChange={e => setSkuId(e.target.value)}
                      onKeyDown={e => e.key === "Enter" && agregarPorSku()}
                    />
                  </div>
                  <div className="vf-scan-qty-wrap">
                    <input
                      type="number" min="1" value={skuQty}
                      onChange={e => setSkuQty(e.target.value)}
                      className="vf-scan-qty"
                    />
                  </div>
                  <button className="vf-scan-add" onClick={agregarPorSku} style={{ marginTop:0 }}>
                    + Añadir
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* 3. Método de pago */}
          <div className="vf-panel">
            <div className="vf-section-header">
              <div className="vf-section-ico">💳</div>
              <div>
                <p className="vf-section-title">Método de Pago</p>
              </div>
            </div>
            <div className="vf-metodos">
              {METODOS.map(m => (
                <button
                  key={m.key}
                  className={`vf-metodo-btn${metodoPago === m.key ? " active" : ""}`}
                  onClick={() => setMetodoPago(m.key)}
                  type="button"
                >
                  <span className="ico">{m.ico}</span>
                  {m.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* ── Resumen lateral ── */}
        <div className="vf-resumen">
          <div className="vf-resumen-header">
            <span className="vf-resumen-title">Resumen de Venta</span>
            {items.length > 0 && <span className="vf-items-count">{items.length} ITEMS</span>}
          </div>

          <div className="vf-resumen-items">
            {items.length === 0 ? (
              <p className="vf-resumen-empty">Agrega productos al carrito</p>
            ) : items.map(it => (
              <div key={it.productoId} className="vf-resumen-item">
                <div style={{ flex:1, minWidth:0 }}>
                  <p className="vf-resumen-item-name" style={{ overflow:"hidden", textOverflow:"ellipsis", whiteSpace:"nowrap" }}>{it.nombre}</p>
                  <p className="vf-resumen-item-sub">{it.cantidad} x ${it.precio.toLocaleString()}</p>
                </div>
                <div style={{ display:"flex", alignItems:"center", gap:".35rem", flexShrink:0 }}>
                  <span className="vf-resumen-item-price">${(it.precio*it.cantidad).toLocaleString()}</span>
                  <button className="vf-resumen-item-del" onClick={() => quitarItem(it.productoId)}>✕</button>
                </div>
              </div>
            ))}
          </div>

          <hr className="vf-resumen-divider" />

          <div className="vf-resumen-totals">
            <div className="vf-resumen-total-row">
              <span className="label">Subtotal</span>
              <span className="val">${subtotal.toLocaleString("es-CO", { minimumFractionDigits:2 })}</span>
            </div>
            <div className="vf-resumen-total-row">
              <span className="label">Impuestos (16%)</span>
              <span className="val">${impuesto.toLocaleString("es-CO", { minimumFractionDigits:2 })}</span>
            </div>
          </div>

          <div className="vf-total-final">
            <span className="vf-total-label">Total</span>
            <div style={{ display:"flex", alignItems:"baseline", gap:".2rem" }}>
              <span className="vf-total-curr">COP</span>
              <span className="vf-total-val">${total.toLocaleString("es-CO", { minimumFractionDigits:2 })}</span>
            </div>
          </div>

          <button className="vf-btn-registrar" onClick={handleRegistrar} disabled={loading}>
            {loading ? "Procesando..." : "Registrar Venta"}
          </button>
        </div>
      </div>

      {/* ── MODAL CATÁLOGO ── */}
      {showCatalog && (
        <div className="vf-catalog-overlay" onClick={e => e.target === e.currentTarget && setShowCatalog(false)}>
          <div className="vf-catalog-modal">
            <div className="vf-catalog-modal-header">
              <span className="vf-catalog-modal-title">Catálogo de Productos</span>
              <button className="vf-catalog-close" onClick={() => setShowCatalog(false)}>✕</button>
            </div>
            <div className="vf-catalog-modal-search">
              <input
                placeholder="Buscar por nombre o ID..."
                value={catalogSearch}
                onChange={e => setCatalogSearch(e.target.value)}
                autoFocus
              />
            </div>
            <div className="vf-catalog-list">
              {catalogoFiltrado.length === 0 ? (
                <p style={{ textAlign:"center", color:"var(--muted)", fontSize:".82rem", padding:"2rem 0" }}>Sin resultados</p>
              ) : catalogoFiltrado.map(p => (
                <div
                  key={p.id}
                  className={`vf-catalog-item${catalogSelected?.id === p.id ? " selected" : ""}`}
                  onClick={() => setCatalogSelected(p)}
                >
                  <div className="vf-catalog-item-left">
                    <div className="vf-catalog-item-ico">🔧</div>
                    <div>
                      <p className="vf-catalog-item-name">{p.nombre}</p>
                      <p className="vf-catalog-item-stock">Stock: {p.cantidad ?? p.stock ?? "—"} uds · ID: {p.id}</p>
                    </div>
                  </div>
                  <span className="vf-catalog-item-price">${parseFloat(p.precioVenta||0).toLocaleString()}</span>
                </div>
              ))}
            </div>
            <div className="vf-catalog-modal-footer">
              <span className="vf-catalog-selected-info">
                {catalogSelected ? <><strong>{catalogSelected.nombre}</strong> seleccionado</> : "Selecciona un producto"}
              </span>
              <div className="vf-catalog-qty-wrap">
                <span className="vf-catalog-qty-label">Cant.</span>
                <input
                  type="number" min="1" value={catalogQty}
                  onChange={e => setCatalogQty(Number(e.target.value))}
                  className="vf-catalog-qty-input"
                />
              </div>
              <button
                className="vf-catalog-confirm"
                onClick={confirmarCatalogo}
                disabled={!catalogSelected || catalogQty < 1}
              >
                Agregar al carrito
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── MODAL EFECTIVO ── */}
      {showModal && (
        <div className="vf-modal-overlay">
          <div className="vf-modal">
            <div className="vf-modal-header">
              <span className="vf-modal-ico">💵</span>
              <h2 className="vf-modal-title">Pago en Efectivo</h2>
            </div>
            <div className="vf-modal-total-box">
              <span className="vf-modal-total-label">Total a cobrar</span>
              <span className="vf-modal-total-val">${total.toLocaleString("es-CO", { minimumFractionDigits:2 })}</span>
            </div>
            <div className="vf-field" style={{ marginBottom:"1rem" }}>
              <label>Dinero del cliente</label>
              <input
                type="number" min={total} value={dinero}
                onChange={e => setDinero(e.target.value)}
                placeholder={`Mínimo $${total.toFixed(0)}`}
                autoFocus
              />
            </div>
            {dinero && Number(dinero) >= total && (
              <div className="vf-vuelto-box">
                <span className="vf-vuelto-label">Vuelto</span>
                <span className="vf-vuelto-val">${(Number(dinero)-total).toLocaleString("es-CO", { minimumFractionDigits:2 })}</span>
              </div>
            )}
            <div className="vf-modal-actions">
              <button className="vf-modal-cancel" onClick={() => setShowModal(false)}>Cancelar</button>
              <button
                className="vf-modal-confirm"
                disabled={loading || !dinero || Number(dinero) < total}
                onClick={async () => { await registrar(); setShowModal(false); }}
              >
                {loading ? "Procesando..." : "Confirmar"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default VentaForm;