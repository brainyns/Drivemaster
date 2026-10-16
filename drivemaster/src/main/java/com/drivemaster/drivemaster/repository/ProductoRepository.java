package com.drivemaster.drivemaster.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.drivemaster.drivemaster.model.Producto;

public interface ProductoRepository extends MongoRepository<Producto, String> {

    Optional<Producto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    // El código es único dentro de cada tienda, no en todo el marketplace.
    boolean existsByTiendaIdAndCodigo(String tiendaId, String codigo);

    List<Producto> findByTiendaId(String tiendaId);

    List<Producto> findByTiendaIdIn(java.util.Collection<String> tiendaIds);

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
    
    // Búsqueda del chat: el término llega como expresión ya armada por PatronBusqueda (escapada,
    // tolerante a tildes y plurales, con sinónimos).
    // Incluye la compatibilidad para que "mazda" o "corolla" encuentren los repuestos de ese vehículo.
    @Query("{ '$or': [ " +
           "{ 'nombre': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'categoria': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'marca': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'modelosCompatibles.marca': { '$regex': ?0, '$options': 'i' } }, " +
           "{ 'modelosCompatibles.modelo': { '$regex': ?0, '$options': 'i' } } " +
           "] }")
    List<Producto> searchByTerm(String term);

    List<Producto> findByTipo(String tipo);

    List<Producto> findByTipoAndActivo(String tipo, Boolean activo);

    List<Producto> findByActivoTrue();
}
