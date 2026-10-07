package com.drivemaster.drivemaster.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Marca la petición cuando trajo un token que no se pudo validar (vencido, alterado...). */
    public static final String ATTR_TOKEN_INVALIDO = "drivemaster.tokenInvalido";

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Value("${security.session.max-inactive-minutes:30}")
    private int maxInactiveMinutes;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String jwt = null;

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }

        if (jwt == null) {
            jwt = getTokenFromCookie(request);
        }

        if (jwt != null) {
            try {
                autenticar(jwt, request, response);
            } catch (Exception ex) {
                // Un token vencido o inválido no corta la petición: se sigue sin
                // autenticar y la autorización decide. Las rutas públicas (catálogo,
                // webhook, redirect de Wompi) funcionan aunque el navegador guarde un
                // token viejo; las protegidas responden 401 desde el entry point.
                SecurityContextHolder.clearContext();
                request.setAttribute(ATTR_TOKEN_INVALIDO, Boolean.TRUE);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(String jwt, HttpServletRequest request, HttpServletResponse response) {
        // El refresh token solo es válido en /api/auth/refresh, nunca como token de acceso.
        if (jwtUtil.isRefreshToken(jwt)) {
            throw new IllegalArgumentException("Refresh token usado como token de acceso");
        }

        String correo = jwtUtil.extractUsername(jwt);

        if (correo == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(correo);

        if (!userDetails.isEnabled() || !jwtUtil.validateToken(jwt, userDetails)) {
            throw new IllegalArgumentException("Token no válido para el usuario");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());

        authToken.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request));

        SecurityContextHolder
                .getContext()
                .setAuthentication(authToken);

        response.setHeader("X-Session-Expires-In", String.valueOf(maxInactiveMinutes * 60));
    }

    private String getTokenFromCookie(HttpServletRequest request) {

        if (request.getCookies() == null)
            return null;

        for (Cookie cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}