package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Proveedor;

/**
 * Los proveedores son de cada tienda: todo lo que se expone por la API pasa por los métodos "DeTienda", que filtran
 * por dueño en el servidor. obtenerPorId solo lo usan otros servicios que ya validaron el dueño.
 */
public interface ProveedorService {
    Proveedor crear(Proveedor proveedor);
    List<Proveedor> listarTodos();
    Proveedor obtenerPorId(String id);
    void eliminarProveedor(String id);

    List<Proveedor> listarDeTienda(String tiendaId);
    /** 404 si no existe o es de otra tienda (no se revela que existe). */
    Proveedor obtenerDeTienda(String id, String tiendaId);
    Proveedor crearEnTienda(Proveedor proveedor, String tiendaId);
    Proveedor actualizarEnTienda(String id, Proveedor datos, String tiendaId);
    void eliminarDeTienda(String id, String tiendaId);
}
