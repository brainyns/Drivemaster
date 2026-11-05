import InfoAyuda from "./InfoAyuda";
import { GLOSARIO } from "../../utils/glosario";
import { formatPrecio } from "../servicio/estadosServicio";
import "../../css/banda-dinero.css";

/**
 * Ventas → − IVA → − Comisión → = Lo tuyo, del periodo elegido. Cada paso con su "?" del glosario. banda: el resultado
 * de bandaDinero(). titulo: lo que cubre ("En los últimos 6 meses").
 */
export default function BandaDinero({ banda, titulo }) {
  if (!banda) return null;
  const pasos = [
    { clave: "Ventas", signo: "", valor: banda.ventas },
    { clave: "IVA", signo: "−", valor: banda.iva, falta: "Sin dato" },
    { clave: "Comisión", signo: "−", valor: banda.comision, nota: banda.comisionSeDescuenta ? null : "No se descuenta todavía" },
  ];
  return (
    <section className="bd-banda" aria-label={titulo}>
      {titulo && <p className="bd-banda__titulo">{titulo}</p>}
      <ol className="bd-banda__pasos">
        {pasos.map((p) => (
          <li key={p.clave} className="bd-paso">
            <span className="bd-paso__nombre">
              {p.signo && <span className="bd-paso__signo" aria-hidden="true">{p.signo}</span>}{p.clave}
              <InfoAyuda texto={GLOSARIO[p.clave]} etiqueta={`Qué significa ${p.clave}`} />
            </span>
            <span className="bd-paso__valor">{p.valor == null ? p.falta : `${p.signo ? `${p.signo} ` : ""}${formatPrecio(p.valor)}`}</span>
            {p.nota && <span className="bd-paso__nota">{p.nota}</span>}
          </li>
        ))}
        <li className="bd-paso bd-paso--total">
          <span className="bd-paso__nombre">
            <span className="bd-paso__signo" aria-hidden="true">=</span>Lo tuyo
            <InfoAyuda texto={GLOSARIO["Lo tuyo"]} etiqueta="Qué significa Lo tuyo" />
          </span>
          <span className="bd-paso__valor">{banda.loTuyo == null ? "—" : formatPrecio(banda.loTuyo)}</span>
          {banda.loTuyo == null && <span className="bd-paso__nota">Algún pago antiguo no guardó su IVA</span>}
        </li>
      </ol>
    </section>
  );
}
