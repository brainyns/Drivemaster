// Navegación entre secciones del panel. La app no usa rutas: navega con el estado `pagina` de App y le pasa a la
// sección un parámetro (idSeleccionado) con irA(pagina, param). Sin componentes: lo prueban los tests.

/**
 * Lo que llega a una sección en el segundo argumento de irA: el filtro de la pestaña como texto (menú lateral) o
 * { filtro, abrir, volverA } para abrir un elemento concreto (una solicitud, un pedido) y que "Volver" regrese a la
 * página de origen; sin volverA, "Volver" queda en la lista de la misma sección.
 */
export function destinoSeccion(param) {
  if (param && typeof param === "object") {
    return { filtro: param.filtro || undefined, abrir: param.abrir || null, volverA: param.volverA || null };
  }
  return { filtro: param || undefined, abrir: null, volverA: null };
}
