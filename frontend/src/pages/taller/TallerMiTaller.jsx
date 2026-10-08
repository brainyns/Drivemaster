import MecanicosTaller from "../../components/servicio/MecanicosTaller";
import { formatPrecio } from "../../components/servicio/estadosServicio";

/** Datos del taller (tarifa y horario) y su equipo. */
export default function TallerMiTaller({ taller, ordenes, token }) {
  const nombre = taller?.nombre || ordenes[0]?.tallerNombre;
  const precio = taller?.precioManoObra ?? ordenes[0]?.precioManoObra;
  const faltaTarifa = !taller?.tarifaHora;
  const faltaHorario = !taller?.horario;
  return (
    <div className="pt-perfil">
      {nombre ? (
        <>
          <span className="pt-eyebrow">Datos del taller</span>
          <h2 className="pt-perfil-nombre">{nombre}</h2>
          <div className="pt-perfil-facts">
            <div className="pt-fact">
              <span className="pt-fact-label">Tarifa por hora</span>
              <span className="pt-fact-value">{faltaTarifa ? "Sin definir" : formatPrecio(taller.tarifaHora)}</span>
            </div>
            <div className="pt-fact">
              <span className="pt-fact-label">Precio de mano de obra</span>
              <span className="pt-fact-value">{formatPrecio(precio)}</span>
            </div>
            <div className="pt-fact">
              <span className="pt-fact-label">Horario</span>
              <span className="pt-fact-value tp-horario">{faltaHorario ? "Sin definir" : taller.horario}</span>
            </div>
            {taller?.ciudad && (
              <div className="pt-fact">
                <span className="pt-fact-label">Ciudad</span>
                <span className="pt-fact-value">{taller.ciudad}</span>
              </div>
            )}
          </div>
          {(faltaTarifa || faltaHorario) && (
            <p className="ss-aviso">
              Falta {faltaTarifa && "la tarifa por hora"}{faltaTarifa && faltaHorario && " y "}{faltaHorario && "el horario"}.
              La tarifa se usa al cotizar y el horario lo ve el cliente al elegir taller.
            </p>
          )}
          <p className="pt-hint">
            Para actualizar el nombre, la ciudad, la dirección, la tarifa o el horario de tu taller, escríbele al administrador de DriveMaster.
          </p>
        </>
      ) : (
        <p className="sv-empty">Tu taller todavía no aparece en el reporte.</p>
      )}
      <MecanicosTaller token={token} />
    </div>
  );
}
