package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.PedidoTienda;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PedidoTiendaRepository extends MongoRepository<PedidoTienda, String> {

    /** Lo que un taller compró para su inventario y aún no confirma que le llegó. */
    List<PedidoTienda> findByCompradorTallerIdAndFechaEntradaInventarioIsNullOrderByFechaCreacionDesc(String compradorTallerId);
    List<PedidoTienda> findByTiendaIdOrderByFechaCreacionDesc(String tiendaId);
    List<PedidoTienda> findByOrdenIdOrderByFechaCreacionAsc(String ordenId);
    List<PedidoTienda> findByPagoId(Long pagoId);
    List<PedidoTienda> findByCompraIdOrderByFechaCreacionAsc(String compraId);
    /** Pedidos con un problema reportado por el cliente (abiertos y ya resueltos), el más reciente primero. */
    List<PedidoTienda> findByProblemaReportadoTrueOrderByFechaProblemaDesc();
}
