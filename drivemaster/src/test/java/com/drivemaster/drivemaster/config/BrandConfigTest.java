package com.drivemaster.drivemaster.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Logo recortado: solo cuenta como publicado si la URL entrega una imagen. */
class BrandConfigTest {

    private HttpServer servidor;
    private String base;

    @BeforeEach
    void levantar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        responder("/logo.png", 200, "image/png");
        // Lo que hace Vercel con una ruta que no existe: devuelve index.html con 200
        responder("/sin-desplegar.png", 200, "text/html; charset=utf-8");
        responder("/borrado.png", 404, "image/png");
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private void responder(String ruta, int estado, String tipo) {
        servidor.createContext(ruta, intercambio -> {
            intercambio.getResponseHeaders().set("Content-Type", tipo);
            intercambio.sendResponseHeaders(estado, -1);
            intercambio.close();
        });
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    @Test
    void unaImagenCuentaComoPublicada() {
        assertTrue(BrandConfig.publicado(base + "/logo.png"));
    }

    @Test
    void elIndexDeVercelConEstado200NoCuentaComoPublicado() {
        assertFalse(BrandConfig.publicado(base + "/sin-desplegar.png"));
    }

    @Test
    void unaRespuestaDeErrorOSinServidorNoCuentaComoPublicada() {
        assertFalse(BrandConfig.publicado(base + "/borrado.png"));
        servidor.stop(0);
        assertFalse(BrandConfig.publicado(base + "/logo.png"));
    }
}
