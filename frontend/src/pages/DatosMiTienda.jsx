import { useEffect, useState } from "react";
import { actualizarMiTienda, miTienda } from "../services/tiendaService";
import DatosTiendaPropia from "../components/DatosTiendaPropia";
import "../css/aprobaciones.css";

/**
 * La TIENDA completa sus datos públicos: ciudad, dirección, horario de atención (para el retiro) y, si
 * hace envíos, el costo fijo (solo dentro de su ciudad). Sin ciudad, dirección y horario sus productos no
 * aparecen en el catálogo. Lo administrativo (NIT, plan, correo) no se muestra ni se edita aquí.
 */
export default function DatosMiTienda({ token, onGuardado }) {
  const [tienda, setTienda] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let vivo = true;
    miTienda(token)
      .then((t) => { if (vivo) setTienda(t); })
      .catch((e) => { if (vivo) setError(e.message); });
    return () => { vivo = false; };
  }, [token]);

  const guardar = async (tk, datos) => {
    const actualizada = await actualizarMiTienda(tk, datos);
    setTienda(actualizada);
    onGuardado?.(actualizada);
    return actualizada;
  };

  if (error) return <p className="ap-alerta ap-alerta--error" role="alert" style={{ margin: 16 }}>{error}</p>;
  if (!tienda) return <p style={{ padding: 16 }}>Cargando...</p>;

  return (
    <div className="ap-page">
      <DatosTiendaPropia key={tienda.id} token={token} tienda={tienda}
        alGuardar={guardar} titulo="Mi tienda" />
    </div>
  );
}
