/**
 * En celular y tableta (hasta 1023 px): barra fija abajo con el total y la acción principal (children), para
 * que el botón esté siempre a mano mientras el resumen completo queda al final de la página. En escritorio no
 * se muestra (la acción vive en el resumen fijo de la derecha). alerta: un aviso (p. ej. un error al enviar).
 */
export default function BarraAccionMovil({ etiqueta, valor, nota, alerta, children }) {
  return (
    <div className="ui-barra-movil">
      {alerta && <div className="ui-barra-movil__alerta">{alerta}</div>}
      <div className="ui-barra-movil__total">
        {etiqueta && <span className="ui-barra-movil__etiqueta">{etiqueta}</span>}
        {valor != null && <strong className="ui-barra-movil__valor">{valor}</strong>}
        {nota && <span className="ui-barra-movil__nota">{nota}</span>}
      </div>
      <div className="ui-barra-movil__accion">{children}</div>
    </div>
  );
}
