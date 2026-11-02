package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.DesgloseLiquidacionDTO;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Admin;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Cliente;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Fila;
import com.drivemaster.drivemaster.dto.comun.ResumenServicioDTO.Taller;
import com.drivemaster.drivemaster.model.Cotizacion;
import com.drivemaster.drivemaster.model.OrdenServicio;
import com.drivemaster.drivemaster.model.Usuario;
import com.drivemaster.drivemaster.model.mysql.PagoEntity;
import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.CotizacionRepository;
import com.drivemaster.drivemaster.repository.OrdenServicioRepository;
import com.drivemaster.drivemaster.repository.mysql.PagoRepository;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * El total de una orden para el taller, el cliente y el ADMIN. El dinero sale de lo que de verdad se pagó y repartió:
 * las líneas del taller y los pagos del cliente. La cotización solo aporta el anticipo. El dueño sale de la sesión:
 * cualquier otro recibe 404, para no revelar que la orden existe.
 */
@Service
public class ResumenServicioService {

    private static final String DIAGNOSTICO = "DIAGNOSTICO";
    private static final String COTIZACION = "COTIZACION";
    private static final String PAGADO = "PAGADO";

    private final RepartoPagoRepository repartos;
    private final PagoRepository pagos;
    private final ReembolsoRepository reembolsos;
    private final OrdenServicioRepository ordenes;
    private final CotizacionRepository cotizaciones;
    private final UsuarioService usuarios;
    private final TallerService talleres;
    private final ParametrosMarketplace parametros;

    public ResumenServicioService(RepartoPagoRepository repartos, PagoRepository pagos, ReembolsoRepository reembolsos,
                                  OrdenServicioRepository ordenes, CotizacionRepository cotizaciones, UsuarioService usuarios,
                                  TallerService talleres, ParametrosMarketplace parametros) {
        this.repartos = repartos;
        this.pagos = pagos;
        this.reembolsos = reembolsos;
        this.ordenes = ordenes;
        this.cotizaciones = cotizaciones;
        this.usuarios = usuarios;
        this.talleres = talleres;
        this.parametros = parametros;
    }

    public Taller deTaller(String correoTaller, String ordenId) {
        String tallerId = talleres.obtenerPorUsuario(correoTaller).getId();
        OrdenServicio orden = ordenes.findById(ordenId).filter(o -> tallerId.equals(o.getTallerId()))
                .orElseThrow(ResumenServicioService::noEncontrada);
        return taller(orden, lineasDelTaller(ordenId).stream().filter(r -> tallerId.equals(r.getBeneficiarioId())).toList());
    }

    public Cliente deCliente(String correoCliente, String ordenId) {
        Usuario cliente = usuarios.obtenerPorCorreo(correoCliente);
        ordenes.findById(ordenId).filter(o -> cliente.getId().equals(o.getUsuarioId()))
                .orElseThrow(ResumenServicioService::noEncontrada);
        return cliente(ordenId);
    }

    public Admin deAdmin(String ordenId) {
        OrdenServicio orden = ordenes.findById(ordenId).orElseThrow(ResumenServicioService::noEncontrada);
        List<RepartoPago> deTiendas = vigentes(repartos.findByOrdenServicioIdAndBeneficiarioTipo(ordenId, RepartoPago.TIENDA));
        return new Admin(taller(orden, lineasDelTaller(ordenId)), cliente(ordenId),
                suma(deTiendas, ResumenServicioService::bruto), suma(deTiendas, ResumenServicioService::comision));
    }

    private List<RepartoPago> lineasDelTaller(String ordenId) {
        return repartos.findByOrdenServicioIdAndBeneficiarioTipo(ordenId, RepartoPago.TALLER);
    }

    private Taller taller(OrdenServicio orden, List<RepartoPago> lineas) {
        Cotizacion cotizacion = orden.getCotizacionId() == null ? null : cotizaciones.findById(orden.getCotizacionId()).orElse(null);
        return calcular(orden, cotizacion, lineas, parametros.comisionRetenida());
    }

