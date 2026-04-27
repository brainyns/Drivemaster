import { useEffect, useState } from "react";
import { getReportesProductos } from "../services/reporteService";
import "../css/reportes.css";

const PERIODOS = [{k:"dia",l:"Hoy"},{k:"semana",l:"Semana"},{k:"mes",l:"Mes"}];

/* ── Pie chart SVG ────────────────────────────────────────────── */
function PieChart({ items, title }) {
  const validos = (items||[]).filter(i=>i.val>0);
  if (!validos.length) return (
    <div className="rp-chart-empty-box">Sin datos en este período</div>
  );
  const total  = validos.reduce((s,i)=>s+i.val,0);
  const COLORS = ["var(--primary)","var(--blue)","var(--purple)","var(--green)","var(--amber)"];
  const R=55, CX=65, CY=65;
  let angle = -Math.PI/2;
  const slices = validos.map((item,idx)=>{
    const pct=item.val/total; const start=angle; angle+=pct*2*Math.PI;
    const x1=CX+R*Math.cos(start), y1=CY+R*Math.sin(start);
    const x2=CX+R*Math.cos(angle), y2=CY+R*Math.sin(angle);
    return { ...item, path:`M${CX},${CY} L${x1},${y1} A${R},${R} 0 ${pct>.5?1:0} 1 ${x2},${y2} Z`, color:COLORS[idx%COLORS.length], pct };
  });
  return (
    <div style={{ padding:"1rem 1.25rem" }}>
      {title && <p style={{ fontSize:".75rem",fontWeight:700,color:"var(--muted)",marginBottom:".75rem",textTransform:"uppercase",letterSpacing:".06em" }}>{title}</p>}
      <div style={{ display:"flex",alignItems:"center",gap:"1.25rem",flexWrap:"wrap" }}>
        <svg viewBox="0 0 130 130" width="120" height="120">
          {slices.map((s,i)=><path key={i} d={s.path} fill={s.color} opacity=".88"/>)}
          <circle cx={CX} cy={CY} r="30" fill="var(--surface)"/>
        </svg>
        <div style={{ display:"flex",flexDirection:"column",gap:".4rem" }}>
          {slices.map((s,i)=>(
            <div key={i} style={{ display:"flex",alignItems:"center",gap:".45rem",fontSize:".76rem" }}>
              <span style={{ width:9,height:9,borderRadius:"50%",background:s.color,flexShrink:0 }}/>
              <span style={{ color:"var(--text)" }}>{s.label}</span>
              <span style={{ color:"var(--muted)",fontWeight:700,marginLeft:"auto",paddingLeft:".5rem" }}>{Math.round(s.pct*100)}%</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ── Barra horizontal ─────────────────────────────────────────── */
function HBar({ items, valKey="unidadesVendidas", suffix="uds" }) {
  if (!items?.length||items.every(i=>!i[valKey])) return <div className="rp-chart-empty-box">Sin ventas en este período</div>;
  const max=Math.max(...items.map(i=>i[valKey]||0),1);
  return (
    <div style={{ padding:".85rem 1.25rem",display:"flex",flexDirection:"column",gap:".5rem" }}>
      {items.map((p,i)=>(
        <div key={p.id||i} style={{ display:"flex",alignItems:"center",gap:".65rem" }}>
          <span style={{ fontSize:".73rem",color:"var(--text)",width:90,textAlign:"right",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap",flexShrink:0 }} title={p.nombre}>{p.nombre}</span>
          <div style={{ flex:1,height:20,background:"var(--surface2)",borderRadius:5,overflow:"hidden" }}>
            <div style={{ height:"100%",borderRadius:5,background:"linear-gradient(90deg,var(--primary),var(--accent))",width:`${Math.round(((p[valKey]||0)/max)*100)}%`,display:"flex",alignItems:"center",paddingLeft:".35rem",transition:"width .55s cubic-bezier(.4,0,.2,1)" }}>
              {(p[valKey]||0)>0 && <span style={{ fontSize:".65rem",fontWeight:700,color:"rgba(255,255,255,.9)",whiteSpace:"nowrap" }}>{p[valKey]} {suffix}</span>}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ── Gráfica días ─────────────────────────────────────────────── */
function DiasChart({ data }) {
  if (!data||Object.values(data).every(v=>v===0)) return <div className="rp-chart-empty-box">Sin ventas registradas en este período</div>;
  const max=Math.max(...Object.values(data),1);
  return (
    <div style={{ display:"flex",alignItems:"flex-end",gap:".35rem",height:100,padding:"1rem 1.25rem .9rem" }}>
      {Object.entries(data).map(([dia,val])=>(
        <div key={dia} style={{ display:"flex",flexDirection:"column",alignItems:"center",flex:1,gap:".3rem" }}>
          <div style={{ width:"100%",flex:1,display:"flex",alignItems:"flex-end" }}>
            <div style={{ width:"100%",borderRadius:"4px 4px 0 0",background:val>0?"linear-gradient(180deg,var(--primary),rgba(255,61,0,.35))":"var(--surface3)",height:`${Math.round((val/max)*100)}%`,minHeight:3 }} title={`$${(val||0).toLocaleString("es-CO",{minimumFractionDigits:0})}`}/>
          </div>
          <span style={{ fontSize:".62rem",color:"var(--muted)" }}>{dia}</span>
        </div>
      ))}
    </div>
  );
}

function ReportesProductos({ token }) {
  const [periodo,  setPeriodo]  = useState("mes");
  const [data,     setData]     = useState(null);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState(null);
  const [tabMas,   setTabMas]   = useState("unidades");
  const [tabGraf,  setTabGraf]  = useState("barras"); // "barras" | "pastel"

  useEffect(()=>{
    setLoading(true); setError(null);
    getReportesProductos(token, periodo)
      .then(setData).catch(e=>setError(e.message)).finally(()=>setLoading(false));
  }, [periodo]);

  if (loading) return <div className="rp-loading"><div className="rp-spinner"/>Cargando...</div>;
  if (error)   return <p className="rp-error">{error}</p>;
  if (!data)   return null;

  const fmt = n => (n||0).toLocaleString("es-CO",{minimumFractionDigits:2});

  // Rotación de inventario = unidades vendidas / stock actual promedio
  const masVendidos = data.masVendidos||[];
  const rotacion = masVendidos.map(p=>({
    nombre: p.nombre,
    rotacion: p.stockActual>0 ? (p.unidadesVendidas/p.stockActual).toFixed(2) : "N/A",
    nivel: !p.stockActual ? "—"
         : p.unidadesVendidas/p.stockActual > 2 ? "Alta"
         : p.unidadesVendidas/p.stockActual > 0.5 ? "Media"
         : "Baja",
  })).slice(0,8);

  // Datos pie chart por categoría
  const pieCategoria = Object.entries(data.unidadesPorCategoria||{})
    .map(([label,val])=>({ label, val }))
    .sort((a,b)=>b.val-a.val);

  return (
    <>
      {/* Período */}
      <div style={{ display:"flex",alignItems:"center",gap:".75rem",marginBottom:"1rem" }}>
        <span style={{ fontSize:".78rem",color:"var(--muted)",fontWeight:600 }}>Período:</span>
        <div className="rp-period-tabs">
          {PERIODOS.map(p=>(
            <button key={p.k} className={`rp-period-tab${periodo===p.k?" active":""}`} onClick={()=>setPeriodo(p.k)}>{p.l}</button>
          ))}
        </div>
      </div>

      {/* KPIs */}
      <div className="rp-kpi-grid" style={{ marginBottom:"1rem" }}>
        <div className="rp-kpi">
          <div className="rp-kpi-top"><span className="rp-kpi-ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="15" height="15"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg></span></div>
          <p className="rp-kpi-label">Producto #1</p>
          <p className="rp-kpi-val" style={{fontSize:".95rem"}}>{data.masVendidos?.[0]?.nombre||"—"}</p>
        </div>
        <div className="rp-kpi">
          <div className="rp-kpi-top"></div>
          <p className="rp-kpi-label">Unidades vendidas</p>
          <p className="rp-kpi-val orange">{data.masVendidos?.[0]?.unidadesVendidas||0}</p>
        </div>
        <div className="rp-kpi">
          <div className="rp-kpi-top"></div>
          <p className="rp-kpi-label">Mayor ingreso</p>
          <p className="rp-kpi-val green">${fmt(data.masVendidos?.[0]?.totalGenerado)}</p>
        </div>
        <div className="rp-kpi">
          <div className="rp-kpi-top"></div>
          <p className="rp-kpi-label">Sin movimiento</p>
          <p className="rp-kpi-val red">{data.sinMovimiento?.length||0}</p>
        </div>
      </div>

      {/* Más vendidos + Ventas por día */}
      <div className="rp-two-col">
        <div className="rp-panel">
          <div className="rp-panel-header">
            <span className="rp-panel-title">Más vendidos</span>
            <div style={{ display:"flex",gap:".3rem" }}>
              <div className="rp-period-tabs">
                <button className={`rp-period-tab${tabMas==="unidades"?" active":""}`} onClick={()=>setTabMas("unidades")}>Unidades</button>
                <button className={`rp-period-tab${tabMas==="ingresos"?" active":""}`} onClick={()=>setTabMas("ingresos")}>Ingresos</button>
              </div>
            </div>
          </div>
          {tabMas==="unidades"
            ? <HBar items={masVendidos} valKey="unidadesVendidas" suffix="uds"/>
            : <HBar items={masVendidos} valKey="totalGenerado"    suffix="COP"/>
          }
        </div>

        <div className="rp-panel">
          <div className="rp-panel-header">
            <span className="rp-panel-title">Ventas últimos 7 días</span>
          </div>
          <DiasChart data={data.ventasPorDia}/>
        </div>
      </div>

      {/* Gráficas de análisis: toggle barras/pastel */}
      <div className="rp-panel" style={{ marginBottom:"1rem" }}>
        <div className="rp-panel-header">
          <span className="rp-panel-title">Análisis por categoría</span>
          <div className="rp-period-tabs">
            <button className={`rp-period-tab${tabGraf==="barras"?" active":""}`} onClick={()=>setTabGraf("barras")}>Barras</button>
            <button className={`rp-period-tab${tabGraf==="pastel"?" active":""}`} onClick={()=>setTabGraf("pastel")}>Pastel</button>
          </div>
        </div>
        {tabGraf==="barras"
          ? <HBar items={pieCategoria.map(c=>({nombre:c.label,unidadesVendidas:c.val}))} valKey="unidadesVendidas" suffix="uds"/>
          : <PieChart items={pieCategoria}/>
        }
      </div>

      {/* Rotación de inventario */}
      <div className="rp-panel" style={{ marginBottom:"1rem" }}>
        <div className="rp-panel-header">
          <span className="rp-panel-title">Rotación de inventario</span>
          <span className="rp-badge nuevo" style={{ fontSize:".68rem" }}>Ventas ÷ Stock</span>
        </div>
        <div className="rp-table-wrap">
          <table className="rp-table">
            <thead><tr><th>Producto</th><th style={{textAlign:"right"}}>Índice</th><th>Nivel</th></tr></thead>
            <tbody>
              {rotacion.length===0
                ? <tr><td colSpan={3} className="rp-empty">Sin datos</td></tr>
                : rotacion.map((r,i)=>(
                    <tr key={i}>
                      <td style={{fontWeight:600,fontSize:".83rem"}}>{r.nombre}</td>
                      <td style={{textAlign:"right",fontWeight:800,color:"var(--blue)"}}>{r.rotacion}</td>
                      <td>
                        <span className={`rp-badge ${r.nivel==="Alta"?"frecuente":r.nivel==="Media"?"ocasional":r.nivel==="Baja"?"agotado":"nuevo"}`}>
                          {r.nivel}
                        </span>
                      </td>
                    </tr>
                  ))
              }
            </tbody>
          </table>
        </div>
      </div>

      {/* Sin movimiento */}
      {data.sinMovimiento?.length>0 && (
        <div className="rp-panel">
          <div className="rp-panel-header">
            <span className="rp-panel-title">Productos sin movimiento en el período</span>
            <span className="rp-badge agotado">{data.sinMovimiento.length}</span>
          </div>
          <div className="rp-table-wrap">
            <table className="rp-table">
              <thead><tr><th>Producto</th><th>Categoría</th><th style={{textAlign:"right"}}>Precio</th><th style={{textAlign:"right"}}>Stock</th><th>Estado</th></tr></thead>
              <tbody>
                {data.sinMovimiento.map(p=>(
                  <tr key={p.id}>
                    <td style={{fontWeight:600,fontSize:".83rem"}}>{p.nombre}</td>
                    <td><span className="rp-badge nuevo" style={{maxWidth:120,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap",display:"inline-block"}} title={p.categoria}>{p.categoria}</span></td>
                    <td style={{textAlign:"right"}}>${fmt(p.precioVenta)}</td>
                    <td style={{textAlign:"right",color:"var(--muted)"}}>{p.stockActual}</td>
                    <td><span className={`rp-badge ${(p.estadoStock||"NORMAL").toLowerCase()}`}>{p.estadoStock}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}

export default ReportesProductos;