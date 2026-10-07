package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.PedidoTienda;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PedidoTiendaRepository extends MongoRepository<PedidoTienda, String> {
    List<PedidoTienda> findByTiendaIdOrderByFechaCreacionDesc(String tiendaId);
    List<PedidoTienda> findByOrdenIdOrderByFechaCreacionAsc(String ordenId);
    List<PedidoTienda> findByPagoId(Long pagoId);
}
