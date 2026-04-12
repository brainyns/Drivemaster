import { useEffect, useState } from "react";
import { listarClientes, eliminarCliente } from "../services/clienteService";
import "../css/clientes.css";

const AVATARES = ["#2563eb","#7c3aed","#db2777","#059669","#d97706","#dc2626","#0891b2"];
const getIniciales = (n) => n ? n.split(" ").map(x=>x[0]).join("").toUpperCase().slice(0,2) : "?";
const getColor = (n) => n ? AVATARES[n.charCodeAt(0) % AVATARES.length] : AVATARES[0];

const POR_PAG = 10;

function Clientes({ onNuevo, onEditar }) {
  const [clientes, setClientes] = useState([]);
  const [loading, setLoading]   = useState(true);
  const [busqueda, setBusqueda] = useState("");
  const [pagina, setPagina]     = useState(1);

  useEffect(() => { cargar(); }, []);

  const cargar = async () => {
    try {
      const data = await listarClientes();
      setClientes(data);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  const handleEliminar = async (id) => {
    if (!confirm("¿Eliminar este cliente?")) return;
    try {
      await eliminarCliente(id);
      setClientes(p => p.filter(c => c.id !== id));
    } catch(e) { alert(e.message); }
  };

  const filtrados = clientes.filter(c =>
    [c.nombre, c.identificacion, c.correo, c.telefono, c.direccion]
      .some(v => v?.toLowerCase().includes(busqueda.toLowerCase()))
  );

  const totalPags = Math.ceil(filtrados.length / POR_PAG) || 1;
  const paginados = filtrados.slice((pagina-1)*POR_PAG, pagina*POR_PAG);
  const conCorreo   = clientes.filter(c => c.correo).length;
  const conTelefono = clientes.filter(c => c.telefono).length;

  return (
    <div className="km-root km-page">

      {/* Topbar */}
      <header className="km-top">
        <div className="km-top-left">
          <span className="km-brand">DriveMaster</span>
          <span className="km-divider">|</span>
          <span className="km-breadcrumb">Gestión de Clientes</span>
        </div>
        <div className="km-top-tabs">
          <button className="km-tab active">Ver Clientes</button>
          <button className="km-tab" onClick={onNuevo}>
            Agregar Cliente <span className="km-tab-dot" />
          </button>
        </div>
        <div className="km-top-right">
          <div className="km-search">
            <span className="km-search-ico">🔍</span>
            <input
              placeholder="Buscar cliente..."
              value={busqueda}
              onChange={e => { setBusqueda(e.target.value); setPagina(1); }}
            />
          </div>
          <button className="km-ico-btn">🔔</button>
          <button className="km-ico-btn">⚙</button>
          <div className="km-avatar">A</div>
        </div>
      </header>

      {/* Page header */}
      <div className="km-page-header">
        <div>
          <h1 className="km-page-h1">Directorio de Clientes</h1>
          <p className="km-page-sub">Administra la base de datos de tus clientes y sus datos de contacto.</p>
        </div>
        <div className="km-btn-group">
          <button className="km-btn-toggle active">Ver Clientes</button>
          <button className="km-btn-toggle" onClick={onNuevo}>Agregar Cliente</button>
        </div>
      </div>

      {/* Stats */}
      <div className="km-stats">
        <div className="km-stat">
          <p className="km-stat-label">Clientes Totales</p>
          <p className="km-stat-val">{clientes.length.toLocaleString()}</p>
        </div>
        <div className="km-stat">
          <p className="km-stat-label">Registrados Hoy</p>
          <p className="km-stat-val purple">—</p>
        </div>
        <div className="km-stat">
          <p className="km-stat-label">Con Correo</p>
          <p className="km-stat-val blue">{conCorreo}</p>
        </div>
        <div className="km-stat">
          <p className="km-stat-label">Con Teléfono</p>
          <p className="km-stat-val orange">+{conTelefono}</p>
        </div>
      </div>

      {/* Table */}
      <div className="km-card">
        <div className="km-toolbar">
          <div className="km-toolbar-l">
            <button className="km-tool-btn">⚙ Filtro</button>
            <button className="km-tool-btn">↓ Exportador</button>
          </div>
          <span className="km-count">
            Mostrando {filtrados.length === 0 ? 0 : (pagina-1)*POR_PAG+1}–{Math.min(pagina*POR_PAG, filtrados.length)} de {filtrados.length} clientes
          </span>
        </div>

        {loading ? (
          <p className="km-empty">Cargando clientes...</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="km-table">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Identificación</th>
                  <th>Teléfono</th>
                  <th>Correo</th>
                  <th>Dirección</th>
                  <th style={{ textAlign: "right" }}>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {paginados.length === 0 ? (
                  <tr><td colSpan={6} className="km-empty">No se encontraron clientes</td></tr>
                ) : paginados.map(c => (
                  <tr key={c.id}>
                    <td>
                      <div className="km-cell-name">
                        <div className="km-init" style={{ background: getColor(c.nombre) }}>
                          {getIniciales(c.nombre)}
                        </div>
                        <div>
                          <p className="km-name">{c.nombre}</p>
                          <p className="km-sub">{c.correo || "—"}</p>
                        </div>
                      </div>
                    </td>
                    <td><span className="km-badge">{c.identificacion || "—"}</span></td>
                    <td className="km-muted">{c.telefono || "—"}</td>
                    <td className="km-muted">{c.correo || "—"}</td>
                    <td className="km-muted">{c.direccion || "—"}</td>
                    <td>
                      <div className="km-actions">
                        <button onClick={() => onEditar(c.id)} className="km-action-btn edit" title="Editar">✏</button>
                        <button onClick={() => handleEliminar(c.id)} className="km-action-btn del" title="Eliminar">🗑</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPags > 1 && (
          <div className="km-pag">
            <button className="km-pag-btn" onClick={() => setPagina(p => Math.max(1,p-1))} disabled={pagina===1}>‹ Anterior</button>
            <div className="km-pag-nums">
              {Array.from({ length: Math.min(totalPags,5) }, (_,i) => i+1).map(n => (
                <button key={n} className={`km-pag-num${pagina===n?" on":""}`} onClick={() => setPagina(n)}>{n}</button>
              ))}
              {totalPags > 5 && <span className="km-muted" style={{ padding:"0 0.2rem" }}>...</span>}
              {totalPags > 5 && <button className="km-pag-num" onClick={() => setPagina(totalPags)}>{totalPags}</button>}
            </div>
            <button className="km-pag-btn" onClick={() => setPagina(p => Math.min(totalPags,p+1))} disabled={pagina===totalPags}>Siguiente ›</button>
          </div>
        )}
      </div>

      {/* Bottom panels */}
      <div className="km-bottom-grid">
        <div className="km-panel">
          <div className="km-panel-header">
            <span className="km-panel-ico">📈</span>
            <h4 className="km-panel-title">Actividad Reciente</h4>
          </div>
          {[
            { ico: "👤", title: "Nuevo Cliente Registrado", desc: "Se agregó un nuevo cliente a la base de datos", time: "HACE 15M" },
            { ico: "✏", title: "Cliente Actualizado",       desc: "Se modificó la información de un cliente",   time: "HACE 1H"  },
            { ico: "🗑", title: "Cliente Eliminado",         desc: "Se eliminó un registro de la base de datos", time: "HACE 3H"  },
          ].map((a,i) => (
            <div key={i} className="km-activity-item">
              <div className="km-activity-ico">{a.ico}</div>
              <div className="km-activity-text">
                <p className="km-activity-title">{a.title}</p>
                <p className="km-activity-desc">{a.desc}</p>
              </div>
              <span className="km-activity-time">{a.time}</span>
            </div>
          ))}
        </div>

        <div className="km-panel">
          <div className="km-panel-header" style={{ justifyContent: "space-between" }}>
            <div style={{ display:"flex", alignItems:"center", gap:"0.5rem" }}>
              <span className="km-panel-ico">📊</span>
              <h4 className="km-panel-title">Resumen de Base de Datos</h4>
            </div>
            <div className="km-bay-live">
              <span className="km-live-dot" />
              <span className="km-live-label">Live</span>
            </div>
          </div>
          <p style={{ fontSize:"0.78rem", color:"var(--muted)", marginBottom:"0.75rem", lineHeight:1.55 }}>
            Estadísticas generales del directorio de clientes registrados en el sistema.
          </p>
          <div className="km-bay-grid">
            <div className="km-bay-card">
              <p className="km-bay-num">{conCorreo}</p>
              <p className="km-bay-label">Con Correo</p>
            </div>
            <div className="km-bay-card free">
              <p className="km-bay-num">{conTelefono}</p>
              <p className="km-bay-label">Con Teléfono</p>
            </div>
          </div>
          <div className="km-capacity">
            <div className="km-cap-label">
              <span>Completitud de datos</span>
              <span>{clientes.length ? Math.round(((conCorreo + conTelefono) / (clientes.length * 2)) * 100) : 0}%</span>
            </div>
            <div className="km-cap-bar">
              <div
                className="km-cap-fill"
                style={{ width: clientes.length ? `${Math.round(((conCorreo + conTelefono) / (clientes.length * 2)) * 100)}%` : "0%" }}
              />
            </div>
          </div>
        </div>
      </div>

      <button className="km-fab" onClick={onNuevo} title="Agregar Cliente">+</button>
    </div>
  );
}

export default Clientes;