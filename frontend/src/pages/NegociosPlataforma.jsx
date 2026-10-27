import { Fragment, useCallback, useEffect, useState } from "react";
import { negociosPlataforma } from "../services/plataformaService";
import { resolverAprobacionTaller, activarDesactivarTaller } from "../services/tallerService";
import { resolverAprobacionTienda, activarDesactivarTienda } from "../services/tiendaService";
import { capitalizar, formatoFecha, unir } from "../utils/formato";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import ChipEstado from "../components/ui/ChipEstado";
import Calificacion from "../components/ui/Calificacion";
import LogoNegocio from "../components/ui/LogoNegocio";
import BarraProgreso from "../components/ui/BarraProgreso";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Campo from "../components/ui/Campo";
import Icon from "../components/public/Icon";
import DetallePlataforma from "../components/admin/DetallePlataforma";
import {
  ESTADOS_NEGOCIO, TIPOS_NEGOCIO, actividadNegocio, estadoNegocio, filtrosInicialesNegocios, perfilNegocio,
} from "./admin/plataformaAdmin";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/negocios-plataforma.css";

const TAMANO = 20;
const clave = (n) => `${n.tipo}-${n.id}`;
const nombreTipo = (n) => (n.tipo === "TIENDA" ? "Tienda" : "Taller");

/**
 * Lo que se hace con un negocio desde su fila: aprobar o rechazar con motivo (pendientes) y pausar o activar
 * (aprobados). Sin planes: DriveMaster cobra solo la comisión. El resultado lo informa la pantalla.
 */
function Gestion({ n, procesando, onAprobar, onRechazar, onAlternar, error }) {
  const k = clave(n);
  const [rechazando, setRechazando] = useState(false);
  const [motivo, setMotivo] = useState("");
  const [errorMotivo, setErrorMotivo] = useState(null);
  const ocupado = procesando === k;
  const perfil = perfilNegocio(n);

  const confirmarRechazo = () => {
    if (!motivo.trim()) { setErrorMotivo("Escribe el motivo del rechazo: se le enviará al negocio."); return; }
    onRechazar(n, motivo.trim());
  };

  return (
    <div className="neg-gestion" id={`neg-gestion-${k}`}>
      <ul className="neg-gestion__datos">
        {n.tipo === "TIENDA"
          ? <li>{n.productosActivos ?? 0} de {n.productos ?? 0} productos activos</li>
          : <li>{n.ordenesEntregadas ?? 0} de {n.ordenes ?? 0} órdenes entregadas</li>}
        <li>{perfil.completo ? "Perfil completo" : `Le falta: ${perfil.faltan.join(", ")}`}</li>
      </ul>
      {n.estado === "RECHAZADO" && n.motivoRechazo && (
        <p className="neg-gestion__motivo"><strong>Motivo del rechazo:</strong> {n.motivoRechazo}</p>
      )}

      {n.estado === "PENDIENTE" && (
        <div className="neg-gestion__fila">
          <button type="button" className="ui-btn ui-btn--primario" disabled={ocupado} aria-busy={ocupado}
            onClick={() => onAprobar(n)}>
            <Icon name="check" size={16} /> Aprobar
          </button>
          <button type="button" className="ui-btn ui-btn--secundario" aria-expanded={rechazando} disabled={ocupado}
            onClick={() => { setRechazando(!rechazando); setErrorMotivo(null); }}>
            Rechazar…
          </button>
        </div>
      )}

      {rechazando && (
        <div className="neg-gestion__fila">
          <Campo id={`neg-motivo-${k}`} label="Motivo del rechazo" ayuda="Se le envía al negocio para que pueda corregirlo."
            error={errorMotivo} className="neg-gestion__campo neg-gestion__campo--ancho">
            <input className="ui-field__input" value={motivo} maxLength={300}
              onChange={(e) => { setMotivo(e.target.value); if (errorMotivo) setErrorMotivo(null); }} />
          </Campo>
          <button type="button" className="ui-btn ui-btn--peligro" disabled={ocupado} aria-busy={ocupado} onClick={confirmarRechazo}>
            Confirmar rechazo
          </button>
        </div>
      )}

      {n.estado === "APROBADO" && (
        <div className="neg-gestion__fila">
          <button type="button" className={`ui-btn ${n.activo ? "ui-btn--secundario neg-pausar" : "ui-btn--primario"}`}
            disabled={ocupado} aria-busy={ocupado} onClick={() => onAlternar(n)}>
            {n.activo ? "Pausar" : "Activar"}
          </button>
        </div>
      )}

      {error && <Alerta>{error}</Alerta>}
    </div>
  );
}

