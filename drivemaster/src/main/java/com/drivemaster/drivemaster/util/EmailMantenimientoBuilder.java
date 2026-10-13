package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class EmailMantenimientoBuilder {

    private static final Locale LOCALE = Locale.forLanguageTag("es");

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy 'a las' HH:mm", LOCALE);

    private static String fecha(LocalDateTime valor, DateTimeFormatter fmt) {
        return valor != null ? valor.format(fmt) : "—";
    }

    private static String vehiculo(Vehiculo v) {
        return v.getMarca() + " " + v.getModelo() + (v.getAnio() != null ? " " + v.getAnio() : "");
    }

    private static final String PIE = "Este es un mensaje automático de DriveMaster. Si no deseas recibir recordatorios "
            + "de mantenimiento, puedes desactivarlos en el perfil de tu vehículo.";

    private static String envolver(String etiqueta, String titulo, String cuerpo) {
        return PlantillaCorreo.documento(etiqueta, titulo, cuerpo, PIE);
    }

    /** Aviso de que el vehículo se acerca o superó el intervalo de mantenimiento. */
    public static String recordatorioKm(Usuario usuario, Vehiculo vehiculo,
                                        int kmActual, int kmUltimoMantenimiento, int intervaloKm) {

        int kmRestantes = kmUltimoMantenimiento + intervaloKm - kmActual;
        String titulo = "Tu " + vehiculo(vehiculo) + " necesita mantenimiento";

        String situacion = kmRestantes > 0
                ? "Le quedan <strong>" + kmRestantes + " km</strong> para el próximo mantenimiento."
                : "Tu vehículo está <strong>" + Math.abs(kmRestantes) + " km</strong> por encima "
                  + "del intervalo recomendado.";

        String cuerpo =
                "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>" + situacion + "</p>"
                + "<table style=\"border-collapse:collapse;margin:20px 0;font-size:14px\">"
                + fila("Vehículo", vehiculo(vehiculo))
                + fila("Placa", vehiculo.getPlaca() != null ? vehiculo.getPlaca() : "—")
                + fila("Kilometraje actual", kmActual + " km")
                + fila("Último mantenimiento", kmUltimoMantenimiento + " km")
                + fila("Intervalo configurado", intervaloKm + " km")
                + "</table>"
                + "<p>Un mantenimiento al día evita averías-costosas. Agenda tu cita desde la "
                + "sección de Mis Vehículos.</p>";

        return envolver("Mantenimiento", titulo, cuerpo);
    }

    /** Aviso de la fecha y hora en que el vehículo ingresará al taller. */
    public static String citaProgramada(Usuario usuario, Vehiculo vehiculo, LocalDateTime fechaProgramada) {

        String titulo = "Cita confirmada: " + vehiculo(vehiculo);

        String cuerpo =
                "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>Tu cita en el taller ya está agendada. Estos son los datos:</p>"
                + "<table style=\"border-collapse:collapse;margin:20px 0;font-size:14px\">"
                + fila("Vehículo", vehiculo(vehiculo))
                + fila("Placa", vehiculo.getPlaca() != null ? vehiculo.getPlaca() : "—")
                + fila("Fecha y hora de ingreso", "<strong>" + fecha(fechaProgramada, FECHA_HORA) + "</strong>")
                + "</table>"
                + "<p>Te recomendamos llegar unos minutos antes. Si necesitas reprogramar, "
                + "responde a este correo o escríbenos por WhatsApp.</p>";

        return envolver("Cita en el taller", titulo, cuerpo);
    }

    /** Aviso de que el mantenimiento quedó registrado al finalizar el servicio. */
    public static String mantenimientoRealizado(Usuario usuario, Vehiculo vehiculo,
                                                int kmActual, int intervaloKm) {

        int proximoKm = kmActual + intervaloKm;

        String titulo = "Mantenimiento registrado";

        String cuerpo =
                "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>Registramos el mantenimiento de tu <strong>" + vehiculo(vehiculo)
                + "</strong>. A partir de ahora el conteo se reinicia.</p>"
                + "<table style=\"border-collapse:collapse;margin:20px 0;font-size:14px\">"
                + fila("Kilometraje actual", kmActual + " km")
                + fila("Próximo mantenimiento sugerido", proximoKm + " km")
                + "</table>"
                + "<p>Te avisaremos de nuevo cuando se acerque el momento.</p>";

        return envolver("Mantenimiento", titulo, cuerpo);
    }

    private static String fila(String etiqueta, String valor) {
        return "<tr>"
                + "<td style=\"padding:6px 18px 6px 0;color:#6b7280\">" + etiqueta + "</td>"
                + "<td style=\"padding:6px 0;color:#374151\">" + valor + "</td>"
                + "</tr>";
    }

    /** Aviso de que el vehículo acaba de entrar al taller. */
    public static String servicioIniciado(Usuario usuario, Vehiculo vehiculo, OrdenServicio orden) {
        String titulo = "Tu vehículo está en el taller";
        String cuerpo =
                "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>El <strong>" + vehiculo(vehiculo) + "</strong> ya entró al taller "
                + (orden.getTallerNombre() != null ? "<strong>" + orden.getTallerNombre() + "</strong>" : "del taller")
                + " y comenzamos el servicio en este momento.</p>"
                + "<table style=\"border-collapse:collapse;margin:20px 0;font-size:14px\">"
                + fila("Vehículo", vehiculo(vehiculo))
                + fila("Placa", vehiculo.getPlaca() != null ? vehiculo.getPlaca() : "—")
                + fila("Inicio del servicio", fecha(LocalDateTime.now(), FECHA_HORA))
                + "</table>"
                + "<p>Te avisaremos apenas tu vehículo esté listo para retirarlo.</p>";
        return envolver("Servicio", titulo, cuerpo);
    }

    /** Aviso de que el vehículo ya está listo para retirar. */
    public static String servicioFinalizado(Usuario usuario, Vehiculo vehiculo, OrdenServicio orden) {
        String titulo = "Tu vehículo está listo 🚗";
        String cuerpo =
                "<p>Hola " + usuario.getNombre() + ",</p>"
                + "<p>Puedes pasar a recoger tu <strong>" + vehiculo(vehiculo) + "</strong>. "
                + "El servicio terminó y el vehículo quedó listo.</p>"
                + "<table style=\"border-collapse:collapse;margin:20px 0;font-size:14px\">"
                + fila("Taller", orden.getTallerNombre() != null ? orden.getTallerNombre() : "—")
                + fila("Vehículo", vehiculo(vehiculo))
                + fila("Resumen del servicio", orden.getProductoNombre() != null ? orden.getProductoNombre() : "—")
                + (orden.getObservaciones() != null && !orden.getObservaciones().isBlank()
                        ? fila("Observaciones", orden.getObservaciones()) : "")
                + fila("Total", "$ " + String.format("%,.0f", orden.getTotal() != null ? orden.getTotal() : 0.0))
                + "</table>"
                + "<p>Gracias por confiar en DriveMaster.</p>";
        return envolver("Servicio", titulo, cuerpo);
    }

    private EmailMantenimientoBuilder() {
    }
}
