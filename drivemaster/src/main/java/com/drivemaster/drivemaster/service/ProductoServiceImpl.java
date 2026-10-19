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

import com.drivemaster.drivemaster.dto.ProductoDTO;
import com.drivemaster.drivemaster.exception.StockInsuficienteException;
import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.model.Tienda;
import com.drivemaster.drivemaster.repository.ProductoRepository;
import com.drivemaster.drivemaster.util.PatronBusqueda;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Producto cambiarPublicado(String id, boolean publicado) {
        Producto producto = obtenerPorId(id);
        producto.setActivo(publicado);
        return productoRepository.save(producto);
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

    /**
     * Productos que recibe el agente por búsqueda. Cada producto ocupa tokens en cada llamada al modelo
     * (se pagan por token en OpenRouter) y el agente solo muestra 3: 4 deja margen para elegir.
     */
    static final int MAX_PRODUCTOS_AGENTE = 4;

    /** Palabras que el modelo a veces manda como términos y que coinciden con casi todo el catálogo. */
    private static final Set<String> PALABRAS_VACIAS = Set.of(
            "de", "del", "la", "las", "el", "los", "un", "una", "para", "con", "por", "en", "y", "o");

    @Override
    public List<ProductoDTO> buscarParaAgente(List<String> terminos) {
        if (terminos == null || terminos.isEmpty()) return List.of();
        List<String> limpios = terminos.stream()
                .map(t -> t == null ? "" : PatronBusqueda.normalizar(t))
                .filter(t -> t.length() >= 2 && !PALABRAS_VACIAS.contains(t))
                .distinct()
                .limit(10)
                .toList();
        if (limpios.isEmpty()) return List.of();

        // Mismo alcance que el catálogo público: solo tiendas visibles (aprobadas, activas y con datos completos).
        Map<String, String> tiendasVisibles = new HashMap<>();
        for (Tienda t : tiendaService.listarVisibles()) {
            tiendasVisibles.put(t.getId(), t.getNombre());
        }
        if (tiendasVisibles.isEmpty()) return List.of();

        // Relevancia: cuántos términos coinciden con cada producto. Con "pastillas,freno,mazda",
        // las pastillas compatibles con Mazda salen antes que cualquier otro repuesto de frenos.
        // Cada término tolera tildes, plurales y sinónimos (rodamiento = balinera): ver PatronBusqueda.
        Map<String, Producto> porId = new LinkedHashMap<>();
        Map<String, Integer> coincidencias = new HashMap<>();
        for (String termino : limpios) {
            for (Producto p : productoRepository.searchByTerm(PatronBusqueda.aRegex(termino))) {
                porId.putIfAbsent(p.getId(), p);
                coincidencias.merge(p.getId(), 1, Integer::sum);
            }
        }

        return porId.values().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActivo()))
                .filter(p -> p.getTiendaId() != null && tiendasVisibles.containsKey(p.getTiendaId()))
                .sorted(Comparator.comparingInt((Producto p) -> coincidencias.get(p.getId())).reversed())
                .limit(MAX_PRODUCTOS_AGENTE)
                .map(p -> paraAgente(p, tiendasVisibles.get(p.getTiendaId())))
                .toList();
    }

    // Sin imagenUrl: el modelo no la usa y cada URL de Cloudinary son decenas de tokens.
    private static ProductoDTO paraAgente(Producto p, String nombreTienda) {
        ProductoDTO dto = new ProductoDTO();
        dto.setId(p.getId());
        dto.setCodigo(p.getCodigo());
        dto.setNombre(p.getNombre());
        dto.setCategoria(p.getCategoria());
        dto.setMarca(p.getMarca());
        dto.setPrecioVenta(p.getPrecioVenta());
        dto.setStockActual(p.getStockActual());
        dto.setTipo(p.getTipo());
        dto.setDiasEntregaEncargo(p.getDiasEntregaEncargo());
        dto.setCompatibilidadUniversal(p.getCompatibilidadUniversal());
        dto.setModelosCompatibles(p.getModelosCompatibles());
        dto.setTienda(nombreTienda);
        return dto;
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