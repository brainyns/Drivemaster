import { useCallback, useEffect, useState } from "react";
import { actualizarMiTienda, miTienda } from "../services/tiendaService";
import { cambiarResumenSemanal, probarResumenSemanal, resumenSemanal } from "../services/informesTiendaService";
import { formatFecha, formatPrecio } from "../components/servicio/estadosServicio";
import SelectorUbicacion from "../components/mapa/SelectorUbicacion";
import TiendaVendedor from "../components/marketplace/TiendaVendedor";
import CabeceraNegocio from "../components/ui/CabeceraNegocio";
import VistaPrevia from "../components/ui/VistaPrevia";
import SeccionEditable from "../components/ui/SeccionEditable";
import LogoNegocio from "../components/ui/LogoNegocio";
import ChipEstado from "../components/ui/ChipEstado";
import Campo from "../components/ui/Campo";
import Dato from "../components/ui/Dato";
import PieFormulario from "../components/ui/PieFormulario";
import Alerta from "../components/ui/Alerta";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import {
  COSTO_ENVIO_MAX, comoLaVeElCliente, completitudTienda, errorCostoEnvio, errorHorarioTienda, errorTelefono, erroresUbicacion,
} from "./tienda/perfilTienda";
import "../css/mi-tienda.css";

const ADMIN = "Para cambiarlo, escríbele al administrador de DriveMaster.";
const ESTADO = {
  APROBADO: { tono: "ok", texto: "Aprobada" },
  PENDIENTE: { tono: "aviso", texto: "Pendiente de aprobación" },
  RECHAZADO: { tono: "error", texto: "Rechazada" },
};
// Sección del perfil → formulario que abre "lo que falta"
const EDITOR = { "mtd-ubicacion": "ubicacion", "mtd-horario": "horario", "mtd-contacto": "contacto" };

/**
 * Guarda una parte de los datos con PUT /mi-tienda (el backend ignora lo que no viaja) y devuelve la tienda
 * actualizada. Cada formulario envía solo sus campos.
 */
function useGuardar(token, onGuardado) {
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState(null);
  const guardar = async (datos) => {
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await actualizarMiTienda(token, datos));
    } catch (e) {
      setError(e.message);
      setGuardando(false);
    }
  };
  return { guardar, guardando, error };
}

/** Ciudad, barrio, dirección y el punto en el mapa. La dirección del pin solo completa lo que esté vacío. */
function FormUbicacion({ tienda, token, onGuardado, onCancelar }) {
  const [form, setForm] = useState({ ciudad: tienda.ciudad || "", barrio: tienda.barrio || "", direccion: tienda.direccion || "" });
  const [punto, setPunto] = useState(tienda.latitud != null && tienda.longitud != null
    ? { latitud: tienda.latitud, longitud: tienda.longitud } : null);
  const [errores, setErrores] = useState({});
  const { guardar, guardando, error } = useGuardar(token, onGuardado);

  const cambiar = (e) => {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
    if (errores[name]) setErrores((x) => ({ ...x, [name]: null }));
  };

  const completar = ({ direccion, barrio, ciudad }) => setForm((f) => ({
    direccion: f.direccion.trim() || direccion || f.direccion,
    barrio: f.barrio.trim() || barrio || f.barrio,
    ciudad: f.ciudad.trim() || ciudad || f.ciudad,
  }));

  const enviar = (e) => {
    e.preventDefault();
    const faltan = erroresUbicacion(form);
    setErrores(faltan);
    if (Object.keys(faltan).length) return;
    guardar({
      ciudad: form.ciudad.trim(), barrio: form.barrio.trim(), direccion: form.direccion.trim(),
      latitud: punto?.latitud ?? null, longitud: punto?.longitud ?? null,
    });
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <div className="ui-form__campos">
        <Campo id="mtd-ciudad" label="Ciudad" error={errores.ciudad}>
          <input className="ui-field__input" name="ciudad" value={form.ciudad} onChange={cambiar} autoComplete="address-level2" autoFocus />
        </Campo>
        <Campo id="mtd-barrio" label="Barrio" opcional>
          <input className="ui-field__input" name="barrio" value={form.barrio} onChange={cambiar} />
        </Campo>
        <Campo id="mtd-direccion" label="Dirección" error={errores.direccion}>
          <input className="ui-field__input" name="direccion" value={form.direccion} onChange={cambiar} autoComplete="street-address" />
        </Campo>
      </div>
      <SelectorUbicacion
        id="mtd-mapa"
        ciudad={form.ciudad}
        value={punto}
        onChange={setPunto}
        onDireccion={completar}
        direccionInicial={punto && tienda.direccion ? tienda.direccion : null}
        ayuda="Busca la dirección de la tienda, usa tu ubicación o toca el mapa para marcar la entrada."
      />
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar ubicación" />
    </form>
  );
}

