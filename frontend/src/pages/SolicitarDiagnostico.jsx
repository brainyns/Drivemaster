import { useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { misVehiculos } from "../services/vehiculoService";
import { obtenerMiPerfil } from "../services/clienteService";
import { crearSolicitudServicio } from "../services/solicitudServicioService";
import SubidaFotos from "../components/servicio/SubidaFotos";
import MapaTalleres from "../components/mapa/MapaTalleres";
import RutaTaller from "../components/mapa/RutaTaller";
import { formatPrecio } from "../components/servicio/estadosServicio";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080";

const HORARIOS = ["Cualquier horario", "Mañanas entre semana", "Tardes entre semana", "Sábados"];
const MIN_SINTOMAS = 10;

/** El cliente pide un diagnóstico: el taller más cercano revisa el vehículo y cotiza. */
export default function SolicitarDiagnostico({ vehiculoIdInicial, onVolver, onVerServicios, onIrVehiculos }) {
  const token = getToken();

  const [vehiculos, setVehiculos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [tarifa, setTarifa] = useState(null);

  const [vehiculoId, setVehiculoId] = useState(vehiculoIdInicial || "");
  const [sintomas, setSintomas] = useState("");
  const [fotos, setFotos] = useState([]);
  const [ciudad, setCiudad] = useState("");
  const [barrio, setBarrio] = useState("");
  const [coordenadas, setCoordenadas] = useState(null);
  // Dirección legible del pin (la da el buscador o Nominatim)
  const [direccion, setDireccion] = useState("");
  const [horario, setHorario] = useState(HORARIOS[0]);
  const [notaHorario, setNotaHorario] = useState("");
  // Taller elegido en el mapa; null = asignación automática
  const [tallerId, setTallerId] = useState(null);

  const [errores, setErrores] = useState({});
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState(null);

  useEffect(() => {
    let vivo = true;
    Promise.allSettled([misVehiculos(token), obtenerMiPerfil(token)]).then(([v, p]) => {
      if (!vivo) return;
      if (v.status === "fulfilled") {
        setVehiculos(v.value || []);
        if (!vehiculoIdInicial && v.value?.length === 1) setVehiculoId(v.value[0].id);
      } else {
        setError(v.reason.message);
      }
      if (p.status === "fulfilled") {
        setCiudad((c) => c || p.value?.ciudad || "");
      }
      setCargando(false);
    });
    // La tarifa es un parámetro público; si no carga, el formulario sigue.
    fetch(`${API}/api/config/parametros/TARIFA_DIAGNOSTICO`)
      .then((r) => (r.ok ? r.json() : null))
      .then((p) => { if (vivo && p?.valor) setTarifa(Number(p.valor)); })
      .catch(() => {});
    return () => { vivo = false; };
  }, [token, vehiculoIdInicial]);

  // La dirección sigue al pin; ciudad y barrio solo se completan si el cliente no los escribió
  const recibirDireccion = (d) => {
    setDireccion(d.etiqueta || d.direccion || "");
    if (d.ciudad) setCiudad((c) => c.trim() ? c : d.ciudad);
    if (d.barrio) setBarrio((b) => b.trim() ? b : d.barrio);
  };

  const validar = () => {
    const e = {};
    if (!vehiculoId) e.vehiculo = "Elige el vehículo.";
    if (sintomas.trim().length < MIN_SINTOMAS) e.sintomas = `Cuéntanos qué pasa (mínimo ${MIN_SINTOMAS} caracteres).`;
    if (!ciudad.trim()) e.ciudad = "Indica la ciudad.";
    return e;
  };

  const enviar = async (ev) => {
    ev.preventDefault();
    setError(null);
    const e = validar();
    setErrores(e);
    if (Object.keys(e).length) return;

    setEnviando(true);
    try {
      const creada = await crearSolicitudServicio(token, {
        vehiculoId,
        sintomas: sintomas.trim(),
        fotos,
        ciudad: ciudad.trim(),
        barrio: barrio.trim() || null,
        direccion: coordenadas && direccion.trim() ? direccion.trim() : null,
        latitud: coordenadas?.latitud ?? null,
        longitud: coordenadas?.longitud ?? null,
        preferenciaHorario: notaHorario.trim() ? `${horario} — ${notaHorario.trim()}` : horario,
        tallerId,
      });
      setResultado(creada);
    } catch (err) {
      setError(err.message);
    } finally {
      setEnviando(false);
    }
  };

  if (cargando) return <div className="sv-root"><p className="sv-empty">Cargando...</p></div>;

  if (resultado) {
    return (
      <div className="sv-root">
        <div className="ss-confirmacion">
          <span className="ss-eyebrow">Solicitud #{resultado.id?.slice(-6).toUpperCase()}</span>
          {resultado.estado === "ASIGNADA_A_TALLER" && (
            <>
              <h1 className="sv-title">Tu solicitud ya tiene taller</h1>
              <p>
                {resultado.tallerElegidoId && resultado.tallerElegidoId === resultado.tallerId
                  ? "La enviamos al taller que elegiste, " : "La asignamos a "}
                <strong>{resultado.tallerNombre}</strong>
                {resultado.distanciaTallerKm != null && ` (a ${resultado.distanciaTallerKm.toFixed(1)} km)`}.
                El taller te propondrá una fecha para revisar tu vehículo; te avisaremos por correo.
              </p>
              <RutaTaller
                origen={resultado.latitud != null ? { latitud: resultado.latitud, longitud: resultado.longitud } : null}
                taller={resultado.tallerLatitud != null
                  ? { nombre: resultado.tallerNombre, latitud: resultado.tallerLatitud, longitud: resultado.tallerLongitud }
                  : null}
              />
            </>
          )}
          {resultado.estado === "RECHAZADA" && (
            <>
              <h1 className="sv-title">No encontramos taller disponible</h1>
              <p>Por ahora no hay talleres cerca de tu ubicación. Intenta más tarde o indica otra ciudad.</p>
            </>
          )}
          {!["ASIGNADA_A_TALLER", "RECHAZADA"].includes(resultado.estado) && (
            <>
              <h1 className="sv-title">Recibimos tu solicitud</h1>
              <p>Estamos buscando el taller más cercano. Te avisaremos por correo en cuanto quede asignada.</p>
            </>
          )}
          <div className="ss-acciones">
            <button className="sv-btn" onClick={onVerServicios}>Ver mis servicios</button>
            {onVolver && <button className="sv-btn-outline" onClick={onVolver}>Volver al inicio</button>}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="sv-root">
      <div className="sv-header-top">
        <div>
          <span className="ss-eyebrow">Paso 1 de 5 · Diagnóstico</span>
          <h1 className="sv-title">Solicitar diagnóstico</h1>
          <p className="sv-subtitle">
            Cuéntanos qué le pasa a tu vehículo y elige un taller en el mapa, o deja que lo asignemos al más cercano. El taller lo revisa y te envía una cotización.
          </p>
        </div>
        {onVolver && <button className="sv-volver" onClick={onVolver}>← Volver</button>}
      </div>

      {tarifa != null && (
        <p className="ss-aviso">
          La revisión tiene una tarifa fija de <strong>{formatPrecio(tarifa)}</strong>. Se paga cuando el taller
          confirma la cita y se descuenta si aceptas la reparación.
        </p>
      )}

      <form className="ss-form" onSubmit={enviar} noValidate>
        <section className="ss-bloque">
          <h2 className="ss-bloque__titulo">Vehículo</h2>
          {vehiculos.length === 0 ? (
            <div className="ss-vacio">
              <p>Aún no tienes vehículos registrados.</p>
              <button type="button" className="sv-btn" onClick={onIrVehiculos}>Registrar mi vehículo</button>
            </div>
          ) : (
            <div className="ss-vehiculos" role="radiogroup" aria-label="Vehículo">
              {vehiculos.map((v) => (
                <label key={v.id} className={`ss-vehiculo${vehiculoId === v.id ? " ss-vehiculo--activo" : ""}`}>
                  <input type="radio" name="vehiculo" value={v.id} checked={vehiculoId === v.id}
                    onChange={() => setVehiculoId(v.id)} />
                  {v.imagenUrl
                    ? <img src={v.imagenUrl} alt="" loading="lazy" />
                    : <span className="ss-vehiculo__sinfoto" aria-hidden="true">🚗</span>}
                  <span className="ss-vehiculo__nombre">{v.marca} {v.modelo} {v.anio}</span>
                  <span className="ss-vehiculo__meta">{v.placa}{v.motor ? ` · ${v.motor}` : ""}</span>
                </label>
              ))}
            </div>
          )}
          {errores.vehiculo && <span className="ss-error">{errores.vehiculo}</span>}
        </section>

        <section className="ss-bloque">
          <h2 className="ss-bloque__titulo">¿Qué está pasando?</h2>
          <div className="sv-field">
            <label htmlFor="ss-sintomas">Síntomas o problema</label>
            <textarea id="ss-sintomas" rows={5} value={sintomas} maxLength={1500}
              onChange={(e) => setSintomas(e.target.value)}
              placeholder="Ej. Suena un chirrido al frenar y el pedal se siente blando desde hace una semana." />
            <span className="ss-ayuda">{sintomas.trim().length}/1500 · El taller hace el diagnóstico; describe lo que notas.</span>
            {errores.sintomas && <span className="ss-error">{errores.sintomas}</span>}
          </div>
          <div className="sv-field">
            <label>Fotos (opcional)</label>
            <SubidaFotos token={token} fotos={fotos} onChange={setFotos} max={5} />
          </div>
        </section>

        <section className="ss-bloque">
          <h2 className="ss-bloque__titulo">Ciudad y horario</h2>
          <div className="sv-form">
            <div className="sv-field">
              <label htmlFor="ss-ciudad">Ciudad</label>
              <input id="ss-ciudad" value={ciudad} onChange={(e) => setCiudad(e.target.value)} placeholder="Bogotá D.C." />
              {errores.ciudad && <span className="ss-error">{errores.ciudad}</span>}
            </div>
            <div className="sv-field">
              <label htmlFor="ss-barrio">Barrio (opcional)</label>
              <input id="ss-barrio" value={barrio} onChange={(e) => setBarrio(e.target.value)} placeholder="Chapinero" />
            </div>
            <div className="sv-field">
              <label htmlFor="ss-horario">Preferencia de horario</label>
              <select id="ss-horario" value={horario} onChange={(e) => setHorario(e.target.value)}>
                {HORARIOS.map((h) => <option key={h} value={h}>{h}</option>)}
              </select>
            </div>
            <div className="sv-field">
              <label htmlFor="ss-nota-horario">Detalle (opcional)</label>
              <input id="ss-nota-horario" value={notaHorario} maxLength={120}
                onChange={(e) => setNotaHorario(e.target.value)} placeholder="Ej. después de las 10 a. m." />
            </div>
          </div>
        </section>

        <section className="ss-bloque">
          <h2 className="ss-bloque__titulo">¿Dónde está el vehículo? Elige tu taller</h2>
          <p className="ss-ayuda ss-bloque__ayuda">
            Marca tu punto en el mapa (opcional) y te mostramos los talleres más cercanos primero. Toca un taller en el
            mapa o en la lista para ver sus datos, o deja que lo asignemos automáticamente.
          </p>
          <MapaTalleres
            id="ss-mapa"
            token={token}
            ciudad={ciudad}
            ubicacion={coordenadas}
            onUbicacion={setCoordenadas}
            onDireccion={recibirDireccion}
            valor={tallerId}
            onElegir={setTallerId}
          />
        </section>

        {error && <p className="ss-alerta" role="alert">{error}</p>}

        <div className="ss-acciones">
          <button type="submit" className="sv-btn" disabled={enviando || vehiculos.length === 0}>
            {enviando ? "Enviando..." : "Enviar solicitud"}
          </button>
        </div>
      </form>
    </div>
  );
}
