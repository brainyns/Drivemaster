package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.config.BrandConfig;
import com.drivemaster.drivemaster.model.DetalleVenta;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.model.Venta;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Identidad de los correos (blanco, naranja y gris oscuro) y datos del usuario siempre escapados. */
class PlantillaCorreoTest {

    private static final String PELIGROSO = "<script>alert(1)</script>";

    @Test
    void laFacturaUsaLaPlantillaComunSinAnchoFijoNiDatosCrudos() {
        Venta venta = FacturaMontosTest.venta();
        DetalleVenta linea = venta.getProductos().get(0);
        linea.setNombre(PELIGROSO);
        Usuario usuario = Usuario.builder().nombre(PELIGROSO).correo(PELIGROSO).identificacion(PELIGROSO).build();

        String html = EmailVentaBuilder.construir(usuario, venta, 0.19);

        assertFalse(html.contains("<script>"), "datos escapados");
        assertTrue(html.contains("max-width:620px;width:100%"), "se adapta al celular");
        assertTrue(html.contains("class=\"dm-apilar\""), "las columnas se apilan en el celular");
    }

    @Test
    void losCorreosDeMantenimientoEscapanLosDatos() {
        Usuario usuario = Usuario.builder().nombre(PELIGROSO).build();
        Vehiculo vehiculo = Vehiculo.builder().marca(PELIGROSO).modelo("Spark").placa(PELIGROSO).build();
        OrdenServicio orden = OrdenServicio.builder().tallerNombre(PELIGROSO).productoNombre(PELIGROSO)
                .observaciones(PELIGROSO).total(1000.0).build();

        for (String html : List.of(
                EmailMantenimientoBuilder.recordatorioKm(usuario, vehiculo, 100, 0, 50),
                EmailMantenimientoBuilder.citaProgramada(usuario, vehiculo, LocalDateTime.of(2026, 10, 30, 8, 0)),
                EmailMantenimientoBuilder.mantenimientoRealizado(usuario, vehiculo, 100, 50),
                EmailMantenimientoBuilder.servicioIniciado(usuario, vehiculo, orden),
                EmailMantenimientoBuilder.servicioFinalizado(usuario, vehiculo, orden))) {
            assertFalse(html.contains("<script>"), html);
        }
    }

    @Test
    void elFondoEsBlancoYElBotonLlevaTextoOscuroSobreElNaranja() {
        String html = PlantillaCorreo.documento("Etiqueta", "Título", PlantillaCorreo.parrafo("Hola"), null);
        assertTrue(html.contains("background-color:#ffffff"), "cuerpo blanco");
        assertFalse(html.contains("#f3f4f6") || html.contains("#f9fafb"), "sin fondos grises");
        assertTrue(html.contains("name=\"color-scheme\" content=\"light\""), "pide no invertir los colores");

        assertEquals("#17191C", BrandConfig.colorSobrePrimario());
        String boton = PlantillaCorreo.boton("Pagar", "https://drivemaster.co/pagar");
        assertTrue(boton.contains("background:#F28C28;color:#17191C"), boton);
        assertTrue(html.contains("color:" + PlantillaCorreo.COLOR_TITULO), "título en gris oscuro, no naranja");
    }

    @Test
    void elModoClaroSeRefuerzaPorClienteSinRomperLosDemas() {
        String html = PlantillaCorreo.documento("Etiqueta", "Título", PlantillaCorreo.parrafo("Hola"), null);

        assertTrue(html.contains("name=\"supported-color-schemes\" content=\"light\""), html);
        assertTrue(html.contains("<body class=\"body dm-fondo\""), "u + .body necesita la clase");

        // Si un cliente borra el estilo no debe quedar un fondo blanco fijo con el texto ya aclarado
        String cuerpo = html.substring(html.indexOf("<body"));
        assertFalse(cuerpo.contains("linear-gradient(#ffffff"), "el degradado blanco solo va en el estilo de Gmail");
        assertFalse(html.contains("mix-blend-mode:screen"), "screen borraría el texto oscuro sobre blanco");

        // Las reglas de Outlook no comparten bloque con las de Gmail
        for (String bloque : html.split("<style>")) {
            assertFalse(bloque.contains("u + .body") && bloque.contains("[data-og"), bloque);
        }
        assertTrue(html.contains("[data-ogsc]") && html.contains("[data-ogsb]"), html);
    }

    @Test
    void elLogoEsUnaUrlPublicaConTextoAlternativo() {
        // docker-compose pasa BRAND_LOGO_URL vacía: también debe caer en la URL pública
        new BrandConfig("DriveMaster", "#F28C28", "#17191C", "#17191C", "   ", "80");
        try {
            assertTrue(BrandConfig.logoUrl().startsWith("https://"), BrandConfig.logoUrl());
            String logo = PlantillaCorreo.logo();
            assertTrue(logo.contains("src=\"https://") && logo.contains("alt=\"Logo de DriveMaster\""), logo);
            assertFalse(logo.contains("cid:") || logo.contains("data:"), "ni adjunto ni incrustado");
        } finally {
            // Sin logo, como en el resto de los tests: así ninguno sale a internet a descargarlo
            ReflectionTestUtils.setField(BrandConfig.class, "logoUrl", "");
            ReflectionTestUtils.setField(BrandConfig.class, "logoAncho", 80);
            ReflectionTestUtils.setField(BrandConfig.class, "confirmarLogoNuevo", false);
        }
    }
}
