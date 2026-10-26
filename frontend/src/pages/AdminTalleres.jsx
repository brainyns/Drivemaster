import { useEffect, useMemo, useState } from "react";
import { getToken, getUser } from "../services/authService";
import { listarTalleres, crearTaller, actualizarTaller, activarDesactivarTaller } from "../services/tallerService";
import { ordenesPorTallerAdmin } from "../services/ordenServicioService";
import SelectorUbicacion from "../components/mapa/SelectorUbicacion";
import DrawerAdmin from "../components/admin/DrawerAdmin";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import { capitalizar } from "../utils/formato";
import Calificacion from "../components/ui/Calificacion";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import { paginar, usePaginacion } from "../components/ui/reglasPaginacion";
import Indicador from "../components/ui/Indicador";
import ChipEstado from "../components/ui/ChipEstado";
import LogoNegocio from "../components/ui/LogoNegocio";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import CampoClave from "../components/ui/CampoClave";
import Icon from "../components/public/Icon";
import { promedioDeTalleres } from "../utils/calificacion";
// El panel lateral y el diálogo (DrawerAdmin, ConfirmacionAdmin) traen su estilo de admin-base.css
import "../css/admin-base.css";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/servicios-plataforma.css";

// Estado de cada orden con el formato de catálogo de ChipEstado
const ESTADO_ORDEN = {
  SOLICITADA: { label: "Solicitada", tono: "info" },
  COTIZADA: { label: "Cotizada", tono: "apagado" },
  PAGADA: { label: "Pagada", tono: "aviso" },
  EN_PROCESO: { label: "En proceso", tono: "proceso" },
  FINALIZADA: { label: "Finalizada", tono: "ok" },
  CANCELADA: { label: "Cancelada", tono: "error" },
  REASIGNADA: { label: "Reasignada", tono: "apagado" },
};

const VACIO = {
  nombre: "", ciudad: "", direccion: "", latitud: "", longitud: "",
  especialidades: "", precioManoObra: "",
  correoUsuario: "", passwordUsuario: "",
};

const FILTROS = [
  { k: "todos", l: "Todos" },
  { k: "activos", l: "Activos" },
  { k: "inactivos", l: "Pausados" },
];

