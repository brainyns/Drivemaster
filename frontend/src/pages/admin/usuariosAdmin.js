// Gestión de usuarios del SUPERADMIN: etiquetas, validación de los formularios y el cuerpo de la edición. Sin
// componentes: lo prueban los tests con node --test. Las reglas son las del backend (UsuarioController): desde aquí
// solo se crean o asignan ADMIN y SUPERADMIN, y el rol de un cliente, una tienda o un taller no se cambia.

const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MINIMO_CLAVE = 6;

export const ROLES_DEL_EQUIPO = ["ADMIN", "SUPERADMIN"];

export const ROLES = {
  CLIENTE: { label: "Cliente", plural: "Clientes" },
  TIENDA: { label: "Tienda", plural: "Tiendas" },
  TALLER: { label: "Taller", plural: "Talleres" },
  ADMIN: { label: "Administrador", plural: "Administradores" },
  SUPERADMIN: { label: "Super admin", plural: "Super admins" },
  MECANICO: { label: "Mecánico", plural: "Mecánicos" },
  // Rol retirado: sus cuentas se siguen listando para reasignarlas o desactivarlas
  VENDEDOR: { label: "Vendedor (retirado)", plural: "Vendedores" },
};

// Los filtros por rol que siempre se ven; mecánicos y vendedores solo si hay cuentas
const ROLES_FIJOS = ["CLIENTE", "TIENDA", "TALLER", "ADMIN", "SUPERADMIN"];

export const etiquetaRol = (rol) => ROLES[rol]?.label || rol || "Sin rol";

/** Roles para filtrar, en orden: los fijos y los demás que tengan cuentas (o el que está elegido). */
export function rolesParaFiltrar(porRol = {}, elegido = "") {
  const extra = Object.keys(ROLES).filter((r) => !ROLES_FIJOS.includes(r) && ((porRol[r] || 0) > 0 || r === elegido));
  return [...ROLES_FIJOS, ...extra];
}

/** El rol solo se cambia en cuentas del equipo y en las del vendedor retirado (para reasignarlas). */
export const rolEditable = (rol) => ROLES_DEL_EQUIPO.includes(rol) || rol === "VENDEDOR";

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
  if (!ROLES_DEL_EQUIPO.includes(rol)) e.rol = "Elige administrador o super admin.";
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
export function avisoDesactivar(u) {
  const base = "No podrá iniciar sesión hasta que la vuelvas a activar desde esta lista.";
  if (u.rol === "TIENDA") return `${base} Su tienda queda en pausa y se reactiva aparte, en Aprobaciones.`;
  if (u.rol === "TALLER") return `${base} Su taller queda en pausa y se reactiva aparte, en Aprobaciones.`;
  return base;
}
