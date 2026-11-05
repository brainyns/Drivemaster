import { useEffect, useState } from "react";
import { formatPrecio } from "../servicio/estadosServicio";
import "../../css/banda-dinero.css";

/**
 * "Este mes: $X", arriba de los informes. Es el dato exacto del mes en curso: se consulta aparte (meses=1), así no
 * depende del periodo elegido abajo. Solo lee. cargar: () => Promise<number|null>. version: para volver a consultar.
 */
export default function EsteMes({ cargar, detalle, version = 0 }) {
  const [estado, setEstado] = useState({ version: null, valor: null, error: false });

  useEffect(() => {
    let vivo = true;
    cargar()
      .then((valor) => { if (vivo) setEstado({ version, valor, error: false }); })
      .catch(() => { if (vivo) setEstado({ version, valor: null, error: true }); });
    return () => { vivo = false; };
    // cargar cambia en cada render de quien lo usa: solo se vuelve a pedir cuando cambia version
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version]);

  const cargando = estado.version !== version;
  const mes = new Date().toLocaleDateString("es-CO", { month: "long" });
  return (
    <p className="bd-mes" aria-live="polite" aria-busy={cargando}>
      <span className="bd-mes__etiqueta">Este mes</span>
      <strong className="bd-mes__valor">
        {cargando ? "…" : estado.error || estado.valor == null ? "Sin dato" : formatPrecio(estado.valor)}
      </strong>
      <span className="bd-mes__detalle">{detalle} · del 1 de {mes} a hoy</span>
    </p>
  );
}
