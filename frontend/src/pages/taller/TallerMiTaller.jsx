import MecanicosTaller from "../../components/servicio/MecanicosTaller";
import { formatPrecio } from "../../components/servicio/estadosServicio";

/** Datos del taller y su equipo. Lo que ya existía en la pestaña "Mi taller", sin cambios de fondo. */
export default function TallerMiTaller({ taller, ordenes, token }) {
  const nombre = taller?.nombre || ordenes[0]?.tallerNombre;
  const precio = taller?.precioManoObra ?? ordenes[0]?.precioManoObra;
  return (
    <div className="pt-perfil">
      {nombre ? (
        <>
          <span className="pt-eyebrow">Datos del taller</span>
          <h2 className="pt-perfil-nombre">{nombre}</h2>
          <div className="pt-perfil-facts">
            <div className="pt-fact">
              <span className="pt-fact-label">Precio de mano de obra</span>
              <span className="pt-fact-value">{formatPrecio(precio)}</span>
            </div>
          </div>
          <p className="pt-hint">
            Para actualizar el nombre, la ciudad, la dirección o el precio de mano de
            obra de tu taller, escríbele al administrador de DriveMaster.
          </p>
        </>
      ) : (
        <p className="sv-empty">Tu taller todavía no aparece en el reporte.</p>
      )}
      <MecanicosTaller token={token} />
    </div>
  );
}
