import { useCallback, useEffect, useState } from "react";
import MecanicosTaller from "../../components/servicio/MecanicosTaller";
import HorarioSemanal, { HorarioResumen } from "../../components/servicio/HorarioSemanal";
import { formatFecha, formatPrecio } from "../../components/servicio/estadosServicio";
import {
  actualizarDatosTaller, actualizarUbicacionTaller, guardarTelefonoTaller, miTaller, subirLogoTaller,
} from "../../services/tallerService";
import { guardarHorario, obtenerHorario } from "../../services/horarioService";
import {
  copiarLunesAHabiles, errorDia, franjasDesdeSemana, semanaDesdeFranjas, semanaPorDefecto,
} from "./horarioTaller";
import LogoNegocio from "../../components/ui/LogoNegocio";
import SelectorUbicacion from "../../components/mapa/SelectorUbicacion";
import DatosTallerCliente from "../../components/mapa/DatosTallerCliente";
import SeccionEditable from "../../components/ui/SeccionEditable";
import SeccionContacto from "../../components/ui/SeccionContacto";
import Campo from "../../components/ui/Campo";
import ChipEstado from "../../components/ui/ChipEstado";
import Alerta from "../../components/ui/Alerta";
import Esqueleto from "../../components/ui/Esqueleto";
import Calificacion from "../../components/ui/Calificacion";
import CabeceraNegocio from "../../components/ui/CabeceraNegocio";
import VistaPrevia from "../../components/ui/VistaPrevia";
import Dato from "../../components/ui/Dato";
import PieFormulario from "../../components/ui/PieFormulario";
import Icon from "../../components/public/Icon";
import { comoLoVeElCliente, completitudPerfil, errorTarifa } from "./perfilTaller";
import LogoEditable from "../../components/ui/LogoEditable";
import "../../css/mi-taller.css";

const ADMIN = "Para cambiarlo, escríbele al administrador de DriveMaster.";

/** Régimen de IVA del taller: decide el IVA de su mano de obra y su diagnóstico (los repuestos llevan el IVA vigente). */
const REGIMENES_IVA = [
  { valor: true, titulo: "Responsable de IVA", detalle: "Tu mano de obra y el diagnóstico llevan el IVA vigente." },
  { valor: false, titulo: "No responsable de IVA", detalle: "Tu mano de obra y el diagnóstico van al 0 %." },
];

const textoRegimenIva = (responsable) => (responsable === false ? "No responsable de IVA (0 %)" : "Responsable de IVA");

/**
 * Formulario de la tarifa por hora y el régimen de IVA: solo viajan esos dos campos (el horario queda como estaba).
 * Sin tarifa todavía, se puede guardar solo el IVA.
 */
function FormTarifa({ taller, token, onGuardado, onCancelar }) {
  const [tarifa, setTarifa] = useState(taller?.tarifaHora != null ? String(taller.tarifaHora) : "");
  const [responsableIva, setResponsableIva] = useState(taller?.responsableIva !== false);
  const [tocado, setTocado] = useState(false);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const sinTarifa = taller?.tarifaHora == null && String(tarifa).trim() === "";
  const errorCampo = tocado && !sinTarifa ? errorTarifa(tarifa) : null;

  const guardar = async (e) => {
    e.preventDefault();
    setTocado(true);
    if (!sinTarifa && errorTarifa(tarifa)) return;
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await actualizarDatosTaller(token, { tarifaHora: sinTarifa ? undefined : Number(tarifa), responsableIva }));
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form" onSubmit={guardar} noValidate>
      <Campo id="mt-tarifa" label="Tarifa por hora (COP)" error={errorCampo}
        ayuda="Se usa al cotizar por horas. Las cotizaciones que ya emitiste no cambian.">
        <input className="ui-field__input ui-form__corto" type="number" inputMode="numeric" min="1" step="any" autoFocus
          value={tarifa} onChange={(e) => setTarifa(e.target.value)} onBlur={() => setTocado(true)} />
      </Campo>
      <fieldset className="mt-iva">
        <legend className="ui-field__label">IVA de tu mano de obra</legend>
        {REGIMENES_IVA.map((r) => (
          <label key={String(r.valor)} className="mt-iva__opcion">
            <input type="radio" name="mt-iva" checked={responsableIva === r.valor} onChange={() => setResponsableIva(r.valor)} />
            <span><strong>{r.titulo}</strong><span className="mt-iva__detalle">{r.detalle}</span></span>
          </label>
        ))}
        <p className="mt-iva__nota">Los repuestos siempre llevan el IVA vigente. Las cotizaciones que ya emitiste no cambian.</p>
      </fieldset>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar" />
    </form>
  );
}

