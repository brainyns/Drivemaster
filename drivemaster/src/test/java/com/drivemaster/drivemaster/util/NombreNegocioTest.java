package com.drivemaster.drivemaster.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** "Taller Taller los hermanos": el tipo no se repite si el nombre ya lo trae. */
class NombreNegocioTest {

    @Test
    void elPrefijoNoSeDuplica() {
        assertEquals("Taller los hermanos", NombreNegocio.conPrefijo("Taller", "Taller los hermanos"));
        assertEquals("taller blanco", NombreNegocio.conPrefijo("Taller", "taller blanco"), "sin importar mayúsculas");
        assertEquals("Taller AutoMax", NombreNegocio.conPrefijo("Taller", "AutoMax"));
        assertEquals("Tienda Repuestos Sur", NombreNegocio.conPrefijo("Tienda", "Repuestos Sur"));
        assertEquals("Tiendas LOS RIOS", NombreNegocio.conPrefijo("Tienda", "Tiendas LOS RIOS"), "también en plural");
        assertEquals("Talleres Unidos", NombreNegocio.conPrefijo("Taller", "Talleres Unidos"));
        assertEquals("Taller Tallerísimo", NombreNegocio.conPrefijo("Taller", "Tallerísimo"), "solo la palabra completa");
        assertEquals("Taller", NombreNegocio.conPrefijo("Taller", null));
    }
}
