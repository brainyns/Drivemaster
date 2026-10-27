/**
 * Las ciudades que se escogen en el registro, en Mi perfil y en la ventana que la pide a quien no la tiene. Es la misma
 * lista de Ciudades.java en el backend (una prueba revisa que coincidan), que rechaza cualquier otra.
 */
export const CIUDADES = [
  "Bogotá D.C.", "Medellín", "Cali", "Barranquilla", "Cartagena", "Cúcuta", "Bucaramanga", "Ibagué",
  "Santa Marta", "Villavicencio", "Pereira", "Manizales", "Pasto", "Neiva", "Armenia", "Montería",
  "Valledupar", "Sincelejo", "Popayán", "Tunja", "Riohacha", "Florencia", "Quibdó", "Yopal", "Arauca",
  "San Andrés", "Leticia", "Mocoa", "San José del Guaviare", "Inírida", "Mitú", "Puerto Carreño",
  "Soacha", "Bello", "Soledad", "Itagüí", "Envigado", "Sabaneta", "Rionegro", "Apartadó", "Palmira",
  "Buenaventura", "Tuluá", "Buga", "Cartago", "Jamundí", "Yumbo", "Floridablanca", "Girón", "Piedecuesta",
  "Barrancabermeja", "Dosquebradas", "Zipaquirá", "Chía", "Cajicá", "Facatativá", "Fusagasugá", "Girardot",
  "Mosquera", "Madrid", "Funza", "Malambo", "Turbaco", "Arjona", "Magangué", "El Carmen de Bolívar",
  "Ciénaga", "Maicao", "Lorica", "Sogamoso", "Duitama",
];

// Otras formas de escribir la misma ciudad (como en el backend)
const ALIAS = {
  bogota: "Bogotá D.C.",
  bogotadistritocapital: "Bogotá D.C.",
  cartagenadeindias: "Cartagena",
  sanandresisla: "San Andrés",
  santiagodecali: "Cali",
};

const clave = (t) => t.trim().toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/[^a-z0-9]/g, "");

const POR_CLAVE = new Map(CIUDADES.map((c) => [clave(c), c]));
for (const [k, c] of Object.entries(ALIAS)) if (!POR_CLAVE.has(k)) POR_CLAVE.set(k, c);

/** La ciudad de la lista que corresponde al texto ("cartagena" → "Cartagena"), o "" si no está. */
export function ciudadDeLista(texto) {
  if (!texto || !String(texto).trim()) return "";
  return POR_CLAVE.get(clave(String(texto))) || "";
}

/** Las opciones del selector, ordenadas alfabéticamente sin importar tildes. */
export const CIUDADES_ORDENADAS = [...CIUDADES].sort((a, b) => a.localeCompare(b, "es"));
