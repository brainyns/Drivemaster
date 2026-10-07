import { useCallback, useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { listarRepartos, liquidarReparto, listarReembolsos, marcarReembolsado } from "../services/liquidacionService";
import { formatFechaHora, formatPrecio, refSolicitud } from "../components/servicio/estadosServicio";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/cotizacion.css";

const VISTAS = [
  { key: "repartos", label: "Liquidaciones a talleres y tiendas" },
  { key: "reembolsos", label: "Reembolsos a clientes" },
];

const ESTADOS = {
  repartos: [["PENDIENTE", "Pendientes"], ["LIQUIDADO", "Liquidados"], ["ANULADO", "Anulados"], ["", "Todos"]],
  reembolsos: [["PENDIENTE", "Pendientes"], ["REALIZADO", "Realizados"], ["", "Todos"]],
};

/**
 * Lista mínima del ADMIN. Las transferencias y los reembolsos se hacen fuera
 * (banco / panel de Wompi); aquí solo se marcan como hechos, con una nota.
 */
export default function Liquidaciones() {
  const token = getToken();
  const [vista, setVista] = useState("repartos");
  const [estado, setEstado] = useState("PENDIENTE");
  const [filas, setFilas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [marcandoId, setMarcandoId] = useState(null);
  const [nota, setNota] = useState("");
  const [procesando, setProcesando] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    try {
      const datos = vista === "repartos" ? await listarRepartos(token, estado) : await listarReembolsos(token, estado);
      setFilas(datos || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, vista, estado]);

  useEffect(() => { cargar(); }, [cargar]);

  const cambiarVista = (v) => {
    setVista(v);
    setEstado("PENDIENTE");
    setMarcandoId(null);
  };

  const marcar = async (id) => {
    setProcesando(true);
    setError(null);
    try {
      if (vista === "repartos") await liquidarReparto(token, id, nota.trim() || null);
      else await marcarReembolsado(token, id, nota.trim() || null);
      setMarcandoId(null);
      setNota("");
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const pendientes = filas.filter((f) => f.estado === "PENDIENTE");
  const totalPendiente = pendientes.reduce((s, f) => s + (f.monto || 0), 0);

  return (
    <div className="ss-panel">
      <nav className="ss-filtros" aria-label="Tipo">
        {VISTAS.map((v) => (
          <button key={v.key} className={`ss-filtro${vista === v.key ? " ss-filtro--activo" : ""}`}
            aria-current={vista === v.key ? "page" : undefined} onClick={() => cambiarVista(v.key)}>
            {v.label}
          </button>
        ))}
      </nav>

      <div className="ct-resumen">
        <div className="ct-resumen__dato">
          <span className="ss-meta">Pendiente en esta lista</span>
          <span className="ct-resumen__valor">{formatPrecio(totalPendiente)}</span>
        </div>
        <div className="sv-field">
          <label htmlFor="lq-estado">Estado</label>
          <select id="lq-estado" value={estado} onChange={(e) => setEstado(e.target.value)}>
            {ESTADOS[vista].map(([valor, label]) => <option key={valor || "todos"} value={valor}>{label}</option>)}
          </select>
        </div>
      </div>

      {error && <p className="ss-alerta" role="alert">{error}</p>}

      {cargando ? <p className="sv-empty">Cargando...</p> : filas.length === 0 ? (
        <p className="sv-empty">No hay registros en esta lista.</p>
      ) : (
        <div className="ct-tabla-wrap">
          <table className="ct-tabla">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Solicitud</th>
                {vista === "repartos" ? <><th>Beneficiario</th><th>Concepto</th></> : <><th>Pago</th><th>Motivo</th></>}
                <th className="ct-tabla__monto">Monto</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {filas.map((f) => (
                <tr key={f.id}>
                  <td>{formatFechaHora(f.fechaCreacion)}</td>
                  <td>#{refSolicitud(f.ordenServicioId)}</td>
                  {vista === "repartos" ? (
                    <>
                      <td>{f.beneficiarioNombre || f.beneficiarioId}<span className="ss-meta">{f.beneficiarioTipo === "TALLER" ? "Taller" : "Tienda"}</span></td>
                      <td>{f.concepto === "DIAGNOSTICO" ? "Diagnóstico" : "Cotización"}</td>
                    </>
                  ) : (
                    <>
                      <td>{f.referenciaPago}<span className="ss-meta">{f.transactionId}</span></td>
                      <td>{f.motivo}</td>
                    </>
                  )}
                  <td className="ct-tabla__monto">{formatPrecio(f.monto)}</td>
                  <td>
                    {f.estado}
                    {f.nota && <span className="ss-meta">{f.nota}</span>}
                  </td>
                  <td>
                    {f.estado === "PENDIENTE" && (marcandoId === f.id ? (
                      <div className="ct-respuesta">
                        <input className="ss-input" placeholder="Nota (ej. n.º de transferencia)" maxLength={300}
                          aria-label="Nota" value={nota} onChange={(e) => setNota(e.target.value)} />
                        <div className="ss-acciones">
                          <button className="sv-btn" onClick={() => marcar(f.id)} disabled={procesando}>
                            {procesando ? "Guardando..." : "Confirmar"}
                          </button>
                          <button className="sv-btn-outline" onClick={() => setMarcandoId(null)} disabled={procesando}>Cancelar</button>
                        </div>
                      </div>
                    ) : (
                      <button className="sv-btn-outline" onClick={() => { setMarcandoId(f.id); setNota(""); }}>
                        {vista === "repartos" ? "Marcar liquidado" : "Marcar reembolsado"}
                      </button>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
