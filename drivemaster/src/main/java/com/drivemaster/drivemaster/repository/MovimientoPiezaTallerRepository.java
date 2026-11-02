package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.MovimientoPiezaTaller;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoPiezaTallerRepository extends MongoRepository<MovimientoPiezaTaller, String> {

    List<MovimientoPiezaTaller> findByReferenciaAndTipo(String referencia, String tipo);
}
