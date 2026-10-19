import { useCallback, useEffect, useRef, useState } from "react";
import { getToken } from "../services/authService";
import { misVehiculos, crearVehiculo, subirImagenVehiculo, configurarMantenimiento } from "../services/vehiculoService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import TarjetaVehiculo from "../components/ui/TarjetaVehiculo";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Alerta from "../components/ui/Alerta";
import Campo from "../components/ui/Campo";
import ChipEstado from "../components/ui/ChipEstado";
import Icon from "../components/public/Icon";
import "../css/servicios.css";
import "../css/mis-vehiculos.css";

const VACIO = { marca: "", modelo: "", anio: "", motor: "", combustible: "Gasolina", placa: "", kilometraje: "" };
const COMBUSTIBLES = ["Gasolina", "Diésel", "Híbrido", "Eléctrico", "Gas"];
const ANIO_MAX = new Date().getFullYear() + 1;
const MAX_FOTO_MB = 5;

/** Errores del formulario de alta, en el orden de los campos. */
function validarVehiculo(f) {
  const e = {};
  if (!f.marca.trim()) e.marca = "Escribe la marca.";
  if (!f.modelo.trim()) e.modelo = "Escribe el modelo.";
  const anio = Number(f.anio);
  if (!f.anio) e.anio = "Escribe el año.";
  else if (!Number.isInteger(anio) || anio < 1950 || anio > ANIO_MAX) e.anio = `El año debe estar entre 1950 y ${ANIO_MAX}.`;
  if (!f.placa.trim()) e.placa = "Escribe la placa.";
  if (f.kilometraje !== "" && (Number(f.kilometraje) < 0 || !Number.isFinite(Number(f.kilometraje)))) e.kilometraje = "Kilometraje no válido.";
  return e;
}

/** Mantenimiento preventivo del vehículo: estado, intervalo y recordatorios por correo. */
function Mantenimiento({ vehiculo, alGuardar }) {
  const [editando, setEditando] = useState(false);
  const [intervalo, setIntervalo] = useState(vehiculo.intervaloMantenimientoKm ?? 5000);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");
  const idCampo = `mv-intervalo-${vehiculo.id}`;

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

  const faltan = vehiculo.kmProximoMantenimiento != null && vehiculo.kilometraje != null
    ? vehiculo.kmProximoMantenimiento - vehiculo.kilometraje
    : null;
  const intervaloValido = Number(intervalo) >= 500;

  return (
    <div className={`mv-manto${vehiculo.mantenimientoVencido ? " mv-manto--vencido" : ""}`}>
      <div className="mv-manto__cabecera">
        <span className="mv-manto__titulo">Mantenimiento</span>
        {vehiculo.mantenimientoVencido
          ? <ChipEstado tono="error">Vencido</ChipEstado>
          : faltan != null
            ? <ChipEstado tono={faltan <= 1000 ? "aviso" : "ok"}>En {faltan.toLocaleString("es-CO")} km</ChipEstado>
            : null}
      </div>

      {vehiculo.mantenimientoVencido && (
        <p className="mv-manto__texto">
          {vehiculo.ultimoRecordatorioEnviado
            ? `Te avisamos por correo el ${new Date(vehiculo.ultimoRecordatorioEnviado).toLocaleDateString("es-CO")}.`
            : "Te avisaremos por correo."}
        </p>
      )}

      {vehiculo.kmUltimoMantenimiento != null && (
        <p className="mv-manto__texto">
          Último: {vehiculo.kmUltimoMantenimiento.toLocaleString("es-CO")} km
          {vehiculo.kmProximoMantenimiento != null && ` · Próximo: ${vehiculo.kmProximoMantenimiento.toLocaleString("es-CO")} km`}
        </p>
      )}

      {editando ? (
        <div className="mv-manto__edicion">
          <Campo id={idCampo} label="Cada cuántos km" ayuda="Mínimo 500 km." error={!intervaloValido ? "Escribe 500 km o más." : null}>
            <input type="number" min="500" step="500" inputMode="numeric" className="ui-field__input"
              value={intervalo} onChange={(e) => setIntervalo(e.target.value)} />
          </Campo>
          <div className="mv-manto__acciones">
            <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" disabled={guardando || !intervaloValido}
              aria-busy={guardando} onClick={() => guardar({ intervaloKm: parseInt(intervalo, 10) })}>
              {guardando ? "Guardando…" : "Guardar"}
            </button>
            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={guardando} onClick={() => setEditando(false)}>
              Cancelar
            </button>
          </div>
        </div>
      ) : (
        <div className="mv-manto__acciones">
          <button type="button" className="ui-btn ui-btn--texto ui-btn--chico"
            onClick={() => { setIntervalo(vehiculo.intervaloMantenimientoKm ?? 5000); setEditando(true); }}>
            Cambiar intervalo
          </button>
          <label className="mv-interruptor">
            <input type="checkbox" role="switch" checked={!!vehiculo.recordatoriosActivos} disabled={guardando}
              onChange={() => guardar({ recordatoriosActivos: !vehiculo.recordatoriosActivos })} />
            <span className="mv-interruptor__pista" aria-hidden="true" />
            Recordatorios por correo
          </label>
        </div>
      )}

      {error && <p className="mv-manto__error" role="alert">{error}</p>}
    </div>
  );
}

