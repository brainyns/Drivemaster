package com.drivemaster.drivemaster.service;

import org.springframework.stereotype.Service;

import com.drivemaster.drivemaster.model.Producto;
import com.drivemaster.drivemaster.repository.ProductoRepository;

import java.util.List;

@Service
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoServiceImpl(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public Producto crearProducto(Producto producto) {

        if (productoRepository.existsByCodigo(producto.getCodigo())) {
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

}
