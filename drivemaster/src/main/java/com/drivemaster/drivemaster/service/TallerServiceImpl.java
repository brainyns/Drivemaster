package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.UsuarioRepository;
import com.drivemaster.drivemaster.util.HaversineUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TallerServiceImpl implements TallerService {

    private final TallerRepository tallerRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public TallerServiceImpl(TallerRepository tallerRepository,
                              UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder) {
        this.tallerRepository = tallerRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public TallerDTO crear(CrearTallerRequest request) {
        String usuarioId = request.getUsuarioId();

        if (usuarioId == null || usuarioId.isBlank()) {
            if (request.getCorreoUsuario() == null || request.getCorreoUsuario().isBlank()
                    || request.getPasswordUsuario() == null || request.getPasswordUsuario().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe indicar correoUsuario y passwordUsuario para crear el usuario del taller");
            }
            if (usuarioRepository.existsByCorreo(request.getCorreoUsuario())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
            }

            Usuario usuario = Usuario.builder()
                    .nombre(request.getNombre())
                    .correo(request.getCorreoUsuario())
                    .password(passwordEncoder.encode(request.getPasswordUsuario()))
                    .proveedor("LOCAL")
                    .rol("TALLER")
                    .permisos(List.of("TALLER_ORDENES_READ", "TALLER_ORDENES_WRITE"))
                    .activo(true)
                    .datosCompletos(true)
                    .fechaCreacion(Instant.now())
                    .intentosFallidos(0)
                    .bloqueado(false)
                    .build();
            usuario = usuarioRepository.save(usuario);
            usuarioId = usuario.getId();
        }

        Taller taller = Taller.builder()
                .usuarioId(usuarioId)
                .nombre(request.getNombre())
                .ciudad(request.getCiudad())
                .direccion(request.getDireccion())
                .latitud(request.getLatitud())
                .longitud(request.getLongitud())
                .especialidades(request.getEspecialidades() != null ? request.getEspecialidades() : new ArrayList<>())
                .rating(request.getRating() != null ? request.getRating() : 5.0)
                .precioManoObra(request.getPrecioManoObra())
                .activo(true)
                .build();

        taller = tallerRepository.save(taller);
        return toDTO(taller, null, null);
    }

    @Override
    public TallerDTO actualizar(String id, CrearTallerRequest request) {
        Taller taller = obtenerEntidad(id);
        taller.setNombre(request.getNombre());
        taller.setCiudad(request.getCiudad());
        taller.setDireccion(request.getDireccion());
        taller.setLatitud(request.getLatitud());
        taller.setLongitud(request.getLongitud());
        if (request.getEspecialidades() != null) taller.setEspecialidades(request.getEspecialidades());
        if (request.getRating() != null) taller.setRating(request.getRating());
        if (request.getPrecioManoObra() != null) taller.setPrecioManoObra(request.getPrecioManoObra());

        taller = tallerRepository.save(taller);
        return toDTO(taller, null, null);
    }

    @Override
    public TallerDTO activarDesactivar(String id, boolean activo) {
        Taller taller = obtenerEntidad(id);
        taller.setActivo(activo);
        taller = tallerRepository.save(taller);
        return toDTO(taller, null, null);
    }

    @Override
    public List<TallerDTO> listarTodos() {
        return tallerRepository.findAll().stream()
                .map(t -> toDTO(t, null, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<TallerDTO> listarActivos(String ciudad, Double lat, Double lng) {
        List<Taller> talleres = (ciudad != null && !ciudad.isBlank())
                ? tallerRepository.findByCiudadIgnoreCaseAndActivoTrue(ciudad)
                : tallerRepository.findByActivoTrue();

        return talleres.stream()
                .map(t -> toDTO(t, lat, lng))
                .collect(Collectors.toList());
    }

    @Override
    public Taller obtenerEntidad(String id) {
        return tallerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Taller no encontrado"));
    }

    @Override
    public Taller obtenerPorUsuario(String correoUsuario) {
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        Optional<Taller> taller = tallerRepository.findByUsuarioId(usuario.getId());
        return taller.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Este usuario no tiene un taller asociado"));
    }

    private TallerDTO toDTO(Taller t, Double lat, Double lng) {
        Double distancia = null;
        if (lat != null && lng != null && t.getLatitud() != null && t.getLongitud() != null) {
            distancia = HaversineUtil.calcularDistanciaKm(lat, lng, t.getLatitud(), t.getLongitud());
        }
        return new TallerDTO(t.getId(), t.getNombre(), t.getCiudad(), t.getDireccion(),
                t.getLatitud(), t.getLongitud(), t.getEspecialidades(), t.getRating(),
                t.getPrecioManoObra(), t.getActivo(), distancia);
    }
}