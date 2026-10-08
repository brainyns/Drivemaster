import { useState } from "react";

/**
 * Datos públicos de una tienda: ciudad, dirección, horario y costo de envío. Mientras falten ciudad,
 * dirección u horario, sus productos no aparecen en el catálogo marketplace.
 * La TIENDA edita la suya: se pasa alGuardar = actualizarMiTienda.
 */
export default function DatosTiendaPropia({ token, tienda, onGuardado, alGuardar, titulo = "Mi tienda" }) {
  const [form, setForm] = useState({
    ciudad: tienda.ciudad || "",
    barrio: tienda.barrio || "",
    direccion: tienda.direccion || "",
    horario: tienda.horario || "",
    telefono: tienda.telefono || "",
    ofreceEnvio: tienda.costoEnvio != null,
    costoEnvio: tienda.costoEnvio != null ? String(tienda.costoEnvio) : "",
  });
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const [ok, setOk] = useState(false);

  const cambiar = (e) => {
    const { name, value, type, checked } = e.target;
    setForm((f) => ({ ...f, [name]: type === "checkbox" ? checked : value }));
    setOk(false);
  };

  const faltan = ["ciudad", "direccion", "horario"].filter((k) => !form[k].trim());

  const guardar = async (e) => {
    e.preventDefault();
    if (faltan.length) return setError("Completa ciudad, dirección y horario.");
    if (form.ofreceEnvio && (form.costoEnvio === "" || Number(form.costoEnvio) < 0)) {
      return setError("Escribe el costo fijo de envío (puede ser 0).");
    }
    setGuardando(true);
    setError(null);
    try {
      await alGuardar(token, {
        ciudad: form.ciudad.trim(), barrio: form.barrio.trim() || null, direccion: form.direccion.trim(),
        horario: form.horario.trim(), telefono: form.telefono.trim() || null,
        ofreceEnvio: form.ofreceEnvio, costoEnvio: form.ofreceEnvio ? Number(form.costoEnvio) : null,
      });
      setOk(true);
      await onGuardado?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <section className="ap-propia" aria-labelledby="ap-propia-titulo">
      <div className="ap-propia__cabecera">
        <h2 id="ap-propia-titulo">{titulo}</h2>
        <span className={`ap-chip ${tienda.datosCompletos ? "ap-chip--aprobado" : "ap-chip--pendiente"}`}>
          {tienda.datosCompletos ? "Visible en el catálogo" : "Completar datos"}
        </span>
      </div>
      <p className="ap-sub">
        {tienda.datosCompletos
          ? "Tus productos se muestran en el catálogo de las tiendas."
          : "Tus productos no se muestran en el catálogo hasta completar ciudad, dirección y horario."}
      </p>

      <form className="ap-propia__form" onSubmit={guardar}>
        <label>Ciudad<input name="ciudad" value={form.ciudad} onChange={cambiar} required /></label>
        <label>Barrio<input name="barrio" value={form.barrio} onChange={cambiar} /></label>
        <label className="ap-propia__ancho">Dirección<input name="direccion" value={form.direccion} onChange={cambiar} required /></label>
        <label className="ap-propia__ancho">Horario de atención<input name="horario" value={form.horario} onChange={cambiar}
          placeholder="L-V 8:00-18:00, S 8:00-13:00" required /></label>
        <label>Teléfono<input name="telefono" value={form.telefono} onChange={cambiar} /></label>
        <label className="ap-propia__check">
          <input type="checkbox" name="ofreceEnvio" checked={form.ofreceEnvio} onChange={cambiar} />
          Hace envíos (solo dentro de su ciudad)
        </label>
        {form.ofreceEnvio && (
          <label>Costo fijo de envío (COP)
            <input name="costoEnvio" type="number" min="0" step="500" value={form.costoEnvio} onChange={cambiar} />
          </label>
        )}
        <div className="ap-propia__acciones">
          <button type="submit" className="ap-btn ap-btn--primario" disabled={guardando}>
            {guardando ? "Guardando..." : "Guardar datos"}
          </button>
        </div>
      </form>
      {error && <p className="ap-alerta ap-alerta--error" role="alert">{error}</p>}
      {ok && <p className="ap-alerta ap-alerta--ok" role="status">Datos guardados.</p>}
    </section>
  );
}
