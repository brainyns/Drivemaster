import { useCallback, useRef, useState } from "react";
import MecanicosTaller from "../../components/servicio/MecanicosTaller";
import { formatFecha, formatPrecio } from "../../components/servicio/estadosServicio";
import { actualizarDatosTaller, actualizarUbicacionTaller, subirLogoTaller } from "../../services/tallerService";
import LogoNegocio from "../../components/ui/LogoNegocio";
import SelectorUbicacion from "../../components/mapa/SelectorUbicacion";
import DatosTallerCliente from "../../components/mapa/DatosTallerCliente";
import SeccionEditable from "../../components/ui/SeccionEditable";
import Campo from "../../components/ui/Campo";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import Calificacion from "../../components/ui/Calificacion";
import BarraProgreso from "../../components/ui/BarraProgreso";
import Icon from "../../components/public/Icon";
import { HORARIO_MAX, TIPOS_LOGO, comoLoVeElCliente, completitudPerfil, errorHorario, errorLogo, errorTarifa } from "./perfilTaller";
import "../../css/mi-taller.css";

const ADMIN = "Para cambiarlo, escríbele al administrador de DriveMaster.";

/** Un dato de solo lectura; sin valor muestra "Sin registrar" para que se note lo que falta. */
function Dato({ etiqueta, children }) {
  const vacio = children == null || children === "" || (Array.isArray(children) && children.length === 0);
  return (
    <div className="ui-datos__item">
      <dt>{etiqueta}</dt>
      <dd>{vacio ? <span className="ui-datos__falta">Sin registrar</span> : children}</dd>
    </div>
  );
}

/** Guardar y Cancelar de una sección; el error del servidor va encima. */
function PieFormulario({ guardando, error, onCancelar, textoGuardar = "Guardar cambios" }) {
  return (
    <>
      {error && <Alerta>{error}</Alerta>}
      <div className="ui-form-pie">
        <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
          {guardando ? "Guardando…" : textoGuardar}
        </button>
        <button type="button" className="ui-btn ui-btn--secundario" onClick={onCancelar} disabled={guardando}>Cancelar</button>
      </div>
    </>
  );
}

/** Formulario de la tarifa por hora: solo viaja ese campo (el horario queda como estaba). */
function FormTarifa({ taller, token, onGuardado, onCancelar }) {
  const [tarifa, setTarifa] = useState(taller?.tarifaHora != null ? String(taller.tarifaHora) : "");
  const [tocado, setTocado] = useState(false);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const errorCampo = tocado ? errorTarifa(tarifa) : null;

  const guardar = async (e) => {
    e.preventDefault();
    setTocado(true);
    if (errorTarifa(tarifa)) return;
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await actualizarDatosTaller(token, { tarifaHora: Number(tarifa) }));
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <form className="mt-form" onSubmit={guardar} noValidate>
      <Campo id="mt-tarifa" label="Tarifa por hora (COP)" error={errorCampo}
        ayuda="Se usa al cotizar por horas. Las cotizaciones que ya emitiste no cambian.">
        <input className="ui-field__input mt-form__corto" type="number" inputMode="numeric" min="1" step="any" autoFocus
          value={tarifa} onChange={(e) => setTarifa(e.target.value)} onBlur={() => setTocado(true)} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar tarifa" />
    </form>
  );
}

function FormHorario({ taller, token, onGuardado, onCancelar }) {
  const [horario, setHorario] = useState(taller?.horario ?? "");
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const errorCampo = errorHorario(horario);

  const guardar = async (e) => {
    e.preventDefault();
    if (errorCampo) return;
    if (horario.trim() === (taller?.horario ?? "").trim()) { onCancelar(); return; }
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await actualizarDatosTaller(token, { horario }));
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <form className="mt-form" onSubmit={guardar} noValidate>
      <Campo id="mt-horario" label="Horario de atención" error={errorCampo}
        ayuda={`Texto libre, una línea por día o franja. ${horario.length}/${HORARIO_MAX} caracteres.`}>
        <textarea className="ui-field__input" maxLength={HORARIO_MAX} rows={4} autoFocus value={horario}
          placeholder={"Lunes a viernes: 8:00 a. m. – 6:00 p. m.\nSábados: 8:00 a. m. – 1:00 p. m."}
          onChange={(e) => setHorario(e.target.value)} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar horario" />
    </form>
  );
}

