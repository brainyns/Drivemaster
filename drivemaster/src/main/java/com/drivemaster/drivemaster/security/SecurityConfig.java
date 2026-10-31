package com.drivemaster.drivemaster.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Orígenes del frontend: los usan el CORS de la API y el WebSocket de notificaciones. */
    public static final List<String> ORIGENES_PERMITIDOS = List.of("http://localhost:5173", "http://localhost:5174",
            "https://drivermaster.onrender.com", "https://drivemaster-orcin.vercel.app");

    /**
     * ORIGENES_PERMITIDOS más la URL pública del frontend (FRONTEND_URL), si está definida.
     * La usan el CORS y el WebSocket de notificaciones (si solo la usara CORS, la campanita no conectaría
     * en el HomeLab), para que cada despliegue (Render, HomeLab) solo cambie su configuración.
     */
    public static List<String> origenesPermitidos(String frontendUrl) {
        List<String> origenes = new ArrayList<>(ORIGENES_PERMITIDOS);
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String url = frontendUrl.strip().replaceAll("/+$", "");
            if (!origenes.contains(url)) {
                origenes.add(url);
            }
        }
        return origenes;
    }

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${security.session.max-inactive-minutes:30}")
    private int maxInactiveMinutes;

    // URL pública del frontend (FRONTEND_URL). Se suma a los orígenes permitidos para que un
    // despliegue propio (p. ej. el HomeLab) funcione sin tocar esta lista.
    @Value("${frontend.url:}")
    private String frontendUrl;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return authBuilder.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // El reenvío interno a /error no lleva el JWT: sin esto un 500
                        // no manejado llegaba al cliente disfrazado de 401/403.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/google").permitAll()
                        .requestMatchers("/api/auth/login-interno").permitAll()
                        // ¿Olvidaste tu contraseña? Sin sesión; limitado por IP y por correo (RecuperacionClaveService)
                        .requestMatchers(HttpMethod.POST, "/api/auth/recuperar/solicitar", "/api/auth/recuperar/confirmar").permitAll()
                        .requestMatchers("/api/auth/logout").authenticated()
                        // Cambio del correo de la cuenta: solo talleres y tiendas, siempre sobre la cuenta de la sesión
                        .requestMatchers("/api/cuenta/correo", "/api/cuenta/correo/**").hasAnyRole("TALLER", "TIENDA")
                        // Panel de plataforma: la gestión de usuarios es solo del SUPERADMIN.
                        // El ADMIN queda con la operación diaria (aprobaciones, servicios y problemas de envío).
                        .requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/").permitAll()
                        // Herramientas del agente IA (n8n): público aquí, pero exige el token de
                        // servicio X-Service-Token en ProductoController.
                        .requestMatchers(HttpMethod.GET, "/api/productos/buscar").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/productos/mi-tienda", "/api/productos/mi-tienda/tabla").hasRole("TIENDA")
                        // Publicar u ocultar: solo la TIENDA dueña (AlcanceTienda verifica que el producto sea suyo)
                        .requestMatchers(HttpMethod.PATCH, "/api/productos/*/publicado").hasRole("TIENDA")
                        // TIENDA solo gestiona sus productos: lo verifica AlcanceTienda en el controlador
                        .requestMatchers("/api/productos/**").hasAnyRole("SUPERADMIN", "VENDEDOR", "TIENDA")
                        // Clientes: la lista, el alta, el detalle, la exportación y el borrado son solo del SUPERADMIN.
                        // Cada usuario edita únicamente su propia cuenta (lo verifica ClienteController).
                        .requestMatchers(HttpMethod.GET, "/api/clientes/mi-perfil").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").authenticated()
                        .requestMatchers("/api/clientes", "/api/clientes/", "/api/clientes/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/ventas/mis-ventas").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ventas/compra-inmediata").authenticated()
                        .requestMatchers("/api/ventas/**").hasAnyRole("SUPERADMIN", "VENDEDOR")
                        // Compras, proveedores e inventario son de cada tienda: solo ese rol, cada una con lo suyo
                        .requestMatchers("/api/compras", "/api/compras/**").hasRole("TIENDA")
                        .requestMatchers("/api/proveedores", "/api/proveedores/**").hasRole("TIENDA")
                        // Reportes financieros y de plataforma: solo SUPERADMIN
                        .requestMatchers("/api/reportes/**").hasRole("SUPERADMIN")
                        // Inventario: antes los movimientos eran públicos y cualquier usuario con
                        // sesión podía ajustar stock. TIENDA queda limitada a su inventario.
                        .requestMatchers("/api/movimientos", "/api/movimientos/**").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes/mis-solicitudes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes").hasAnyRole("SUPERADMIN", "VENDEDOR")
                        .requestMatchers("/api/solicitudes/**").hasAnyRole("SUPERADMIN", "VENDEDOR")
                        .requestMatchers("/api/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/verificar/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/confirmar-redirect").permitAll()
                        .requestMatchers("/api/perfil/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")

                        // Módulo de servicios (Taller)
                        .requestMatchers(HttpMethod.GET, "/api/talleres/activos").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/talleres/registro").permitAll()
                        // Mapa para elegir taller al pedir un diagnóstico (antes de la regla general /api/talleres/**)
                        .requestMatchers(HttpMethod.GET, "/api/talleres/mapa", "/api/talleres/cercanos").hasAnyRole("CLIENTE", "SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/talleres/mi-taller", "/api/talleres/mi-taller/**").hasRole("TALLER")
                        // Logo de cualquier taller: también el ADMIN (el del dueño va por /mi-taller/logo, arriba)
                        .requestMatchers(HttpMethod.POST, "/api/talleres/*/logo").hasAnyRole("SUPERADMIN", "ADMIN")
                        // El ADMIN solo ve la lista de talleres y los aprueba o rechaza; crear, editar y pausar es del SUPERADMIN
                        .requestMatchers(HttpMethod.PATCH, "/api/talleres/*/aprobacion").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/talleres").hasAnyRole("SUPERADMIN", "ADMIN")
                        // Horario y citas de un taller: el dueño (solo el suyo, lo valida HorarioTallerService) y el personal lo consultan
                        .requestMatchers(HttpMethod.GET, "/api/talleres/*/horario", "/api/talleres/*/citas").hasAnyRole("TALLER", "SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/talleres/*/horario").hasRole("TALLER")
                        .requestMatchers("/api/talleres", "/api/talleres/**").hasRole("SUPERADMIN")

                        // Marketplace: solicitudes de diagnóstico (el acceso a cada solicitud lo valida el servicio)
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes-servicio").hasRole("CLIENTE")
                        // Fotos: el cliente al pedir el diagnóstico y el taller al registrarlo
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes-servicio/fotos").hasAnyRole("CLIENTE", "TALLER")
                        .requestMatchers(HttpMethod.PUT, "/api/solicitudes-servicio/*/diagnostico",
                                "/api/solicitudes-servicio/*/repuestos").hasRole("TALLER")
                        .requestMatchers(HttpMethod.GET, "/api/repuestos/buscar").hasRole("TALLER")
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes-servicio/mias").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-servicio/*/cancelar").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes-servicio/taller").hasRole("TALLER")
                        .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-servicio/*/aceptar",
                                "/api/solicitudes-servicio/*/rechazar", "/api/solicitudes-servicio/*/mecanico").hasRole("TALLER")
                        // Etapas finales: solo TALLER (el servicio exige además que sea el taller de la orden)
                        .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-servicio/*/iniciar-instalacion",
                                "/api/solicitudes-servicio/*/en-pruebas", "/api/solicitudes-servicio/*/lista",
                                "/api/solicitudes-servicio/*/entregada").hasRole("TALLER")
                        .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-servicio/*/confirmar-recepcion").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.PATCH, "/api/solicitudes-servicio/*/reasignar").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes-servicio").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes-servicio/*").authenticated()
                        .requestMatchers("/api/solicitudes-servicio/**").denyAll()

                        // Marketplace: cotizaciones. Las públicas validan el token del correo (en el cuerpo)
                        // y tienen límite de intentos por IP.
                        .requestMatchers(HttpMethod.POST, "/api/cotizaciones/publica/consultar",
                                "/api/cotizaciones/publica/responder").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cotizaciones/orden/*/previsualizar",
                                "/api/cotizaciones/orden/*").hasRole("TALLER")
                        .requestMatchers(HttpMethod.GET, "/api/cotizaciones/taller").hasRole("TALLER")
                        .requestMatchers(HttpMethod.POST, "/api/cotizaciones/*/responder").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/cotizaciones/orden/*").authenticated()
                        // PDF: el servicio deja pasar solo al cliente dueño y al taller que la emitió
                        .requestMatchers(HttpMethod.GET, "/api/cotizaciones/*/pdf").hasAnyRole("CLIENTE", "TALLER")
                        .requestMatchers("/api/cotizaciones/**").denyAll()

                        // Catálogo marketplace: la consulta es pública; la compra y sus códigos son del CLIENTE
                        .requestMatchers(HttpMethod.GET, "/api/catalogo/productos", "/api/catalogo/productos/*",
                                "/api/catalogo/filtros").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/catalogo/compras/cotizar", "/api/catalogo/compras",
                                "/api/catalogo/pedidos/*/codigo-retiro", "/api/catalogo/pedidos/*/recibido",
                                "/api/catalogo/pedidos/*/problema").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/catalogo/compras/mias").hasRole("CLIENTE")
                        .requestMatchers("/api/catalogo/**").denyAll()

                        // Marketplace: pagos (links), pedidos a tiendas y liquidaciones
                        .requestMatchers(HttpMethod.POST, "/api/pagos-marketplace/diagnostico/*",
                                "/api/pagos-marketplace/cotizacion/*", "/api/pagos-marketplace/simular/*").hasRole("CLIENTE")
                        .requestMatchers("/api/pagos-marketplace/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/pedidos-tienda/mios", "/api/pedidos-tienda/mios/tabla").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos-tienda/problemas", "/api/pedidos-tienda/retiros-sin-recoger").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/pedidos-tienda/*/preparar", "/api/pedidos-tienda/*/despachar",
                                "/api/pedidos-tienda/*/no-despachar", "/api/pedidos-tienda/*/listo-retiro").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos-tienda/*/retiro/validar").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos-tienda/*/problema/resolver", "/api/pedidos-tienda/*/retiro/resolver").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos-tienda/orden/*/recibidos").hasRole("TALLER")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos-tienda/orden/*").authenticated()
                        .requestMatchers("/api/pedidos-tienda/**").denyAll()
                        // Finanzas: liquidaciones solo SUPERADMIN
                        .requestMatchers(HttpMethod.POST, "/api/liquidaciones/webhook").permitAll()
                        .requestMatchers("/api/liquidaciones/**").hasRole("SUPERADMIN")
                        // Panel de la plataforma: solo SUPERADMIN, salvo el panel operativo mínimo del ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/plataforma/operacion", "/api/plataforma/pedidos",
                                "/api/plataforma/negocios/pendientes").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/plataforma/**").hasRole("SUPERADMIN")

                        // Marketplace: tiendas y suscripciones
                        .requestMatchers(HttpMethod.POST, "/api/tiendas/registro").permitAll()
                        .requestMatchers("/api/tiendas/mi-tienda", "/api/tiendas/mi-tienda/**").hasRole("TIENDA")
                        // Logo de cualquier tienda: también el ADMIN (el de la dueña va por /mi-tienda/logo, arriba)
                        .requestMatchers(HttpMethod.POST, "/api/tiendas/*/logo").hasAnyRole("SUPERADMIN", "ADMIN")
                        // El ADMIN solo aprueba o rechaza (con el plan de prueba); lo demás de tiendas y planes es del SUPERADMIN
                        .requestMatchers(HttpMethod.PATCH, "/api/tiendas/*/aprobacion").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/tiendas", "/api/tiendas/**").hasRole("SUPERADMIN")
                        .requestMatchers("/api/suscripciones", "/api/suscripciones/**").hasRole("SUPERADMIN")

                        // Configuración: se puede leer con sesión o sin ella (ver más abajo), pero escribir
                        // (comisión, tarifas, métodos de pago) es solo del SUPERADMIN
                        .requestMatchers(HttpMethod.POST, "/api/config/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/config/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/config/**").hasRole("SUPERADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/vehiculos/mantenimiento/enviar-recordatorios").hasRole("SUPERADMIN")
            .requestMatchers("/api/vehiculos/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/ordenes-servicio/taller").hasRole("TALLER")
                        // Órdenes de un taller cualquiera: solo administración
                        .requestMatchers(HttpMethod.GET, "/api/ordenes-servicio/taller/*").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/ordenes-servicio/*/confirmar-pago").hasRole("TALLER")
                        .requestMatchers(HttpMethod.PATCH, "/api/ordenes-servicio/*/iniciar").hasRole("TALLER")
                        .requestMatchers(HttpMethod.PATCH, "/api/ordenes-servicio/*/finalizar").hasRole("TALLER")
            .requestMatchers(HttpMethod.PATCH, "/api/ordenes-servicio/*/programar").hasRole("TALLER")
                        .requestMatchers("/api/ordenes-servicio/**").authenticated()

                        .requestMatchers("/api/chat/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/config/parametros/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/home/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::responderNoAutenticado)
                        .accessDeniedHandler(this::responderAccesoDenegado))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }

    /**
     * 401 en JSON. El código permite al frontend distinguir una sesión vencida
     * (intenta renovar el token) de un 401 de negocio, como una contraseña incorrecta.
     */
    private void responderNoAutenticado(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException ex) throws IOException {
        boolean tokenInvalido = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.ATTR_TOKEN_INVALIDO));
        String code = tokenInvalido ? "SESSION_EXPIRED" : "UNAUTHENTICATED";
        String mensaje = tokenInvalido ? "Token inválido o sesión expirada" : "Debe iniciar sesión";
        escribirJson(response, HttpServletResponse.SC_UNAUTHORIZED, mensaje, code);
    }

    private void responderAccesoDenegado(HttpServletRequest request, HttpServletResponse response,
                                         AccessDeniedException ex) throws IOException {
        escribirJson(response, HttpServletResponse.SC_FORBIDDEN, "No tiene permisos para esta acción", "FORBIDDEN");
    }

    private void escribirJson(HttpServletResponse response, int status, String mensaje, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"" + mensaje + "\", \"code\": \"" + code + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origenesPermitidos(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("X-Session-Expires-In", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
