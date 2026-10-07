import { useState, useEffect, useRef } from "react";
import { getToken } from "../services/authService";
import { misVehiculos, crearVehiculo, subirImagenVehiculo, configurarMantenimiento } from "../services/vehiculoService";
import "../css/servicios.css";

const VACIO = { marca: "", modelo: "", anio: "", motor: "", combustible: "Gasolina", placa: "", kilometraje: "" };

/** Panel de mantenimiento preventivo del vehículo: estado, intervalo y opt-out. */
function MantenimientoCard({ vehiculo, alGuardar }) {
  const [editando, setEditando] = useState(false);
  const [intervalo, setIntervalo] = useState(vehiculo.intervaloMantenimientoKm ?? 5000);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");

  const guardar = async (config) => {
    setGuardando(true);
    setError("");
    try {
      await configurarMantenimiento(getToken(), vehiculo.id, config);
      setEditando(false);
      await alGuardar();
    } catch (e) {
      setError(e.message);
    } finally {
      setGuardando(false);
    }
  };

  const alternarRecordatorios = () =>
    guardar({ recordatoriosActivos: !vehiculo.recordatoriosActivos });

  const faltan = vehiculo.kmProximoMantenimiento != null && vehiculo.kilometraje != null
    ? vehiculo.kmProximoMantenimiento - vehiculo.kilometraje
    : null;

  return (
    <div className={`sv-manto ${vehiculo.mantenimientoVencido ? "sv-manto-vencido" : ""}`}>
      <div className="sv-manto-head">
        <span className="sv-manto-label">Mantenimiento</span>
        {vehiculo.mantenimientoVencido ? (
          <span className="sv-manto-badge sv-manto-badge-alerta">Vencido</span>
        ) : faltan != null ? (
          <span className="sv-manto-badge">En {faltan.toLocaleString("es-CO")} km</span>
        ) : null}
      </div>

      {vehiculo.mantenimientoVencido && (
        <span className="sv-manto-aviso">
          {vehiculo.ultimoRecordatorioEnviado
            ? `Te avisamos por correo el ${new Date(vehiculo.ultimoRecordatorioEnviado).toLocaleDateString("es-CO")}.`
            : "Te avisaremos por correo."}
        </span>
      )}

      {vehiculo.kmUltimoMantenimiento != null && (
        <span className="sv-manto-meta">
          Último: {vehiculo.kmUltimoMantenimiento.toLocaleString("es-CO")} km
          {vehiculo.kmProximoMantenimiento != null &&
            ` · Próximo: ${vehiculo.kmProximoMantenimiento.toLocaleString("es-CO")} km`}
        </span>
      )}

      {editando ? (
        <div className="sv-manto-edit">
          <label className="sv-manto-label" htmlFor={`int-${vehiculo.id}`}>
            Intervalo (km)
          </label>
          <input
            id={`int-${vehiculo.id}`}
            type="number"
            min="500"
            step="500"
            value={intervalo}
            onChange={(e) => setIntervalo(e.target.value)}
          />
          <div className="sv-manto-actions">
            <button className="sv-btn" disabled={guardando} onClick={() => guardar({ intervaloKm: parseInt(intervalo, 10) })}>
              {guardando ? "Guardando..." : "Guardar"}
            </button>
            <button className="sv-btn-outline" disabled={guardando} onClick={() => setEditando(false)}>
              Cancelar
            </button>
          </div>
        </div>
      ) : (
        <div className="sv-manto-actions">
          <button className="sv-manto-link" onClick={() => { setIntervalo(vehiculo.intervaloMantenimientoKm ?? 5000); setEditando(true); }}>
            Cambiar intervalo
          </button>
          <label className="sv-manto-opt">
            <input
              type="checkbox"
              checked={!!vehiculo.recordatoriosActivos}
              disabled={guardando}
              onChange={alternarRecordatorios}
            />
            Recordatorios por correo
          </label>
        </div>
      )}

      {error && <span className="sv-manto-error">{error}</span>}
    </div>
  );
}

