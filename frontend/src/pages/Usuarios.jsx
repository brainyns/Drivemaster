import { useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "../services/http";
import { crearUsuario, eliminarUsuario, getUser } from "../services/authService";
import { usuariosPlataforma } from "../services/plataformaService";
import { errorDeRespuesta } from "../utils/errores";
import { formatoFecha } from "../utils/formato";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import ChipEstado from "../components/ui/ChipEstado";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import CampoClave from "../components/ui/CampoClave";
import LogoNegocio from "../components/ui/LogoNegocio";
import ConfirmarAccion from "../components/marketplace/ConfirmarAccion";
import Icon from "../components/public/Icon";
import {
  ROLES, ROLES_DEL_EQUIPO, etiquetaRol, rolesParaFiltrar, rolEditable, filaUsuario,
  erroresNuevoUsuario, erroresEdicion, cuerpoEdicion, erroresDelServidor, avisoDesactivar,
} from "./admin/usuariosAdmin";
import "../css/dashboard-plataforma.css";
import "../css/marketplace.css"; // el diálogo de ConfirmarAccion
import "../css/usuarios-admin.css";

const API_BASE = `${import.meta.env.VITE_API_URL || "http://localhost:8080"}/api`;
const TAMANO = 20;
const ESTADOS = [
  { valor: "", label: "Todas" },
  { valor: "activos", label: "Activas" },
  { valor: "inactivos", label: "Inactivas" },
];
const NUEVO = { nombre: "", correo: "", password: "", rol: "ADMIN" };

async function actualizarUsuario(id, body) {
  const res = await apiFetch(`${API_BASE}/usuarios/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw await errorDeRespuesta(res, "No se pudo guardar el usuario. Inténtalo de nuevo.");
  return res.json();
}

/** Lleva el foco al primer campo con error (o al primero del formulario) después de pintar. */
function enfocar(form, soloInvalido = false) {
  requestAnimationFrame(() => {
    const campo = form?.querySelector(soloInvalido ? "[aria-invalid='true']" : "input:not([disabled]), select:not([disabled])");
    campo?.focus();
  });
}

/** Crear una cuenta del equipo (ADMIN o SUPERADMIN). Valida en línea al salir de cada campo y al enviar. */
function FormNuevo({ onCreado, onCancelar }) {
  const ref = useRef(null);
  const [form, setForm] = useState(NUEVO);
  const [tocados, setTocados] = useState({});
  const [servidor, setServidor] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => { enfocar(ref.current); }, []);

  const validos = erroresNuevoUsuario(form);
  const errores = { ...servidor };
  for (const k of Object.keys(validos)) if (tocados[k]) errores[k] = validos[k];

  const cambiar = (e) => {
    const { name, value } = e.target;
    setForm((f) => ({ ...f, [name]: value }));
    if (servidor[name]) setServidor(({ [name]: _quitado, ...resto }) => resto); // eslint-disable-line no-unused-vars
  };
  const tocar = (e) => setTocados((t) => ({ ...t, [e.target.name]: true }));

  const enviar = async (e) => {
    e.preventDefault();
    setTocados({ nombre: true, correo: true, password: true, rol: true });
    setError(null);
    if (Object.keys(validos).length > 0) { enfocar(ref.current, true); return; }
    setGuardando(true);
    try {
      const creado = await crearUsuario({ ...form, nombre: form.nombre.trim(), correo: form.correo.trim() });
      onCreado(creado?.nombre || form.nombre.trim());
    } catch (err) {
      const campos = erroresDelServidor(err.message);
      if (campos) { setServidor(campos); enfocar(ref.current, true); } else setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <section className="ui-seccion ui-seccion--editando usr-panel" aria-labelledby="usr-nuevo-titulo">
      <div className="ui-seccion__cabecera">
        <span className="ui-seccion__icono" aria-hidden="true"><Icon name="user" size={18} /></span>
        <div className="ui-seccion__texto">
          <h2 id="usr-nuevo-titulo" className="ui-seccion__titulo">Nuevo usuario del equipo</h2>
          <p className="ui-seccion__desc">
            Para administradores y super admins. Las tiendas y los talleres se registran con «Registra tu negocio» y se
            aprueban en Aprobaciones, porque necesitan perfil.
          </p>
        </div>
      </div>
      <form ref={ref} className="ui-form" onSubmit={enviar} noValidate>
        <div className="ui-form__campos ui-form__campos--2">
          <Campo id="usr-nuevo-nombre" label="Nombre completo" error={errores.nombre}>
            <input className="ui-field__input" name="nombre" autoComplete="name" value={form.nombre} onChange={cambiar} onBlur={tocar} />
          </Campo>
          <Campo id="usr-nuevo-correo" label="Correo" error={errores.correo} ayuda="Con este correo inicia sesión.">
            <input className="ui-field__input" name="correo" type="email" autoComplete="off" inputMode="email"
              placeholder="nombre@drivemaster.co" value={form.correo} onChange={cambiar} onBlur={tocar} />
          </Campo>
          <Campo id="usr-nuevo-clave" label="Contraseña temporal" error={errores.password}
            ayuda="Mínimo 6 caracteres. Compártela por un canal seguro y pide que la cambie al entrar.">
            <CampoClave name="password" autoComplete="new-password" value={form.password} onChange={cambiar} onBlur={tocar} />
          </Campo>
          <Campo id="usr-nuevo-rol" label="Rol" error={errores.rol}>
            <select className="ui-field__input" name="rol" value={form.rol} onChange={cambiar} onBlur={tocar}>
              {ROLES_DEL_EQUIPO.map((r) => <option key={r} value={r}>{etiquetaRol(r)}</option>)}
            </select>
          </Campo>
        </div>
        {error && <Alerta>{error}</Alerta>}
        <div className="ui-form-pie">
          <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
            {guardando ? "Creando…" : "Crear usuario"}
          </button>
          <button type="button" className="ui-btn ui-btn--secundario" onClick={onCancelar} disabled={guardando}>Cancelar</button>
        </div>
      </form>
    </section>
  );
}

/** Editar nombre, contraseña y, en cuentas del equipo, el rol. */
function FormEditar({ usuario, onGuardado, onCancelar }) {
  const ref = useRef(null);
  const [form, setForm] = useState({ nombre: usuario.nombre, password: "", rol: usuario.rol });
  const [tocados, setTocados] = useState({});
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const editableRol = rolEditable(usuario.rol);

  useEffect(() => { enfocar(ref.current); }, []);

  const validos = erroresEdicion(form);
  const errores = {};
  for (const k of Object.keys(validos)) if (tocados[k]) errores[k] = validos[k];

  const cambiar = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
  const tocar = (e) => setTocados((t) => ({ ...t, [e.target.name]: true }));

  const enviar = async (e) => {
    e.preventDefault();
    setTocados({ nombre: true, password: true });
    setError(null);
    if (Object.keys(validos).length > 0) { enfocar(ref.current, true); return; }
    setGuardando(true);
    try {
      await actualizarUsuario(usuario.id, cuerpoEdicion(form, usuario));
      onGuardado(form.nombre.trim());
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <section className="ui-seccion ui-seccion--editando usr-panel" aria-labelledby="usr-editar-titulo">
      <div className="ui-seccion__cabecera">
        <LogoNegocio nombre={usuario.nombre} tamano="mediano" />
        <div className="ui-seccion__texto">
          <h2 id="usr-editar-titulo" className="ui-seccion__titulo">Editar a {usuario.nombre || "este usuario"}</h2>
          <p className="ui-seccion__desc">{usuario.correo} · {etiquetaRol(usuario.rol)}</p>
        </div>
      </div>
      <form ref={ref} className="ui-form" onSubmit={enviar} noValidate>
        <div className="ui-form__campos ui-form__campos--2">
          <Campo id="usr-editar-nombre" label="Nombre completo" error={errores.nombre}>
            <input className="ui-field__input" name="nombre" autoComplete="off" value={form.nombre} onChange={cambiar} onBlur={tocar} />
          </Campo>
          <Campo id="usr-editar-clave" label="Nueva contraseña" opcional error={errores.password}
            ayuda="Déjala vacía para mantener la actual.">
            <CampoClave name="password" autoComplete="new-password" value={form.password} onChange={cambiar} onBlur={tocar} />
          </Campo>
          {editableRol ? (
            <Campo id="usr-editar-rol" label="Rol"
              ayuda={usuario.rol === "VENDEDOR" ? "El rol de vendedor se retiró: reasígnala a administrador o super admin, o desactívala." : undefined}>
              <select className="ui-field__input" name="rol" value={form.rol} onChange={cambiar}>
                {!ROLES_DEL_EQUIPO.includes(usuario.rol) && <option value={usuario.rol} disabled>{etiquetaRol(usuario.rol)}</option>}
                {ROLES_DEL_EQUIPO.map((r) => <option key={r} value={r}>{etiquetaRol(r)}</option>)}
              </select>
            </Campo>
          ) : (
            <div className="ui-field usr-rol-fijo">
              <span className="ui-field__label">Rol</span>
              <p className="usr-rol-fijo__valor">{etiquetaRol(usuario.rol)}</p>
              <p className="ui-field__ayuda">El rol de un cliente, una tienda o un taller no se cambia: su perfil depende de él.</p>
            </div>
          )}
        </div>
        {error && <Alerta>{error}</Alerta>}
        <div className="ui-form-pie">
          <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
            {guardando ? "Guardando…" : "Guardar cambios"}
          </button>
          <button type="button" className="ui-btn ui-btn--secundario" onClick={onCancelar} disabled={guardando}>Cancelar</button>
        </div>
      </form>
    </section>
  );
}

/** Cuentas de DriveMaster (solo SUPERADMIN): quién entra, con qué rol y si puede iniciar sesión. */
export default function Usuarios({ token }) {
  const yo = getUser();
  const puedeGestionar = yo?.rol === "SUPERADMIN";

  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [rol, setRol] = useState("");
  const [estado, setEstado] = useState("");
  const [pagina, setPagina] = useState(0);
  const [version, setVersion] = useState(0);
  const [datos, setDatos] = useState(null); // { items, total, porRol }
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);

  const [panel, setPanel] = useState(null); // "nuevo" | usuario en edición
  const [desactivando, setDesactivando] = useState(null);
  const [activando, setActivando] = useState(null);
  const [aviso, setAviso] = useState(null); // { tono, texto }

  // La búsqueda va al servidor; espera a que se deje de escribir
  useEffect(() => {
    const termino = busqueda.trim();
    if (termino === q) return undefined;
    const id = setTimeout(() => { setQ(termino); setPagina(0); }, 300);
    return () => clearTimeout(id);
  }, [busqueda, q]);

  const cargar = useCallback(async (vivo = () => true) => {
    setCargando(true);
    try {
      const r = await usuariosPlataforma(token, { q, rol, estado, pagina, tamano: TAMANO });
      if (!vivo()) return;
      setDatos({ items: (r.usuarios?.items || []).map(filaUsuario), total: r.usuarios?.total || 0, porRol: r.porRol || {} });
      setError(null);
    } catch (e) {
      if (vivo()) setError(e.message);
    } finally {
      if (vivo()) setCargando(false);
    }
  }, [token, q, rol, estado, pagina]);

  useEffect(() => {
    let activo = true;
    const id = setTimeout(() => cargar(() => activo), 0);
    return () => { activo = false; clearTimeout(id); };
  }, [cargar, version]);

  const recargar = () => setVersion((v) => v + 1);
  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };

  const elegirRol = (r) => { setRol(r); setPagina(0); };
  const elegirEstado = (e) => { setEstado(e); setPagina(0); };
  const hayFiltros = q !== "" || rol !== "" || estado !== "";
  const limpiar = () => { setBusqueda(""); setQ(""); setRol(""); setEstado(""); setPagina(0); };

  const abrir = (siguiente) => {
    setAviso(null);
    setPanel(siguiente);
    window.scrollTo?.({ top: 0, behavior: "smooth" });
  };

  const confirmarDesactivar = async () => {
    await eliminarUsuario(desactivando.id);
    setAviso({ tono: "ok", texto: `Desactivaste la cuenta de ${desactivando.nombre}.` });
    setDesactivando(null);
    recargar();
  };

  const activar = async (u) => {
    setActivando(u.id);
    setAviso(null);
    try {
      await actualizarUsuario(u.id, { activo: true });
      setAviso({ tono: "ok", texto: `${u.nombre} ya puede iniciar sesión de nuevo.` });
      recargar();
    } catch (e) {
      setAviso({ tono: "error", texto: `No pudimos activar a ${u.nombre}. ${e.message}` });
    } finally {
      setActivando(null);
    }
  };

  const total = datos?.total || 0;
  const paginas = Math.max(1, Math.ceil(total / TAMANO));
  const porRol = datos?.porRol || {};
  const totalCuentas = Object.values(porRol).reduce((s, n) => s + n, 0);

  return (
    <div className="ss-panel--plataforma usr-root">
      <EncabezadoPagina titulo="Usuarios"
        descripcion="Las cuentas que entran a DriveMaster: su rol y si pueden iniciar sesión."
        acciones={puedeGestionar && panel !== "nuevo" && (
          <button type="button" className="ui-btn ui-btn--primario" onClick={() => abrir("nuevo")}>
            <Icon name="plus" size={16} /> Nuevo usuario del equipo
          </button>
        )} />

      {aviso && <Alerta tono={aviso.tono} className="usr-bloque" onCerrar={() => setAviso(null)}>{aviso.texto}</Alerta>}

      {panel === "nuevo" && (
        <FormNuevo onCancelar={() => setPanel(null)}
          onCreado={(nombre) => { setPanel(null); setAviso({ tono: "ok", texto: `Creaste la cuenta de ${nombre}.` }); recargar(); }} />
      )}
      {panel && panel !== "nuevo" && (
        <FormEditar key={panel.id} usuario={panel} onCancelar={() => setPanel(null)}
          onGuardado={(nombre) => { setPanel(null); setAviso({ tono: "ok", texto: `Guardaste los cambios de ${nombre}.` }); recargar(); }} />
      )}

      <div className="usr-filtros">
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="usr-busqueda" className="ui-sr">Buscar por nombre o correo</label>
          <input id="usr-busqueda" className="ui-field__input" type="search" placeholder="Nombre o correo"
            value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </div>
        <div className="ui-filtros" role="group" aria-label="Estado de la cuenta">
          {ESTADOS.map((e) => (
            <button key={e.valor || "todas"} type="button" className="ui-chip" aria-pressed={estado === e.valor}
              onClick={() => elegirEstado(e.valor)}>{e.label}</button>
          ))}
        </div>
      </div>
      <div className="ui-filtros usr-bloque" role="group" aria-label="Rol">
        <button type="button" className="ui-chip" aria-pressed={rol === ""} onClick={() => elegirRol("")}>
          Todos los roles {datos && <span className="ui-chip__contador">{totalCuentas}</span>}
        </button>
        {rolesParaFiltrar(porRol, rol).map((r) => (
          <button key={r} type="button" className="ui-chip" aria-pressed={rol === r} onClick={() => elegirRol(r)}>
            {ROLES[r].plural} {datos && <span className="ui-chip__contador">{porRol[r] || 0}</span>}
          </button>
        ))}
      </div>

      {error && (
        // El servicio ya trae el mensaje del backend o "No se pudieron cargar los usuarios."
        <Alerta className="usr-bloque" onReintentar={reintentar} reintentando={reintentando}>{error}</Alerta>
      )}

      {!datos ? (
        !error && <Esqueleto variante="lista" cantidad={5} texto="Cargando usuarios…" />
      ) : datos.items.length === 0 ? (
        hayFiltros ? (
          <EstadoVacio icono="search" titulo="Ningún usuario coincide"
            texto="Prueba con otro nombre o correo, o quita algún filtro.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        ) : (
          <EstadoVacio icono="users" titulo="Todavía no hay usuarios"
            texto="Crea la primera cuenta del equipo; los clientes, las tiendas y los talleres aparecen aquí cuando se registran.">
            {puedeGestionar && (
              <button type="button" className="ui-btn ui-btn--primario" onClick={() => abrir("nuevo")}>
                <Icon name="plus" size={16} /> Nuevo usuario del equipo
              </button>
            )}
          </EstadoVacio>
        )
      ) : (
        <>
          <div className={`ui-card ui-table-card${cargando ? " usr-actualizando" : ""}`} aria-busy={cargando}>
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--tarjetas">
                <caption className="ui-sr">Usuarios, los más recientes primero</caption>
                <thead>
                  <tr>
                    <th scope="col">Usuario</th>
                    <th scope="col">Rol</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Creada</th>
                    <th scope="col">Último acceso</th>
                    {puedeGestionar && <th scope="col"><span className="ui-sr">Acciones</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {datos.items.map((u) => {
                    const soyYo = yo?.id && yo.id === u.id;
                    return (
                      <tr key={u.id}>
                        <td data-label="Usuario" className="ui-table__titulo">
                          <span className="usr-persona">
                            <LogoNegocio nombre={u.nombre} />
                            <span className="usr-persona__texto">
                              <strong>{u.nombre || "Sin nombre"}{soyYo && <span className="usr-tu">Tú</span>}</strong>
                              <span className="usr-persona__correo">{u.correo}</span>
                            </span>
                          </span>
                        </td>
                        <td data-label="Rol">
                          {u.rol === "VENDEDOR" ? <ChipEstado tono="aviso">{etiquetaRol(u.rol)}</ChipEstado> : etiquetaRol(u.rol)}
                        </td>
                        <td data-label="Estado">
                          <ChipEstado tono={u.activo ? "ok" : "apagado"}>{u.activo ? "Activa" : "Inactiva"}</ChipEstado>
                        </td>
                        <td data-label="Creada" className="usr-fecha">{formatoFecha(u.fechaCreacion)}</td>
                        <td data-label="Último acceso" className="usr-fecha">
                          {u.ultimoLogin ? formatoFecha(u.ultimoLogin) : <span className="usr-tenue">Nunca ha entrado</span>}
                        </td>
                        {puedeGestionar && (
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => abrir(u)}
                              aria-label={`Editar a ${u.nombre}`}>
                              <Icon name="pencil" size={14} /> Editar
                            </button>
                            {u.activo ? (
                              !soyYo && (
                                <button type="button" className="ui-btn ui-btn--texto ui-btn--chico usr-desactivar"
                                  onClick={() => setDesactivando(u)} aria-label={`Desactivar a ${u.nombre}`}>
                                  Desactivar
                                </button>
                              )
                            ) : (
                              <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => activar(u)}
                                disabled={activando === u.id} aria-busy={activando === u.id} aria-label={`Activar a ${u.nombre}`}>
                                {activando === u.id ? "Activando…" : "Activar"}
                              </button>
                            )}
                          </td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
          <nav className="usr-paginas" aria-label="Páginas de usuarios">
            <span>Mostrando {pagina * TAMANO + 1}–{pagina * TAMANO + datos.items.length} de {total}</span>
            {paginas > 1 && (
              <span className="usr-paginas__botones">
                <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={pagina === 0}
                  onClick={() => setPagina(pagina - 1)}>Anterior</button>
                <span aria-current="page">Página {pagina + 1} de {paginas}</span>
                <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={pagina + 1 >= paginas}
                  onClick={() => setPagina(pagina + 1)}>Siguiente</button>
              </span>
            )}
          </nav>
        </>
      )}

      {desactivando && (
        <ConfirmarAccion titulo={`¿Desactivar la cuenta de ${desactivando.nombre}?`} peligro textoConfirmar="Desactivar cuenta"
          mensaje={avisoDesactivar(desactivando)}
          onConfirmar={confirmarDesactivar} onCancelar={() => setDesactivando(null)} />
      )}
    </div>
  );
}
