package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Tienda;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** El logo pasa por las reglas de la foto del vehículo (imagen, 5 MB) y se guarda en la tienda que corresponde. */
class LogoTiendaServiceTest {

    private TiendaService tiendas;
    private CloudinaryService cloudinary;
    private MongoTemplate mongo;
    private LogoTiendaService servicio;

    @BeforeEach
    void setUp() throws IOException {
        tiendas = mock(TiendaService.class);
        cloudinary = mock(CloudinaryService.class);
        mongo = mock(MongoTemplate.class);
        servicio = new LogoTiendaService(tiendas, cloudinary, mongo);
        when(cloudinary.subirImagen(any())).thenReturn("https://img/logo.png");
        when(tiendas.obtenerPorUsuario("dueno@demo.com")).thenReturn(Tienda.builder().id("A").build());
        when(tiendas.obtenerEntidad("B")).thenReturn(Tienda.builder().id("B").build());
    }

    private static MockMultipartFile archivo(String tipo, int bytes) {
        return new MockMultipartFile("archivo", "logo", tipo, new byte[bytes]);
    }

    private String idGuardado() {
        ArgumentCaptor<Query> donde = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> cambio = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(donde.capture(), cambio.capture(), eq(Tienda.class));
        assertEquals("https://img/logo.png", ((Document) cambio.getValue().getUpdateObject().get("$set")).get("logoUrl"));
        return donde.getValue().getQueryObject().getString("_id");
    }

    @Test
    void laDuenaGuardaElLogoEnSuPropiaTienda() {
        assertEquals("https://img/logo.png", servicio.subirMio("dueno@demo.com", archivo("image/png", 10)));
        assertEquals("A", idGuardado());
    }

    @Test
    void elAdministradorLoGuardaEnLaTiendaQueIndica() {
        servicio.subirPorAdmin("B", archivo("image/webp", 10));
        assertEquals("B", idGuardado());
    }

    @Test
    void soloImagenesComunesDeHastaCincoMegas() {
        for (MockMultipartFile malo : new MockMultipartFile[]{
                archivo("image/png", 0), archivo("application/pdf", 10), archivo("image/svg+xml", 10),
                archivo(null, 10), archivo("image/jpeg", 5 * 1024 * 1024 + 1)}) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class, () -> servicio.subirMio("dueno@demo.com", malo)).getStatusCode());
        }
        verifyNoInteractions(cloudinary, mongo);
        servicio.subirMio("dueno@demo.com", archivo("image/jpeg", 5 * 1024 * 1024));
    }

    @Test
    void unaCuentaSinTiendaNoSubeNada() {
        when(tiendas.obtenerPorUsuario("cliente@demo.com"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Este usuario no tiene una tienda asociada"));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> servicio.subirMio("cliente@demo.com", archivo("image/png", 10))).getStatusCode());
        verifyNoInteractions(cloudinary, mongo);
    }
}
