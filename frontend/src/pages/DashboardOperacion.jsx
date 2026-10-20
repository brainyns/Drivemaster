import { useCallback, useEffect, useState } from "react";
import { operacionPlataforma } from "../services/plataformaService";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Indicador from "../components/ui/Indicador";
import Alerta from "../components/ui/Alerta";
import Icon from "../components/public/Icon";
import "../css/dashboard-plataforma.css";
import "../css/panel-plataforma.css";

const REFRESCO_MS = 60000;

const hora = (iso) => new Date(iso).toLocaleTimeString("es-CO", { hour: "2-digit", minute: "2-digit" });
const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;

/**
 * Inicio del ADMIN: solo lo que le toca atender. Sin dinero ni datos de la plataforma (eso es del SUPERADMIN).
 */
export default function DashboardOperacion({ token, onIr }) {
  const [estado, setEstado] = useState({ datos: null, error: null });
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    return operacionPlataforma(token)
      .then((datos) => setEstado({ datos, error: null }))
      .catch((e) => setEstado((previo) => ({ datos: previo.datos, error: e.message })))
      .finally(() => setCargando(false));
  }, [token]);

  useEffect(() => {
    const inicial = setTimeout(cargar, 0);
    const intervalo = setInterval(cargar, REFRESCO_MS);
    return () => { clearTimeout(inicial); clearInterval(intervalo); };
  }, [cargar]);

  const { datos: d, error } = estado;

  const encabezado = (
    <EncabezadoPagina titulo="Tu resumen" descripcion="Lo que hay que atender hoy en el marketplace."
      acciones={(
        <>
          {d && <span className="pp-actualizado">Actualizado a las {hora(d.generadoEn)}</span>}
          <button type="button" className="ui-btn ui-btn--secundario" onClick={cargar} disabled={cargando} aria-busy={cargando}>
            <Icon name="refresh" size={16} /> {cargando && d ? "Actualizando…" : "Actualizar"}
          </button>
        </>
      )} />
  );

  if (!d) {
    return (
      <div className="ss-panel--plataforma pp-root">
        {encabezado}
        {error ? <Alerta onReintentar={cargar} reintentando={cargando}>{error}</Alerta> : (
          <div className="ui-indicadores" aria-busy="true">
            <p className="ui-sr" role="status">Cargando tu resumen…</p>
            {[0, 1, 2].map((i) => <span key={i} className="ui-skeleton pp-esqueleto-indicador" aria-hidden="true" />)}
          </div>
        )}
      </div>
    );
  }

  const porAprobar = d.tiendasPorAprobar + d.talleresPorAprobar;
  const tareas = [
    {
      n: porAprobar, titulo: "Negocios por aprobar", icono: "shield",
      detalle: `${plural(d.tiendasPorAprobar, "tienda", "tiendas")} y ${plural(d.talleresPorAprobar, "taller", "talleres")} esperan tu revisión.`,
      ir: "aprobaciones",
    },
    {
      n: d.problemasEnvioAbiertos, titulo: "Problemas de envío abiertos", icono: "alert",
      detalle: "Clientes que reportaron que un pedido no llegó o llegó mal. Decide si se reembolsa o se confirma la entrega.",
      ir: "problemas-envios",
    },
    {
      n: d.despachosAtrasados, titulo: "Pedidos atrasados", icono: "clock",
      detalle: "Pedidos pagados que la tienda no despachó dentro del plazo. Contacta a la tienda.",
      ir: "pedidos-envios", preset: "atrasados",
    },
  ];
  const alDia = tareas.every((t) => t.n === 0);

  return (
    <div className="ss-panel--plataforma pp-root">
      {encabezado}

      {error && (
        <Alerta tono="aviso" className="pp-bloque-sep" onReintentar={cargar} reintentando={cargando}>
          No pudimos actualizar tu resumen: se muestran los datos de las {hora(d.generadoEn)}.
        </Alerta>
      )}

      {alDia && (
        <p className="pp-bloque pp-bloque__vacio pp-bloque-sep">
          <Icon name="check" size={18} /> Todo al día: no hay negocios por aprobar, problemas de envío ni pedidos atrasados.
        </p>
      )}

      <section aria-label="Lo que hay que atender">
        <div className="ui-indicadores">
          {tareas.map((t) => (
            <Indicador key={t.titulo} icono={t.icono} titulo={t.titulo} valor={t.n} pie={t.detalle}
              tono={t.n > 0 ? "accion" : undefined} onClick={() => onIr?.(t.ir, t.preset)} />
          ))}
        </div>
      </section>
    </div>
  );
}
