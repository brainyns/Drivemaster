import { useState } from "react";
import MecanicosTaller from "../../components/servicio/MecanicosTaller";
import { formatPrecio } from "../../components/servicio/estadosServicio";
import { actualizarDatosTaller } from "../../services/tallerService";

const HORARIO_MAX = 200;
const TARIFA_MAX = 1000000;

/** Formulario para cambiar la tarifa por hora y el horario. El horario se envía y se muestra como texto plano. */
function EditarDatos({ taller, token, onGuardado, onCancelar }) {
  const [tarifa, setTarifa] = useState(taller?.tarifaHora ?? "");
  const [horario, setHorario] = useState(taller?.horario ?? "");
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  const guardar = async (e) => {
    e.preventDefault();
    const valor = tarifa === "" ? null : Number(tarifa);
    if (valor !== null && (!Number.isFinite(valor) || valor <= 0 || valor > TARIFA_MAX)) {
      setError(`La tarifa por hora debe ser mayor que 0 y no pasar de ${formatPrecio(TARIFA_MAX)}.`);
      return;
    }
    if (valor === null && horario.trim() === (taller?.horario ?? "").trim()) {
      setError("No hay cambios para guardar.");
      return;
    }
    setError(null);
    setGuardando(true);
    try {
      const actualizado = await actualizarDatosTaller(token, { tarifaHora: valor, horario });
      onGuardado(actualizado);
    } catch (err) {
      setError(err.message);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <form className="tp-edicion" onSubmit={guardar} noValidate>
      <h3>Editar tarifa y horario</h3>
      <div className="tp-edicion__campo">
        <label htmlFor="tp-tarifa">Tarifa por hora (COP)</label>
        <input id="tp-tarifa" type="number" inputMode="decimal" min="1" max={TARIFA_MAX} step="any"
          value={tarifa} onChange={(e) => setTarifa(e.target.value)} aria-describedby="tp-tarifa-ayuda" />
        <p id="tp-tarifa-ayuda" className="tp-edicion__ayuda">
          Es el valor de la hora de mano de obra que se usa al cotizar por horas. Las cotizaciones que ya emitiste
          no cambian; las nuevas usan este valor. No cambia el “precio de mano de obra” del servicio.
        </p>
      </div>
      <div className="tp-edicion__campo">
        <label htmlFor="tp-horario">Horario de atención</label>
        <textarea id="tp-horario" maxLength={HORARIO_MAX} value={horario} onChange={(e) => setHorario(e.target.value)}
          aria-describedby="tp-horario-ayuda" />
        <p id="tp-horario-ayuda" className="tp-edicion__ayuda">
          Texto simple, hasta {HORARIO_MAX} caracteres ({horario.length}/{HORARIO_MAX}). Lo ve el cliente al elegir taller.
        </p>
      </div>
      {error && <p className="ss-alerta" role="alert">{error}</p>}
      <div className="tp-edicion__botones">
        <button type="submit" className="sv-btn" disabled={guardando}>{guardando ? "Guardando..." : "Guardar cambios"}</button>
        <button type="button" className="sv-btn-outline" onClick={onCancelar} disabled={guardando}>Cancelar</button>
      </div>
    </form>
  );
}

/** Datos del taller (tarifa y horario) y su equipo. */
export default function TallerMiTaller({ taller, ordenes, token, onActualizado }) {
  const [editando, setEditando] = useState(false);
  const [guardado, setGuardado] = useState(false);
  const nombre = taller?.nombre || ordenes[0]?.tallerNombre;
  const precio = taller?.precioManoObra ?? ordenes[0]?.precioManoObra;
  const faltaTarifa = !taller?.tarifaHora;
  const faltaHorario = !taller?.horario;
  const aprobado = taller?.estadoAprobacion === "APROBADO";
  return (
    <div className="pt-perfil">
      {nombre ? (
        <>
          <span className="pt-eyebrow">Datos del taller</span>
          <h2 className="pt-perfil-nombre">{nombre}</h2>
          <div className="pt-perfil-facts">
            <div className="pt-fact">
              <span className="pt-fact-label">Tarifa por hora</span>
              <span className="pt-fact-value">{faltaTarifa ? "Sin definir" : formatPrecio(taller.tarifaHora)}</span>
            </div>
            <div className="pt-fact">
              <span className="pt-fact-label">Precio de mano de obra</span>
              <span className="pt-fact-value">{formatPrecio(precio)}</span>
            </div>
            <div className="pt-fact">
              <span className="pt-fact-label">Horario</span>
              <span className="pt-fact-value tp-horario">{faltaHorario ? "Sin definir" : taller.horario}</span>
            </div>
            {taller?.ciudad && (
              <div className="pt-fact">
                <span className="pt-fact-label">Ciudad</span>
                <span className="pt-fact-value">{taller.ciudad}</span>
              </div>
            )}
          </div>
          {(faltaTarifa || faltaHorario) && (
            <p className="ss-aviso">
              Falta {faltaTarifa && "la tarifa por hora"}{faltaTarifa && faltaHorario && " y "}{faltaHorario && "el horario"}.
              La tarifa se usa al cotizar por horas y el horario lo ve el cliente al elegir taller.
            </p>
          )}

          {editando ? (
            <EditarDatos
              taller={taller} token={token}
              onCancelar={() => setEditando(false)}
              onGuardado={(t) => { onActualizado?.(t); setEditando(false); setGuardado(true); }}
            />
          ) : (
            <>
              {aprobado && (
                <button type="button" className="sv-btn-outline" onClick={() => { setGuardado(false); setEditando(true); }}>
                  Editar tarifa y horario
                </button>
              )}
              {guardado && <p className="tp-edicion__ok" role="status">Cambios guardados.</p>}
            </>
          )}
          <p className="pt-hint">
            Para cambiar el nombre, la ciudad, la dirección o el precio de mano de obra, escríbele al administrador de DriveMaster.
          </p>
        </>
      ) : (
        <p className="sv-empty">Tu taller todavía no aparece en el reporte.</p>
      )}
      <MecanicosTaller token={token} />
    </div>
  );
}
