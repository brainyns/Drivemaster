package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.CrearTallerRequest;
import com.drivemaster.drivemaster.dto.CuentaBancariaDTO;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TallerDTO;
import com.drivemaster.drivemaster.dto.TallerMapaDTO;
import com.drivemaster.drivemaster.model.Mecanico;
import com.drivemaster.drivemaster.model.Taller;

import java.util.List;

public interface TallerService {

    TallerDTO crear(CrearTallerRequest request);

    TallerDTO actualizar(String id, CrearTallerRequest request);

    TallerDTO activarDesactivar(String id, boolean activo);

    List<TallerDTO> listarTodos(String estadoAprobacion);

    List<TallerDTO> listarActivos(String ciudad, Double lat, Double lng);

    List<TallerMapaDTO> listarParaMapa();

    Taller obtenerEntidad(String id);

    Taller obtenerPorUsuario(String correoUsuario);

    // ─── Marketplace ───

    TallerDTO registrar(RegistroNegocioRequest request);

    TallerDTO resolverAprobacion(String id, AprobacionRequest request, String correoAdmin);

    TallerDTO miTaller(String correoTaller);

    TallerDTO guardarCuentaBancaria(String correoTaller, CuentaBancariaDTO dto);

    List<Mecanico> listarMecanicos(String correoTaller);

    Mecanico agregarMecanico(String correoTaller, Mecanico mecanico);

    Mecanico actualizarMecanico(String correoTaller, String mecanicoId, Mecanico datos);

    Mecanico cambiarEstadoMecanico(String correoTaller, String mecanicoId, boolean activo);
}
