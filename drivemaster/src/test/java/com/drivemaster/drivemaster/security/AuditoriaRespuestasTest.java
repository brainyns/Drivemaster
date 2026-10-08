package com.drivemaster.drivemaster.security;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.util.CodigoRetiro;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auditoría de TODAS las respuestas de los controladores: recorre el tipo que devuelve cada ruta (y los tipos que
 * contiene) y comprueba, con la misma introspección de Jackson que usa Spring, que ninguna propiedad serializada se
 * llame hash, contraseña o secreto. Si alguien agrega un campo así a una respuesta, esta prueba falla.
 */
class AuditoriaRespuestasTest {

    private static final Pattern PROHIBIDO = Pattern.compile("(?i).*(hash|password|passwd|secret|secreto).*");
    private static final int PROFUNDIDAD = 6;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void ningunaRespuestaDeNingunControladorExponeHashesContrasenasNiSecretos() throws Exception {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> visitados = new HashSet<>();
        List<String> violaciones = new ArrayList<>();
        int rutas = 0;
        for (BeanDefinition bd : escaner.findCandidateComponents("com.drivemaster.drivemaster.controller")) {
            Class<?> controlador = Class.forName(bd.getBeanClassName());
            for (Method m : controlador.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || !tieneRuta(m)) continue;
                rutas++;
                JavaType tipo = mapper.getTypeFactory().constructType(m.getGenericReturnType());
                revisar(tipo, controlador.getSimpleName() + "." + m.getName(), 0, visitados, violaciones);
            }
        }

        assertTrue(rutas > 50, "la auditoría debe cubrir las rutas reales (" + rutas + ")");
        assertTrue(visitados.contains(PedidoTienda.class.getName()), "debe haber revisado los pedidos de tienda: " + visitados);
        assertTrue(violaciones.isEmpty(), "Respuestas con propiedades sensibles:\n" + String.join("\n", violaciones));
    }

    private static boolean tieneRuta(Method m) {
        return java.util.Arrays.stream(m.getAnnotations()).anyMatch(a -> a.annotationType().getSimpleName().endsWith("Mapping"));
    }

    private void revisar(JavaType tipo, String ruta, int nivel, Set<String> visitados, List<String> violaciones) {
        if (tipo == null || nivel > PROFUNDIDAD) return;
        // Genéricos y contenedores: ResponseEntity<List<X>>, Map<String, X>, X[]...
        for (int i = 0; i < tipo.containedTypeCount(); i++) revisar(tipo.containedType(i), ruta, nivel + 1, visitados, violaciones);
        if (tipo.isArrayType() || tipo.isContainerType()) {
            revisar(tipo.getContentType(), ruta, nivel + 1, visitados, violaciones);
            return;
        }
        Class<?> clase = tipo.getRawClass();
        if (clase.isPrimitive() || clase.isEnum() || !clase.getName().startsWith("com.drivemaster")) return;
        if (!visitados.add(clase.getName())) return;

        SerializationConfig cfg = mapper.getSerializationConfig();
        for (BeanPropertyDefinition p : cfg.introspect(tipo).findProperties()) {
            if (!p.couldSerialize()) continue;
            if (PROHIBIDO.matcher(p.getName()).matches()) {
                violaciones.add(ruta + " -> " + clase.getSimpleName() + "." + p.getName());
            }
            revisar(p.getPrimaryType(), ruta, nivel + 1, visitados, violaciones);
        }
    }

    @Test
    void unPedidoConHashNuncaLoSerializa() throws Exception {
        String hash = CodigoRetiro.hash("ped1", "ABCD-1234");
        PedidoTienda p = PedidoTienda.builder().id("ped1").codigoRetiroHash(hash).codigoRetiroIntentos(3)
                .codigoRetiroBloqueadoHasta(java.time.LocalDateTime.now()).build();

        String json = mapper.writeValueAsString(p);

        assertFalse(json.contains(hash), json);
        assertFalse(json.toLowerCase().contains("codigoretirohash"), json);
        assertFalse(json.contains("codigoRetiroIntentos") || json.contains("codigoRetiroBloqueadoHasta"), json);
    }
}
