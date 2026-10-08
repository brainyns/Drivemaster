package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.CambioTaller;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CambioTallerRepository extends MongoRepository<CambioTaller, String> {

    List<CambioTaller> findByTallerIdOrderByFechaDesc(String tallerId);
}
