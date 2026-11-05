package com.drivemaster.drivemaster.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.security.RolPermisos;

/**
 * Las ciudades que se escogen en el registro, en Mi perfil y en la ventana que la pide a quien no la tiene. Capitales
 * de departamento y los municipios grandes, con un nombre canónico. La misma lista está en frontend/src/utils/ciudades.js
 * (una prueba del frontend revisa que coincidan).
 */
public final class Ciudades {

    public static final List<String> LISTA = List.of(
            "Bogotá D.C.", "Medellín", "Cali", "Barranquilla", "Cartagena", "Cúcuta", "Bucaramanga", "Ibagué",
            "Santa Marta", "Villavicencio", "Pereira", "Manizales", "Pasto", "Neiva", "Armenia", "Montería",
            "Valledupar", "Sincelejo", "Popayán", "Tunja", "Riohacha", "Florencia", "Quibdó", "Yopal", "Arauca",
            "San Andrés", "Leticia", "Mocoa", "San José del Guaviare", "Inírida", "Mitú", "Puerto Carreño",
            "Soacha", "Bello", "Soledad", "Itagüí", "Envigado", "Sabaneta", "Rionegro", "Apartadó", "Palmira",
            "Buenaventura", "Tuluá", "Buga", "Cartago", "Jamundí", "Yumbo", "Floridablanca", "Girón", "Piedecuesta",
            "Barrancabermeja", "Dosquebradas", "Zipaquirá", "Chía", "Cajicá", "Facatativá", "Fusagasugá", "Girardot",
            "Mosquera", "Madrid", "Funza", "Malambo", "Turbaco", "Arjona", "Magangué", "El Carmen de Bolívar",
            "Ciénaga", "Maicao", "Lorica", "Sogamoso", "Duitama");

    // Otras formas de escribir la misma ciudad (ya normalizadas: sin tildes, minúsculas, solo letras y números)
    private static final Map<String, String> ALIAS = Map.of(
            "bogota", "Bogotá D.C.",
            "bogotadistritocapital", "Bogotá D.C.",
            "cartagenadeindias", "Cartagena",
            "sanandresisla", "San Andrés",
            "santiagodecali", "Cali");

    private static final Map<String, String> POR_CLAVE = new LinkedHashMap<>();

    static {
        LISTA.forEach(c -> POR_CLAVE.put(clave(c), c));
        ALIAS.forEach(POR_CLAVE::putIfAbsent);
    }

    private Ciudades() {
    }

    /** La ciudad de la lista que corresponde al texto ("cartagena", "Bogotá" → "Cartagena", "Bogotá D.C."), si hay. */
    public static Optional<String> canonica(String texto) {
        if (texto == null || texto.isBlank()) return Optional.empty();
        return Optional.ofNullable(POR_CLAVE.get(clave(texto)));
    }

    /** Un cliente sin ciudad (o con una que no está en la lista) debe escogerla al iniciar sesión. */
    public static boolean pendiente(Usuario u) {
        return u != null && RolPermisos.CLIENTE.equals(u.getRol()) && canonica(u.getCiudad()).isEmpty();
    }

    private static String clave(String texto) {
        return PatronBusqueda.normalizar(texto).replaceAll("[^a-z0-9]", "");
    }
}
