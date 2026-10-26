package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO;
import com.drivemaster.drivemaster.model.CambioTaller;
import com.drivemaster.drivemaster.model.DiaSemana;
import com.drivemaster.drivemaster.model.EstadoAprobacion;
import com.drivemaster.drivemaster.model.HorarioTaller;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.repository.CambioTallerRepository;
import com.drivemaster.drivemaster.repository.HorarioTallerRepository;
import com.drivemaster.drivemaster.repository.TallerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Horario de atención estructurado del taller: por cada día, cerrado o una o más franjas sin cruces. Reemplaza al
 * texto libre de Taller.horario, que se sigue llenando con un resumen generado para que el cliente lo vea igual que
 * antes. El taller solo ve y edita el suyo; el administrador puede consultarlo.
 */
@Service
public class HorarioTallerService {

    public static final int MAX_FRANJAS_POR_DIA = 4;

    private final HorarioTallerRepository horarios;
    private final TallerRepository tallerRepository;
    private final TallerService tallerService;
    private final CambioTallerRepository cambios;

    public HorarioTallerService(HorarioTallerRepository horarios, TallerRepository tallerRepository,
                                TallerService tallerService, CambioTallerRepository cambios) {
        this.horarios = horarios;
        this.tallerRepository = tallerRepository;
        this.tallerService = tallerService;
        this.cambios = cambios;
    }

    /**
     * El taller {id} si quien llama puede verlo: el administrador ve cualquiera; el taller, solo el suyo (si pide otro,
     * 403 sin decir si existe).
     */
    public Taller tallerAccesible(String id, String correo, boolean esAdmin) {
        if (esAdmin) {
            return tallerRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taller no encontrado"));
        }
        Taller propio = tallerService.obtenerPorUsuario(correo);
        if (!propio.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes ver el horario de tu taller");
        }
        return propio;
    }

    /** Las franjas del taller ordenadas por día y hora; vacío si aún no configuró el horario. */
    public List<FranjaHorarioDTO> obtener(String id, String correo, boolean esAdmin) {
        return franjasDe(tallerAccesible(id, correo, esAdmin).getId()).stream().map(HorarioTallerService::toDTO).toList();
    }

    /** Las franjas guardadas de un taller, por día y luego por orden. */
    public List<HorarioTaller> franjasDe(String tallerId) {
        return horarios.findByTallerId(tallerId).stream()
                .sorted(Comparator.comparing(HorarioTaller::getDiaSemana).thenComparingInt(HorarioTaller::getOrden))
                .toList();
    }