function FormHorario({ tienda, token, onGuardado, onCancelar }) {
  const [horario, setHorario] = useState(tienda.horario || "");
  const [errorCampo, setErrorCampo] = useState(null);
  const { guardar, guardando, error } = useGuardar(token, onGuardado);

  const enviar = (e) => {
    e.preventDefault();
    const err = errorHorarioTienda(horario);
    setErrorCampo(err);
    if (!err) guardar({ horario: horario.trim() });
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <Campo id="mtd-horario-texto" label="Horario de atención" error={errorCampo}
        ayuda="El cliente lo ve en cada producto tuyo y lo necesita para pasar a retirar.">
        <textarea className="ui-field__input" rows={3} autoFocus value={horario}
          placeholder={"Lunes a viernes: 8:00 a. m. – 6:00 p. m.\nSábados: 8:00 a. m. – 1:00 p. m."}
          onChange={(e) => { setHorario(e.target.value); if (errorCampo) setErrorCampo(null); }} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar horario" />
    </form>
  );
}

/** Retiro en la tienda siempre; el envío a domicilio es opcional, con un costo fijo y solo dentro de la ciudad. */
function FormEntrega({ tienda, token, onGuardado, onCancelar }) {
  const [ofreceEnvio, setOfreceEnvio] = useState(tienda.costoEnvio != null);
  const [costo, setCosto] = useState(tienda.costoEnvio != null ? String(tienda.costoEnvio) : "");
  const [errorCampo, setErrorCampo] = useState(null);
  const { guardar, guardando, error } = useGuardar(token, onGuardado);

  const enviar = (e) => {
    e.preventDefault();
    const err = errorCostoEnvio(ofreceEnvio, costo);
    setErrorCampo(err);
    if (!err) guardar({ ofreceEnvio, costoEnvio: ofreceEnvio ? Number(costo) : null });
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <p className="mtd-fijo"><Icon name="check" size={16} /> Retiro en tu tienda con código: siempre disponible.</p>
      <label className="mtd-check">
        <input type="checkbox" checked={ofreceEnvio} autoFocus
          onChange={(e) => { setOfreceEnvio(e.target.checked); setErrorCampo(null); }} />
        Hago envíos a domicilio{tienda.ciudad ? ` dentro de ${tienda.ciudad}` : " dentro de mi ciudad"}
      </label>
      {ofreceEnvio && (
        <Campo id="mtd-costo" label="Costo fijo del envío (COP)" error={errorCampo}
          ayuda={`Puede ser $ 0. Máximo ${formatPrecio(COSTO_ENVIO_MAX)}. El cliente lo paga al comprar.`}>
          <input className="ui-field__input ui-form__corto" type="number" inputMode="numeric" min="0" step="500"
            value={costo} onChange={(e) => { setCosto(e.target.value); if (errorCampo) setErrorCampo(null); }} />
        </Campo>
      )}
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar entrega" />
    </form>
  );
}

