import { useEffect, useState } from "react";
import { getToken } from "../services/authService";
import { misVehiculos } from "../services/vehiculoService";
import { obtenerMiPerfil } from "../services/clienteService";
import { crearSolicitudServicio } from "../services/solicitudServicioService";
import SubidaFotos from "../components/servicio/SubidaFotos";
import PasosServicio from "../components/servicio/PasosServicio";
import MapaTalleres from "../components/mapa/MapaTalleres";
import RutaTaller from "../components/mapa/RutaTaller";
import Icon from "../components/public/Icon";
import { formatPrecio } from "../components/servicio/estadosServicio";
import {
  MAX_SINTOMAS, MIN_SINTOMAS, SUGERENCIAS_SINTOMAS, alternarSugerencia, faltantes, formatoKm, formatoMotor,
  tieneSugerencia,
} from "../components/servicio/solicitudDiagnostico";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/marketplace.css";
import "../css/solicitar-diagnostico.css";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080";

const HORARIOS = ["Cualquier horario", "Mañanas entre semana", "Tardes entre semana", "Sábados"];

// Campo que recibe el foco cuando falta ese dato al enviar
const FOCO_CAMPO = { sintomas: "ss-sintomas", ciudad: "ss-ciudad" };

/** Tarjeta seleccionable de un vehículo: un radio nativo (flechas del teclado) con foto o imagen de respaldo. */
function TarjetaVehiculo({ vehiculo: v, elegido, onElegir, describedBy }) {
  const [fotoRota, setFotoRota] = useState(false);
  const datos = [formatoMotor(v.motor), v.combustible, formatoKm(v.kilometraje)].filter(Boolean);
  return (
    <label className={`sd-vehiculo${elegido ? " sd-vehiculo--elegido" : ""}`}>
      <input
        type="radio"
        name="vehiculo"
        id={`sd-vehiculo-${v.id}`}
        className="sd-sr"
        value={v.id}
        checked={elegido}
        onChange={() => onElegir(v.id)}
        aria-describedby={describedBy}
      />
      <span className="sd-vehiculo__media">
        {v.imagenUrl && !fotoRota
          ? <img src={v.imagenUrl} alt="" loading="lazy" onError={() => setFotoRota(true)} />
          : (
            <span className="sd-vehiculo__respaldo" aria-hidden="true">
              <Icon name="car" size={34} />
              <span>{v.marca}</span>
            </span>
          )}
        <span className="sd-vehiculo__check" aria-hidden="true"><Icon name="check" size={14} /></span>
      </span>
      <span className="sd-vehiculo__cuerpo">
        <span className="sd-vehiculo__nombre">{v.marca} {v.modelo} <span className="sd-vehiculo__anio">{v.anio}</span></span>
        {v.placa && <span className="sd-placa"><span className="sd-sr">Placa </span>{v.placa}</span>}
        {datos.length > 0 && <span className="sd-vehiculo__datos">{datos.join(" · ")}</span>}
      </span>
    </label>
  );
}

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
  const [tallerNombre, setTallerNombre] = useState(null);

  // Los errores en línea se muestran después del primer intento de envío y se actualizan al corregir
  const [intentado, setIntentado] = useState(false);
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

  const elegirTaller = (id, nombre) => {
    setTallerId(id);
    setTallerNombre(id ? nombre : null);
  };

  const pendientes = faltantes({ vehiculoId, sintomas, ciudad });
  const errores = intentado ? Object.fromEntries(pendientes.map((f) => [f.campo, f.error])) : {};

  const enfocar = (campo) => {
    const id = campo === "vehiculo"
      ? (vehiculos[0] ? `sd-vehiculo-${vehiculos[0].id}` : "sd-registrar-vehiculo")
      : FOCO_CAMPO[campo];
    const el = document.getElementById(id);
    el?.focus({ preventScroll: true });
    el?.closest("section")?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const enviar = async (ev) => {
    ev.preventDefault();
    setError(null);
    setIntentado(true);
    if (pendientes.length) {
      enfocar(pendientes[0].campo);
      return;
    }

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

  if (cargando) {
    return (
      <div className="sv-root sd-pagina" aria-busy="true">
        <div className="sd-esqueleto" aria-hidden="true">
          <span /><span /><span />
        </div>
        <p className="sd-sr" role="status">Cargando el formulario…</p>
      </div>
    );
  }

  if (resultado) {
    return (
      <div className="sv-root">
        <div className="ss-confirmacion">
          {resultado.estado === "ASIGNADA_A_TALLER" && (
            <>
              <h1 className="sv-title">Tu solicitud ya tiene taller</h1>
              <p className="sd-codigo">Solicitud #{resultado.id?.slice(-6).toUpperCase()}</p>
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
              <p className="sd-codigo">Solicitud #{resultado.id?.slice(-6).toUpperCase()}</p>
              <p>Por ahora no hay talleres cerca de tu ubicación. Intenta más tarde o indica otra ciudad.</p>
            </>
          )}
          {!["ASIGNADA_A_TALLER", "RECHAZADA"].includes(resultado.estado) && (
            <>
              <h1 className="sv-title">Recibimos tu solicitud</h1>
              <p className="sd-codigo">Solicitud #{resultado.id?.slice(-6).toUpperCase()}</p>
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

  const vehiculo = vehiculos.find((v) => v.id === vehiculoId) || null;
  const largoSintomas = sintomas.trim().length;
  const ubicacionTexto = coordenadas && direccion ? direccion : [barrio.trim(), ciudad.trim()].filter(Boolean).join(", ");
  const horarioTexto = notaHorario.trim() ? `${horario} — ${notaHorario.trim()}` : horario;
  const sinVehiculos = vehiculos.length === 0;

  const botonEnviar = (clase) => (
    <button type="submit" form="sd-form" className={`sv-btn sd-enviar ${clase}`} disabled={enviando || sinVehiculos}
      aria-busy={enviando} aria-describedby={pendientes.length ? "sd-faltan" : undefined}>
      {enviando ? "Enviando…" : "Enviar solicitud"}
      {!enviando && <Icon name="arrow" size={16} />}
    </button>
  );

  return (
    <div className="sv-root sd-pagina">
      <header className="sd-cabecera">
        {onVolver && (
          <button type="button" className="sd-volver" onClick={onVolver}>
            <Icon name="arrow" size={16} className="sd-volver__icono" />
            Volver
          </button>
        )}
        <h1 className="sv-title sd-titulo">Solicitar diagnóstico</h1>
        <p className="sv-subtitle sd-subtitulo">
          Cuéntanos qué le pasa a tu vehículo. Un taller cercano lo revisa y te envía una cotización.
        </p>
        <PasosServicio actual={1} />
      </header>

      <form id="sd-form" className="sd-grid" onSubmit={enviar} noValidate>
        <div className="sd-principal">
          <section className="sd-seccion" aria-labelledby="sd-t-vehiculo">
            <fieldset className="sd-fieldset" aria-describedby={errores.vehiculo ? "sd-e-vehiculo" : undefined}>
              <legend className="sd-seccion__titulo" id="sd-t-vehiculo">Tu vehículo</legend>
              {sinVehiculos ? (
                <div className="sd-vacio">
                  <span className="sd-vacio__icono" aria-hidden="true"><Icon name="car" size={28} /></span>
                  <p><strong>Aún no tienes vehículos registrados.</strong></p>
                  <p className="sd-ayuda">Regístralo una vez y lo eliges aquí cada vez que necesites un diagnóstico.</p>
                  <button type="button" id="sd-registrar-vehiculo" className="sv-btn" onClick={onIrVehiculos}>
                    Registrar mi vehículo
                  </button>
                </div>
              ) : (
                <div className="sd-vehiculos">
                  {vehiculos.map((v) => (
                    <TarjetaVehiculo key={v.id} vehiculo={v} elegido={vehiculoId === v.id} onElegir={setVehiculoId}
                      describedBy={errores.vehiculo ? "sd-e-vehiculo" : undefined} />
                  ))}
                  {onIrVehiculos && (
                    <button type="button" className="sd-vehiculo sd-vehiculo--agregar" onClick={onIrVehiculos}>
                      <Icon name="plus" size={22} />
                      <span>Agregar otro vehículo</span>
                    </button>
                  )}
                </div>
              )}
              {errores.vehiculo && <p className="sd-error" id="sd-e-vehiculo">{errores.vehiculo}</p>}
            </fieldset>
          </section>

          <section className="sd-seccion" aria-labelledby="sd-t-problema">
            <h2 className="sd-seccion__titulo" id="sd-t-problema">¿Qué está pasando?</h2>
            <p className="sd-seccion__ayuda">
              Describe lo que notas, desde cuándo y cuándo pasa (al frenar, en frío, a cierta velocidad). El taller hace el diagnóstico.
            </p>

            <div className="sd-sugerencias" role="group" aria-label="Síntomas comunes">
              {SUGERENCIAS_SINTOMAS.map((s) => {
                const activa = tieneSugerencia(sintomas, s);
                return (
                  <button key={s.id} type="button" className={`sd-chip${activa ? " sd-chip--activa" : ""}`}
                    aria-pressed={activa} onClick={() => setSintomas((t) => alternarSugerencia(t, s).slice(0, MAX_SINTOMAS))}>
                    {activa ? <Icon name="check" size={14} /> : <Icon name="plus" size={14} />}
                    {s.etiqueta}
                  </button>
                );
              })}
            </div>

            <div className="sd-campo">
              <label htmlFor="ss-sintomas" className="sd-label">Síntomas o problema</label>
              <textarea
                id="ss-sintomas"
                className={`sd-textarea${errores.sintomas ? " sd-control--error" : ""}`}
                rows={5}
                value={sintomas}
                maxLength={MAX_SINTOMAS}
                onChange={(e) => setSintomas(e.target.value)}
                aria-invalid={errores.sintomas ? true : undefined}
                aria-describedby={`sd-contador${errores.sintomas ? " sd-e-sintomas" : ""}`}
                placeholder="Ej. Suena un chirrido al frenar y el pedal se siente blando desde hace una semana."
              />
              <div className="sd-campo__pie">
                <span id="sd-contador" className={`sd-contador${largoSintomas >= MIN_SINTOMAS ? " sd-contador--ok" : ""}`}>
                  {largoSintomas >= MIN_SINTOMAS
                    ? <><Icon name="check" size={14} /> Listo</>
                    : `Mínimo ${MIN_SINTOMAS} caracteres`}
                </span>
                <span className="sd-contador__numero">{sintomas.length}/{MAX_SINTOMAS}</span>
              </div>
              {errores.sintomas && <p className="sd-error" id="sd-e-sintomas">{errores.sintomas}</p>}
            </div>

            <div className="sd-campo">
              <span className="sd-label" id="sd-t-fotos">Fotos <span className="sd-opcional">opcional</span></span>
              <p className="sd-ayuda">Una foto del tablero, de una fuga o de la pieza ayuda al taller a llegar preparado.</p>
              <SubidaFotos token={token} fotos={fotos} onChange={setFotos} max={5} />
            </div>
          </section>

          <section className="sd-seccion sd-seccion--mapa" aria-labelledby="sd-t-ubicacion">
            <h2 className="sd-seccion__titulo" id="sd-t-ubicacion">Ubicación y taller</h2>
            <div className="sd-fila">
              <div className="sd-campo">
                <label htmlFor="ss-ciudad" className="sd-label">Ciudad</label>
                <input id="ss-ciudad" className={`sd-input${errores.ciudad ? " sd-control--error" : ""}`} value={ciudad}
                  onChange={(e) => setCiudad(e.target.value)} placeholder="Bogotá D.C." autoComplete="address-level2"
                  aria-invalid={errores.ciudad ? true : undefined} aria-describedby={errores.ciudad ? "sd-e-ciudad" : undefined} />
                {errores.ciudad && <p className="sd-error" id="sd-e-ciudad">{errores.ciudad}</p>}
              </div>
              <div className="sd-campo">
                <label htmlFor="ss-barrio" className="sd-label">Barrio <span className="sd-opcional">opcional</span></label>
                <input id="ss-barrio" className="sd-input" value={barrio} onChange={(e) => setBarrio(e.target.value)}
                  placeholder="Chapinero" />
              </div>
            </div>
            <MapaTalleres
              id="ss-mapa"
              token={token}
              ciudad={ciudad}
              ubicacion={coordenadas}
              onUbicacion={setCoordenadas}
              onDireccion={recibirDireccion}
              valor={tallerId}
              onElegir={elegirTaller}
            />
          </section>

          <section className="sd-seccion" aria-labelledby="sd-t-horario">
            <fieldset className="sd-fieldset">
              <legend className="sd-seccion__titulo" id="sd-t-horario">¿Cuándo te queda mejor?</legend>
              <p className="sd-seccion__ayuda">El taller te propone la cita según tu preferencia.</p>
              <div className="sd-opciones">
                {HORARIOS.map((h) => (
                  <label key={h} className={`sd-opcion${horario === h ? " sd-opcion--elegida" : ""}`}>
                    <input type="radio" name="horario" className="sd-sr" value={h} checked={horario === h}
                      onChange={() => setHorario(h)} />
                    {h}
                  </label>
                ))}
              </div>
              <div className="sd-campo sd-campo--corto">
                <label htmlFor="ss-nota-horario" className="sd-label">Detalle <span className="sd-opcional">opcional</span></label>
                <input id="ss-nota-horario" className="sd-input" value={notaHorario} maxLength={120}
                  onChange={(e) => setNotaHorario(e.target.value)} placeholder="Ej. después de las 10 a. m." />
              </div>
            </fieldset>
          </section>
        </div>

        <aside className="sd-lateral" aria-labelledby="sd-resumen-titulo">
          <div className="mk-resumen sd-resumen">
            <h2 id="sd-resumen-titulo" className="sd-resumen__titulo">Resumen</h2>
            <dl className="sd-resumen__datos">
              <div className="mk-resumen__grupo">
                <dt className="mk-resumen__detalle">Vehículo</dt>
                <dd className={vehiculo ? "" : "sd-resumen__vacio"}>
                  {vehiculo ? <>{vehiculo.marca} {vehiculo.modelo} {vehiculo.anio}{vehiculo.placa && <span className="sd-placa sd-placa--chica">{vehiculo.placa}</span>}</> : "Sin elegir"}
                </dd>
              </div>
              <div className="mk-resumen__grupo">
                <dt className="mk-resumen__detalle">Taller</dt>
                <dd>{tallerNombre || "Asignación automática al más cercano"}</dd>
              </div>
              <div className="mk-resumen__grupo">
                <dt className="mk-resumen__detalle">Ubicación</dt>
                <dd className={ubicacionTexto ? "" : "sd-resumen__vacio"}>{ubicacionTexto || "Sin indicar"}</dd>
              </div>
              <div className="mk-resumen__grupo">
                <dt className="mk-resumen__detalle">Horario</dt>
                <dd>{horarioTexto}</dd>
              </div>
              <div className="mk-resumen__grupo sd-resumen__tarifa">
                <dt className="mk-resumen__fila">Tarifa de diagnóstico</dt>
                <dd className="mk-resumen__fila">
                  <strong>{tarifa != null ? formatPrecio(tarifa) : "Por confirmar"}</strong>
                </dd>
              </div>
            </dl>
            <p className="mk-resumen__nota sd-resumen__explicacion">
              Se paga cuando el taller confirma la cita y se descuenta del total si aceptas la reparación.
            </p>

            <div className="sd-resumen__estado" aria-live="polite">
              {pendientes.length > 0 ? (
                <div id="sd-faltan">
                  <p className="sd-resumen__faltan-titulo">Para enviar te falta:</p>
                  <ul className="sd-faltan">
                    {pendientes.map((f) => (
                      <li key={f.campo}>
                        <button type="button" className="sd-faltan__ir" onClick={() => enfocar(f.campo)}>{f.resumen}</button>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="sd-listo"><Icon name="check" size={16} /> Todo listo para enviar</p>
              )}
            </div>

            {/* El error se muestra junto al botón visible: aquí en escritorio, en la barra inferior en celular
                (display: none saca la copia oculta del árbol de accesibilidad, así se anuncia una sola vez) */}
            {error && <p className="ss-alerta sd-alerta sd-alerta--lateral" role="alert">{error}</p>}
            {botonEnviar("sd-enviar--lateral")}
          </div>
        </aside>
      </form>

      {/* Celular: el total y el botón siempre a mano */}
      <div className="sd-barra-movil">
        {error && <p className="ss-alerta sd-alerta sd-alerta--movil" role="alert">{error}</p>}
        <div className="sd-barra-movil__total">
          <span className="sd-barra-movil__etiqueta">Diagnóstico</span>
          <strong>{tarifa != null ? formatPrecio(tarifa) : "Por confirmar"}</strong>
          <span className="sd-barra-movil__nota">
            {pendientes.length ? `Falta: ${pendientes.map((f) => f.resumen.toLowerCase()).join(", ")}` : "Se descuenta si aceptas la reparación"}
          </span>
        </div>
        {botonEnviar("sd-enviar--movil")}
      </div>
    </div>
  );
}
