package com.drivemaster.drivemaster.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.Usuario;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByCorreo(String correo);
    // Sin distinguir mayúsculas (regex ^…$ con el valor escapado): para las cuentas antiguas guardadas con mayúsculas
    java.util.List<Usuario> findByCorreoIgnoreCase(String correo);
    Optional<Usuario> findByGoogleId(String googleId);
    boolean existsByCorreo(String correo);
    java.util.List<Usuario> findByRol(String rol);
}
