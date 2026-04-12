package com.drivemaster.drivemaster.service;

import java.util.List;
import com.drivemaster.drivemaster.model.Venta;

public interface VentaService {

    Venta registrarVenta(Venta venta);

    Venta obtenerPorId(String id);

    List<Venta> listarTodas();

    void anularVenta(String ventaId);
}

 
