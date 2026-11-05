package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.PedidoTienda;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Lo que debe cumplir una orden para que el taller la trabaje, además de estar en el estado correcto. El orden de los
 * estados ya lo garantiza por el camino normal: estas reglas protegen de una orden corregida a mano o con datos viejos.
 */
@Service
public class ReglasTrabajoTaller {

    static final String SIN_REPUESTOS = "Ningún repuesto llegó: no se puede iniciar la instalación. DriveMaster revisará esta solicitud.";

    private final CotizacionRepository cotizaciones;
    private final PedidoTiendaRepository pedidos;

    public ReglasTrabajoTaller(CotizacionRepository cotizaciones, PedidoTiendaRepository pedidos) {
        this.cotizaciones = cotizaciones;
        this.pedidos = pedidos;
    }

    /** La cotización está pagada, o el diagnóstico la cubrió y no había nada que pagar. */
    public void exigirPago(OrdenServicio orden) {
        if (orden.getPagoCotizacionId() != null) return;
        boolean sinSaldo = orden.getCotizacionId() != null && cotizaciones.findById(orden.getCotizacionId())
                .filter(c -> Cotizacion.APROBADA.equals(c.getEstado())).filter(CotizacionService::sinSaldo).isPresent();
        if (!sinSaldo) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cotización de esta solicitud no está pagada");
        }
    }

    public void exigirRepuestos(OrdenServicio orden) {
        if (ningunoLlego(pedidos.findByOrdenIdOrderByFechaCreacionAsc(orden.getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, SIN_REPUESTOS);
        }
    }

    /** Había repuestos pedidos y ninguna tienda los despachó. Sin pedidos es una cotización solo de mano de obra. */
    static boolean ningunoLlego(List<PedidoTienda> pedidos) {
        return !pedidos.isEmpty() && pedidos.stream().allMatch(p -> PedidoTienda.NO_DESPACHADO.equals(p.getEstado()));
    }
}
