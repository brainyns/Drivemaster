package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AprobacionRequest;
import com.drivemaster.drivemaster.dto.RegistroNegocioRequest;
import com.drivemaster.drivemaster.dto.TiendaDTO;
import com.drivemaster.drivemaster.model.Tienda;

import java.util.List;

public interface TiendaService {

    TiendaDTO registrar(RegistroNegocioRequest request);

    List<TiendaDTO> listar(String estadoAprobacion);

    TiendaDTO obtener(String id);

    TiendaDTO resolverAprobacion(String id, AprobacionRequest request, String correoAdmin);

    TiendaDTO activarDesactivar(String id, boolean activo);

    TiendaDTO miTienda(String correoUsuario);

    TiendaDTO actualizarMiTienda(String correoUsuario, RegistroNegocioRequest request);

    /** El ADMIN completa los datos de la tienda propia de DriveMaster (ciudad, dirección, horario, envío). */
    TiendaDTO actualizarPlataforma(RegistroNegocioRequest request);

    /** Tienda del usuario, solo si ya fue aprobada y está activa (403 en otro caso). */
    Tienda obtenerOperativaPorUsuario(String correoUsuario);

    /** Tienda propia de DriveMaster (catálogo de compra directa). */
    Tienda obtenerPlataforma();
}