    /**
     * El anticipo solo cuenta si la cotización se pagó o el diagnóstico la cubrió. La mano de obra sale de lo que recibió
     * el taller más ese anticipo, no de la cotización: el IVA de cada tienda se redondea aparte y el total debe cuadrar.
     */
    static Taller calcular(OrdenServicio orden, Cotizacion cotizacion, List<RepartoPago> lineas, boolean seDescuenta) {
        List<RepartoPago> vivas = vigentes(lineas);
        double diagnostico = suma(vivas.stream().filter(r -> DIAGNOSTICO.equals(r.getConcepto())).toList(), ResumenServicioService::bruto);
        double cotizacionPagada = suma(vivas.stream().filter(r -> COTIZACION.equals(r.getConcepto())).toList(), ResumenServicioService::bruto);
        double anticipo = cubierta(orden, cotizacion) && cotizacion.getAnticipoDiagnostico() != null ? cotizacion.getAnticipoDiagnostico() : 0;
        double total = suma(vivas, ResumenServicioService::bruto);
        List<DesgloseLiquidacionDTO.Linea> detalle = lineas.stream()
                .sorted(Comparator.comparing(RepartoPago::getFechaCreacion, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> DesgloseLiquidacionService.linea(r, seDescuenta)).toList();
        return new Taller(diagnostico, cotizacionPagada + anticipo, anticipo, cotizacionPagada, total, iva(vivas, total),
                suma(vivas, ResumenServicioService::comision), suma(vivas, ResumenServicioService::descontada), seDescuenta,
                suma(vivas, ResumenServicioService::neto), estado(vivas), detalle);
    }

    private static boolean cubierta(OrdenServicio orden, Cotizacion c) {
        return c != null && Cotizacion.APROBADA.equals(c.getEstado())
                && (orden.getPagoCotizacionId() != null || CotizacionService.sinSaldo(c));
    }

    /** Lo que no es base es IVA. Si una línea antigua no guardó su base, no se inventa la cifra. */
    private static Double iva(List<RepartoPago> vivas, double total) {
        if (vivas.isEmpty() || vivas.stream().anyMatch(r -> r.getComisionBase() == null)) return null;
        return (double) Math.max(0, Math.round(total - suma(vivas, RepartoPago::getComisionBase)));
    }

    /** Si todas las líneas están igual, ese es el estado; si ya se pagó algo, PARCIAL; si no, lo más cercano a pagarse. */
    private static String estado(List<RepartoPago> vivas) {
        if (vivas.isEmpty()) return ResumenServicioDTO.SIN_PAGOS;
        List<String> estados = vivas.stream().map(DesgloseLiquidacionService::estado).distinct().toList();
        if (estados.size() == 1) return estados.get(0);
        if (estados.contains(DesgloseLiquidacionDTO.PAGADO) || estados.contains(DesgloseLiquidacionDTO.SIMULADO)) return ResumenServicioDTO.PARCIAL;
        return DesgloseLiquidacionDTO.LISTO;
    }

    /** Lo pagado por el cliente y lo que se le devuelve. Un pago sin aprobar no cuenta. */
    private Cliente cliente(String ordenId) {
        List<PagoEntity> pagados = pagos.findByOrdenServicioIdOrderByIdAsc(ordenId).stream().filter(p -> PAGADO.equals(p.getEstado())).toList();
        double diagnostico = sumaPagos(pagados, DIAGNOSTICO);
        double cotizacion = sumaPagos(pagados, COTIZACION);
        List<Reembolso> devoluciones = reembolsos.findByOrdenServicioId(ordenId);
        return new Cliente(diagnostico, cotizacion, diagnostico + cotizacion, sumaDevoluciones(devoluciones, Reembolso.PENDIENTE),
                sumaDevoluciones(devoluciones, Reembolso.REALIZADO));
    }

    private static double sumaPagos(List<PagoEntity> pagos, String concepto) {
        return pagos.stream().filter(p -> concepto.equals(p.getConcepto())).mapToDouble(p -> p.getMonto() == null ? 0 : p.getMonto()).sum();
    }

    private static double sumaDevoluciones(List<Reembolso> devoluciones, String estado) {
        return devoluciones.stream().filter(b -> estado.equals(b.getEstado())).mapToDouble(b -> b.getMonto() == null ? 0 : b.getMonto()).sum();
    }

    /** El total de una orden en una fila de Liquidaciones, con todas sus líneas del taller. */
    static Fila fila(List<RepartoPago> lineasDelTaller) {
        List<RepartoPago> vivas = vigentes(lineasDelTaller);
        return new Fila(suma(vivas, ResumenServicioService::bruto), suma(vivas, ResumenServicioService::comision),
                suma(vivas, ResumenServicioService::descontada), suma(vivas, ResumenServicioService::neto), estado(vivas), vivas.size());
    }

    private static List<RepartoPago> vigentes(List<RepartoPago> lineas) {
        return lineas.stream().filter(r -> !RepartoPago.ANULADO.equals(r.getEstado())).toList();
    }

    private static double suma(List<RepartoPago> lineas, ToDoubleFunction<RepartoPago> valor) {
        return lineas.stream().mapToDouble(valor).sum();
    }

    private static double neto(RepartoPago r) {
        return r.getMonto() == null ? 0 : r.getMonto();
    }

    private static double bruto(RepartoPago r) {
        return r.getMontoBruto() != null ? r.getMontoBruto() : neto(r);
    }

    private static double comision(RepartoPago r) {
        return r.getComisionCalculada() == null ? 0 : r.getComisionCalculada();
    }

    private static double descontada(RepartoPago r) {
        return r.getComisionRetenida() == null ? 0 : r.getComisionRetenida();
    }

    private static ResponseStatusException noEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
    }
}
