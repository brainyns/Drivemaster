// Nombre visible de una categoría de producto. Los valores guardados (CATEGORIAS en pages/tienda/reglasProducto.js,
// los productos ya creados y los filtros del servidor) siguen sin tildes: aquí solo se escribe bien lo que se ve.
// Solo se agregan las tildes; las mayúsculas quedan como en la lista. Sin componentes: lo prueban los tests con node --test.

const ETIQUETAS = {
  "Transmision y Embrague": "Transmisión y Embrague",
  "Suspension y Direccion": "Suspensión y Dirección",
  "Sistema Electrico": "Sistema Eléctrico",
  "Baterias y Carga": "Baterías y Carga",
  "Combustible e Inyeccion": "Combustible e Inyección",
  "Aire Acondicionado y Climatizacion": "Aire Acondicionado y Climatización",
  "Carroceria Exterior": "Carrocería Exterior",
  "Iluminacion": "Iluminación",
  "Neumaticos y Llantas": "Neumáticos y Llantas",
};

/** "Suspension y Direccion" → "Suspensión y Dirección"; cualquier otra categoría se muestra tal cual. */
export const etiquetaCategoria = (valor) => (valor && ETIQUETAS[valor]) || valor;