function formatPrecio(v) {
  if (v == null) return "—";
  return v.toLocaleString("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
}

export default function AdminTalleres() {
  // Crear, editar y pausar talleres es del SUPERADMIN; el ADMIN solo los ve y revisa sus servicios
  const puedeGestionar = getUser()?.rol === "SUPERADMIN";
  const token = getToken();
  const [talleres, setTalleres] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [cargado, setCargado] = useState(false);
  const [error, setError] = useState(null);
  const [formAbierto, setFormAbierto] = useState(false);
  const [editandoId, setEditandoId] = useState(null);
  const [form, setForm] = useState(VACIO);
  const [guardando, setGuardando] = useState(false);

  const [busqueda, setBusqueda] = useState("");
  const [filtro, setFiltro] = useState("todos");

  const [pedidosDe, setPedidosDe] = useState(null);
  const [pedidos, setPedidos] = useState([]);
  const [cargandoPedidos, setCargandoPedidos] = useState(false);

  const cargar = async () => {
    setCargando(true);
    setError(null);
    try {
      setTalleres(await listarTalleres(token));
      setCargado(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { cargar(); }, []);

  const abrirNuevo = () => {
    setEditandoId(null);
    setForm(VACIO);
    setError(null);
    setFormAbierto(true);
  };

  const abrirEditar = (t) => {
    setEditandoId(t.id);
    setForm({
      nombre: t.nombre || "", ciudad: t.ciudad || "", direccion: t.direccion || "",
      latitud: t.latitud ?? "", longitud: t.longitud ?? "",
      especialidades: (t.especialidades || []).join(", "),
      precioManoObra: t.precioManoObra ?? "",
      correoUsuario: "", passwordUsuario: "",
    });
    setError(null);
    setFormAbierto(true);
  };

  const handleGuardar = async (e) => {
    e.preventDefault();
    setError(null);
    // Un taller nuevo debe quedar en el mapa; los anteriores pueden seguir sin ubicación
    if (!editandoId && (form.latitud === "" || form.longitud === "")) {
      setError("Marca la ubicación del taller en el mapa.");
      return;
    }
    setGuardando(true);
    try {
      const payload = {
        nombre: form.nombre,
        ciudad: form.ciudad,
        direccion: form.direccion,
        latitud: form.latitud === "" ? null : Number(form.latitud),
        longitud: form.longitud === "" ? null : Number(form.longitud),
        especialidades: form.especialidades
          ? form.especialidades.split(",").map(s => s.trim()).filter(Boolean)
          : [],
        precioManoObra: form.precioManoObra === "" ? null : Number(form.precioManoObra),
      };
      if (!editandoId) {
        payload.correoUsuario = form.correoUsuario;
        payload.passwordUsuario = form.passwordUsuario;
      }

      if (editandoId) {
        await actualizarTaller(token, editandoId, payload);
      } else {
        await crearTaller(token, payload);
      }
      setFormAbierto(false);
      await cargar();
    } catch (e) {
      setError(e.message);
    } finally {
      setGuardando(false);
    }
  };

  const verPedidos = async (t) => {
    setPedidosDe({ id: t.id, nombre: t.nombre, logoUrl: t.logoUrl });
    setCargandoPedidos(true);
    setError(null);
    try {
      setPedidos(await ordenesPorTallerAdmin(token, t.id));
    } catch (e) {
      setError(e.message);
    } finally {
      setCargandoPedidos(false);
    }
  };

  const handleToggleActivo = async (t) => {
    try {
      await activarDesactivarTaller(token, t.id, !t.activo);
      await cargar();
    } catch (e) {
      setError(e.message);
    }
  };

  const resumen = useMemo(() => {
    const activos = talleres.filter(t => t.activo).length;
    // Con la misma regla que el chip de cada fila: un taller "Nuevo" no entra en el promedio
    return { total: talleres.length, activos, inactivos: talleres.length - activos, promedio: promedioDeTalleres(talleres) };
  }, [talleres]);

  const visibles = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    return talleres.filter(t => {
      if (filtro === "activos" && !t.activo) return false;
      if (filtro === "inactivos" && t.activo) return false;
      if (!q) return true;
      return [t.nombre, t.ciudad, t.direccion, ...(t.especialidades || [])]
        .filter(Boolean)
        .some(campo => String(campo).toLowerCase().includes(q));
    });
  }, [talleres, busqueda, filtro]);

  const conteoFiltro = { todos: resumen.total, activos: resumen.activos, inactivos: resumen.inactivos };
  // 20 por página; vuelve a la primera al cambiar de filtro o de búsqueda
  const pag = usePaginacion(visibles.length, filtro + "|" + busqueda);
  const enPagina = paginar(visibles, pag.pagina);

  // Pausar un taller pide confirmación; activarlo no
  const [pausando, setPausando] = useState(null);
  const [pausandoEnCurso, setPausandoEnCurso] = useState(false);
  const confirmarPausa = async () => {
    setPausandoEnCurso(true);
    await handleToggleActivo(pausando);
    setPausandoEnCurso(false);
    setPausando(null);
  };

  const cerrarForm = () => { if (!guardando) setFormAbierto(false); };
  const cambiar = (campo) => (e) => setForm({ ...form, [campo]: e.target.value });
  const limpiar = () => { setBusqueda(""); setFiltro("todos"); };

  return (
    <div className="ss-panel--plataforma pp-root srv-root">
      <EncabezadoPagina titulo="Servicios"
        descripcion="Talleres aliados conectados a DriveMaster. Desde aquí los activas, editas su tarifa de mano de obra y revisas los servicios que les hicieron."
        acciones={puedeGestionar && (
          <button type="button" className="ui-btn ui-btn--primario" onClick={abrirNuevo}>
            <Icon name="plus" size={16} /> Nuevo taller
          </button>
        )} />

      {error && !formAbierto && !pedidosDe && (
        <Alerta className="pp-bloque-sep" onReintentar={!cargado ? cargar : undefined} reintentando={cargando}
          onCerrar={cargado ? () => setError(null) : undefined}>
          {error}
        </Alerta>
      )}

      {cargado && (
        <section className="ui-indicadores pp-seccion" aria-label="Resumen de talleres">
          <Indicador icono="tool" titulo="Talleres registrados" valor={resumen.total} />
          <Indicador titulo="Recibiendo servicios" valor={resumen.activos} tono={resumen.activos > 0 ? "ok" : undefined} />
          <Indicador titulo="Pausados" valor={resumen.inactivos} />
          <Indicador icono="star" titulo="Calificación promedio"
            valor={resumen.promedio != null ? resumen.promedio.toFixed(1) : "Sin datos"}
            pie={resumen.promedio != null ? "De los talleres que ya tienen calificaciones" : "Ningún taller tiene calificaciones aún"} />
        </section>
      )}

      <div className="pp-filtros pp-bloque-sep">
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="srv-busqueda" className="ui-sr">Buscar por nombre, ciudad o especialidad</label>
          <input id="srv-busqueda" className="ui-field__input" type="search" placeholder="Nombre, ciudad o especialidad"
            value={busqueda} onChange={e => setBusqueda(e.target.value)} />
        </div>
        <div className="ui-filtros" role="group" aria-label="Filtrar por estado">
          {FILTROS.map(f => (
            <button key={f.k} type="button" className="ui-chip" aria-pressed={filtro === f.k} onClick={() => setFiltro(f.k)}>
              {f.l} {cargado && <span className="ui-chip__contador">{conteoFiltro[f.k]}</span>}
            </button>
          ))}
        </div>
      </div>

      {!cargado ? (
        cargando && <Esqueleto variante="lista" cantidad={5} texto="Cargando talleres…" />
      ) : visibles.length === 0 ? (
        talleres.length === 0 ? (
          <EstadoVacio icono="tool" titulo="Todavía no hay talleres"
            texto="Registra el primer taller aliado para que los clientes puedan cotizar servicios.">
            {puedeGestionar && (
              <button type="button" className="ui-btn ui-btn--primario" onClick={abrirNuevo}>
                <Icon name="plus" size={16} /> Registrar primer taller
              </button>
            )}
          </EstadoVacio>
        ) : (
          <EstadoVacio icono="search" titulo="Ningún taller coincide"
            texto="Prueba con otro nombre, ciudad o especialidad, o cambia el filtro.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        )
      ) : (
        <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
          <div className="ui-table-wrap">
            <table className="ui-table ui-table--tarjetas srv-tabla">
              <caption className="ui-sr">Talleres aliados</caption>
              <thead>
                <tr>
                  <th scope="col">Taller</th>
                  <th scope="col">Ciudad</th>
                  <th scope="col">Especialidades</th>
                  <th scope="col" className="pp-num">Mano de obra</th>
                  <th scope="col">Calificación</th>
                  <th scope="col">Estado</th>
                  <th scope="col"><span className="ui-sr">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {enPagina.map(t => {
                  const esp = t.especialidades || [];
                  return (
                    <tr key={t.id}>
                      <td className="ui-table__titulo">
                        <span className="srv-taller">
                          <LogoNegocio url={t.logoUrl} nombre={t.nombre} />
                          <span className="srv-taller__texto">
                            <strong>{t.nombre}</strong>
                            {t.direccion && <span className="pp-sub">{t.direccion}</span>}
                          </span>
                        </span>
                      </td>
                      <td data-label="Ciudad">{capitalizar(t.ciudad) || <span className="pp-tenue">Sin ciudad</span>}</td>
                      <td data-label="Especialidades">
                        {esp.length === 0 ? <span className="pp-tenue">Sin especialidades</span> : (
                          <span className="srv-especialidades" title={esp.join(", ")}>
                            {esp.slice(0, 2).map(s => <span className="srv-especialidad" key={s}>{s}</span>)}
                            {esp.length > 2 && <span className="srv-especialidad" aria-label={`y ${esp.length - 2} más`}>+{esp.length - 2}</span>}
                          </span>
                        )}
                      </td>
                      <td data-label="Mano de obra" className="pp-num">{formatPrecio(t.precioManoObra)}</td>
                      <td data-label="Calificación"><Calificacion rating={t.rating} calificaciones={t.calificaciones} compacta /></td>
                      <td data-label="Estado">
                        <ChipEstado tono={t.activo ? "ok" : "apagado"}>{t.activo ? "Activo" : "Pausado"}</ChipEstado>
                      </td>
                      <td className="ui-table__acciones">
                        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => verPedidos(t)}
                          aria-label={`Servicios de ${t.nombre}`}>Servicios</button>
                        {puedeGestionar && (
                          <>
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => abrirEditar(t)}
                              aria-label={`Editar ${t.nombre}`}><Icon name="pencil" size={14} /> Editar</button>
                            {t.activo ? (
                              <button type="button" className="ui-btn ui-btn--texto ui-btn--chico srv-peligro" onClick={() => setPausando(t)}
                                aria-label={`Pausar ${t.nombre}`}>Pausar</button>
                            ) : (
                              <button type="button" className="ui-btn ui-btn--primario ui-btn--chico" onClick={() => handleToggleActivo(t)}
                                aria-label={`Activar ${t.nombre}`}>Activar</button>
                            )}
                          </>
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
        <Paginacion pagina={pag.pagina} total={visibles.length} onCambiar={pag.cambiar} etiqueta="Páginas de talleres" />
      )}

      {pedidosDe && (
        <DrawerAdmin titulo={pedidosDe.nombre} etiqueta={`Servicios de ${pedidosDe.nombre}`}
          encabezado={(
            <div className="srv-drawer__cabecera">
              <LogoNegocio url={pedidosDe.logoUrl} nombre={pedidosDe.nombre} tamano="mediano" />
              <ChipEstado tono="info">Historial del taller</ChipEstado>
            </div>
          )}
          onCerrar={() => setPedidosDe(null)}>
          {error && <Alerta>{error}</Alerta>}
          {cargandoPedidos ? (
            <Esqueleto variante="lista" cantidad={3} texto="Cargando servicios…" />
          ) : pedidos.length === 0 ? (
            !error && <EstadoVacio icono="tool" nivel={3} titulo="Sin servicios todavía" texto="Este taller todavía no tiene servicios registrados." />
          ) : (
            <ul className="srv-ordenes">
              {pedidos.map(p => (
                <li key={p.id} className="srv-orden">
                  <span className="srv-orden__texto">
                    <strong>{p.vehiculoDescripcion || "Vehículo sin descripción"}</strong>
                    <span className="pp-sub">{p.productoNombre || "Sin repuesto"}</span>
                  </span>
                  <span className="srv-orden__lado">
                    <span className="srv-orden__total">{formatPrecio(p.total)}</span>
                    <ChipEstado catalogo={ESTADO_ORDEN} estado={p.estado} />
                  </span>
                </li>
              ))}
            </ul>
          )}
        </DrawerAdmin>
      )}

      {formAbierto && (
        <DrawerAdmin
          titulo={editandoId ? "Datos del taller" : "Registrar taller aliado"}
          encabezado={<ChipEstado tono="info">{editandoId ? "Editar" : "Nuevo"}</ChipEstado>}
          onCerrar={cerrarForm}
          pie={(
            <>
              <button type="button" className="ui-btn ui-btn--secundario" onClick={cerrarForm} disabled={guardando}>Cancelar</button>
              <button type="submit" form="srv-form" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
                {guardando ? "Guardando…" : editandoId ? "Guardar cambios" : "Crear taller"}
              </button>
            </>
          )}
        >
          <form id="srv-form" onSubmit={handleGuardar} className="srv-form">
            <section className="srv-form__bloque" aria-labelledby="srv-identidad">
              <h3 id="srv-identidad" className="srv-form__titulo">Identidad</h3>
              <Campo id="srv-nombre" label="Nombre">
                <input className="ui-field__input" required value={form.nombre} onChange={cambiar("nombre")} />
              </Campo>
              <div className="ui-form__campos ui-form__campos--2">
                <Campo id="srv-ciudad" label="Ciudad">
                  <input className="ui-field__input" required value={form.ciudad} onChange={cambiar("ciudad")} />
                </Campo>
                <Campo id="srv-mano-obra" label="Precio mano de obra" ayuda="En pesos, sin puntos.">
                  <input className="ui-field__input" type="number" step="any" min="0" inputMode="decimal" required
                    value={form.precioManoObra} onChange={cambiar("precioManoObra")} />
                </Campo>
              </div>
              <Campo id="srv-direccion" label="Dirección" opcional>
                <input className="ui-field__input" value={form.direccion} onChange={cambiar("direccion")} />
              </Campo>
              <Campo id="srv-especialidades" label="Especialidades" opcional ayuda="Sepáralas con comas.">
                <input className="ui-field__input" placeholder="Frenos, suspensión, eléctrico" value={form.especialidades} onChange={cambiar("especialidades")} />
              </Campo>
            </section>

            <section className="srv-form__bloque" aria-labelledby="srv-ubicacion">
              <h3 id="srv-ubicacion" className="srv-form__titulo">Ubicación en el mapa</h3>
              <SelectorUbicacion
                id="srv-mapa"
                ciudad={form.ciudad}
                value={form.latitud === "" || form.longitud === "" ? null : { latitud: Number(form.latitud), longitud: Number(form.longitud) }}
                onChange={({ latitud, longitud }) => setForm((f) => ({ ...f, latitud, longitud }))}
              />
            </section>

            {!editandoId && (
              <section className="srv-form__bloque" aria-labelledby="srv-acceso">
                <h3 id="srv-acceso" className="srv-form__titulo">Acceso del taller</h3>
                <p className="srv-form__ayuda">Se crea el usuario con el que el taller iniciará sesión.</p>
                <div className="ui-form__campos ui-form__campos--2">
                  <Campo id="srv-correo" label="Correo">
                    <input className="ui-field__input" type="email" inputMode="email" autoComplete="off" required
                      value={form.correoUsuario} onChange={cambiar("correoUsuario")} />
                  </Campo>
                  <Campo id="srv-clave" label="Contraseña">
                    <CampoClave autoComplete="new-password" required value={form.passwordUsuario} onChange={cambiar("passwordUsuario")} />
                  </Campo>
                </div>
              </section>
            )}
            {error && <Alerta>{error}</Alerta>}
          </form>
        </DrawerAdmin>
      )}

      {pausando && (
        <ConfirmacionAdmin
          titulo={`Pausar ${pausando.nombre}`}
          texto="El taller dejará de recibir solicitudes nuevas hasta que lo actives otra vez."
          confirmar="Pausar"
          variante="peligro"
          cargando={pausandoEnCurso}
          onConfirmar={confirmarPausa}
          onCancelar={() => setPausando(null)}
        />
      )}
    </div>
  );
}
