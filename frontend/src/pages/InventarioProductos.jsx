import { useEffect, useState } from "react";
import { getInventarioProductos } from "../services/reporteService";
import "../css/reportes.css";

const POR_PAG  = 10;
const EST_CSS  = { NORMAL:"normal", BAJO:"bajo", CRITICO:"critico", AGOTADO:"agotado" };

const IcBox    = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>;
const IcWarn   = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>;
const IcVal    = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><rect x="1" y="4" width="22" height="16" rx="2"/><line x1="1" y1="10" x2="23" y2="10"/></svg>;
const IcSearch = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>;
const IcBan    = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>;

function InventarioProductos({ token }) {
  const [data,    setData]    = useState(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(null);
  const [busq,    setBusq]    = useState("");
  const [filtro,  setFiltro]  = useState("todos");
  const [pagina,  setPagina]  = useState(1);
  const [menu3p,  setMenu3p]  = useState(null); // id del producto con menú abierto

  useEffect(() => {
    getInventarioProductos(token)
      .then(setData).catch(e=>setError(e.message)).finally(()=>setLoading(false));
    const closeMenu = () => setMenu3p(null);
    document.addEventListener("click", closeMenu);
    return () => document.removeEventListener("click", closeMenu);
  }, [token]);

  if (loading) return <div className="rp-loading"><div className="rp-spinner"/>Cargando inventario...</div>;
  if (error)   return <p className="rp-error">{error}</p>;
  if (!data)   return null;

  const filtrados = data.productos.filter(p => {
    const q  = busq.toLowerCase();
    const mQ = !q || [p.nombre,p.codigo,p.categoria,p.marca].some(v=>v?.toLowerCase().includes(q));
    const mF = filtro==="todos" || p.estadoStock?.toLowerCase()===filtro;
    return mQ && mF;
  });

  const totalPags = Math.ceil(filtrados.length / POR_PAG) || 1;
  const paginados = filtrados.slice((pagina-1)*POR_PAG, pagina*POR_PAG);
  const fmt = n => (n||0).toLocaleString("es-CO",{minimumFractionDigits:2});

  const getPct = p => {
    const max = (p.stockMinimo||1)*5;
    return Math.min(Math.round(((p.stockActual||0)/max)*100), 100);
  };

  return (
    <>
      {/* KPIs */}
      <div className="rp-kpi-grid" style={{ marginBottom:"1rem" }}>
        {[
          { ico:IcBox,  label:"Total Productos",   val:data.totalProductos,       cls:"" },
          { ico:IcBan,  label:"Agotados",           val:data.productosAgotados,    cls:"red" },
          { ico:IcWarn, label:"Bajo Stock",          val:data.productosBajoStock,   cls:"amber" },
          { ico:IcVal,  label:"Valor Inventario",   val:`$${(data.valorTotalInventario||0).toLocaleString("es-CO",{minimumFractionDigits:0})}`, cls:"orange" },
        ].map((k,i)=>(
          <div className="rp-kpi" key={i}>
            <div className="rp-kpi-top"><span className="rp-kpi-ico">{k.ico}</span></div>
            <p className="rp-kpi-label">{k.label}</p>
            <p className={`rp-kpi-val ${k.cls}`}>{typeof k.val==="number"?k.val.toLocaleString():k.val}</p>
          </div>
        ))}
      </div>

      {/* Alerta */}
      {(data.productosAgotados>0||data.productosBajoStock>0) && (
        <div className="rp-alert-banner">
          <span className="ico">{IcWarn}</span>
          <span>
            {data.productosAgotados>0 && <><strong>{data.productosAgotados} agotado{data.productosAgotados>1?"s":""}</strong>. </>}
            {data.productosBajoStock>0 && <><strong>{data.productosBajoStock} con stock crítico/bajo</strong>.</>}
            {" "}Revisa tu inventario.
          </span>
        </div>
      )}

      {/* Tabla */}
      <div className="rp-panel">
        <div className="rp-panel-header">
          <span className="rp-panel-title">Catálogo de Productos</span>
          <div className="rp-panel-actions">
            <div className="rp-period-tabs">
              {[{k:"todos",l:"Todos"},{k:"normal",l:"Normal"},{k:"bajo",l:"Bajo"},{k:"critico",l:"Crítico"},{k:"agotado",l:"Agotado"}].map(f=>(
                <button key={f.k} className={`rp-period-tab${filtro===f.k?" active":""}`} onClick={()=>{setFiltro(f.k);setPagina(1);}}>{f.l}</button>
              ))}
            </div>
            <div className="rp-search-wrap">
              <span className="ico">{IcSearch}</span>
              <input className="rp-search" placeholder="Buscar..." value={busq} onChange={e=>{setBusq(e.target.value);setPagina(1);}}/>
            </div>
          </div>
        </div>

        <div className="rp-table-wrap">
          <table className="rp-table">
            <thead>
              <tr>
                <th>Código</th><th>Producto</th>
                <th style={{maxWidth:130}}>Categoría</th>
                <th>Marca</th>
                <th style={{textAlign:"right"}}>Precio</th>
                <th>Stock</th><th>Estado</th>
                <th style={{textAlign:"right"}}>Vendidas</th>
                <th style={{textAlign:"right"}}>Generado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {paginados.length===0
                ? <tr><td colSpan={10} className="rp-empty">Sin resultados</td></tr>
                : paginados.map(p=>{
                    const pct    = getPct(p);
                    const estCss = EST_CSS[p.estadoStock]||"normal";
                    return (
                      <tr key={p.id}>
                        <td style={{fontSize:".72rem",color:"var(--muted)",fontFamily:"monospace"}}>#{p.codigo||p.id?.slice(-6)}</td>
                        <td>
                          <div style={{display:"flex",alignItems:"center",gap:".5rem"}}>
                            <div style={{width:32,height:32,borderRadius:8,background:"var(--surface3)",display:"grid",placeItems:"center",fontSize:".8rem",fontWeight:800,color:"var(--primary)",flexShrink:0}}>
                              {p.nombre?.[0]?.toUpperCase()}
                            </div>
                            <div>
                              <p style={{fontWeight:600,fontSize:".83rem"}}>{p.nombre}</p>
                              <p style={{fontSize:".7rem",color:"var(--muted)"}}>{p.marca}</p>
                            </div>
                          </div>
                        </td>
                        {/* Categoría con ellipsis y tooltip */}
                        <td style={{maxWidth:130}}>
                          <span
                            title={p.categoria}
                            style={{
                              display:"inline-block",maxWidth:120,
                              overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap",
                              background:"rgba(34,197,94,.1)",color:"var(--green)",
                              padding:".2rem .55rem",borderRadius:20,
                              fontSize:".7rem",fontWeight:700,
                              cursor:"default",verticalAlign:"middle"
                            }}
                          >{p.categoria||"—"}</span>
                        </td>
                        <td style={{fontSize:".77rem",color:"var(--muted)"}}>{p.marca||"—"}</td>
                        <td style={{textAlign:"right",fontWeight:700}}>${fmt(p.precioVenta)}</td>
                        <td>
                          <div style={{display:"flex",alignItems:"center",gap:".45rem"}}>
                            <div style={{width:70,height:5,background:"var(--surface3)",borderRadius:3,overflow:"hidden"}}>
                              <div style={{
                                height:"100%",width:`${pct}%`,borderRadius:3,
                                background: estCss==="normal"?"var(--green)":estCss==="bajo"?"var(--amber)":estCss==="critico"?"var(--primary)":"var(--red)"
                              }}/>
                            </div>
                            <span style={{fontSize:".77rem",fontWeight:700}}>{p.stockActual}</span>
                          </div>
                        </td>
                        <td><span className={`rp-badge ${estCss}`}>{p.estadoStock}</span></td>
                        <td style={{textAlign:"right",fontWeight:700,color:"var(--blue)"}}>{p.unidadesVendidas}</td>
                        <td style={{textAlign:"right",fontWeight:700,color:"var(--green)"}}>${fmt(p.totalGenerado)}</td>
                        {/* Menú 3 puntos */}
                        <td style={{position:"relative"}}>
                          <button
                            className="rp-icon-btn"
                            style={{fontSize:"1.1rem",fontWeight:800,letterSpacing:".05em"}}
                            onClick={e=>{e.stopPropagation();setMenu3p(menu3p===p.id?null:p.id);}}
                          >⋮</button>
                          {menu3p===p.id && (
                            <div className="rp-3p-menu" onClick={e=>e.stopPropagation()}>
                              <button className="rp-3p-item">Editar</button>
                              <button className="rp-3p-item">Ver detalle</button>
                              <button className="rp-3p-item">Reabastecer</button>
                              <button className="rp-3p-item rp-3p-item--danger">Eliminar</button>
                            </div>
                          )}
                        </td>
                      </tr>
                    );
                  })
              }
            </tbody>
          </table>
        </div>

        <div className="rp-pag">
          <span>Mostrando <strong>{filtrados.length===0?0:(pagina-1)*POR_PAG+1}–{Math.min(pagina*POR_PAG,filtrados.length)}</strong> de <strong>{filtrados.length}</strong></span>
          <div className="rp-pag-right">
            <button className="rp-pag-btn" onClick={()=>setPagina(p=>Math.max(1,p-1))} disabled={pagina===1}>‹</button>
            {Array.from({length:Math.min(totalPags,5)},(_,i)=>i+1).map(n=>(
              <button key={n} className={`rp-pag-num${pagina===n?" on":""}`} onClick={()=>setPagina(n)}>{n}</button>
            ))}
            <button className="rp-pag-btn" onClick={()=>setPagina(p=>Math.min(totalPags,p+1))} disabled={pagina===totalPags}>›</button>
          </div>
        </div>
      </div>
    </>
  );
}

export default InventarioProductos;