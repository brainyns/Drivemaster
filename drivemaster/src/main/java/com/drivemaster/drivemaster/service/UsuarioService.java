package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Usuario;

public interface UsuarioService {

    Usuario crearUsuario(Usuario usuario);


    List<Usuario> listarTodos();

    Usuario obtenerPorCorreo(String correo);

    Usuario obtenerPorId(String id);

    Usuario actualizarUsuario(Usuario usuario);
}
