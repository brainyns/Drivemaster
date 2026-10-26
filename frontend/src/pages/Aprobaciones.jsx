import { useCallback, useEffect, useMemo, useState } from "react";
import { listarTalleres, resolverAprobacionTaller, activarDesactivarTaller } from "../services/tallerService";
import { listarTiendas, resolverAprobacionTienda, activarDesactivarTienda } from "../services/tiendaService";
import DrawerAdmin from "../components/admin/DrawerAdmin";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import MiniMapa from "../components/admin/MiniMapa";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import ChipEstado from "../components/ui/ChipEstado";
import LogoNegocio from "../components/ui/LogoNegocio";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import Dato from "../components/ui/Dato";
import Icon from "../components/public/Icon";
import { capitalizar, formatoCop, formatoFecha, unir } from "../utils/formato";
import {
  ESTADOS_NEGOCIO, TIPOS_NEGOCIO, chipRevision, conteoRevision, correoNegocio, estadoRevision, filtrarRevision,
} from "./admin/plataformaAdmin";
// El panel lateral y el diálogo de confirmación (DrawerAdmin, ConfirmacionAdmin) traen su estilo de admin-base.css
import "../css/admin-base.css";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/aprobaciones-plataforma.css";

const clave = (n) => `${n.tipo}-${n.id}`;
const tieneUbicacion = (n) => n.latitud != null && n.longitud != null;
const nombreTipo = (n) => (n.tipo === "TALLER" ? "Taller" : "Tienda");

const CONFIRMACIONES = {
  aprobar: { texto: "La cuenta quedará activa y podrá operar en el marketplace.", boton: "Aprobar", variante: "primario" },
  rechazar: { texto: "El negocio recibirá el motivo por correo y no podrá operar.", boton: "Rechazar", variante: "peligro" },
  desactivar: { texto: "Dejará de aparecer para los clientes y no recibirá solicitudes ni pedidos hasta que lo actives otra vez.", boton: "Desactivar", variante: "peligro" },
  activar: { texto: "Volverá a aparecer para los clientes y a recibir solicitudes y pedidos.", boton: "Activar", variante: "primario" },
};

