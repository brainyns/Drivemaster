import { useCallback, useEffect, useState } from "react";
import {
  listarMecanicos, agregarMecanico, actualizarMecanico, cambiarEstadoMecanico,
} from "../../services/tallerService";
import SeccionEditable from "../ui/SeccionEditable";
import Campo from "../ui/Campo";
import ChipEstado from "../ui/ChipEstado";
import Alerta from "../ui/Alerta";
import Esqueleto from "../ui/Esqueleto";
import EstadoVacio from "../ui/EstadoVacio";
import Icon from "../public/Icon";
import { iniciales } from "../../pages/taller/perfilTaller";

const VACIO = { nombre: "", especialidad: "", telefono: "" };

/**
 * Mecánicos del taller. En v1 son nombres asignables a las solicitudes; no se
 * borran (quedan en el historial) sino que se desactivan. onLista recibe la
 * lista cada vez que se carga (Mi taller la usa para el perfil completo).
 */
export default function MecanicosTaller({ token, onCambio, onLista, id = "mt-equipo" }) {
  const [mecanicos, setMecanicos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [form, setForm] = useState(VACIO);
  // null: sin formulario; "nuevo" o el id del mecánico que se edita
  const [editandoId, setEditandoId] = useState(null);
  const [errorNombre, setErrorNombre] = useState(null);
  const [error, setError] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [procesando, setProcesando] = useState(false);

  const cargar = useCallback(async () => {
    try {
      const data = await listarMecanicos(token);
      setMecanicos(data || []);
      onLista?.(data || []);
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  }, [token, onLista]);

  useEffect(() => { cargar(); }, [cargar]);

  const ejecutar = async (accion, textoOk) => {
    setProcesando(true);
    setError(null);
    setAviso(null);
    try {
      await accion();
      await cargar();
      onCambio?.();
      setAviso(textoOk);
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(false);
    }
  };

  const cerrar = () => { setEditandoId(null); setForm(VACIO); setErrorNombre(null); };

  const guardar = (e) => {
    e.preventDefault();
    if (!form.nombre.trim()) {
      setErrorNombre("Escribe el nombre del mecánico.");
      return;
    }
    const datos = { nombre: form.nombre.trim(), especialidad: form.especialidad.trim() || null, telefono: form.telefono.trim() || null };
    const nuevo = editandoId === "nuevo";
    ejecutar(async () => {
      if (nuevo) await agregarMecanico(token, datos);
      else await actualizarMecanico(token, editandoId, datos);
      cerrar();
    }, nuevo ? `${datos.nombre} ya está en tu equipo.` : "Cambios guardados.");
  };

  const editar = (m) => {
    setAviso(null);
    setEditandoId(m.id);
    setForm({ nombre: m.nombre || "", especialidad: m.especialidad || "", telefono: m.telefono || "" });
  };

  const activos = mecanicos.filter((m) => m.activo !== false).length;

  return (
    <SeccionEditable
      id={id}
      icono="users"
      titulo="Equipo"
      descripcion={mecanicos.length > 0
        ? `${activos} ${activos === 1 ? "mecánico activo" : "mecánicos activos"}. Asigna uno a cada solicitud: el diagnóstico queda a su nombre.`
        : "Asigna un mecánico a cada solicitud: el diagnóstico queda a su nombre."}
      onEditar={mecanicos.length > 0 ? () => { setAviso(null); setEditandoId("nuevo"); } : undefined}
      textoEditar="Agregar mecánico"
      editando={editandoId !== null}
      aviso={(error || aviso) && (
        <Alerta tono={error ? "error" : "ok"} onCerrar={() => { setError(null); setAviso(null); }}
          onReintentar={error && mecanicos.length === 0 ? cargar : undefined}>
          {error || aviso}
        </Alerta>
      )}
    >
      {cargando ? (
        <Esqueleto variante="lista" cantidad={2} texto="Cargando tu equipo…" />
      ) : mecanicos.length === 0 && editandoId === null ? (
        <EstadoVacio icono="users" nivel={3} titulo="Aún no tienes mecánicos"
          texto="Sin mecánicos no puedes asignar las revisiones. Agrega a quien recibe los vehículos.">
          <button type="button" className="ui-btn ui-btn--primario" onClick={() => setEditandoId("nuevo")}>
            <Icon name="plus" size={16} /> Agregar mecánico
          </button>
        </EstadoVacio>
      ) : mecanicos.length > 0 && (
        <ul className="mt-equipo">
          {mecanicos.map((m) => {
            const inactivo = m.activo === false;
            return (
              <li key={m.id} className={`mt-equipo__fila${inactivo ? " mt-equipo__fila--inactivo" : ""}`}>
                <span className="mt-equipo__avatar" aria-hidden="true">{iniciales(m.nombre)}</span>
                <span className="mt-equipo__datos">
                  <strong>{m.nombre}</strong>
                  <span>{[m.especialidad, m.telefono].filter(Boolean).join(" · ") || "Sin especialidad registrada"}</span>
                </span>
                <ChipEstado tono={inactivo ? "apagado" : "ok"}>{inactivo ? "Inactivo" : "Activo"}</ChipEstado>
                <span className="mt-equipo__acciones">
                  <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={() => editar(m)}
                    disabled={procesando} aria-label={`Editar a ${m.nombre}`}>Editar</button>
                  <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" disabled={procesando}
                    aria-label={`${inactivo ? "Activar" : "Desactivar"} a ${m.nombre}`}
                    onClick={() => ejecutar(() => cambiarEstadoMecanico(token, m.id, inactivo),
                      inactivo ? `${m.nombre} vuelve a recibir solicitudes.` : `${m.nombre} ya no aparece al asignar solicitudes.`)}>
                    {inactivo ? "Activar" : "Desactivar"}
                  </button>
                </span>
              </li>
            );
          })}
        </ul>
      )}

      {editandoId !== null && (
        <form className="ui-form" onSubmit={guardar} noValidate aria-label={editandoId === "nuevo" ? "Agregar mecánico" : "Editar mecánico"}>
          <div className="ui-form__campos">
            <Campo id="mt-mec-nombre" label="Nombre" error={errorNombre}>
              <input className="ui-field__input" value={form.nombre} maxLength={80} autoFocus autoComplete="off"
                onChange={(e) => { setForm({ ...form, nombre: e.target.value }); if (errorNombre) setErrorNombre(null); }}
                placeholder="Carlos Pérez" />
            </Campo>
            <Campo id="mt-mec-esp" label="Especialidad" opcional>
              <input className="ui-field__input" value={form.especialidad} maxLength={80}
                onChange={(e) => setForm({ ...form, especialidad: e.target.value })} placeholder="Frenos y suspensión" />
            </Campo>
            <Campo id="mt-mec-tel" label="Teléfono" opcional>
              <input className="ui-field__input" type="tel" inputMode="tel" value={form.telefono} maxLength={20}
                onChange={(e) => setForm({ ...form, telefono: e.target.value })} />
            </Campo>
          </div>
          <div className="ui-form-pie">
            <button type="submit" className="ui-btn ui-btn--primario" disabled={procesando} aria-busy={procesando}>
              {procesando ? "Guardando…" : editandoId === "nuevo" ? "Agregar al equipo" : "Guardar cambios"}
            </button>
            <button type="button" className="ui-btn ui-btn--secundario" onClick={cerrar} disabled={procesando}>Cancelar</button>
          </div>
        </form>
      )}
    </SeccionEditable>
  );
}
