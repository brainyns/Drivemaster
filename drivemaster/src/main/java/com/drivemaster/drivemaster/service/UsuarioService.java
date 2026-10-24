package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Usuario;

public interface UsuarioService {

    Usuario crearUsuario(Usuario usuario);


    List<Usuario> listarTodos();

    Usuario obtenerPorCorreo(String correo);

    Usuario obtenerPorId(String id);

    /** Varios usuarios en una sola consulta; los que no existen simplemente no vienen. */
    java.util.List<Usuario> obtenerPorIds(java.util.Collection<String> ids);

    Usuario actualizarUsuario(Usuario usuario);
}
