package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO;
import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Desglose;
import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO.Linea;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.PedidoTiendaRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.dispersion.SimulatedDispersionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * El desglose del dinero de un pedido para su tienda y de una orden para su taller. El dueño sale de la sesión: cualquier
 * otro recibe 404, para no revelar que el pedido o la orden existen.
 */
@Service
public class DesgloseLiquidacionService {

    private final RepartoPagoRepository repartos;
    private final PedidoTiendaRepository pedidos;
    private final OrdenServicioRepository ordenes;
    private final TiendaRepository tiendas;
    private final UsuarioService usuarios;
    private final TallerService talleres;
    private final ParametrosMarketplace parametros;

    public DesgloseLiquidacionService(RepartoPagoRepository repartos, PedidoTiendaRepository pedidos,
                                      OrdenServicioRepository ordenes, TiendaRepository tiendas, UsuarioService usuarios,
                                      TallerService talleres, ParametrosMarketplace parametros) {
        this.repartos = repartos;
        this.pedidos = pedidos;
        this.ordenes = ordenes;
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.talleres = talleres;
        this.parametros = parametros;
    }

    public Desglose dePedido(String correoTienda, String pedidoId) {
        Usuario usuario = usuarios.obtenerPorCorreo(correoTienda);
        Tienda tienda = tiendas.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu cuenta no tiene una tienda"));
        pedidos.findById(pedidoId).filter(p -> tienda.getId().equals(p.getTiendaId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        return desglose(repartos.findByPedidoTiendaId(pedidoId).stream()
                .filter(r -> RepartoPago.TIENDA.equals(r.getBeneficiarioTipo()) && tienda.getId().equals(r.getBeneficiarioId()))
                .toList());
    }

    /** La orden puede tener dos líneas del taller: la del diagnóstico y la de la cotización. */
    public Desglose deOrden(String correoTaller, String ordenId) {
        Taller taller = talleres.obtenerPorUsuario(correoTaller);
        OrdenServicio orden = ordenes.findById(ordenId).filter(o -> taller.getId().equals(o.getTallerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        return desglose(repartos.findByOrdenServicioIdAndBeneficiarioTipo(orden.getId(), RepartoPago.TALLER).stream()
                .filter(r -> taller.getId().equals(r.getBeneficiarioId()))
                .toList());
    }

    private Desglose desglose(List<RepartoPago> lineas) {
        boolean seDescuenta = parametros.comisionRetenida();
        return new Desglose(seDescuenta, lineas.stream()
                .sorted(Comparator.comparing(RepartoPago::getFechaCreacion, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> linea(r, seDescuenta)).toList());
    }

    static Linea linea(RepartoPago r, boolean seDescuenta) {
        double recibes = r.getMonto() != null ? r.getMonto() : 0;
        boolean descontada = r.getComisionRetenida() != null && r.getComisionRetenida() > 0;
        String estadoComision = descontada ? DesgloseLiquidacionDTO.DESCONTADA
                : seDescuenta ? DesgloseLiquidacionDTO.NO_EN_ESTE_PAGO : DesgloseLiquidacionDTO.TODAVIA_NO;
        return new Linea(r.getConcepto(), estado(r), r.getMontoBruto() != null ? r.getMontoBruto() : recibes,
                r.getComisionPct(), r.getComisionBase(), r.getComisionCalculada(), estadoComision, recibes,
                RepartoPago.LIQUIDADO.equals(r.getEstado()) ? r.getFechaLiquidacion() : null, referencia(r));
    }

    /** Un fallo o un pago en curso siguen siendo dinero listo para el negocio: lo resuelve DriveMaster. */
    static String estado(RepartoPago r) {
        return switch (r.getEstado() == null ? "" : r.getEstado()) {
            case RepartoPago.LIQUIDADO -> SimulatedDispersionService.MODO.equals(r.getModoLiquidacion())
                    ? DesgloseLiquidacionDTO.SIMULADO : DesgloseLiquidacionDTO.PAGADO;
            case RepartoPago.ANULADO -> DesgloseLiquidacionDTO.ANULADO;
            case RepartoPago.POR_LIQUIDAR, RepartoPago.PROCESANDO, RepartoPago.FALLIDO -> DesgloseLiquidacionDTO.LISTO;
            default -> DesgloseLiquidacionDTO.EN_ESPERA;
        };
    }

    /** La referencia de la transferencia, sin el id interno de Wompi. Solo de lo que de verdad se pagó. */
    static String referencia(RepartoPago r) {
        String ref = r.getReferenciaLiquidacion();
        if (ref == null || !RepartoPago.LIQUIDADO.equals(r.getEstado())
                || RepartoPago.SIN_TRANSFERENCIA.equals(r.getModoLiquidacion())) return null;
        int id = ref.indexOf(" [ID:");
        return id >= 0 ? ref.substring(0, id) : ref;
    }
}
