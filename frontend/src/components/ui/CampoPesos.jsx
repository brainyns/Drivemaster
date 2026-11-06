import { useLayoutEffect, useRef } from "react";
import { formatPesos, pesosAlEscribir } from "../../utils/formato";

// Cuántos dígitos (y comas) hay antes de una posición: es lo que no cambia al poner o quitar los puntos de miles
const significativos = (texto) => (texto.match(/[\d,]/g) || []).length;

/** La posición del texto formateado que queda justo después del dígito número n. */
function posicionTras(texto, n) {
  if (n <= 0) return 0;
  let vistos = 0;
  for (let i = 0; i < texto.length; i++) {
    if (/[\d,]/.test(texto[i])) vistos++;
    if (vistos === n) return i + 1;
  }
  return texto.length;
}

/**
 * Campo de dinero: se escribe con o sin puntos de miles y se ve formateado ("481.500"). onChange recibe
 * { target: { name, value } } con el texto formateado; el número sale con parsePesos(value). Un value numérico (el que
 * llega del backend) se muestra formateado. decimales: acepta coma decimal (hasta 2), para porcentajes o unidades.
 */
export default function CampoPesos({ value, onChange, name, decimales = false, className = "ui-field__input", ...props }) {
  const ref = useRef(null);
  const cursor = useRef(null);
  const visible = typeof value === "number" ? formatPesos(value, decimales ? 2 : 0) : pesosAlEscribir(value, { decimales });

  // Después de formatear, el cursor vuelve a quedar tras el mismo dígito (no salta al final)
  useLayoutEffect(() => {
    if (cursor.current == null || !ref.current || document.activeElement !== ref.current) return;
    const pos = posicionTras(ref.current.value, cursor.current);
    ref.current.setSelectionRange(pos, pos);
    cursor.current = null;
  });

  const cambiar = (e) => {
    const el = e.target;
    const antes = el.value.slice(0, el.selectionStart ?? el.value.length);
    cursor.current = significativos(antes);
    onChange?.({ target: { name, value: pesosAlEscribir(el.value, { decimales }) } });
  };

  return (
    <input {...props} ref={ref} name={name} className={className} type="text"
      inputMode={decimales ? "decimal" : "numeric"} autoComplete="off" value={visible} onChange={cambiar} />
  );
}
