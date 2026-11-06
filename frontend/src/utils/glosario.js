// Glosario del dinero: una frase simple por término, la misma en todas las pantallas (informes del taller, de la
// tienda y del admin, y Pagos). Sin componentes: lo prueban los tests.

export const GLOSARIO = {
  "Ventas": "Lo que pagaron los clientes en el período, con IVA incluido.",
  "IVA": "El impuesto que va incluido en el precio. No es ganancia: se le paga al Estado.",
  "Comisión": "Lo que cobra DriveMaster por cada venta: un porcentaje del precio con IVA, sin envío.",
  "Lo tuyo": "Lo que te queda de las ventas después de quitar el IVA y la comisión.",
  "Ya te pagaron": "DriveMaster ya te transfirió este dinero a tu cuenta.",
  "Por recibir": "El pedido ya se entregó: DriveMaster te lo transfiere cuando apruebe el pago.",
  "Falta que se entregue": "El cliente ya pagó, pero el dinero se habilita cuando el pedido o el vehículo se entrega.",
  "Cancelados": "Pagos que se le devolvieron al cliente: no cuentan como venta.",
  "Adelanto del diagnóstico": "Lo que el cliente pagó por el diagnóstico. Se descuenta de la cotización.",
  "Resta de la cotización": "Lo que el cliente paga de la cotización después de descontar el adelanto.",
  "Mano de obra": "Lo que cobras por tu trabajo en el vehículo, sin los repuestos.",
  "Repuestos": "Las piezas que vendiste de tu inventario en las cotizaciones.",
  "Por pagar": "Ya se entregó: falta que DriveMaster le transfiera al taller o a la tienda.",
  "Pagado": "DriveMaster ya le transfirió el dinero al taller o a la tienda.",
  "Se transfiere": "Lo que recibe el negocio: el valor de la venta menos la comisión.",
  "Tu ganancia": "La comisión que DriveMaster ya cobró en pagos transferidos.",
  "Por cobrar": "Comisión de pagos que todavía no se transfieren al negocio.",
  "Dinero que pasó por la plataforma": "Todo lo que pagaron los clientes con Wompi. La mayor parte es de los talleres y las tiendas.",
  "Saldo a favor": "Dinero que un negocio le debe a DriveMaster después de un reembolso.",
};

/** Los términos pedidos, con su frase, en el orden dado. Un término que no está en el glosario se omite. */
export function terminos(...claves) {
  return claves.filter((c) => GLOSARIO[c]).map((c) => ({ termino: c, texto: GLOSARIO[c] }));
}
