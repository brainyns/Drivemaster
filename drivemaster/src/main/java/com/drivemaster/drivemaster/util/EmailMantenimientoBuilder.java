package com.drivemaster.drivemaster.util;

import static com.drivemaster.drivemaster.util.PlantillaCorreo.e;
import static com.drivemaster.drivemaster.util.PlantillaCorreo.parrafo;

import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Correos de mantenimiento y de las órdenes del flujo anterior. Todo dato del usuario se escapa. */
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

    private static String saludo(Usuario usuario) {
        return parrafo("Hola " + e(usuario.getNombre()) + ",");
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
                saludo(usuario)
                + parrafo(situacion)
                + PlantillaCorreo.tabla(
                        fila("Vehículo", e(vehiculo(vehiculo)))
                        + fila("Placa", placa(vehiculo))
                        + fila("Kilometraje actual", kmActual + " km")
                        + fila("Último mantenimiento", kmUltimoMantenimiento + " km")
                        + fila("Intervalo configurado", intervaloKm + " km"))
                + parrafo("Un mantenimiento al día evita averías costosas. Agenda tu cita desde la "
                        + "sección de Mis Vehículos.");

        return envolver("Mantenimiento", titulo, cuerpo);
    }

    /** Aviso de la fecha y hora en que el vehículo ingresará al taller. */
    public static String citaProgramada(Usuario usuario, Vehiculo vehiculo, LocalDateTime fechaProgramada) {

        String titulo = "Cita confirmada: " + vehiculo(vehiculo);

        String cuerpo =
                saludo(usuario)
                + parrafo("Tu cita en el taller ya está agendada. Estos son los datos:")
                + PlantillaCorreo.tabla(
                        fila("Vehículo", e(vehiculo(vehiculo)))
                        + fila("Placa", placa(vehiculo))
                        + fila("Fecha y hora de ingreso", "<strong>" + e(fecha(fechaProgramada, FECHA_HORA)) + "</strong>"))
                + parrafo("Te recomendamos llegar unos minutos antes. Si necesitas reprogramar, "
                        + "responde a este correo o escríbenos por WhatsApp.");

        return envolver("Cita en el taller", titulo, cuerpo);
    }

    /** Aviso de que el mantenimiento quedó registrado al finalizar el servicio. */
    public static String mantenimientoRealizado(Usuario usuario, Vehiculo vehiculo,
                                                int kmActual, int intervaloKm) {

        int proximoKm = kmActual + intervaloKm;

        String titulo = "Mantenimiento registrado";

        String cuerpo =
                saludo(usuario)
                + parrafo("Registramos el mantenimiento de tu <strong>" + e(vehiculo(vehiculo))
                        + "</strong>. A partir de ahora el conteo se reinicia.")
                + PlantillaCorreo.tabla(
                        fila("Kilometraje actual", kmActual + " km")
                        + fila("Próximo mantenimiento sugerido", proximoKm + " km"))
                + parrafo("Te avisaremos de nuevo cuando se acerque el momento.");

        return envolver("Mantenimiento", titulo, cuerpo);
    }

    /** {@code valorHtml} ya es HTML: quien llama escapa los datos del usuario. */
    private static String fila(String etiqueta, String valorHtml) {
        return PlantillaCorreo.filaDato(etiqueta, valorHtml);
    }

    private static String placa(Vehiculo vehiculo) {
        return vehiculo.getPlaca() != null ? e(vehiculo.getPlaca()) : "—";
    }

    /** Aviso de que el vehículo acaba de entrar al taller. */
    public static String servicioIniciado(Usuario usuario, Vehiculo vehiculo, OrdenServicio orden) {
        String titulo = "Tu vehículo está en el taller";
        String cuerpo =
                saludo(usuario)
                + parrafo("El <strong>" + e(vehiculo(vehiculo)) + "</strong> ya entró al taller "
                        + (orden.getTallerNombre() != null ? "<strong>" + e(orden.getTallerNombre()) + "</strong>" : "del taller")
                        + " y comenzamos el servicio en este momento.")
                + PlantillaCorreo.tabla(
                        fila("Vehículo", e(vehiculo(vehiculo)))
                        + fila("Placa", placa(vehiculo))
                        + fila("Inicio del servicio", e(fecha(LocalDateTime.now(), FECHA_HORA))))
                + parrafo("Te avisaremos apenas tu vehículo esté listo para retirarlo.");
        return envolver("Servicio", titulo, cuerpo);
    }

    /** Aviso de que el vehículo ya está listo para retirar. */
    public static String servicioFinalizado(Usuario usuario, Vehiculo vehiculo, OrdenServicio orden) {
        String titulo = "Tu vehículo está listo";
        String cuerpo =
                saludo(usuario)
                + parrafo("Puedes pasar a recoger tu <strong>" + e(vehiculo(vehiculo)) + "</strong>. "
                        + "El servicio terminó y el vehículo quedó listo.")
                + PlantillaCorreo.tabla(
                        fila("Taller", orden.getTallerNombre() != null ? e(orden.getTallerNombre()) : "—")
                        + fila("Vehículo", e(vehiculo(vehiculo)))
                        + fila("Resumen del servicio", orden.getProductoNombre() != null ? e(orden.getProductoNombre()) : "—")
                        + (orden.getObservaciones() != null && !orden.getObservaciones().isBlank()
                                ? fila("Observaciones", e(orden.getObservaciones())) : "")
                        + fila("Total", PlantillaCorreo.moneda(orden.getTotal())))
                + parrafo("Gracias por confiar en DriveMaster.");
        return envolver("Servicio", titulo, cuerpo);
    }

    private EmailMantenimientoBuilder() {
    }
}