/** Botón sobre la foto para cambiarla (la foto es lo único del vehículo que se puede editar después del alta). */
function CambiarFoto({ vehiculo, alCambiar }) {
  const input = useRef(null);
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState(null);

  const elegir = async (e) => {
    const archivo = e.target.files?.[0];
    e.target.value = "";
    if (!archivo) return;
    if (!archivo.type.startsWith("image/")) return setError("Elige una imagen.");
    if (archivo.size > MAX_FOTO_MB * 1024 * 1024) return setError(`La foto debe pesar menos de ${MAX_FOTO_MB} MB.`);
    setError(null);
    setSubiendo(true);
    try {
      await subirImagenVehiculo(getToken(), vehiculo.id, archivo);
      await alCambiar();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <>
      <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico mv-foto" onClick={() => input.current?.click()}
        disabled={subiendo} aria-busy={subiendo}
        aria-label={`${vehiculo.imagenUrl ? "Cambiar" : "Agregar"} la foto de ${vehiculo.marca} ${vehiculo.modelo}`}>
        {!subiendo && <Icon name="camera" size={14} />}
        {subiendo ? "Subiendo…" : vehiculo.imagenUrl ? "Cambiar foto" : "Agregar foto"}
      </button>
      <input ref={input} type="file" accept="image/*" hidden onChange={elegir} />
      {error && <span className="mv-foto__error" role="alert">{error}</span>}
    </>
  );
}

/** Alta de un vehículo: datos con su etiqueta, validación en línea y foto opcional. */
function FormularioVehiculo({ token, onGuardado, onCancelar }) {
  const [form, setForm] = useState(VACIO);
  const [intentado, setIntentado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const [foto, setFoto] = useState(null);
  const [vista, setVista] = useState(null);
  const inputFoto = useRef(null);
  const titulo = useRef(null);

  useEffect(() => { titulo.current?.focus(); }, []);
  useEffect(() => () => { if (vista) URL.revokeObjectURL(vista); }, [vista]);

  const errores = intentado ? validarVehiculo(form) : {};
  const cambiar = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

  const elegirFoto = (e) => {
    const archivo = e.target.files?.[0];
    if (!archivo) return;
    setFoto(archivo);
    setVista(URL.createObjectURL(archivo));
  };

  const quitarFoto = () => {
    setFoto(null);
    setVista(null);
    if (inputFoto.current) inputFoto.current.value = "";
  };

  const guardar = async (e) => {
    e.preventDefault();
    setIntentado(true);
    const faltan = validarVehiculo(form);
    const primero = Object.keys(faltan)[0];
    if (primero) {
      document.getElementById(`mv-${primero}`)?.focus();
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const creado = await crearVehiculo(token, {
        ...form,
        anio: form.anio ? parseInt(form.anio, 10) : null,
        kilometraje: form.kilometraje ? parseInt(form.kilometraje, 10) : null,
      });
      if (foto && creado?.id) await subirImagenVehiculo(token, creado.id, foto);
      await onGuardado();
    } catch (err) {
      setError(err.message);
    } finally {
      setGuardando(false);
    }
  };

  const campo = (nombre, label, props = {}, opcional = false) => (
    <Campo id={`mv-${nombre}`} label={label} error={errores[nombre]} opcional={opcional}>
      <input name={nombre} className="ui-field__input" value={form[nombre]} onChange={cambiar} {...props} />
    </Campo>
  );

  return (
    <section className="mv-formulario" aria-labelledby="mv-form-titulo">
      <h2 className="mv-formulario__titulo" id="mv-form-titulo" ref={titulo} tabIndex={-1}>Agregar vehículo</h2>
      <form onSubmit={guardar} noValidate>
        <div className="mv-formulario__rejilla">
          {campo("marca", "Marca", { placeholder: "Chevrolet", autoComplete: "off" })}
          {campo("modelo", "Modelo", { placeholder: "Spark GT", autoComplete: "off" })}
          {campo("anio", "Año", { type: "number", inputMode: "numeric", min: "1950", max: String(ANIO_MAX), placeholder: "2018" })}
          {campo("placa", "Placa", { placeholder: "ABC123", autoCapitalize: "characters", autoComplete: "off" })}
          {campo("motor", "Motor", { placeholder: "1.2" }, true)}
          <Campo id="mv-combustible" label="Combustible">
            <select name="combustible" className="ui-field__input" value={form.combustible} onChange={cambiar}>
              {COMBUSTIBLES.map((c) => <option key={c}>{c}</option>)}
            </select>
          </Campo>
          {campo("kilometraje", "Kilometraje", { type: "number", inputMode: "numeric", min: "0", placeholder: "45000" }, true)}
          <div className="mv-formulario__foto">
            <span className="ui-field__label">Foto <span className="ui-field__opcional">opcional</span></span>
            {vista ? (
              <div className="mv-vista">
                <img src={vista} alt="Vista previa del vehículo" />
                <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={quitarFoto}>Quitar foto</button>
              </div>
            ) : (
              <button type="button" className="ui-btn ui-btn--secundario" onClick={() => inputFoto.current?.click()}>
                <Icon name="camera" size={16} /> Elegir foto
              </button>
            )}
            <input ref={inputFoto} type="file" accept="image/*" hidden onChange={elegirFoto} />
          </div>
        </div>

        {error && <Alerta className="mv-formulario__alerta">{error}</Alerta>}

        <div className="mv-formulario__acciones">
          <button type="button" className="ui-btn ui-btn--secundario" onClick={onCancelar} disabled={guardando}>Cancelar</button>
          <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
            {guardando ? "Guardando…" : "Guardar vehículo"}
          </button>
        </div>
      </form>
    </section>
  );
}

export default function MisVehiculos({ onVolver, onVerServicio }) {
  const token = getToken();
  const [vehiculos, setVehiculos] = useState([]);
  const [estado, setEstado] = useState("cargando"); // cargando | listo | error
  const [error, setError] = useState(null);
  const [mostrarForm, setMostrarForm] = useState(false);
  const [aviso, setAviso] = useState(null);
  const botonAgregar = useRef(null);

  const [intento, setIntento] = useState(0);

  const aplicar = useCallback((lista) => {
    setVehiculos(lista || []);
    setError(null);
    setEstado("listo");
  }, []);
  const fallar = useCallback((e) => {
    setError(e.message);
    setEstado("error");
  }, []);

  // Carga inicial y "Reintentar"
  useEffect(() => {
    let vivo = true;
    misVehiculos(token)
      .then((l) => { if (vivo) aplicar(l); })
      .catch((e) => { if (vivo) fallar(e); });
    return () => { vivo = false; };
  }, [token, intento, aplicar, fallar]);

  // Recarga después de una acción (alta, foto, mantenimiento), sin volver a mostrar la carga
  const cargar = useCallback(() => misVehiculos(token).then(aplicar).catch(fallar), [token, aplicar, fallar]);

  const reintentar = () => {
    setEstado("cargando");
    setIntento((n) => n + 1);
  };

  const abrirFormulario = () => {
    setAviso(null);
    setMostrarForm(true);
  };

  const cerrarFormulario = () => {
    setMostrarForm(false);
    requestAnimationFrame(() => botonAgregar.current?.focus());
  };

  const guardado = async () => {
    setMostrarForm(false);
    setAviso("Vehículo agregado.");
    await cargar();
  };

  const vacio = estado === "listo" && vehiculos.length === 0;

  return (
    <div className="sv-root mv-pagina">
      <EncabezadoPagina
        titulo="Mis vehículos"
        descripcion="Tus vehículos, su mantenimiento y los repuestos compatibles con cada uno."
        onVolver={onVolver}
        textoVolver="Volver al catálogo"
        acciones={!vacio && !mostrarForm && estado === "listo" && (
          <button ref={botonAgregar} type="button" className="ui-btn ui-btn--primario" onClick={abrirFormulario}>
            <Icon name="plus" size={16} /> Agregar vehículo
          </button>
        )}
      />

      {aviso && <Alerta tono="ok" onCerrar={() => setAviso(null)} className="mv-aviso">{aviso}</Alerta>}

      {mostrarForm && <FormularioVehiculo token={token} onGuardado={guardado} onCancelar={cerrarFormulario} />}

      {estado === "cargando" && <Esqueleto variante="tarjetas" cantidad={3} texto="Cargando tus vehículos…" />}

      {estado === "error" && (
        <Alerta onReintentar={reintentar}>No pudimos cargar tus vehículos. {error}</Alerta>
      )}

      {vacio && !mostrarForm && (
        <EstadoVacio icono="car" titulo="Aún no tienes vehículos"
          texto="Agrégalo una vez y te mostramos los repuestos compatibles, su mantenimiento y lo eliges al pedir un diagnóstico.">
          <button ref={botonAgregar} type="button" className="ui-btn ui-btn--primario" onClick={abrirFormulario}>
            <Icon name="plus" size={16} /> Agregar mi primer vehículo
          </button>
        </EstadoVacio>
      )}

      {estado === "listo" && vehiculos.length > 0 && (
        <div className="ui-vehiculos mv-lista">
          {vehiculos.map((v) => (
            <TarjetaVehiculo key={v.id} vehiculo={v} nivelTitulo={2}
              accionMedia={<CambiarFoto vehiculo={v} alCambiar={cargar} />}>
              <Mantenimiento vehiculo={v} alGuardar={cargar} />
              <button type="button" className="ui-btn ui-btn--secundario ui-btn--bloque mv-ver" onClick={() => onVerServicio(v.id)}>
                Repuestos e historial <Icon name="arrow" size={16} />
              </button>
            </TarjetaVehiculo>
          ))}
        </div>
      )}
    </div>
  );
}
