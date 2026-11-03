// La banda de los informes: Ventas → − IVA → − Comisión → = Lo tuyo. Sin componentes: lo prueban los tests.

/**
 * desglose: el resumen del backend (DesgloseLiquidacionDTO.Resumen: venta con IVA, comisión que corresponde, comisión
 * descontada y lo que recibe). iva: el IVA del periodo, o null si una línea antigua no lo guardó.
 * La comisión que se resta es la que de verdad se descontó: con el cobro apagado es 0 y la banda lo dice.
 * loTuyo es null cuando falta el IVA: no se inventa la cifra.
 */
export function bandaDinero(desglose, iva) {
  if (!desglose) return null;
  const ventas = Math.round(Number(desglose.venta) || 0);
  const comision = Math.round(Number(desglose.comisionDescontada) || 0);
  const conIva = iva == null ? null : Math.round(Number(iva) || 0);
  return {
    ventas,
    iva: conIva,
    comision,
    comisionSeDescuenta: desglose.comisionSeDescuenta !== false,
    loTuyo: conIva == null ? null : Math.max(0, ventas - conIva - comision),
  };
}

/** "Este mes": lo tuyo del mes en curso, o null si no se puede saber. */
export const loTuyoDelMes = (desglose, iva) => bandaDinero(desglose, iva)?.loTuyo ?? null;

/** Lo que ganó DriveMaster en el mes en curso: la comisión cobrada del último mes del reporte (meses=1). */
export function gananciaDelMes(reporte) {
  const meses = reporte?.meses;
  if (!Array.isArray(meses) || meses.length === 0) return null;
  return Math.round(Number(meses[meses.length - 1].cobrada) || 0);
}
