package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Producto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ImagenIaServiceTest {

    private static Producto producto(String nombre) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setMarca("Bosch");
        p.setCategoria("FRENOS");
        p.setPrecioCompra(50_000.0);
        return p;
    }

    @Test
    void elPromptLlevaLosDatosDeLaPiezaPeroNoLosPrecios() {
        ImagenIaService s = new ImagenIaService(mock(CloudinaryService.class), "sk-test", "modelo", 5);
        String prompt = s.prompt(producto(" Pastillas de freno "));
        assertTrue(prompt.endsWith("{\"nombre\":\"Pastillas de freno\",\"categoria\":\"FRENOS\",\"marca\":\"Bosch\"}"), prompt);
        assertFalse(prompt.contains("50000"));
    }

    @Test
    void sinKeyRespondeNoConfigurado() {
        ImagenIaService s = new ImagenIaService(mock(CloudinaryService.class), "", "modelo", 5);
        var e = assertThrows(ResponseStatusException.class, () -> s.generar(producto("Filtro")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }

    @Test
    void sinNombreNoGastaUnaImagen() {
        ImagenIaService s = new ImagenIaService(mock(CloudinaryService.class), "sk-test", "modelo", 5);
        var e = assertThrows(ResponseStatusException.class, () -> s.generar(producto("  ")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }
}
