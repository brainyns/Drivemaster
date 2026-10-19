import { useRef, useState } from "react";
import LogoNegocio from "./LogoNegocio";
import Icon from "../public/Icon";
import { TIPOS_IMAGEN, errorImagen } from "../../utils/imagen";

/**
 * Logo de un negocio con su botón para subirlo o cambiarlo (Mi taller, Mi tienda); sin logo, las iniciales. Valida
 * tipo y tamaño antes de enviarlo (el backend vuelve a validar y solo deja cambiar el del negocio de la sesión).
 * subir(archivo) devuelve { logoUrl }; onSubido(logoUrl) actualiza el negocio y onAviso recibe { tono, texto } o null.
 */
export default function LogoEditable({ url, nombre, subir, onSubido, onAviso, id = "logo-negocio" }) {
  const input = useRef(null);
  const [subiendo, setSubiendo] = useState(false);

  const elegir = async (e) => {
    const archivo = e.target.files?.[0];
    e.target.value = "";
    if (!archivo) return;
    const error = errorImagen(archivo, "El logo");
    if (error) { onAviso({ tono: "error", texto: error }); return; }
    setSubiendo(true);
    onAviso(null);
    try {
      const { logoUrl } = await subir(archivo);
      onSubido(logoUrl);
      onAviso({ tono: "ok", texto: "Logo guardado. Así te verán los clientes." });
    } catch (err) {
      onAviso({ tono: "error", texto: err.message });
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <div className="ui-logo-editable">
      <LogoNegocio url={url} nombre={nombre} tamano="grande" />
      <button id={id} type="button" className="ui-btn ui-btn--secundario ui-btn--chico" disabled={subiendo}
        aria-busy={subiendo} onClick={() => input.current?.click()}>
        {!subiendo && <Icon name="camera" size={14} />}
        {subiendo ? "Subiendo…" : url ? "Cambiar logo" : "Subir logo"}
      </button>
      <input ref={input} type="file" accept={TIPOS_IMAGEN.join(",")} hidden onChange={elegir} />
    </div>
  );
}
