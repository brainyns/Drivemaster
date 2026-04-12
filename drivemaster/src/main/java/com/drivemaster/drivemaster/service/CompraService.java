package com.drivemaster.drivemaster.service;

import java.util.List;

import com.drivemaster.drivemaster.model.Compra;

public interface CompraService {

    Compra registrarCompra(Compra compra);

    Compra obtenerPorId(String id);

    List<Compra> listarTodas();
}

