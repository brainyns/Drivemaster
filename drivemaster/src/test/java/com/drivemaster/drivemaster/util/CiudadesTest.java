package com.drivemaster.drivemaster.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.drivemaster.drivemaster.model.Usuario;

class CiudadesTest {

    @Test
    void lasFormasQueYaHayEnLosDatosLlevanASuCiudadDeLaLista() {
        assertEquals(Optional.of("Cartagena"), Ciudades.canonica("cartagena"));
        assertEquals(Optional.of("Cartagena"), Ciudades.canonica(" Cartagena de Indias "));
        assertEquals(Optional.of("Bogotá D.C."), Ciudades.canonica("Bogotá"));
        assertEquals(Optional.of("Bogotá D.C."), Ciudades.canonica("BOGOTA D.C."));
        assertEquals(Optional.of("Turbaco"), Ciudades.canonica("turbaco"));
        assertEquals(Optional.of("Medellín"), Ciudades.canonica("medellin"));
    }

    @Test
    void loQueNoEstaEnLaListaNoSeAcepta() {
        assertEquals(Optional.empty(), Ciudades.canonica("bucarambag"));
        assertEquals(Optional.empty(), Ciudades.canonica(""));
        assertEquals(Optional.empty(), Ciudades.canonica(null));
    }

    @Test
    void laListaNoTieneRepetidos() {
        assertEquals(Ciudades.LISTA.size(), new HashSet<>(Ciudades.LISTA).size());
        Ciudades.LISTA.forEach(c -> assertEquals(Optional.of(c), Ciudades.canonica(c), c));
    }

    @Test
    void soloAlClienteSinCiudadValidaSeLePide() {
        assertTrue(Ciudades.pendiente(Usuario.builder().rol("CLIENTE").build()));
        assertTrue(Ciudades.pendiente(Usuario.builder().rol("CLIENTE").ciudad("bucarambag").build()));
        assertFalse(Ciudades.pendiente(Usuario.builder().rol("CLIENTE").ciudad("cartagena").build()));
        assertFalse(Ciudades.pendiente(Usuario.builder().rol("TIENDA").build()));
        assertFalse(Ciudades.pendiente(Usuario.builder().rol("SUPERADMIN").build()));
        assertFalse(Ciudades.pendiente(null));
    }
}
