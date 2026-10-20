import { useState, useEffect, useRef } from "react";
import { getToken } from "../services/authService";
import { verificarPago } from "../services/solicitudService";
import {
  clasificar, debeReintentar, conLimiteDeTiempo, TiempoAgotado,
  INTERVALO_REINTENTO_MS, LIMITE_VERIFICACION_MS,
} from "./estadoPago";
import EncabezadoPagina from "../components/ui/EncabezadoPagina";
import Icon from "../components/public/Icon";
import "../css/pago-resultado.css";

// Pagos del marketplace (el backend envía "concepto"); las compras no lo traen.
const TEXTO_MARKETPLACE = {
  DIAGNOSTICO: "Recibimos el pago del diagnóstico. Tu cita de revisión quedó confirmada.",
  COTIZACION: "Recibimos el pago de la cotización. Pedimos los repuestos y el taller seguirá con el trabajo.",
  CATALOGO: "Recibimos el pago de tu compra. Avisamos a cada tienda; el estado de cada pedido está en “Mis compras”. Si elegiste retiro, el código te llegó por correo.",
};

/** Pantalla donde el cliente sigue cada tipo de pago. */
function lugar(concepto) {
  if (concepto === "CATALOGO") return "“Mis compras”";
  if (concepto) return "“Mis servicios”";
  return "“Mis solicitudes”";
}

// Si el servidor tarda más que esto en la primera consulta, se avisa que puede estar iniciándose.
const AVISO_LENTO_MS = 8000;

// Ícono del resultado: el color acompaña al título, que siempre dice qué pasó
const MARCAS = { ok: "check", aviso: "alert", error: "close", espera: "clock" };
const Marca = ({ tono }) => (
  <span className={`pr-marca pr-marca--${tono}`} aria-hidden="true"><Icon name={MARCAS[tono]} size={28} /></span>
);

