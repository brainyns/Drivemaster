import "../css/estado-negocio.css";

const TEXTOS = {
  PENDIENTE: {
    titulo: "Tu cuenta está en revisión",
    detalle: "El equipo de DriveMaster está validando los datos de tu negocio. Te avisaremos por correo cuando quede aprobada; mientras tanto no podrás operar en el marketplace.",
  },
  RECHAZADO: {
    titulo: "Tu registro no fue aprobado",
    detalle: "Revisa el motivo y escríbenos si quieres corregir la información.",
  },
  INACTIVO: {
    titulo: "Tu cuenta está desactivada",
    detalle: "Comunícate con el administrador de DriveMaster para reactivarla.",
  },
};

/** El negocio puede operar cuando está aprobado y activo. */
function puedeOperar(negocio) {
  const aprobado = !negocio.estadoAprobacion || negocio.estadoAprobacion === "APROBADO";
  return aprobado && negocio.activo !== false;
}

/**
 * Muestra el contenido (children) solo si el taller o la tienda puede operar.
 * Si está pendiente de aprobación, rechazado o desactivado, muestra el aviso.
 * Sin datos del negocio (aún cargando o sin perfil) muestra el contenido.
 */
export default function EstadoNegocio({ negocio, tipo = "negocio", children }) {
  if (!negocio || puedeOperar(negocio)) return children;

  const estado = negocio?.estadoAprobacion === "RECHAZADO" ? "RECHAZADO"
    : negocio?.estadoAprobacion === "PENDIENTE" ? "PENDIENTE"
    : "INACTIVO";
  const { titulo, detalle } = TEXTOS[estado];

  return (
    <section className={`estado-negocio estado-negocio--${estado.toLowerCase()}`} aria-live="polite">
      <span className="estado-negocio__eyebrow">{tipo === "taller" ? "Taller aliado" : "Tienda aliada"}</span>
      <h2 className="estado-negocio__titulo">{titulo}</h2>
      {negocio?.nombre && <p className="estado-negocio__nombre">{negocio.nombre}</p>}
      <p className="estado-negocio__detalle">{detalle}</p>

      {estado === "RECHAZADO" && negocio?.motivoRechazo && (
        <p className="estado-negocio__motivo">
          <strong>Motivo:</strong> {negocio.motivoRechazo}
        </p>
      )}

      {estado === "PENDIENTE" && (
        <ol className="estado-negocio__pasos">
          <li className="estado-negocio__paso estado-negocio__paso--hecho">Registro enviado</li>
          <li className="estado-negocio__paso estado-negocio__paso--actual">Validación de DriveMaster</li>
          <li className="estado-negocio__paso">Cuenta activa</li>
        </ol>
      )}
    </section>
  );
}
