package com.drivemaster.drivemaster.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.dto.CrearSolicitudRequest;
import com.drivemaster.drivemaster.service.SolicitudService;

/**
 * La compra directa de la antigua tienda DriveMaster (POST /api/solicitudes) está cerrada por la bandera
 * compra-legado.habilitada (false por defecto): responde 410 sin crear nada ni tocar el stock.
 */
class SolicitudControllerTest {

    @Test
    void conLaBanderaApagadaLaCompraDirectaResponde410SinCrearNada() {
        SolicitudService servicio = mock(SolicitudService.class);
        SolicitudController controller = new SolicitudController(servicio); // la bandera queda en false, como por defecto

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.crear(new CrearSolicitudRequest(), mock(Authentication.class)));

        assertEquals(HttpStatus.GONE, e.getStatusCode());
        assertEquals("Las compras se hacen desde el catálogo: agrega los repuestos al carrito", e.getReason());
        verifyNoInteractions(servicio);
    }
}
