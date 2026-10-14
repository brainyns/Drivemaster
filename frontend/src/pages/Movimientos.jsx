import { useEffect, useState } from "react";
import {
  listarMovimientos,
  registrarMovimiento,
} from "../services/movimientoService";
import { listarProductos, listarProductosMiTienda } from "../services/productoService";
import "../css/movimientos.css";
import "../css/admin-base.css";
import ConfirmacionAdmin from "../components/admin/ConfirmacionAdmin";

// esTienda: la tienda solo ve y ajusta su propio inventario (el backend ya filtra los movimientos).
function Movimientos({ token, esTienda = false }) {
  const [movimientos, setMovimientos] = useState([]);
  const [productos, setProductos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pagina, setPagina] = useState(1);
  const POR_PAGINA = 10;

  const [filtros, setFiltros] = useState({
    busqueda: "",
    tipo: "",
    fechaInicio: "",
    fechaFin: "",
  });

  const [filtrosAplicados, setFiltrosAplicados] = useState({
    busqueda: "",
    tipo: "",
    fechaInicio: "",
    fechaFin: "",
  });

  const [mostrarModal, setMostrarModal] = useState(false);
  const [ajuste, setAjuste] = useState({
    productoId: "",
    tipo: "AJUSTE",
    cantidad: "",
    motivo: "",
  });
  const [loadingAjuste, setLoadingAjuste] = useState(false);

  useEffect(() => {
    (esTienda ? listarProductosMiTienda(token) : listarProductos(token))
      .then(setProductos)
      .catch(() => {});
    cargar();
  }, [token, esTienda]);

  const cargar = async () => {
    setLoading(true);
    try {
      const data = await listarMovimientos({}, token);
      setMovimientos(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const aplicarFiltros = (e) => {
    e.preventDefault();
    setPagina(1);
    setFiltrosAplicados({ ...filtros });
  };

  const getNombre = (id) => {
    const p = productos.find((p) => p.id === id);
    return p ? p.nombre : id;
  };

  const getCodigo = (id) => {
    const p = productos.find((p) => p.id === id);
    return p ? p.codigo : "—";
  };

  const getInicial = (id) => {
    const nombre = getNombre(id);
    return nombre?.[0]?.toUpperCase() || "?";
  };

  const formatFecha = (fecha) => {
    if (!fecha) return { fecha: "—", hora: "—" };
    const d = new Date(fecha);
    return {
      fecha: d.toLocaleDateString("es-CO", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }),
      hora: d.toLocaleTimeString("es-CO", {
        hour: "2-digit",
        minute: "2-digit",
      }),
    };
  };

  const totalEntradas = movimientos
    .filter((m) => m.tipo === "ENTRADA")
    .reduce((a, m) => a + m.cantidad, 0);
  const totalSalidas = movimientos
    .filter((m) => m.tipo === "SALIDA")
    .reduce((a, m) => a + m.cantidad, 0);
  const totalAjustes = movimientos.filter((m) => m.tipo === "AJUSTE").length;

  const filtradosBusqueda = movimientos.filter((m) => {
    const nombre = getNombre(m.productoId).toLowerCase();
    const codigo = getCodigo(m.productoId).toLowerCase();
    const q = filtrosAplicados.busqueda.toLowerCase();

    const matchBusqueda = !q || nombre.includes(q) || codigo.includes(q);

    const matchTipo =
      !filtrosAplicados.tipo || m.tipo === filtrosAplicados.tipo;

    const fechaMov = m.fecha ? new Date(m.fecha) : null;
    const desde = filtrosAplicados.fechaInicio
      ? new Date(filtrosAplicados.fechaInicio)
      : null;
    const hasta = filtrosAplicados.fechaFin
      ? new Date(filtrosAplicados.fechaFin + "T23:59:59")
      : null;
    const matchDesde = !desde || !fechaMov || fechaMov >= desde;
    const matchHasta = !hasta || !fechaMov || fechaMov <= hasta;

    return matchBusqueda && matchTipo && matchDesde && matchHasta;
  });

  const totalPags = Math.ceil(filtradosBusqueda.length / POR_PAGINA);
  const paginados = filtradosBusqueda.slice(
    (pagina - 1) * POR_PAGINA,
    pagina * POR_PAGINA
  );

  const handleAjuste = async () => {
    if (!ajuste.productoId || !ajuste.motivo || !ajuste.cantidad) return;
    setLoadingAjuste(true);
    try {
      await registrarMovimiento(
        { ...ajuste, cantidad: Number(ajuste.cantidad) },
        token
      );
      setMostrarModal(false);
      setAjuste({ productoId: "", tipo: "AJUSTE", cantidad: "", motivo: "" });
      cargar();
    } catch (e) {
      alert(e.message);
    } finally {
      setLoadingAjuste(false);
    }
  };

  // Variante del badge y signo de cada tipo de movimiento (admin-base.css)
  const TIPO = {
    ENTRADA: { label: "Entrada", badge: "exito", signo: "+" },
    SALIDA:  { label: "Salida",  badge: "peligro", signo: "-" },
    AJUSTE:  { label: "Ajuste",  badge: "pendiente", signo: "" },
  };
  const desdeN = filtradosBusqueda.length === 0 ? 0 : Math.min((pagina - 1) * POR_PAGINA + 1, filtradosBusqueda.length);
  const hastaN = Math.min(pagina * POR_PAGINA, filtradosBusqueda.length);

  return (
    <div className="adm-pagina">
      <header className="adm-encabezado">
        <div>
          <h1 className="adm-encabezado__titulo">Inventario</h1>
          <p className="adm-encabezado__sub">Entradas, salidas y ajustes de stock de tus productos, con su motivo y referencia.</p>
        </div>
        <div className="adm-encabezado__lado">
          <button type="button" className="adm-btn adm-btn--primario" onClick={() => setMostrarModal(true)}>
            Ajuste manual
          </button>
        </div>
      </header>

      <section className="adm-kpis" aria-label="Resumen de movimientos">
        <div className="adm-kpi">
          <span className="adm-kpi__icono adm-kpi__icono--info" aria-hidden="true">≡</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Movimientos</span>
            <p className="adm-kpi__valor">{movimientos.length.toLocaleString("es-CO")}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono adm-kpi__icono--exito" aria-hidden="true">↓</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Unidades que entraron</span>
            <p className="adm-kpi__valor">{totalEntradas.toLocaleString("es-CO")}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono adm-kpi__icono--peligro" aria-hidden="true">↑</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Unidades que salieron</span>
            <p className="adm-kpi__valor">{totalSalidas.toLocaleString("es-CO")}</p>
          </div>
        </div>
        <div className="adm-kpi">
          <span className="adm-kpi__icono adm-kpi__icono--pendiente" aria-hidden="true">⚙</span>
          <div className="adm-kpi__texto">
            <span className="adm-kpi__etiqueta">Ajustes realizados</span>
            <p className="adm-kpi__valor">{totalAjustes}</p>
          </div>
        </div>
      </section>

      <form onSubmit={aplicarFiltros} className="adm-filtros" role="search" aria-label="Filtros de movimientos">
        <label className="adm-campo adm-campo--ancho">
          <span>Buscar producto</span>
          <input type="search" placeholder="Código o nombre" value={filtros.busqueda}
            onChange={(e) => setFiltros({ ...filtros, busqueda: e.target.value })} />
        </label>
        <label className="adm-campo">
          <span>Tipo</span>
          <select value={filtros.tipo} onChange={(e) => setFiltros({ ...filtros, tipo: e.target.value })}>
            <option value="">Todos</option>
            <option value="ENTRADA">Entrada</option>
            <option value="SALIDA">Salida</option>
            <option value="AJUSTE">Ajuste</option>
          </select>
        </label>
        <label className="adm-campo">
          <span>Desde</span>
          <input type="date" value={filtros.fechaInicio} onChange={(e) => setFiltros({ ...filtros, fechaInicio: e.target.value })} />
        </label>
        <label className="adm-campo">
          <span>Hasta</span>
          <input type="date" value={filtros.fechaFin} onChange={(e) => setFiltros({ ...filtros, fechaFin: e.target.value })} />
        </label>
        <div className="adm-filtros__acciones">
          <button type="submit" className="adm-btn">Filtrar</button>
        </div>
      </form>

      <div className="adm-tarjeta" aria-busy={loading}>
        {loading ? (
          <div className="adm-cargando">Cargando movimientos…</div>
        ) : paginados.length === 0 ? (
          <p className="adm-vacio">No hay movimientos registrados.</p>
        ) : (
          <div className="adm-tabla-wrap">
            <table className="adm-tabla adm-tabla--tarjetas">
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Producto</th>
                  <th>Tipo</th>
                  <th className="adm-tabla__der">Cantidad</th>
                  <th className="adm-tabla__der">Stock anterior</th>
                  <th className="adm-tabla__der">Stock nuevo</th>
                  <th>Motivo</th>
                  <th>Referencia</th>
                </tr>
              </thead>
              <tbody>
                {paginados.map((m) => {
                  const { fecha, hora } = formatFecha(m.fecha);
                  const tipo = TIPO[m.tipo] || { label: m.tipo, badge: "neutro", signo: "" };
                  return (
                    <tr key={m.id}>
                      <td data-label="Fecha" className="adm-tabla__nowrap">
                        {fecha}
                        <span className="adm-tabla__sub">{hora}</span>
                      </td>
                      <td data-label="Producto" className="adm-tabla__celda--ancha">
                        <div className="adm-celda-producto">
                          <span className="adm-miniatura" aria-hidden="true">{getInicial(m.productoId)}</span>
                          <div>
                            <strong>{getNombre(m.productoId)}</strong>
                            <span className="adm-tabla__sub">Código {getCodigo(m.productoId)}</span>
                          </div>
                        </div>
                      </td>
                      <td data-label="Tipo"><span className={`adm-badge adm-badge--${tipo.badge}`}>{tipo.label}</span></td>
                      <td data-label="Cantidad" className="adm-tabla__der"><strong>{tipo.signo}{m.cantidad}</strong></td>
                      <td data-label="Stock anterior" className="adm-tabla__der">{m.stockAnterior}</td>
                      <td data-label="Stock nuevo" className="adm-tabla__der"><strong>{m.stockNuevo}</strong></td>
                      <td data-label="Motivo">{m.motivo || "—"}</td>
                      <td data-label="Referencia">{m.referencia ? <span className="adm-mono">#{m.referencia}</span> : "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <nav className="adm-paginacion" aria-label="Paginación">
        {totalPags > 1 && (
          <button type="button" className="adm-btn" onClick={() => setPagina((p) => Math.max(1, p - 1))} disabled={pagina === 1}>Anterior</button>
        )}
        <span>Mostrando {desdeN}–{hastaN} de {filtradosBusqueda.length} movimientos</span>
        {totalPags > 1 && (
          <button type="button" className="adm-btn" onClick={() => setPagina((p) => Math.min(totalPags, p + 1))} disabled={pagina === totalPags}>Siguiente</button>
        )}
      </nav>

      {/* Ajuste manual */}
      {mostrarModal && (
        <ConfirmacionAdmin
          titulo="Ajuste manual de stock"
          texto="Registra una corrección manual al inventario."
          confirmar="Registrar"
          cargando={loadingAjuste}
          deshabilitado={!ajuste.productoId || !ajuste.motivo || !ajuste.cantidad}
          onConfirmar={handleAjuste}
          onCancelar={() => setMostrarModal(false)}
        >
          <label className="adm-campo adm-campo--bloque">
            <span>Producto</span>
            <select value={ajuste.productoId} onChange={(e) => setAjuste({ ...ajuste, productoId: e.target.value })}>
              <option value="">Seleccionar producto…</option>
              {productos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </label>
          <label className="adm-campo adm-campo--bloque">
            <span>Tipo</span>
            <select value={ajuste.tipo} onChange={(e) => setAjuste({ ...ajuste, tipo: e.target.value })}>
              <option value="ENTRADA">Entrada</option>
              <option value="SALIDA">Salida</option>
              <option value="AJUSTE">Ajuste</option>
            </select>
          </label>
          <label className="adm-campo adm-campo--bloque">
            <span>Cantidad</span>
            <input type="text" inputMode="numeric" placeholder="0" value={ajuste.cantidad}
              onChange={(e) => setAjuste({ ...ajuste, cantidad: e.target.value })} />
          </label>
          <label className="adm-campo adm-campo--bloque">
            <span>Motivo</span>
            <input type="text" placeholder="Ej. Corrección de inventario" value={ajuste.motivo}
              onChange={(e) => setAjuste({ ...ajuste, motivo: e.target.value })} />
          </label>
        </ConfirmacionAdmin>
      )}
    </div>
  );
}

export default Movimientos;
