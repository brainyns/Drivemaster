package com.drivemaster.drivemaster.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TiendaDatosCompletosTest {

    private static Tienda.TiendaBuilder completa() {
        return Tienda.builder().direccion("Cra 7 # 45-10").ciudad("Bogotá").horario("L-S 8am-6pm");
    }

    @Test
    void conDireccionCiudadYHorarioEstaCompleta() {
        assertTrue(completa().build().tieneDatosCompletos());
    }

    @Test
    void siFaltaCualquieraDeLosTresMarcaCompletarDatos() {
        assertFalse(completa().direccion(null).build().tieneDatosCompletos());
        assertFalse(completa().direccion("  ").build().tieneDatosCompletos());
        assertFalse(completa().ciudad("").build().tieneDatosCompletos());
        assertFalse(completa().horario(null).build().tieneDatosCompletos());
    }

    @Test
    void unaTiendaAnteriorALa4ASinHorarioQuedaPorCompletar() {
        // Tiendas ya registradas: tienen dirección y ciudad pero no horario (el campo no existía)
        assertFalse(Tienda.builder().direccion("Cll 1").ciudad("Cali").build().tieneDatosCompletos());
    }
}
