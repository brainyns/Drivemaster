// Gestión de usuarios del SUPERADMIN: etiquetas, validación de los formularios y el cuerpo de la edición. Sin
// componentes: lo prueban los tests con node --test. Las reglas son las del backend (UsuarioController): desde aquí
// solo se crean o asignan SUPERADMIN (ADMIN y VENDEDOR quedan de cuentas anteriores), el rol de un cliente, una tienda
// o un taller no se cambia, nadie se desactiva ni se cambia el rol a sí mismo y siempre queda un SUPERADMIN activo.

const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MINIMO_CLAVE = 6;

export const ROLES_DEL_EQUIPO = ["SUPERADMIN"];

export const ROLES = {
  CLIENTE: { label: "Cliente", plural: "Clientes" },
  TIENDA: { label: "Tienda", plural: "Tiendas" },
  TALLER: { label: "Taller", plural: "Talleres" },
  // Roles retirados: sus cuentas se siguen listando (en "Todos") para pasarlas a super admin o desactivarlas
  ADMIN: { label: "Administrador (retirado)", plural: "Administradores" },
  SUPERADMIN: { label: "Super admin", plural: "Super admins" },
  MECANICO: { label: "Mecánico", plural: "Mecánicos" },
  VENDEDOR: { label: "Vendedor (retirado)", plural: "Vendedores" },
};

// Los filtros por rol que siempre se ven; los mecánicos solo si hay cuentas. ADMIN y VENDEDOR ya no tienen filtro.
const ROLES_FIJOS = ["CLIENTE", "TIENDA", "TALLER", "SUPERADMIN"];
const ROLES_RETIRADOS = ["ADMIN", "VENDEDOR"];

export const etiquetaRol = (rol) => ROLES[rol]?.label || rol || "Sin rol";

/** Roles para filtrar, en orden: los fijos y los demás que tengan cuentas (o el que está elegido). */
export function rolesParaFiltrar(porRol = {}, elegido = "") {
  const extra = Object.keys(ROLES).filter((r) => !ROLES_FIJOS.includes(r) && !ROLES_RETIRADOS.includes(r)
    && ((porRol[r] || 0) > 0 || r === elegido));
  return [...ROLES_FIJOS, ...extra];
}

/** El rol solo se cambia en cuentas del equipo, también las de roles retirados (para pasarlas a super admin). */
export const rolEditable = (rol) => ROLES_DEL_EQUIPO.includes(rol) || ROLES_RETIRADOS.includes(rol);

/**
 * Una fila de la lista con los campos de UsuarioAdminDTO. La lista de la plataforma llama a las fechas creado y
 * ultimoAcceso; aquí quedan como fechaCreacion y ultimoLogin, como en el DTO.
 */
export function filaUsuario(u) {
  return {
    id: u.id,
    nombre: u.nombre || "",
    correo: u.correo || "",
    rol: u.rol || "",
    activo: u.activo !== false,
    fechaCreacion: u.fechaCreacion ?? u.creado ?? null,
    ultimoLogin: u.ultimoLogin ?? u.ultimoAcceso ?? null,
  };
}

export function erroresNuevoUsuario({ nombre, correo, password, rol }) {
  const e = {};
  if (!String(nombre || "").trim()) e.nombre = "Escribe el nombre completo.";
  if (!String(correo || "").trim()) e.correo = "Escribe el correo con el que va a entrar.";
  else if (!CORREO.test(correo.trim())) e.correo = "Escribe un correo válido, como nombre@drivemaster.co.";
  if (!password) e.password = "Escribe una contraseña temporal.";
  else if (password.length < MINIMO_CLAVE) e.password = `Usa al menos ${MINIMO_CLAVE} caracteres.`;
  if (!ROLES_DEL_EQUIPO.includes(rol)) e.rol = "Las cuentas del equipo son de super admin.";
  return e;
}

export function erroresEdicion({ nombre, password }) {
  const e = {};
  if (!String(nombre || "").trim()) e.nombre = "Escribe el nombre completo.";
  if (password && password.length < MINIMO_CLAVE) e.password = `Usa al menos ${MINIMO_CLAVE} caracteres o déjala vacía.`;
  return e;
}

/** Cuerpo del PUT /api/usuarios/{id}: el nombre siempre; la contraseña y el rol solo si cambian. */
export function cuerpoEdicion(form, original) {
  const body = { nombre: form.nombre.trim() };
  if (form.password) body.password = form.password;
  if (rolEditable(original.rol) && form.rol && form.rol !== original.rol) body.rol = form.rol;
  return body;
}

/** Errores del backend que corresponden a un campo, para mostrarlos en línea. */
export function erroresDelServidor(mensaje) {
  if (/correo ya registrado/i.test(mensaje || "")) return { correo: "Ya hay una cuenta con ese correo. Usa otro." };
  return null;
}

/** Lo que pasa al desactivar, dicho antes de confirmar (las tiendas y los talleres quedan en pausa). */
/**
 * Lo que dice el modal antes de desactivar. enCurso (de /usuarios/{id}/en-curso) trae los pedidos de la tienda o las
 * solicitudes del taller que siguen abiertos; si no llegó, se avisa igual sin la cifra.
 */
export function avisoDesactivar(u, enCurso = null) {
  const base = "Ya no podrá iniciar sesión hasta que la vuelvas a activar desde esta lista.";
  if (u.rol === "TIENDA") {
    const n = enCurso?.pedidos;
    const curso = n ? ` Tiene ${n} ${n === 1 ? "pedido" : "pedidos"} en curso: seguirán su curso, pero la tienda no podrá atenderlos desde el panel.` : "";
    return `${base} Su tienda queda en pausa y se reactiva aparte, en Aprobaciones.${curso}`;
  }
  if (u.rol === "TALLER") {
    const n = enCurso?.solicitudes;
    const curso = n ? ` Tiene ${n} ${n === 1 ? "solicitud" : "solicitudes"} en curso que el taller ya no podrá atender desde el panel.` : "";
    return `${base} Su taller queda en pausa y se reactiva aparte, en Aprobaciones.${curso}`;
  }
  return base;
}