/** El punto exacto del taller en el mapa: es el que usa la asignación por cercanía y el que ve el cliente. */
function FormUbicacion({ taller, token, onGuardado, onCancelar }) {
  const inicial = taller?.latitud != null && taller?.longitud != null
    ? { latitud: taller.latitud, longitud: taller.longitud } : null;
  const [punto, setPunto] = useState(inicial);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const cambio = punto && (punto.latitud !== inicial?.latitud || punto.longitud !== inicial?.longitud);

  const guardar = async (e) => {
    e.preventDefault();
    if (!punto) { setError("Marca el punto de tu taller en el mapa antes de guardar."); return; }
    if (!cambio) { onCancelar(); return; }
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await actualizarUbicacionTaller(token, punto));
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <form className="mt-form" onSubmit={guardar} noValidate>
      <SelectorUbicacion
        id="mt-mapa"
        ciudad={taller?.ciudad}
        value={punto}
        onChange={(p) => { setPunto(p); setError(null); }}
        direccionInicial={inicial && taller?.direccion ? taller.direccion : null}
        ayuda="Ajusta el pin a la entrada de tu taller: busca la dirección, usa tu ubicación o toca el mapa."
      />
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar ubicación" />
    </form>
  );
}

/**
 * Logo del taller con su botón para subirlo o cambiarlo (sin logo, las iniciales). Valida tipo y tamaño antes de
 * enviarlo; el backend vuelve a validar y solo deja cambiar el del taller de la sesión. onAviso recibe el resultado.
 */
