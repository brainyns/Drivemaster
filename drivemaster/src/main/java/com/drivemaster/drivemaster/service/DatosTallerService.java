package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.taller.ActualizarDatosTallerRequest;
import com.drivemaster.drivemaster.model.CambioTaller;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.CambioTallerRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Edición de la tarifa por hora y el horario por parte del propio taller. El taller sale de la sesión; el cuerpo solo
 * trae los dos valores. Cada cambio deja un registro con quién, cuándo y el valor anterior.
 *
 * La tarifa por hora es el valor que usa el constructor de cotizaciones para la mano de obra por horas. Las cotizaciones
 * ya emitidas guardan su propio precio unitario y no cambian.
 */
@Service
public class DatosTallerService {

    public static final double TARIFA_MAXIMA = 1_000_000.0;
    public static final int HORARIO_MAX = 200;

    private final TallerService tallerService;
    private final TallerRepository tallerRepository;
    private final CambioTallerRepository cambios;

    public DatosTallerService(TallerService tallerService, TallerRepository tallerRepository,
                              CambioTallerRepository cambios) {
        this.tallerService = tallerService;
        this.tallerRepository = tallerRepository;
        this.cambios = cambios;
    }

    public TallerDTO actualizar(String correo, ActualizarDatosTallerRequest datos) {
        Taller taller = tallerService.obtenerPorUsuario(correo);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller aún no está aprobado");
        }
        if (datos == null || (datos.getTarifaHora() == null && datos.getHorario() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la tarifa por hora o el horario");
        }

        Double nuevaTarifa = null;
        if (datos.getTarifaHora() != null) {
            double t = datos.getTarifaHora();
            if (Double.isNaN(t) || Double.isInfinite(t) || t <= 0 || t > TARIFA_MAXIMA) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La tarifa por hora debe ser mayor que 0 y no pasar de " + (long) TARIFA_MAXIMA);
            }
            nuevaTarifa = Math.round(t * 100.0) / 100.0;
        }
        String nuevoHorario = datos.getHorario() == null ? null : limpiarHorario(datos.getHorario());

        LocalDateTime ahora = LocalDateTime.now();
        if (nuevaTarifa != null && !Objects.equals(nuevaTarifa, taller.getTarifaHora())) {
            registrar(taller.getId(), CambioTaller.TARIFA_HORA, taller.getTarifaHora(), nuevaTarifa, correo, ahora);
            taller.setTarifaHora(nuevaTarifa);
        }
        if (nuevoHorario != null && !Objects.equals(nuevoHorario.isEmpty() ? null : nuevoHorario, taller.getHorario())) {
            registrar(taller.getId(), CambioTaller.HORARIO, taller.getHorario(),
                    nuevoHorario.isEmpty() ? null : nuevoHorario, correo, ahora);
            taller.setHorario(nuevoHorario.isEmpty() ? null : nuevoHorario);
        }
        tallerRepository.save(taller);
        return tallerService.miTaller(correo);
    }

    /** Texto plano: sin etiquetas ni caracteres de control, espacios normalizados y máximo 200 caracteres. */
    static String limpiarHorario(String texto) {
        String limpio = texto
                .replaceAll("<[^>]*>", " ")
                .replace("<", " ").replace(">", " ")
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return limpio.length() > HORARIO_MAX ? limpio.substring(0, HORARIO_MAX).trim() : limpio;
    }

    private void registrar(String tallerId, String campo, Object anterior, Object nuevo, String correo, LocalDateTime fecha) {
        cambios.save(CambioTaller.builder()
                .tallerId(tallerId).campo(campo)
                .valorAnterior(anterior == null ? null : String.valueOf(anterior))
                .valorNuevo(nuevo == null ? null : String.valueOf(nuevo))
                .cambiadoPor(correo).fecha(fecha).build());
    }
}