/**
 * Horario por días: abierto o cerrado y sus franjas. Se guarda completo (PUT); el backend regenera el texto que ve el
 * cliente, por eso al terminar se vuelven a pedir los datos del taller.
 */
function FormHorario({ taller, token, semanaInicial, onGuardado, onCancelar }) {
  const [semana, setSemana] = useState(() => semanaInicial || semanaPorDefecto());
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const conErrores = semana.some((d) => errorDia(d));

  const guardar = async (e) => {
    e.preventDefault();
    if (conErrores) { setError("Revisa los días marcados antes de guardar."); return; }
    setGuardando(true);
    setError(null);
    try {
      const guardada = semanaDesdeFranjas(await guardarHorario(token, taller.id, franjasDesdeSemana(semana)));
      onGuardado(guardada, await miTaller(token).catch(() => taller));
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <form className="ui-form" onSubmit={guardar} noValidate>
      {!semanaInicial && taller?.horario && (
        <Alerta tono="info">
          Antes escribiste: <q>{taller.horario}</q>. Te proponemos un horario por días; ajústalo para que coincida.
        </Alerta>
      )}
      <div className="mt-horario__barra">
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico"
          onClick={() => { setSemana(copiarLunesAHabiles(semana)); setError(null); }}>
          Copiar lunes a todos los días hábiles
        </button>
      </div>
      <HorarioSemanal semana={semana} onCambiar={(s) => { setSemana(s); setError(null); }} />
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
    <form className="ui-form" onSubmit={guardar} noValidate>
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
 * "Así te ven los clientes": la misma información que muestra la lista de talleres al pedir un diagnóstico
 * (nombre, dirección, teléfono y si está en el mapa).
 */
function VistaCliente({ taller: datos }) {
  const taller = comoLoVeElCliente(datos);
  const sinPunto = taller.latitud == null || taller.longitud == null;
  return (
    <VistaPrevia id="mt-vista" ayuda="Al pedir un diagnóstico, el cliente elige taller en esta lista y en el mapa."
      tonoConsejo={sinPunto ? "aviso" : undefined}
      consejo={sinPunto
        ? "Sin pin no apareces en el mapa ni te llegan solicitudes por cercanía."
        : "La distancia es un ejemplo: cada cliente ve la suya."}>
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
    </VistaPrevia>
  );
}

/** Mi taller: identidad, qué tan completo está el perfil y cada sección editable por separado. */
export default function TallerMiTaller({ taller, token, cargando, error, onRecargar, onActualizado, abrirSeccion = null }) {
  // Desde el aviso del Inicio llega abrirSeccion="horario": la sección se abre ya en edición
  const [editando, setEditando] = useState(abrirSeccion === "horario" ? "horario" : null); // "servicios" | "horario" | "ubicacion"
  const [aviso, setAviso] = useState(null); // { seccion, texto }
  const [activos, setActivos] = useState(0);
  const [avisoLogo, setAvisoLogo] = useState(null); // { tono, texto }
  const onLista = useCallback((lista) => setActivos(lista.filter((m) => m.activo !== false).length), []);
  // Horario por días: undefined mientras carga, null si el taller aún no lo configura
  const [semana, setSemana] = useState(undefined);
  const tallerId = taller?.id;

  useEffect(() => {
    if (!tallerId) return undefined;
    let vivo = true;
    obtenerHorario(token, tallerId)
      .then((filas) => { if (vivo) setSemana(semanaDesdeFranjas(filas)); })
      .catch(() => { if (vivo) setSemana(null); });
    return () => { vivo = false; };
  }, [token, tallerId]);

  useEffect(() => {
    if (abrirSeccion === "horario" && tallerId) document.getElementById("mt-horario")?.scrollIntoView({ block: "start" });
  }, [abrirSeccion, tallerId]);

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
    <div className="ui-perfil">
      <CabeceraNegocio
        nombre={taller.nombre}
        logo={(
          <LogoEditable id="mt-logo" url={taller.logoUrl} nombre={taller.nombre} onAviso={setAvisoLogo}
            subir={(archivo) => subirLogoTaller(token, archivo)} onSubido={(logoUrl) => onActualizado?.({ ...taller, logoUrl })} />
        )}
        porcentaje={porcentaje} faltan={faltan} onResolver={resolver}
        textoListo="Tienes todo lo que los clientes y la asignación necesitan."
        aviso={avisoLogo && (
          <Alerta tono={avisoLogo.tono} onCerrar={() => setAvisoLogo(null)}>{avisoLogo.texto}</Alerta>
        )}
        chips={(
          <>
              <ChipEstado tono={aprobado ? "ok" : "aviso"}>{aprobado ? "Aprobado" : "Pendiente de aprobación"}</ChipEstado>
              <ChipEstado tono={activo ? "ok" : "apagado"}>{activo ? "Activo" : "Inactivo"}</ChipEstado>
              {taller.planActual && (
                <ChipEstado tono="acento">
                  Plan {taller.planActual.toLowerCase()}{taller.planVence ? ` hasta ${formatFecha(taller.planVence)}` : ""}
                </ChipEstado>
              )}
          </>
        )}
        meta={(
          <>
              {/* Promedio real de las calificaciones de los clientes; sin ninguna, "Nuevo" */}
              <Calificacion rating={taller.rating} calificaciones={taller.calificaciones} />
              {(taller.barrio || taller.ciudad) && (
                <span><Icon name="pin" size={14} /> {[taller.barrio, taller.ciudad].filter(Boolean).join(", ")}</span>
              )}
              {(taller.fechaAprobacion || taller.fechaRegistro) && (
                <span>En DriveMaster desde {formatFecha(taller.fechaAprobacion || taller.fechaRegistro)}</span>
              )}
          </>
        )}
      />

      <div className="ui-perfil__rejilla">
        <div className="ui-perfil__secciones">
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
                onGuardado={guardado("servicios", "Cambios guardados. Las cotizaciones nuevas usan estos valores.")} />
            ) : (
              <dl className="ui-datos">
                <div className="ui-datos__item ui-datos__item--ancho">
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
                <Dato etiqueta="IVA de tu mano de obra">{textoRegimenIva(taller.responsableIva)}</Dato>
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
            textoEditar={semana || taller.horario ? "Editar" : "Agregar horario"}
            editando={editando === "horario"} aviso={avisoDe("horario")}>
            {editando === "horario" && semana === undefined ? (
              <Esqueleto variante="formulario" cantidad={2} texto="Cargando tu horario…" />
            ) : editando === "horario" ? (
              <FormHorario taller={taller} token={token} semanaInicial={semana} onCancelar={cerrar}
                onGuardado={(guardada, t) => {
                  setSemana(guardada);
                  guardado("horario", "Horario guardado. Las citas nuevas solo se podrán agendar dentro de él.")(t);
                }} />
            ) : semana ? (
              <HorarioResumen semana={semana} />
            ) : taller.horario ? (
              <>
                <p className="ui-texto-pre">{taller.horario}</p>
                {semana === null && (
                  <p className="ui-datos__falta">Configúralo por días para que las citas respeten tu horario.</p>
                )}
              </>
            ) : (
              <p className="ui-datos__falta">Aún no configuras tu horario.</p>
            )}
          </SeccionEditable>

          <MecanicosTaller token={token} onLista={onLista} />

          <SeccionContacto id="mt-contacto"
            descripcion="Por aquí te escriben DriveMaster y los clientes con cita aceptada."
            telefono={taller.telefono} correo={taller.correoUsuario}
            ayudaTelefono="Celular o fijo. Lo ven los clientes con cita aceptada."
            guardarTelefono={(numero) => guardarTelefonoTaller(token, numero)}
            onTelefonoGuardado={guardado("contacto", "Teléfono guardado.")}
            editandoTelefono={editando === "contacto"} onEditarTelefono={() => abrir("contacto")} onCerrarTelefono={cerrar}
            aviso={avisoDe("contacto")}
            onCorreoCambiado={(correoUsuario) => onActualizado?.({ ...taller, correoUsuario })} />
        </div>

        <aside className="ui-perfil__lateral">
          <VistaCliente taller={taller} />
        </aside>
      </div>
    </div>
  );
}
