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

    /** Solo el teléfono de la tienda de la sesión (celular o fijo colombiano, ver TelefonoColombia). */
    TiendaDTO actualizarTelefono(String correoUsuario, String telefono);

    /** Reemplaza el horario por días de la tienda (mismas reglas que el del taller) y regenera el texto que ve el cliente. */
    TiendaDTO guardarHorario(String correoUsuario, java.util.List<com.drivemaster.drivemaster.dto.taller.FranjaHorarioDTO> franjas);

    /** Tienda del usuario, solo si ya fue aprobada y está activa (403 en otro caso). */
    Tienda obtenerOperativaPorUsuario(String correoUsuario);

    /** La tienda de la cuenta (aunque esté pendiente o pausada); 403 si la cuenta no tiene tienda. */
    Tienda obtenerPorUsuario(String correoUsuario);

    /** La tienda por id; 404 si no existe. */
    Tienda obtenerEntidad(String id);

    /** Tiendas que muestran sus productos: aprobadas, activas y con dirección, ciudad y horario. */
    java.util.List<Tienda> listarVisibles();
}
