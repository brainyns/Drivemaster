import { useEffect, useMemo, useState } from "react";
import { getToken, getUser } from "../services/authService";
import { listarTalleres, crearTaller, actualizarTaller, activarDesactivarTaller } from "../services/tallerService";
import { ordenesPorTallerAdmin } from "../services/ordenServicioService";
import SelectorUbicacion from "../components/mapa/SelectorUbicacion";
import DrawerAdmin from "../components/admin/DrawerAdmin";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";
import { capitalizar } from "../utils/formato";
import Calificacion from "../components/ui/Calificacion";
import { promedioDeTalleres } from "../utils/calificacion";
import "../css/admin-talleres.css";
import "../css/admin-base.css";

const ESTADO_LABEL = {
  SOLICITADA: "Solicitada", COTIZADA: "Cotizada", PAGADA: "Pagada",
  EN_PROCESO: "En proceso", FINALIZADA: "Finalizada", CANCELADA: "Cancelada",
  REASIGNADA: "Reasignada",
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

function BadgeOrden({ estado }) {
  const variante = {
    FINALIZADA: "exito",
    EN_PROCESO: "info",
    PAGADA: "pendiente",
    COTIZADA: "neutro",
    CANCELADA: "peligro",
  }[estado] || "neutro";
  return <span className={`adm-badge adm-badge--${variante}`}>{ESTADO_LABEL[estado] || estado}</span>;
}

export default function AdminTalleres() {
  // Crear, editar y pausar talleres es del SUPERADMIN; el ADMIN solo los ve y revisa sus servicios
  const puedeGestionar = getUser()?.rol === "SUPERADMIN";
  const token = getToken();
  const [talleres, setTalleres] = useState([]);
  const [cargando, setCargando] = useState(true);
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
    } catch (e) {
      setError(e.message);
    } finally {
      setCargando(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const abrirNuevo = () => {
    setEditandoId(null);
    setForm(VACIO);
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
    setPedidosDe({ id: t.id, nombre: t.nombre });
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

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Servicios</h1>
          <p className="adm-encabezado__sub">
            Talleres aliados conectados a DriveMaster. Desde aquí los activas, editas su
            tarifa de mano de obra y revisas los servicios que les hicieron.
          </p>
        </div>
        <div className="adm-encabezado__lado">
          {!cargando && (
            <span className="adm-encabezado__dato">
              {visibles.length} de {talleres.length} taller{talleres.length === 1 ? "" : "es"}
            </span>
          )}
          {puedeGestionar && (
            <button type="button" className="adm-btn adm-btn--primario" onClick={abrirNuevo}>Nuevo taller</button>
          )}
        </div>
      </header>

      {error && !formAbierto && !pedidosDe && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}

      <section className="at-stats" aria-label="Resumen de talleres">
        <div className="at-stat">
          <span className="at-stat-value">{resumen.total}</span>
          <span className="at-stat-label">Talleres registrados</span>
        </div>
        <div className="at-stat">
          <span className="at-stat-value at-stat-value--ok">{resumen.activos}</span>
          <span className="at-stat-label">Recibiendo servicios</span>
        </div>
        <div className="at-stat">
          <span className="at-stat-value">{resumen.inactivos}</span>
          <span className="at-stat-label">Pausados</span>
        </div>
        <div className="at-stat">
          <span className="at-stat-value">
            {resumen.promedio != null ? resumen.promedio.toFixed(1) : "—"}
          </span>
          <span className="at-stat-label">Calificación promedio</span>
        </div>
      </section>

      <div className="adm-filtros" role="search" aria-label="Filtros de talleres">
        <nav className="adm-pestanas" aria-label="Filtrar por estado">
          {FILTROS.map(f => (
            <button
              key={f.k}
              type="button"
              aria-pressed={filtro === f.k}
              className={`adm-pestana${filtro === f.k ? " adm-pestana--activa" : ""}`}
              onClick={() => setFiltro(f.k)}
            >
              {f.l}
              <span className="adm-pestana__n">{conteoFiltro[f.k]}</span>
            </button>
          ))}
        </nav>
        <span className="adm-filtros__separador" />
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input
            type="search"
            placeholder="Nombre, ciudad o especialidad"
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
          />
        </label>
      </div>

      <div className="adm-tarjeta" aria-busy={cargando}>
        {cargando ? (
          <div className="adm-cargando">Cargando talleres…</div>
        ) : visibles.length === 0 ? (
          <div className="adm-vacio">
            <p>
              {talleres.length === 0
                ? "Todavía no hay talleres. Registra el primer taller aliado para que los clientes puedan cotizar servicios."
                : "Ningún taller coincide con la búsqueda. Prueba con otro nombre, ciudad o especialidad, o cambia el filtro."}
            </p>
            {talleres.length === 0 ? (
              puedeGestionar && <button type="button" className="adm-btn adm-btn--primario" onClick={abrirNuevo}>Registrar primer taller</button>
            ) : (
              <button type="button" className="adm-btn" onClick={() => { setBusqueda(""); setFiltro("todos"); }}>
                Limpiar filtros
              </button>
            )}
          </div>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Taller</th>
                  <th>Ciudad</th>
                  <th>Especialidades</th>
                  <th className="adm-tabla__der">Mano de obra</th>
                  <th>Calificación</th>
                  <th>Estado</th>
                  <th className="adm-tabla__der">Acciones</th>
                </tr>
              </thead>
              <tbody>
                {visibles.map(t => (
                  <tr key={t.id}>
                    <td data-label="Taller">
                      <strong>{t.nombre}</strong>
                      {t.direccion && <span className="adm-tabla__sub">{t.direccion}</span>}
                    </td>
                    <td data-label="Ciudad">{capitalizar(t.ciudad) || "—"}</td>
                    <td data-label="Especialidades">
                      {(t.especialidades || []).length === 0 ? "—" : (
                        <span className="adm-badges">
                          {(t.especialidades || []).slice(0, 2).map(s => (
                            <span className="adm-badge adm-badge--neutro" key={s}>{s}</span>
                          ))}
                          {(t.especialidades || []).length > 2 && (
                            <span className="adm-badge adm-badge--neutro">+{(t.especialidades || []).length - 2}</span>
                          )}
                        </span>
                      )}
                    </td>
                    <td data-label="Mano de obra" className="adm-tabla__der">{formatPrecio(t.precioManoObra)}</td>
                    <td data-label="Calificación"><Calificacion rating={t.rating} calificaciones={t.calificaciones} compacta /></td>
                    <td data-label="Estado">
                      <span className={`adm-badge ${t.activo ? "adm-badge--exito" : "adm-badge--neutro"}`}>
                        {t.activo ? "Activo" : "Pausado"}
                      </span>
                    </td>
                    <td className="adm-tabla__der adm-tabla__celda--ancha">
                      <div className="adm-tabla__acciones">
                        <button type="button" className="adm-btn adm-btn--chico" onClick={() => verPedidos(t)}>Servicios</button>
                        {puedeGestionar && (
                          <>
                            <button type="button" className="adm-btn adm-btn--chico" onClick={() => abrirEditar(t)}>Editar</button>
                            {t.activo ? (
                              <button type="button" className="adm-btn adm-btn--chico adm-btn--peligro" onClick={() => setPausando(t)}>Pausar</button>
                            ) : (
                              <button type="button" className="adm-btn adm-btn--chico adm-btn--primario" onClick={() => handleToggleActivo(t)}>Activar</button>
                            )}
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {pedidosDe && (
        <DrawerAdmin
          titulo={pedidosDe.nombre}
          encabezado={<span className="adm-badge adm-badge--chico adm-badge--info">Historial del taller</span>}
          onCerrar={() => setPedidosDe(null)}
        >
          {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}
          {cargandoPedidos ? (
            <div className="adm-cargando">Cargando servicios…</div>
          ) : pedidos.length === 0 ? (
            <p className="adm-vacio">Este taller todavía no tiene servicios registrados.</p>
          ) : (
            <div className="adm-tarjeta">
              <table className="adm-tabla adm-tabla--tarjetas">
                <thead>
                  <tr>
                    <th>Vehículo</th>
                    <th>Repuesto</th>
                    <th className="adm-tabla__der">Total</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {pedidos.map(p => (
                    <tr key={p.id}>
                      <td data-label="Vehículo">{p.vehiculoDescripcion || "—"}</td>
                      <td data-label="Repuesto">{p.productoNombre || "—"}</td>
                      <td data-label="Total" className="adm-tabla__der">{formatPrecio(p.total)}</td>
                      <td data-label="Estado"><BadgeOrden estado={p.estado} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </DrawerAdmin>
      )}

      {formAbierto && (
        <DrawerAdmin
          titulo={editandoId ? "Datos del taller" : "Registrar taller aliado"}
          encabezado={<span className="adm-badge adm-badge--chico adm-badge--info">{editandoId ? "Editar" : "Nuevo"}</span>}
          onCerrar={cerrarForm}
          pie={(
            <>
              <button type="button" className="adm-btn" onClick={cerrarForm} disabled={guardando}>Cancelar</button>
              <button type="submit" form="at-form" className={`adm-btn adm-btn--primario${guardando ? " adm-btn--cargando" : ""}`}
                disabled={guardando} aria-busy={guardando}>
                {guardando ? "Guardando…" : editandoId ? "Guardar cambios" : "Crear taller"}
              </button>
            </>
          )}
        >
          <form id="at-form" onSubmit={handleGuardar} className="adm-drawer__form">
            <section className="adm-drawer__bloque">
              <h3 className="adm-drawer__subtitulo">Identidad</h3>
              <label className="adm-campo adm-campo--bloque">
                <span>Nombre</span>
                <input required value={form.nombre} onChange={e => setForm({ ...form, nombre: e.target.value })} />
              </label>
              <div className="adm-drawer__fila">
                <label className="adm-campo">
                  <span>Ciudad</span>
                  <input required value={form.ciudad} onChange={e => setForm({ ...form, ciudad: e.target.value })} />
                </label>
                <label className="adm-campo">
                  <span>Precio mano de obra</span>
                  <input type="number" step="any" required value={form.precioManoObra} onChange={e => setForm({ ...form, precioManoObra: e.target.value })} />
                </label>
              </div>
              <label className="adm-campo adm-campo--bloque">
                <span>Dirección</span>
                <input value={form.direccion} onChange={e => setForm({ ...form, direccion: e.target.value })} />
              </label>
              <label className="adm-campo adm-campo--bloque">
                <span>Especialidades</span>
                <input placeholder="Frenos, suspensión, eléctrico" value={form.especialidades} onChange={e => setForm({ ...form, especialidades: e.target.value })} />
                <span className="adm-campo__ayuda">Sepáralas con comas.</span>
              </label>
            </section>

            <section className="adm-drawer__bloque">
              <h3 className="adm-drawer__subtitulo">Ubicación en el mapa</h3>
              <SelectorUbicacion
                id="at-mapa"
                ciudad={form.ciudad}
                value={form.latitud === "" || form.longitud === "" ? null : { latitud: Number(form.latitud), longitud: Number(form.longitud) }}
                onChange={({ latitud, longitud }) => setForm((f) => ({ ...f, latitud, longitud }))}
              />
            </section>

            {!editandoId && (
              <section className="adm-drawer__bloque">
                <h3 className="adm-drawer__subtitulo">Acceso del taller</h3>
                <p className="adm-encabezado__sub adm-campo__ayuda">Se crea el usuario con el que el taller iniciará sesión.</p>
                <div className="adm-drawer__fila">
                  <label className="adm-campo">
                    <span>Correo</span>
                    <input type="email" required value={form.correoUsuario} onChange={e => setForm({ ...form, correoUsuario: e.target.value })} />
                  </label>
                  <label className="adm-campo">
                    <span>Contraseña</span>
                    <input type="password" required value={form.passwordUsuario} onChange={e => setForm({ ...form, passwordUsuario: e.target.value })} />
                  </label>
                </div>
              </section>
            )}
            {error && <p className="adm-alerta adm-alerta--error" role="alert">{error}</p>}
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
