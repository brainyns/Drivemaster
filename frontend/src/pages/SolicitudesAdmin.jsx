import { useState, useEffect } from "react";
import { listarSolicitudes, aprobarSolicitud, rechazarSolicitud, obtenerSolicitud, crearPago, getWompiRedirectUrl } from "../services/solicitudService";
import "../css/solicitudes.css";

const ESTADOS = {
  PENDIENTE: "Pendiente",
  APROBADO: "Aprobado",
  RECHAZADO: "Rechazado",
  COMPLETADA: "Completada",
  PAGADO: "Pagado",
};

const ESTADOS_CLASS = {
  PENDIENTE: "sq-pending",
  APROBADO: "sq-approved",
  RECHAZADO: "sq-rejected",
  COMPLETADA: "sq-verified",
  PAGADO: "sq-approved",
};

const formatPrecio = (p) =>
  new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", minimumFractionDigits: 0 }).format(p || 0);

function getInitials(nombre) {
  return (nombre || "").split(" ").map(p => p[0]).join("").slice(0, 2).toUpperCase();
}

function esEncargo(s) {
  return Array.isArray(s.productos) && s.productos.some(p => p.tipo === "ENCARGO");
}

function SkeletonRow() {
  return (
    <div className="sq-row sq-skeleton">
      <div className="sq-skel sq-skel-avatar" />
      <div className="sq-skel sq-skel-text" />
      <div className="sq-skel sq-skel-text short" />
      <div className="sq-skel sq-skel-badge" />
      <div className="sq-skel sq-skel-text short" />
      <div className="sq-skel sq-skel-btn" />
    </div>
  );
}

