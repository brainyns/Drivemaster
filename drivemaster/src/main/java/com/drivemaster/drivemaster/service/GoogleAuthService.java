package com.drivemaster.drivemaster.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Service
public class GoogleAuthService {

    private final UsuarioRepository usuarioRepository;

    @Value("${google.client-id}")
    private String googleClientId;

    public GoogleAuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario autenticarConGoogle(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new RuntimeException("Token de Google inválido");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String nombre = (String) payload.get("name");
            String googleId = payload.getSubject();

            Optional<Usuario> existente = usuarioRepository.findByCorreo(email);
            if (existente.isPresent()) {
                Usuario usuario = existente.get();
                if (usuario.getGoogleId() == null) {
                    usuario.setGoogleId(googleId);
                    usuario.setProveedor("GOOGLE");
                }
                usuario.setUltimoLogin(Instant.now());
                return usuarioRepository.save(usuario);
            }

            Usuario nuevo = Usuario.builder()
                    .nombre(nombre)
                    .correo(email)
                    .googleId(googleId)
                    .proveedor("GOOGLE")
                    .rol("CLIENTE")
                    .permisos(List.of("ROLE_CLIENTE", "PRODUCTOS_READ"))
                    .activo(true)
                    .datosCompletos(false)
                    .fechaCreacion(Instant.now())
                    .ultimoLogin(Instant.now())
                    .intentosFallidos(0)
                    .bloqueado(false)
                    .build();
            return usuarioRepository.save(nuevo);

        } catch (Exception e) {
            throw new RuntimeException("Error al verificar token de Google: " + e.getMessage());
        }
    }
}
