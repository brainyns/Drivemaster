package com.drivemaster.drivemaster.util;

/**
 * Nombre de un negocio con su tipo delante ("Taller AutoMax"), sin repetirlo si el nombre ya lo trae: "Taller los
 * hermanos" queda igual y no "Taller Taller los hermanos". Igual con "Tienda" y con el plural ("Tiendas LOS RIOS").
 */
public final class NombreNegocio {

    private NombreNegocio() {
    }

    public static String conPrefijo(String prefijo, String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) return prefijo;
        boolean yaLoTrae = java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(prefijo) + "(s|es)?(\\s|$)",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE).matcher(n).find();
        return yaLoTrae ? n : prefijo + " " + n;
    }
}
