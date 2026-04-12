package com.drivemaster.drivemaster.repository;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.drivemaster.drivemaster.model.Producto;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    Optional<Producto> findByCodigo(String codigo);   
    boolean existsByCodigo(String codigo);
}
