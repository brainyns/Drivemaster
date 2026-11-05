import Campo from "../ui/Campo";
import { CIUDADES_ORDENADAS } from "../../utils/ciudades";

/** La ciudad del cliente, siempre de la lista (registro, Mi perfil y la ventana que la pide). */
export default function SelectorCiudad({ id, valor, onCambio, error, className }) {
  return (
    <Campo id={id} label="Ciudad" error={error} className={className}>
      <select className="ui-field__input" value={valor} onChange={(e) => onCambio(e.target.value)}
        autoComplete="address-level2" required>
        <option value="">Escoge tu ciudad</option>
        {CIUDADES_ORDENADAS.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
    </Campo>
  );
}