    /** Reemplaza el horario completo. Los días que no vienen quedan cerrados. */
    public List<FranjaHorarioDTO> reemplazar(String id, String correo, List<FranjaHorarioDTO> franjas) {
        Taller taller = tallerAccesible(id, correo, false);
        if (!EstadoAprobacion.estaAprobado(taller.getEstadoAprobacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu taller aún no está aprobado");
        }
        Map<DiaSemana, List<HorarioTaller>> porDia = validar(franjas);

        List<HorarioTaller> nuevas = new ArrayList<>();
        porDia.forEach((dia, lista) -> {
            if (lista.isEmpty()) {
                nuevas.add(HorarioTaller.builder().tallerId(taller.getId()).diaSemana(dia).abierto(false).orden(0).build());
            } else {
                for (int i = 0; i < lista.size(); i++) {
                    HorarioTaller f = lista.get(i);
                    f.setTallerId(taller.getId());
                    f.setOrden(i);
                    nuevas.add(f);
                }
            }
        });
        horarios.deleteByTallerId(taller.getId());
        horarios.saveAll(nuevas);

        // TODO: retirar Taller.horario cuando el mapa y la lista del cliente lean el horario estructurado.
        String resumen = DatosTallerService.limpiarHorario(resumen(porDia));
        if (!Objects.equals(resumen, taller.getHorario())) {
            cambios.save(CambioTaller.builder()
                    .tallerId(taller.getId()).campo(CambioTaller.HORARIO)
                    .valorAnterior(taller.getHorario()).valorNuevo(resumen)
                    .cambiadoPor(correo).fecha(LocalDateTime.now()).build());
            taller.setHorario(resumen);
            tallerRepository.save(taller);
        }
        return franjasDe(taller.getId()).stream().map(HorarioTallerService::toDTO).toList();
    }

    /**
     * Rechaza con 400 una cita de revisión fuera del horario del taller: un día que cierra o una hora fuera de sus
     * franjas (la hora de cierre ya no cuenta). Si el taller aún no configura su horario por días, no restringe.
     */
    public void validarCita(String tallerId, LocalDateTime fecha) {
        List<HorarioTaller> franjas = franjasDe(tallerId);
        if (franjas.isEmpty()) return;
        DiaSemana dia = DiaSemana.de(fecha.getDayOfWeek());
        List<HorarioTaller> delDia = franjas.stream().filter(f -> f.getDiaSemana() == dia && f.isAbierto()).toList();
        String nombreDia = nombre(dia).toLowerCase();
        if (delDia.isEmpty()) {
            throw invalido("Tu taller está cerrado los " + (nombreDia.endsWith("s") ? nombreDia : nombreDia + "s")
                    + ". Elige otro día para la revisión");
        }
        LocalTime hora = fecha.toLocalTime();
        boolean dentro = delDia.stream()
                .anyMatch(f -> !hora.isBefore(f.getHoraApertura()) && hora.isBefore(f.getHoraCierre()));
        if (!dentro) {
            throw invalido("La revisión a las " + hora12(hora) + " queda fuera de tu horario del " + nombreDia
                    + " (" + textoFranjas(delDia) + ")");
        }
    }

    /**
     * Valida el cuerpo y lo agrupa por día (lista vacía = cerrado), cada día con sus franjas de la más temprana a la
     * más tarde. Rechaza con 400 y un mensaje que nombra el día.
     */
    static Map<DiaSemana, List<HorarioTaller>> validar(List<FranjaHorarioDTO> franjas) {
        if (franjas == null) throw invalido("Envía el horario de la semana");
        Map<DiaSemana, List<HorarioTaller>> porDia = new EnumMap<>(DiaSemana.class);
        Map<DiaSemana, Boolean> cerrado = new EnumMap<>(DiaSemana.class);
        for (DiaSemana d : DiaSemana.values()) porDia.put(d, new ArrayList<>());

        for (FranjaHorarioDTO f : franjas) {
            if (f == null || f.getDiaSemana() == null) throw invalido("Cada franja debe indicar el día de la semana");
            DiaSemana dia = f.getDiaSemana();
            if (!f.isAbierto()) {
                cerrado.put(dia, true);
                continue;
            }
            if (f.getHoraApertura() == null || f.getHoraCierre() == null) {
                throw invalido(nombre(dia) + ": indica la hora de apertura y la de cierre");
            }
            if (!f.getHoraCierre().isAfter(f.getHoraApertura())) {
                throw invalido(nombre(dia) + ": la hora de cierre debe ser después de la de apertura");
            }
            porDia.get(dia).add(HorarioTaller.builder().diaSemana(dia).abierto(true)
                    .horaApertura(f.getHoraApertura()).horaCierre(f.getHoraCierre()).build());
        }

        porDia.forEach((dia, lista) -> {
            if (lista.isEmpty()) return;
            if (cerrado.containsKey(dia)) throw invalido(nombre(dia) + ": no puede estar abierto y cerrado a la vez");
            if (lista.size() > MAX_FRANJAS_POR_DIA) {
                throw invalido(nombre(dia) + ": máximo " + MAX_FRANJAS_POR_DIA + " franjas por día");
            }
            lista.sort(Comparator.comparing(HorarioTaller::getHoraApertura));
            for (int i = 1; i < lista.size(); i++) {
                if (lista.get(i).getHoraApertura().isBefore(lista.get(i - 1).getHoraCierre())) {
                    throw invalido(nombre(dia) + ": las franjas se cruzan");
                }
            }
        });
        return porDia;
    }

    /** "Lun a Vie 8:00 a. m.–12:00 p. m. y 2:00 p. m.–6:00 p. m.; Sáb 8:00 a. m.–1:00 p. m.; Dom cerrado". */
    static String resumen(Map<DiaSemana, List<HorarioTaller>> porDia) {
        List<String> partes = new ArrayList<>();
        DiaSemana[] dias = DiaSemana.values();
        int i = 0;
        while (i < dias.length) {
            String franjas = textoFranjas(porDia.get(dias[i]));
            int j = i;
            while (j + 1 < dias.length && textoFranjas(porDia.get(dias[j + 1])).equals(franjas)) j++;
            String rango = i == j ? dias[i].corto()
                    : dias[i].corto() + (j == i + 1 ? " y " : " a ") + dias[j].corto();
            partes.add(rango + " " + franjas);
            i = j + 1;
        }
        return String.join("; ", partes);
    }

    private static String textoFranjas(List<HorarioTaller> lista) {
        if (lista == null || lista.isEmpty()) return "cerrado";
        return String.join(" y ", lista.stream().map(f -> hora12(f.getHoraApertura()) + "–" + hora12(f.getHoraCierre())).toList());
    }

    static String hora12(LocalTime t) {
        int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return h + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? " a. m." : " p. m.");
    }

    static String nombre(DiaSemana dia) {
        return switch (dia) {
            case LUNES -> "Lunes";
            case MARTES -> "Martes";
            case MIERCOLES -> "Miércoles";
            case JUEVES -> "Jueves";
            case VIERNES -> "Viernes";
            case SABADO -> "Sábado";
            case DOMINGO -> "Domingo";
        };
    }

    private static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private static FranjaHorarioDTO toDTO(HorarioTaller h) {
        return FranjaHorarioDTO.builder().diaSemana(h.getDiaSemana()).abierto(h.isAbierto())
                .horaApertura(h.getHoraApertura()).horaCierre(h.getHoraCierre()).orden(h.getOrden()).build();
    }
}
