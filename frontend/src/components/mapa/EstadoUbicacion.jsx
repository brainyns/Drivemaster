/**
 * Debajo del mapa: la dirección del pin (o lo que está pasando con ella), sus coordenadas, el botón para
 * reintentar si falló y el aviso del buscador o del GPS.
 */
export default function EstadoUbicacion({ value, direccion, ayuda, aviso, onReintentar, className = "" }) {
  const tieneValor = value?.latitud != null && value?.longitud != null;

  let texto = ayuda;
  if (tieneValor) {
    if (direccion?.estado === "buscando") texto = "Buscando la dirección de este punto…";
    else if (direccion?.estado === "listo") texto = direccion.etiqueta;
    else if (direccion?.estado === "error") {
      texto = `El punto quedó marcado, pero no pudimos obtener su dirección. ${direccion.motivo || ""}`.trim();
    } else if (direccion?.estado === "sin-datos") texto = "El punto quedó marcado; no encontramos una dirección para este lugar.";
    else texto = "Punto marcado en el mapa.";
  }

  return (
    <div className={className}>
      <div className={`mapa-selector__estado${tieneValor ? " mapa-selector__estado--marcado" : ""}`} aria-live="polite">
        {tieneValor && (
          <svg viewBox="0 0 32 42" width="14" height="18" aria-hidden="true" focusable="false" className="mapa-selector__icono">
            <path d="M16 1C7.7 1 1 7.6 1 15.8c0 10.6 13.3 24.3 13.9 24.9a1.5 1.5 0 0 0 2.2 0C17.7 40.1 31 26.4 31 15.8 31 7.6 24.3 1 16 1z" />
          </svg>
        )}
        <span>
          {texto}
          {tieneValor && (
            <span className="mapa-selector__coords">{value.latitud.toFixed(6)}, {value.longitud.toFixed(6)}</span>
          )}
        </span>
        {tieneValor && direccion?.estado === "error" && (
          <button type="button" className="mapa-boton mapa-boton--chico mapa-selector__reintentar" onClick={onReintentar}>
            Reintentar
          </button>
        )}
      </div>
      {aviso && (
        <p className={`mapa-aviso${aviso.tipo === "error" ? " mapa-aviso--error" : ""}`} role={aviso.tipo === "error" ? "alert" : "status"}>
          {aviso.texto}
        </p>
      )}
    </div>
  );
}
