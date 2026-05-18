package com.drivemaster.drivemaster.service;

import java.io.IOException;
import java.util.List;

import com.drivemaster.drivemaster.model.Producto;
import org.springframework.web.multipart.MultipartFile;

public interface ProductoService {

    Producto crearProducto(Producto producto);

    Producto actualizarProducto(String id, Producto producto);

    Producto obtenerPorId(String id);

    Producto obtenerPorCodigo(String codigo);

    List<Producto> listarTodos();

    void eliminarProducto(String id);

    Producto actualizarStock(String productoId, Integer nuevoStock);

    List<Producto> obtenerPorCategoria(String categoria);

    List<Producto> obtenerPorMarca(String marca);

    List<Producto> buscarPorNombre(String nombre);

    List<Producto> obtenerPorModeloCompatible(String modelo);

    List<Producto> obtenerConStockBajo();

    String subirImagen(String id, MultipartFile archivo) throws IOException;

}
