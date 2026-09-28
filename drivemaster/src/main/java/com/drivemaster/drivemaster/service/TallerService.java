package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.model.Taller;

import java.util.List;

public interface TallerService {

    TallerDTO crear(CrearTallerRequest request);

    TallerDTO actualizar(String id, CrearTallerRequest request);

    TallerDTO activarDesactivar(String id, boolean activo);

    List<TallerDTO> listarTodos();

    List<TallerDTO> listarActivos(String ciudad, Double lat, Double lng);

    Taller obtenerEntidad(String id);

    Taller obtenerPorUsuario(String correoUsuario);
}