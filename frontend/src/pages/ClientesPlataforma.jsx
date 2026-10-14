import { useEffect, useState } from "react";
import { clientesPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";
import "../css/admin-base.css";
import { capitalizar } from "../utils/formato";

const TAMANO = 20;
const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short", year: "numeric" }) : "—");

const FILTROS_INICIALES = { q: "", ciudad: "", estado: "" };

/**
 * Clientes registrados, solo lectura y solo lo necesario para monitorear: nombre, correo parcial, ciudad,
 * cuántas compras y servicios llevan. No se muestra teléfono, dirección ni documento.
 */
export default function ClientesPlataforma({ token }) {
  const [borrador, setBorrador] = useState(FILTROS_INICIALES);
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [pagina, setPagina] = useState(0);
  const [estado, setEstado] = useState({ clave: null, datos: null, error: null });

  const clave = JSON.stringify([filtros, pagina]);

  useEffect(() => {
    let vivo = true;
    clientesPlataforma(token, { ...filtros, pagina, tamano: TAMANO })
      .then((datos) => { if (vivo) setEstado({ clave, datos, error: null }); })
      .catch((e) => { if (vivo) setEstado({ clave, datos: null, error: e.message }); });
    return () => { vivo = false; };
  }, [token, filtros, pagina, clave]);

  const cargando = estado.clave !== clave;
  const respuesta = estado.datos;
  const lista = respuesta?.clientes;
  const paginas = lista ? Math.max(1, Math.ceil(lista.total / lista.tamano)) : 1;

  const aplicar = (e) => {
    e.preventDefault();
    setPagina(0);
    setFiltros(borrador);
  };
  const cambiar = (campo) => (e) => {
    const siguiente = { ...borrador, [campo]: e.target.value };
    setBorrador(siguiente);
    if (campo !== "q") { setPagina(0); setFiltros(siguiente); }
  };
  const limpiar = () => { setBorrador(FILTROS_INICIALES); setFiltros(FILTROS_INICIALES); setPagina(0); };
  const hayFiltros = Object.values(filtros).some((v) => v !== "");

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Clientes</h1>
          <p className="adm-encabezado__sub">Clientes registrados en la plataforma, solo lectura.</p>
        </div>
        {lista && <span className="adm-encabezado__dato">{lista.total} {lista.total === 1 ? "cliente" : "clientes"}</span>}
      </header>

      <form className="adm-filtros" onSubmit={aplicar} role="search" aria-label="Filtros de clientes">
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar</span>
          <input type="search" value={borrador.q} onChange={cambiar("q")} placeholder="Nombre o correo" />
        </label>
        <label className="adm-campo">
          <span>Ciudad</span>
          <select value={borrador.ciudad} onChange={cambiar("ciudad")}>
            <option value="">Todas</option>
            {(respuesta?.ciudades || []).map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        <label className="adm-campo">
          <span>Estado</span>
          <select value={borrador.estado} onChange={cambiar("estado")}>
            <option value="">Todos</option>
            <option value="activos">Activos</option>
            <option value="inactivos">Inactivos</option>
          </select>
        </label>
        <div className="adm-filtros__acciones">
          <button type="submit" className="adm-btn">Buscar</button>
          {hayFiltros && <button type="button" className="adm-btn adm-btn--sutil" onClick={limpiar}>Limpiar</button>}
        </div>
      </form>

      {estado.error && <div className="adm-alerta adm-alerta--error" role="alert">{estado.error}</div>}

      <div className="adm-tarjeta" aria-busy={cargando}>
        {!lista && !estado.error ? (
          <div className="adm-cargando">Cargando clientes…</div>
        ) : lista && lista.items.length === 0 ? (
          <p className="adm-vacio">{hayFiltros ? "Ningún cliente coincide con los filtros." : "Todavía no hay clientes."}</p>
        ) : lista && (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Cliente</th><th>Ciudad</th><th>Estado</th><th>Registro</th>
                  <th className="adm-tabla__der">Compras</th><th className="adm-tabla__der">Servicios</th>
                </tr>
              </thead>
              <tbody>
                {lista.items.map((c) => (
                  <tr key={c.id}>
                    <td data-label="Cliente">
                      <strong>{c.nombre || "Sin nombre"}</strong>
                      <span className="adm-tabla__sub">{c.correo || "—"}</span>
                    </td>
                    <td data-label="Ciudad">{capitalizar(c.ciudad) || "—"}</td>
                    <td data-label="Estado">
                      <span className={`adm-badge ${c.activo ? "adm-badge--exito" : "adm-badge--neutro"}`}>
                        {c.activo ? "Activo" : "Inactivo"}
                      </span>
                    </td>
                    <td data-label="Registro">{fecha(c.registrado)}</td>
                    <td className="adm-tabla__der" data-label="Compras">{c.compras}</td>
                    <td className="adm-tabla__der" data-label="Servicios">{c.servicios}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {lista && lista.total > lista.tamano && (
        <nav className="adm-paginacion" aria-label="Paginación">
          <button type="button" className="adm-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {paginas}</span>
          <button type="button" className="adm-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  );
}
