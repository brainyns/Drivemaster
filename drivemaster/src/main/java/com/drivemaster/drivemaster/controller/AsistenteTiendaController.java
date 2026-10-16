package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.security.AlcanceTienda;
import com.drivemaster.drivemaster.security.ChatRateLimiter;
import com.drivemaster.drivemaster.service.AsistenteTiendaIA;
import com.drivemaster.drivemaster.service.AsistenteTiendaIA.Resultado;
import com.drivemaster.drivemaster.service.ContextoAsistenteTienda;
import com.drivemaster.drivemaster.service.ResumenSemanalTienda;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IA de la TIENDA (solo ese rol: lo define SecurityConfig). Solo lee y sugiere: nada cambia en la tienda. La tienda
 * sale de la sesión y sus datos viajan a n8n como contexto; allí el modelo no tiene herramientas.
 */
@RestController
@RequestMapping("/api/tiendas/mi-tienda/asistente")
public class AsistenteTiendaController {

    private static final String MSG_ERROR =
            "El resumen no está disponible en este momento. Tus informes siguen abajo.";
    private static final String MSG_CHAT_ERROR =
            "El asistente no está disponible en este momento. Intenta de nuevo en unos segundos.";
    private static final String MSG_SIN_SUGERENCIAS =
            "La IA no devolvió sugerencias. Intenta con un nombre más completo.";
    private static final String PEDIDO_RESUMEN =
            "Hazme el resumen de hoy de mi tienda en máximo 5 líneas con guion: cómo van las ventas frente al mes anterior, "
                    + "lo más urgente por despachar, qué reponer primero, cuánto dinero tengo por liquidar y una oportunidad "
                    + "(producto sin movimiento o margen bajo). Sin saludo.";
    private static final int MAX_MENSAJE = 500;
    private static final int MAX_HISTORIAL = 6;
    private static final int MAX_TEXTO_HISTORIAL = 2_000;
    private static final int MAX_COMPATIBILIDADES = 8;

    public record Respuesta(String respuesta) {}
    public record ResumenSemanal(boolean activo) {}

    public record Turno(String rol, String texto) {}
    public record Pregunta(String mensaje, List<Turno> historial) {}
    /** "Detrás de escena": qué pasó en el sistema para responder. */
    public record Detras(List<String> seguridad, String modelo, Integer tokensEntrada, Integer tokensSalida, long ms,
                         String herramienta, List<String> datos) {}
    public record RespuestaChat(String respuesta, boolean error, Detras detras) {}

    public record DatosProducto(String nombre, String marca, String referencia, List<String> categorias) {}
    public record Compatibilidad(String marca, String modelo, int anoDesde, int anoHasta, String motor) {}
    public record Sugerencia(String categoria, List<Compatibilidad> compatibilidades, String nota) {}

    private final AlcanceTienda alcanceTienda;
    private final AsistenteTiendaIA ia;
    private final ResumenSemanalTienda resumenSemanal;
    private final TiendaRepository tiendas;
    private final ChatRateLimiter limitador;
    private final ObjectMapper objectMapper;

    // Un resumen por tienda y día: se genera al abrir Informes y no debe pagar el modelo en cada visita.
    private final Cache<String, String> resumenes = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .maximumSize(10_000)
            .build();

    // Correo de prueba: uno cada 10 minutos por tienda, para no usarlo como envío masivo.
    private final Cache<String, Boolean> pruebas = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    public AsistenteTiendaController(AlcanceTienda alcanceTienda, AsistenteTiendaIA ia, ResumenSemanalTienda resumenSemanal,
                                     TiendaRepository tiendas, ChatRateLimiter limitador, ObjectMapper objectMapper) {
        this.alcanceTienda = alcanceTienda;
        this.ia = ia;
        this.resumenSemanal = resumenSemanal;
        this.tiendas = tiendas;
        this.limitador = limitador;
        this.objectMapper = objectMapper;
    }

    // ─── Página "Asistente de tu tienda" ─────────────────────────────────────

