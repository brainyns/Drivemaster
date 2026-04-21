package com.drivemaster.drivemaster.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.service.UsuarioService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioService usuarioService;

    public CustomUserDetailsService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioService.obtenerPorCorreo(correo);

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol()));

        if (usuario.getPermisos() != null) {
            for (String permiso : usuario.getPermisos()) {
                authorities.add(new SimpleGrantedAuthority(permiso));
            }
        }

        return User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getPassword())
                .authorities(authorities)
                .disabled(usuario.getActivo() != null && !usuario.getActivo())
                .build();
    }
}
