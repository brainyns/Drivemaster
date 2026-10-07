import { useCallback, useEffect, useState } from "react";
import {
  listarMecanicos, agregarMecanico, actualizarMecanico, cambiarEstadoMecanico,
} from "../../services/tallerService";

const VACIO = { nombre: "", especialidad: "", telefono: "" };

/**
 * Mecánicos del taller. En v1 son nombres asignables a las solicitudes; no se
 * borran (quedan en el historial) sino que se desactivan.
 */
export default function MecanicosTaller({ token, onCambio }) {
  const [mecanicos, setMecanicos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [form, setForm] = useState(VACIO);
  const [editandoId, setEditandoId] = useState(null);
  const [error, setError] = useState(null);
  const [procesando, setProcesando] = useState(false);

  const cargar = useCallback(async () => {
    try {
      const data = await listarMecanicos(token);
      setMecanicos(data || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token]);

  useEffect(() => { cargar(); }, [cargar]);

  const ejecutar = async (accion) => {
    setProcesando(true);
    setError(null);
    try {
      await accion();
      await cargar();
      onCambio?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const guardar = (e) => {
    e.preventDefault();
    if (!form.nombre.trim()) {
      setError("Escribe el nombre del mecánico.");
      return;
    }
    const datos = { nombre: form.nombre.trim(), especialidad: form.especialidad.trim() || null, telefono: form.telefono.trim() || null };
    ejecutar(async () => {
      if (editandoId) await actualizarMecanico(token, editandoId, datos);
      else await agregarMecanico(token, datos);
      setForm(VACIO);
      setEditandoId(null);
    });
  };

  const editar = (m) => {
    setEditandoId(m.id);
    setForm({ nombre: m.nombre || "", especialidad: m.especialidad || "", telefono: m.telefono || "" });
  };

  return (
    <section className="ss-bloque">
      <h2 className="ss-bloque__titulo">Mecánicos</h2>
      <p className="ss-meta">Asigna un mecánico a cada solicitud: el diagnóstico queda a su nombre.</p>

      {error && <p className="ss-alerta" role="alert">{error}</p>}

      {cargando ? (
        <p className="sv-empty">Cargando...</p>
      ) : mecanicos.length === 0 ? (
        <p className="ss-meta">Todavía no has registrado mecánicos.</p>
      ) : (
        <ul className="ss-mecanicos">
          {mecanicos.map((m) => (
            <li key={m.id} className={`ss-mecanico${m.activo === false ? " ss-mecanico--inactivo" : ""}`}>
              <div>
                <strong>{m.nombre}</strong>
                <span className="ss-meta">
                  {[m.especialidad, m.telefono].filter(Boolean).join(" · ") || "Sin especialidad registrada"}
                  {m.activo === false && " · Inactivo"}
                </span>
              </div>
              <div className="ss-acciones">
                <button type="button" className="sv-btn-outline" onClick={() => editar(m)} disabled={procesando}>Editar</button>
                <button type="button" className="sv-btn-outline" disabled={procesando}
                  onClick={() => ejecutar(() => cambiarEstadoMecanico(token, m.id, m.activo === false))}>
                  {m.activo === false ? "Activar" : "Desactivar"}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <form className="sv-form ss-mecanicos__form" onSubmit={guardar}>
        <div className="sv-field">
          <label htmlFor="ss-mec-nombre">{editandoId ? "Editar nombre" : "Nombre"}</label>
          <input id="ss-mec-nombre" value={form.nombre} maxLength={80}
            onChange={(e) => setForm({ ...form, nombre: e.target.value })} placeholder="Carlos Pérez" />
        </div>
        <div className="sv-field">
          <label htmlFor="ss-mec-esp">Especialidad (opcional)</label>
          <input id="ss-mec-esp" value={form.especialidad} maxLength={80}
            onChange={(e) => setForm({ ...form, especialidad: e.target.value })} placeholder="Frenos y suspensión" />
        </div>
        <div className="sv-field">
          <label htmlFor="ss-mec-tel">Teléfono (opcional)</label>
          <input id="ss-mec-tel" type="tel" value={form.telefono} maxLength={20}
            onChange={(e) => setForm({ ...form, telefono: e.target.value })} />
        </div>
        <div className="sv-field ss-mecanicos__botones">
          <button type="submit" className="sv-btn" disabled={procesando}>
            {editandoId ? "Guardar cambios" : "Agregar mecánico"}
          </button>
          {editandoId && (
            <button type="button" className="sv-btn-outline" onClick={() => { setEditandoId(null); setForm(VACIO); }}>
              Cancelar
            </button>
          )}
        </div>
      </form>
    </section>
  );
}