/** Detalle completo del negocio en el panel lateral. */
function DetalleNegocio({ negocio: n, ocupado, error, onCerrar, onPedir }) {
  const estado = estadoRevision(n);
  const chip = chipRevision(n);

  const pie = estado === "APROBADO" ? (
    n.activo
      ? <button type="button" className="ui-btn ui-btn--secundario apr-peligro" onClick={() => onPedir("desactivar", n)} disabled={ocupado}>Desactivar</button>
      : <button type="button" className="ui-btn ui-btn--primario" onClick={() => onPedir("activar", n)} disabled={ocupado}>Activar</button>
  ) : (
    <>
      {estado === "PENDIENTE" && (
        <button type="button" className="ui-btn ui-btn--secundario apr-peligro" onClick={() => onPedir("rechazar", n)} disabled={ocupado}>Rechazar</button>
      )}
      <button type="button" className="ui-btn ui-btn--primario" onClick={() => onPedir("aprobar", n)} disabled={ocupado}>
        <Icon name="check" size={16} /> Aprobar
      </button>
    </>
  );

  return (
    <DrawerAdmin titulo={n.nombre} onCerrar={onCerrar} pie={pie}
      encabezado={(
        <div className="apr-detalle__cabecera">
          <LogoNegocio url={n.logoUrl} nombre={n.nombre} tamano="mediano" />
          <span className="apr-detalle__chips">
            <ChipEstado tono="apagado">{nombreTipo(n)}</ChipEstado>
            <ChipEstado tono={chip.tono}>{chip.label}</ChipEstado>
          </span>
        </div>
      )}>
      {error && <Alerta>{error}</Alerta>}

      <section className="apr-detalle__bloque" aria-labelledby="apr-negocio">
        <h3 id="apr-negocio" className="apr-detalle__titulo">Negocio</h3>
        <dl className="ui-datos">
          <Dato etiqueta="NIT">{n.nit}</Dato>
          <Dato etiqueta="Registro">{n.fechaRegistro && formatoFecha(n.fechaRegistro)}</Dato>
          <Dato etiqueta="Dirección" ancho>{unir([n.direccion, capitalizar(n.barrio)])}</Dato>
          <Dato etiqueta="Ciudad">{capitalizar(n.ciudad)}</Dato>
          <Dato etiqueta="Horario">{n.horario}</Dato>
          {n.tipo === "TIENDA" && (
            <Dato etiqueta="Envío" ancho>
              {n.costoEnvio != null ? `${formatoCop(n.costoEnvio)} (solo en ${capitalizar(n.ciudad) || "su ciudad"})` : "Solo retiro en el local"}
            </Dato>
          )}
          {n.tipo === "TALLER" && (
            <>
              <Dato etiqueta="Mano de obra">{n.precioManoObra ? formatoCop(n.precioManoObra) : null}</Dato>
              <Dato etiqueta="Tarifa por hora">{n.tarifaHora ? formatoCop(n.tarifaHora) : null}</Dato>
              {n.especialidades?.length > 0 && <Dato etiqueta="Especialidades" ancho>{n.especialidades.join(", ")}</Dato>}
            </>
          )}
          {estado === "RECHAZADO" && n.motivoRechazo && <Dato etiqueta="Motivo del rechazo" ancho>{n.motivoRechazo}</Dato>}
        </dl>
        {n.tipo === "TIENDA" && n.datosCompletos === false && (
          <Alerta tono="aviso">Le falta dirección, ciudad u horario: sus productos no se muestran en el catálogo hasta completarlos.</Alerta>
        )}
      </section>

      <section className="apr-detalle__bloque" aria-labelledby="apr-contacto">
        <h3 id="apr-contacto" className="apr-detalle__titulo">Contacto</h3>
        <dl className="ui-datos">
          <Dato etiqueta="Correo" ancho>{correoNegocio(n)}</Dato>
          <Dato etiqueta="Teléfono">{n.telefono}</Dato>
        </dl>
      </section>

      <section className="apr-detalle__bloque" aria-labelledby="apr-ubicacion">
        <h3 id="apr-ubicacion" className="apr-detalle__titulo">Ubicación GPS</h3>
        {tieneUbicacion(n) ? (
          <>
            <MiniMapa latitud={n.latitud} longitud={n.longitud} titulo={n.nombre} />
            <span className="apr-coordenadas">{n.latitud.toFixed(6)}, {n.longitud.toFixed(6)}</span>
          </>
        ) : (
          <p className="apr-detalle__nota"><ChipEstado tono="aviso">Sin ubicación</ChipEstado> El negocio no marcó su punto en el mapa.</p>
        )}
      </section>

    </DrawerAdmin>
  );
}

