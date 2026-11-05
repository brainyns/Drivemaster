package com.drivemaster.drivemaster.dto.plataforma;

import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.service.RetiroPendienteService;

import java.time.LocalDateTime;

/**
 * Lo que el ADMIN necesita para decidir un retiro que nadie recogió. Como ProblemaEnvioDTO: sin el hash del código ni
 * identificadores del cliente; la tienda y su ciudad bastan para ubicarlo.
 */
public record RetiroSinRecogerDTO(
        String id, String tiendaNombre, String tiendaCiudad, Double total, LocalDateTime fechaListoRetiro, long diasSinRetirar,
        Integer recordatoriosRetiro, LocalDateTime revisionRetiroDesde, String estado,
        String resolucionRetiro, LocalDateTime fechaResolucionRetiro, String motivoResolucionRetiro, String retiroResueltoPor) {

    public static RetiroSinRecogerDTO de(PedidoTienda p) {
        if (p == null) return null;
        return new RetiroSinRecogerDTO(p.getId(), p.getTiendaNombre(), p.getTiendaCiudad(), p.getTotal(), p.getFechaListoRetiro(),
                RetiroPendienteService.diasSinRetirar(p, LocalDateTime.now()), p.getRecordatoriosRetiro(), p.getRevisionRetiroDesde(),
                p.getEstado(), p.getResolucionRetiro(), p.getFechaResolucionRetiro(), p.getMotivoResolucionRetiro(), p.getRetiroResueltoPor());
    }
}
