import { useEffect, useState } from "react";
import { actualizarMiTienda, miTienda } from "../services/tiendaService";
import DatosTiendaPropia from "../components/DatosTiendaPropia";
import { cambiarResumenSemanal, probarResumenSemanal, resumenSemanal } from "../services/informesTiendaService";
import "../css/aprobaciones.css";

/**
 * La TIENDA completa sus datos públicos: ciudad, dirección, horario de atención (para el retiro) y, si
 * hace envíos, el costo fijo (solo dentro de su ciudad). Sin ciudad, dirección y horario sus productos no
 * aparecen en el catálogo. Lo administrativo (NIT, plan, correo) no se muestra ni se edita aquí.
 */
export default function DatosMiTienda({ token, onGuardado }) {
  const [tienda, setTienda] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let vivo = true;
    miTienda(token)
      .then((t) => { if (vivo) setTienda(t); })
      .catch((e) => { if (vivo) setError(e.message); });
    return () => { vivo = false; };
  }, [token]);

  const guardar = async (tk, datos) => {
    const actualizada = await actualizarMiTienda(tk, datos);
    setTienda(actualizada);
    onGuardado?.(actualizada);
    return actualizada;
  };

  if (error) return <p className="ap-alerta ap-alerta--error" role="alert" style={{ margin: 16 }}>{error}</p>;
  if (!tienda) return <p style={{ padding: 16 }}>Cargando...</p>;

  return (
    <div className="ap-page">
      <DatosTiendaPropia key={tienda.id} token={token} tienda={tienda}
        alGuardar={guardar} titulo="Mi tienda" />
      <ResumenSemanal token={token} />
    </div>
  );
}

/** Interruptor del resumen semanal por correo (lunes 7:00) y envío de prueba al correo del dueño. */
function ResumenSemanal({ token }) {
  const [activo, setActivo] = useState(null);
  const [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState(null);

  useEffect(() => {
    let vivo = true;
    resumenSemanal(token)
      .then((r) => { if (vivo) setActivo(r.activo); })
      .catch((e) => { if (vivo) setAviso({ error: true, texto: e.message }); });
    return () => { vivo = false; };
  }, [token]);

  const ejecutar = async (accion) => {
    setOcupado(true);
    setAviso(null);
    try { await accion(); } catch (e) { setAviso({ error: true, texto: e.message }); } finally { setOcupado(false); }
  };

  const cambiar = (e) => ejecutar(async () => {
    const r = await cambiarResumenSemanal(token, e.target.checked);
    setActivo(r.activo);
    setAviso({ texto: r.activo ? "Listo: recibirás el resumen cada lunes a las 7:00." : "Resumen semanal desactivado." });
  });

  const probar = () => ejecutar(async () => setAviso({ texto: (await probarResumenSemanal(token)).mensaje }));

  return (
    <section className="ap-propia" aria-labelledby="resumen-semanal-titulo" style={{ marginTop: 16 }}>
      <div className="ap-propia__cabecera">
        <h2 id="resumen-semanal-titulo">Resumen semanal por correo</h2>
      </div>
      <p className="ap-sub">
        Cada lunes a las 7:00 te enviamos cómo te fue la semana: ventas frente a la anterior, lo más vendido, qué reponer y
        el dinero quieto en inventario. Las cifras salen de tus pedidos; la IA solo redacta el titular.
      </p>
      <div className="ap-propia__form">
        <label className="ap-propia__check">
          <input type="checkbox" checked={!!activo} disabled={activo === null || ocupado} onChange={cambiar} />
          Recibir el resumen semanal
        </label>
        <div className="ap-propia__acciones">
          <button type="button" className="ap-btn" onClick={probar} disabled={ocupado} aria-busy={ocupado}>
            {ocupado ? "Enviando..." : "Enviarme una prueba ahora"}
          </button>
        </div>
      </div>
      {aviso && <p className={`ap-alerta ${aviso.error ? "ap-alerta--error" : "ap-alerta--ok"}`} role={aviso.error ? "alert" : "status"}>{aviso.texto}</p>}
    </section>
  );
}