function LogoTaller({ taller, token, onActualizado, onAviso }) {
  const input = useRef(null);
  const [subiendo, setSubiendo] = useState(false);

  const elegir = async (e) => {
    const archivo = e.target.files?.[0];
    e.target.value = "";
    if (!archivo) return;
    const error = errorLogo(archivo);
    if (error) { onAviso({ tono: "error", texto: error }); return; }
    setSubiendo(true);
    onAviso(null);
    try {
      const { logoUrl } = await subirLogoTaller(token, archivo);
      onActualizado?.({ ...taller, logoUrl });
      onAviso({ tono: "ok", texto: "Logo guardado. Así te verán los clientes en la lista y el mapa." });
    } catch (err) {
      onAviso({ tono: "error", texto: err.message });
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <div className="mt-logo">
      <LogoNegocio url={taller.logoUrl} nombre={taller.nombre} tamano="grande" />
      <button id="mt-logo" type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={subiendo}
        aria-busy={subiendo} onClick={() => input.current?.click()}>
        {!subiendo && <Icon name="camera" size={14} />}
        {subiendo ? "Subiendo…" : taller.logoUrl ? "Cambiar logo" : "Subir logo"}
      </button>
      <input ref={input} type="file" accept={TIPOS_LOGO.join(",")} hidden onChange={elegir} />
    </div>
  );
}

/**
 * "Así te ven los clientes": la misma información que muestra la lista de talleres al pedir un diagnóstico
 * (nombre, dirección, teléfono y si está en el mapa).
 */
function VistaCliente({ taller: datos }) {
  const taller = comoLoVeElCliente(datos);
  const sinPunto = taller.latitud == null || taller.longitud == null;
  return (
    <section className="mt-vista" aria-labelledby="mt-vista-titulo">
      <h2 id="mt-vista-titulo" className="mt-vista__titulo">Así te ven los clientes</h2>
      <p className="mt-vista__ayuda">Al pedir un diagnóstico, el cliente elige taller en esta lista y en el mapa.</p>
      <div className="mt-vista__marco" aria-hidden="true">
        <div className="mt-vista__item">
          <LogoNegocio url={taller.logoUrl} nombre={taller.nombre} className="mt-vista__logo" />
          <span className="mt-vista__nombre">{taller.nombre}</span>
          <span className="mt-vista__dato">
            {taller.direccion || "Sin dirección"}{taller.ciudad ? `, ${taller.ciudad}` : ""}
          </span>
          <DatosTallerCliente taller={taller} />
          {taller.telefono && <span className="mt-vista__dato">Tel. {taller.telefono}</span>}
          {sinPunto
            ? <span className="mt-vista__nota">Sin ubicación en el mapa</span>
            : <span className="mt-vista__distancia">12 min en carro · 3,4 km</span>}
          <span className="mt-vista__boton">Elegir</span>
        </div>
      </div>
      {sinPunto ? (
        <p className="mt-vista__consejo mt-vista__consejo--aviso">
          <Icon name="alert" size={16} /> Sin pin no apareces en el mapa ni te llegan solicitudes por cercanía.
        </p>
      ) : (
        <p className="mt-vista__consejo"><Icon name="pin" size={16} /> La distancia es un ejemplo: cada cliente ve la suya.</p>
      )}
    </section>
  );
}

/** Mi taller: identidad, qué tan completo está el perfil y cada sección editable por separado. */
export default function TallerMiTaller({ taller, token, cargando, error, onRecargar, onActualizado }) {
  const [editando, setEditando] = useState(null); // "servicios" | "horario" | "ubicacion"
  const [aviso, setAviso] = useState(null); // { seccion, texto }
  const [activos, setActivos] = useState(0);
  const [avisoLogo, setAvisoLogo] = useState(null); // { tono, texto }
  const onLista = useCallback((lista) => setActivos(lista.filter((m) => m.activo !== false).length), []);

  if (!taller) {
    if (cargando) return <Esqueleto variante="formulario" cantidad={4} texto="Cargando los datos de tu taller…" />;
    return (
      <Alerta onReintentar={onRecargar}>
        {error || "No pudimos cargar los datos de tu taller."} Revisa tu conexión e inténtalo de nuevo.
      </Alerta>
    );
  }

  const { porcentaje, faltan } = completitudPerfil(taller, activos);
  const aprobado = taller.estadoAprobacion === "APROBADO";
  const activo = taller.activo !== false;

  const abrir = (seccion) => { setAviso(null); setEditando(seccion); };
  const cerrar = () => setEditando(null);
  const guardado = (seccion, texto) => (t) => {
    onActualizado?.(t);
    setEditando(null);
    setAviso({ seccion, texto });
  };
  const avisoDe = (seccion) => aviso?.seccion === seccion && (
    <Alerta tono="ok" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>
  );
  // Desde "lo que falta": lleva a la sección y abre su edición (el equipo tiene su propio botón)
  const resolver = (item) => {
    const destino = document.getElementById(item.seccion);
    destino?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start" });
    if (item.seccion === "mt-servicios") abrir("servicios");
    else if (item.seccion === "mt-horario") abrir("horario");
    else if (item.seccion === "mt-ubicacion") abrir("ubicacion");
    else if (item.seccion === "mt-logo") destino?.focus({ preventScroll: true });
    else destino?.querySelector("button")?.focus({ preventScroll: true });
  };

  return (
    <div className="mt-root">
      <header className="mt-identidad">
        <div className="mt-identidad__marca">
          <LogoTaller taller={taller} token={token} onActualizado={onActualizado} onAviso={setAvisoLogo} />
          <div className="mt-identidad__texto">
            <h1 className="mt-identidad__nombre">{taller.nombre}</h1>
            <div className="mt-identidad__chips">
              <ChipEstado tono={aprobado ? "ok" : "aviso"}>{aprobado ? "Aprobado" : "Pendiente de aprobación"}</ChipEstado>
              <ChipEstado tono={activo ? "ok" : "apagado"}>{activo ? "Activo" : "Inactivo"}</ChipEstado>
              {taller.planActual && (
                <ChipEstado tono="acento">
                  Plan {taller.planActual.toLowerCase()}{taller.planVence ? ` hasta ${formatFecha(taller.planVence)}` : ""}
                </ChipEstado>
              )}
            </div>
            <p className="mt-identidad__meta">
              {/* Promedio real de las calificaciones de los clientes; sin ninguna, "Nuevo" */}
              <Calificacion rating={taller.rating} calificaciones={taller.calificaciones} />
              {(taller.barrio || taller.ciudad) && (
                <span><Icon name="pin" size={14} /> {[taller.barrio, taller.ciudad].filter(Boolean).join(", ")}</span>
              )}
              {(taller.fechaAprobacion || taller.fechaRegistro) && (
                <span>En DriveMaster desde {formatFecha(taller.fechaAprobacion || taller.fechaRegistro)}</span>
              )}
            </p>
          </div>
        </div>

        <div className="mt-completo">
          <p className="mt-completo__titulo">
            Perfil completo al <strong>{porcentaje} %</strong>
          </p>
          <BarraProgreso valor={porcentaje} etiqueta="Perfil completo" tono={porcentaje === 100 ? "ok" : "acento"} />
          {faltan.length === 0 ? (
            <p className="mt-completo__listo">Tienes todo lo que los clientes y la asignación necesitan.</p>
          ) : (
            <ul className="mt-completo__faltan" aria-label="Lo que falta">
              {faltan.map((f) => (
                <li key={f.clave}>
                  {f.seccion ? (
                    <button type="button" className="mt-completo__accion" onClick={() => resolver(f)}>
                      {f.texto} <Icon name="arrow" size={14} />
                    </button>
                  ) : (
                    <span className="mt-completo__admin">{f.texto}: lo registra el administrador</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
        {avisoLogo && (
          <Alerta tono={avisoLogo.tono} className="mt-identidad__aviso" onCerrar={() => setAvisoLogo(null)}>
            {avisoLogo.texto}
          </Alerta>
        )}
      </header>

      <div className="mt-rejilla">
        <div className="mt-secciones">
          <SeccionEditable id="mt-negocio" icono="file" titulo="Datos del negocio"
            descripcion="Con estos datos te identifican los clientes y DriveMaster." nota={ADMIN}>
            <dl className="ui-datos">
              <Dato etiqueta="Nombre del taller">{taller.nombre}</Dato>
              <Dato etiqueta="Ciudad">{taller.ciudad}</Dato>
              <Dato etiqueta="Fecha de registro">{taller.fechaRegistro && formatFecha(taller.fechaRegistro)}</Dato>
              <Dato etiqueta="Plan">{taller.planActual && `${taller.planActual.toLowerCase()}${taller.planVence ? `, vence el ${formatFecha(taller.planVence)}` : ""}`}</Dato>
            </dl>
          </SeccionEditable>

          <SeccionEditable id="mt-servicios" icono="tool" titulo="Servicios y precios"
            descripcion="Lo que ofreces y cuánto cobras por la mano de obra."
            onEditar={aprobado ? () => abrir("servicios") : undefined} textoEditar="Editar tarifa"
            editando={editando === "servicios"} aviso={avisoDe("servicios")}
            nota={editando !== "servicios" && "Los servicios que ofreces y el precio de mano de obra los cambia el administrador de DriveMaster."}>
            {editando === "servicios" ? (
              <FormTarifa taller={taller} token={token} onCancelar={cerrar}
                onGuardado={guardado("servicios", "Tarifa guardada. Las cotizaciones nuevas usan este valor.")} />
            ) : (
              <dl className="ui-datos">
                <div className="ui-datos__item mt-datos__ancho">
                  <dt>Servicios que ofreces</dt>
                  <dd>
                    {taller.especialidades?.length > 0 ? (
                      <ul className="mt-especialidades">
                        {taller.especialidades.map((e) => <li key={e}>{e}</li>)}
                      </ul>
                    ) : <span className="ui-datos__falta">Sin registrar</span>}
                  </dd>
                </div>
                <Dato etiqueta="Tarifa por hora">{taller.tarifaHora > 0 && <span className="ui-precio">{formatPrecio(taller.tarifaHora)}</span>}</Dato>
                <Dato etiqueta="Precio de mano de obra">{taller.precioManoObra != null && <span className="ui-precio">{formatPrecio(taller.precioManoObra)}</span>}</Dato>
              </dl>
            )}
          </SeccionEditable>

          <SeccionEditable id="mt-ubicacion" icono="pin" titulo="Ubicación"
            descripcion="Con este punto te asignamos las solicitudes cercanas y el cliente calcula cómo llegar."
            onEditar={aprobado ? () => abrir("ubicacion") : undefined}
            textoEditar={taller.latitud != null ? "Ajustar en el mapa" : "Marcar en el mapa"}
            editando={editando === "ubicacion"} aviso={avisoDe("ubicacion")}
            nota={editando !== "ubicacion" && "La dirección escrita y el barrio los cambia el administrador de DriveMaster."}>
            {editando === "ubicacion" ? (
              <FormUbicacion taller={taller} token={token} onCancelar={cerrar}
                onGuardado={guardado("ubicacion", "Ubicación guardada. Así te verán los clientes en el mapa.")} />
            ) : (
              <>
                <dl className="ui-datos">
                  <Dato etiqueta="Dirección">{taller.direccion}</Dato>
                  <Dato etiqueta="Barrio">{taller.barrio}</Dato>
                  <Dato etiqueta="Pin en el mapa">
                    {taller.latitud != null && taller.longitud != null
                      ? <ChipEstado tono="ok">Marcado</ChipEstado>
                      : <ChipEstado tono="aviso">Sin marcar</ChipEstado>}
                  </Dato>
                </dl>
                {taller.latitud == null && (
                  <Alerta tono="aviso">Sin pin no te llegan solicitudes por cercanía. Márcalo para empezar a recibirlas.</Alerta>
                )}
              </>
            )}
          </SeccionEditable>

          <SeccionEditable id="mt-horario" icono="clock" titulo="Horario de atención"
            descripcion="Ayuda a proponer citas que sí puedes cumplir."
            onEditar={aprobado ? () => abrir("horario") : undefined}
            textoEditar={taller.horario ? "Editar" : "Agregar horario"}
            editando={editando === "horario"} aviso={avisoDe("horario")}>
            {editando === "horario" ? (
              <FormHorario taller={taller} token={token} onCancelar={cerrar}
                onGuardado={guardado("horario", "Horario guardado.")} />
            ) : taller.horario ? (
              <p className="mt-horario">{taller.horario}</p>
            ) : (
              <p className="ui-datos__falta">Aún no escribes tu horario.</p>
            )}
          </SeccionEditable>

          <MecanicosTaller token={token} onLista={onLista} />

          <SeccionEditable id="mt-contacto" icono="phone" titulo="Contacto"
            descripcion="Por aquí te escriben DriveMaster y los clientes con cita aceptada." nota={ADMIN}>
            <dl className="ui-datos">
              <Dato etiqueta="Teléfono">{taller.telefono}</Dato>
              <Dato etiqueta="Correo de la cuenta">{taller.correoUsuario}</Dato>
            </dl>
          </SeccionEditable>
        </div>

        <aside className="mt-lateral">
          <VistaCliente taller={taller} />
        </aside>
      </div>
    </div>
  );
}
