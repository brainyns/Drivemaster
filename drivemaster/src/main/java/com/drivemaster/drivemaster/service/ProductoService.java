package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Producto;

public interface ProductoService {

    Producto crearProducto(Producto producto);

    Producto actualizarProducto(String id, Producto producto);

    Producto obtenerPorId(String id);

    Producto obtenerPorCodigo(String codigo);

    List<Producto> listarTodos();

    void eliminarProducto(String id);

    Producto actualizarStock(String productoId, Integer nuevoStock);

}
