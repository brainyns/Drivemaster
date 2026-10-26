import { useCallback, useEffect, useState } from "react";
import { clientesPlataforma } from "../services/plataformaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Paginacion from "../components/ui/Paginacion";
import ChipEstado from "../components/ui/ChipEstado";
import LogoNegocio from "../components/ui/LogoNegocio";
import Alerta from "../components/ui/Alerta";
import EstadoVacio from "../components/ui/EstadoVacio";
import Esqueleto from "../components/ui/Esqueleto";
import Icon from "../components/public/Icon";
import { capitalizar } from "../utils/formato";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";
import "../css/listas-plataforma.css";

const TAMANO = 20;
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

const FILTROS_INICIALES = { ciudad: "", estado: "" };
const ESTADOS = [
  { valor: "", label: "Todos" },
  { valor: "activos", label: "Activos" },
  { valor: "inactivos", label: "Inactivos" },
];

/**
 * Clientes registrados, solo lectura y solo lo necesario para monitorear: nombre, correo parcial, ciudad,
 * cuántas compras y servicios llevan. No se muestra teléfono, dirección ni documento.
 */
export default function ClientesPlataforma({ token }) {
  const [busqueda, setBusqueda] = useState("");
  const [q, setQ] = useState("");
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [pagina, setPagina] = useState(0);
  const [datos, setDatos] = useState(null); // { clientes: { items, total }, ciudades }
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);
  const [reintentando, setReintentando] = useState(false);

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
      const r = await clientesPlataforma(token, { ...filtros, q, pagina, tamano: TAMANO });
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
  }, [cargar]);

  const reintentar = async () => { setReintentando(true); await cargar(); setReintentando(false); };
  const poner = (cambio) => { setFiltros((f) => ({ ...f, ...cambio })); setPagina(0); };
  const limpiar = () => { setBusqueda(""); setQ(""); setFiltros(FILTROS_INICIALES); setPagina(0); };
  const hayFiltros = q !== "" || Object.values(filtros).some((v) => v !== "");

  const lista = datos?.clientes;
  const items = lista?.items || [];
  const total = lista?.total || 0;

  return (
    <div className="ss-panel--plataforma pp-root lst-root">
      <EncabezadoPagina titulo="Clientes"
        descripcion="Clientes registrados en la plataforma, solo lectura. Sin teléfono, dirección ni documento." />

      <div className="pp-filtros">
        <div className="ui-buscador">
          <Icon name="search" size={18} />
          <label htmlFor="cli-busqueda" className="ui-sr">Buscar por nombre o correo</label>
          <input id="cli-busqueda" className="ui-field__input" type="search" placeholder="Nombre o correo"
            value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </div>
        <label className="pp-filtros__select">
          Ciudad
          <select className="ui-field__input" value={filtros.ciudad} onChange={(e) => poner({ ciudad: e.target.value })}>
            <option value="">Todas</option>
            {(datos?.ciudades || []).map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        <div className="ui-filtros" role="group" aria-label="Estado de la cuenta">
          {ESTADOS.map((x) => (
            <button key={x.valor || "todos"} type="button" className="ui-chip" aria-pressed={filtros.estado === x.valor}
              onClick={() => poner({ estado: x.valor })}>{x.label}</button>
          ))}
        </div>
        {hayFiltros && <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={limpiar}>Limpiar filtros</button>}
      </div>

      {error && <Alerta className="pp-bloque-sep" onReintentar={reintentar} reintentando={reintentando}>{error}</Alerta>}

      {!datos ? (
        !error && <Esqueleto variante="lista" cantidad={6} texto="Cargando clientes…" />
      ) : items.length === 0 ? (
        hayFiltros ? (
          <EstadoVacio icono="search" titulo="Ningún cliente coincide" texto="Prueba con otro nombre o correo, o quita algún filtro.">
            <button type="button" className="ui-btn ui-btn--secundario" onClick={limpiar}>Limpiar filtros</button>
          </EstadoVacio>
        ) : (
          <EstadoVacio icono="users" titulo="Todavía no hay clientes"
            texto="Los clientes aparecen aquí cuando se registran para comprar en el catálogo o pedir un servicio." />
        )
      ) : (
        <>
          <div className={`ui-card ui-table-card${cargando ? " pp-actualizando" : ""}`} aria-busy={cargando}>
            <div className="ui-table-wrap">
              <table className="ui-table ui-table--tarjetas">
                <caption className="ui-sr">Clientes registrados</caption>
                <thead>
                  <tr>
                    <th scope="col">Cliente</th>
                    <th scope="col">Ciudad</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Registro</th>
                    <th scope="col" className="pp-num">Compras</th>
                    <th scope="col" className="pp-num">Servicios</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((c) => (
                    <tr key={c.id}>
                      <td className="ui-table__titulo">
                        <span className="lst-item">
                          <LogoNegocio nombre={c.nombre} />
                          <span className="lst-item__texto">
                            <strong>{c.nombre || "Sin nombre"}</strong>
                            <span className="pp-sub lst-correo">{c.correo || "Sin correo"}</span>
                          </span>
                        </span>
                      </td>
                      <td data-label="Ciudad">{capitalizar(c.ciudad) || <span className="pp-tenue">Sin ciudad</span>}</td>
                      <td data-label="Estado"><ChipEstado tono={c.activo ? "ok" : "apagado"}>{c.activo ? "Activo" : "Inactivo"}</ChipEstado></td>
                      <td data-label="Registro" className="lst-fecha">{fecha(c.registrado)}</td>
                      <td data-label="Compras" className="pp-num">{c.compras}</td>
                      <td data-label="Servicios" className="pp-num">{c.servicios}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Paginacion pagina={pagina + 1} total={total} porPagina={TAMANO} onCambiar={(p) => setPagina(p - 1)} etiqueta="Páginas de clientes" />
        </>
      )}
    </div>
  );
}
