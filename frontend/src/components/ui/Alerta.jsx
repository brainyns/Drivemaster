import Icon from "../public/Icon";

const ICONO = { error: "alert", aviso: "alert", ok: "check", info: "message" };

/**
 * Aviso en línea. tono: error | aviso | ok | info. Con onReintentar muestra "Reintentar" (errores de carga);
 * con onCerrar, un botón para descartarlo. Los errores se anuncian de inmediato (role="alert"), el resto con
 * cortesía (role="status").
 */
export default function Alerta({ tono = "error", children, onReintentar, reintentando = false, onCerrar, className = "" }) {
  return (
    <div className={`ui-alerta ui-alerta--${tono} ${className}`} role={tono === "error" ? "alert" : "status"}>
      <Icon name={ICONO[tono] || "alert"} size={18} />
      <p className="ui-alerta__texto">{children}</p>
      {(onReintentar || onCerrar) && (
        <div className="ui-alerta__acciones">
          {onReintentar && (
            <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={onReintentar}
              disabled={reintentando} aria-busy={reintentando}>
              {!reintentando && <Icon name="refresh" size={14} />}
              {reintentando ? "Reintentando…" : "Reintentar"}
            </button>
          )}
          {onCerrar && (
            <button type="button" className="ui-btn ui-btn--texto ui-btn--chico" onClick={onCerrar}>Cerrar</button>
          )}
        </div>
      )}
    </div>
  );
}
