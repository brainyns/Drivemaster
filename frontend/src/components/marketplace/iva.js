// IVA para mostrar precios con el impuesto incluido. El cálculo real (cotizaciones, compras) vive en el
// backend; aquí solo se formatea lo que ve el usuario: precio final con IVA, como pidió el negocio.
// App.jsx fija el porcentaje real desde /api/config/parametros/IVA al arrancar; si no llega, se usa 19 %.

let pctActual = 19;

export function setIvaPct(valor) {
  const n = Number(valor);
  if (Number.isFinite(n) && n >= 0 && n <= 100) pctActual = n;
}

export const ivaPctActual = () => pctActual;

/** Precio con IVA del catálogo (los precios de base vienen sin IVA). Redondeo al peso. */
export const conIva = (neto) => Math.round((Number(neto) || 0) * (1 + pctActual / 100));
