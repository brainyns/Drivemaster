import { useEffect, useRef, useState } from "react";
import { actualizarMiPerfil } from "../../services/clienteService";
import SelectorCiudad from "./SelectorCiudad";
import "../../css/marketplace.css";

/**
 * Ventana obligatoria para el cliente que no tiene ciudad (las cuentas creadas antes de pedirla, y las de Google):
 * no se cierra con Escape ni con el fondo hasta que la escoge. El backend también la exige al guardar Mi perfil.
 */
export default function PedirCiudad({ token, onGuardada }) {
  const [ciudad, setCiudad] = useState("");
  const [error, setError] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const refDialogo = useRef(null);

  useEffect(() => { refDialogo.current?.focus(); }, []);

  const guardar = async (e) => {
    e.preventDefault();
    if (!ciudad) { setError("Escoge tu ciudad de la lista."); return; }
    setGuardando(true);
    setError(null);
    try {
      const perfil = await actualizarMiPerfil(token, { ciudad });
      onGuardada(perfil?.ciudad || ciudad);
    } catch (err) {
      setError(err.message);
      setGuardando(false);
    }
  };

  return (
    <div className="mk-modal">
      <form className="mk-modal__caja" role="dialog" aria-modal="true" aria-labelledby="pc-ciudad-titulo"
        aria-describedby="pc-ciudad-texto" tabIndex={-1} ref={refDialogo} onSubmit={guardar} noValidate>
        <h2 id="pc-ciudad-titulo">¿En qué ciudad estás?</h2>
        <p id="pc-ciudad-texto">La usamos para mostrarte tiendas y talleres cerca de ti. Puedes cambiarla después en Mi perfil.</p>
        <SelectorCiudad id="pc-ciudad-modal" valor={ciudad} onCambio={(c) => { setCiudad(c); setError(null); }} error={error} />
        <div className="mk-modal__acciones">
          <button type="submit" className="dm-btn dm-btn--primary" disabled={guardando} aria-busy={guardando}>
            {guardando ? "Guardando…" : "Guardar ciudad"}
          </button>
        </div>
      </form>
    </div>
  );
}
