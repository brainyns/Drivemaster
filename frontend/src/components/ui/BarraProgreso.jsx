/** Barra de avance con su porcentaje para lectores de pantalla (perfil completo, dinero liquidado…). */
export default function BarraProgreso({ valor, etiqueta, tono = "acento" }) {
  const pct = Math.max(0, Math.min(100, Math.round(valor || 0)));
  return (
    <span className={`ui-progreso ui-progreso--${tono}`} role="progressbar" aria-label={etiqueta}
      aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}>
      <span className="ui-progreso__barra" style={{ width: `${pct}%` }} />
    </span>
  );
}
