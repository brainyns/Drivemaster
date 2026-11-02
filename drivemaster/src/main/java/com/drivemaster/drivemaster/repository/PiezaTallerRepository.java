package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.PiezaTaller;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PiezaTallerRepository extends MongoRepository<PiezaTaller, String> {

    List<PiezaTaller> findByTallerIdOrderByNombreAsc(String tallerId);

    List<PiezaTaller> findByTallerIdAndStockGreaterThan(String tallerId, int stock);
}
