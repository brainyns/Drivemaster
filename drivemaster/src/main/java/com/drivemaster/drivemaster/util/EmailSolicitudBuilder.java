package com.drivemaster.drivemaster.util;

import java.text.NumberFormat;
import java.util.Locale;

import com.drivemaster.drivemaster.model.Usuario;

import static com.drivemaster.drivemaster.util.PlantillaCorreo.e;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.parrafo;

public class EmailSolicitudBuilder {

    private static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");
    private static final String PIE = "Gracias por confiar en DriveMaster. © 2026 DriveMaster · Todos los derechos reservados";
    private static final String CONTACTO = "<p style=\"margin:0;font-size:13px;line-height:1.6;color:#6b7280;\">"
            + "Si tienes dudas, contáctanos por WhatsApp al <strong>573015335263</strong>.</p>";

    public static String pagoRecibido(Usuario usuario, double total) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(LOCALE_CO);
        fmt.setMaximumFractionDigits(0);

        return PlantillaCorreo.documento("Pago recibido", "Hemos recibido tu pago",
                parrafo("Hola <strong>" + e(nombre(usuario)) + "</strong>,")
                + parrafo("Gracias por tu compra. Hemos recibido tu pago por:")
                + PlantillaCorreo.montoDestacado(fmt.format(total))
                + parrafo("Tu pedido está siendo gestionado y será enviado en un plazo de "
                        + "<strong>7 a 15 días hábiles</strong>. Te notificaremos por correo cuando esté en camino.")
                + CONTACTO,
                PIE);
    }

    public static String entregado(Usuario usuario) {
        return PlantillaCorreo.documento("Pedido entregado", "¡Tu pedido ha sido entregado!",
                parrafo("Hola <strong>" + e(nombre(usuario)) + "</strong>,")
                + parrafo("Tu pedido ha sido entregado. Gracias por comprar con DriveMaster, esperamos que disfrutes tu compra.")
                + CONTACTO,
                PIE);
    }

    private static String nombre(Usuario usuario) {
        return usuario.getNombre() != null ? usuario.getNombre() : "cliente";
    }
}
