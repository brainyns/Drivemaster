package com.drivemaster.drivemaster.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.drivemaster.drivemaster.model.Proveedor;

@Repository
public interface ProveedorRepository extends MongoRepository<Proveedor, String> {

}
