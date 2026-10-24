package com.drivemaster.drivemaster.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${security.jwt.refresh-expiration-ms:86400000}")
    private long refreshExpirationMs;

    // Claim que distingue el token de acceso del de refresco. Los tokens emitidos
    // antes de este cambio no lo traen y se tratan como de acceso.
    private static final String CLAIM_TIPO = "typ";
    private static final String TIPO_ACCESO = "access";
    private static final String TIPO_REFRESCO = "refresh";

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(UserDetails userDetails) {
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("roles", roles)
                .claim(CLAIM_TIPO, TIPO_ACCESO)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /** Token de larga duración que solo sirve para pedir un nuevo token de acceso. */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim(CLAIM_TIPO, TIPO_REFRESCO)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isRefreshToken(String token) {
        return TIPO_REFRESCO.equals(extractClaim(token, claims -> claims.get(CLAIM_TIPO, String.class)));
    }

    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> {
            Object roles = claims.get("roles");
            if (roles instanceof List<?> lista) {
                return lista.stream().map(String::valueOf).collect(Collectors.toList());
            }
            return java.util.Collections.emptyList();
        });
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        boolean cerrada = userDetails instanceof DetallesUsuario d && emitidoAntesDe(token, d.getSesionesDesde());
        return extractUsername(token).equals(userDetails.getUsername()) && !isTokenExpired(token) && !cerrada;
    }

    /** true si el token se emitió antes de desde (sesiones cerradas en bloque). Sin desde, ninguno lo está. */
    public boolean emitidoAntesDe(String token, java.time.Instant desde) {
        if (desde == null) return false;
        Date emitido = extractClaim(token, Claims::getIssuedAt);
        return emitido == null || emitido.toInstant().isBefore(desde);
    }

    private Key getSigningKey() {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
    }
}
