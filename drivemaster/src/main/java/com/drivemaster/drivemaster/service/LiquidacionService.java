package com.drivemaster.drivemaster.service;

import com.drivemaster.drivemaster.model.mysql.Reembolso;
import com.drivemaster.drivemaster.model.mysql.RepartoPago;
import com.drivemaster.drivemaster.repository.mysql.ReembolsoRepository;
import com.drivemaster.drivemaster.repository.mysql.RepartoPagoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lista mínima del ADMIN: lo que hay que transferir a talleres y tiendas y lo que
 * hay que reembolsar a clientes. Las transferencias y los reembolsos se hacen a
 * mano (banco / panel de Wompi); aquí solo se marcan como hechos.
 */
@Service
public class LiquidacionService {

    private final RepartoPagoRepository repartoRepository;
    private final ReembolsoRepository reembolsoRepository;

    public LiquidacionService(RepartoPagoRepository repartoRepository, ReembolsoRepository reembolsoRepository) {
        this.repartoRepository = repartoRepository;
        this.reembolsoRepository = reembolsoRepository;
    }

    public List<RepartoPago> repartos(String estado) {
        return estado != null && !estado.isBlank()
                ? repartoRepository.findByEstadoOrderByFechaCreacionDesc(estado.trim().toUpperCase())
                : repartoRepository.findAllByOrderByFechaCreacionDesc();
    }

    public RepartoPago liquidar(Long id, String correoAdmin, String nota) {
        RepartoPago r = repartoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reparto no encontrado"));
        if (!RepartoPago.PENDIENTE.equals(r.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este reparto no está pendiente");
        }
        r.setEstado(RepartoPago.LIQUIDADO);
        r.setFechaLiquidacion(LocalDateTime.now());
        r.setNota(texto(nota, correoAdmin));
        return repartoRepository.save(r);
    }

    public List<Reembolso> reembolsos(String estado) {
        return estado != null && !estado.isBlank()
                ? reembolsoRepository.findByEstadoOrderByFechaCreacionDesc(estado.trim().toUpperCase())
                : reembolsoRepository.findAllByOrderByFechaCreacionDesc();
    }

    public Reembolso marcarReembolsado(Long id, String correoAdmin, String nota) {
        Reembolso r = reembolsoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reembolso no encontrado"));
        if (!Reembolso.PENDIENTE.equals(r.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este reembolso no está pendiente");
        }
        r.setEstado(Reembolso.REALIZADO);
        r.setFechaRealizado(LocalDateTime.now());
        r.setNota(texto(nota, correoAdmin));
        return reembolsoRepository.save(r);
    }

    private static String texto(String nota, String correoAdmin) {
        String t = "Marcado por " + correoAdmin + (nota != null && !nota.isBlank() ? ": " + nota.trim() : "");
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
