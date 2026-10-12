package com.drivemaster.drivemaster.util;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convierte un término que escribió una persona (a través del agente del chat) en una expresión
 * regular tolerante para Mongo: da igual si lleva tildes, si está en plural o si usa otro nombre
 * del mismo repuesto. "rodamientos" encuentra la "Balinera delantera Ford Escape" y "bateria" la
 * "Batería 12V".
 */
public final class PatronBusqueda {

    /**
     * Nombres del mismo repuesto que se usan en Colombia. Sin tildes y en singular: se comparan
     * contra la raíz del término. Cada grupo cuenta como un solo término para la relevancia.
     */
    private static final List<Set<String>> SINONIMOS = List.of(
            Set.of("rodamiento", "balinera", "cojinete", "ruliman", "rodaje"),
            Set.of("bateria", "acumulador"),
            Set.of("correa", "banda"),
            Set.of("llanta", "neumatico"),
            Set.of("aceite", "lubricante"),
            Set.of("bombillo", "bombilla", "foco"),
            Set.of("pastilla", "balata"),
            Set.of("exploradora", "antiniebla"));

    private PatronBusqueda() {
    }

    /** Minúsculas y sin tildes: "Batería" → "bateria". La ñ se conserva como n. */
    public static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /**
     * Quita el plural para buscar por la raíz: "rodamientos" → "rodamiento", "motores" → "motor",
     * "aceites" → "aceit". Como la búsqueda es "contiene", una raíz más corta nunca pierde resultados.
     */
    static String raiz(String palabra) {
        int n = palabra.length();
        if (n > 4 && palabra.endsWith("es") && !esVocal(palabra.charAt(n - 3)) && n - 2 >= 3) {
            return palabra.substring(0, n - 2);
        }
        if (n > 4 && palabra.endsWith("s")) {
            return palabra.substring(0, n - 1);
        }
        return palabra;
    }

    /** Expresión regular para Mongo (se usa con la opción 'i'). */
    public static String aRegex(String termino) {
        String base = raiz(normalizar(termino));
        Set<String> variantes = new LinkedHashSet<>();
        variantes.add(base);
        for (Set<String> grupo : SINONIMOS) {
            Set<String> raices = grupo.stream().map(PatronBusqueda::raiz).collect(Collectors.toSet());
            // "aceites" queda en "aceit": también cuenta como "aceite" (mínimo 4 letras para no
            // disparar sinónimos con términos sueltos como "ba").
            boolean esDelGrupo = raices.contains(base)
                    || (base.length() >= 4 && grupo.stream().anyMatch(s -> s.startsWith(base)));
            if (esDelGrupo) {
                variantes.addAll(raices);
            }
        }
        return variantes.stream().map(PatronBusqueda::tolerarTildes).collect(Collectors.joining("|"));
    }

    /** "bateria" → "b[aáÁ]t[eéÉ]r[iíÍ][aáÁ]": coincide con "Batería" y con "bateria". El resto se escapa. */
    static String tolerarTildes(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case 'a' -> sb.append("[aáÁ]");
                case 'e' -> sb.append("[eéÉ]");
                case 'i' -> sb.append("[iíÍ]");
                case 'o' -> sb.append("[oóÓ]");
                case 'u' -> sb.append("[uúüÚÜ]");
                case 'n' -> sb.append("[nñÑ]");
                default -> {
                    if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) sb.append('\\');
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static boolean esVocal(char c) {
        return "aeiou".indexOf(c) >= 0;
    }
}
