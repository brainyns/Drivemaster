package com.drivemaster.drivemaster.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.drivemaster.drivemaster.model.Producto;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    Optional<Producto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Producto> findByCategoria(String categoria);

    List<Producto> findByMarca(String marca);

    List<Producto> findByCategoriaAndMarca(String categoria, String marca);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    @Query("{ $expr: { $lte: ['$stockActual', '$stockMinimo'] } }")
    List<Producto> findProductosConStockBajo();

    @Query("{ 'modelosCompatibles.modelo': ?0 }")
    List<Producto> findByModeloCompatible(String modelo);
}
