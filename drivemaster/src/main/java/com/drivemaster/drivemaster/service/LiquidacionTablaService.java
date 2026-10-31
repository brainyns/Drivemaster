package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.dto.comun.Pagina;
import com.drivemaster.drivemaster.dto.comun.PeticionTabla;
import com.drivemaster.drivemaster.dto.comun.RangoFechas;
import com.drivemaster.drivemaster.model.CuentaBancaria;
import com.drivemaster.drivemaster.model.Taller;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.TallerRepository;
import com.drivemaster.drivemaster.repository.TiendaRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import com.drivemaster.drivemaster.service.dispersion.WompiPayoutService;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La tabla de Liquidaciones del ADMIN: cada línea con el nombre actual del negocio y su cuenta enmascarada, y el resumen
 * de arriba, que cuenta todas las líneas sin importar los filtros de la lista. Del número de cuenta solo salen los
 * últimos 4 dígitos. Una línea guarda el nombre que tenía el negocio al pagar: es el respaldo si ya no existe.
 */
@Service
public class LiquidacionTablaService {

    private final LiquidacionService liquidacion;
    private final RepartoPagoRepository repartos;
    private final TallerRepository talleres;
    private final TiendaRepository tiendas;

    public LiquidacionTablaService(LiquidacionService liquidacion, RepartoPagoRepository repartos,
                                   TallerRepository talleres, TiendaRepository tiendas) {
        this.liquidacion = liquidacion;
        this.repartos = repartos;
        this.talleres = talleres;
        this.tiendas = tiendas;
    }

    /** Solo lo necesario para reconocer la cuenta destino antes de pagar. */
    public record CuentaDestino(String banco, String tipoCuenta, String numero) {}

    /** La línea con sus campos de siempre, más beneficiario (nombre actual), cuenta (null si no tiene) y si se puede resolver a mano. */
    public record Fila(@JsonUnwrapped RepartoPago reparto, String beneficiario, CuentaDestino cuenta, boolean sePuedeResolver) {}

    public record Cifra(long cantidad, double monto) {
        static final Cifra CERO = new Cifra(0, 0);
    }

    /** pagadoMes incluye lo simulado: pagadoMesSimulado dice cuánto de eso no movió dinero. */
    public record Resumen(Cifra porPagar, Cifra enEspera, Cifra fallidas, Cifra enProceso, Cifra pagadoMes, double pagadoMesSimulado) {}

    public record Tabla(Pagina<Fila> repartos, long pendientes, double montoPendiente, Resumen resumen) {}

    public Tabla tabla(String estado, RangoFechas rango, PeticionTabla peticion) {
        LiquidacionService.RepartosTabla base = liquidacion.repartosTabla(estado, rango, peticion);
        Pagina<RepartoPago> p = base.repartos();
        return new Tabla(new Pagina<>(filas(p.items()), p.total(), p.pagina(), p.tamano(), p.totalPaginas()),
                base.pendientes(), base.montoPendiente(), resumen(LocalDate.now(RangoFechas.ZONA)));
    }

    private List<Fila> filas(List<RepartoPago> lineas) {
        Map<String, Taller> porTaller = delTipo(lineas, RepartoPago.TALLER, talleres::findAllById, Taller::getId);
        Map<String, Tienda> porTienda = delTipo(lineas, RepartoPago.TIENDA, tiendas::findAllById, Tienda::getId);
        return lineas.stream().map(r -> {
            if (RepartoPago.TALLER.equals(r.getBeneficiarioTipo())) {
                Taller t = porTaller.get(r.getBeneficiarioId());
                return fila(r, t == null ? null : t.getNombre(), t == null ? null : t.getCuentaBancaria());
            }
            Tienda t = porTienda.get(r.getBeneficiarioId());
            return fila(r, t == null ? null : t.getNombre(), t == null ? null : t.getCuentaBancaria());
        }).toList();
    }

    private static <T> Map<String, T> delTipo(List<RepartoPago> lineas, String tipo, Function<List<String>, List<T>> buscar,
                                              Function<T, String> id) {
        List<String> ids = lineas.stream().filter(r -> tipo.equals(r.getBeneficiarioTipo()))
                .map(RepartoPago::getBeneficiarioId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : buscar.apply(ids).stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static Fila fila(RepartoPago r, String nombreActual, CuentaBancaria cuenta) {
        String nombre = nombreActual != null && !nombreActual.isBlank() ? nombreActual : r.getBeneficiarioNombre();
        boolean conCuenta = cuenta != null && cuenta.getNumeroCuenta() != null && !cuenta.getNumeroCuenta().isBlank();
        return new Fila(r, nombre, !conCuenta ? null : new CuentaDestino(cuenta.getBanco(), cuenta.getTipoCuenta(),
                WompiPayoutService.enmascarar(cuenta.getNumeroCuenta())), LiquidacionService.sePuedeResolver(r, LocalDateTime.now()));
    }

    Resumen resumen(LocalDate hoy) {
        Map<String, Cifra> porEstado = new HashMap<>();
        for (Object[] f : repartos.resumenSinPagar()) porEstado.put(String.valueOf(f[0]), cifra(f));

        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        Cifra pagado = Cifra.CERO;
        double simulado = 0;
        for (Object[] f : repartos.resumenPagado(inicioMes, inicioMes.plusMonths(1))) {
            Cifra c = cifra(f);
            pagado = new Cifra(pagado.cantidad() + c.cantidad(), pagado.monto() + c.monto());
            if ("SIMULADO".equals(f[0])) simulado += c.monto();
        }
        return new Resumen(porEstado.getOrDefault(RepartoPago.POR_LIQUIDAR, Cifra.CERO),
                porEstado.getOrDefault(RepartoPago.PENDIENTE, Cifra.CERO),
                porEstado.getOrDefault(RepartoPago.FALLIDO, Cifra.CERO),
                porEstado.getOrDefault(RepartoPago.PROCESANDO, Cifra.CERO), pagado, simulado);
    }

    private static Cifra cifra(Object[] fila) {
        return new Cifra(((Number) fila[1]).longValue(), ((Number) fila[2]).doubleValue());
    }
}