/** Panel del SUPERADMIN para validar y activar talleres y tiendas. */
export default function Aprobaciones({ token }) {
  const [negocios, setNegocios] = useState(null);
  const [filtro, setFiltro] = useState("PENDIENTE");
  const [tipo, setTipo] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const [error, setError] = useState(null);
  const [errorCarga, setErrorCarga] = useState(null);
  const [reintentando, setReintentando] = useState(false);
  const [aviso, setAviso] = useState(null);
  const [procesando, setProcesando] = useState(null);
  const [seleccionado, setSeleccionado] = useState(null);
  // { accion: "aprobar" | "rechazar" | "desactivar" | "activar", negocio }
  const [confirmacion, setConfirmacion] = useState(null);
  const [motivo, setMotivo] = useState("");

  const cargar = useCallback(async () => {
    try {
      const [talleres, tiendas] = await Promise.all([listarTalleres(token), listarTiendas(token)]);
      setNegocios([
        ...talleres.map((t) => ({ ...t, tipo: "TALLER" })),
        ...tiendas.map((t) => ({ ...t, tipo: "TIENDA" })),
      ]);
      setErrorCarga(null);
    } catch (e) {
      setErrorCarga(e.message);
    }
  }, [token]);

  useEffect(() => {
    const id = setTimeout(cargar, 0);
    return () => clearTimeout(id);
  }, [cargar]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const lista = useMemo(() => negocios || [], [negocios]);
  const conteo = useMemo(() => conteoRevision(lista, tipo), [lista, tipo]);
  const visibles = useMemo(() => filtrarRevision(lista, { estado: filtro, tipo, busqueda }), [lista, filtro, tipo, busqueda]);
  // 20 por página; vuelve a la primera al cambiar de filtro o de búsqueda (los contadores siguen contando todo)
  const pag = usePaginacion(visibles.length, [filtro, tipo, busqueda].join("|"));
  const enPagina = paginar(visibles, pag.pagina);

  // El detalle se lee de la lista para mostrar los datos frescos tras cada acción
  const detalle = seleccionado ? lista.find((n) => clave(n) === seleccionado) : null;

  const ejecutar = async (n, accion, mensaje) => {
    setProcesando(clave(n));
    setError(null);
    setAviso(null);
    try {
      await accion();
      setAviso(mensaje);
      setConfirmacion(null);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const pedir = (accion, negocio) => {
    setError(null);
    setMotivo("");
    setConfirmacion({ accion, negocio });
  };

  const confirmar = () => {
    const { accion, negocio: n } = confirmacion;
    const resolver = n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda;
    const activar = n.tipo === "TALLER" ? activarDesactivarTaller : activarDesactivarTienda;
    if (accion === "aprobar") {
      ejecutar(n, () => resolver(token, n.id, { aprobado: true }), `${n.nombre} quedó aprobado y activo.`);
    } else if (accion === "rechazar") {
      ejecutar(n, () => resolver(token, n.id, { aprobado: false, motivo: motivo.trim() }), `${n.nombre} fue rechazado.`);
    } else {
      const activo = accion === "activar";
      ejecutar(n, () => activar(token, n.id, activo), `${n.nombre} quedó ${activo ? "activo" : "desactivado"}.`);
    }
  };

  const abrirConTeclado = (e, n) => {
    if (e.target !== e.currentTarget) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      setSeleccionado(clave(n));
    }
  };

  // Los botones de la fila no abren el detalle
  const sinPropagar = (fn) => (e) => { e.stopPropagation(); fn(); };
  const textos = confirmacion && CONFIRMACIONES[confirmacion.accion];
  const tituloConfirmacion = confirmacion && `${textos.boton} ${confirmacion.negocio.nombre}`;
  const limpiar = () => { setBusqueda(""); setTipo(""); };

  return (
    <div className="ss-panel--plataforma pp-root apr-root">
      <EncabezadoPagina titulo="Aprobaciones"
        descripcion="Valida los talleres y tiendas que se registran y controla qué cuentas pueden operar." />

      {aviso && <Alerta tono="ok" className="pp-bloque-sep" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}

      <div className="pp-filtros">
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="apr-busqueda" className="ui-sr">Buscar por nombre, NIT o correo</label>
          <input id="apr-busqueda" className="ui-field__input" type="search" placeholder="Nombre, NIT o correo"
            value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </div>
        <div className="ui-filtros" role="group" aria-label="Tipo de negocio">
          {TIPOS_NEGOCIO.map((t) => (
            <button key={t.valor || "todos"} type="button" className="ui-chip" aria-pressed={tipo === t.valor}
              onClick={() => setTipo(t.valor)}>{t.label}</button>
          ))}
        </div>
      </div>
      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Estado de revisión">
        {Object.entries(ESTADOS_NEGOCIO).map(([e, info]) => (
          <button key={e} type="button" className="ui-chip" aria-pressed={filtro === e} onClick={() => setFiltro(e)}>
            {info.plural} {negocios && <span className="ui-chip__contador">{conteo[e]}</span>}
          </button>
        ))}
      </div>

      {error && !confirmacion && !detalle && <Alerta className="pp-bloque-sep" onCerrar={() => setError(null)}>{error}</Alerta>}
      {errorCarga && (
        <Alerta className="pp-bloque-sep" onReintentar={reintentar} reintentando={reintentando}>
          {negocios ? `No pudimos actualizar la lista: ${errorCarga}` : errorCarga}
        </Alerta>
      )}

      {!negocios ? (
        !errorCarga && <Esqueleto variante="lista" cantidad={5} texto="Cargando negocios…" />
      ) : visibles.length === 0 ? (
        busqueda.trim() || tipo ? (
          <EstadoVacio icono="search" titulo="Ningún negocio coincide"
            texto="Prueba con otro nombre, NIT o correo, o muestra tiendas y talleres.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        ) : filtro === "PENDIENTE" ? (
          <EstadoVacio icono="check" titulo="Nada por revisar"
            texto="Cuando una tienda o un taller se registre, aparece aquí para que lo apruebes o lo rechaces." />
        ) : (
          <EstadoVacio icono="box" titulo={`No hay negocios ${ESTADOS_NEGOCIO[filtro].plural.toLowerCase()}`}
            texto="Cambia de estado para ver los demás." />
        )
      ) : (
        <div className="ui-card ui-table-card">
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas apr-tabla">
              <caption className="ui-sr">Negocios {ESTADOS_NEGOCIO[filtro].plural.toLowerCase()}, los más recientes primero. Abre una fila para ver el detalle.</caption>
              <thead>
                <tr>
                  <th scope="col">Negocio</th>
                  <th scope="col">Ciudad</th>
                  <th scope="col">Contacto</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Registro</th>
                  <th scope="col"><span className="ui-sr">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {enPagina.map((n) => {
                  const k = clave(n);
                  const ocupado = procesando === k;
                  const chip = chipRevision(n);
                  return (
                    <tr key={k} className={`apr-fila${seleccionado === k ? " apr-fila--seleccionada" : ""}`} tabIndex={0}
                      aria-label={`${n.nombre}, ${nombreTipo(n).toLowerCase()}: ver detalle`}
                      onClick={() => setSeleccionado(k)} onKeyDown={(e) => abrirConTeclado(e, n)}>
                      <td className="ui-table__titulo">
                        <span className="apr-negocio">
                          <LogoNegocio url={n.logoUrl} nombre={n.nombre} />
                          <span className="apr-negocio__texto">
                            <strong>{n.nombre}</strong>
                            <span className="pp-sub">{unir([nombreTipo(n), unir([n.direccion, capitalizar(n.barrio)], ", ") || "Sin dirección"])}</span>
                          </span>
                        </span>
                      </td>
                      <td data-label="Ciudad">{capitalizar(n.ciudad) || <span className="pp-tenue">Sin ciudad</span>}</td>
                      <td data-label="Contacto" className="apr-contacto">
                        {correoNegocio(n) || <span className="pp-tenue">Sin correo</span>}
                        {n.telefono && <span className="pp-sub">{n.telefono}</span>}
                      </td>
                      <td data-label="Estado"><ChipEstado tono={chip.tono}>{chip.label}</ChipEstado></td>
                      <td data-label="Registro" className="apr-fecha">{formatoFecha(n.fechaRegistro)}</td>
                      <td className="ui-table__acciones">
                        {estadoRevision(n) === "PENDIENTE" ? (
                          <>
                            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico apr-peligro"
                              onClick={sinPropagar(() => pedir("rechazar", n))} disabled={ocupado} aria-label={`Rechazar ${n.nombre}`}>Rechazar</button>
                            <button type="button" className="ui-btn ui-btn--primario ui-btn--chico"
                              onClick={sinPropagar(() => pedir("aprobar", n))} disabled={ocupado} aria-label={`Aprobar ${n.nombre}`}>Aprobar</button>
                          </>
                        ) : (
                          <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico"
                            onClick={sinPropagar(() => setSeleccionado(k))} aria-label={`Ver detalle de ${n.nombre}`}>Ver detalle</button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {visibles.length > 0 && (
        <Paginacion pagina={pag.pagina} total={visibles.length} onCambiar={pag.cambiar} etiqueta="Páginas de negocios" />
      )}

      {detalle && (
        <DetalleNegocio key={seleccionado} negocio={detalle} ocupado={procesando === seleccionado}
          error={confirmacion ? null : error} onCerrar={() => setSeleccionado(null)} onPedir={pedir} />
      )}

      {confirmacion && (
        <ConfirmacionAdmin titulo={tituloConfirmacion} texto={textos.texto} confirmar={textos.boton} variante={textos.variante}
          cargando={procesando === clave(confirmacion.negocio)}
          deshabilitado={confirmacion.accion === "rechazar" && !motivo.trim()}
          onConfirmar={confirmar} onCancelar={() => { setConfirmacion(null); setError(null); }}>
          {confirmacion.accion === "rechazar" && (
            <Campo id="apr-motivo" label="Motivo del rechazo" ayuda="Se le enviará al negocio por correo.">
              <textarea className="ui-field__input apr-motivo" value={motivo} onChange={(e) => setMotivo(e.target.value)}
                maxLength={300} required />
            </Campo>
          )}
          {error && <Alerta>{error}</Alerta>}
        </ConfirmacionAdmin>
      )}
    </div>
  );
}
