import { useCallback, useEffect, useState } from "react";
import { negociosPendientes } from "../services/plataformaService";
import { resolverAprobacionTaller } from "../services/tallerService";
import { resolverAprobacionTienda } from "../services/tiendaService";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import { capitalizar, formatoFecha } from "../utils/formato";
import "../css/admin-base.css";

/**
 * Aprobaciones del ADMIN: solo los negocios pendientes y dos decisiones, aprobar o rechazar (con motivo). El plan,
 * la calificación y la actividad de los negocios, y pausarlos o cambiarles el plan, son del SUPERADMIN. Un negocio
 * aprobado aquí arranca con el plan de prueba.
 */
export default function AprobacionesAdmin({ token }) {
  const [estado, setEstado] = useState({ lista: null, error: null });
  // { accion: "aprobar" | "rechazar", negocio }
  const [confirmacion, setConfirmacion] = useState(null);
  const [motivo, setMotivo] = useState("");
  const [procesando, setProcesando] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);

  const cargar = useCallback(() => {
    negociosPendientes(token)
      .then((lista) => setEstado({ lista, error: null }))
      .catch((e) => setEstado((p) => ({ ...p, error: e.message })));
  }, [token]);

  useEffect(() => {
    const t = setTimeout(cargar, 0);
    return () => clearTimeout(t);
  }, [cargar]);

  const clave = (n) => `${n.tipo}-${n.id}`;
  const llamar = (n) => (n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda);

  const ejecutar = async (n, decision, mensaje) => {
    setProcesando(clave(n));
    setErrorAccion(null);
    setAviso(null);
    try {
      await llamar(n)(token, n.id, decision);
      setAviso(mensaje);
      setConfirmacion(null);
      setMotivo("");
      cargar();
    } catch (e) {
      setErrorAccion(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const pedir = (accion, negocio) => {
    setErrorAccion(null);
    setMotivo("");
    setConfirmacion({ accion, negocio });
  };

  const confirmar = () => {
    const { accion, negocio: n } = confirmacion;
    if (accion === "aprobar") ejecutar(n, { aprobado: true }, `${n.nombre} quedó aprobado.`);
    else ejecutar(n, { aprobado: false, motivo: motivo.trim() }, `${n.nombre} fue rechazado.`);
  };

  const { lista, error } = estado;

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Aprobaciones</h1>
          <p className="adm-encabezado__sub">
            Negocios que esperan tu revisión. Aprobarlos los activa con el plan de prueba; al rechazarlos se les envía el motivo.
          </p>
        </div>
        {lista && <span className="adm-encabezado__dato">{lista.length} {lista.length === 1 ? "pendiente" : "pendientes"}</span>}
      </header>

      {aviso && <p className="adm-alerta adm-alerta--exito" role="status">{aviso}</p>}
      {(error || (errorAccion && !confirmacion)) && <p className="adm-alerta adm-alerta--error" role="alert">{errorAccion || error}</p>}

      <div className="adm-tarjeta" aria-busy={!lista && !error}>
        {!lista && !error ? (
          <div className="adm-cargando">Cargando…</div>
        ) : lista && lista.length === 0 ? (
          <p className="adm-vacio">Todo al día: no hay negocios pendientes de revisión.</p>
        ) : lista && (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Negocio</th><th>Ciudad</th><th>NIT</th><th>Datos</th><th>Registro</th>
                  <th className="adm-tabla__der">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {lista.map((n) => {
                  const ocupado = procesando === clave(n);
                  return (
                    <tr key={clave(n)}>
                      <td data-label="Negocio">
                        <span className="adm-tabla__nombre">
                          <strong>{n.nombre || "Sin nombre"}</strong>
                          <span className="adm-badge adm-badge--chico adm-badge--info">{n.tipo === "TIENDA" ? "Tienda" : "Taller"}</span>
                        </span>
                        <span className="adm-tabla__sub">{n.direccion || "Sin dirección"}</span>
                      </td>
                      <td data-label="Ciudad">{capitalizar(n.ciudad) || "—"}</td>
                      <td data-label="NIT">{n.nit || "No registrado"}</td>
                      <td data-label="Datos">
                        {n.datosPorCompletar.length > 0
                          ? <span className="adm-badge adm-badge--pendiente">Falta: {n.datosPorCompletar.join(", ")}</span>
                          : <span className="adm-badge adm-badge--exito">Completos</span>}
                      </td>
                      <td data-label="Registro">{formatoFecha(n.registrado)}</td>
                      <td className="adm-tabla__der adm-tabla__celda--ancha">
                        <div className="adm-tabla__acciones">
                          <button type="button" className="adm-btn adm-btn--chico adm-btn--peligro" disabled={ocupado}
                            onClick={() => pedir("rechazar", n)}>Rechazar</button>
                          <button type="button" className="adm-btn adm-btn--chico adm-btn--primario" disabled={ocupado}
                            onClick={() => pedir("aprobar", n)}>Aprobar</button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {confirmacion && (
        <ConfirmacionAdmin
          titulo={`${confirmacion.accion === "aprobar" ? "Aprobar" : "Rechazar"} ${confirmacion.negocio.nombre}`}
          texto={confirmacion.accion === "aprobar"
            ? "La cuenta quedará activa con el plan de prueba y podrá operar en el marketplace."
            : "El negocio recibirá el motivo por correo y no podrá operar."}
          confirmar={confirmacion.accion === "aprobar" ? "Aprobar" : "Rechazar"}
          variante={confirmacion.accion === "aprobar" ? "primario" : "peligro"}
          cargando={procesando === clave(confirmacion.negocio)}
          deshabilitado={confirmacion.accion === "rechazar" && !motivo.trim()}
          onConfirmar={confirmar}
          onCancelar={() => { setConfirmacion(null); setErrorAccion(null); }}
        >
          {confirmacion.accion === "rechazar" && (
            <label className="adm-campo adm-campo--bloque">
              <span>Motivo (se le envía al negocio)</span>
              <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={300} required />
            </label>
          )}
          {errorAccion && <p className="adm-alerta adm-alerta--error" role="alert">{errorAccion}</p>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
