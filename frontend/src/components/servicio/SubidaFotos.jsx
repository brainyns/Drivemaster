import { useRef, useState } from "react";
import { subirFotoServicio } from "../../services/solicitudServicioService";
import Icon from "../public/Icon";

const MAX_MB = 5;

/**
 * Selector de fotos que las sube al momento (Cloudinary vía backend) y devuelve
 * la lista de URLs. Lo usan el cliente al pedir el diagnóstico y el taller al registrarlo.
 */
export default function SubidaFotos({ token, fotos, onChange, max = 5, deshabilitado = false }) {
  const inputRef = useRef(null);
  const [subiendo, setSubiendo] = useState(0);
  const [error, setError] = useState(null);

  const seleccionar = async (e) => {
    const archivos = Array.from(e.target.files || []);
    e.target.value = "";
    setError(null);

    const disponibles = max - fotos.length;
    if (archivos.length > disponibles) {
      setError(`Puedes agregar ${disponibles} foto(s) más.`);
    }
    const validos = archivos.slice(0, Math.max(disponibles, 0)).filter((f) => {
      if (!f.type.startsWith("image/")) { setError("Solo se permiten imágenes."); return false; }
      if (f.size > MAX_MB * 1024 * 1024) { setError(`Cada foto debe pesar menos de ${MAX_MB} MB.`); return false; }
      return true;
    });

    let actuales = fotos;
    for (const archivo of validos) {
      setSubiendo((n) => n + 1);
      try {
        const url = await subirFotoServicio(token, archivo);
        actuales = [...actuales, url];
        onChange(actuales);
      } catch (err) {
        setError(err.message);
      } finally {
        setSubiendo((n) => n - 1);
      }
    }
  };

  const quitar = (url) => onChange(fotos.filter((f) => f !== url));

  return (
    <div className="ss-fotos">
      <div className="ss-fotos__lista">
        {fotos.map((url) => (
          <div className="ss-fotos__item" key={url}>
            <img src={url} alt="Foto adjunta" loading="lazy" />
            {!deshabilitado && (
              <button type="button" className="ss-fotos__quitar" onClick={() => quitar(url)} aria-label="Quitar foto"><Icon name="close" size={16} /></button>
            )}
          </div>
        ))}
        {Array.from({ length: subiendo }).map((_, i) => (
          <div className="ss-fotos__item ss-fotos__item--cargando" key={`cargando-${i}`}>Subiendo…</div>
        ))}
        {fotos.length + subiendo < max && !deshabilitado && (
          <button type="button" className="ss-fotos__agregar" onClick={() => inputRef.current?.click()}>
            <span aria-hidden="true">+</span>
            Agregar foto
          </button>
        )}
      </div>
      <input ref={inputRef} type="file" accept="image/*" multiple hidden onChange={seleccionar} />
      <span className="ss-fotos__ayuda">{fotos.length}/{max} fotos · máximo {MAX_MB} MB cada una</span>
      {error && <span className="ss-error">{error}</span>}
    </div>
  );
}
