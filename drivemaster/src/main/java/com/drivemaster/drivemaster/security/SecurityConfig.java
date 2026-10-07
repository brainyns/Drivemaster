package com.drivemaster.drivemaster.security;

import java.io.IOException;
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

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${security.session.max-inactive-minutes:30}")
    private int maxInactiveMinutes;

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
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/usuarios", "/api/usuarios/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/productos/mi-tienda").hasRole("TIENDA")
                        // TIENDA solo gestiona sus productos: lo verifica AlcanceTienda en el controlador
                        .requestMatchers("/api/productos/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR", "TIENDA")
                        .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").authenticated()
                         .requestMatchers(HttpMethod.GET, "/api/clientes/mi-perfil").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE")
                        .requestMatchers("/api/clientes/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.GET, "/api/ventas/mis-ventas").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ventas/compra-inmediata").authenticated()
                        .requestMatchers("/api/ventas/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/compras/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/proveedores/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/reportes/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        // Inventario: antes los movimientos eran públicos y cualquier usuario con
                        // sesión podía ajustar stock. TIENDA queda limitada a su inventario.
                        .requestMatchers("/api/movimientos", "/api/movimientos/**").hasAnyRole("SUPERADMIN", "ADMIN", "TIENDA")
                        .requestMatchers("/inventario/**").hasAnyRole("SUPERADMIN", "ADMIN", "TIENDA")
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes/mis-solicitudes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/solicitudes/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/pagos/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/verificar/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/confirmar-redirect").permitAll()
                        .requestMatchers("/api/perfil/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")

                        // Módulo de servicios (Taller)
                        .requestMatchers(HttpMethod.GET, "/api/talleres/activos").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/talleres/registro").permitAll()
                        .requestMatchers("/api/talleres/mi-taller", "/api/talleres/mi-taller/**").hasRole("TALLER")
                        .requestMatchers("/api/talleres/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/talleres").hasAnyRole("SUPERADMIN", "ADMIN")

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
                        .requestMatchers(HttpMethod.POST, "/api/cotizaciones/*/responder").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, "/api/cotizaciones/orden/*").authenticated()
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
                        .requestMatchers(HttpMethod.GET, "/api/pedidos-tienda/mios").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos-tienda/problemas").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/pedidos-tienda/*/preparar", "/api/pedidos-tienda/*/despachar",
                                "/api/pedidos-tienda/*/no-despachar", "/api/pedidos-tienda/*/listo-retiro").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos-tienda/*/retiro/validar").hasRole("TIENDA")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos-tienda/*/problema/resolver").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos-tienda/orden/*/recibidos").hasRole("TALLER")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos-tienda/orden/*").authenticated()
                        .requestMatchers("/api/pedidos-tienda/**").denyAll()
                        .requestMatchers("/api/liquidaciones/**").hasAnyRole("SUPERADMIN", "ADMIN")

                        // Marketplace: tiendas y suscripciones
                        .requestMatchers(HttpMethod.POST, "/api/tiendas/registro").permitAll()
                        .requestMatchers("/api/tiendas/mi-tienda").hasRole("TIENDA")
                        .requestMatchers("/api/tiendas", "/api/tiendas/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/suscripciones", "/api/suscripciones/**").hasAnyRole("SUPERADMIN", "ADMIN")

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
        configuration.setAllowedOriginPatterns(List.of("http://localhost:5173", "http://localhost:5174", "https://drivermaster.onrender.com", "https://drivemaster-orcin.vercel.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("X-Session-Expires-In", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}