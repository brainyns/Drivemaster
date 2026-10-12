import { useCallback, useEffect, useState } from "react";
import { pedidosPlataforma } from "../services/plataformaService";
import "../css/dashboard.css";
import "../css/dashboard-plataforma.css";
import "../css/admin-base.css";

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
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Pedidos y envíos</h1>
          <p className="adm-encabezado__sub">Lo que se vende en las tiendas y los envíos y retiros en curso. Se actualiza cada 30 segundos.</p>
        </div>
        <div className="adm-encabezado__lado dp-acciones">
          {actualizado && (
            <span className="adm-encabezado__dato">
              Actualizado {actualizado.toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
            </span>
          )}
          <button type="button" className="adm-btn" onClick={cargar} disabled={cargando}>{cargando ? "Actualizando…" : "Actualizar"}</button>
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
        <div className="adm-pestanas" role="tablist" aria-label="Estado del pedido">
          <button type="button" role="tab" aria-selected={filtros.estado === ""} className={`adm-pestana${filtros.estado === "" ? " adm-pestana--activa" : ""}`}
            onClick={() => poner({ estado: "" })}>Todos</button>
          {PESTANAS.filter((e) => datos.porEstado[e]).map((e) => (
            <button key={e} type="button" role="tab" aria-selected={filtros.estado === e}
              className={`adm-pestana${filtros.estado === e ? " adm-pestana--activa" : ""}`} onClick={() => poner({ estado: e })}>
              {ESTADOS[e]} <span className="adm-pestana__n">{datos.porEstado[e]}</span>
            </button>
          ))}
        </div>
      )}

      <form className="adm-filtros" onSubmit={(e) => e.preventDefault()} aria-label="Filtros de pedidos">
        <label className="adm-campo">
          <span>Tienda</span>
          <select value={filtros.tiendaId} onChange={(e) => poner({ tiendaId: e.target.value })}>
            <option value="">Todas</option>
            {(datos?.tiendas || []).map((t) => <option key={t.id} value={t.id}>{t.nombre}</option>)}
          </select>
        </label>
        <label className="adm-campo">
          <span>Entrega</span>
          <select value={filtros.entrega} onChange={(e) => poner({ entrega: e.target.value })}>
            <option value="">Envío y retiro</option>
            <option value="ENVIO">Envío a domicilio</option>
            <option value="RETIRO">Retiro en tienda</option>
          </select>
        </label>
        <label className="adm-campo adm-campo--check">
          <input type="checkbox" checked={filtros.atrasados} onChange={(e) => poner({ atrasados: e.target.checked })} />
          <span>Solo atrasados</span>
        </label>
        {hayFiltros && (
          <div className="adm-filtros__acciones">
            <button type="button" className="adm-btn adm-btn--sutil" onClick={() => poner(FILTROS_INICIALES)}>Limpiar</button>
          </div>
        )}
      </form>

      {error && <div className="adm-alerta adm-alerta--error" role="alert">{error}{datos ? " (se muestran los últimos datos)" : ""}</div>}

      <div className="adm-tarjeta" aria-busy={cargando}>
        {!lista && !error ? (
          <div className="adm-cargando">Cargando pedidos…</div>
        ) : lista && lista.items.length === 0 ? (
          <p className="adm-vacio">{hayFiltros ? "Ningún pedido coincide con los filtros." : "Todavía no hay pedidos."}</p>
        ) : lista && (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Pedido</th><th>Tienda</th><th>Estado</th><th>Entrega</th><th>Destino</th>
                  <th className="adm-tabla__der">Artículos</th><th className="adm-tabla__der">Días desde el pago</th><th>Alerta</th>
                </tr>
              </thead>
              <tbody>
                {lista.items.map((p) => (
                  <tr key={p.id} className={p.atrasado || p.problemaAbierto ? "adm-tabla__fila--alerta" : ""}>
                    <td data-label="Pedido">
                      <strong>#{String(p.id).slice(-6).toUpperCase()}</strong>
                      <span className="adm-tabla__sub">{p.origen === "TALLER" ? "Cotización de taller" : "Compra del catálogo"} · {fecha(p.fechaPago)}</span>
                    </td>
                    <td data-label="Tienda">{p.tienda || "—"}</td>
                    <td data-label="Estado"><span className="adm-badge adm-badge--neutro">{ESTADOS[p.estado] || p.estado}</span></td>
                    <td data-label="Entrega">{p.entrega === "ENVIO" ? "Envío" : p.entrega === "RETIRO" ? "Retiro" : "—"}</td>
                    <td data-label="Destino">{p.destino || "—"}</td>
                    <td className="adm-tabla__der" data-label="Artículos">{p.articulos}</td>
                    <td className="adm-tabla__der" data-label="Días desde el pago">{p.diasDesdePago}</td>
                    <td data-label="Alerta">
                      {p.atrasado && <span className="adm-badge adm-badge--peligro">Atrasado · plazo {fecha(p.plazoDespacho)}</span>}
                      {p.problemaAbierto && <span className="adm-badge adm-badge--peligro">Problema de envío</span>}
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
        <nav className="adm-paginacion" aria-label="Paginación">
          <button type="button" className="adm-btn" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>Anterior</button>
          <span>Página {pagina + 1} de {paginas}</span>
          <button type="button" className="adm-btn" disabled={pagina + 1 >= paginas} onClick={() => setPagina(pagina + 1)}>Siguiente</button>
        </nav>
      )}
    </div>
  );
}
