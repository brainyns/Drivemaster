package com.drivemaster.drivemaster.repository;

import com.drivemaster.drivemaster.model.HorarioTaller;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HorarioTallerRepository extends MongoRepository<HorarioTaller, String> {

    List<HorarioTaller> findByTallerId(String tallerId);

    void deleteByTallerId(String tallerId);
}
