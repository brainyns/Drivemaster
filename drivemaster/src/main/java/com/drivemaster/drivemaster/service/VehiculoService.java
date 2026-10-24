package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.ConfigMantenimientoRequest;
import com.drivemaster.drivemaster.dto.CrearVehiculoRequest;
import com.drivemaster.drivemaster.dto.RepuestoCompatibleDTO;
import com.drivemaster.drivemaster.dto.VehiculoDTO;
import com.drivemaster.drivemaster.model.Vehiculo;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface VehiculoService {

    VehiculoDTO crear(String correoUsuario, CrearVehiculoRequest request);

    List<VehiculoDTO> listarPorUsuario(String correoUsuario);

    Vehiculo obtenerEntidad(String vehiculoId);

    /** Varios vehículos en una sola consulta; los que no existen simplemente no vienen. */
    List<Vehiculo> obtenerEntidades(java.util.Collection<String> ids);

    VehiculoDTO obtenerDeUsuario(String correoUsuario, String vehiculoId);

    List<RepuestoCompatibleDTO> buscarRepuestosCompatibles(String correoUsuario, String vehiculoId);

    String subirImagen(String correoUsuario, String vehiculoId, MultipartFile archivo) throws IOException;

    VehiculoDTO configurarMantenimiento(String correoUsuario, String vehiculoId,
                                        ConfigMantenimientoRequest request);
}