package com.drivemaster.drivemaster.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.drivemaster.drivemaster.model.Producto;
import java.util.stream.Collectors;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    Optional<Producto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Producto> findByCategoria(String categoria);
    
    List<Producto> findByCategoriaContainingIgnoreCase(String categoria);

    List<Producto> findByMarca(String marca);
    
    List<Producto> findByMarcaContainingIgnoreCase(String marca);

    List<Producto> findByCategoriaAndMarca(String categoria, String marca);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);
    
    List<Producto> findByNombreContainingIgnoreCaseOrCategoriaContainingIgnoreCase(String nombre, String categoria);

    @Query("{ $expr: { $lte: ['$stockActual', '$stockMinimo'] } }")
    List<Producto> findProductosConStockBajo();

    @Query("{ 'modelosCompatibles.modelo': ?0 }")
    List<Producto> findByModeloCompatible(String modelo);
    
    @Query("{ '$or': [ " +
           "{ 'nombre': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'categoria': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'marca': { '$regex': ?0, '$options': 'i' } } " +
           "] }")
    List<Producto> searchByTerm(String term);
    
    default List<Producto> searchByTerms(List<String> terms) {
        return terms.stream()
                .flatMap(t -> this.searchByTerm(t).stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
