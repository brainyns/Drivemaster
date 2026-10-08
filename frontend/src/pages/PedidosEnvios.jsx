import { useCallback, useEffect, useState } from "react";
import { pedidosPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";

const TAMANO = 20;
const REFRESCO_MS = 30000;

const ESTADOS = {
  PENDIENTE: "Por despachar",
  EN_PREPARACION: "En preparación",
  DESPACHADO: "Despachado",
  LISTO_PARA_RETIRAR: "Listo para retirar",
  RECIBIDO: "Recibido",
  RETIRADO: "Retirado",
  ENTREGADO: "Entregado",
  NO_DESPACHADO: "No despachado",
  NO_ENTREGADO: "No entregado",
};
// Los que se muestran como pestañas, en el orden en que avanza un pedido
const PESTANAS = ["PENDIENTE", "EN_PREPARACION", "DESPACHADO", "LISTO_PARA_RETIRAR", "RECIBIDO", "RETIRADO", "ENTREGADO", "NO_DESPACHADO", "NO_ENTREGADO"];

const fecha = (iso) => (iso ? new Date(iso).toLocaleDateString("es-CO", { day: "2-digit", month: "short" }) : "—");

const FILTROS_INICIALES = { estado: "", tiendaId: "", entrega: "", atrasados: false };

/**
 * Monitor de pedidos y envíos de todas las tiendas (ADMIN y SUPERADMIN). Solo lectura y sin datos del cliente:
 * estado, tienda, destino (ciudad), días desde el pago y alertas de atraso. Se actualiza solo cada 30 segundos.
 */
export default function PedidosEnvios({ token, filtroInicial }) {
  const [filtros, setFiltros] = useState(() => (filtroInicial === "atrasados" ? { ...FILTROS_INICIALES, atrasados: true } : FILTROS_INICIALES));
  const [pagina, setPagina] = useState(0);
  const [estado, setEstado] = useState({ datos: null, error: null, actualizado: null });
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    pedidosPlataforma(token, { ...filtros, atrasados: filtros.atrasados ? "true" : "", pagina, tamano: TAMANO })
      .then((datos) => setEstado({ datos, error: null, actualizado: new Date() }))
      .catch((e) => setEstado((previo) => ({ ...previo, error: e.message })))
      .finally(() => setCargando(false));
  }, [token, filtros, pagina]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const intervalo = setInterval(cargar, REFRESCO_MS);
    return () => { clearTimeout(inicial); clearInterval(intervalo); };
  }, [cargar]);

  const { datos, error, actualizado } = estado;
  const lista = datos?.pedidos;
  const paginas = lista ? Math.max(1, Math.ceil(lista.total / lista.tamano)) : 1;
  const hayFiltros = filtros.estado !== "" || filtros.tiendaId !== "" || filtros.entrega !== "" || filtros.atrasados;

  const poner = (cambio) => { setPagina(0); setFiltros((f) => ({ ...f, ...cambio })); };

  return (
    <div className="db-root dp-root">
      <header className="db-header">
        <div>
          <h1 className="db-h1">Pedidos y envíos</h1>
          <p className="db-sub">Lo que se vende en las tiendas y los envíos y retiros en curso. Se actualiza cada 30 segundos.</p>
        </div>
        <div className="db-header-right dp-acciones">
          {actualizado && (
            <span className="db-date">
              Actualizado {actualizado.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
            </span>
          )}
          <button type="button" className="dp-btn" onClick={cargar} disabled={cargando}>{cargando ? "Actualizando…" : "Actualizar"}</button>
        </div>
      </header>

      {datos && (datos.atrasados > 0 || datos.problemasAbiertos > 0) && (
        <section aria-label="Alertas" className="dp-alertas">
          {datos.atrasados > 0 && (
            <button type="button" className={`dp-alerta dp-alerta--critica${filtros.atrasados ? " dp-alerta--activa" : ""}`}
              onClick={() => poner({ atrasados: !filtros.atrasados })}>
              <strong>{datos.atrasados}</strong> pedido(s) sin despachar fuera del plazo
            </button>
          )}
          {datos.problemasAbiertos > 0 && (
            <button type="button" className={`dp-alerta dp-alerta--critica${filtros.estado === "PROBLEMA" ? " dp-alerta--activa" : ""}`}
              onClick={() => poner({ estado: filtros.estado === "PROBLEMA" ? "" : "PROBLEMA" })}>
              <strong>{datos.problemasAbiertos}</strong> problema(s) de envío abierto(s)
            </button>
          )}
        </section>
      )}

      {datos && (
        <div className="dp-pestanas" role="tablist" aria-label="Estado del pedido">
          <button type="button" role="tab" aria-selected={filtros.estado === ""} className={`dp-pestana${filtros.estado === "" ? " dp-pestana--activa" : ""}`}
            onClick={() => poner({ estado: "" })}>Todos</button>
          {PESTANAS.filter((e) => datos.porEstado[e]).map((e) => (
            <button key={e} type="button" role="tab" aria-selected={filtros.estado === e}
              className={`dp-pestana${filtros.estado === e ? " dp-pestana--activa" : ""}`} onClick={() => poner({ estado: e })}>
              {ESTADOS[e]} <span className="dp-pestana__n">{datos.porEstado[e]}</span>
            </button>
          ))}
        </div>
      )}

      <form className="dp-filtros" onSubmit={(e) => e.preventDefault()} aria-label="Filtros de pedidos">
        <label className="dp-campo">
          <span>Tienda</span>
          <select value={filtros.tiendaId} onChange={(e) => poner({ tiendaId: e.target.value })}>
            <option value="">Todas</option>
            {(datos?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <label className="dp-campo">
          <span>Entrega</span>
          <select value={filtros.entrega} onChange={(e) => poner({ entrega: e.target.value })}>
            <option value="">Envío y retiro</option>
            <option value="ENVIO">Envío a domicilio</option>
            <option value="RETIRO">Retiro en tienda</option>
          </select>
        </label>
        <label className="dp-campo dp-campo--check">
          <input type="checkbox" checked={filtros.atrasados} onChange={(e) => poner({ atrasados: e.target.checked })} />
          <span>Solo atrasados</span>
        </label>
        {hayFiltros && (
          <div className="dp-filtros__acciones">
            <button type="button" className="dp-btn dp-btn--sutil" onClick={() => poner(FILTROS_INICIALES)}>Limpiar</button>
          </div>
        )}
      </form>

      {error && <div className="db-error" role="alert">{error}{datos ? " (se muestran los últimos datos)" : ""}</div>}

      <div className="db-card dp-lista" aria-busy={cargando}>
        {!lista && !error ? (
          <div className="db-loading"><div className="db-spinner" /> Cargando pedidos…</div>
        ) : lista && lista.items.length === 0 ? (
          <p className="db-empty">{hayFiltros ? "Ningún pedido coincide con los filtros." : "Todavía no hay pedidos."}</p>
        ) : lista && (
          <div className="db-table-wrap">
            <table className="db-table">
              <thead>
                <tr>
                  <th>Pedido</th><th>Tienda</th><th>Estado</th><th>Entrega</th><th>Destino</th>
                  <th className="dp-der">Artículos</th><th className="dp-der">Días desde el pago</th><th>Alerta</th>
                </tr>
              </thead>
              <tbody>
                {lista.items.map((p) => (
                  <tr key={p.id} className={p.atrasado || p.problemaAbierto ? "dp-fila--alerta" : ""}>
                    <td>
                      <strong>#{String(p.id).slice(-6).toUpperCase()}</strong>
                      <span className="dp-sub-celda">{p.origen === "TALLER" ? "Cotización de taller" : "Compra del catálogo"} · {fecha(p.fechaPago)}</span>
                    </td>
                    <td>{p.tienda || "—"}</td>
                    <td><span className="dp-etiqueta">{ESTADOS[p.estado] || p.estado}</span></td>
                    <td>{p.entrega === "ENVIO" ? "Envío" : p.entrega === "RETIRO" ? "Retiro" : "—"}</td>
                    <td>{p.destino || "—"}</td>
                    <td className="dp-der">{p.articulos}</td>
                    <td className="dp-der">{p.diasDesdePago}</td>
                    <td>
                      {p.atrasado && <span className="dp-etiqueta dp-etiqueta--alerta">Atrasado · plazo {fecha(p.plazoDespacho)}</span>}
                      {p.problemaAbierto && <span className="dp-etiqueta dp-etiqueta--alerta">Problema de envío</span>}
                      {!p.atrasado && !p.problemaAbierto && "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {lista && lista.total > lista.tamano && (
        <nav className="dp-paginacion" aria-label="Paginación">
          <button type="button" className="dp-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {paginas}</span>
          <button type="button" className="dp-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  );
}
