import { useState } from "react";
import { getToken } from "../../services/authService";
import { descargarPdfCotizacion } from "../../services/cotizacionService";
import Icon from "../public/Icon";

/** "Descargar PDF" de una cotización (taller y cliente). El PDF lo genera el backend; si falla, el error queda al lado. */
export default function BotonPdfCotizacion({ cotizacionId, className = "ui-btn ui-btn--secundario ui-btn--chico" }) {
  const [descargando, setDescargando] = useState(false);
  const [error, setError] = useState(null);

  const descargar = async () => {
    setDescargando(true);
    setError(null);
    try {
      await descargarPdfCotizacion(getToken(), cotizacionId);
    } catch (e) {
      setError(e.message);
    } finally {
      setDescargando(false);
    }
  };

  return (
    <>
      <button type="button" className={className} onClick={descargar} disabled={descargando} aria-busy={descargando}>
        <Icon name="file" size={16} /> {descargando ? "Generando PDF…" : "Descargar PDF"}
      </button>
      {error && <span className="ss-meta" role="alert">{error}</span>}
    </>
  );
}
