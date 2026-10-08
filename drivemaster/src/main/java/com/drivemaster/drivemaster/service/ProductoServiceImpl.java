package com.drivemaster.drivemaster.service;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.repository.ProductoRepository;

import java.io.IOException;
import java.util.List;

@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final CloudinaryService cloudinaryService;
    private final TiendaService tiendaService;
    private final MongoTemplate mongoTemplate;

    public ProductoServiceImpl(ProductoRepository productoRepository,
                               CloudinaryService cloudinaryService,
                               TiendaService tiendaService,
                               MongoTemplate mongoTemplate) {
        this.productoRepository = productoRepository;
        this.cloudinaryService = cloudinaryService;
        this.tiendaService = tiendaService;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Producto crearProducto(Producto producto) {

        // DriveMaster no es una tienda: todo producto pertenece a una tienda y quien lo crea tiene que indicarla
        if (producto.getTiendaId() == null || producto.getTiendaId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El producto necesita una tienda: los productos los crea cada tienda desde su panel");
        }

        producto.setFechaCreacion(java.time.LocalDateTime.now());

        if (productoRepository.existsByTiendaIdAndCodigo(producto.getTiendaId(), producto.getCodigo())) {
            throw new RuntimeException("Ya existe un producto con ese código");
        }

        producto.setStockActual(
                producto.getStockActual() != null ? producto.getStockActual() : 0);

        return productoRepository.save(producto);
    }

    @Override
    public Producto actualizarProducto(String id, Producto producto) {

        Producto existente = obtenerPorId(id);

        existente.setNombre(producto.getNombre());
        existente.setCategoria(producto.getCategoria());
        existente.setMarca(producto.getMarca());
        existente.setModelosCompatibles(producto.getModelosCompatibles());
        existente.setPrecioCompra(producto.getPrecioCompra());
        existente.setPrecioVenta(producto.getPrecioVenta());
        existente.setStockMinimo(producto.getStockMinimo());
        existente.setImagenUrl(producto.getImagenUrl());
        // La tienda dueña no se cambia al editar.
        existente.setReferencia(producto.getReferencia());
        existente.setUbicacion(producto.getUbicacion());
        existente.setProveedorId(producto.getProveedorId());
        existente.setDiasEntregaEncargo(producto.getDiasEntregaEncargo());
        existente.setCompatibilidadUniversal(producto.getCompatibilidadUniversal());

        return productoRepository.save(existente);
    }

    @Override
    public Producto obtenerPorId(String id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    @Override
    public Producto obtenerPorCodigo(String codigo) {
        return productoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    @Override
    public List<Producto> listarTodos() {
        return productoRepository.findAll();
    }

    @Override
    public List<Producto> listarCatalogoPublico() {
        // Productos activos de las tiendas visibles, sin datos internos (costo, proveedor, ubicación)
        java.util.Set<String> visibles = tiendaService.listarVisibles().stream().map(com.drivemaster.drivemaster.model.Tienda::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (visibles.isEmpty()) return List.of();
        return productoRepository.findByTiendaIdIn(visibles).stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActivo()))
                .map(ProductoServiceImpl::sinDatosInternos)
                .toList();
    }

    /** Copia pública del producto: nunca expone precio de compra, proveedor, ubicación ni stock mínimo. */
    static Producto sinDatosInternos(Producto p) {
        Producto c = new Producto();
        c.setId(p.getId());
        c.setCodigo(p.getCodigo());
        c.setNombre(p.getNombre());
        c.setCategoria(p.getCategoria());
        c.setMarca(p.getMarca());
        c.setModelosCompatibles(p.getModelosCompatibles());
        c.setPrecioVenta(p.getPrecioVenta());
        c.setStockActual(p.getStockActual());
        c.setTipo(p.getTipo());
        c.setActivo(p.getActivo());
        c.setImagenUrl(p.getImagenUrl());
        c.setTiendaId(p.getTiendaId());
        c.setReferencia(p.getReferencia());
        c.setDiasEntregaEncargo(p.getDiasEntregaEncargo());
        c.setCompatibilidadUniversal(p.getCompatibilidadUniversal());
        return c;
    }

    @Override
    public List<Producto> listarPorTienda(String tiendaId) {
        return productoRepository.findByTiendaId(tiendaId);
    }

    @Override
    public Producto descontarStock(String productoId, int cantidad) {
        validarCantidad(cantidad);
        // La condición stockActual >= cantidad se evalúa en la misma operación que el
        // descuento: dos compras simultáneas ya no pueden dejar el stock en negativo.
        Query query = Query.query(Criteria.where("id").is(productoId).and("stockActual").gte(cantidad));
        Update update = new Update().inc("stockActual", -cantidad);
        Producto actualizado = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Producto.class);
        if (actualizado == null) {
            Producto actual = obtenerPorId(productoId); // 404 si no existe
            throw new StockInsuficienteException(actual.getNombre(),
                    actual.getStockActual() != null ? actual.getStockActual() : 0);
        }
        return actualizado;
    }

    @Override
    public Producto incrementarStock(String productoId, int cantidad) {
        validarCantidad(cantidad);
        Query query = Query.query(Criteria.where("id").is(productoId));
        Update update = new Update().inc("stockActual", cantidad);
        Producto actualizado = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Producto.class);
        if (actualizado == null) {
            throw new RuntimeException("Producto no encontrado");
        }
        return actualizado;
    }

    private void validarCantidad(int cantidad) {
        if (cantidad <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que cero");
        }
    }

    @Override
    public void eliminarProducto(String id) {
        obtenerPorId(id); // valida existencia
        productoRepository.deleteById(id);
    }

    @Override
    public Producto actualizarStock(String productoId, Integer nuevoStock) {

        if (nuevoStock < 0) {
            throw new RuntimeException("El stock no puede ser negativo");
        }

        Producto producto = obtenerPorId(productoId);
        producto.setStockActual(nuevoStock);

        return productoRepository.save(producto);
    }

    @Override
    public List<Producto> obtenerPorCategoria(String categoria) {
        return productoRepository.findByCategoria(categoria);
    }

    @Override
    public List<Producto> obtenerPorMarca(String marca) {
        return productoRepository.findByMarca(marca);
    }

    @Override
    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Override
    public List<Producto> obtenerPorModeloCompatible(String modelo) {
        return productoRepository.findByModeloCompatible(modelo);
    }

    @Override
    public List<Producto> obtenerConStockBajo() {
        return productoRepository.findProductosConStockBajo();
    }

    @Override
    public String subirImagen(String id, MultipartFile archivo) throws IOException {
        Producto producto = obtenerPorId(id);
        String url = cloudinaryService.subirImagen(archivo);
        producto.setImagenUrl(url);
        productoRepository.save(producto);
        return url;
    }

}