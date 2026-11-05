package com.drivemaster.drivemaster.config;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Marca de DriveMaster en un solo lugar: nombre, colores y logo de los correos y los PDF
 * (propiedades brand.* de application.properties; el logo sale de BRAND_LOGO_URL).
 *
 * Los constructores de correos y PDF son estáticos, por eso los valores se publican en campos
 * estáticos al arrancar. Sin Spring (tests) quedan los valores por defecto, que son los mismos
 * de application.properties, salvo el logo: sin Spring no hay logo y los tests no salen a internet.
 */
@Component
public class BrandConfig {

    private static final Logger log = LoggerFactory.getLogger(BrandConfig.class);
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;
    /** Si el logo no se pudo descargar para el PDF, se vuelve a intentar pasado este tiempo. */
    private static final long REINTENTO_LOGO_MS = 5 * 60 * 1000L;
    /** Logo recortado, el que se usa si BRAND_LOGO_URL no está o viene vacía (docker-compose la pasa vacía). */
    static final String LOGO_POR_DEFECTO = "https://drivemaster-orcin.vercel.app/images/Drivemaster_logo_correo.png";
    /** Se usa mientras el logo recortado no esté publicado: un correo no puede cambiar de imagen si la suya falla. */
    static final String LOGO_RESPALDO = "https://drivemaster-orcin.vercel.app/images/Drivemaster_logo.png";

    private static volatile String nombre = "DriveMaster";
    private static volatile String colorPrimario = "#F28C28";
    private static volatile String colorSobrePrimario = "#17191C";
    private static volatile String colorTexto = "#17191C";
    private static volatile String logoUrl = "";
    private static volatile int logoAncho = 80;

    private static volatile byte[] logoBytes;
    private static volatile long ultimoFalloLogo;
    /** true mientras se usa el respaldo y falta confirmar que el logo recortado ya está publicado. */
    private static volatile boolean confirmarLogoNuevo;
    private static volatile long ultimaRevisionLogo;

    public BrandConfig(@Value("${brand.name:DriveMaster}") String nombre,
                       @Value("${brand.color.primary:#F28C28}") String colorPrimario,
                       @Value("${brand.color.on-primary:#17191C}") String colorSobrePrimario,
                       @Value("${brand.color.text:#17191C}") String colorTexto,
                       @Value("${brand.logo-url:}") String logoUrl,
                       @Value("${brand.logo-width:80}") String logoAncho) {
        BrandConfig.logoAncho = anchoValido(logoAncho);
        if (nombre != null && !nombre.isBlank()) BrandConfig.nombre = nombre.trim();
        BrandConfig.colorPrimario = hexValido(colorPrimario, BrandConfig.colorPrimario);
        BrandConfig.colorSobrePrimario = hexValido(colorSobrePrimario, BrandConfig.colorSobrePrimario);
        BrandConfig.colorTexto = hexValido(colorTexto, BrandConfig.colorTexto);
        boolean porDefecto = logoUrl == null || logoUrl.isBlank();
        String url = porDefecto ? LOGO_RESPALDO : logoUrl.trim();
        if (!url.isEmpty() && !url.startsWith("https://") && !url.startsWith("http://")) {
            log.warn("BRAND_LOGO_URL no es una URL http(s); los correos mostrarán el nombre en texto.");
            url = "";
        }
        BrandConfig.logoUrl = url;
        BrandConfig.logoBytes = null;
        BrandConfig.confirmarLogoNuevo = porDefecto;
        BrandConfig.ultimaRevisionLogo = 0;
        revisarLogoNuevo();
    }

    /** Ancho del logo en el correo entre 40 y 300 px; vacío o inválido: 80. */
    private static int anchoValido(String valor) {
        try {
            int ancho = Integer.parseInt(valor == null ? "" : valor.trim());
            if (ancho >= 40 && ancho <= 300) return ancho;
        } catch (NumberFormatException e) {
            // se usa el valor por defecto
        }
        if (valor != null && !valor.isBlank()) log.warn("BRAND_LOGO_WIDTH inválido '{}'; se usa 80.", valor);
        return 80;
    }

    private static String hexValido(String valor, String porDefecto) {
        if (valor != null && HEX.matcher(valor.trim()).matches()) return valor.trim();
        log.warn("Color de marca inválido '{}'; se usa {}.", valor, porDefecto);
        return porDefecto;
    }

    public static String nombre() { return nombre; }

    /** Color principal (--primary): encabezados, botones, franjas y líneas. */
    public static String colorPrimario() { return colorPrimario; }

    /** Texto sobre el color principal: oscuro, como --on-primary en la web. */
    public static String colorSobrePrimario() { return colorSobrePrimario; }

    /** Texto oscuro del nombre de la marca en los encabezados (como el texto de la página). */
    public static String colorTexto() { return colorTexto; }

    public static Color colorTextoAwt() { return Color.decode(colorTexto); }

