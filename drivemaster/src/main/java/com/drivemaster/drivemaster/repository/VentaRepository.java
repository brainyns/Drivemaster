package com.drivemaster.drivemaster.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.drivemaster.drivemaster.model.Venta;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VentaRepository extends MongoRepository<Venta, String> {

    List<Venta> findByFechaBetween(
            LocalDateTime inicio,
            LocalDateTime fin
    );

    List<Venta> findByUsuarioId(String usuarioId);

    List<Venta> findByClienteId(String clienteId);

    List<Venta> findByTipoVenta(String tipoVenta);

    List<Venta> findByEstado(String estado);
}
