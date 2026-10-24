package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.ConfigMantenimientoRequest;
import com.drivemaster.drivemaster.dto.CrearVehiculoRequest;
import com.drivemaster.drivemaster.dto.RepuestoCompatibleDTO;
import com.drivemaster.drivemaster.dto.VehiculoDTO;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.Vehiculo;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.repository.VehiculoRepository;
import com.drivemaster.drivemaster.util.CompatibilidadVehiculo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VehiculoServiceImpl implements VehiculoService {

    private final VehiculoRepository vehiculoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioService usuarioService;
    private final CloudinaryService cloudinaryService;
    private final MantenimientoService mantenimientoService;
    private final TiendaService tiendaService;

 public VehiculoServiceImpl(VehiculoRepository vehiculoRepository,
                            ProductoRepository productoRepository,
                            UsuarioService usuarioService,
                            CloudinaryService cloudinaryService,
                            MantenimientoService mantenimientoService,
                            TiendaService tiendaService) {
    this.vehiculoRepository = vehiculoRepository;
    this.productoRepository = productoRepository;
    this.usuarioService = usuarioService;
    this.cloudinaryService = cloudinaryService;
    this.mantenimientoService = mantenimientoService;
    this.tiendaService = tiendaService;
}

    @Override
    public VehiculoDTO crear(String correoUsuario, CrearVehiculoRequest request) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoUsuario);

        Vehiculo vehiculo = Vehiculo.builder()
                .usuarioId(usuario.getId())
                .marca(request.getMarca())
                .modelo(request.getModelo())
                .anio(request.getAnio())
                .motor(request.getMotor())
                .combustible(request.getCombustible())
                .placa(request.getPlaca())
                .kilometraje(request.getKilometraje())
                .build();

        // Intervalo por defecto y opt-in de recordatorios de mantenimiento.
        mantenimientoService.aplicarDefaults(vehiculo);

        vehiculo = vehiculoRepository.save(vehiculo);
        return toDTO(vehiculo);
    }

    @Override
    public List<VehiculoDTO> listarPorUsuario(String correoUsuario) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoUsuario);
        return vehiculoRepository.findByUsuarioId(usuario.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public Vehiculo obtenerEntidad(String vehiculoId) {
        return vehiculoRepository.findById(vehiculoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehículo no encontrado"));
    }

    @Override
    public List<Vehiculo> obtenerEntidades(java.util.Collection<String> ids) {
        return ids.isEmpty() ? List.of() : vehiculoRepository.findAllById(ids);
    }

    @Override
    public VehiculoDTO obtenerDeUsuario(String correoUsuario, String vehiculoId) {
        Vehiculo vehiculo = validarPropiedad(correoUsuario, vehiculoId);
        return toDTO(vehiculo);
    }

    @Override
    public List<RepuestoCompatibleDTO> buscarRepuestosCompatibles(String correoUsuario, String vehiculoId) {
        Vehiculo vehiculo = validarPropiedad(correoUsuario, vehiculoId);

        List<Producto> candidatos = productoRepository.findByModeloCompatible(vehiculo.getModelo());

        // Se ofrecen los repuestos de TODAS las tiendas visibles (aprobadas, activas y con sus datos completos)
        Map<String, Tienda> visibles = tiendaService.listarVisibles().stream()
                .collect(Collectors.toMap(Tienda::getId, t -> t, (a, b) -> a));

        return candidatos.stream()
                .filter(p -> p.getTiendaId() != null && visibles.containsKey(p.getTiendaId()))
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .filter(p -> p.getStockActual() != null && p.getStockActual() > 0)
                .filter(p -> CompatibilidadVehiculo.esCompatible(p, vehiculo))
                .map(p -> toRepuestoDTO(p, visibles.get(p.getTiendaId())))
                .collect(Collectors.toList());
    }

    private Vehiculo validarPropiedad(String correoUsuario, String vehiculoId) {
        Usuario usuario = usuarioService.obtenerPorCorreo(correoUsuario);
        Vehiculo vehiculo = obtenerEntidad(vehiculoId);
        boolean esAdmin = "SUPERADMIN".equals(usuario.getRol()) || "ADMIN".equals(usuario.getRol());
        if (!esAdmin && !vehiculo.getUsuarioId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este vehículo no le pertenece");
        }
        return vehiculo;
    }

    private RepuestoCompatibleDTO toRepuestoDTO(Producto p, Tienda tienda) {
        return new RepuestoCompatibleDTO(p.getId(), p.getCodigo(), p.getNombre(), p.getCategoria(),
                p.getMarca(), p.getPrecioVenta(), p.getStockActual(), p.getImagenUrl(),
                tienda.getId(), tienda.getNombre(), tienda.getCiudad());
    }

    private VehiculoDTO toDTO(Vehiculo v) {
    mantenimientoService.aplicarDefaults(v);

    Integer kmProximo = null;
    Boolean vencido = null;
    if (v.getKmUltimoMantenimiento() != null && v.getIntervaloMantenimientoKm() != null) {
        kmProximo = v.getKmUltimoMantenimiento() + v.getIntervaloMantenimientoKm();
        vencido = v.getKilometraje() != null && v.getKilometraje() >= kmProximo;
    }

    return new VehiculoDTO(v.getId(), v.getMarca(), v.getImagenUrl(), v.getModelo(), v.getAnio(),
    v.getMotor(), v.getCombustible(), v.getPlaca(), v.getKilometraje(),
    v.getKmUltimoMantenimiento(), v.getIntervaloMantenimientoKm(), v.getRecordatoriosActivos(),
    kmProximo, vencido, v.getUltimoRecordatorioEnviado());
    }

    @Override
    public VehiculoDTO configurarMantenimiento(String correoUsuario, String vehiculoId,
                                               ConfigMantenimientoRequest request) {
        // validarPropiedad lanza 403/404 si el vehículo no es del usuario.
        Vehiculo vehiculo = validarPropiedad(correoUsuario, vehiculoId);
        mantenimientoService.aplicarDefaults(vehiculo);

        if (request.getIntervaloKm() != null) {
            if (request.getIntervaloKm() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El intervalo debe ser un número positivo de kilómetros");
            }
            vehiculo.setIntervaloMantenimientoKm(request.getIntervaloKm());
        }
        if (request.getRecordatoriosActivos() != null) {
            vehiculo.setRecordatoriosActivos(request.getRecordatoriosActivos());
        }

        return toDTO(vehiculoRepository.save(vehiculo));
    }

    @Override
    public String subirImagen(String correoUsuario, String vehiculoId, MultipartFile archivo) throws IOException {
    Vehiculo vehiculo = validarPropiedad(correoUsuario, vehiculoId);
    String url = cloudinaryService.subirImagen(archivo);
    vehiculo.setImagenUrl(url);
    vehiculoRepository.save(vehiculo);
    return url;
}
}