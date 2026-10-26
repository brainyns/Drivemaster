import { POR_PAGINA, paginasVisibles, rangoMostrado, totalPaginas } from "./reglasPaginacion";

/**
 * Pie de una lista paginada, igual en todo el panel: "Mostrando 1–20 de 135" y, si hay más de una página, Anterior,
 * los números (con "…" en los huecos) y Siguiente. pagina empieza en 1. Al cambiar de página vuelve al inicio de la
 * lista (ancla: id del elemento; sin ancla, al inicio de la página).
 */
export default function Paginacion({ pagina, total, porPagina = POR_PAGINA, onCambiar, etiqueta = "Páginas", ancla }) {
  const paginas = totalPaginas(total, porPagina);
  const { desde, hasta } = rangoMostrado(pagina, total, porPagina);
  if (!total) return null;

  const ir = (p) => {
    onCambiar(p);
    const destino = ancla ? document.getElementById(ancla) : null;
    if (destino) destino.scrollIntoView({ block: "start" });
    else window.scrollTo({ top: 0 });
  };

  return (
    <nav className="ui-paginacion" aria-label={etiqueta}>
      <span className="ui-paginacion__texto">
        Mostrando {desde.toLocaleString("es-CO")}–{hasta.toLocaleString("es-CO")} de {Number(total).toLocaleString("es-CO")}
      </span>
      {paginas > 1 && (
        <span className="ui-paginacion__botones">
          <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={pagina === 1} onClick={() => ir(pagina - 1)}>
            Anterior
          </button>
          <span className="ui-paginacion__numeros">
            {paginasVisibles(pagina, paginas).map((p, i) => (p === "…" ? (
              <span key={`hueco-${i}`} className="ui-paginacion__hueco" aria-hidden="true">…</span>
            ) : (
              <button key={p} type="button" className="ui-paginacion__numero" aria-current={p === pagina ? "page" : undefined}
                aria-label={`Página ${p} de ${paginas}`} onClick={() => p !== pagina && ir(p)}>
                {p}
              </button>
            )))}
          </span>
          <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={pagina === paginas} onClick={() => ir(pagina + 1)}>
            Siguiente
          </button>
        </span>
      )}
    </nav>
  );
}