    @PostMapping
    public RespuestaChat preguntar(@RequestBody Pregunta pregunta, Authentication auth) {
        if (pregunta == null || pregunta.mensaje() == null || pregunta.mensaje().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe una pregunta sobre tu tienda.");
        }
        if (pregunta.mensaje().length() > MAX_MENSAJE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tu pregunta es muy larga. Escríbela en máximo " + MAX_MENSAJE + " caracteres.");
        }
        Tienda tienda = alcanceTienda.tienda(auth);
        // Mismo cupo que el chat público (el global acota el gasto diario), contado por tienda y no por IP.
        limitador.consumir("tienda:" + tienda.getId());
        Resultado r = ia.conversar(tienda, pregunta.mensaje(), historialSeguro(pregunta.historial()));
        Detras detras = new Detras(List.of(
                "Tienda tomada de tu sesión: " + tienda.getNombre() + ". Lo que escribas no puede cambiarla.",
                "El modelo solo recibió datos de esta tienda, sin nombres, teléfonos ni direcciones de clientes.",
                "Solo lectura: no puede comprar, ajustar stock, cambiar precios ni despachar.",
                "Tu conversación se recortó a los últimos " + MAX_HISTORIAL + " mensajes antes de enviarse."),
                r.modelo(), r.tokensEntrada(), r.tokensSalida(), r.ms(),
                "Lectura de los datos de tu tienda, calculados por DriveMaster antes de preguntar al modelo", r.datos());
        return new RespuestaChat(r.texto() == null ? MSG_CHAT_ERROR : r.texto(), r.texto() == null, detras);
    }

    /** El historial lo manda el navegador: solo los últimos turnos, con rol conocido y texto acotado. */
    static List<Map<String, String>> historialSeguro(List<Turno> historial) {
        if (historial == null) return List.of();
        List<Map<String, String>> limpio = historial.stream()
                .filter(t -> t != null && t.texto() != null && !t.texto().isBlank()
                        && ("usuario".equals(t.rol()) || "asistente".equals(t.rol())))
                .map(t -> Map.of(
                        "role", "usuario".equals(t.rol()) ? "user" : "assistant",
                        "content", recortar(t.texto(), MAX_TEXTO_HISTORIAL)))
                .collect(Collectors.toList());
        return limpio.subList(Math.max(0, limpio.size() - MAX_HISTORIAL), limpio.size());
    }

    // ─── Resumen de hoy (Informes) ───────────────────────────────────────────

    @GetMapping("/resumen")
    public ResponseEntity<Respuesta> resumen(Authentication auth) {
        Tienda tienda = alcanceTienda.tienda(auth);
        String clave = tienda.getId() + "|" + LocalDate.now(ContextoAsistenteTienda.ZONA);
        String guardado = resumenes.getIfPresent(clave);
        if (guardado != null) return ResponseEntity.ok(new Respuesta(guardado));

        limitador.consumir("tienda:" + tienda.getId());
        String r = ia.pedir(tienda, PEDIDO_RESUMEN);
        if (r == null) return ResponseEntity.ok(new Respuesta(MSG_ERROR));
        resumenes.put(clave, r);   // los errores no se guardan: el siguiente intento vuelve a probar
        return ResponseEntity.ok(new Respuesta(r));
    }

    // ─── Sugerencias en el formulario de producto ────────────────────────────

    @PostMapping("/sugerir-producto")
    public Sugerencia sugerirProducto(@RequestBody DatosProducto datos, Authentication auth) {
        if (datos == null || datos.nombre() == null || datos.nombre().strip().length() < 3 || datos.nombre().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe el nombre del producto (entre 3 y 150 caracteres).");
        }
        Tienda tienda = alcanceTienda.tienda(auth);
        limitador.consumir("tienda:" + tienda.getId());
        List<String> categorias = datos.categorias() == null ? List.of() : datos.categorias().stream()
                .filter(c -> c != null && !c.isBlank() && c.length() <= 60).limit(40).toList();
        String producto = "Nombre: " + datos.nombre().strip()
                + "\nMarca del repuesto: " + recortar(datos.marca(), 60)
                + "\nReferencia del fabricante: " + recortar(datos.referencia(), 60);
        Resultado r = ia.sugerirProducto(producto, categorias);
        if (r.texto() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La IA no está disponible en este momento. Intenta de nuevo.");
        }
        return leerSugerencia(r.texto(), categorias, objectMapper);
    }

