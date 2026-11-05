import { useEffect, useState } from "react";
import Icon from "../public/Icon";
import { DURACION_AVISO_MS, EVENTO_AVISO } from "./avisar";

const ICONO = { ok: "check", error: "alert", aviso: "alert", info: "info" };
const MAXIMO = 3;

/**
 * Pila de avisos flotantes abajo a la derecha (en celular, abajo y a lo ancho). Escucha avisar() de avisar.js; cada
 * aviso se va solo a los pocos segundos o con su botón Cerrar. Los anuncia el lector de pantalla (role="status").
 */
export default function Avisos() {
  const [avisos, setAvisos] = useState([]);

  useEffect(() => {
    const timers = new Map();
    const quitar = (id) => {
      setAvisos((prev) => prev.filter((a) => a.id !== id));
      clearTimeout(timers.get(id));
      timers.delete(id);
    };
    const alAvisar = (e) => {
      const aviso = e.detail;
      setAvisos((prev) => [...prev, aviso].slice(-MAXIMO));
      timers.set(aviso.id, setTimeout(() => quitar(aviso.id), DURACION_AVISO_MS));
    };
    window.addEventListener(EVENTO_AVISO, alAvisar);
    return () => {
      window.removeEventListener(EVENTO_AVISO, alAvisar);
      timers.forEach(clearTimeout);
    };
  }, []);

  const cerrar = (id) => setAvisos((prev) => prev.filter((a) => a.id !== id));

  return (
    <div className="ui-avisos" role="status" aria-live="polite">
      {avisos.map((a) => (
        <div key={a.id} className={`ui-aviso ui-aviso--${a.tono}`}>
          <Icon name={ICONO[a.tono] || "info"} size={18} />
          <p className="ui-aviso__texto">{a.texto}</p>
          <button type="button" className="ui-aviso__cerrar" onClick={() => cerrar(a.id)} aria-label="Cerrar aviso">
            <Icon name="close" size={16} />
          </button>
        </div>
      ))}
    </div>
  );
}
