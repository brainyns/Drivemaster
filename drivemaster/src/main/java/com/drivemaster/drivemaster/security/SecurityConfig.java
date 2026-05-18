package com.drivemaster.drivemaster.security;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/google").permitAll()
                        .requestMatchers("/api/auth/login-interno").permitAll()
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/usuarios", "/api/usuarios/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/").permitAll()
                        .requestMatchers("/api/productos/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").authenticated()
                         .requestMatchers(HttpMethod.GET, "/api/clientes/mi-perfil").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR", "CLIENTE")
                        .requestMatchers("/api/clientes/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers(HttpMethod.GET, "/api/ventas/mis-ventas").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ventas/compra-inmediata").authenticated()
                        .requestMatchers("/api/ventas/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/compras/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/proveedores/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/movimientos", "/api/movimientos/").permitAll()
                        .requestMatchers("/api/movimientos/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes/mis-solicitudes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/solicitudes/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/perfil/**").hasAnyRole("SUPERADMIN", "ADMIN", "VENDEDOR")
                        .requestMatchers("/api/chat/**").permitAll()
                        .requestMatchers("/api/home/**").authenticated()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174","https://drivemaster-1.onrender.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("X-Session-Expires-In", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}