    /**
     * Lo que devuelve el modelo no es de fiar: la categoría solo vale si está en la lista del formulario y cada
     * compatibilidad necesita marca, modelo y años razonables. Si el JSON no se puede leer, no hay sugerencias.
     */
    static Sugerencia leerSugerencia(String texto, List<String> categorias, ObjectMapper om) {
        int desde = texto.indexOf('{');
        int hasta = texto.lastIndexOf('}');
        JsonNode j = null;
        if (desde >= 0 && hasta > desde) {
            try {
                j = om.readTree(texto.substring(desde, hasta + 1));
            } catch (Exception ignorado) {
                // JSON roto: se trata como "sin sugerencias"
            }
        }
        if (j == null || !j.isObject()) return new Sugerencia(null, List.of(), MSG_SIN_SUGERENCIAS);

        String pedida = j.path("categoria").asText("").strip();
        String categoria = categorias.stream().filter(c -> c.equalsIgnoreCase(pedida)).findFirst().orElse(null);

        int maxAno = LocalDate.now(ContextoAsistenteTienda.ZONA).getYear() + 2;
        List<Compatibilidad> compat = new ArrayList<>();
        for (JsonNode c : j.path("compatibilidades")) {
            String marca = recortar(c.path("marca").asText(""), 40);
            String modelo = recortar(c.path("modelo").asText(""), 40);
            int a = c.path("anoDesde").asInt(0);
            int b = c.path("anoHasta").asInt(0);
            if (marca.isEmpty() || modelo.isEmpty() || a < 1950 || b > maxAno || a > b) continue;
            compat.add(new Compatibilidad(marca, modelo, a, b, recortar(c.path("motor").asText(""), 20)));
            if (compat.size() >= MAX_COMPATIBILIDADES) break;
        }
        return new Sugerencia(categoria, compat, recortar(j.path("nota").asText(""), 300));
    }

    private static String recortar(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ─── Resumen semanal por correo ──────────────────────────────────────────

    @GetMapping("/resumen-semanal")
    public ResumenSemanal resumenSemanal(Authentication auth) {
        return new ResumenSemanal(Boolean.TRUE.equals(alcanceTienda.tienda(auth).getResumenSemanal()));
    }

    @PutMapping("/resumen-semanal")
    public ResumenSemanal cambiarResumenSemanal(@RequestBody ResumenSemanal cuerpo, Authentication auth) {
        Tienda tienda = alcanceTienda.tienda(auth);
        tienda.setResumenSemanal(cuerpo != null && cuerpo.activo());
        tiendas.save(tienda);
        return new ResumenSemanal(Boolean.TRUE.equals(tienda.getResumenSemanal()));
    }

    /** Envía ya, al correo del dueño, el resumen de la semana pasada: para ver cómo llega sin esperar al lunes. */
    @PostMapping("/resumen-semanal/prueba")
    public Map<String, String> enviarPrueba(Authentication auth) {
        Tienda tienda = alcanceTienda.tienda(auth);
        if (pruebas.asMap().putIfAbsent(tienda.getId(), true) != null) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Ya enviamos una prueba hace poco. Espera unos minutos.");
        }
        limitador.consumir("tienda:" + tienda.getId());
        if (!resumenSemanal.enviar(tienda, ResumenSemanalTienda.lunesSemanaPasada(LocalDate.now(ContextoAsistenteTienda.ZONA)))) {
            pruebas.invalidate(tienda.getId());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tu usuario no tiene un correo registrado.");
        }
        return Map.of("mensaje", "Te enviamos el resumen de la semana pasada. Revisa tu correo (y la carpeta de spam).");
    }
}