export default function SolicitudesAdmin({ token }) {
  const [solicitudes, setSolicitudes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [busqueda, setBusqueda] = useState("");
  const [filtro, setFiltro] = useState("TODAS");
  const [detalleId, setDetalleId] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [rechazarId, setRechazarId] = useState(null);
  const [motivoRechazo, setMotivoRechazo] = useState("");
  const [accionId, setAccionId] = useState(null);
  const [accionTipo, setAccionTipo] = useState(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [linkPago, setLinkPago] = useState(null);
  const [pagina, setPagina] = useState(1);
  const porPagina = 15;

  const cargar = async () => {
    setCargando(true);
    try {
      const data = await listarSolicitudes(token);
      setSolicitudes(data);
    } catch (e) {
      console.error(e);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const handleAprobar = async () => {
    if (!accionId) return;
    try { await aprobarSolicitud(token, accionId); setConfirmOpen(false); setAccionId(null); cargar(); } catch (e) { console.error(e); alert("Error al aprobar: " + e.message); }
  };

  const handleRechazar = async () => {
    if (!rechazarId || !motivoRechazo.trim()) return;
    try { await rechazarSolicitud(token, rechazarId, motivoRechazo); setRechazarId(null); setMotivoRechazo(""); cargar(); } catch (e) { console.error(e); }
  };

  const generarLink = async (solicitudId) => {
    try {
      const redirectUrl = getWompiRedirectUrl();
      const data = await crearPago(token, solicitudId, redirectUrl);
      setLinkPago({ solicitudId, url: data.checkoutUrl, copiado: false });
    } catch (e) {
      console.error(e);
      alert("Error al generar link: " + e.message);
    }
  };

  const copiarLink = async () => {
    if (!linkPago) return;
    try {
      await navigator.clipboard.writeText(linkPago.url);
      setLinkPago({ ...linkPago, copiado: true });
    } catch (e) {
      console.error(e);
      alert("No se pudo copiar: " + e.message);
    }
  };

  const abrirDetalle = async (id) => {
    setDetalleId(id);
    try {
      const data = await obtenerSolicitud(token, id);
      setDetalle(data);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => { setPagina(1); }, [filtro, busqueda]);

  const filtradas = solicitudes
    .filter(s => filtro === "TODAS" || s.estado === filtro)
    .filter(s => {
      if (!busqueda) return true;
      const q = busqueda.toLowerCase();
      return (s.clienteNombre?.toLowerCase().includes(q) ||
              s.clienteCorreo?.toLowerCase().includes(q) ||
              s.id?.toLowerCase().includes(q));
    });

  const totalPaginas = Math.ceil(filtradas.length / porPagina);
  const paginadas = filtradas.slice((pagina - 1) * porPagina, pagina * porPagina);

  const conteo = {
    PENDIENTE: solicitudes.filter(s => s.estado === "PENDIENTE").length,
    APROBADO: solicitudes.filter(s => s.estado === "APROBADO").length,
    PAGADO: solicitudes.filter(s => s.estado === "PAGADO").length,
    RECHAZADO: solicitudes.filter(s => s.estado === "RECHAZADO").length,
  };

  return (
    <div className="sq-root">
      <div className="sq-header">
        <div className="sq-header-left">
          <h1 className="sq-title">Solicitudes</h1>
          <p className="sq-subtitle">Gestione las solicitudes de compra pendientes y aprobadas.</p>
        </div>
      </div>

      <div className="sq-counters">
        <div className="sq-counter sq-counter--pending">
          <span className="sq-counter-num">{conteo.PENDIENTE}</span>
          <span className="sq-counter-label">Pendientes</span>
        </div>
        <div className="sq-counter sq-counter--approved">
          <span className="sq-counter-num">{conteo.APROBADO}</span>
          <span className="sq-counter-label">Aprobadas</span>
        </div>
        <div className="sq-counter sq-counter--paid">
          <span className="sq-counter-num">{conteo.PAGADO}</span>
          <span className="sq-counter-label">Pagadas</span>
        </div>
        <div className="sq-counter sq-counter--rejected">
          <span className="sq-counter-num">{conteo.RECHAZADO}</span>
          <span className="sq-counter-label">Rechazadas</span>
        </div>
      </div>

      <div className="sq-toolbar">
        <div className="sq-search">
          <svg className="sq-search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            className="sq-search-input"
            placeholder="Buscar cliente, correo o ID..."
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
          />
        </div>
        <select className="sq-filter" value={filtro} onChange={e => setFiltro(e.target.value)}>
          <option value="TODAS">Todas</option>
          <option value="PENDIENTE">Pendientes</option>
          <option value="APROBADO">Aprobadas</option>
          <option value="PAGADO">Pagadas</option>
          <option value="RECHAZADO">Rechazadas</option>
        </select>
      </div>

      <div className="sq-table-wrap">
        <div className="sq-table">
          <div className="sq-thead">
            <span>Cliente</span>
            <span>Contacto</span>
            <span>Productos</span>
            <span>Total</span>
            <span>Estado</span>
            <span>Fecha</span>
            <span>Acciones</span>
          </div>

          {cargando ? (
            <>
              <SkeletonRow /><SkeletonRow /><SkeletonRow /><SkeletonRow /><SkeletonRow />
            </>
          ) : filtradas.length === 0 ? (
            <div className="sq-empty">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
              <p>No hay solicitudes</p>
            </div>
          ) : (
            paginadas.map(s => (
              <div key={s.id} className={`sq-row ${detalleId === s.id ? "sq-row--active" : ""}`}>
                <div className="sq-cell-client">
                  <div className="sq-avatar" style={{ background: s.estado === "APROBADO" ? "#10b981" : s.estado === "RECHAZADO" ? "#ef4444" : "#f59e0b" }}>
                    {getInitials(s.clienteNombre)}
                  </div>
                  <div className="sq-client-info">
                    <span className="sq-client-name">{s.clienteNombre || "—"}</span>
                    <span className="sq-client-sub">{s.id?.slice(-8).toUpperCase()}</span>
                  </div>
                </div>

                <div className="sq-cell-contact">
                  <span className="sq-contact-email">{s.clienteCorreo || "—"}</span>
                  {s.clienteTelefono && <span className="sq-contact-tel">{s.clienteTelefono}</span>}
                  {s.clienteCiudad && <span className="sq-contact-city">{s.clienteCiudad}</span>}
                </div>

                <div className="sq-cell-prod">
                  <span className="sq-prod-count">{s.productos?.length || 0} producto{(s.productos?.length || 0) !== 1 ? "s" : ""}</span>
                  <button className="sq-prod-detail-btn" onClick={() => abrirDetalle(s.id)}>Ver detalle</button>
                </div>

                <div className="sq-cell-total">{formatPrecio(s.total)}</div>

                <div className="sq-cell-estado">
                  <span className={`sq-badge ${ESTADOS_CLASS[s.estado] || ""}`}>
                    <span className="sq-badge-dot" />
                    {ESTADOS[s.estado] || s.estado}
                    {s.estado === "APROBADO" && s.ventaId && <span className="sq-badge-venta">Venta generada</span>}
                  </span>
                </div>

                <div className="sq-cell-fecha">
                  {s.fechaCreacion ? new Date(s.fechaCreacion).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—"}
                </div>

                <div className="sq-cell-acciones">
                  {s.estado === "PENDIENTE" && (
                    <>
                      {(esEncargo(s) || s.metodoPago !== "WOMPI") && (
                        <button className="sq-btn sq-btn-approve" title="Aprobar" onClick={() => { setAccionId(s.id); setAccionTipo("aprobar"); setConfirmOpen(true); }}>
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="20 6 9 17 4 12"/></svg>
                        </button>
                      )}
                      <button className="sq-btn sq-btn-reject" title="Rechazar" onClick={() => setRechazarId(s.id)}>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                      </button>
                    </>
                  )}
                  {s.estado === "APROBADO" && s.metodoPago === "WOMPI" && !s.ventaId && esEncargo(s) && (
                    <button className="sq-btn sq-btn-link" title="Generar link de pago" onClick={() => generarLink(s.id)}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71"/></svg>
                    </button>
                  )}
                  <button className="sq-btn sq-btn-view" title="Ver detalles" onClick={() => abrirDetalle(s.id)}>
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  </button>
                </div>

                {rechazarId === s.id && (
                  <div className="sq-reject-form">
                    <input
                      className="sq-reject-input"
                      value={motivoRechazo}
                      onChange={e => setMotivoRechazo(e.target.value)}
                      placeholder="Motivo del rechazo..."
                      autoFocus
                    />
                    <button className="sq-btn sq-btn-reject-confirm" onClick={handleRechazar} disabled={!motivoRechazo.trim()}>
                      Confirmar
                    </button>
                    <button className="sq-btn sq-btn-cancel" onClick={() => { setRechazarId(null); setMotivoRechazo(""); }}>
                      Cancelar
                    </button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>

      {totalPaginas > 1 && (
        <div className="sq-paginacion">
          <button className="sq-page-btn" disabled={pagina <= 1} onClick={() => setPagina(p => p - 1)}>← Anterior</button>
          <span className="sq-page-info">Página {pagina} de {totalPaginas} ({filtradas.length} resultados)</span>
          <button className="sq-page-btn" disabled={pagina >= totalPaginas} onClick={() => setPagina(p => p + 1)}>Siguiente →</button>
        </div>
      )}

      {detalleId && detalle && (
        <div className="sq-modal-overlay" onClick={() => { setDetalleId(null); setDetalle(null); }}>
          <div className="sq-modal" onClick={e => e.stopPropagation()}>
            <button className="sq-modal-close" onClick={() => { setDetalleId(null); setDetalle(null); }}>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>

            <div className="sq-modal-header">
              <div className="sq-modal-avatar" style={{ background: detalle.estado === "APROBADO" ? "#10b981" : detalle.estado === "RECHAZADO" ? "#ef4444" : "#f59e0b" }}>
                {getInitials(detalle.clienteNombre)}
              </div>
              <div>
                <h2 className="sq-modal-title">{detalle.clienteNombre || "Cliente"}</h2>
                <span className={`sq-badge sq-badge--lg ${ESTADOS_CLASS[detalle.estado] || ""}`}>
                  <span className="sq-badge-dot" />
                  {ESTADOS[detalle.estado] || detalle.estado}
                </span>
              </div>
            </div>

            <div className="sq-modal-body">
              <div className="sq-modal-section">
                <h3>Información del cliente</h3>
                <div className="sq-modal-grid">
                  <div><span>Correo</span><p>{detalle.clienteCorreo || "—"}</p></div>
                  <div><span>Teléfono</span><p>{detalle.clienteTelefono || "—"}</p></div>
                  <div><span>Identificación</span><p>{detalle.clienteIdentificacion || "—"}</p></div>
                  <div><span>Ciudad</span><p>{detalle.clienteCiudad || "—"}</p></div>
                </div>
              </div>

              <div className="sq-modal-section">
                <h3>Productos ({detalle.productos?.length || 0})</h3>
                <div className="sq-modal-prods">
                  {detalle.productos?.map((p, i) => (
                    <div key={i} className="sq-modal-prod">
                      <div className="sq-modal-prod-info">
                        <span className="sq-modal-prod-name">{p.nombre}</span>
                        <span className="sq-modal-prod-meta">{p.cantidad} x {formatPrecio(p.precioUnitario)}</span>
                      </div>
                      <span className="sq-modal-prod-sub">{formatPrecio(p.subtotal)}</span>
                    </div>
                  ))}
                </div>
              </div>

              {detalle.observaciones && (
                <div className="sq-modal-section">
                  <h3>Motivo de rechazo</h3>
                  <div className="sq-modal-reason">{detalle.observaciones}</div>
                </div>
              )}

              <div className="sq-modal-total">
                <span>Total</span>
                <strong>{formatPrecio(detalle.total)}</strong>
              </div>

              {detalle.ventaId && (
                <div className="sq-modal-venta">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="20 6 9 17 4 12"/></svg>
                  Venta generada: <strong>{detalle.ventaId.slice(-8).toUpperCase()}</strong>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {confirmOpen && (
        <div className="sq-modal-overlay" onClick={() => setConfirmOpen(false)}>
          <div className="sq-modal sq-modal--small" onClick={e => e.stopPropagation()}>
            <h2 className="sq-modal-title">Confirmar acción</h2>
            <p className="sq-modal-desc">
              ¿Estás seguro de {accionTipo === "aprobar" ? "aprobar" : "rechazar"} esta solicitud?{" "}
              {accionTipo === "aprobar"
                ? solicitudes.find(s => s.id === accionId)?.metodoPago === "WOMPI"
                  ? "Se generará el link de pago para el cliente."
                  : "Se generará una venta automáticamente."
                : ""}
            </p>
            <div className="sq-modal-actions">
              <button className="sq-btn sq-btn-approve sq-btn--md" onClick={handleAprobar}>
                {accionTipo === "aprobar" ? "Sí, aprobar" : "Sí, rechazar"}
              </button>
              <button className="sq-btn sq-btn-cancel sq-btn--md" onClick={() => setConfirmOpen(false)}>
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      {linkPago && (
        <div className="sq-modal-overlay" onClick={() => setLinkPago(null)}>
          <div className="sq-modal sq-modal--small" onClick={e => e.stopPropagation()}>
            <h2 className="sq-modal-title">Link de pago generado</h2>
            <p className="sq-modal-desc">Envía este enlace al cliente para que complete el pago:</p>
            <div className="sq-link-box">
              <span className="sq-link-url">{linkPago.url}</span>
            </div>
            <div className="sq-modal-actions">
              <button className="sq-btn sq-btn-approve sq-btn--md" onClick={copiarLink}>
                {linkPago.copiado ? "Copiado ✓" : "Copiar enlace"}
              </button>
              <button className="sq-btn sq-btn-cancel sq-btn--md" onClick={() => setLinkPago(null)}>
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
