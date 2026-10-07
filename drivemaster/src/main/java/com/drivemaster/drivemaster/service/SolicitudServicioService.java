package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.AceptarSolicitudRequest;
import com.drivemaster.drivemaster.dto.CrearSolicitudServicioRequest;
import com.drivemaster.drivemaster.dto.DiagnosticoRequest;
import com.drivemaster.drivemaster.dto.SeleccionRepuestosRequest;
import com.drivemaster.drivemaster.dto.SolicitudServicioDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Solicitudes de diagnóstico del marketplace (OrdenServicio con flujo MARKETPLACE). */
public interface SolicitudServicioService {

    // ─── Cliente ───
    SolicitudServicioDTO crear(String correoCliente, CrearSolicitudServicioRequest request);

    String subirFoto(MultipartFile archivo);

    List<SolicitudServicioDTO> misSolicitudes(String correoCliente);

    SolicitudServicioDTO cancelarPorCliente(String correoCliente, String id, String motivo);

    /** Cliente dueño, taller asignado o ADMIN. */
    SolicitudServicioDTO obtener(String correo, String id);

    // ─── Taller ───
    List<SolicitudServicioDTO> bandejaTaller(String correoTaller);

    SolicitudServicioDTO aceptar(String correoTaller, String id, AceptarSolicitudRequest request);

    SolicitudServicioDTO rechazarPorTaller(String correoTaller, String id, String motivo);

    SolicitudServicioDTO asignarMecanico(String correoTaller, String id, String mecanicoId);

    /** EN_REVISION → DIAGNOSTICADA; en DIAGNOSTICADA permite corregir el diagnóstico. */
    SolicitudServicioDTO registrarDiagnostico(String correoTaller, String id, DiagnosticoRequest request);

    /** Reemplaza la lista de repuestos elegidos (solo en DIAGNOSTICADA). */
    SolicitudServicioDTO seleccionarRepuestos(String correoTaller, String id, SeleccionRepuestosRequest request);

    // ─── ADMIN ───
    List<SolicitudServicioDTO> listarAdmin(String estado);

    SolicitudServicioDTO reasignarPorAdmin(String correoAdmin, String id, String motivo);
}
