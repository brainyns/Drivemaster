/**
 * En celular las tablas pasan a tarjetas y el encabezado se oculta: este selector ordena por las mismas columnas
 * (solo se ve por debajo de 760 px). Con siempre se ve en todos los anchos (listas que no son tabla, sin encabezado).
 * opciones es [{ campo, label, asc?, desc? }]; el orden por defecto es la primera opción vacía.
 */
export default function SelectorOrden({ id, opciones, orden, onOrdenar, porDefecto = "Orden por defecto", siempre = false }) {
  const valor = orden ? `${orden.campo},${orden.dir}` : "";
  const cambiar = (v) => {
    if (!v) return onOrdenar(null);
    const [campo, dir] = v.split(",");
    onOrdenar({ campo, dir });
  };
  return (
    <label htmlFor={id} className={`ui-orden-movil${siempre ? " ui-orden-movil--siempre" : ""}`}>
      Ordenar por
      <select id={id} className="ui-field__input" value={valor} onChange={(e) => cambiar(e.target.value)}>
        <option value="">{porDefecto}</option>
        {opciones.flatMap((o) => [
          <option key={`${o.campo},asc`} value={`${o.campo},asc`}>{o.label} {o.asc || "(menor a mayor)"}</option>,
          <option key={`${o.campo},desc`} value={`${o.campo},desc`}>{o.label} {o.desc || "(mayor a menor)"}</option>,
        ])}
      </select>
    </label>
  );
}