    /** Tinte muy claro del color principal para fondos de recuadros. */
    public static String colorPrimarioSuave() {
        Color c = Color.decode(colorPrimario);
        return String.format("#%02X%02X%02X", tinte(c.getRed()), tinte(c.getGreen()), tinte(c.getBlue()));
    }

    private static int tinte(int canal) {
        return Math.round(canal + (255 - canal) * 0.9f);
    }

    public static Color colorPrimarioAwt() { return Color.decode(colorPrimario); }

    /** URL pública del logo; vacía solo sin Spring (tests). */
    public static String logoUrl() {
        revisarLogoNuevo();
        return logoUrl;
    }

    /**
     * Mientras se use el respaldo, comprueba en segundo plano (como mucho cada 5 minutos) si el logo
     * recortado ya está publicado y, en cuanto lo esté, pasa a usarlo. Nunca demora un correo.
     */
    private static void revisarLogoNuevo() {
        long ahora = System.currentTimeMillis();
        if (!confirmarLogoNuevo || ahora - ultimaRevisionLogo < REINTENTO_LOGO_MS) return;
        ultimaRevisionLogo = ahora;
        Thread.ofVirtual().start(() -> {
            if (publicado(LOGO_POR_DEFECTO) && confirmarLogoNuevo) {
                logoUrl = LOGO_POR_DEFECTO;
                logoBytes = null;
                confirmarLogoNuevo = false;
                log.info("Logo recortado publicado: los correos y PDF pasan a usarlo.");
            }
        });
    }

    /**
     * true si la URL entrega una imagen. No basta el 200: Vercel responde con index.html (text/html)
     * cualquier ruta que no exista, y en el correo eso sería una imagen rota.
     */
    static boolean publicado(String url) {
        try {
            HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> resp = cliente.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200
                    && resp.headers().firstValue("Content-Type").orElse("").toLowerCase().startsWith("image/");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ancho del logo en el correo (px). */
    public static int logoAncho() { return logoAncho; }

    /**
     * Imagen del logo para los PDF, descargada una vez de BRAND_LOGO_URL y guardada en memoria.
     * Devuelve null si no hay URL o la descarga falla (el PDF muestra entonces el nombre en texto).
     */
    public static byte[] logoParaPdf() {
        byte[] enCache = logoBytes;
        if (enCache != null) return enCache;
        String url = logoUrl;
        if (url.isEmpty() || System.currentTimeMillis() - ultimoFalloLogo < REINTENTO_LOGO_MS) return null;
        synchronized (BrandConfig.class) {
            if (logoBytes != null) return logoBytes;
            try {
                HttpClient cliente = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<byte[]> resp = cliente.send(req, HttpResponse.BodyHandlers.ofByteArray());
                byte[] cuerpo = resp.body();
                if (resp.statusCode() != 200 || cuerpo == null || cuerpo.length == 0 || cuerpo.length > MAX_LOGO_BYTES) {
                    throw new IllegalStateException("respuesta " + resp.statusCode());
                }
                byte[] recortado = recortarMargen(cuerpo);
                logoBytes = recortado;
                return recortado;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("No se pudo descargar el logo para el PDF ({}); se usa el nombre en texto.", e.getMessage());
            }
            ultimoFalloLogo = System.currentTimeMillis();
            return null;
        }
    }

    /**
     * Quita el margen claro alrededor del logo (el archivo trae fondo blanco y mucho aire) para que
     * en el encabezado del PDF se vea a buen tamaño. Si no se puede leer, devuelve la imagen tal cual.
     */
    static byte[] recortarMargen(byte[] imagen) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imagen));
            if (img == null) return imagen;
            int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getRGB(x, y);
                    int alfa = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    boolean fondo = alfa < 16 || (r > 225 && g > 225 && b > 225);
                    if (!fondo) {
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0) return imagen;
            int pad = Math.max(2, (maxY - minY) / 20);
            minX = Math.max(0, minX - pad); minY = Math.max(0, minY - pad);
            maxX = Math.min(img.getWidth() - 1, maxX + pad); maxY = Math.min(img.getHeight() - 1, maxY + pad);
            int ancho = maxX - minX + 1, alto = maxY - minY + 1;
            BufferedImage recorte = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    int argb = img.getRGB(minX + x, minY + y);
                    // Sobre blanco: los píxeles semitransparentes se mezclan con el fondo
                    float alfa = (argb >>> 24) / 255f;
                    int r = sobreBlanco((argb >> 16) & 0xFF, alfa);
                    int g = sobreBlanco((argb >> 8) & 0xFF, alfa);
                    int b = sobreBlanco(argb & 0xFF, alfa);
                    // El fondo casi blanco queda blanco puro, igual que el encabezado
                    boolean fondo = r > 225 && g > 225 && b > 225;
                    recorte.setRGB(x, y, fondo ? 0xFFFFFF : (r << 16) | (g << 8) | b);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(recorte, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return imagen;
        }
    }

    private static int sobreBlanco(int canal, float alfa) {
        return Math.round(canal * alfa + 255 * (1 - alfa));
    }
}
