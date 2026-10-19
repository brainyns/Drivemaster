import BuscadorDireccion from "./BuscadorDireccion";

/** Buscador de direcciones y botón "Usar mi ubicación", encima del mapa. */
export default function BarraUbicacion({ id, cerca, onElegirSugerencia, onUsarMiUbicacion, ubicando }) {
  return (
    <div className="mapa-selector__barra">
      <BuscadorDireccion id={`${id}-buscar`} cerca={cerca} onElegir={onElegirSugerencia} />
      <button type="button" className="mapa-boton mapa-boton--ubicacion" onClick={onUsarMiUbicacion} disabled={ubicando} aria-busy={ubicando}>
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false">
          <circle cx="12" cy="12" r="3.5" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" /><circle cx="12" cy="12" r="7.5" />
        </svg>
        {ubicando ? "Ubicando…" : "Usar mi ubicación"}
      </button>
    </div>
  );
}
