package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Usuario;

public interface UsuarioService {

    Usuario crearUsuario(Usuario usuario);


    List<Usuario> listarTodos();

    Usuario obtenerPorCorreo(String correo);

    /**
     * Busca la cuenta de un correo sin importar espacios ni mayúsculas (las cuentas antiguas pueden tenerlas). Vacío
     * si no existe o si hay varias que solo se diferencian en mayúsculas.
     */
    java.util.Optional<Usuario> buscarPorCorreo(String correo);

    /** true si el correo ya es de alguna cuenta, comparando sin mayúsculas ni espacios. */
    boolean correoOcupado(String correo);

    Usuario obtenerPorId(String id);

    /** Varios usuarios en una sola consulta; los que no existen simplemente no vienen. */
    java.util.List<Usuario> obtenerPorIds(java.util.Collection<String> ids);

    Usuario actualizarUsuario(Usuario usuario);
}
