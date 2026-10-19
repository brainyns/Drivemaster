import Pasos from "../ui/Pasos";
import { PASOS_SERVICIO } from "./solicitudDiagnostico";

/** Los cinco pasos del servicio (diagnóstico → instalación) con el paso actual resaltado. */
export default function PasosServicio({ actual = 1 }) {
  return <Pasos pasos={PASOS_SERVICIO} actual={actual} id="pasos-servicio" />;
}
