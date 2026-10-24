package com.drivemaster.drivemaster.controller;

import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.service.HorarioTallerService;
import com.drivemaster.drivemaster.service.SolicitudServicioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Agenda del taller: sus citas de revisión entre dos días. El taller solo ve las suyas; el personal, las de cualquiera. */
@RestController
@RequestMapping("/api/talleres/{id}")
public class AgendaTallerController {

    // Lo que pide la vista mensual con sus semanas de borde (6 semanas) y algo de margen
    static final int MAX_DIAS = 62;

    private final HorarioTallerService horario;
    private final SolicitudServicioService solicitudes;

    public AgendaTallerController(HorarioTallerService horario, SolicitudServicioService solicitudes) {
        this.horario = horario;
        this.solicitudes = solicitudes;
    }

    @GetMapping("/citas")
    public ResponseEntity<List<SolicitudServicioDTO>> citas(@PathVariable String id, @RequestParam String desde,
                                                            @RequestParam String hasta, Authentication auth) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        if (fin.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha \"hasta\" no puede ser anterior a \"desde\"");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > MAX_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta como máximo " + MAX_DIAS + " días a la vez");
        }
        Taller taller = horario.tallerAccesible(id, auth.getName(), HorarioTallerController.esPersonal(auth));
        return ResponseEntity.ok(solicitudes.citasTaller(taller.getId(), inicio, fin));
    }

    private static LocalDate fecha(String texto, String nombre) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + nombre + "\" debe tener el formato AAAA-MM-DD");
        }
    }
}
