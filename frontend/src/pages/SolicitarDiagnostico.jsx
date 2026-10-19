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
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import TarjetaVehiculo from "../components/ui/TarjetaVehiculo";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Alerta from "../components/ui/Alerta";
import Campo from "../components/ui/Campo";
import BarraAccionMovil from "../components/ui/BarraAccionMovil";
import { formatPrecio } from "../components/servicio/estadosServicio";
import {
  MAX_SINTOMAS, MIN_SINTOMAS, SUGERENCIAS_SINTOMAS, alternarSugerencia, faltantes, tieneSugerencia,
} from "../components/servicio/solicitudDiagnostico";
import "../css/servicios.css";
import "../css/solicitud-servicio.css";
import "../css/marketplace.css";
import "../css/solicitar-diagnostico.css";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080";

const HORARIOS = ["Cualquier horario", "Mañanas entre semana", "Tardes entre semana", "Sábados"];

// Campo que recibe el foco cuando falta ese dato al enviar
const FOCO_CAMPO = { sintomas: "ss-sintomas", ciudad: "ss-ciudad" };

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
      ? (vehiculos[0] ? `vehiculo-${vehiculos[0].id}` : "sd-registrar-vehiculo")
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
      <div className="sv-root sd-pagina">
        <Esqueleto variante="formulario" cantidad={4} texto="Cargando el formulario…" />
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
            <button className="ui-btn ui-btn--primario" onClick={onVerServicios}>Ver mis servicios</button>
            {onVolver && <button className="ui-btn ui-btn--secundario" onClick={onVolver}>Volver al inicio</button>}
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
  const tarifaTexto = tarifa != null ? formatPrecio(tarifa) : "Por confirmar";

  const botonEnviar = (clase = "") => (
    <button type="submit" form="sd-form" className={`ui-btn ui-btn--primario ui-btn--grande ${clase}`}
      disabled={enviando || sinVehiculos} aria-busy={enviando}
      aria-describedby={pendientes.length ? "sd-faltan" : undefined}>
      {enviando ? "Enviando…" : "Enviar solicitud"}
      {!enviando && <Icon name="arrow" size={16} />}
    </button>
  );

  return (
    <div className="sv-root sd-pagina">
      <EncabezadoPagina
        titulo="Solicitar diagnóstico"
        descripcion="Cuéntanos qué le pasa a tu vehículo. Un taller cercano lo revisa y te envía una cotización."
        onVolver={onVolver}
      >
        <PasosServicio actual={1} />
      </EncabezadoPagina>

      <form id="sd-form" className="sd-grid" onSubmit={enviar} noValidate>
        <div className="sd-principal">
          <section className="sd-seccion" aria-labelledby="sd-t-vehiculo">
            <fieldset className="sd-fieldset" aria-describedby={errores.vehiculo ? "sd-e-vehiculo" : undefined}>
              <legend className="sd-seccion__titulo" id="sd-t-vehiculo">Tu vehículo</legend>
              {sinVehiculos ? (
                <EstadoVacio icono="car" titulo="Aún no tienes vehículos registrados" nivel={3}
                  texto="Regístralo una vez y lo eliges aquí cada vez que necesites un diagnóstico.">
                  <button type="button" id="sd-registrar-vehiculo" className="ui-btn ui-btn--primario" onClick={onIrVehiculos}>
                    Registrar mi vehículo
                  </button>
                </EstadoVacio>
              ) : (
                <div className="ui-vehiculos">
                  {vehiculos.map((v) => (
                    <TarjetaVehiculo key={v.id} vehiculo={v}
                      seleccion={{ name: "vehiculo", elegido: vehiculoId === v.id, onElegir: setVehiculoId,
                        describedBy: errores.vehiculo ? "sd-e-vehiculo" : undefined }} />
                  ))}
                  {onIrVehiculos && (
                    <button type="button" className="ui-vehiculo ui-vehiculo--agregar" onClick={onIrVehiculos}>
                      <Icon name="plus" size={22} />
                      <span>Agregar otro vehículo</span>
                    </button>
                  )}
                </div>
              )}
              {errores.vehiculo && <p className="sd-error" id="sd-e-vehiculo"><Icon name="alert" size={14} />{errores.vehiculo}</p>}
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
                  <button key={s.id} type="button" className="ui-chip" aria-pressed={activa}
                    onClick={() => setSintomas((t) => alternarSugerencia(t, s).slice(0, MAX_SINTOMAS))}>
                    <Icon name={activa ? "check" : "plus"} size={14} />
                    {s.etiqueta}
                  </button>
                );
              })}
            </div>

            <Campo id="ss-sintomas" label="Síntomas o problema" error={errores.sintomas}>
              <textarea
                className="ui-field__input"
                rows={5}
                value={sintomas}
                maxLength={MAX_SINTOMAS}
                onChange={(e) => setSintomas(e.target.value)}
                aria-describedby="sd-contador"
                placeholder="Ej. Suena un chirrido al frenar y el pedal se siente blando desde hace una semana."
              />
            </Campo>
            <div className="sd-campo__pie">
              <span id="sd-contador" className={`sd-contador${largoSintomas >= MIN_SINTOMAS ? " sd-contador--ok" : ""}`}>
                {largoSintomas >= MIN_SINTOMAS
                  ? <><Icon name="check" size={14} /> Listo</>
                  : `Mínimo ${MIN_SINTOMAS} caracteres`}
              </span>
              <span className="sd-contador__numero">{sintomas.length}/{MAX_SINTOMAS}</span>
            </div>

            <div className="sd-fotos">
              <span className="ui-field__label" id="sd-t-fotos">Fotos <span className="ui-field__opcional">opcional</span></span>
              <p className="sd-seccion__ayuda sd-seccion__ayuda--pegada">
                Una foto del tablero, de una fuga o de la pieza ayuda al taller a llegar preparado.
              </p>
              <SubidaFotos token={token} fotos={fotos} onChange={setFotos} max={5} />
            </div>
          </section>

          <section className="sd-seccion sd-seccion--mapa" aria-labelledby="sd-t-ubicacion">
            <h2 className="sd-seccion__titulo" id="sd-t-ubicacion">Ubicación y taller</h2>
            <div className="sd-fila">
              <Campo id="ss-ciudad" label="Ciudad" error={errores.ciudad}>
                <input className="ui-field__input" value={ciudad} onChange={(e) => setCiudad(e.target.value)}
                  placeholder="Bogotá D.C." autoComplete="address-level2" />
              </Campo>
              <Campo id="ss-barrio" label="Barrio" opcional>
                <input className="ui-field__input" value={barrio} onChange={(e) => setBarrio(e.target.value)} placeholder="Chapinero" />
              </Campo>
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
                  <label key={h} className={`ui-opcion${horario === h ? " ui-opcion--elegida" : ""}`}>
                    <input type="radio" name="horario" className="ui-sr" value={h} checked={horario === h}
                      onChange={() => setHorario(h)} />
                    {h}
                  </label>
                ))}
              </div>
              <Campo id="ss-nota-horario" label="Detalle" opcional className="sd-campo--corto">
                <input className="ui-field__input" value={notaHorario} maxLength={120}
                  onChange={(e) => setNotaHorario(e.target.value)} placeholder="Ej. después de las 10 a. m." />
              </Campo>
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
                  {vehiculo
                    ? <>{vehiculo.marca} {vehiculo.modelo} {vehiculo.anio}{vehiculo.placa && <span className="ui-placa ui-placa--chica">{vehiculo.placa}</span>}</>
                    : "Sin elegir"}
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
                <dd className="mk-resumen__fila"><strong className="ui-precio">{tarifaTexto}</strong></dd>
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
            {error && <Alerta className="sd-alerta sd-solo-escritorio">{error}</Alerta>}
            {botonEnviar("ui-btn--bloque sd-enviar sd-solo-escritorio")}
          </div>
        </aside>
      </form>

      {/* Celular: el total y el botón siempre a mano */}
      <BarraAccionMovil
        etiqueta="Diagnóstico"
        valor={tarifaTexto}
        nota={pendientes.length ? `Falta: ${pendientes.map((f) => f.resumen.toLowerCase()).join(", ")}` : "Se descuenta si aceptas la reparación"}
        alerta={error && <Alerta>{error}</Alerta>}
      >
        {botonEnviar()}
      </BarraAccionMovil>
    </div>
  );
}
