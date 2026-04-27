import { useEffect, useState } from "react";
import { getInventarioClientes, getHistorialCliente } from "../services/reporteService";
import "../css/reportes.css";

const AVATARES = ["#2563eb","#7c3aed","#db2777","#059669","#d97706","#dc2626","#0891b2"];
const getColor    = n => n ? AVATARES[n.charCodeAt(0) % AVATARES.length] : AVATARES[0];
const getIniciales= n => n ? n.split(" ").map(x=>x[0]).join("").toUpperCase().slice(0,2) : "?";

const IcEye   = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="14" height="14"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>;
const IcClose = <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>;
const IcSearch= <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="13" height="13"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>;

const POR_PAG = 10;

function InventarioClientes({ token }) {
  const [data,        setData]        = useState(null);
  const [loading,     setLoading]     = useState(true);
  const [error,       setError]       = useState(null);
  const [busqueda,    setBusqueda]    = useState("");
  const [filtroClase, setFiltroClase] = useState("todos");
  const [pagina,      setPagina]      = useState(1);
  const [historial,   setHistorial]   = useState(null);
  const [loadHist,    setLoadHist]    = useState(false);

  useEffect(() => {
    getInventarioClientes(token)
      .then(setData).catch(e => setError(e.message)).finally(() => setLoading(false));
  }, [token]);

  if (loading) return <div className="rp-loading"><div className="rp-spinner"/>Cargando inventario de clientes...</div>;
  if (error)   return <p className="rp-error">{error}</p>;
  if (!data)   return null;

  const totalRevenue = Math.max(data.clientes.reduce((s,c) => s+(c.totalGastado||0), 0), 1);

  const filtrados = data.clientes.filter(c => {
    const q  = busqueda.toLowerCase();
    const mQ = !q || [c.nombre,c.identificacion,c.correo,c.telefono].some(v=>v?.toLowerCase().includes(q));
    const mC = filtroClase==="todos" || c.clasificacion===filtroClase.toUpperCase();
    return mQ && mC;
  });

  const totalPags = Math.ceil(filtrados.length / POR_PAG) || 1;
  const paginados = filtrados.slice((pagina-1)*POR_PAG, pagina*POR_PAG);
  const fmt = n => (n||0).toLocaleString("es-CO",{minimumFractionDigits:2});

  const verHistorial = async id => {
    setLoadHist(true);
    try { setHistorial(await getHistorialCliente(id, token)); }
    catch(e) { alert(e.message); } finally { setLoadHist(false); }
  };

  const kpiIcos = [
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>,
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>,
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 014-4h14M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 01-4 4H3"/></svg>,
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>,
  ];

  return (
    <>
      <div className="rp-kpi-grid" style={{ marginBottom:"1rem" }}>
        {[
          { label:"Total Clientes",  val:data.totalClientes,       cls:"",      idx:0 },
          { label:"Frecuentes",      val:data.clientesFrecuentes,  cls:"blue",  idx:1 },
          { label:"Ocasionales",     val:data.clientesOcasionales, cls:"amber", idx:2 },
          { label:"Nuevos",          val:data.clientesNuevos,      cls:"green", idx:3 },
        ].map((k,i) => (
          <div className="rp-kpi" key={i}>
            <div className="rp-kpi-top"><span className="rp-kpi-ico">{kpiIcos[k.idx]}</span></div>
            <p className="rp-kpi-label">{k.label}</p>
            <p className={`rp-kpi-val ${k.cls}`}>{k.val?.toLocaleString()}</p>
          </div>
        ))}
      </div>

      <div className="rp-panel">
        <div className="rp-panel-header">
          <span className="rp-panel-title">Directorio de Clientes</span>
          <div className="rp-panel-actions">
            <div className="rp-period-tabs">
              {["todos","frecuente","ocasional","nuevo"].map(f=>(
                <button key={f} className={`rp-period-tab${filtroClase===f?" active":""}`}
                  onClick={()=>{setFiltroClase(f);setPagina(1);}}>
                  {f.charAt(0).toUpperCase()+f.slice(1)}
                </button>
              ))}
            </div>
            <div className="rp-search-wrap">
              <span className="ico">{IcSearch}</span>
              <input className="rp-search" placeholder="Buscar cliente..." value={busqueda}
                onChange={e=>{setBusqueda(e.target.value);setPagina(1);}}/>
            </div>
          </div>
        </div>

        <div className="rp-table-wrap">
          <table className="rp-table">
            <thead>
              <tr>
                <th>#</th><th>Cliente</th><th>Identificación</th><th>Compras</th>
                <th style={{textAlign:"right"}}>Total Gastado</th>
                <th style={{textAlign:"right"}}>Ticket Prom.</th>
                <th style={{textAlign:"right"}}>% Revenue</th>
                <th>Última Compra</th><th>Pago Frecuente</th><th>Tipo</th><th></th>
              </tr>
            </thead>
            <tbody>
              {paginados.length===0
                ? <tr><td colSpan={11} className="rp-empty">Sin resultados</td></tr>
                : paginados.map((c,i) => {
                    const rank    = (pagina-1)*POR_PAG+i+1;
                    const rCls    = rank===1?"gold":rank===2?"silver":rank===3?"bronze":"normal";
                    const ticket  = c.totalCompras>0 ? c.totalGastado/c.totalCompras : 0;
                    const pctRev  = ((c.totalGastado/totalRevenue)*100).toFixed(1);
                    return (
                      <tr key={c.id}>
                        <td><span className={`rp-rank ${rCls}`}>{rank}</span></td>
                        <td>
                          <div className="rp-avatar-cell">
                            <div className="rp-avatar-ico" style={{background:getColor(c.nombre)}}>{getIniciales(c.nombre)}</div>
                            <div>
                              <p className="rp-avatar-name">{c.nombre}</p>
                              <p className="rp-avatar-sub">{c.correo||"—"}</p>
                            </div>
                          </div>
                        </td>
                        <td style={{fontSize:".75rem",color:"var(--muted)"}}>{c.identificacion||"—"}</td>
                        <td style={{fontWeight:700}}>{c.totalCompras}</td>
                        <td style={{textAlign:"right",fontWeight:800,color:"var(--primary)"}}>${fmt(c.totalGastado)}</td>
                        <td style={{textAlign:"right",color:"var(--blue)",fontWeight:600}}>${fmt(ticket)}</td>
                        <td style={{textAlign:"right"}}>
                          <div style={{display:"flex",alignItems:"center",gap:".3rem",justifyContent:"flex-end"}}>
                            <div style={{width:40,height:5,borderRadius:3,background:"var(--surface3)",overflow:"hidden"}}>
                              <div style={{height:"100%",width:`${Math.min(parseFloat(pctRev),100)}%`,background:"var(--primary)",borderRadius:3}}/>
                            </div>
                            <span style={{fontSize:".72rem",color:"var(--muted)",fontWeight:700}}>{pctRev}%</span>
                          </div>
                        </td>
                        <td style={{fontSize:".74rem",color:"var(--muted)"}}>{c.ultimaCompra||"—"}</td>
                        <td style={{fontSize:".74rem"}}>{c.metodoPagoMasUsado||"—"}</td>
                        <td><span className={`rp-badge ${c.clasificacion?.toLowerCase()||"nuevo"}`}>{c.clasificacion}</span></td>
                        <td>
                          <button className="rp-icon-btn" title="Ver historial" onClick={()=>verHistorial(c.id)} disabled={loadHist}>
                            {IcEye}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
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

      {historial && (
        <div className="rp-modal-overlay" onClick={e=>e.target===e.currentTarget&&setHistorial(null)}>
          <div className="rp-modal">
            <div className="rp-modal-header">
              <span className="rp-modal-title">Historial — {historial.nombre}</span>
              <button className="rp-modal-close" onClick={()=>setHistorial(null)}>{IcClose}</button>
            </div>
            <div className="rp-modal-body">
              {!historial.compras?.length
                ? <div className="rp-empty-state"><p>Sin compras registradas</p></div>
                : <table className="rp-table">
                    <thead><tr><th>ID</th><th>Fecha</th><th>Items</th><th>Método</th><th style={{textAlign:"right"}}>Total</th><th>Estado</th></tr></thead>
                    <tbody>
                      {historial.compras.map(v=>(
                        <tr key={v.id}>
                          <td style={{fontSize:".73rem",color:"var(--muted)"}}>{v.id?.slice(-6)}</td>
                          <td style={{fontSize:".74rem"}}>{v.fecha}</td>
                          <td style={{fontSize:".74rem"}}>{v.cantidadProductos}</td>
                          <td style={{fontSize:".74rem"}}>{v.metodoPago||"—"}</td>
                          <td style={{textAlign:"right",fontWeight:800,color:"var(--primary)"}}>
                            ${(v.total||0).toLocaleString("es-CO",{minimumFractionDigits:2})}
                          </td>
                          <td><span className={`rp-badge ${v.estado?.toLowerCase()==="pagada"?"frecuente":"ocasional"}`}>{v.estado}</span></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
              }
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default InventarioClientes;