export default function MisVehiculos({ onVolver, onVerServicio }) {
  const token = getToken();
  const [vehiculos, setVehiculos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [form, setForm] = useState(VACIO);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");
  const [imagenFile, setImagenFile] = useState(null);
  const [imagenPreview, setImagenPreview] = useState(null);
  const fileInputRef = useRef(null);

  const cargar = async () => {
    setCargando(true);
    try {
      setVehiculos(await misVehiculos(token));
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleImagenChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setImagenFile(file);
    setImagenPreview(URL.createObjectURL(file));
  };

  const quitarImagen = () => {
    setImagenFile(null);
    setImagenPreview(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setGuardando(true);
    setError("");
    try {
      const creado = await crearVehiculo(token, {
        ...form,
        anio: form.anio ? parseInt(form.anio, 10) : null,
        kilometraje: form.kilometraje ? parseInt(form.kilometraje, 10) : null,
      });
      if (imagenFile && creado?.id) {
        await subirImagenVehiculo(token, creado.id, imagenFile);
      }
      setForm(VACIO);
      quitarImagen();
      setMostrarForm(false);
      cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="sv-root">
      <div className="sv-header-top">
        <div>
          <h1 className="sv-title">Mis Vehículos</h1>
          <p className="sv-subtitle">Gestiona tus vehículos y encuentra repuestos e instalación compatibles.</p>
        </div>
        {onVolver && <button className="sv-volver" onClick={onVolver}>← Volver al catálogo</button>}
      </div>

      <button className="sv-btn" style={{ marginBottom: "var(--sp-lg)" }} onClick={() => setMostrarForm(m => !m)}>
        {mostrarForm ? "Cancelar" : "+ Agregar vehículo"}
      </button>

      {mostrarForm && (
        <form className="sv-form" onSubmit={handleSubmit}>
          <div className="sv-field">
            <label>Marca</label>
            <input name="marca" value={form.marca} onChange={handleChange} required />
          </div>
          <div className="sv-field">
            <label>Modelo</label>
            <input name="modelo" value={form.modelo} onChange={handleChange} required />
          </div>
          <div className="sv-field">
            <label>Año</label>
            <input name="anio" type="number" value={form.anio} onChange={handleChange} required />
          </div>
          <div className="sv-field">
            <label>Motor</label>
            <input name="motor" value={form.motor} onChange={handleChange} />
          </div>
          <div className="sv-field">
            <label>Combustible</label>
            <select name="combustible" value={form.combustible} onChange={handleChange}>
              <option>Gasolina</option>
              <option>Diésel</option>
              <option>Híbrido</option>
              <option>Eléctrico</option>
              <option>Gas</option>
            </select>
          </div>
          <div className="sv-field">
            <label>Placa</label>
            <input name="placa" value={form.placa} onChange={handleChange} required />
          </div>
          <div className="sv-field">
            <label>Kilometraje</label>
            <input name="kilometraje" type="number" value={form.kilometraje} onChange={handleChange} />
          </div>
          <div className="sv-form-full">
            <label>Foto del vehículo (opcional)</label>
            {imagenPreview ? (
              <div className="sv-imagen-preview">
                <img src={imagenPreview} alt="Vista previa del vehículo" />
                <button type="button" className="sv-btn-outline" onClick={quitarImagen}>
                  Quitar foto
                </button>
              </div>
            ) : (
              <button
                type="button"
                className="sv-btn-outline"
                onClick={() => fileInputRef.current?.click()}
              >
                + Agregar foto
              </button>
            )}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleImagenChange}
              style={{ display: "none" }}
            />
          </div>
          <div className="sv-form-full">
            <button className="sv-btn" type="submit" disabled={guardando}>
              {guardando ? "Guardando..." : "Guardar vehículo"}
            </button>
          </div>
        </form>
      )}

      {error && <p style={{ color: "var(--error)" }}>{error}</p>}

      {cargando ? (
        <p className="sv-empty">Cargando...</p>
      ) : vehiculos.length === 0 ? (
        <p className="sv-empty">Aún no tienes vehículos registrados.</p>
      ) : (
        <div className="sv-grid">
          {vehiculos.map(v => (
            <div className="sv-card" key={v.id}>
              {v.imagenUrl && (
                <img className="sv-card-foto" src={v.imagenUrl} alt={`${v.marca} ${v.modelo}`} />
              )}
              <h3>{v.marca} {v.modelo}</h3>
              <span className="sv-card-meta">{v.anio} · {v.placa}</span>
              <span className="sv-card-meta">{v.motor} · {v.combustible}</span>
              <span className="sv-card-meta">{v.kilometraje != null ? `${v.kilometraje.toLocaleString("es-CO")} km` : ""}</span>

              <MantenimientoCard vehiculo={v} alGuardar={cargar} />
              <button className="sv-btn" style={{ marginTop: 8 }} onClick={() => onVerServicio(v.id)}>
                Ver repuestos e historial
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}