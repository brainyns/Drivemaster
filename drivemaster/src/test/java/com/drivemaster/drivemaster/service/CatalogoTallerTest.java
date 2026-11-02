package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Solo un taller aprobado y activo ve el precio con descuento en el catálogo. */
class CatalogoTallerTest {

    private CatalogoService catalogo(String rol, String aprobacion, boolean activo) {
        UsuarioService usuarios = mock(UsuarioService.class);
        TallerService talleres = mock(TallerService.class);
        when(usuarios.obtenerPorCorreo("x@demo.com")).thenReturn(Usuario.builder().id("u").rol(rol).build());
        when(talleres.obtenerPorUsuario("x@demo.com")).thenReturn(Taller.builder().id("t").estadoAprobacion(aprobacion).activo(activo).build());
        CatalogoService c = new CatalogoService(null, null, null);
        c.setCuentas(usuarios, talleres);
        return c;
    }

    @Test
    void solo_el_taller_aprobado_y_activo() {
        assertTrue(catalogo("TALLER", "APROBADO", true).esTallerAprobado("x@demo.com"));
        assertFalse(catalogo("TALLER", "PENDIENTE", true).esTallerAprobado("x@demo.com"));
        assertFalse(catalogo("TALLER", "APROBADO", false).esTallerAprobado("x@demo.com"));
        assertFalse(catalogo("CLIENTE", "APROBADO", true).esTallerAprobado("x@demo.com"));
        assertFalse(catalogo("TALLER", "APROBADO", true).esTallerAprobado(null), "Sin sesión, precio público");
    }
}
