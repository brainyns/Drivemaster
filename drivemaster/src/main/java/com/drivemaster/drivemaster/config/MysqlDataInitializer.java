package com.drivemaster.drivemaster.config;

import com.drivemaster.drivemaster.model.mysql.*;
import com.drivemaster.drivemaster.repository.mysql.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MysqlDataInitializer implements CommandLineRunner {

    private final MetodoPagoRepository metodoPagoRepo;
    private final EstadoVentaRepository estadoVentaRepo;
    private final ParametroRepository parametroRepo;

    @Override
    public void run(String... args) {
        inicializarMetodosPago();
        inicializarEstados();
        inicializarParametros();
        log.info("✅ MySQL inicializado correctamente");
    }

    private void inicializarMetodosPago() {
        List<Object[]> metodos = List.of(
            new Object[]{"EFECTIVO",      "Efectivo",      "💵", false},
            new Object[]{"TARJETA",       "Tarjeta",       "💳", true},
            new Object[]{"TRANSFERENCIA", "Transferencia", "🏦", true},
            new Object[]{"WOMPI",         "Pago en línea (Wompi)", "🟢", false}
        );

        for (Object[] m : metodos) {
            String codigo = (String) m[0];
            if (!metodoPagoRepo.existsByCodigo(codigo)) {
                MetodoPago mp = new MetodoPago();
                mp.setCodigo(codigo);
                mp.setNombre((String) m[1]);
                mp.setIcono((String) m[2]);
                mp.setActivo(true);
                mp.setRequiereReferencia((Boolean) m[3]);
                metodoPagoRepo.save(mp);
                log.info("➕ Método de pago insertado: {}", codigo);
            }
        }
    }

    private void inicializarEstados() {
        List<Object[]> estados = List.of(
            new Object[]{"PAGADA",              "Pagada"},
            new Object[]{"ANULADA",             "Anulada"},
            new Object[]{"PENDIENTE",           "Pendiente"},
            new Object[]{"CANCELADA",           "Cancelada"},
            new Object[]{"PENDIENTE_PAGO",      "Pendiente de pago"},
            new Object[]{"PAGO_VERIFICADO",     "Pago verificado"},
            new Object[]{"PENDIENTE_APROBACION","Pendiente de aprobación"},
            new Object[]{"APROBADO",            "Aprobado"},
            new Object[]{"RECHAZADO",           "Rechazado"},
            new Object[]{"EN_CAMINO",           "En camino"},
            new Object[]{"ENTREGADO",           "Entregado"}
        );

        for (Object[] e : estados) {
            String codigo = (String) e[0];
            if (estadoVentaRepo.findByCodigo(codigo).isEmpty()) {
                EstadoVenta ev = new EstadoVenta();
                ev.setCodigo(codigo);
                ev.setNombre((String) e[1]);
                ev.setActivo(true);
                estadoVentaRepo.save(ev);
                log.info("➕ Estado insertado: {}", codigo);
            }
        }
    }

    private void inicializarParametros() {
        List<Object[]> params = List.of(
    new Object[]{"IVA",                  "16",     "Porcentaje de impuesto (%)"},
    new Object[]{"TERMINAL_DEFAULT",     "POS-01", "Terminal por defecto"},
    new Object[]{"ESTADO_VENTA_DEFAULT", "PAGADA", "Estado por defecto al registrar venta"}
);

        for (Object[] p : params) {
            String clave = (String) p[0];
            if (parametroRepo.findByClave(clave).isEmpty()) {
                Parametro param = new Parametro();
                param.setClave(clave);
                param.setValor((String) p[1]);
                param.setDescripcion((String) p[2]);
                parametroRepo.save(param);
                log.info("➕ Parámetro insertado: {}", clave);
            }
        }
    }

    
}