function FormContacto({ tienda, token, onGuardado, onCancelar }) {
  const [telefono, setTelefono] = useState(tienda.telefono || "");
  const [errorCampo, setErrorCampo] = useState(null);
  const { guardar, guardando, error } = useGuardar(token, onGuardado);

  const enviar = (e) => {
    e.preventDefault();
    const err = errorTelefono(telefono);
    setErrorCampo(err);
    if (!err) guardar({ telefono: telefono.trim() });
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <Campo id="mtd-telefono" label="Teléfono de la tienda" error={errorCampo}
        ayuda="Lo usan DriveMaster y los talleres que te compran para coordinar entregas.">
        <input className="ui-field__input ui-form__corto" type="tel" inputMode="tel" autoComplete="tel" maxLength={20} autoFocus
          value={telefono} onChange={(e) => { setTelefono(e.target.value); if (errorCampo) setErrorCampo(null); }} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={onCancelar} textoGuardar="Guardar teléfono" />
    </form>
  );
}

/** Interruptor del resumen semanal por correo (lunes 7:00) y envío de prueba al correo del dueño. */
function ResumenSemanal({ token }) {
  const [activo, setActivo] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState(null);

  useEffect(() => {
    let vivo = true;
    resumenSemanal(token)
      .then((r) => { if (vivo) setActivo(r.activo); })
      .catch((e) => { if (vivo) setAviso({ error: true, texto: e.message }); });
    return () => { vivo = false; };
  }, [token]);

  const ejecutar = async (accion) => {
    setOcupado(true);
    setAviso(null);
    try { await accion(); } catch (e) { setAviso({ error: true, texto: e.message }); } finally { setOcupado(false); }
  };

  const cambiar = (e) => ejecutar(async () => {
    const r = await cambiarResumenSemanal(token, e.target.checked);
    setActivo(r.activo);
    setAviso({ texto: r.activo ? "Listo: recibirás el resumen cada lunes a las 7:00." : "Resumen semanal desactivado." });
  });

  const probar = () => ejecutar(async () => setAviso({ texto: (await probarResumenSemanal(token)).mensaje }));

  return (
    <SeccionEditable id="mtd-resumen" icono="mail" titulo="Resumen semanal por correo"
      descripcion="Cada lunes a las 7:00: ventas frente a la semana anterior, lo más vendido, qué reponer y el dinero quieto en inventario. Las cifras salen de tus pedidos; la IA solo redacta el titular."
      aviso={aviso && <Alerta tono={aviso.error ? "error" : "ok"} onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>}>
      <div className="mtd-resumen">
        <label className="mtd-check">
          <input type="checkbox" checked={!!activo} disabled={activo === null || ocupado} onChange={cambiar} />
          Recibir el resumen semanal
        </label>
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={probar} disabled={ocupado} aria-busy={ocupado}>
          {ocupado ? "Enviando…" : "Enviarme una prueba ahora"}
        </button>
      </div>
    </SeccionEditable>
  );
}

/**
 * Mi tienda: identidad, qué tan completo está el perfil y cada sección editable por separado. Sin ciudad, dirección
 * y horario los productos no aparecen en el catálogo. Lo administrativo (nombre, NIT, plan) se ve pero no se edita.
 */
