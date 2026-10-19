import Alerta from "./Alerta";

/** Guardar y Cancelar al pie de un formulario por sección; el error del servidor va encima. */
export default function PieFormulario({ guardando, error, onCancelar, textoGuardar = "Guardar cambios" }) {
  return (
    <>
      {error && <Alerta>{error}</Alerta>}
      <div className="ui-form-pie">
        <button type="submit" className="ui-btn ui-btn--primario" disabled={guardando} aria-busy={guardando}>
          {guardando ? "Guardando…" : textoGuardar}
        </button>
        <button type="button" className="ui-btn ui-btn--secundario" onClick={onCancelar} disabled={guardando}>Cancelar</button>
      </div>
    </>
  );
}
