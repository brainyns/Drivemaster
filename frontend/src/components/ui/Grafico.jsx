import { altoBarra, marcasEje, topeEje } from "./escalaGrafico";
import "../../css/grafico.css";

/**
 * Gráfico de barras por periodo (mes o semana), con una o dos series en el mismo eje: solo se juntan series de la misma
 * unidad (para pesos y conteos van dos gráficos). Al pasar el puntero por una columna se ve su valor exacto, y una
 * tabla oculta repite los datos para lectores de pantalla (el dibujo va oculto para ellos).
 *
 * - etiquetas: texto corto bajo cada columna ("ago"); detalles (opcional): el texto largo del tooltip ("agosto 2026").
 * - series: [{ nombre, valores }]; el color sale del orden (--ui-serie-1, --ui-serie-2), nunca del valor.
 * - formato: cómo se escribe un valor en el tooltip y la tabla; formatoEje, en el eje (por defecto, el mismo).
 */
export default function Grafico({ titulo, etiquetas, detalles = etiquetas, series, formato = String, formatoEje = formato, alto = 160 }) {
  const tope = topeEje(Math.max(0, ...series.flatMap((s) => s.valores)));
  const marcas = marcasEje(tope);
  return (
    <figure className="ui-graf" style={{ "--graf-alto": `${alto}px` }}>
      {series.length > 1 && (
        <ul className="ui-graf__leyenda">
          {series.map((s, i) => (
            <li key={s.nombre}><span className={`ui-graf__muestra ui-graf__serie--${i + 1}`} aria-hidden="true" />{s.nombre}</li>
          ))}
        </ul>
      )}
      <div className="ui-graf__area" aria-hidden="true">
        <div className="ui-graf__eje">
          {marcas.slice().reverse().map((m) => <span key={m}>{formatoEje(m)}</span>)}
        </div>
        <div className="ui-graf__plot">
          <div className="ui-graf__guias">
            {marcas.map((m) => <span key={m} className="ui-graf__guia" style={{ bottom: `${altoBarra(m, tope)}%` }} />)}
          </div>
          {etiquetas.map((etiqueta, j) => (
            <div key={`${etiqueta}-${j}`} className="ui-graf__col">
              <div className="ui-graf__barras">
                {series.map((s, i) => (
                  <span key={s.nombre} className={`ui-graf__barra ui-graf__serie--${i + 1}`}
                    style={{ height: `${altoBarra(s.valores[j], tope)}%` }} />
                ))}
              </div>
              <span className="ui-graf__x">{etiqueta}</span>
              <span className="ui-graf__tip">
                <strong>{detalles[j]}</strong>
                {series.map((s) => <span key={s.nombre}>{series.length > 1 ? `${s.nombre}: ` : ""}{formato(s.valores[j])}</span>)}
              </span>
            </div>
          ))}
        </div>
      </div>
      <table className="ui-sr">
        <caption>{titulo}</caption>
        <thead><tr><th scope="col">Periodo</th>{series.map((s) => <th key={s.nombre} scope="col">{s.nombre}</th>)}</tr></thead>
        <tbody>
          {detalles.map((d, j) => (
            <tr key={`${d}-${j}`}><th scope="row">{d}</th>{series.map((s) => <td key={s.nombre}>{formato(s.valores[j])}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