export default function DatosMiTienda({ token, onGuardado }) {
  const [tienda, setTienda] = useState(null);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [editando, setEditando] = useState(null); // "ubicacion" | "horario" | "entrega" | "contacto"
  const [aviso, setAviso] = useState(null); // { seccion, texto }

  const cargar = useCallback(async () => {
    try {
      setTienda(await miTienda(token));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [token]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  if (!tienda) {
    if (!error) return <div className="ss-panel--tienda"><Esqueleto variante="formulario" cantidad={4} texto="Cargando los datos de tu tienda…" /></div>;
    return (
      <div className="ss-panel--tienda">
        <Alerta reintentando={reintentando} onReintentar={async () => { setReintentando(true); await cargar(); setReintentando(false); }}>
          No pudimos cargar los datos de tu tienda. {error}
        </Alerta>
      </div>
    );
  }

  const t = tienda;
  const { porcentaje, faltan } = completitudTienda(t);
  const estado = ESTADO[t.estadoAprobacion] || ESTADO.APROBADO;
  const activa = t.activo !== false;

  const abrir = (seccion) => { setAviso(null); setEditando(seccion); };
  const cerrar = () => setEditando(null);
  const guardado = (seccion, texto) => (actualizada) => {
    setTienda(actualizada);
    onGuardado?.(actualizada);
    setEditando(null);
    setAviso({ seccion, texto });
  };
  const avisoDe = (seccion) => aviso?.seccion === seccion && (
    <Alerta tono="ok" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>
  );
  // Desde "lo que falta": lleva a la sección y abre su edición
  const resolver = (item) => {
    document.getElementById(item.seccion)?.scrollIntoView({
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "start",
    });
    if (EDITOR[item.seccion]) abrir(EDITOR[item.seccion]);
  };
  const vista = comoLaVeElCliente(t);

  return (
    <div className="ss-panel--tienda ui-perfil">
      <CabeceraNegocio
        nombre={t.nombre}
        logo={<LogoNegocio url={t.logoUrl} nombre={t.nombre} tamano="grande" />}
        porcentaje={porcentaje} faltan={faltan} onResolver={resolver}
        textoListo="Tu tienda tiene todo lo que el catálogo y tus clientes necesitan."
        chips={(
          <>
            <ChipEstado tono={estado.tono}>{estado.texto}</ChipEstado>
            <ChipEstado tono={activa ? "ok" : "apagado"}>{activa ? "Activa" : "Inactiva"}</ChipEstado>
            <ChipEstado tono={t.datosCompletos ? "ok" : "aviso"}>{t.datosCompletos ? "Visible en el catálogo" : "Oculta del catálogo"}</ChipEstado>
            {t.planActual && (
              <ChipEstado tono="acento">
                Plan {t.planActual.toLowerCase()}{t.planVence ? ` hasta ${formatFecha(t.planVence)}` : ""}
              </ChipEstado>
            )}
          </>
        )}
        meta={(
          <>
            {(t.barrio || t.ciudad) && <span><Icon name="pin" size={14} /> {[t.barrio, t.ciudad].filter(Boolean).join(", ")}</span>}
            {(t.fechaAprobacion || t.fechaRegistro) && <span>En DriveMaster desde {formatFecha(t.fechaAprobacion || t.fechaRegistro)}</span>}
          </>
        )}
        aviso={!t.datosCompletos ? (
          <Alerta tono="aviso">Tus productos no se muestran en el catálogo hasta que completes la ciudad, la dirección y el horario.</Alerta>
        ) : t.estadoAprobacion === "PENDIENTE" ? (
          <Alerta tono="info">DriveMaster está revisando tu tienda. Puedes dejar listos tus datos mientras tanto.</Alerta>
        ) : null}
      />

      <div className="ui-perfil__rejilla">
        <div className="ui-perfil__secciones">
          <SeccionEditable id="mtd-negocio" icono="file" titulo="Datos del negocio"
            descripcion="Con estos datos te identifican los clientes y DriveMaster." nota={ADMIN}>
            <dl className="ui-datos">
              <Dato etiqueta="Nombre de la tienda">{t.nombre}</Dato>
              <Dato etiqueta="NIT">{t.nit}</Dato>
              <Dato etiqueta="Fecha de registro">{t.fechaRegistro && formatFecha(t.fechaRegistro)}</Dato>
              <Dato etiqueta="Plan">{t.planActual && `${t.planActual.toLowerCase()}${t.planVence ? `, vence el ${formatFecha(t.planVence)}` : ""}`}</Dato>
            </dl>
          </SeccionEditable>

          <SeccionEditable id="mtd-ubicacion" icono="pin" titulo="Ubicación"
            descripcion="Donde retiran los clientes. Ciudad y dirección son obligatorias para vender en el catálogo."
            onEditar={() => abrir("ubicacion")} editando={editando === "ubicacion"} aviso={avisoDe("ubicacion")}>
            {editando === "ubicacion" ? (
              <FormUbicacion tienda={t} token={token} onCancelar={cerrar}
                onGuardado={guardado("ubicacion", "Ubicación guardada.")} />
            ) : (
              <dl className="ui-datos">
                <Dato etiqueta="Ciudad">{t.ciudad}</Dato>
                <Dato etiqueta="Barrio">{t.barrio}</Dato>
                <Dato etiqueta="Dirección" ancho>{t.direccion}</Dato>
                <Dato etiqueta="Pin en el mapa">
                  {t.latitud != null && t.longitud != null
                    ? <ChipEstado tono="ok">Marcado</ChipEstado>
                    : <ChipEstado tono="aviso">Sin marcar</ChipEstado>}
                </Dato>
              </dl>
            )}
          </SeccionEditable>

          <SeccionEditable id="mtd-horario" icono="clock" titulo="Horario de atención"
            descripcion="Obligatorio para vender: el cliente lo ve en cada producto."
            onEditar={() => abrir("horario")} textoEditar={t.horario ? "Editar" : "Agregar horario"}
            editando={editando === "horario"} aviso={avisoDe("horario")}>
            {editando === "horario" ? (
              <FormHorario tienda={t} token={token} onCancelar={cerrar} onGuardado={guardado("horario", "Horario guardado.")} />
            ) : t.horario ? (
              <p className="ui-texto-pre">{t.horario}</p>
            ) : (
              <p className="ui-datos__falta">Aún no escribes tu horario.</p>
            )}
          </SeccionEditable>

          <SeccionEditable id="mtd-entrega" icono="box" titulo="Entrega y retiro"
            descripcion="Cómo reciben los clientes lo que compran en el catálogo."
            onEditar={() => abrir("entrega")} editando={editando === "entrega"} aviso={avisoDe("entrega")}>
            {editando === "entrega" ? (
              <FormEntrega tienda={t} token={token} onCancelar={cerrar} onGuardado={guardado("entrega", "Entrega guardada.")} />
            ) : (
              <dl className="ui-datos">
                <Dato etiqueta="Retiro en la tienda"><ChipEstado tono="ok">Disponible</ChipEstado></Dato>
                <Dato etiqueta="Envío a domicilio">
                  {t.costoEnvio != null
                    ? <>{formatPrecio(t.costoEnvio)} <span className="mtd-detalle">· solo dentro de {t.ciudad || "tu ciudad"}</span></>
                    : <ChipEstado tono="apagado">No ofreces envíos</ChipEstado>}
                </Dato>
              </dl>
            )}
          </SeccionEditable>

          <SeccionEditable id="mtd-contacto" icono="phone" titulo="Contacto"
            descripcion="Por aquí te escriben DriveMaster y los talleres que te compran."
            onEditar={() => abrir("contacto")} textoEditar={t.telefono ? "Editar" : "Agregar teléfono"}
            editando={editando === "contacto"} aviso={avisoDe("contacto")}
            nota={editando !== "contacto" && "El correo de contacto lo cambia el administrador de DriveMaster."}>
            {editando === "contacto" ? (
              <FormContacto tienda={t} token={token} onCancelar={cerrar} onGuardado={guardado("contacto", "Teléfono guardado.")} />
            ) : (
              <dl className="ui-datos">
                <Dato etiqueta="Teléfono">{t.telefono}</Dato>
                <Dato etiqueta="Correo de contacto">{t.correoContacto || t.correoUsuario}</Dato>
              </dl>
            )}
          </SeccionEditable>

          <ResumenSemanal token={token} />
        </div>

        <aside className="ui-perfil__lateral">
          <VistaPrevia id="mtd-vista"
            ayuda="En el catálogo, cada producto tuyo muestra tu nombre y ciudad; al abrirlo, el cliente ve esta ficha."
            tonoConsejo={t.datosCompletos ? undefined : "aviso"} iconoConsejo="eye"
            consejo={t.datosCompletos
              ? "Así aparece tu tienda en el detalle de cada producto."
              : "Mientras falten la ciudad, la dirección o el horario, tus productos no aparecen en el catálogo."}>
            <p className="mtd-vista__tarjeta">
              <span>Así en la tarjeta del producto:</span>
              <strong>{vista.nombre} · {vista.ciudad || "Sin ciudad"}</strong>
            </p>
            <TiendaVendedor tienda={vista} idTitulo="mtd-vista-tienda" className="mk-tienda mtd-vista__ficha" />
          </VistaPrevia>
        </aside>
      </div>
    </div>
  );
}
