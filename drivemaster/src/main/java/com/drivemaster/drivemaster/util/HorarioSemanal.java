package com.drivemaster.drivemaster.util;

import com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO;
import com.drivemaster.drivemaster.model.DiaSemana;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reglas del horario de atención por días, comunes al taller (HorarioTallerService) y a la tienda (TiendaServiceImpl):
 * cada día cerrado o con una a {@value #MAX_FRANJAS_POR_DIA} franjas, el cierre después de la apertura y sin cruces.
 * También arma el resumen en texto que ve el cliente ("Lun a Vie 8:00 a. m.–6:00 p. m.; Dom cerrado").
 */
public final class HorarioSemanal {

    public static final int MAX_FRANJAS_POR_DIA = 4;

    private HorarioSemanal() {
    }

    /**
     * Valida el horario y lo agrupa por día (lista vacía = cerrado), cada día con sus franjas de la más temprana a la
     * más tarde. Los días que no vienen quedan cerrados. Rechaza con 400 y un mensaje que nombra el día.
     */
    public static Map<DiaSemana, List<FranjaHorarioDTO>> validar(List<FranjaHorarioDTO> franjas) {
        if (franjas == null) throw invalido("Envía el horario de la semana");
        Map<DiaSemana, List<FranjaHorarioDTO>> porDia = new EnumMap<>(DiaSemana.class);
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
            porDia.get(dia).add(FranjaHorarioDTO.builder().diaSemana(dia).abierto(true)
                    .horaApertura(f.getHoraApertura()).horaCierre(f.getHoraCierre()).build());
        }

        porDia.forEach((dia, lista) -> {
            if (lista.isEmpty()) return;
            if (cerrado.containsKey(dia)) throw invalido(nombre(dia) + ": no puede estar abierto y cerrado a la vez");
            if (lista.size() > MAX_FRANJAS_POR_DIA) {
                throw invalido(nombre(dia) + ": máximo " + MAX_FRANJAS_POR_DIA + " franjas por día");
            }
            lista.sort(Comparator.comparing(FranjaHorarioDTO::getHoraApertura));
            for (int i = 1; i < lista.size(); i++) {
                if (lista.get(i).getHoraApertura().isBefore(lista.get(i - 1).getHoraCierre())) {
                    throw invalido(nombre(dia) + ": las franjas se cruzan");
                }
            }
        });
        return porDia;
    }

    /** La semana completa para guardar: un día cerrado es una fila con abierto=false; uno abierto, sus franjas en orden. */
    public static List<FranjaHorarioDTO> semanaCompleta(Map<DiaSemana, List<FranjaHorarioDTO>> porDia) {
        List<FranjaHorarioDTO> semana = new ArrayList<>();
        porDia.forEach((dia, lista) -> {
            if (lista.isEmpty()) {
                semana.add(FranjaHorarioDTO.builder().diaSemana(dia).abierto(false).orden(0).build());
            } else {
                for (int i = 0; i < lista.size(); i++) {
                    FranjaHorarioDTO f = lista.get(i);
                    semana.add(FranjaHorarioDTO.builder().diaSemana(dia).abierto(true)
                            .horaApertura(f.getHoraApertura()).horaCierre(f.getHoraCierre()).orden(i).build());
                }
            }
        });
        return semana;
    }

    /** "Lun a Vie 8:00 a. m.–12:00 p. m. y 2:00 p. m.–6:00 p. m.; Sáb 8:00 a. m.–1:00 p. m.; Dom cerrado". */
    public static String resumen(Map<DiaSemana, List<FranjaHorarioDTO>> porDia) {
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

    /** "8:00 a. m.–12:00 p. m. y 2:00 p. m.–6:00 p. m.", o "cerrado" si no hay franjas. */
    public static String textoFranjas(List<FranjaHorarioDTO> lista) {
        if (lista == null || lista.isEmpty()) return "cerrado";
        return String.join(" y ", lista.stream().map(f -> hora12(f.getHoraApertura()) + "–" + hora12(f.getHoraCierre())).toList());
    }

    public static String hora12(LocalTime t) {
        int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return h + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? " a. m." : " p. m.");
    }

    public static String nombre(DiaSemana dia) {
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
}
