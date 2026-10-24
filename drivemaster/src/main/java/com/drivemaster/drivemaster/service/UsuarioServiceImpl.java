package com.drivemaster.drivemaster.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.Correos;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Usuario crearUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @Override
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @Override
    public Usuario obtenerPorCorreo(String correo) {
        return buscarPorCorreo(correo)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    /**
     * Primero tal cual llega (la sesión trae el correo guardado, sea como sea), luego normalizado y por último sin
     * mayúsculas. Las dos primeras usan el índice; la última solo corre si el correo no existe en minúsculas.
     */
    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        if (correo == null || correo.isBlank()) return Optional.empty();
        String tal = correo.trim();
        String normalizado = Correos.normalizar(correo);
        Optional<Usuario> exacto = usuarioRepository.findByCorreo(tal);
        if (exacto.isEmpty() && !normalizado.equals(tal)) exacto = usuarioRepository.findByCorreo(normalizado);
        if (exacto.isPresent()) return exacto;
        List<Usuario> parecidos = usuarioRepository.findByCorreoIgnoreCase(normalizado);
        return parecidos.size() == 1 ? Optional.of(parecidos.get(0)) : Optional.empty();
    }

    @Override
    public boolean correoOcupado(String correo) {
        String normalizado = Correos.normalizar(correo);
        if (normalizado == null || normalizado.isEmpty()) return false;
        return usuarioRepository.existsByCorreo(normalizado) || !usuarioRepository.findByCorreoIgnoreCase(normalizado).isEmpty();
    }

    @Override
    public Usuario obtenerPorId(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    public List<Usuario> obtenerPorIds(java.util.Collection<String> ids) {
        return ids.isEmpty() ? List.of() : usuarioRepository.findAllById(ids);
    }

    @Override
    public Usuario actualizarUsuario(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }
}