import { useState } from "react";
import Campo from "./Campo";
import PieFormulario from "./PieFormulario";
import { errorNit, normalizarNit } from "../../utils/nit";
import { textoError } from "../../utils/errores";

/**
 * El NIT de Mi taller / Mi tienda. Con NIT, solo se muestra (cambiarlo lo hace DriveMaster). Sin él (negocios
 * registrados antes de que fuera obligatorio), "Sin registrar" y un botón para agregarlo una vez.
 * guardar(nit) devuelve el negocio actualizado, que recibe onGuardado.
 */
export default function AgregarNit({ id, nit, guardar, onGuardado }) {
  const [abierto, setAbierto] = useState(false);
  const [valor, setValor] = useState("");
  const [errorCampo, setErrorCampo] = useState(null);
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);

  if (nit) return nit;
  if (!abierto) {
    return (
      <>
        <span className="ui-datos__falta">Sin registrar</span>{" "}
        <button type="button" className="ui-btn ui-btn--secundario ui-btn--chico" onClick={() => setAbierto(true)}>Agregar NIT</button>
      </>
    );
  }

  const enviar = async (e) => {
    e.preventDefault();
    const err = errorNit(valor);
    setErrorCampo(err);
    if (err) return;
    setGuardando(true);
    setError(null);
    try {
      onGuardado(await guardar(normalizarNit(valor)));
    } catch (ex) {
      setError(textoError(ex));
      setGuardando(false);
    }
  };

  const cancelar = () => {
    setAbierto(false);
    setValor("");
    setErrorCampo(null);
    setError(null);
  };

  return (
    <form className="ui-form" onSubmit={enviar} noValidate>
      <Campo id={`${id}-nit`} label="NIT" error={errorCampo}
        ayuda="Con o sin dígito de verificación. Después de guardarlo, para cambiarlo escríbele al administrador de DriveMaster.">
        <input className="ui-field__input ui-form__corto" inputMode="numeric" maxLength={20} placeholder="900123456-7"
          autoFocus value={valor} onChange={(e) => { setValor(e.target.value); if (errorCampo) setErrorCampo(null); }} />
      </Campo>
      <PieFormulario guardando={guardando} error={error} onCancelar={cancelar} textoGuardar="Guardar NIT" />
    </form>
  );
}