export default function PagoResultado({ onIrSolicitudes, onPagoExitoso, onIrServicios, onCompraCatalogoPagada, onIrMisCompras }) {
  const token = getToken();
  const [params] = useState(() => new URLSearchParams(window.location.search));
  const transaccionId = params.get("id") || "";
  // confirmar-redirect no pudo verificar al volver de Wompi; esta pantalla lo intenta por su cuenta
  const falloAlVolver = params.get("verificacion") === "error";

  // VERIFICANDO | APROBADO | PENDIENTE | RECHAZADO | NO_VERIFICADO | SIN_RESPUESTA
  const [fase, setFase] = useState(transaccionId ? "VERIFICANDO" : "NO_VERIFICADO");
  const [consultando, setConsultando] = useState(Boolean(transaccionId)); // se volverá a consultar sola
  const [lento, setLento] = useState(false);
  const [concepto, setConcepto] = useState(null); // null en compras normales
  const [aplicacion, setAplicacion] = useState(null); // { aplicado, reembolsoPendiente, estadoPago } del marketplace
  const [ronda, setRonda] = useState(0); // "Consultar de nuevo" reinicia los reintentos

  // Los avisos al padre van en un ref: si fueran dependencias del efecto, cada render del padre
  // (que crea funciones nuevas) reiniciaría los reintentos.
  const avisos = useRef({ onPagoExitoso, onCompraCatalogoPagada });
  useEffect(() => {
    avisos.current = { onPagoExitoso, onCompraCatalogoPagada };
  });

  useEffect(() => {
    if (!transaccionId) return;
    let cancelado = false;
    let espera;
    const avisoLento = setTimeout(() => { if (!cancelado) setLento(true); }, AVISO_LENTO_MS);
    const dormir = (ms) => new Promise((listo) => { espera = setTimeout(listo, ms); });

    const verificar = async () => {
      const intentos = {};
      for (;;) {
        let clase;
        let res = null;
        try {
          res = await conLimiteDeTiempo(
            (signal) => verificarPago(token, transaccionId, { signal }),
            LIMITE_VERIFICACION_MS,
          );
          clase = clasificar(res.estado);
        } catch (e) {
          if (!(e instanceof TiempoAgotado)) console.error(e);
          clase = e instanceof TiempoAgotado ? "SIN_RESPUESTA" : "NO_VERIFICADO";
        }
        if (cancelado) return;
        clearTimeout(avisoLento);

        // Solo informativo: el backend ya verificó y aplicó el pago con Wompi
        if (res?.concepto) {
          setConcepto(res.concepto);
          setAplicacion({ aplicado: res.aplicado, reembolsoPendiente: res.reembolsoPendiente, estadoPago: res.estadoPago });
        }
        intentos[clase] = (intentos[clase] || 0) + 1;
        const seguir = debeReintentar(clase, intentos[clase]);
        setFase(clase);
        setConsultando(seguir);
        setLento(false);

        if (!seguir) {
          const { onPagoExitoso: exito, onCompraCatalogoPagada: compraPagada } = avisos.current;
          if (clase === "APROBADO" && !res.concepto) {
            exito && exito();
          }
          // Compra del catálogo aplicada: se vacían del carrito solo los productos que se pagaron
          if (clase === "APROBADO" && res.concepto === "CATALOGO" && res.aplicado !== false) {
            compraPagada && compraPagada();
          }
          return;
        }
        await dormir(INTERVALO_REINTENTO_MS);
        if (cancelado) return;
      }
    };
    verificar();
    return () => { cancelado = true; clearTimeout(espera); clearTimeout(avisoLento); };
  }, [transaccionId, token, ronda]);

  const consultarDeNuevo = () => {
    setFase("VERIFICANDO");
    setConsultando(true);
    setLento(false);
    setRonda((r) => r + 1);
  };

  const salir = (accion) => () => {
    if (window.history?.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }
    accion && accion();
  };

  const transaccion = transaccionId && (
    <p className="pr-transaccion">Transacción: {transaccionId}</p>
  );
  const enCurso = (texto) => (
    <>
      <span className="pr-girando" aria-hidden="true" />
      <p className="pr-nota" role="status">{texto}</p>
    </>
  );
  const botonConsultar = !consultando && transaccionId && (
    <button type="button" className="ui-btn ui-btn--secundario" onClick={consultarDeNuevo}>
      <Icon name="refresh" size={16} />
      Consultar de nuevo
    </button>
  );

  let cuerpo;
  if (fase === "VERIFICANDO") {
    cuerpo = (
      <>
        <span className="pr-girando" aria-hidden="true" />
        <h2 className="pr-titulo" role="status">Verificando tu pago…</h2>
        {lento && (
          <p className="pr-nota">
            El servidor está tardando en responder: si llevaba un rato sin uso, puede tardar hasta un minuto en iniciarse.
            No cierres esta página ni vuelvas a pagar.
          </p>
        )}
      </>
    );
  } else if (fase === "APROBADO" && concepto && aplicacion?.aplicado === false) {
    cuerpo = (
      <>
        <Marca tono="aviso" />
        <h2 className="pr-titulo">
          {aplicacion.reembolsoPendiente ? "Recibimos tu pago, pero no se pudo aplicar" : "Estamos confirmando tu pago"}
        </h2>
        <p className="pr-texto">
          {aplicacion.reembolsoPendiente
            ? "Te enviamos un correo con el motivo. DriveMaster te devolverá el dinero."
            : `Revisa el estado ${concepto === "CATALOGO" ? "de tu compra" : "de tu solicitud"} en unos segundos en ${lugar(concepto)}.`}
        </p>
      </>
    );
  } else if (fase === "APROBADO") {
    cuerpo = (
      <>
        <Marca tono="ok" />
        <h2 className="pr-titulo">¡Pago exitoso!</h2>
        {concepto ? (
          <>
            <p className="pr-texto">{TEXTO_MARKETPLACE[concepto]}</p>
            <p className="pr-texto">Te enviamos el comprobante de pago por correo.</p>
          </>
        ) : (
          <p className="pr-texto">Tu pedido fue confirmado y ya está en proceso.</p>
        )}
        {transaccion}
      </>
    );
  } else if (fase === "PENDIENTE") {
    // Nunca "no se hizo ningún cobro": el banco todavía puede aprobarlo
    cuerpo = (
      <>
        <Marca tono="espera" />
        <h2 className="pr-titulo">Tu pago está en proceso</h2>
        <p className="pr-texto">
          Tu banco (PSE) o Nequi todavía no lo confirma. No vuelvas a pagar.
        </p>
        {consultando ? enCurso("Seguimos consultando cada pocos segundos…") : (
          <p className="pr-texto">
            Cuando se confirme te llegará un correo y lo verás en {lugar(concepto)}. Si el banco lo rechaza, no se hará ningún cobro.
          </p>
        )}
        {transaccion}
      </>
    );
  } else if (fase === "RECHAZADO") {
    cuerpo = (
      <>
        <Marca tono="error" />
        {concepto ? (
          <>
            <h2 className="pr-titulo">Pago no aprobado</h2>
            <p className="pr-texto">
              No se hizo ningún cobro. {concepto === "CATALOGO"
                ? "Tu carrito sigue guardado: puedes intentarlo de nuevo."
                : "Puedes intentarlo de nuevo desde “Mis servicios”."}
            </p>
          </>
        ) : (
          <>
            <h2 className="pr-titulo">Pago rechazado</h2>
            <p className="pr-texto">Tu pago no fue aprobado. Intenta nuevamente.</p>
          </>
        )}
      </>
    );
  } else if (fase === "SIN_RESPUESTA") {
    cuerpo = (
      <>
        <Marca tono="aviso" />
        <h2 className="pr-titulo">El servidor no responde</h2>
        <p className="pr-texto">
          No pudimos comunicarnos con DriveMaster: puede estar iniciándose después de un rato sin uso.
          Tu pago no se pierde, Wompi nos lo confirma por su cuenta. No vuelvas a pagar.
        </p>
        {consultando ? enCurso("Reintentando…") : (
          <p className="pr-texto">Prueba de nuevo en un minuto.</p>
        )}
        {transaccion}
      </>
    );
  } else {
    cuerpo = (
      <>
        <Marca tono="aviso" />
        <h2 className="pr-titulo">No se pudo verificar el pago</h2>
        {!transaccionId ? (
          <p className="pr-texto">
            No encontramos una transacción para verificar. Si realizaste el pago, pronto quedará confirmado.
          </p>
        ) : (
          <>
            <p className="pr-texto">
              {falloAlVolver && "Volviste de Wompi, pero no pudimos confirmar el pago en ese momento. "}
              Si realizaste el pago, se confirmará automáticamente y lo verás en {lugar(concepto)}. No vuelvas a pagar sin revisarlo antes.
            </p>
            {consultando && enCurso("Reintentando…")}
            {transaccion}
          </>
        )}
      </>
    );
  }

  let botonSalir = null;
  if (fase !== "VERIFICANDO") {
    botonSalir = concepto === "CATALOGO" ? (
      <button type="button" className="ui-btn ui-btn--primario" onClick={salir(onIrMisCompras)}>Ver mis compras</button>
    ) : concepto ? (
      <button type="button" className="ui-btn ui-btn--primario" onClick={salir(onIrServicios)}>Ver mis servicios</button>
    ) : (
      <button type="button" className="ui-btn ui-btn--primario" onClick={salir(onIrSolicitudes)}>Ver mis solicitudes</button>
    );
  }

  return (
    <div className="pr-root">
      <EncabezadoPagina titulo="Resultado del pago" descripcion="Confirmación de tu pago en línea." />
      <section className="pr-tarjeta" aria-live="polite">
        {cuerpo}
        <div className="pr-acciones">
          {fase !== "VERIFICANDO" && fase !== "APROBADO" && fase !== "RECHAZADO" && botonConsultar}
          {botonSalir}
        </div>
      </section>
    </div>
  );
}
