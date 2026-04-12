package com.drivemaster.drivemaster.service;

import java.util.List;
import com.drivemaster.drivemaster.model.Proveedor;

public interface ProveedorService {
    Proveedor crear(Proveedor proveedor);
    List<Proveedor> listarTodos();
    Proveedor obtenerPorId(String id);
    void eliminarProveedor(String id);
}