/**
 * Tiendas y talleres en una sola lista (SUPERADMIN): estado de aprobación, calificación, actividad y perfil
 * completo. Desde aquí se aprueba, rechaza y pausa; los archivados se ven solo con su filtro. El ADMIN no ve esta pantalla: solo aprueba o
 * rechaza desde "Aprobaciones". filtroInicial ("TIENDA" o "TALLER") llega desde el dashboard.
 */
export default function NegociosPlataforma({ token, filtroInicial }) {
  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [filtros, setFiltros] = useState(() => filtrosInicialesNegocios(filtroInicial));
  const [pagina, setPagina] = useState(0);
  const [version, setVersion] = useState(0);
  const [detalle, setDetalle] = useState(null); // { tipo, id } del negocio cuyo detalle está abierto
  const [datos, setDatos] = useState(null); // { negocios: { items, total }, porEstado }
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);

  const [abierto, setAbierto] = useState(null);
  const [procesando, setProcesando] = useState(null);
  const [errorAccion, setErrorAccion] = useState(null);
  const [aviso, setAviso] = useState(null);

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
      const r = await negociosPlataforma(token, { ...filtros, q, pagina, tamano: TAMANO });
      if (!vivo()) return;
      setDatos(r);
      setError(null);
    } catch (e) {
      if (vivo()) setError(e.message);
    } finally {
      if (vivo()) setCargando(false);
    }
  }, [token, filtros, q, pagina]);

  useEffect(() => {
    let activo = true;
    const id = setTimeout(() => cargar(() => activo), 0);
    return () => { activo = false; clearTimeout(id); };
  }, [cargar, version]);


  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
  const poner = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(0); setAbierto(null); };
  const hayFiltros = q !== "" || filtros.tipo !== "" || filtros.estado !== "";
  const limpiar = () => { setBusqueda(""); setQ(""); setFiltros(filtrosInicialesNegocios()); setPagina(0); };

  const ejecutar = async (n, accion, mensaje) => {
    setProcesando(clave(n));
    setErrorAccion(null);
    setAviso(null);
    try {
      await accion();
      setAviso(mensaje);
      setAbierto(null);
      setVersion((v) => v + 1);
    } catch (e) {
      setErrorAccion(e.message);
    } finally {
      setProcesando(null);
    }
  };

  const aprobar = (n) => ejecutar(n,
    () => (n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda)(token, n.id, { aprobado: true }),
    `${n.nombre} quedó aprobado.`);
  const rechazar = (n, motivo) => ejecutar(n,
    () => (n.tipo === "TALLER" ? resolverAprobacionTaller : resolverAprobacionTienda)(token, n.id, { aprobado: false, motivo }),
    `${n.nombre} fue rechazado.`);
  const alternar = (n) => ejecutar(n,
    () => (n.tipo === "TALLER" ? activarDesactivarTaller : activarDesactivarTienda)(token, n.id, !n.activo),
    n.activo ? `${n.nombre} quedó en pausa.` : `${n.nombre} volvió a estar activo.`);

  const lista = datos?.negocios;
  const items = lista?.items || [];
  const total = lista?.total || 0;
  const porEstado = datos?.porEstado || {};
  // "Todos" no incluye los archivados (como el backend): se ven solo con su filtro
  const totalNegocios = Object.entries(porEstado).filter(([e]) => e !== "ARCHIVADO").reduce((s, [, n]) => s + n, 0);

  return (
    <div className="ss-panel--plataforma pp-root neg-root">
      <EncabezadoPagina titulo="Tiendas y talleres"
        descripcion="Aprobación, calificación, actividad y perfil de cada negocio del marketplace." />

      {aviso && <Alerta tono="ok" className="pp-bloque-sep" onCerrar={() => setAviso(null)}>{aviso}</Alerta>}

      <div className="pp-filtros">
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="neg-busqueda" className="ui-sr">Buscar por nombre o ciudad</label>
          <input id="neg-busqueda" className="ui-field__input" type="search" placeholder="Nombre o ciudad"
            value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </div>
        <div className="ui-filtros" role="group" aria-label="Tipo de negocio">
          {TIPOS_NEGOCIO.map((t) => (
            <button key={t.valor || "todos"} type="button" className="ui-chip" aria-pressed={filtros.tipo === t.valor}
              onClick={() => poner({ tipo: t.valor })}>{t.label}</button>
          ))}
        </div>
      </div>
      <div className="ui-filtros pp-bloque-sep" role="group" aria-label="Estado de aprobación">
        <button type="button" className="ui-chip" aria-pressed={filtros.estado === ""} onClick={() => poner({ estado: "" })}>
          Todos {datos && <span className="ui-chip__contador">{totalNegocios}</span>}
        </button>
        {Object.entries(ESTADOS_NEGOCIO).map(([e, info]) => (
          <button key={e} type="button" className="ui-chip" aria-pressed={filtros.estado === e} onClick={() => poner({ estado: e })}>
            {info.plural} {datos && <span className="ui-chip__contador">{porEstado[e] ?? 0}</span>}
          </button>
        ))}
      </div>

      {error && (
        // El servicio ya trae "No se pudieron cargar los negocios." o el permiso que falta
        <Alerta className="pp-bloque-sep" onReintentar={reintentar} reintentando={reintentando}>{error}</Alerta>
      )}

      {!datos ? (
        !error && <Esqueleto variante="lista" cantidad={6} texto="Cargando negocios…" />
      ) : items.length === 0 ? (
        hayFiltros ? (
          <EstadoVacio icono="search" titulo="Ningún negocio coincide"
            texto="Prueba con otro nombre o ciudad, o quita algún filtro.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        ) : (
          <EstadoVacio icono="box" titulo="Todavía no hay negocios"
            texto="Las tiendas y los talleres aparecen aquí cuando se registran con «Registra tu negocio»; los nuevos llegan como pendientes." />
        )
      ) : (
        <>
          <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--tarjetas neg-tabla">
                <caption className="ui-sr">Tiendas y talleres; los pendientes primero y después los más recientes</caption>
                <thead>
                  <tr>
                    <th scope="col">Negocio</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Calificación</th>
                    <th scope="col">Actividad</th>
                    <th scope="col">Perfil</th>
                    <th scope="col">Registro</th>
                    <th scope="col"><span className="ui-sr">Acciones</span></th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((n) => {
                    const k = clave(n);
                    const est = estadoNegocio(n);
                    const perfil = perfilNegocio(n);
                    const act = actividadNegocio(n);
                    const abiertoAqui = abierto === k;
                    return (
                      <Fragment key={k}>
                        <tr className={abiertoAqui ? "neg-fila--abierta" : undefined}>
                          <td className="ui-table__titulo">
                            <span className="neg-negocio">
                              <LogoNegocio url={n.logoUrl} nombre={n.nombre} />
                              <span className="neg-negocio__texto">
                                <button type="button" className="pp-nombre-boton" onClick={() => setDetalle({ tipo: n.tipo, id: n.id })}
                                  aria-label={`Ver el detalle de ${n.nombre || "este negocio"}`}>{n.nombre || "Sin nombre"}</button>
                                <span className="pp-sub">{unir([nombreTipo(n), capitalizar(n.ciudad) || "Sin ciudad"])}</span>
                              </span>
                            </span>
                          </td>
                          <td data-label="Estado"><ChipEstado tono={est.tono}>{est.label}</ChipEstado></td>
                          <td data-label="Calificación">
                            {n.tipo === "TALLER"
                              ? <Calificacion rating={n.calificacion} calificaciones={n.calificaciones} compacta />
                              : <span className="pp-tenue">No aplica</span>}
                          </td>
                          <td data-label="Actividad" className="neg-actividad">
                            {act.texto}<span className="pp-sub">{act.detalle}</span>
                          </td>
                          <td data-label="Perfil">
                            <span className="neg-perfil" title={perfil.completo ? undefined : `Falta: ${perfil.faltan.join(", ")}`}>
                              <BarraProgreso valor={perfil.pct} tono={perfil.completo ? "ok" : "acento"}
                                etiqueta={`Perfil de ${n.nombre}: ${perfil.completos} de ${perfil.total} datos`} />
                              <span className={perfil.completo ? "neg-perfil__ok" : "neg-perfil__falta"}>
                                {perfil.completo ? "Completo" : `Falta ${perfil.faltan.join(", ")}`}
                              </span>
                            </span>
                          </td>
                          <td data-label="Registro" className="neg-fecha">{formatoFecha(n.registrado)}</td>
                          <td className="ui-table__acciones">
                            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" aria-expanded={abiertoAqui}
                              aria-controls={abiertoAqui ? `neg-gestion-${k}` : undefined}
                              aria-label={`${abiertoAqui ? "Cerrar" : "Gestionar"} ${n.nombre}`}
                              onClick={() => { setAbierto(abiertoAqui ? null : k); setErrorAccion(null); }}>
                              {abiertoAqui ? "Cerrar" : n.estado === "PENDIENTE" ? "Revisar" : "Gestionar"}
                            </button>
                          </td>
                        </tr>
                        {abiertoAqui && (
                          <tr className="neg-fila-gestion">
                            <td colSpan={7}>
                              <Gestion n={n} procesando={procesando} error={errorAccion}
                                onAprobar={aprobar} onRechazar={rechazar} onAlternar={alternar} />
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
          <Paginacion pagina={pagina + 1} total={total} porPagina={TAMANO} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de negocios" />
        </>
      )}

      {detalle && (
        <DetallePlataforma key={`${detalle.tipo}-${detalle.id}`} token={token}
          objetivo={{ tipo: "negocio", negocioTipo: detalle.tipo, id: detalle.id }} onCerrar={() => setDetalle(null)} />
      )}
    </div>
  );
}
