import Icon from "../public/Icon";

/** Calificación de 0 a 5 con estrellas dibujadas; el lector de pantalla oye "4,5 de 5". */
export default function Estrellas({ valor, size = 16 }) {
  const llenas = Math.round(valor || 0);
  const texto = `${(valor ?? 0).toLocaleString("es-CO", { maximumFractionDigits: 1 })} de 5`;
  return (
    <span className="ui-estrellas" role="img" aria-label={texto}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Icon key={n} name="star" size={size} className={n <= llenas ? "ui-estrellas__on" : undefined} />
      ))}
    </span>
  );
}
