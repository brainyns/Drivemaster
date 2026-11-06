import { useEffect, useState } from "react";

/** Pasado este tiempo cargando, se explica la espera (el backend de Render puede tardar en despertar). */
const ESPERA_LARGA_MS = 8000;
const TEXTO_ESPERA_LARGA = "Está tardando un poco más de lo normal: el servidor se está despertando. Puede tomar hasta un minuto.";

/**
 * Marcador de carga con la forma del contenido que viene: "lista" (filas), "tarjetas" (rejilla con imagen),
 * "formulario" (etiqueta + campo), "tabla" (filas falsas con columnas, en celular como tarjetas) o "indicadores"
 * (las tarjetas de cifras de un dashboard). columnas: cuántas tiene la tabla. Bloques grises con un brillo suave, sin
 * animación si la persona pidió reducir el movimiento. El texto solo lo leen los lectores de pantalla; si la carga
 * tarda, se ve un aviso de que el servidor está despertando (nunca un error mientras la petición sigue). conAviso={false}
 * en el segundo esqueleto de una misma pantalla, para que el aviso salga una sola vez.
 */
export default function Esqueleto({ variante = "lista", cantidad = 3, columnas = 4, texto = "Cargando…", conAviso = true }) {
  const [tarda, setTarda] = useState(false);
  useEffect(() => {
    const reloj = setTimeout(() => setTarda(true), ESPERA_LARGA_MS);
    return () => clearTimeout(reloj);
  }, []);

  const items = Array.from({ length: cantidad }, (_, i) => i);
  const celdas = Array.from({ length: columnas }, (_, i) => i);
  return (
    <div aria-busy="true">
      <p className="ui-sr" role="status">{texto}</p>
      <div className={`ui-esqueleto ui-esqueleto--${variante}`} aria-hidden="true">
        {variante === "tarjetas" && items.map((i) => (
          <div key={i} className="ui-esqueleto__tarjeta">
            <span className="ui-skeleton ui-esqueleto__media" />
            <span className="ui-skeleton ui-esqueleto__titulo" />
            <span className="ui-skeleton ui-esqueleto__linea" />
          </div>
        ))}
        {variante === "formulario" && items.map((i) => (
          <div key={i} className="ui-esqueleto__campo">
            <span className="ui-skeleton ui-esqueleto__etiqueta" />
            <span className="ui-skeleton ui-esqueleto__control" />
          </div>
        ))}
        {variante === "lista" && items.map((i) => (
          <div key={i} className="ui-esqueleto__fila">
            <span className="ui-skeleton ui-esqueleto__titulo" />
            <span className="ui-skeleton ui-esqueleto__linea" />
            <span className="ui-skeleton ui-esqueleto__linea ui-esqueleto__linea--corta" />
          </div>
        ))}
        {variante === "tabla" && (
          <div className="ui-esqueleto__tabla">
            <div className="ui-esqueleto__tabla-fila ui-esqueleto__tabla-fila--cabecera">
              {celdas.map((c) => <span key={c} className="ui-skeleton ui-esqueleto__celda" />)}
            </div>
            {items.map((i) => (
              <div key={i} className="ui-esqueleto__tabla-fila">
                {celdas.map((c) => <span key={c} className="ui-skeleton ui-esqueleto__celda" />)}
              </div>
            ))}
          </div>
        )}
        {variante === "indicadores" && items.map((i) => (
          <div key={i} className="ui-esqueleto__indicador">
            <span className="ui-skeleton ui-esqueleto__etiqueta" />
            <span className="ui-skeleton ui-esqueleto__cifra" />
            <span className="ui-skeleton ui-esqueleto__linea ui-esqueleto__linea--corta" />
          </div>
        ))}
      </div>
      {tarda && conAviso && <p className="ui-esqueleto__espera" role="status">{TEXTO_ESPERA_LARGA}</p>}
    </div>
  );
}
