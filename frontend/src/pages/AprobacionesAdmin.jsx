import { useCallback, useEffect, useState } from "react";
import { negociosPendientes } from "../services/plataformaService";
import { resolverAprobacionTaller } from "../services/tallerService";
import { resolverAprobacionTienda } from "../services/tiendaService";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import LogoNegocio from "../components/ui/LogoNegocio";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import { capitalizar, formatoFecha, unir } from "../utils/formato";
// El diálogo de confirmación (ConfirmacionAdmin) trae su estilo de admin-base.css
import "../css/admin-base.css";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/aprobaciones-plataforma.css";

const clave = (n) => `${n.tipo}-${n.id}`;
const nombreTipo = (n) => (n.tipo === "TIENDA" ? "Tienda" : "Taller");

/**
 * Aprobaciones del ADMIN: solo los negocios pendientes y dos decisiones, aprobar o rechazar (con motivo). La
 * calificación y la actividad de los negocios, y pausarlos, son del SUPERADMIN.
 */
export default function AprobacionesAdmin({ token }) {
  const [estado, setEstado] = useState({ lista: null, error: null });
  // { accion: "aprobar" | "rechazar", negocio }
  const [confirmacion, setConfirmacion] = useState(null);
  const [motivo, setMotivo] = useState("");
  const [procesando, setProcesando] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);
  const [reintentando, setReintentando] = useState(false);

  const cargar = useCallback(() => (
    negociosPendientes(token)
      .then((lista) => setEstado({ lista, error: null }))
      .catch((e) => setEstado((p) => ({ ...p, error: e.message })))
  ), [token]);

  useEffect(() => {
    const t = setTimeout(cargar, 0);
    return () => clearTimeout(t);
  }, [cargar]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
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
    <div className="ss-panel--plataforma pp-root apr-root">
      <EncabezadoPagina titulo="Aprobaciones"
        descripcion="Negocios que esperan tu revisión. Aprobarlos los activa en el marketplace; al rechazarlos se les envía el motivo." />

      {aviso && <Alerta tono="ok" className="pp-bloque-sep" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}
      {errorAccion && !confirmacion && <Alerta className="pp-bloque-sep" onCerrar={() => setErrorAccion(null)}>{errorAccion}</Alerta>}
      {error && (
        <Alerta className="pp-bloque-sep" onReintentar={reintentar} reintentando={reintentando}>
          {lista ? `No pudimos actualizar la lista: ${error}` : error}
        </Alerta>
      )}

      {!lista ? (
        !error && <Esqueleto variante="lista" cantidad={4} texto="Cargando negocios pendientes…" />
      ) : lista.length === 0 ? (
        <EstadoVacio icono="check" titulo="Todo al día"
          texto="No hay negocios pendientes de revisión. Cuando una tienda o un taller se registre, aparece aquí." />
      ) : (
        <div className="ui-card ui-table-card">
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas">
              <caption className="ui-sr">{lista.length} {lista.length === 1 ? "negocio pendiente" : "negocios pendientes"} de revisión</caption>
              <thead>
                <tr>
                  <th scope="col">Negocio</th>
                  <th scope="col">Ciudad</th>
                  <th scope="col">NIT</th>
                  <th scope="col">Datos</th>
                  <th scope="col">Registro</th>
                  <th scope="col"><span className="ui-sr">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {lista.map((n) => {
                  const ocupado = procesando === clave(n);
                  const faltan = n.datosPorCompletar || [];
                  return (
                    <tr key={clave(n)}>
                      <td className="ui-table__titulo">
                        <span className="apr-negocio">
                          <LogoNegocio nombre={n.nombre} />
                          <span className="apr-negocio__texto">
                            <strong>{n.nombre || "Sin nombre"}</strong>
                            <span className="pp-sub">{unir([nombreTipo(n), n.direccion || "Sin dirección"])}</span>
                          </span>
                        </span>
                      </td>
                      <td data-label="Ciudad">{capitalizar(n.ciudad) || <span className="pp-tenue">Sin ciudad</span>}</td>
                      <td data-label="NIT">{n.nit || <span className="pp-tenue">No registrado</span>}</td>
                      <td data-label="Datos">
                        {faltan.length > 0
                          ? <ChipEstado tono="aviso">Falta: {faltan.join(", ")}</ChipEstado>
                          : <ChipEstado tono="ok">Completos</ChipEstado>}
                      </td>
                      <td data-label="Registro" className="apr-fecha">{formatoFecha(n.registrado)}</td>
                      <td className="ui-table__acciones">
                        <button type="button" className="ui-btn ui-btn--texto ui-btn--chico apr-peligro" disabled={ocupado}
                          onClick={() => pedir("rechazar", n)} aria-label={`Rechazar ${n.nombre}`}>Rechazar</button>
                        <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" disabled={ocupado}
                          onClick={() => pedir("aprobar", n)} aria-label={`Aprobar ${n.nombre}`}>Aprobar</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {confirmacion && (
        <ConfirmacionAdmin
          titulo={`${confirmacion.accion === "aprobar" ? "Aprobar" : "Rechazar"} ${confirmacion.negocio.nombre}`}
          texto={confirmacion.accion === "aprobar"
            ? "La cuenta quedará activa y podrá operar en el marketplace."
            : "El negocio recibirá el motivo por correo y no podrá operar."}
          confirmar={confirmacion.accion === "aprobar" ? "Aprobar" : "Rechazar"}
          variante={confirmacion.accion === "aprobar" ? "primario" : "peligro"}
          cargando={procesando === clave(confirmacion.negocio)}
          deshabilitado={confirmacion.accion === "rechazar" && !motivo.trim()}
          onConfirmar={confirmar}
          onCancelar={() => { setConfirmacion(null); setErrorAccion(null); }}
        >
          {confirmacion.accion === "rechazar" && (
            <Campo id="apra-motivo" label="Motivo" ayuda="Se le envía al negocio por correo.">
              <textarea className="ui-field__input apr-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={300} required />
            </Campo>
          )}
          {errorAccion && <Alerta>{errorAccion}</Alerta>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
