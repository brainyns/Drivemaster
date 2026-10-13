import { useCallback, useEffect, useState } from "react";
import { subscribeNotifications } from "../services/websocketService";
import { listarMisAvisos } from "../services/notificacionesService";

/**
 * Contador de avisos sin leer: lo trae del servidor al montar (y cada 2 minutos como respaldo)
 * y lo actualiza al instante con cada aviso en vivo. `latido` cambia con cada aviso nuevo.
 * La conexión en vivo la abre App para toda la sesión.
 */
export default function useNoLeidas(activa) {
  const [noLeidas, setNoLeidas] = useState(0);
  const [latido, setLatido] = useState(0);

  const refrescar = useCallback(async () => {
    try {
      const data = await listarMisAvisos(1);
      setNoLeidas(data?.noLeidas || 0);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    if (!activa) return undefined;
    const inicial = setTimeout(refrescar, 0);
    const respaldo = setInterval(refrescar, 120000);
    const unsub = subscribeNotifications((msg) => {
      if (msg?.type !== "notificacion") return;
      if (typeof msg.noLeidas === "number") setNoLeidas(msg.noLeidas);
      setLatido((n) => n + 1);
    });
    return () => { clearTimeout(inicial); clearInterval(respaldo); unsub(); };
  }, [activa, refrescar]);

  return { noLeidas, setNoLeidas, latido };
}
