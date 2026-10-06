package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.CrearOrdenServicioRequest;
import com.drivemaster.drivemaster.dto.OrdenServicioDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface OrdenServicioService {

    OrdenServicioDTO cotizar(String correoUsuario, CrearOrdenServicioRequest request);

    OrdenServicioDTO reportarPago(String correoUsuario, String ordenId, String metodoPago, String referencia);

    OrdenServicioDTO confirmarPago(String correoTaller, String ordenId);

    OrdenServicioDTO cancelar(String correoUsuario, String ordenId);

    OrdenServicioDTO iniciarServicio(String correoTaller, String ordenId);

    OrdenServicioDTO finalizarServicio(String correoTaller, String ordenId, String observaciones);

    OrdenServicioDTO programarCita(String correoTaller, String ordenId, LocalDateTime fechaProgramada);

    List<OrdenServicioDTO> listarPorUsuario(String correoUsuario);

    List<OrdenServicioDTO> listarPorTaller(String correoTaller);

    List<OrdenServicioDTO> historialPorVehiculo(String correoUsuario, String vehiculoId);

    List<OrdenServicioDTO> listarPorTallerAdmin(String tallerId);
}